/**
  * Copyright 2013 MIT Libraries
  * Licensed under: http://www.apache.org/licenses/LICENSE-2.0
  */

package edu.mit.lib.resync

import java.io.InputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import javax.xml.stream.{XMLInputFactory, XMLStreamReader}
import javax.xml.stream.XMLStreamConstants._

import Frequency._

/**
 * Object to read (deserialize, parse) an XML document to a ResourceMap
 *
 * @author richardrodgers
 */
object XMLResourceMapReader {

  def read(in: InputStream): ResourceMap = read(in, true)

  /**
   * Reads and parses an input stream to create a resource map
   *
   * @param in the input stream to read
   * @param full if true, parse entire document, else only read document-level attributes
   */
  def read(in: InputStream, full: Boolean): ResourceMap = {
    val xsr = XMLInputFactory.newInstance().createXMLStreamReader(in, "utf-8")
    var root = "none"
    var metadata: Map[String, String] = null
    var links = List[Link]()
    var resources = List[Resource]()
    var reading = true
    while (xsr.hasNext && reading) {
      xsr.next match {
        case START_ELEMENT => if ("none".equals(root)) root = xsr.getLocalName 
                              else if ("md".equals(xsr.getLocalName)) metadata = procAttrs(xsr)
                              else if ("ln".equals(xsr.getLocalName)) links = procLink(xsr) :: links
                              else if ("url".equals(xsr.getLocalName) || "sitemap".equals(xsr.getLocalName)) 
                                       if (full) resources = procResource(xsr.getLocalName, xsr) :: resources
                                       else reading = false
        //case END_DOCUMENT => xsr.close
        case _ => // ignore
      }
    }
    xsr.close

    val from: Date = if (metadata.get("from").isDefined) metadata.get("from").map(W3CDateTime.parse(_)).get
                     else if (metadata.get("at").isDefined) metadata.get("at").map(W3CDateTime.parse(_)).get
                     else null
    val until: Date = if (metadata.get("until").isDefined) metadata.get("until").map(W3CDateTime.parse(_)).get 
                      else if (metadata.get("completed").isDefined) metadata.get("completed").map(W3CDateTime.parse(_)).get
                      else null

    (metadata.get("capability").get, root) match {
      case ("resourcelist", "urlset") => ResourceList(from, links, resources)
      case ("resourcelist", "sitemapindex") => ResourceListIndex(from, links, resources)
      case ("changelist", "urlset") => ChangeList(from, until, links, resources)
      case ("changelist", "sitemapindex") => ChangeListIndex(from, until, links, resources)
      case ("capabilitylist", "urlset") => CapabilityList(links, resources)
      case ("capabilitylist", "sitemapindex") => CapabilityListIndex(from, links, resources)
      case ("resourcesync", "urlset") => Description(links, resources)
      case ("resourcesync", "sitemapindex") => DescriptionIndex(from, links, resources)
      case ("resourcedump", "urlset") => ResourceDump(from, links, resources)
      case ("resourcedump", "sitemapindex") => ResourceDumpIndex(from, links, resources)
      case ("resourcedump-manifest", "urlset") => ResourceDumpManifest(from, links, resources)
      case ("changedump", "urlset") => ChangeDump(from, until, links, resources)
      case ("changedump", "sitemapindex") => ChangeDumpIndex(from, until, links, resources)
      case ("changedump-manifest", "urlset") => ChangeDumpManifest(from, until, links, resources)
      case _ => throw new IllegalStateException("Unknown document type: " + root)
    }
  }

  private def procResource(tag: String, xsr: XMLStreamReader): Resource = {
    var location: URL = null
    var lastModified: Option[Date] = None
    var changeFrequency: Option[Frequency] = None
    var priority: Option[Double] = None
    var metadata: Option[Map[String, String]] = None
    var links = List[Link]()
    var endResource = false
    while (xsr.hasNext && ! endResource) {
      xsr.next match {
        case START_ELEMENT => if ("loc".equals(xsr.getLocalName)) location = new URL(xsr.getElementText) 
                              else if ("lastmod".equals(xsr.getLocalName)) lastModified = Some(W3CDateTime.parse(xsr.getElementText))
                              else if ("changefreq".equals(xsr.getLocalName)) changeFrequency = Some(Frequency.withName(xsr.getElementText))
                              else if ("priority".equals(xsr.getLocalName)) priority = Some(xsr.getElementText.toDouble)
                              else if ("md".equals(xsr.getLocalName)) metadata = Some(procAttrs(xsr))
                              else if ("ln".equals(xsr.getLocalName)) links = procLink(xsr) :: links
        case END_ELEMENT => if (tag.equals(xsr.getLocalName)) endResource = true
        case _ => // ignore
      }
    }

    if ("url".equals(tag)) URLResource(location, lastModified, changeFrequency, priority, metadata, links) else
                           MapResource(location, lastModified)
  }

  private def procLink(xsr: XMLStreamReader): Link = {
    var href: URL = null
    var rel: String = null
    var attrs = Map[String, String]()
    for (i <- 0 until xsr.getAttributeCount) {
      xsr.getAttributeLocalName(i) match {
        case "href" => href = new URL(xsr.getAttributeValue(i))
        case "rel" => rel = xsr.getAttributeValue(i)
        case _ => attrs = attrs + (xsr.getAttributeLocalName(i) -> xsr.getAttributeValue(i))
      }
    }
    Link(href, rel, attrs)
  }

  private def procAttrs(xsr: XMLStreamReader): Map[String, String] = {
    var attrs = Map[String, String]()
    for (i <- 0 until xsr.getAttributeCount) {
      attrs = attrs + (xsr.getAttributeLocalName(i) -> xsr.getAttributeValue(i))
    }
    attrs
  }
}
