package me.scf37.filewatch

import java.nio.file.Path

sealed trait FileWatcherEvent

/**
  * File have been created or changed. We cannot reliably distinguish those because:
  * - create-and-write usually produces both Create and Modify jdk events
  * - when we create new directory and files within short time period, files in
  *    new directory could be written before we listen for changes, so all we get is Create event even if files
  *    were changed after creation.
  * @param path path to changed file
  */
case class ChangeEvent(path: Path) extends FileWatcherEvent

/**
  * File have been deleted
  * @param path path to deleted file
  */
case class DeleteEvent(path: Path) extends FileWatcherEvent

/**
  * This event is emitted if we possibly lost some events,
  * i.e. captured event sequence no longer represents actual file system contents
  */
case object DesyncEvent extends FileWatcherEvent