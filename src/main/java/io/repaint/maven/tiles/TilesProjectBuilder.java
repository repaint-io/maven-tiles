package io.repaint.maven.tiles;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.building.ModelSource;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import static io.repaint.maven.tiles.ConfigurationHelper.safeTiles;
import static io.repaint.maven.tiles.Constants.TILEPLUGIN_ARTIFACT;
import static io.repaint.maven.tiles.Constants.TILEPLUGIN_GROUP;

@Singleton
@Named("TilesProjectBuilder")
public class TilesProjectBuilder implements ProjectBuilder {
  private static final Logger log = LoggerFactory.getLogger(TilesProjectBuilder.class);
  @Inject
  private ProjectBuilder delegate;

  @Override
  public ProjectBuildingResult build(File pomFile, ProjectBuildingRequest request) throws ProjectBuildingException {
    return injectTileDependencies(delegate.build(pomFile, request));
  }

  @Override
  public ProjectBuildingResult build(ModelSource modelSource, ProjectBuildingRequest request) throws ProjectBuildingException {
    return injectTileDependencies(delegate.build(modelSource, request));
  }

  @Override
  public ProjectBuildingResult build(Artifact artifact, ProjectBuildingRequest request) throws ProjectBuildingException {
    return injectTileDependencies(delegate.build(artifact, request));
  }

  @Override
  public ProjectBuildingResult build(Artifact artifact, boolean allowStubModel, ProjectBuildingRequest request)
      throws ProjectBuildingException {
    return injectTileDependencies(delegate.build(artifact, allowStubModel, request));
  }

  @Override
  public ProjectBuildingResult build(org.apache.maven.api.services.ModelSource modelSource, ProjectBuildingRequest request)
      throws ProjectBuildingException {
    return injectTileDependencies(delegate.build(modelSource, request));
  }

  @Override
  public List<ProjectBuildingResult> build(List<File> pomFiles, boolean recursive, ProjectBuildingRequest request)
      throws ProjectBuildingException {
    return injectTileDependencies(delegate.build(pomFiles, recursive, request));
  }

  private static ProjectBuildingResult injectTileDependencies(ProjectBuildingResult result) {
    MavenProject project = result.getProject();
    Plugin configuration =
        project.getBuild()
            .getPlugins()
            .stream()
            .filter(plugin -> plugin.getGroupId().equals(TILEPLUGIN_GROUP) && plugin.getArtifactId().equals(TILEPLUGIN_ARTIFACT))
            .findFirst()
            .orElse(null);

    if (configuration != null) {
      safeTiles(configuration.getConfiguration()).forEach(tile -> {
        String[] gav = tile.split(":");

        if (gav.length != 3 && gav.length != 5) {
          throw new TileExecutionException(String.format(
              "%s does not have the form group:artifact:version-range or group:artifact:extension:classifier:version-range", tile));
        }

        boolean found = project.getDependencies().stream().anyMatch(
            existing -> existing.getGroupId().equals(gav[0]) && existing.getArtifactId().equals(gav[1]));

        if (!found) {
          Dependency dependency = new Dependency();
          dependency.setGroupId(gav[0]);
          dependency.setArtifactId(gav[1]);
          dependency.setScope("compile");
          if (gav.length == 3) {
            dependency.setType("xml");
            dependency.setVersion(extractTileVersion(gav[2]));
          } else {
            dependency.setType(gav[2]);
            dependency.setClassifier(gav[3]);
            dependency.setVersion(extractTileVersion(gav[4]));
          }
          project.getDependencies().add(dependency);
        }
      });
    }
    return result;
  }

  private static String extractTileVersion(String versionRange) {
    try {
      VersionRange range = VersionRange.createFromVersionSpec(versionRange);
      ArtifactVersion version = range.getRecommendedVersion();
      return version != null ? version.toString() : versionRange;
    } catch (InvalidVersionSpecificationException e) {
      return versionRange;
    }
  }

  private static List<ProjectBuildingResult> injectTileDependencies(List<ProjectBuildingResult> list) {
    for (ProjectBuildingResult result : list) {
      injectTileDependencies(result);
    }
    return list;
  }
}
