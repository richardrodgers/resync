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
 * @param descURL URL to a resource with information about the ResourceSync source
 *
 * @author richardrodgers
 */
class ResyncBuilder(docDir: String, baseURL: String, descURL: String = null) {

  import Capability._
  import ResyncBuilder._

  var iterator: ResourceIterator = null
  var setName: String = null
  var setDescURL: String = null
  var baseLinks: List[Link] = null
  var buildList = false
  var buildDump = false
  var buildChanges = false
  var start: Date = null

  def resources(theIterator: ResourceIterator): ResyncBuilder = {
    iterator = theIterator
    iterator.setBaseURL(baseURL)
    setName = iterator.resourceSet
    setDescURL = iterator.setDescURL.getOrElse(null)
    // all documents should include the 'uplink' to the capability list
    baseLinks = List(Link(docURL(capabilitylist.toString), "up", Map()))
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
    val iter = iterator.iterator
    start = new Date
    while (iter.hasNext) {
      val desc = iter.next
      val res = URLResource(desc.location, desc.modified, desc.frequency, desc.priority, resMetadata(desc), desc.links)
      if (buildDump) {
        if (written > maxDumpSize) {
          dumpList = closeDump(dumpResList, dumpIndex, zipfs) :: dumpList
          dumpIndex += 1
          zipfs = zipFS(dumpIndex)
          written = 0L
          dumpResList = List()
        }
        // squirrel away the resource bits into a zip file, unless a deletion change
        if (! (buildChanges && (desc.change.get == "deleted"))) {
          val src = desc.content.get
          written = written + Files.copy(src, zipfs.getPath("/resources/" + desc.name.get))
          src.close
        }
        dumpResList = res :: dumpResList
      }
      if (buildList) {
        if (counter == maxListSize) {
          // will need multiple lists and an index - finish this one and proceed
          indexList = closeList(listResList, mapIndex, true) :: indexList
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
      indexList = closeList(listResList, mapIndex, multiList) :: indexList
      if (multiList) {
        // we need to construct an index document
        if (buildChanges) {
          save(ChangeListIndex(new Date, new Date, baseLinks, resources = indexList), changelist.toString)
        } else {
          save(ResourceListIndex(new Date, links = baseLinks, resources = indexList), resourcelist.toString)
        }
      }
    }
    if (buildDump) {
      dumpList = closeDump(dumpResList, dumpIndex, zipfs) :: dumpList
      if (buildChanges) {
        save(ChangeDump(new Date, new Date, baseLinks, resources = dumpList), changedump.toString)
      } else {
        save(ResourceDump(new Date, links = baseLinks, resources = dumpList), resourcedump.toString)
      } 
    }
    // now add capability file to description if needed
    val dmd = Map("capability" -> capabilitylist.toString)
    val descLink = getCapabilityList.links.find(_.rel == "describedby")
    val myLinks = if (descLink.isDefined) List(descLink.get) else List()
    val descMapRes = URLResource(docURL(capabilitylist.toString), metadata = Some(dmd), links = myLinks)
    val descFile = new File(docDir, descriptionName + ".xml")
    val desc = if (descFile.exists) {
      XMLResourceMapReader.read(new FileInputStream(descFile)).asInstanceOf[Description]
    } else {
      // create a new empty description
      val descLinks = if (descURL != null) List(Link(new URL(descURL), "describedby", Map())) else List()
      Description(descLinks, List())
    }
    // commit changes back to disk
    XMLResourceMapWriter.write(desc.withResource(descMapRes), new FileOutputStream(descFile))
    // clear flags
    buildList = false; buildDump = false; buildChanges = false
    start = null
  }

  private def closeList(resList: List[URLResource], mapIndex: Int, index: Boolean = false): MapResource = {
    val listName = if (buildChanges) changelist.toString else resourcelist.toString
    val fullName = if (index) listName + mapIndex else listName
    var theLinks = if (index) Link(docURL(listName), "index", Map()) :: baseLinks else baseLinks
    if (buildChanges) {
      // change lists must be time-ordered
      val ordered = resList.sortWith((x: URLResource, y: URLResource) => x.lastModified.get.before(y.lastModified.get))
      save(ChangeList(new Date, new Date, theLinks, resources = ordered), fullName)
    } else {
      save(ResourceList(start, links = theLinks, resources = resList), fullName)
    }
    new MapResource(docURL(listName + mapIndex), Some(new Date))
  }

  private def closeDump(resList: List[URLResource], index: Int, zipfs: FileSystem): URLResource = {
    val manif = if (buildChanges) {
      // change lists must be time-ordered
      val ordered = resList.sortWith((x: URLResource, y: URLResource) => x.lastModified.get.before(y.lastModified.get))
      ChangeDumpManifest(new Date, new Date, links = baseLinks, resources = ordered)
    } else ResourceDumpManifest(new Date, links = baseLinks, resources = resList)
    // serialize the manifest, then copy to the zip archive
    val manifFile = docFile(manif.capability.toString + index)
    XMLResourceMapWriter.write(manif, new FileOutputStream(manifFile))
    val manifPath = docFile(manif.capability.toString + index).toPath
    Files.copy(manifPath, zipfs.getPath(zipManifestName))
    zipfs.close
    // return a resource for this dump to the list
    val resName = if (buildChanges) changedump.toString else resourcedump.toString
    val length = zipFile(resName + index).length.toString
    val md = Map("type" -> "application/zip", "length" -> length)
    val dumpLinks = List(Link(docURL(manif.capability.toString + index), "content", Map("type" -> "application/xml")))
    new URLResource(zipURL(resName + index), Some(new Date), metadata = Some(md), links = dumpLinks)
  }

  private def resMetadata(desc: ResourceDescription): Option[Map[String, String]] = {
    var md: Map[String, String] = Map()
    if (! buildChanges) {
      if (desc.checksum.isDefined) md += "hash" -> desc.checksum.get
    }
    if (desc.size.isDefined) md += "length" -> desc.size.get.toString
    if (desc.mimetype.isDefined) md += "type" -> desc.mimetype.get
    if (buildDump) md += "path" -> ("/resources/" + desc.name.get)
    if (buildChanges) md += "change" -> desc.change.get
    if (md.size > 0) Some(md) else None
  }

  private def save(resourceMap: ResourceMap, docName: String) {
    // serialize map to disk and update capability file if needed
    val mapFile = docFile(docName)
    XMLResourceMapWriter.write(resourceMap, new FileOutputStream(mapFile))
    checkCapability(resourceMap)   
  }

  private def checkCapability(resMap: ResourceMap) {
    val name = resMap.capability.toString
    val md = Map("capability" -> name)
    val resMapRes = URLResource(docURL(name), metadata = Some(md))
    val capFile = docFile(capabilitylist.toString)
    val capList = getCapabilityList
    // commit changes back to disk
    XMLResourceMapWriter.write(capList.withResource(resMapRes), new FileOutputStream(capFile))
  }

  private def getCapabilityList: CapabilityList = {
    // read from disk if present
    val capFile = docFile(capabilitylist.toString)
    if (capFile.exists) {
      XMLResourceMapReader.read(new FileInputStream(capFile)).asInstanceOf[CapabilityList]
    } else {
      // create a new empty capability list
      val docUpLinks = List(Link(new URL(baseURL + descriptionName + ".xml"), "up", Map()))
      val theLinks = if (setDescURL != null) Link(new URL(descURL), "describedby", Map()) :: docUpLinks else docUpLinks
      CapabilityList(theLinks, List())
    }    
  } 

  private def zipFS(index: Int): FileSystem = {
    var env = new java.util.HashMap[String, String]()
    env.put("create", "true")
    val name = if (buildChanges) changedump.toString else resourcedump.toString
    // blow away any existing zip file
    val zip = zipFile(name + index)
    if (zip.exists) zip.delete
    val zipfs = FileSystems.newFileSystem(zipURI(name + index), env)
    Files.createDirectory(zipfs.getPath("/resources"))
    zipfs
  }

  private def zipURL(name: String): URL = {
    val url = if (! "".equals(setName)) baseURL + setName + "/" else baseURL
    new URL(url + name + ".zip")
  }

  private def zipURI(name: String): URI = {
    val path = if (! "".equals(setName)) docDir + "/" + setName else docDir
    new File(path).mkdirs
    URI.create("jar:file:" + path + "/" + name + ".zip")
  }

  private def zipFile(name: String): File = {
    val root = if (! "".equals(setName)) new File(docDir, setName) else new File(docDir)
    new File(root, name + ".zip")
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

  def apply(docDir: String, baseURL: String, descURL: String = null) = new ResyncBuilder(docDir, baseURL, descURL)
}
