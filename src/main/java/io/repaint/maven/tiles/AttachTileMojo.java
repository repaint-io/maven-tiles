package io.repaint.maven.tiles;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.shared.filtering.MavenFilteringException;

import java.io.File;

@Mojo(
    name = "attach-tile",
    requiresDependencyResolution = ResolutionScope.NONE,
    defaultPhase = LifecyclePhase.PACKAGE,
    threadSafe = true)
public class AttachTileMojo extends AbstractTileMojo {
  @Component
  MavenProjectHelper projectHelper;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    try {
      File tile = getTile();
      if (new TileValidator().loadModel(logger, tile, buildSmells) != null) {
        if ("tile".equals(project.getPackaging())) {
          project.getArtifact().setFile(tile);
        } else {
          projectHelper.attachArtifact(project, "tile", tile);
        }

        logger.info("Tile: attaching tile ${tile.getPath()}");
      } else {
        throw new MojoFailureException("Unable to validate tile ${tile.getPath()}!");
      }
    } catch (MavenExecutionException | MavenFilteringException | ProjectBuildingException e) {
      throw new MojoFailureException(e);
    }
  }
}