---
title: About
description: A Project Mixin Implementation for Apache Maven
---

The _tiles-maven-plugin_ is a Maven Plugin that brings a **modular**, **composition** based appraoch to Apache Maven builds.

## How do I make a Maven Tile?

Tiles are [plain Maven pom artifacts](https://github.com/maoo/maven-tiles-examples/tree/master/tiles) which contain parts of a Maven POM; every tile can contain

* build data, for example the license tags that a company wants to report on all their artifacts
* build aspects, for example the configurations of a plugin, such as Groovy, Java or Clojure.
* release aspects, for example the distribution management section
* references to other tiles

This could, for example, allow you to have build aspects consistent across your organization and open sourced, and the distribution of internal vs external artifacts kept in a single tile.

In the current single parent hierarchy, this is all duplicated.

## Where can't I use a Maven Tile?

The following are Repaint project definitions:

* **define: reactor build** - `pom.xml` that contains only modules, no plugins,
  no dependencies, no dependency management, no plugin management. These are used as shortcuts to get your full project installed or tested.
* **define: multi-module build** - `pom.xml` that contains plugins and/or dependencies, dependency management, plugin management.

With those defined:

* You can use a tile in a reactor or multi-module build where the tile is a module and (a) only used in the other modules or (b) used in the parent with the inherits config turned off (so it is not inherited by the children).  This is a side effect of how Maven works and we cannot work around it.
* We do not prioritize issues raised where you are using a multi-module build. These are the anti-thesis of Repaint.IO’s philosophy of composition over inheritance. If you raise the issue and it seems a reasonable use case we will look at it, but please be aware that it is unlikely to be looked at by us without an explicit reproducible test case.

## Execution Ids

Execution ids within tiles are prepended with the gav parameters of the tile that included the execution, for easier debugging/tracing.

If this is not desired, adding a configuration attribute `tiles-keep-id="true"` or entry
`<tiles-keep-id>true<tiles-keep-id>` will keep the original id. If you are using the _Kotlin Maven plugin_, you will need to use this or it will not work.

**tile.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project>
  <modelVersion>4.0.0</modelVersion>
  <build>
    <plugins>
      <plugin>
        <groupId>test</groupId>
        <artifactId>test</artifactId>
        <version>1.0</version>
        <executions>
          <execution>
            <id>1</id>
          </execution>
          <execution>
            <id>2</id>
            <configuration tiles-keep-id="true" />
          </execution>
          <execution>
            <id>3</id>
            <configuration>
              <tiles-keep-id>true</tiles-keep-id>
            </configuration>
         </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
  <profiles>
    <profile>
      <id>test</id>
      <build>
        <plugins>
          <plugin>
            <groupId>test</groupId>
            <artifactId>test</artifactId>
            <version>1.0</version>
            <executions>
              <execution>
                <id>4</id>
              </execution>
              <execution>
                <id>5</id>
                <configuration tiles-keep-id="true" />
              </execution>
              <execution>
                <id>6</id>
                <configuration>
                  <tiles-keep-id>true</tiles-keep-id>
                </configuration>
             </execution>
            </executions>
          </plugin>
        </plugins>
      </build>
    </profile>
  </profiles>
</project>
```

In the above tile, executions with ids 1 and 4 will have their ids changed to
`io.repaint.tiles:execution-id-replacing-tile:1.1-SNAPSHOT::1` and
`io.repaint.tiles:execution-id-replacing-tile:1.1-SNAPSHOT::4` respectively, while executions with ids 2, 3, 5 and 6
will retain their original execution id.

## Build Smells

When migrating from a parent structure, it is worthwhile to take the opportunity to reduce your build smells.

You can do this gradually or in one go, depending on how your builds are done. By default, the plugin will strip all bad smells.

The following is an explanation of what is stripped and why those smells are bad. Richard and Mark will be putting together a short book with tutorials for a better approach to building using Maven, but this is the short explanation.

Note, these are only cleaned from the `tile.xml``, not from your `pom.xml``.

* **dependencymanagement** - this was always a poor substitute for composite poms. Composite poms - aka a pom only release
artifact that stores all related dependencies together. This allows your project to pull in only those dependencies
 that it actually requires for release, and allow them to be directly overridden. Dependency management is only
 for declaring the version of an artifact, and not that it is a dependency - it is better and more composable to
 declare this specifically in a composite pom instead. Use version ranges so changes flow through.
* **pluginrepositories** and **repositories** - see [Repositories in POMs is a bad idea](http://blog.sonatype.com/2009/02/why-putting-repositories-in-your-poms-is-a-bad-idea/) - this has always
been a bad idea. Get rid of it as soon as possible.
* **dependencies** - putting them in a parent or tile prevents your user from exclusion, again composites are a much, much
better idea here. Just don’t use this section anywhere other than your actual artifact or composite poms.

Almost made a build smell:
- pluginmanagement - plugin management is used in parents to define all of the necessary options for a plugin but
not have that plugin actually run during the release of the parent artifact, and also give the child the option of
running it. The reason this is bad is that it is mostly not necessary. You should split your plugins up into tiles
so that they be pulled into a build as a standalone set of functionality that will always run and be properly configured.
Since they will reside in the tile.xml file, they will not be run when the tile is released. However, some plugins are
never run automatically - release and enforcer are two examples. These make sense to stay in pluginManagement.

If you need to use them, add them to your configuration section:

**pom.xml**

```xml
<build>
  <modelVersion>4.0.0</modelVersion>
  <plugins>
    <plugin>
      <groupId>io.repaint.maven</groupId>
      <artifactId>tiles-maven-plugin</artifactId>
      <version>2.15</version>
      <configuration>
        <buildSmells>dependencymanagement, dependencies, repositories, pluginrepositories</buildSmells>
        <tiles>
           <tile>groupid:antrun1-tile:1.1-SNAPSHOT</tile>
           <tile>groupid:antrun2-tile:1.1-SNAPSHOT</tile>
        </tiles>
      </configuration>
    </plugin>
  </plugins>
</build>
```

## Parent structure

Tiles will always be applied as parents of the project that is built. Any orignal parent of that project will be added
as the parent of the last applied tile. So if you apply Tiles `T1` and `T2` to a project `X` with a parent `P`, the
resulting hierarchy will be `X` - `T1` - `T2` - `P`. Thus (see section _Additional Notes_), the definitions in the parent
can be overwritten by a tile, but not the other way around.

However, there are situations where you want to define your tiles in a parent, e.g. when you have a lot of artifacts
that are built in the same way. In this case you would want a structure like this: `X` - `P` - `T1` - `T2`. While you’d
maybe expect it to work this way if the tiles are included in `P`, due to the way Maven works there’s no way to know
where a configuration comes from. To still enable this use case you can manually choose a parent where the tiles will
be applied (in this case before `P`) resulting in the desired structure:

**pom.xml**

```xml
<parent>
  <groupId>group</groupId>
  <artifactId>P</artifactId>
  <version>1.0.0</version>
</parent>
<artifactId>X</artifactId>
...
<build>
  <plugins>
    <plugin>
      <groupId>io.repaint.maven</groupId>
      <artifactId>tiles-maven-plugin</artifactId>
      <version>2.37</version>
      <configuration>
        <applyBefore>group:P</applyBefore>
        <tiles>
          <tile>group:T1:1.0.0</tile>
          <tile>group:T2:1.0.0</tile>
        </tiles>
      </configuration>
    </plugin>
  </plugins>
</build>
```

## Tile Ordering

In v2.19 and earlier, all tiles declared inside tiles are loaded after all tiles declared in the project.  This meant
that all tiles declared inside tiles became ancestors to all tiles declared in the project.

In v2.20+, tile ordering has changed.  All tiles are now loaded in order of when they are declared, recursively tracing
from tiles declared in a project down to the tiles declared within tiles.

Suppose your project declares tiles `T1` and `T2` and the `T1` tile declares tile `T3`.  Earlier versions will load
these in the order `T1`, `T2`, and then `T3`.  Using the notation used earlier on this page, this results in the Maven
project ancestry of `X` - `T3` - `T2` - `T1` - `P`.  Later versions will load them in the order `T1`, `T3`, and then
`T2`.  This results in the Maven project ancestry of `X` - `T2` - `T3` - `T1` - `P`.

In some cases, your project and tile hierarchy will include duplicate declaration of one tile.  In these cases, the
tile is only included once.  The latest declaration is used, resulting it showing up in the Maven project
ancestry as the one closest to the parent `P`.  This guarantees it will be an ancestor to all tiles that included it.

## Mojos

There are two mojos in the plugin, attach-tile and validate. attach-tile is only used by the deploy/install
process and attaches the tile.xml. validate is for your use to ensure your tile is valid before releasing it - this
ensures it can be read and any errors or warnings about content will appear.

## Additional Notes

Some interesting notes:

* Tiles support version ranges, so use them. [1.1, 2) allows you to update and release new versions of tiles and have them
propagate out. Maven 3.2.2 allows this with the version ranges in parents, but it isn’t a good solution because of single
inheritance.
* You can include as many tiles as you like in a pom and tiles can refer to other tiles. The plugin will search through
the poms, telling you which ones it is picking up and then load their configurations in **reverse order**. This means the
poms _closer_ to your artifact get their definitions being the most important ones. If you have duplicate plugins, the one
closest to your pom wins.
* String interpolation for properties works. The plugin first walks the tree of tiles collecting all properties, merges them
together (closest wins), and then reloads the poms and interpolates them. This means all string replacement in plugins and
dependencies works as expected.
* Plugin execution is merged - if you have the same plugin in two different tiles define two different executions, they will
merge.
* The plugin works fine with alternative packaging. It has been tested with war, grails-plugin and grails-app.

## Final Notes

Tiles-Maven works best when **you** and **your team** own the tiles. I don’t recommend relying on open source tiles, always
create your own versions and always lock down versions of third party tiles, just like you would third party dependencies.

## Read More

* [The Original Tiles Maven plugin](https://github.com/maoo/maven-tiles) - although the essential start point is the same, the code is significantly different.
* [Mixin POM fragments](http://jira.codehaus.org/browse/MNG-5102)
* [Stack Overflow](http://stackoverflow.com/questions/11749375/import-maven-plugin-configuration-by-composition-rather-than-inheritance-can-it)
* [Maven Discussion](http://maven.40175.n5.nabble.com/Moving-forward-with-mixins-tc4421069.html)
