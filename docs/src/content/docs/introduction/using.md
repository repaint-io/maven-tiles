---
title: Using Tiles
description: How to use Maven Tiles
---

...how do do use tiles...


## Composing Build functionality

As a use case, an example of how it will be used for my projects.

Richard will have:

* **java8-tile** - for those projects that are on Java 8
* **java11-tile** - for those projects that are on Java LTS (11)
* **groovy-tile** - which defines the build structure necessary to build a Groovy project, including GMavenPlus, GroovyDoc
and Source plugins
* **java-tile** - for Java only projects which include all the Javadoc and Source plugins
* **s3-tile** - for our Servlet3 modules, which includes Sass, JSP compilation and Karma plugins and depends on the groovy-tile
* **github-release-tile** - for artifacts that release to Github (open source)
* **nexus-release-tile** - for artifacts that release to our local Nexus (not open source)

This allows me to open source all my tiles except for the nexus tile, and then decide in the final artifact where I will
release it.
