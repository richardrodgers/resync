# ReSync - an OAI ResourceSync support library in Scala #

Repo contains a library to facilitate:
* construction of ResourceSync-compliant XML documents from sets of resource descriptions
* parsing of ResourceSync documents into sets of resource descriptions

It generally conforms to the '0.9.1' (beta) version of the ResourceSync Framework Specification.

## Usage ##

NB: this section is subject to frequent change, API is not locked down.

### Creating ResourceSync Documents ###

The library is used to create the various ResourceSync artifacts, which are or contain XML
documents based on the Sitemap specification. When using it, we will refer to the
_document directory_ parameter as the location in a filesystem where these
artifact files are written. The library makes the assumption that all relevent
files are co-resident in this directory, or one of its sub-directories. For example, 
if a resource list file is created, the library will look for (or create if absent)
a capability file in the same directory. Needless to say, the files may be _deployed_ 
to a variety of locations when written to a server, and their relationships are ensured
by URL links in the documents. The document directory will have a sub-directory for each
'resource set' declared, with the sub-directory name equal to the resource set name.
If no resource sets are declared, then all files will exist at the directory
root. The ResourceSync description file will always reside at the root.

Another widely used parameter is the _base URL_, which refers to the stem of all URLs constructed
by the library. Typically, the library will construct URLs consisting of the base, followed by a
resource set name (if present), followed by the capability name of the file, followed by an
index (if needed). Thus:

    http://www.example.com/rs/data1/resourcelist.xml

has components: baseURL <http://www.example.com/rs/>, resource set name <data1>, etc
Note that actual resource URLs need not bear any relation to these URLs, but in the
sample resource iterator (FileIterator), the base URL and resource set name are in fact used.

To create ResourceSync documents, the library requires a 'resource iterator' object
that describes each resource in a resource set to be included in the list, dump, etc
Users will typically write custom iterators of their own, but the library contains a simple
iterator that describes files in a directory as resources. Thus, to create a simple resource
list of the files in '/home/bob', using base URL 'http://example.com', and placing the
resulting ResourceSync XML files in the document directory '/public/resync', we would:

    val builder = ResyncBuilder("/public/resync/", "http://example.com/")
    builder.list().resources(new FileIterator("/home/bob")).build()

This invocation would create 3 files in /public/resyc, appropriately linked:

    resourcelist.xml
    capabilitylist.xml
    description.xml

We could create a dump instead with:

    builder.dump().resources(new FileIterator("/home/bob")).build()

Creating both at once is fine as well:

    builder.list().dump().resources(new FileIterator("/home/bob")).build()

If a resource iterator is capable of giving us changes, then we can make change lists:

    builder.list().changes().resources(new FileIterator("/home/bob")).build()

If one prefers the 'DSL' style of invocation:

    builder list changes resources FileIterator "/home/bob" build

### Limits and Autoscaling ###

The library enforces certain limits (recommended by the specification) on created artifacts,
and transparently provides the mechanisms needed to scale beyond those limits.

#### Resource and Change Dump Packages ####

To ensure reasonable download times, the library restricts the maximum size of indvidual
dump archive packages to approximately 50 Megabytes. It does not guarantee that an
individual package  will not exceed that limit, but rather will ensure that no resources
will be added to a package of that size or greater. When this condition occurs, the library
will create a sequence of numbered package files, and construct the resource or change dump
XML file to enumerate them. Users of the library do not need to estimate resource size or
configure the library to cope with the fragmentation.

#### Resource and Change Lists ####

Similarly, to conform to the recommendations of Sitemap community pratice, the library
will not construct resource or change lists with over 50,000 resources. When this limit
is reached, the library will segment the resources accross multiple numbered lists and
create a resource or change list index to enumerate them. Again, no configuration or action
by users of the library is required.

### Reading ResourceSync Documents ###

To read an XML Resource List file:

    val resourceList = XMLResourceMapReader.read(new FileInputStream("resourcelist.xml"))

### Scripting ###

Since scala is a scripting language, you can use this library to script any ResourceSync operations,
without having to compile or deploy any scala code (except, of course, any resource iterators you
need to write). For example, suppose we want to (re)generate a resource list every week for 
the set of files in '/home/docs'. Simply create a text file (let's call it 'resync.scala')
with the following content:

    import edu.mit.lib.resync._

    val builder = ResyncBuilder("/public/resync", "http://resync.mysite.edu")
    val resIter = FileIterator("/home/docs", "set1")
    builder list resources resIter build

Then place this line in the weekly cron tab:

    /bin/scala -cp /path/to/resync.jar resync.scala

You can add other resource sets, dumps, changelists, etc by amending the script.

### Unimplemented Features, Future Work ###

The library does not currently implement:

 * Source Description Indexes
 * Capability List Indexes
 * Resource Dump Indexes
 * Change Dump Indexes

