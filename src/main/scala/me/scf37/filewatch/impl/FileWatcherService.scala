package me.scf37.filewatch.impl

import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

import me.scf37.filewatch.ChangeEvent
import me.scf37.filewatch.DeleteEvent
import me.scf37.filewatch.DesyncEvent
import me.scf37.filewatch.FileWatcher
import me.scf37.filewatch.FileWatcherEvent

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.Promise

/**
  * Manages polling and service lifecycle
  *
  * @param threadFactory
  * @param onError
  * @param listener
  * @param followLinks
  */
private[filewatch] class FileWatcherService(
  threadFactory: ThreadFactory,
  onError: Throwable => Unit,
  listener: FileWatcherEvent => Unit,
  followLinks: Boolean
) extends FileWatcher {

  private final val POLL_TIMEOUT_SECONDS: Int = 5

  private[this] sealed trait State
  private[this] case object Started extends State
  private[this] case class Stopping(p: Promise[Unit]) extends State
  private[this] case object Stopped extends State

  @volatile
  private[this] var state: State = Started

  private[this] val workerThread = threadFactory.newThread(new Runnable {
    override def run(): Unit = main()
  })

  private[this] val watchService: WatchService = FileSystems.getDefault.newWatchService()

  private[this] val registry = new WatchServiceRegistrar(watchService, safeListener, followLinks)

  workerThread.start()

  override def watch(path: Path, filter: (Path) => Boolean): Unit = {
    synchronized {
      state match {
        case Started =>
        case Stopping(_) => throw new IllegalStateException("FileWatcher is stopping")
        case Stopped => throw new IllegalStateException("FileWatcher is stopped")
      }
    }

    registry.watch(path, filter)
  }

  override def isRunning: Boolean = state == Started

  override def close(): Future[Unit] = synchronized {
    state match {
      case Stopping(_) | Stopped => Future successful Unit
      case Started =>
        val p = Promise[Unit]
        state = Stopping(p)
        workerThread.interrupt()
        p.future
    }
  }

  private[this] def main(): Unit = {
    try {
      while (isRunning) {
        poll(watchService).foreach { event =>
          if (isRunning) {
            //tell registry of this event so it can update registration
            registry.update(event)

            if (registry.shouldNotify(event)) {
              safeListener(event)
            }
          }
        }
      }
    } catch {
      case _: InterruptedException | _: ClosedWatchServiceException =>
      case e: Throwable => onError(e)
    } finally {
      state match {
        case Started => state = Stopped
        case Stopping(p) => p.success(Unit)
        case Stopped =>
      }
      watchService.close()
    }
  }

  private[this] def safeListener(event: FileWatcherEvent): Unit =
    try {
      listener(event)
    } catch {
      case e: Throwable => onError(e)
    }

  private[this] def poll(watchService: WatchService): Seq[FileWatcherEvent] =
    watchService.poll(POLL_TIMEOUT_SECONDS, TimeUnit.SECONDS) match {
      case null => Seq.empty
      case watchKey => extractEvents(watchKey)
    }

  private[this] def extractEvents(watchKey: WatchKey): Seq[FileWatcherEvent] = {
    val basePath = watchKey.watchable().asInstanceOf[Path]
    val events = watchKey.pollEvents().asScala

    watchKey.reset()

    events.map(e => e.kind() match {
      case StandardWatchEventKinds.OVERFLOW => DesyncEvent

      case StandardWatchEventKinds.ENTRY_CREATE =>
        ChangeEvent(basePath.resolve(e.context().asInstanceOf[Path]))

      case StandardWatchEventKinds.ENTRY_DELETE =>
        DeleteEvent(basePath.resolve(e.context().asInstanceOf[Path]))

      case StandardWatchEventKinds.ENTRY_MODIFY =>
        ChangeEvent(basePath.resolve(e.context().asInstanceOf[Path]))
    })
  }
}
