/**
  * Copyright 2013 MIT Libraries
  * Licensed under: http://www.apache.org/licenses/LICENSE-2.0
  */

package edu.mit.lib.resync

import java.util.Date

/**
 * ResourceMap is the base trait for all ResourceSync document types
 *
 * @author richardrodgers
 */

object Capability extends Enumeration {
  type Capability = Value
  val resourcesync = Value("resourcesync")
  val capabilitylist = Value("capabilitylist")
  val resourcelist = Value("resourcelist")
  val changelist = Value("changelist")
  val resourcedump = Value("resourcedump")
  val resourcedumpmanifest = Value("resourcedump-manifest")
  val changedump = Value("changedump")
  val changedumpmanifest = Value("changedump-manifest")
}

import Capability._

sealed trait ResourceMap {
  def mapName: String
  def resourceName: String
  def capability: Capability
  def validity: Option[Date]
  def expiry: Option[Date]
  def links: Seq[Link]
  def resources: Seq[Resource]
  def listLike = List(resourcelist, resourcedump, resourcedumpmanifest).contains(capability)
}

trait UrlSet {
  def mapName = "urlset"
  def resourceName = "url"
}

trait MapIndex {
  def mapName = "sitemapindex"
  def resourceName = "sitemap"
}

case class Description(links: Seq[Link], resources: Seq[Resource]) extends ResourceMap with UrlSet {
  def capability = resourcesync
  def validity = None
  def expiry = None
   def withResource(res: Resource): Description = {
    if (resources.exists(_.location == res.location)) this else Description(links, res +: resources)
  }
}
case class DescriptionIndex(from: Date, links: Seq[Link], resources: Seq[Resource]) extends ResourceMap with MapIndex {
  def capability = resourcesync
  def validity = Some(from)
  def expiry = None
}
case class CapabilityList(links: Seq[Link], resources: Seq[Resource]) extends ResourceMap with UrlSet {
  def capability = capabilitylist
  def validity = None
  def expiry = None
  def withResource(res: Resource): CapabilityList = {
    if (resources.exists(_.location == res.location)) this else CapabilityList(links, res +: resources)
  }
}
case class CapabilityListIndex(from: Date, links: Seq[Link], resources: Seq[Resource]) extends ResourceMap with MapIndex {
  def capability = capabilitylist
  def validity = Some(from)
  def expiry = None
}
case class ResourceList(from: Date = new Date, links: Seq[Link] = List(), resources: Seq[Resource]) extends ResourceMap with UrlSet {
  def capability = resourcelist
  def validity = Some(from)
  def expiry = None
}
case class ResourceListIndex(from: Date, links: Seq[Link], resources: Seq[Resource]) extends ResourceMap with MapIndex {
  def capability = resourcelist
  def validity = Some(from)
  def expiry = None
}
case class ResourceDump(from: Date, links: Seq[Link], resources: Seq[Resource]) extends ResourceMap with UrlSet {
  def capability = resourcedump
  def validity = Some(from)
  def expiry = None
}
case class ResourceDumpIndex(from: Date, links: Seq[Link], resources: Seq[Resource]) extends ResourceMap with MapIndex {
  def capability = resourcedump
  def validity = Some(from)
  def expiry = None
}
case class ResourceDumpManifest(from: Date, links: Seq[Link], resources: Seq[Resource]) extends ResourceMap with UrlSet {
  def capability = resourcedumpmanifest
  def validity = Some(from)
  def expiry = None
}
case class ChangeList(from: Date, until: Date, links: Seq[Link], resources: Seq[Resource]) extends ResourceMap with UrlSet {
  def capability = changelist
  def validity = Some(from)
  def expiry = Some(until)
}
case class ChangeListIndex(from: Date, until: Date, links: Seq[Link], resources: Seq[Resource]) extends ResourceMap with MapIndex {
  def capability = changelist
  def validity = Some(from)
  def expiry = Some(until)
}
case class ChangeDump(from: Date, until: Date, links: Seq[Link], resources: Seq[Resource]) extends ResourceMap with UrlSet {
  def capability = changedump
  def validity = Some(from)
  def expiry = Some(until)
}
case class ChangeDumpIndex(from: Date, until: Date, links: Seq[Link], resources: Seq[Resource]) extends ResourceMap with MapIndex {
  def capability = changedump
  def validity = Some(from)
  def expiry = Some(until)
}
case class ChangeDumpManifest(from: Date, until: Date, links: Seq[Link], resources: Seq[Resource]) extends ResourceMap with UrlSet {
  def capability = changedumpmanifest
  def validity = Some(from)
  def expiry = Some(until)
}
