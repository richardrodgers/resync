/**
  * Copyright 2013 MIT Libraries
  * Licensed under: http://www.apache.org/licenses/LICENSE-2.0
  */

package edu.mit.lib.resync

import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Model classes and objects, utility methods, etc
 *
 * @author richardrodgers
 */

object Frequency extends Enumeration {
  type Frequency = Value
  val always = Value("always")
  val hourly = Value("hourly")
  val daily = Value("daily")
  val weekly = Value("weekly")
  val monthy = Value("monthly")
  val yearly = Value("yearly")
  val never = Value("never")
}

import Frequency._

case class Link(href: URL, rel: String, attrs: Map[String, String])

sealed abstract class Resource {
  val location: URL
  val lastModified: Option[Date]
  val changeFrequency: Option[Frequency]
  val priority: Option[Double]
  val metadata: Option[Map[String, String]]
  val links: Seq[Link]
}

case class URLResource(location: URL, lastModified: Option[Date] = None, 
                       changeFrequency: Option[Frequency] = None,
                       priority: Option[Double] = None,
                       metadata: Option[Map[String, String]] = None,
                       links: Seq[Link] = List()) extends Resource

case class MapResource(location: URL, lastModified: Option[Date] = None,
                       metadata: Option[Map[String, String]] = None) extends Resource {
  val changeFrequency = None
  val priority = None
  val links = List()
}

case class Namespace(prefix: String, uri: String)

object RSNamespace {
  val Sitemap = Namespace("", "http://www.sitemaps.org/schemas/sitemap/0.9")
  val ResourceSync = Namespace("rs", "http://www.openarchives.org/rs/terms/")
  val Atom = Namespace("atom", "http://www.w3.org/2005/Atom")
}

object W3CDateTime {
  val formats = Map(4 ->  new SimpleDateFormat("yyyy"),
                    7 ->  new SimpleDateFormat("yyyy-MM"),
                    10 -> new SimpleDateFormat("yyyy-MM-dd"),
                    17 -> new SimpleDateFormat("yyyy-MM-dd'T'HH:mmXXX"),
                    -1 -> new SimpleDateFormat("yyyy-MM-dd'T'HH:mmXXX"),
                    20 -> new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX"),
                    25 -> new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX"),
                    22 -> new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sXXX"),
                    27 -> new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sXXX"))
  def format(date: Date, length: Int = 20) = formats.get(length).get.format(date)
  def parse(dtString: String) = {
    val slen = if (dtString.length == 22 && ! dtString.endsWith("Z")) -1 else dtString.length
    formats.get(slen).get.parse(dtString)
  }
}
