package io.repaint.maven.tiles;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.shared.filtering.MavenFilteringException;

/**
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 * @author: Mark Derricutt - https://plus.google.com/+MarkDerricutt
 */
@Mojo(name = "validate", requiresProject = true, requiresDependencyResolution = ResolutionScope.NONE, threadSafe = true)
public class ValidateTileMojo extends AbstractTileMojo {
  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (project.getModules() != null && !project.getModules().isEmpty()) {
      logger.info("Ignoring reactor for tile check.");
    } else {
      try {
        new TileValidator().loadModel(logger, getTile(), buildSmells);
      } catch (MavenExecutionException | MavenFilteringException | ProjectBuildingException e) {
        throw new MojoExecutionException(e);
      }
    }
  }
}
