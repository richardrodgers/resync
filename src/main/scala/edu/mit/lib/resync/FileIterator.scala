/**
  * Copyright 2013 MIT Libraries
  * Licensed under: http://www.apache.org/licenses/LICENSE-2.0
  */

package edu.mit.lib.resync

import java.io.{Closeable, InputStream}
import java.math.BigInteger
import java.net.URL
import java.nio.file.{DirectoryStream, Files, Path, Paths}
import java.security.{DigestInputStream, MessageDigest}
import java.util.Date

import Frequency._

/**
 * FileIterator is a ResourceIterator for files in a file system,
 * It iterates over all files in passed directory, using OS-supplied
 * values for size, modification date, etc
 *
 * @author richardrodgers
 */

class FileDescription(path: Path, baseURL: String, resSet: String) extends ResourceDescription {
  def location: URL = composeURL
  def name: Option[String] = Some(path.getFileName.toString)
  def modified: Option[Date] = Some(new Date(Files.getLastModifiedTime(path).toMillis))
  def checksum: Option[String] = Some(checksum(content.get))
  def size: Option[Long] = Some(Files.size(path))
  def mimetype: Option[String] = None
  def frequency: Option[Frequency] = None
  def priority: Option[Double] = None
  def change: Option[String] = Some("created")
  def content: Option[InputStream] = Some(Files.newInputStream(path))

  private def composeURL: URL = {
    val sb: StringBuilder = new StringBuilder(baseURL)
    if (! baseURL.endsWith("/")) sb.append("/")
    if (resSet.length > 0) sb.append(resSet).append("/")
    sb.append(path.getFileName.toString)
    new URL(sb.toString)
  }

  private def checksum(stream: InputStream): String = {
    val md: MessageDigest = MessageDigest.getInstance("MD5")
    try {
      val dis: DigestInputStream = new DigestInputStream(stream, md)
      while (dis.read != -1) {}
    } finally {
      stream.close
    }
    "md5:" + new BigInteger(1, md.digest()).toString(16)
  }
}

class FileIterator(rootDir: String, resSetName: String = "") extends ResourceIterator {
  var baseURL: String = null
  val dirFilter = new DirectoryStream.Filter[Path] {
    def accept(dir: Path): Boolean = ! Files.isDirectory(dir)
  }
  val dirStream = Files.newDirectoryStream(Paths.get(rootDir), dirFilter)
  val dsIter: java.util.Iterator[Path] = dirStream.iterator
  def setBaseURL(url: String) = {baseURL = url}
  def resourceSet = resSetName
  def setDescURL = None
  def iterator = new Iterator[ResourceDescription] {
    def hasNext = dsIter.hasNext
    def next = new FileDescription(dsIter.next, baseURL, resSetName)
  }
  def close = dirStream.close
}

object FileIterator {
  def apply(rootDir: String, resSetName: String = "") = new FileIterator(rootDir, resSetName)
}
