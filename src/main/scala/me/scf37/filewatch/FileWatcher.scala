package me.scf37.filewatch

import java.nio.file.Path
import java.util.concurrent.ThreadFactory

import me.scf37.filewatch.impl.FileWatcherService

import scala.concurrent.Future

/**
  *
  * FileWatcher - watches over directory tree, notifying on changes via callback provided on watcher creation.
  *
  * To operate, FileWatcher utilizes own thread plus WatchService instance, so it must be closed when not needed anymore.
  *
  * Note on events: This class tries to guarantee that:
  * - client will get at least one (duplicates are likely!) [[ChangeEvent]] for every file created or updated
  * - client will get at least one (duplicates are possible!) [[DeleteEvent]] for every file deleted
  *
  * In general, it makes sense to pool and dedup events before processing as there could be LOTS of them.
  *
  */
trait FileWatcher {
  /**
    * Add new directory tree to watch for. Directory trees can intersect with each other. If path does not exist
    * or not a directory, this method does nothing.
    *
    * path, passed to filter, is guaranteed to be relative to 'path' parameter.
    *
    * Note - when watching multiple trees, ALL filters will be called for every changed path so
    *   filter implementation should account to that. It is important because single directory watched only once
    *   and in presence of links single directory can have multiple names.
    *
    * @param path root directory of directory tree to watch for
    * @param filter function to decide whether to notify user of event on this path
    */
  def watch(path: Path, filter: Path => Boolean = _ => true): Unit

  /**
    * returns true if this service is running. It is started by default and can be stopped either manually
    *   by calling [[close()]] or by internal error
    * @return true if watcher is operational
    */
  def isRunning: Boolean

  /**
    * Close this service, freeing used resources
    * @return future that completes when service finishes closing
    */
  def close(): Future[Unit]
}

/**
  * FileWatcher factory
  */
object FileWatcher {
  /**
    * Create new instance of FileWatcher. event listener will be called from multiple threads so sync is mandatory.
    *
    * @param listener listener for file events. see [[FileWatcherEvent]] for details on events
    * @param followLinks if true, links will be treated as directories.
    * @param onError callback called when exception is caught. It makes sense to re-check [[FileWatcher.isRunning]] to ensure service is still up
    * @param threadFactory custom thread factory for internal thread
    * @return
    */
  def apply(
    listener: FileWatcherEvent => Unit,
    followLinks: Boolean = true,
    onError: Throwable => Unit = e => e.printStackTrace(),
    threadFactory: ThreadFactory = DefaultThreadFactory
  ): FileWatcher = new FileWatcherService(
    threadFactory = threadFactory,
    onError = onError,
    listener = listener,
    followLinks = followLinks
  )

  object DefaultThreadFactory extends ThreadFactory {
    override def newThread(r: Runnable): Thread = new Thread(r) {
      setDaemon(true)
      setName("file-watcher-thread")
    }
  }
}
