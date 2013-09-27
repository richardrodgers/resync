/**
  * Copyright 2013 MIT Libraries
  * Licensed under: http://www.apache.org/licenses/LICENSE-2.0
  */

package edu.mit.lib.resync

import java.io.{Closeable, InputStream}
import java.net.URL
import java.util.Date

/**
 * ResourceIterator describes a class that can enumerate and describe resources.
 * The resource description must include a resource URL, all other attributes optional.
 *
 * @author richardrodgers
 */

trait ResourceDescription {
  def location: URL
  def name: Option[String]
  def modified: Option[Date]
  def checksum: Option[String]
  def size: Option[Long]
  def content: Option[InputStream]
  def change: Option[String]
}

trait ResourceIterator extends Closeable {
  def setBaseURL(url: String)
  def resourceSet: String
  def iterator: Iterator[ResourceDescription]
}
