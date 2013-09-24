/**
  * Copyright 2013 MIT Libraries
  * Licensed under: http://www.apache.org/licenses/LICENSE-2.0
  */

package edu.mit.lib.resync

import java.io.{File, FileInputStream, FileOutputStream}
import java.net.URL
import java.util.Date

/**
 * ResyncBuilder is a helper class for constructing and linking
 * ResourceSync documents.
 *
 * @param docDir the root directory for builder-managed documents
 * @param baseURL the stem of the URL for all builder-managed documents
 *
 * @author richardrodgers
 */
class ResyncBuilder(docDir: String, baseURL: String) {

  import Capability._
  var iterator: ResourceIterator = null
  var iter: Iterator[ResourceDescription] = null
  var setName: String = null
  var buildList = false
  var buildDump = false
  var buildChanges = false

  def resources(theIterator: ResourceIterator): ResyncBuilder = {
    iterator = theIterator
    iterator.setBaseURL(baseURL)
    setName = iterator.resourceSet
    iter = iterator.iterator
    this
  }

  def list(): ResyncBuilder = {
    buildList = true
    this
  }

  def dump(): ResyncBuilder = {
    buildDump = true
    this
  }

  def changes(): ResyncBuilder = {
    buildChanges = true
    this
  }

  def build() {
    // build then serialize the resource/change list
    import ResyncBuilder._
    var resourceMap: ResourceMap = null
    var mapFile: File = null
    var resList = List[URLResource]()
    while (iter.hasNext) {
      val desc = iter.next
      var md: Option[Map[String, String]] = None
      if (! buildChanges) {
        md = if (desc.checksum.isDefined) Some(Map("hash" -> desc.checksum.get)) else None
      } else {
        md = Some(Map("change" -> desc.change.get))
      }
      resList = URLResource(desc.location, desc.modified, metadata = md) :: resList
    }
    iterator.close
    // provide an uplink to capability list
    val upLink = Link(docURL(capabilitylist.toString), "up", Map())
    if (buildList) {
      if (! buildChanges) {
        resourceMap = ResourceList(links = List(upLink), resources = resList)
      } else {
        resourceMap = ChangeList(new Date, new Date, List(upLink), resources = resList)
      }
      // serialize to disk & update capability list
      mapFile = docFile(resourceMap.capability.toString)
      XMLResourceMapWriter.write(resourceMap, new FileOutputStream(mapFile))
      checkCapability(resourceMap)
    }
    if (buildDump) {
      if (! buildChanges) {
        resourceMap = ResourceDumpManifest(new Date, links = List(upLink), resources = resList)
        // now construct the ResouceDump document itself
        var dump = ResourceDump(new Date, links = List(upLink), resources = resList)
        //XMLResourceMapWriter.write(resourceMap, new FileOutputStream(mapFile)) 
      } else {
        resourceMap = ChangeDumpManifest(new Date, new Date, List(upLink), resources = resList)
      }
      // serialize to disk
      mapFile = docFile(resourceMap.capability.toString)
      XMLResourceMapWriter.write(resourceMap, new FileOutputStream(mapFile))   
    }
    // now add capability file to description if needed
    val dmd = Map("capability" -> capabilitylist.toString)
    val descMapRes = URLResource(docURL(capabilitylist.toString), metadata = Some(dmd))
    val descFile = new File(docDir, descriptionName + ".xml")
    val desc = description(descFile).withResource(descMapRes)
    // commit changes back to disk
    XMLResourceMapWriter.write(desc, new FileOutputStream(descFile))
  }

  private def checkCapability(resMap: ResourceMap) {
    import ResyncBuilder._
    val name = resMap.capability.toString
    val md = Map("capability" -> name)
    val resMapRes = URLResource(docURL(name), metadata = Some(md))
    val capFile = docFile(capabilitylist.toString)
    // read from disk if present
    val capList =
    if (capFile.exists) {
      XMLResourceMapReader.read(new FileInputStream(capFile)).asInstanceOf[CapabilityList]
    } else {
      // create a new empty capability list
      val docUpLink = Link(new URL(baseURL + descriptionName + ".xml"), "up", Map())
      CapabilityList(List(docUpLink), List())
    }
    val updCapList = capList.withResource(resMapRes)
    // commit changes back to disk
    XMLResourceMapWriter.write(updCapList, new FileOutputStream(capFile))
  }

  private def description(descFile: File): Description = {
    // read from disk if present
    import ResyncBuilder._
    if (descFile.exists) {
      XMLResourceMapReader.read(new FileInputStream(descFile)).asInstanceOf[Description]
    } else {
      // create a new empty description
      Description(List(), List())
    }
  }

  private def docFile(capability: String): File = {
    val root = if (! "".equals(setName)) new File(docDir, setName) else new File(docDir)
    root.mkdirs
    new File(root, capability + ".xml")
  }

  private def docURL(capability: String): URL = {
    val url = if (! "".equals(setName)) baseURL + setName + "/" else baseURL
    new URL(url + capability + ".xml")
  }
}

object ResyncBuilder {

  val maxListResources = 50000
  val maxDumpSize = 1024
  
  val descriptionName = "description"

  def apply(docDir: String, baseURL: String) = new ResyncBuilder(docDir, baseURL)
}

class DumpBuilder {

  var from: Date = new Date
  var urlList: List[URLResource] = List()

  def resourceDump = ResourceDump(from, List(), urlList)
}

class ManifestBuilder {

  var from: Date = new Date
  var until: Date = new Date
  var urlList: List[URLResource] = List()

  def listZip = ResourceDumpManifest(from, List(), urlList)
  def listManifest = ResourceDumpManifest(from, List(), urlList)
  def changeZip = ChangeDumpManifest(from, until, List(), urlList)
  def changeManifest = ChangeDumpManifest(from, until, List(), urlList)
}

class IndexBuilder {
  var mapList: List[MapResource] = List()
  var from: Date = new Date
  var until: Date = new Date

  def setValidity(date: Date): IndexBuilder = { 
    from = date
    this
  }

  def setExpiry(date: Date): IndexBuilder = { 
    until = date
    this
  }

  def addMap(url: String, lastMod: Date = null): IndexBuilder = {
    mapList = MapResource(new URL(url), Some(lastMod)) :: mapList
    this
  }

  def descriptionIndex = DescriptionIndex(from, List(), mapList)
  def capabilityListIndex = CapabilityListIndex(from, List(), mapList)
  def resourceListIndex = ResourceListIndex(from, List(), mapList)
  def resourceDumpIndex = ResourceDumpIndex(from, List(), mapList)
  def changeListIndex = ChangeListIndex(from, until, List(), mapList)
  def changeDumpIndex = ChangeDumpIndex(from, until, List(), mapList)  
}

