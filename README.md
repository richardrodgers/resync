# ReSync - an OAI ResourceSync support library in Scala #
Repo contains a library to facilitate:
* construction of ResourceSync-compliant XML documents from sets of resource descriptions
* parsing of ResourceSync documents into sets of resource descriptions

## Usage ##

NB: this section is subject to frequent change, API is not locked down.

### Creating a simple Resource List ###

To create an XML Resource List from a simple list of URLs:

    val urlList = List("http://example.com/res1", "http://example.com/res2")
    val resourceList = ResourceList(urlList.map(url => URLResource(new URL(url))))
    XMLResourceMapWriter.write(resourceList, new FileOutputStream("resourcelist.xml"))

### Reading XML files ###

To read an XML Resource List file:

    val resourceList = XMlResourceMapReader.read(new FileInputStream("resourcelist.xml"))

### DSL ###

TODO