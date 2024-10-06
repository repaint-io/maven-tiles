package io.repaint.maven.tiles;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenFileFilterRequest;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.maven.shared.filtering.MavenResourcesExecution;
import org.apache.maven.shared.filtering.MavenResourcesFiltering;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Properties;

import static io.repaint.maven.tiles.ConfigurationHelper.safeConfig;
import static io.repaint.maven.tiles.Constants.TILEPLUGIN_ARTIFACT;
import static io.repaint.maven.tiles.Constants.TILEPLUGIN_GROUP;
import static io.repaint.maven.tiles.Constants.TILE_POM;

public class FilteringHelper {
  public static File getTile(
      MavenProject project,
      MavenSession mavenSession,
      MavenFileFilter mavenFileFilter,
      MavenResourcesFiltering mavenResourcesFiltering) throws MavenFilteringException {
    // determine whether filtering is enabled
    Plugin configuration =
        project.getBuild()
            .getPlugins()
            .stream()
            .filter(plugin -> plugin.getGroupId().equals(TILEPLUGIN_GROUP) && plugin.getArtifactId().equals(TILEPLUGIN_ARTIFACT))
            .findFirst()
            .orElse(null);

    final boolean filtering = "true".equals(safeConfig(configuration.getConfiguration(), "filtering", "false"));

    if (filtering) {
      String generatedSourcesDirectoryStr = safeConfig(configuration.getConfiguration(), "generatedSourcesDirectory", null);
      File generatedSourcesDirectory = generatedSourcesDirectoryStr != null
                                         ? new File(generatedSourcesDirectoryStr)
                                         : new File(project.getBuild().getDirectory(), "generated-sources");
      return getTile(project, true, generatedSourcesDirectory, mavenSession, mavenFileFilter, mavenResourcesFiltering);
    } else {
      return getTile(project, false, null, mavenSession, null, null);
    }
  }

  public static File getTile(
      MavenProject project,
      boolean filtering,
      File generatedSourcesDirectory,
      MavenSession mavenSession,
      MavenFileFilter mavenFileFilter,
      MavenResourcesFiltering mavenResourcesFiltering) throws MavenFilteringException {
    File baseTile = new File(project.getBasedir(), TILE_POM);
    if (filtering) {
      File processedTileDirectory = new File(generatedSourcesDirectory, "tiles");
      processedTileDirectory.mkdirs();
      File processedTile = new File(processedTileDirectory, TILE_POM);

      Resource tileResource = new Resource();
      tileResource.setDirectory(project.getBasedir().getAbsolutePath());
      tileResource.getIncludes().add(TILE_POM);
      tileResource.setFiltering(true);

      MavenFileFilterRequest req =
          new MavenFileFilterRequest(baseTile, processedTile, true, project, null, true, "UTF-8", mavenSession, new Properties());
      req.setDelimiters(new LinkedHashSet<>(java.util.Arrays.asList("@")));

      MavenResourcesExecution execution = new MavenResourcesExecution(
          java.util.Collections.singletonList(tileResource),
          processedTileDirectory,
          "UTF-8",
          mavenFileFilter.getDefaultFilterWrappers(req),
          project.getBasedir(),
          mavenResourcesFiltering.getDefaultNonFilteredFileExtensions());

      mavenResourcesFiltering.filterResources(execution);

      return new File(processedTileDirectory, TILE_POM);
    } else {
      return baseTile;
    }
  }
}