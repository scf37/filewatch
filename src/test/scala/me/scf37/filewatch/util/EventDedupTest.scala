package me.scf37.filewatch.util

import java.nio.file.Paths

import me.scf37.filewatch.ChangeEvent
import me.scf37.filewatch.DeleteEvent
import me.scf37.filewatch.FileWatcherEvent
import org.scalatest.FunSuite

/**
  * Created by asm on 24.10.16.
  */
class EventDedupTest extends FunSuite {
  test("test dedup") {
    @volatile var events = Seq.empty[FileWatcherEvent]

    val dedup = new EventDedup(es => events = es, dedupFlushDelayMs = 40)

    dedup(ChangeEvent(Paths.get(".")))
    dedup(ChangeEvent(Paths.get("/1")))
    dedup(ChangeEvent(Paths.get(".")))
    dedup(ChangeEvent(Paths.get("/1")))

    dedup(DeleteEvent(Paths.get(".")))
    dedup(DeleteEvent(Paths.get(".")))
    dedup(DeleteEvent(Paths.get("/1")))
    dedup(DeleteEvent(Paths.get("/1")))

    Thread.sleep(1000)

    assert(events.toSet == Set(
      ChangeEvent(Paths.get(".")),
      ChangeEvent(Paths.get("/1")),
      DeleteEvent(Paths.get(".")),
      DeleteEvent(Paths.get("/1"))))


  }
}
