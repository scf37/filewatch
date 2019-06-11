package me.scf37.filewatch.impl

import java.io.IOException
import java.nio.file.FileSystemException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

import me.scf37.filewatch.ChangeEvent
import me.scf37.filewatch.DeleteEvent
import me.scf37.filewatch.DesyncEvent
import me.scf37.filewatch.FileWatcherEvent

import scala.util.Try

/**
  * This class manages WatchService registration, registering new directories as needed when file system changes
  *
  * @param listener
  * @param rootPath
  * @param filter
  * @param followLinks
  */
private[filewatch] class WatchServiceRegistrar(
  listener: FileWatcherEvent => Unit,
  rootPath: Path,
  filter: Path => Boolean,
  followLinks: Boolean) {

  private[this] var watchedPaths = Set.empty[Path]

  private[this] val linkOptions = followLinks match {
    case false => Seq(LinkOption.NOFOLLOW_LINKS)
    case true => Seq.empty
  }

  private[this] val watchService: WatchService = FileSystems.getDefault.newWatchService()

  watchDirs(rootPath, notify_ = false, forceRegistration = false)

  /**
    * Update directory tree registration if necessary
    * @param event
    */
  def update(event: FileWatcherEvent): Unit =  synchronized {
    event match {
      case ChangeEvent(p) if Files.isDirectory(p, linkOptions: _*) =>
        //this one could be re-creation of existing directory.
        //despite having the same name, new directory will have different inode
        //and therefore will NOT be watched! So we do not check for existing registration in this case.
        watchDirs(p, notify_ = true, forceRegistration = true)
      case _ =>
    }
  }

  /**
    * Should we send this event to client?
    *
    * @param event
    * @return
    */
  def shouldNotify(event: FileWatcherEvent): Boolean = event match {
    case ChangeEvent(p) => shouldNotify(p)
    case DeleteEvent(p) => shouldNotify(p)
    case DesyncEvent => true
  }

  def poll(): Seq[FileWatcherEvent] =
    watchService.poll() match {
      case null => Seq.empty
      case watchKey => extractEvents(watchKey)
    }

  private[this] def extractEvents(watchKey: WatchKey): Seq[FileWatcherEvent] = {
    import scala.collection.JavaConverters._

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
    }).toSeq
  }

  def close(): Unit = {
    Try (watchService.close())
  }

  private[this] def shouldNotify(path: Path): Boolean = synchronized {
    filter(rootPath.resolve(rootPath.relativize(path)))
  }

  private[this] def watchDirs(
    dir: Path,
    notify_ : Boolean,
    forceRegistration: Boolean,
    processedDirs: Set[Path] = Set.empty): Unit = {

    if (!Files.exists(dir)) return

    val normalizedDir = normalize(dir)

    if (processedDirs.contains(normalizedDir)) return

    watchDir(dir, forceRegistration)

    Files.list(dir).forEach(new Consumer[Path] {
      override def accept(t: Path): Unit = {
        if (notify_ && shouldNotify(t)) {
          listener(ChangeEvent(t))
        }
        if (Files.isDirectory(t, linkOptions: _*)) {
          watchDirs(t, notify_, forceRegistration, processedDirs + normalizedDir)
        }
      }
    })
  }

  private[this] def normalize(path: Path): Path = {
    //still may be missing
    Try(path.toRealPath()).getOrElse(path)
  }

  private[this] def watchDir(dir: Path, forceRegistration: Boolean): Unit = {
    val normalizedDir = normalize(dir)

    if (!forceRegistration && watchedPaths.contains(normalizedDir)) return

    var retryCount = 0
    var lastException: Exception = null

    while (retryCount < 3) {
      try {

        dir.register(watchService, WatchServiceRegistrar.WATCH_KINDS, WatchServiceRegistrar.WATCH_MODIFIERS: _*)
        watchedPaths += normalizedDir
        return;
      } catch {

        case e: NoSuchFileException =>
          //dir is missing - silent exit
          return

        case e: FileSystemException if e.getMessage != null && e.getMessage.contains("Bad file descriptor") =>
          // retry after getting "Bad file descriptor" exception
          lastException = e

        case e: IOException =>
          // Windows at least will sometimes throw odd exceptions like java.nio.file.AccessDeniedException
          // if the file gets deleted while the watch is being set up.
          // So, we just ignore the exception if the dir doesn't exist anymore
          if (!dir.toFile.exists()) {
            // return silently when directory doesn't exist
            return
          } else {
            // no retry
            throw e
          }
      }
      retryCount += 1
    }
    throw lastException
  }
}

private object WatchServiceRegistrar {
  final val WATCH_MODIFIERS = Array(com.sun.nio.file.SensitivityWatchEventModifier.HIGH)
  final val WATCH_KINDS = Array(
    StandardWatchEventKinds.ENTRY_CREATE,
    StandardWatchEventKinds.ENTRY_DELETE,
    StandardWatchEventKinds.ENTRY_MODIFY).map(_.asInstanceOf[WatchEvent.Kind[_]])
}