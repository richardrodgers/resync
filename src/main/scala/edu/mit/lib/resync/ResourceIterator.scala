/**
  * Copyright 2013 MIT Libraries
  * Licensed under: http://www.apache.org/licenses/LICENSE-2.0
  */

package edu.mit.lib.resync

import java.io.{Closeable, InputStream}
import java.net.URL
import java.util.Date

import Frequency._

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
  def mimetype: Option[String]
  def frequency: Option[Frequency]
  def priority: Option[Double]
  def change: Option[String]
  def content: Option[InputStream]
}

trait ResourceIterator extends Closeable {
  def setBaseURL(url: String)
  def resourceSet: String
  def setDescURL: Option[String]
  def iterator: Iterator[ResourceDescription]
}
