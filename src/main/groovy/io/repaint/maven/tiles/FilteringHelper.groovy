package io.repaint.maven.tiles

import org.apache.maven.execution.MavenSession
import org.apache.maven.model.Plugin
import org.apache.maven.model.Resource
import org.apache.maven.project.MavenProject
import org.apache.maven.shared.filtering.MavenFileFilter
import org.apache.maven.shared.filtering.MavenFileFilterRequest
import org.apache.maven.shared.filtering.MavenResourcesExecution
import org.apache.maven.shared.filtering.MavenResourcesFiltering

class FilteringHelper {

    public static final String TILE_POM = "tile.xml"

    static File getTile(final MavenProject project,
                        final MavenSession mavenSession,
                        final MavenFileFilter mavenFileFilter,
                        final MavenResourcesFiltering mavenResourcesFiltering) {
        // determine whether filtering is enabled
        final List<Plugin> plugins = project.build.plugins
            .stream()
            .filter { p -> p.groupId == "io.repaint.maven" && p.artifactId == "tiles-maven-plugin" }
            .collect()

        final boolean filtering = (plugins.size() == 1) && (plugins[0].configuration?.getChild("filtering")?.getValue() == "true")

        if (filtering) {
            String generatedSourcesDirectoryStr = plugins[0].configuration?.getChild("generatedSourcesDirectory")?.getValue()
            File generatedSourcesDirectory = generatedSourcesDirectoryStr
                ? new File(generatedSourcesDirectoryStr) : new File(project.build.directory, "generated-sources")
            return getTile(project, true, generatedSourcesDirectory, mavenSession, mavenFileFilter, mavenResourcesFiltering)
        } else {
            return getTile(project, false, null, mavenSession, null, null)
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
