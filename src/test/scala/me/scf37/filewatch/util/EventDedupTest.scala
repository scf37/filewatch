package me.scf37.filewatch.util

import java.nio.file.Paths

import me.scf37.filewatch.ChangeEvent
import me.scf37.filewatch.DeleteEvent
import me.scf37.filewatch.DesyncEvent
import me.scf37.filewatch.FileWatcherEvent
import org.scalatest.funsuite.AnyFunSuite

class EventDedupTest extends AnyFunSuite {
  test("test dedup") {
    @volatile var events = Seq.empty[FileWatcherEvent]

    val dedup = new EventDedup(es => events = es, dedupFlushDelayMs = 40)

    dedup(ChangeEvent(Paths.get(".")))
    dedup(ChangeEvent(Paths.get("./.")))
    dedup(ChangeEvent(Paths.get("/1")))
    dedup(ChangeEvent(Paths.get(".")))
    dedup(ChangeEvent(Paths.get("/1")))

    dedup(DeleteEvent(Paths.get(".")))
    dedup(DeleteEvent(Paths.get("./.")))
    dedup(DeleteEvent(Paths.get(".")))
    dedup(DeleteEvent(Paths.get("/1")))
    dedup(DeleteEvent(Paths.get("/1")))

    dedup(DesyncEvent)
    dedup(DesyncEvent)

    Thread.sleep(100)

    assert(events.toSet == Set(
      ChangeEvent(Paths.get(".").toRealPath()),
      ChangeEvent(Paths.get("/1")),
      DeleteEvent(Paths.get(".").toRealPath()),
      DeleteEvent(Paths.get("/1")),
      DesyncEvent))


  }
}
