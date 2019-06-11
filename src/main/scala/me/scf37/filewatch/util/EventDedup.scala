package me.scf37.filewatch.util

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

import me.scf37.filewatch.ChangeEvent
import me.scf37.filewatch.DeleteEvent
import me.scf37.filewatch.DesyncEvent
import me.scf37.filewatch.FileWatcherEvent

import scala.collection.mutable
import scala.util.Try

/**
  * Event deduplicator, use as listener when creating [[me.scf37.filewatch.FileWatcher]]
  */
class EventDedup(
  listener: Seq[FileWatcherEvent] => Unit,
  onError: Throwable => Unit = e => e.printStackTrace(),
  timer: ScheduledExecutorService = EventDedup.defaultExecutor,
  dedupFlushDelayMs: Long = 100,
  normalizeEvents: Boolean = true
) extends (FileWatcherEvent => Unit) {

  val events = mutable.LinkedHashSet.empty[FileWatcherEvent]
  var isFlushScheduled = false

  private[this] def scheduleFlush() = {
    isFlushScheduled = true

    timer.schedule(new Runnable {
      override def run(): Unit = {
        synchronized {
          isFlushScheduled = false
        }

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
    }, dedupFlushDelayMs, TimeUnit.MILLISECONDS)
  }

  override def apply(event: FileWatcherEvent): Unit = synchronized {
    events += (if (normalizeEvents) normalize(event) else event)
    if (!isFlushScheduled) {
      scheduleFlush()
    }
  }

  private[this] def normalize(event: FileWatcherEvent): FileWatcherEvent = event match {
    case ChangeEvent(p) => ChangeEvent(Try(p.toRealPath()).getOrElse(p))
    case DeleteEvent(p) => DeleteEvent(Try(p.toRealPath()).getOrElse(p))
    case DesyncEvent => DesyncEvent
  }
}

object EventDedup {
  private lazy val defaultExecutor = Executors.newSingleThreadScheduledExecutor()
}