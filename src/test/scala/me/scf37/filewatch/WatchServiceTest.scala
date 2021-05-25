package me.scf37.filewatch

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.TimeUnit

import org.scalatest.Assertions
import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class WatchServiceTest extends AnyFunSuite {
  test("No events when watching non-existing directory") {
    val d = new DirectoryHelper
    withWatcher { w =>
      w.watch(d / "missing directory", _ => true)
      Thread.sleep(100)
      w.assert()
    }
  }

  test("No events when watching existing directory tree") {
    val d = new DirectoryHelper
    d.file("a/b/c/file")
    withWatcher { w =>
      w.watch(d, _ => true)
      Thread.sleep(100)
      w.assert()
    }
  }

  test("create and delete file is watched") {
    val d = new DirectoryHelper
    withWatcher { w =>
      w.watch(d, _ => true)
      d.file("file")
      w.assert(ChangeEvent(d / "file"), ChangeEvent(d / "file"))
      d.rm("file")
      w.assert(DeleteEvent(d / "file"))
    }
  }

  test("create and delete directory is watched") {
    val d = new DirectoryHelper
    withWatcher { w =>
      w.watch(d, _ => true)
      d.dir("dir")
      w.assert(ChangeEvent(d / "dir"))
      d.rm("dir")
      w.assert(DeleteEvent(d / "dir"))
    }
  }

  test("watch inside new directory works") {
    val d = new DirectoryHelper
    withWatcher { w =>
      w.watch(d, _ => true)
      d.dir("dir1/dir2")
      w.assertSet(ChangeEvent(d / "dir1"), ChangeEvent(d / "dir1/dir2"))

      d.file("dir1/dir2/file")
      w.assert(ChangeEvent(d / "dir1/dir2/file"), ChangeEvent(d / "dir1/dir2/file"))
    }
  }

  test("watch inside existing directory works") {
    val d = new DirectoryHelper
    d.dir("dir")
    withWatcher { w =>
      w.watch(d, _ => true)

      d.file("dir/file")
      w.assert(ChangeEvent(d / "dir/file"), ChangeEvent(d / "dir/file"))
    }
  }

  test("re-creating existing directory does not break watch") {
    val d = new DirectoryHelper
    d.dir("dir")
    withWatcher { w =>
      w.watch(d, _ => true)

      d.rm("dir")
      d.dir("dir")
      w.assert(DeleteEvent(d / "dir"), ChangeEvent(d / "dir"))

      d.file("dir/file")
      w.assert(ChangeEvent(d / "dir/file"), ChangeEvent(d / "dir/file"))
    }
  }

  test("watch on directory link works and returns path via link") {
    val d = new DirectoryHelper
    d.dir("dir")
    d.ln("ldir", "dir")
    withWatcher { w =>
      w.watch(d / "ldir", _ => true)
      d.file("dir/file")

      w.assert(ChangeEvent(d / "ldir/file"), ChangeEvent(d / "ldir/file"))
    }
  }

  test("creating link in watched directory watches linked directory as well") {
    val d = new DirectoryHelper
    d.dir("dir")
    d.dir("dir2")
    withWatcher { w =>
      w.watch(d / "dir", _ => true)
      d.ln("dir/ldir2", "dir2")

      w.assert(ChangeEvent(d / "dir/ldir2"))

      d.file("dir2/file")
      w.assert(ChangeEvent(d / "dir/ldir2/file"), ChangeEvent(d / "dir/ldir2/file"))
    }
  }

  test("creating recursive links handled correctly") {
    val d = new DirectoryHelper
    d.dir("dir/dir2")
    withWatcher { w =>
      w.watch(d, _ => true)
      d.ln("dir/dir2/ldir", "dir")

      w.assertContains(ChangeEvent(d / "dir/dir2/ldir"))

      //adding recursive link to already watched directory will always produce additional/junk events
      //that is because we never know for sure whether linked directory is already watched or not
      d.file("dir/file")
      w.assertContains(ChangeEvent(d / "dir/file"))
    }
  }

  test("existing recursive links handled correctly") {
    val d = new DirectoryHelper
    d.dir("dir/dir2")
    d.ln("dir/dir2/ldir", "dir")
    withWatcher { w =>
      w.watch(d, _ => true)
      d.file("dir/file")
      w.assert(ChangeEvent(d / "dir/file"), ChangeEvent(d / "dir/file"))
    }
  }

  test("notification works for multiple files") {
    val d = new DirectoryHelper
    withWatcher { w =>
      w.watch(d, p => {
        p.toString.endsWith("2")
        true
      })
      d.file("file1")
      d.file("file2")
      d.file("file3")
      w.assertSet(ChangeEvent(d / "file1"),
        ChangeEvent(d / "file2"),
        ChangeEvent(d / "file3"))
    }
  }

  test("filtering works") {
    val d = new DirectoryHelper
    withWatcher { w =>
      w.watch(d, p => {
        p.toString.endsWith("2")
      })
      d.file("dir/file1")
      d.file("dir/file2")
      d.file("dir/file3")
      d.dir("dir1")
      d.dir("dir2")
      d.dir("dir3")
      w.assertSet(ChangeEvent(d / "dir/file2"), ChangeEvent(d / "dir2"))
    }
  }

  test("filter accepts paths that include watch path (for absolute paths)") {
    val d = new DirectoryHelper
    withWatcher { w =>
      @volatile var filterPath: Path = null

      w.watch(d, p => {
        filterPath = p
        true
      })
      d.file("file")
      w.assert(ChangeEvent(d / "file"), ChangeEvent(d / "file"))
      assert(filterPath == d / "file")
    }
  }

  test("filter accepts paths that include watch path (for relative paths") {
    val d = new DirectoryHelper(Paths.get("./test-dir"))
    try {
      withWatcher { w =>
        @volatile var filterPath: Path = null
        d.dir("")
        w.watch(d, p => {
          filterPath = p
          true
        })

        d.file("file")
        w.assert(ChangeEvent(d / "file"), ChangeEvent(d / "file"))
        assert(filterPath == d / "file")
      }
    } finally {
      d.rm("")
    }
  }

  test("FileWatcher.close() completes") {
    val w = FileWatcher(e => ())
    w.watch(Paths.get("."))
    Await.ready(w.close(), Duration(1, TimeUnit.SECONDS))
  }

  test("when watching unrelated directories, filters are applied only to their paths") {
    val d = new DirectoryHelper
    d.dir("dir1")
    d.dir("dir2")
    withWatcher { w =>
      w.watch(d / "dir1", p => {
        p.toString.endsWith("1")
      })

      w.watch(d / "dir2", p => {
        p.toString.endsWith("2")
      })

      d.file("dir1/file1")
      d.file("dir1/file2")
      d.file("dir2/file1")
      d.file("dir2/file2")

      w.assertSet(ChangeEvent(d / "dir1/file1"), ChangeEvent(d / "dir2/file2"))
    }
  }

  test("when watching related directories, filters are applied only to their paths") {
    val d = new DirectoryHelper
    d.dir("dir2")
    withWatcher { w =>
      w.watch(d , p => {
        p.toString.endsWith("1")
      })

      w.watch(d / "dir2", p => {
        p.toString.endsWith("2")
      })

      d.file("file1")
      d.file("file2")
      d.file("dir2/file1")
      d.file("dir2/file2")

      w.assertSet(ChangeEvent(d / "file1"), ChangeEvent(d / "dir2/file2"))
    }
  }

  test("when watching related directories, filters are applied only to their paths (links)") {
    val d = new DirectoryHelper
    d.dir("dir1")
    d.dir("dir2")
    d.ln("dir2/root", "dir1")
    // /dir1/file1 AKA /dir2/root/file1
    // /dir1/file2 AKA /dir2/root/file2
    // /dir2/file1
    // /dir2/file2

    withWatcher { w =>
      w.watch(d / "dir1" , p => {
        p.toString.endsWith("1")
      })

      w.watch(d / "dir2", p => {
        p.toString.endsWith("2")
      })

      d.file("dir1/file1")
      d.file("dir1/file2")
      d.file("dir2/file1")
      d.file("dir2/file2")

      w.assertSet(ChangeEvent(d / "dir1/file1"), ChangeEvent(d / "dir2/file2"), ChangeEvent(d / "dir2/root/file2"))
    }
  }


  private[this] def withWatcher(body: TestWatcher => Unit) = {
    val tw = new TestWatcher
    try {
      body(tw)
    } finally {
      tw.close()
    }
  }

  private[this] class DirectoryHelper(val root: Path = Files.createTempDirectory("file-watcher-test")) {


    def /(p: Path) = root.resolve(p)
    def /(p: String) = root.resolve(p)

    def dir(path: String): Unit = {
      var parent = this / path

      if (Files.isDirectory(parent) || Files.isSymbolicLink(parent)) return

      //find existing root
      while (parent.getNameCount > 0 && !Files.exists(parent)) parent = parent.getParent

      val missingPart = parent.relativize(this / path)

      for (i <- 0 until missingPart.getNameCount) {
        parent = parent.resolve(missingPart.getName(i))
        Files.createDirectory(parent)
      }
    }

    def file(path: String): Unit = {
      if (path.contains("/")) {
        dir((this / path).getParent.toString)
      }
      Files.write(this / path, "hello".getBytes, StandardOpenOption.CREATE)
    }

    def ln(linkName: String, target: String): Unit = {
      Files.createSymbolicLink(this / linkName, this / target)
    }

    def rm(path: String): Unit = {
      val p = this / path

      if (Files.isDirectory(p)) {
        Files.walkFileTree(p, new SimpleFileVisitor[Path]() {
          override def visitFile(file: Path, attrs: BasicFileAttributes) = {
            Files.delete(file)
            FileVisitResult.CONTINUE
          }

          override def postVisitDirectory(dir: Path, exc: IOException) = {
            Files.delete(dir)
            FileVisitResult.CONTINUE
          }
        })
      } else
        Files.deleteIfExists(p)
    }
  }

  private[this] implicit def toPath(helper: DirectoryHelper): Path = helper.root

  private[this] class TestWatcher {
    private[this] var events = Seq.empty[FileWatcherEvent]
    private[this] val lock = new Object

    private val fw = FileWatcher(e => {
      lock.synchronized {
        events :+= e
        lock.notify()
      }
    })

    def assert(expectedEvents: FileWatcherEvent*): Unit = {
      if (!await(events.toSeq == expectedEvents.toSeq)) {
        Assertions.assert(events.toSeq == expectedEvents.toSeq)
      }
    }

    def assertSet(expectedEvents: FileWatcherEvent*): Unit = {
      if (!await(events.toSet == expectedEvents.toSet)) {
        Assertions.assert(events.toSet == expectedEvents.toSet)
      }
    }

    def assertContains(expectedEvents: FileWatcherEvent*): Unit = {
      def contains[T](seq: Seq[T], subseq: Seq[T]): Boolean = {
        for (i <- 0 to (seq.length - subseq.length)) {
          if (seq.slice(i, i + subseq.length) == subseq) return true
        }
        false
      }

      if (!await(contains(events, expectedEvents))) {
        Assertions.assert(contains(events, expectedEvents), events.toString + " did not contain " + expectedEvents.toString)
      }
    }

    def await(cond: => Boolean): Boolean = {
      val time = System.currentTimeMillis()

      while (System.currentTimeMillis() - time < 1000) {
        lock.synchronized {
          if (cond) {
            events = Seq.empty
            return true
          }
          lock.wait(10)
        }
      }

      false
    }

    def watch(path: Path, filter: Path => Boolean): Unit = fw.watch(path, filter)

    def close(): Unit = fw.close()
  }
}
