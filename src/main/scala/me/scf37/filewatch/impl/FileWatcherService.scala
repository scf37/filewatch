package me.scf37.filewatch.impl

import java.nio.file.ClosedWatchServiceException
import java.nio.file.Path
import java.util.concurrent.ThreadFactory

import me.scf37.filewatch.FileWatcher
import me.scf37.filewatch.FileWatcherEvent

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Try

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

  private final val POOL_DELAY_MS: Int = 10

  private[this] sealed trait State
  private[this] case object Started extends State
  private[this] case class Stopping(p: Promise[Unit]) extends State
  private[this] case object Stopped extends State

  @volatile
  private[this] var state: State = Started

  private[this] val workerThread = threadFactory.newThread(new Runnable {
    override def run(): Unit = main()
  })

  private[this] var registrars = Seq.empty[WatchServiceRegistrar]

  workerThread.start()

  override def watch(path: Path, filter: (Path) => Boolean): Unit = synchronized {
    state match {
      case Started =>
      case Stopping(_) => throw new IllegalStateException("FileWatcher is stopping")
      case Stopped => throw new IllegalStateException("FileWatcher is stopped")
    }
    registrars +:= new WatchServiceRegistrar(safeListener, path, filter, followLinks)
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
        try {
          val regs = synchronized {
            registrars
          }

          regs.foreach { r =>
            r.poll().foreach { event =>
              if (isRunning) {
                //tell registry of this event so it can update registration
                r.update(event)

                if (r.shouldNotify(event)) {
                  safeListener(event)
                }
              }
            }
          }

          Thread.sleep(POOL_DELAY_MS)
        } catch {
          case _: InterruptedException | _: ClosedWatchServiceException => //interrupted = we are already stopping
          case e: Throwable => onError(e)
        }

      }
    } finally {
      state match {
        case Started => state = Stopped
        case Stopping(p) => p.success(Unit)
        case Stopped =>
      }
      synchronized {
        registrars.foreach(r => Try(r.close()))
      }
    }
  }

  private[this] def safeListener(event: FileWatcherEvent): Unit =
    try {
      listener(event)
    } catch {
      case e: Throwable => onError(e)
    }
}
