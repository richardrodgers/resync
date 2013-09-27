/**
  * Copyright 2013 MIT Libraries
  * Licensed under: http://www.apache.org/licenses/LICENSE-2.0
  */

package edu.mit.lib.resync

import java.io.{File, FileInputStream, FileOutputStream}
import java.net.{URI, URL}
import java.nio.file.{Files, FileSystem, FileSystems}
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
  import ResyncBuilder._

  var iterator: ResourceIterator = null
  var setName: String = null
  var buildList = false
  var buildDump = false
  var buildChanges = false

  def resources(theIterator: ResourceIterator): ResyncBuilder = {
    iterator = theIterator
    iterator.setBaseURL(baseURL)
    setName = iterator.resourceSet
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
    // build then serialize the resource/change/dump list
    var resList = List[URLResource]()
    var dumpList = List[URLResource]()
    var index = 1
    var written = 0L
    var zipfs: FileSystem = if (buildDump) zipFS(index) else null
    val upLink = Link(docURL(capabilitylist.toString), "up", Map())
    val iter = iterator.iterator
    while (iter.hasNext) {
      val desc = iter.next
      if (buildDump) {
        if (written > maxDumpSize) {
          dumpList = closeDump(upLink, resList, index, zipfs) :: dumpList
          index = index + 1
          zipfs = zipFS(index)
          written = 0L
          resList = List()
        }
        // squirrel away the resource bits into a zip file
        val src = desc.content.get
        written = written + Files.copy(src, zipfs.getPath(desc.name.get))
        src.close
      }
      resList = URLResource(desc.location, desc.modified, metadata = Some(resMetadata(desc))) :: resList
    }
    iterator.close
    if (buildList) {
      if (buildChanges) {
        save(ChangeList(new Date, new Date, List(upLink), resources = resList))
      } else {
        save(ResourceList(links = List(upLink), resources = resList))
      }
    }
    if (buildDump) {
      dumpList = closeDump(upLink, resList, index, zipfs) :: dumpList
      if (buildChanges) {
        save(ChangeDump(new Date, new Date, List(upLink), resources = dumpList))
      } else {
        save(ResourceDump(new Date, links = List(upLink), resources = dumpList))
      } 
    }
    // now add capability file to description if needed
    val dmd = Map("capability" -> capabilitylist.toString)
    val descMapRes = URLResource(docURL(capabilitylist.toString), metadata = Some(dmd))
    val descFile = new File(docDir, descriptionName + ".xml")
    val desc = description(descFile).withResource(descMapRes)
    // commit changes back to disk
    XMLResourceMapWriter.write(desc, new FileOutputStream(descFile))
    // clear flags
    buildList = false; buildDump = false; buildChanges = false
  }

  private def closeDump(upLink: Link, resList: List[URLResource], index: Int, zipfs: FileSystem): URLResource = {
    val manif = if (buildChanges) ChangeDumpManifest(new Date, new Date, links = List(upLink), resources = resList)
                             else ResourceDumpManifest(new Date, links = List(upLink), resources = resList)
    // serialize the manifest, then copy to the zip archive
    saveManifest(manif, manif.capability.toString + index)
    val manifPath = docFile(manif.capability.toString + index).toPath
    Files.copy(manifPath, zipfs.getPath(zipManifestName))
    zipfs.close
    // return a resource for this dump to the list
    val resName = if (buildChanges) changedump.toString else resourcedump.toString
    new URLResource(zipURL(resName + index), Some(new Date), metadata = None)
  }

  private def resMetadata(desc: ResourceDescription): Map[String, String] = {
    var md: Map[String, String] = Map()
    if (! buildChanges) {
      if (desc.checksum.isDefined) md += "hash" -> desc.checksum.get
    }
    if (desc.size.isDefined) md += "length" -> desc.size.get.toString
    if (buildDump) md += "path" -> desc.name.get
    if (buildChanges) md += "change" -> desc.change.get
    md
  }

  private def save(resourceMap: ResourceMap) {
    // serialize map to disk and update capability file if needed
    val mapFile = docFile(resourceMap.capability.toString)
    XMLResourceMapWriter.write(resourceMap, new FileOutputStream(mapFile))
    checkCapability(resourceMap)   
  }

  private def saveManifest(resourceMap: ResourceMap, extName: String) {
    val mapFile = docFile(extName)
    XMLResourceMapWriter.write(resourceMap, new FileOutputStream(mapFile))
  }

  private def zipFS(index: Int): FileSystem = {
    var env = new java.util.HashMap[String, String]()
    env.put("create", "true")
    val name = if (buildChanges) changedump.toString else resourcedump.toString 
    FileSystems.newFileSystem(zipUri(name + index), env)
  }

  private def checkCapability(resMap: ResourceMap) {
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
    if (descFile.exists) {
      XMLResourceMapReader.read(new FileInputStream(descFile)).asInstanceOf[Description]
    } else {
      // create a new empty description
      Description(List(), List())
    }
  }

  private def zipURL(name: String): URL = {
    val url = if (! "".equals(setName)) baseURL + setName + "/" else baseURL
    new URL(url + name + ".zip")
  }

  private def zipUri(name: String): URI = {
    val path = if (! "".equals(setName)) docDir + "/" + setName else docDir
    new File(path).mkdirs
    URI.create("jar:file:" + path + "/" + name + ".zip")
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
  val maxDumpSize = 52428800  // 50 MB
  
  val descriptionName = "description"
  val zipManifestName = "manifest.xml"

  def apply(docDir: String, baseURL: String) = new ResyncBuilder(docDir, baseURL)
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

