# ReSync - an OAI ResourceSync support library in Scala #
Repo contains a library to facilitate:
* construction of ResourceSync-compliant XML documents from sets of resource descriptions
* parsing of ResourceSync documents into sets of resource descriptions

## Usage ##

NB: this section is subject to frequent change, API is not locked down.

### Creating ResourceSync Documents ###

The library is used to create the various ResourceSync artifacts, which are all XML documents
based on the Sitemap specification. When using it, we will refer to the 'document directory'
as the location in a filesystem where these document files are written. The library makes
the assumption that all relevent files are co-resident in this directory, or one of its
sub-directories. For example, if a resource list file is created, the library will look 
for (or create if absent) a capability file in the same directory. Needless to say,
the files may be _deployed_ to a variety of locations when written to a server, and their
relationships are ensured by URL links in the documents. The document directory will have a
sub-directory for each 'resource set' declared, with the sub-directory name equal to the 
resource set name. If no resource sets are declared, then all files will exist at the directory
root. The ResourceSync description file will always reside at the root.

To create ResourceSync documents, the library requires a 'resource iterator' object
that describes each resource in a resource set to be included in the list, dump, etc
Users will typically write iterators of their own, but the library contains a simple iterator
that describes files in a directory as resources. Thus, to create a simple resource list
of the files in '/home/bob', using the URL root 'http://example.com', and placing the
resulting ResourceSync XML files in the document directory '/public/resync', we would:

    val builder = ResyncBuilder("/public/resync/", "http://example.com/")
    builder.list().resources(new FileIterator("/home/bob")).build()

This invocation would create 3 files in /public/resyc, appropriately linked:

    resourcelist.xml
    capabilitylist.xml
    description.xml

We could create a dump instead with:

    builder.dump().resources(new FileIterator("/home/bob")).build()

Creating both is fine as well:

    builder.list().dump().resources(new FileIterator("/home/bob")).build()

If the resource iterator is capable of giving us changes, then we can make change lists:

    builder.list().changes().resources(new FileIterator("/home/bob")).build()

If one prefers the 'DSL' style of invocation:

    builder list changes resources FileIterator "/home/bob" build


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

