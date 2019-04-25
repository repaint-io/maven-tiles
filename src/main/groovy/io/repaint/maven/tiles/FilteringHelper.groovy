package io.repaint.maven.tiles

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.apache.maven.execution.MavenSession
import org.apache.maven.model.Plugin
import org.apache.maven.model.Resource
import org.apache.maven.project.MavenProject
import org.apache.maven.shared.filtering.MavenFileFilter
import org.apache.maven.shared.filtering.MavenFileFilterRequest
import org.apache.maven.shared.filtering.MavenResourcesExecution
import org.apache.maven.shared.filtering.MavenResourcesFiltering

import static io.repaint.maven.tiles.Constants.TILEPLUGIN_ARTIFACT
import static io.repaint.maven.tiles.Constants.TILEPLUGIN_GROUP
import static io.repaint.maven.tiles.Constants.TILE_POM

@CompileStatic
class FilteringHelper {

    @CompileStatic(TypeCheckingMode.SKIP)
    static File getTile(final MavenProject project,
                        final MavenSession mavenSession,
                        final MavenFileFilter mavenFileFilter,
                        final MavenResourcesFiltering mavenResourcesFiltering) {
        // determine whether filtering is enabled
        def configuration = project.build.plugins
            ?.find({ Plugin plugin -> plugin.groupId == TILEPLUGIN_GROUP && plugin.artifactId == TILEPLUGIN_ARTIFACT})
            ?.configuration

        final boolean filtering = configuration?.getChild("filtering")?.getValue() == "true"

        if (filtering) {
            String generatedSourcesDirectoryStr = configuration?.getChild("generatedSourcesDirectory")?.getValue()
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
