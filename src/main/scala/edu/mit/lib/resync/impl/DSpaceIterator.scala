/**
  * Copyright (C) 2013 MIT Libraries
  * Licensed under: http://www.apache.org/licenses/LICENSE-2.0
  */

package edu.mit.lib.resync.impl

import java.io.{Closeable, InputStream}
import java.net.URL
import java.util.Date

import org.dspace.content.{Bitstream, Collection, Item, ItemIterator}
import org.dspace.core.{ConfigurationManager, Context}

import edu.mit.lib.resync._
import edu.mit.lib.resync.Frequency._

import DSpaceIterator._

/**
 * DSpaceIterator is a ResourceIterator for DSpace items.
 * It iterates over an entire respository, or a given collection.
 * The resources it exposes are item bitstreams, and serializations
 * of item metadata in various formats. Resource model follows that of
 * Richard Jones at: https://github.com/CottageLabs/DSpaceResourceSync
 *
 * @author richardrodgers
 */

class BitstreamDescription(item: Item, bitstream: Bitstream) extends ResourceDescription {
  def location: URL = bitstreamURL(item, bitstream)
  def name: Option[String] = Some(bitstream.getName)
  def modified: Option[Date] = Some(item.getLastModified)
  def checksum: Option[String] = Some(bitstream.getChecksumAlgorithm.toLowerCase + ":" + bitstream.getChecksum)
  def size: Option[Long] = Some(bitstream.getSize)
  def mimetype: Option[String] = Some(bitstream.getFormat.getMIMEType)
  def frequency: Option[Frequency] = None
  def priority: Option[Double] = None
  def links: Seq[Link] = collectionLinks(item) ++ metadataLinks
  def change: Option[String] = Some("created")
  def content: Option[InputStream] = Some(bitstream.retrieve)

  private def metadataLinks: Seq[Link] = mdformats.map{ fmt => Link(metadataURL(item, fmt.prefix), "describedby", Map()) }
}

class MetadataDescription(item: Item, bitstreams: List[Bitstream], format: MetadataFormat) extends ResourceDescription {
  def location: URL = metadataURL(item, format.prefix)
  def name: Option[String] = None
  def modified: Option[Date] = Some(item.getLastModified)
  def checksum: Option[String] = None
  def size: Option[Long] = None
  def mimetype: Option[String] = Some(format.mimeType)
  def frequency: Option[Frequency] = None
  def priority: Option[Double] = None
  def links: Seq[Link] = collectionLinks(item) ++ bitstreamLinks ++ alternateLinks
  def change: Option[String] = Some("created")
  def content: Option[InputStream] = None

  private def bitstreamLinks: Seq[Link] = bitstreams.map{ bs => Link(bitstreamURL(item, bs), "describes", Map()) }

  private def alternateLinks: Seq[Link] = mdformats.filter(_.prefix != format.prefix).map{ fmt => Link(metadataURL(item, fmt.prefix), "alternate", Map()) }
}

class ItemResourceIterator(item: Item) extends Iterator[ResourceDescription] {
  val bitstreams = item.getBundles("ORIGINAL")(0).getBitstreams.toList
  var bsIndex = 0
  var mdIndex = 0

  def hasNext = moreBitstreams || moreMetadata

  def next = {
    if (hasNext) {
      if (moreBitstreams) {
        bsIndex += 1
        new BitstreamDescription(item, bitstreams(bsIndex - 1))
      } else {
        mdIndex += 1
        new MetadataDescription(item, bitstreams, mdformats(mdIndex - 1))
      }
    } else {
      null
    }
  }

  private def moreBitstreams = bsIndex < bitstreams.length
  private def moreMetadata = mdIndex < mdformats.length
}

class DSpaceIterator(rootDir: String, resSetName: String = "") extends ResourceIterator {
  var baseURL: String = null
  val context = new Context
  val itIter: ItemIterator = Item.findAll(context)
  var rsIter: ItemResourceIterator = null
  def setBaseURL(url: String) = {baseURL = url}
  def resourceSet = resSetName
  def setDescURL = None
  def iterator = new Iterator[ResourceDescription] {
    def hasNext = (rsIter != null && rsIter.hasNext) || itIter.hasNext
    def next = {
      if (rsIter != null && rsIter.hasNext) rsIter.next
      else if (itIter.hasNext) { 
        rsIter = new ItemResourceIterator(itIter.next)
        next
      } else null
    }
  }
  def close = {
    itIter.close
    context.abort
  }
}

case class MetadataFormat(prefix: String, mimeType: String)

object DSpaceIterator {

  val baseUrl = ConfigurationManager.getProperty("dspace.url")
  val baseMDUrl = ConfigurationManager.getProperty("resourcesync", "base-url")

  val mdformats = List(MetadataFormat("dc", "application/xml"), MetadataFormat("mets", "application/xml"))

  def bitstreamURL(item: Item, bitstream: Bitstream): URL = {
    val sb: StringBuilder = new StringBuilder(baseUrl)
    if (! baseUrl.endsWith("/")) sb.append("/")
    sb.append("bitstream/").append(item.getHandle).append("/")
    sb.append(bitstream.getSequenceID).append("/").append(bitstream.getName)
    new URL(sb.toString)
  }

  def metadataURL(item: Item, format: String): URL = {
    val sb: StringBuilder = new StringBuilder(baseMDUrl)
    if (! baseMDUrl.endsWith("/")) sb.append("/")
    sb.append("resource/").append(item.getHandle).append("/")
    sb.append(format)
    new URL(sb.toString)
  }

  def collectionLinks(item: Item): Seq[Link] = {
    item.getCollections.toList.map{ coll => Link(new URL(baseUrl + "/" + coll.getHandle), "collection", Map()) }
  }

  def apply(rootDir: String, resSetName: String = "") = new DSpaceIterator(rootDir, resSetName)
}
