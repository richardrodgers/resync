/**
  * Copyright 2013 MIT Libraries
  * Licensed under: http://www.apache.org/licenses/LICENSE-2.0
  */

package edu.mit.lib.resync

import java.net.URL
import java.text.{DateFormat, SimpleDateFormat}
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

case class Metadata(attrs: Map[String, String])
case class Link(attrs: Map[String, String])

sealed abstract class Resource {
  val location: URL
  val lastModified: Option[Date]
  val changeFrequency: Option[Frequency]
  val priority: Option[Double]
  val metadata: Option[Metadata]
  val links: Seq[Link]
}

case class URLResource(location: URL, lastModified: Option[Date] = None, changeFrequency: Option[Frequency] = None,
                       priority: Option[Double] = None, metadata: Option[Metadata] = None, links: Seq[Link] = List()) extends Resource

case class SiteResource(location: URL, lastModified: Option[Date], changeFrequency: Option[Frequency],
                        priority: Option[Double], metadata: Option[Metadata], links: Seq[Link]) extends Resource

case class Namespace(prefix: String, uri: String)

object RSNamespace {
  val Sitemap = Namespace("", "http://www.sitemaps.org/schemas/sitemap/0.9")
  val ResourceSync = Namespace("rs", "http://www.openarchives.org/rs/terms/")
  val Atom = Namespace("atom", "http://www.w3.org/2005/Atom")
  //val fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
}

object W3CDateTime {
  val formats = Map(4 ->  new SimpleDateFormat("yyyy"),
                    7 ->  new SimpleDateFormat("yyyy-MM"),
                    10 -> new SimpleDateFormat("yyyy-MM-DD"),
                    17 -> new SimpleDateFormat("yyyy-MM-DD'T'hh:mmZ"),
                    20 -> new SimpleDateFormat("yyyy-MM-DD'T'hh:mm:ssZ"),
                    22 -> new SimpleDateFormat("yyyy-MM-DD'T'hh:mm:ss.sZ"))
  def format(date: Date, length: Int = 20) = formats.get(length).get.format(date)
  def parse(dtString: String) = formats.get(dtString.length).get.parse(dtString)
}
