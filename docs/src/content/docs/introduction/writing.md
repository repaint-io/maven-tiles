---
title: Writing a Maven Tile
description: How to write a Maven Tile
---

A Maven Tile is made up of a `pom.xml` and a `tile.xml`.

The `pom.xml` is a basic Maven project using the packaging type of `tile`, and the inclusion of the `tiles-maven-plugin`, along with release plugin configuration (often coming itself from a tile).

The POM would be minimal, only declaring the `name`, `groupId`, `artifactId`, `version`, `description`, and `packaging` (tile) attributes and generally only a declaration of the Maven Tiles plugin.

The tiles plugin will only have a `configuration` element defined when using other tiles (generally, you use at least one - a release tile).

**pom.xml**

```xml
<project>
  <groupId>io.repaint.maven</groupId>
  <artifactId>license-tile</artifactId>
  <version>1.1-SNAPSHOT</version>
  <packaging>tile</packaging>
  <description>Contains consistent license information.</description>
  <modelVersion>4.0.0</modelVersion>

  <build>
    <plugins>
      <plugin>
        <groupId>io.repaint.maven</groupId>
        <artifactId>tiles-maven-plugin</artifactId>
        <version>2.37</version>
        <extensions>true</extensions>
        <configuration>
          <filtering>false</filtering>
          <tiles>
            <tile>io.repaint.tiles:github-release-tile:[1.1, 2)</tile>
          </tiles>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

With the packaging `tile`, the plugin will look for the attached `tile.xml``, do some basic validation on it and attach it as an artifact.

When the `<filtering>` configuration item is specified as `true` - then standard Maven resource filtering for `@project.version@` style references is applied to the `tile.xml` file prior to install/deploy.

**tile.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project>
  <modelVersion>4.0.0</modelVersion>
  <licenses>
    <license>
      <name>The Apache Software License, Version 2.0</name>
      <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
</project>
```

## Using Snapshots of Tiles

`-SNAPSHOT` versions of tiles work when installed into your local `~/.m2/repository`, however - if you wish to use a _published_ SNAPSHOT - you will need to declare a `<repository>` in your `pom.xml` that support SNAPSHOTs.

Review the [introduction to repositories](https://maven.apache.org/guides/introduction/introduction-to-repositories.html) section on the Apache Maven website.

If you don’t wish to include `<repository>` definitions in your project source, declaring them in an activated `<profile>` in your `~/.m2/settings.xml` file is a viable alternative.

**📌 NOTE**\
This introduces an element of inconsistentcy/non-reproducability to your build and should be done with care.

## Transcluding Tiles

A tile can define the tiles plugin if it wishes to cascade tile inclusion, or it can use the extended `tile.xml` syntax:

**tile.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project>
  <modelVersion>4.0.0</modelVersion>
  <licenses>
    <license>
      <name>The Apache Software License, Version 2.0</name>
      <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      <distribution>repo</distribution>
    </license>
  </licenses>

  <tiles>
    <tile>io.repaint.tiles:ratpack-tile:[1,2)</tile>
  </tiles>

</project>
```

Although this will appear to not validate when editing in an IDE, the tile plugin will strip off the `tiles` section when processing and use it directly.

