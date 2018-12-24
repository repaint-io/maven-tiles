package io.repaint.maven.tiles

import org.apache.maven.execution.MavenSession
import org.apache.maven.model.Plugin
import org.apache.maven.model.Resource
import org.apache.maven.project.MavenProject
import org.apache.maven.shared.filtering.DefaultMavenFileFilter
import org.apache.maven.shared.filtering.DefaultMavenReaderFilter
import org.apache.maven.shared.filtering.DefaultMavenResourcesFiltering
import org.apache.maven.shared.filtering.MavenFileFilter
import org.apache.maven.shared.filtering.MavenFileFilterRequest
import org.apache.maven.shared.filtering.MavenResourcesExecution
import org.apache.maven.shared.filtering.MavenResourcesFiltering
import org.codehaus.plexus.logging.Logger
import org.codehaus.plexus.util.xml.Xpp3Dom
import org.sonatype.plexus.build.incremental.DefaultBuildContext

class FilteringHelper {

    public static final String TILE_POM = "tile.xml"

    static File getTile(final MavenProject project,
                        final File generatedSourcesDirectory,
                        final MavenSession mavenSession,
                        final Logger logger ) {
        // determine whether filtering is enabled
        final List<Plugin> plugins = project.build.plugins
            .stream()
            .filter { p -> p.groupId == "io.repaint.maven" && p.artifactId == "tiles-maven-plugin" }
            .collect()

        final boolean filtering = (plugins.size() == 1) && ((plugins[0].configuration as Xpp3Dom)?.getChild("filtering")?.getValue() == "true")

        if (filtering) {
            // Big string of assumptions here:
            // getTile() makes use of built-in maven filtering that uses an implementation of MavenFileFilter and MavenResourcesFiltering.
            // These components are injected in the Mojo with @Component, but are unavailable to the maven lifecycle participant.
            // I attempted to supply an implementation myself by looking for available implementations and looking at what implementations
            // maven uses at runtime (using breakpoints and IDE variable inspection). I then brute force set all private fields that caused
            // NullPointerExceptions by reflection with an appropriate implementation.
            final def context = new DefaultBuildContext()

            final def mavenFileFilter = new DefaultMavenFileFilter()
            mavenFileFilter.enableLogging(logger)
            final def buildContextField = DefaultMavenFileFilter.getDeclaredField("buildContext")
            buildContextField.setAccessible(true)
            buildContextField.set(mavenFileFilter, context)
            final def readerFilterField = DefaultMavenFileFilter.getDeclaredField("readerFilter")
            readerFilterField.setAccessible(true)
            readerFilterField.set(mavenFileFilter, new DefaultMavenReaderFilter())

            final def mavenResourcesFiltering = new DefaultMavenResourcesFiltering()
            mavenResourcesFiltering.initialize()
            mavenResourcesFiltering.enableLogging(logger)
            final def buildContextField2 = DefaultMavenResourcesFiltering.getDeclaredField("buildContext")
            buildContextField2.setAccessible(true)
            buildContextField2.set(mavenResourcesFiltering, context)
            final def mavenFileFilterField = DefaultMavenResourcesFiltering.getDeclaredField("mavenFileFilter")
            mavenFileFilterField.setAccessible(true)
            mavenFileFilterField.set(mavenResourcesFiltering, mavenFileFilter)

            return getTile(project, true, generatedSourcesDirectory, mavenSession, mavenFileFilter, mavenResourcesFiltering)
        } else {
            return getTile(project, false, generatedSourcesDirectory, mavenSession, null, null)
        }
    }

    static File getTile(final MavenProject project,
                        final boolean filtering,
                        final File generatedSourcesDirectory,
                        final MavenSession mavenSession,
                        final MavenFileFilter mavenFileFilter,
                        final MavenResourcesFiltering mavenResourcesFiltering) {
        File baseTile = new File(project.basedir, TILE_POM)
        if (filtering) {
            File processedTileDirectory = new File(generatedSourcesDirectory, "tiles")
            processedTileDirectory.mkdirs()
            File processedTile = new File(processedTileDirectory, TILE_POM)

            Resource tileResource = new Resource()
            tileResource.setDirectory(project.basedir.absolutePath)
            tileResource.includes.add(TILE_POM)
            tileResource.setFiltering(true)

            MavenFileFilterRequest req = new MavenFileFilterRequest(baseTile,
                    processedTile,
                    true,
                    project,
                    [],
                    true,
                    "UTF-8",
                    mavenSession,
                    new Properties())
            req.setDelimiters(["@"] as LinkedHashSet)

            MavenResourcesExecution execution = new MavenResourcesExecution(
                    [tileResource], processedTileDirectory, "UTF-8",
                    mavenFileFilter.getDefaultFilterWrappers(req),
                    project.basedir, mavenResourcesFiltering.defaultNonFilteredFileExtensions)

            mavenResourcesFiltering.filterResources(execution)

            return new File(processedTileDirectory, TILE_POM)
        } else {
            return baseTile
        }
    }

}
