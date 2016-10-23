package me.scf37.filewatch.util

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

import me.scf37.filewatch.FileWatcherEvent

import scala.collection.mutable

/**
  * Event deduplicator, use as listener when creating [[me.scf37.filewatch.FileWatcher]]
  */
class EventDedup(
  listener: Seq[FileWatcherEvent] => Unit,
  onError: Throwable => Unit = e => e.printStackTrace(),
  timer: ScheduledExecutorService = EventDedup.defaultExecutor,
  dedupFlushDelayMs: Int = 100
) extends (FileWatcherEvent => Unit) {

  val events = mutable.LinkedHashSet.empty[FileWatcherEvent]

  timer.scheduleWithFixedDelay(new Runnable {
    override def run(): Unit = {
      val copy = synchronized {
        val copy = events.toSeq //at least current implementation makes copy
        events.clear()
        copy
      }

      try {
        if (copy.nonEmpty) {
          listener(copy)
        }
      } catch {
        case e: Throwable => onError(e)
      }

    }
  }, dedupFlushDelayMs, dedupFlushDelayMs, TimeUnit.MILLISECONDS)

  override def apply(event: FileWatcherEvent): Unit = synchronized {
    events += event
  }
}

object EventDedup {
  private lazy val defaultExecutor = Executors.newSingleThreadScheduledExecutor()
}