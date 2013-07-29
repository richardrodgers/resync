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

  def read(in: InputStream): ResourceMap = {
    val xsr = XMLInputFactory.newInstance().createXMLStreamReader(in, "utf-8")
    var root = "none"
    var metadata: Map[String, String] = null
    var links = List[Link]()
    var resources = List[Resource]()
    while (xsr.hasNext) {
      xsr.next match {
        case START_ELEMENT => if ("none".equals(root)) root = xsr.getLocalName 
                              else if ("md".equals(xsr.getLocalName)) metadata = procAttrs(xsr)
                              else if ("ln".equals(xsr.getLocalName)) links = Link(procAttrs(xsr)) :: links
                              else if ("url".equals(xsr.getLocalName) || "sitemap".equals(xsr.getLocalName)) 
                                       resources = procResource(xsr.getLocalName, xsr) :: resources
        case END_DOCUMENT => xsr.close
        case _ => // ignore
      }
    }

    val fromOpt: Option[Date] = metadata.get("from").map(W3CDateTime.parse(_))
    val untilOpt: Option[Date] = metadata.get("until").map(W3CDateTime.parse(_))

    (metadata.get("capability").get, root) match {
      case ("resourcelist", "urlset") => ResourceList(fromOpt.get, links, resources)
      case ("resourcelist", "sitemapindex") => ResourceListIndex(fromOpt.get, links, resources)
      case ("changelist", "urlset") => ChangeList(fromOpt.get, untilOpt.get, links, resources)
      case ("changelist", "sitemapindex") => ChangeListIndex(fromOpt.get, untilOpt.get, links, resources)
      case ("capabilitylist", "urlset") => CapabilityList(links, resources)
      case ("capabilitylist", "sitemapindex") => CapabilityListIndex(fromOpt.get, links, resources)
      case ("resourcesync", "urlset") => Description(links, resources)
      case ("resourcesync", "sitemapindex") => DescriptionIndex(fromOpt.get, links, resources)
      case ("resourcedump", "urlset") => ResourceDump(fromOpt.get, links, resources)
      case ("resourcedump", "sitemapindex") => ResourceDumpIndex(fromOpt.get, links, resources)
      case ("resourcedump-manifest", "urlset") => ResourceDumpManifest(fromOpt.get, links, resources)
      case ("changedump", "urlset") => ChangeDump(fromOpt.get, untilOpt.get, links, resources)
      case ("changedump", "sitemapindex") => ChangeDumpIndex(fromOpt.get, untilOpt.get, links, resources)
      case ("changedump-manifest", "urlset") => ChangeDumpManifest(fromOpt.get, untilOpt.get, links, resources)
      case _ => throw new IllegalStateException("Unknown document type: " + root)
    }
  }

  private def procResource(tag: String, xsr: XMLStreamReader): Resource = {

    var location: URL = null
    var lastModified: Option[Date] = None
    var changeFrequency: Option[Frequency] = None
    var priority: Option[Double] = None
    var metadata: Option[Metadata] = None
    var links = List[Link]()
    var endResource = false
    while (xsr.hasNext && ! endResource) {
      xsr.next match {
        case START_ELEMENT => if ("loc".equals(xsr.getLocalName)) location = new URL(xsr.getElementText) 
                              else if ("lastmod".equals(xsr.getLocalName)) lastModified = Some(W3CDateTime.parse(xsr.getElementText))
                              else if ("changefreq".equals(xsr.getLocalName)) changeFrequency = Some(Frequency.withName(xsr.getElementText))
                              else if ("priority".equals(xsr.getLocalName)) priority = Some(xsr.getElementText.toDouble)
                              else if ("md".equals(xsr.getLocalName)) metadata = Some(Metadata(procAttrs(xsr)))
                              else if ("ln".equals(xsr.getLocalName)) links = Link(procAttrs(xsr)) :: links
        case END_ELEMENT => if (tag.equals(xsr.getLocalName)) endResource = true
        case _ => // ignore
      }
    }

    if ("url".equals(tag)) URLResource(location, lastModified, changeFrequency, priority, metadata, links) else
                           SiteResource(location, lastModified, None, None, metadata, links)
  }

  private def procAttrs(xsr: XMLStreamReader): Map[String, String] = {
    var attrs = Map[String, String]()
    for (i <- 0 to xsr.getAttributeCount) {
      attrs = attrs + (xsr.getAttributeLocalName(i) -> xsr.getAttributeValue(i))
    }
    attrs
  }
}
