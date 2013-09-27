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
 * @param baseURL the URL stem for all builder-managed documents
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
    var listResList = List[URLResource]()
    var dumpResList = List[URLResource]()
    var dumpList = List[URLResource]()
    var indexList = List[MapResource]()
    var counter = 0
    var dumpIndex = 1
    var mapIndex = 0
    var written = 0L
    var zipfs: FileSystem = if (buildDump) zipFS(dumpIndex) else null
    val upLink = Link(docURL(capabilitylist.toString), "up", Map())
    val iter = iterator.iterator
    while (iter.hasNext) {
      val desc = iter.next
      val res = URLResource(desc.location, desc.modified, metadata = Some(resMetadata(desc)))
      if (buildDump) {
        if (written > maxDumpSize) {
          dumpList = closeDump(upLink, dumpResList, dumpIndex, zipfs) :: dumpList
          dumpIndex += 1
          zipfs = zipFS(dumpIndex)
          written = 0L
          dumpResList = List()
        }
        // squirrel away the resource bits into a zip file
        val src = desc.content.get
        written = written + Files.copy(src, zipfs.getPath(desc.name.get))
        src.close
        dumpResList = res :: dumpResList
      }
      if (buildList) {
        if (counter == maxListSize) {
          // will need multiple lists and an index - finish this one and proceed
          indexList = closeList(upLink, listResList, mapIndex, true) :: indexList
          mapIndex += 1
          listResList = List()
        }
        listResList = res :: listResList
      }
      counter += 1
    }
    iterator.close
    if (buildList) {
      val multiList = indexList.size > 0
      indexList = closeList(upLink, listResList, mapIndex, multiList) :: indexList
      if (multiList) {
        // we need to construct an index document
        if (buildChanges) {
          save(ChangeListIndex(new Date, new Date, List(upLink), resources = indexList), changelist.toString)
        } else {
          save(ResourceListIndex(new Date, links = List(upLink), resources = indexList), resourcelist.toString)
        }
      }
    }
    if (buildDump) {
      dumpList = closeDump(upLink, dumpResList, dumpIndex, zipfs) :: dumpList
      if (buildChanges) {
        save(ChangeDump(new Date, new Date, List(upLink), resources = dumpList), changedump.toString)
      } else {
        save(ResourceDump(new Date, links = List(upLink), resources = dumpList), resourcedump.toString)
      } 
    }
    // now add capability file to description if needed
    val dmd = Map("capability" -> capabilitylist.toString)
    val descMapRes = URLResource(docURL(capabilitylist.toString), metadata = Some(dmd))
    val descFile = new File(docDir, descriptionName + ".xml")
    val desc = if (descFile.exists) {
      XMLResourceMapReader.read(new FileInputStream(descFile)).asInstanceOf[Description]
    } else {
      // create a new empty description
      Description(List(), List())
    }
    // commit changes back to disk
    XMLResourceMapWriter.write(desc.withResource(descMapRes), new FileOutputStream(descFile))
    // clear flags
    buildList = false; buildDump = false; buildChanges = false
  }

  private def closeList(upLink: Link, resList: List[URLResource], mapIndex: Int, index: Boolean = false): MapResource = {
    var theLinks = List(upLink)
    val listName = if (buildChanges) changelist.toString else resourcelist.toString
    if (index) {
      val idxLink = Link(docURL(listName), "index", Map())
      theLinks = idxLink :: theLinks
    }
    if (buildChanges) {
      save(ChangeList(new Date, new Date, theLinks, resources = resList), listName + mapIndex)
    } else {
      save(ResourceList(links = theLinks, resources = resList), listName + mapIndex)
    }
    new MapResource(docURL(listName + mapIndex), Some(new Date))
  }

  private def closeDump(upLink: Link, resList: List[URLResource], index: Int, zipfs: FileSystem): URLResource = {
    val manif = if (buildChanges) ChangeDumpManifest(new Date, new Date, links = List(upLink), resources = resList)
                             else ResourceDumpManifest(new Date, links = List(upLink), resources = resList)
    // serialize the manifest, then copy to the zip archive
    val manifFile = docFile(manif.capability.toString + index)
    XMLResourceMapWriter.write(manif, new FileOutputStream(manifFile))
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

  private def save(resourceMap: ResourceMap, docName: String) {
    // serialize map to disk and update capability file if needed
    val mapFile = docFile(docName)
    XMLResourceMapWriter.write(resourceMap, new FileOutputStream(mapFile))
    checkCapability(resourceMap)   
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

  val maxListSize = 50000
  val maxDumpSize = 52428800  // 50 MB
  
  val descriptionName = "description"
  val zipManifestName = "manifest.xml"

  def apply(docDir: String, baseURL: String) = new ResyncBuilder(docDir, baseURL)
}
