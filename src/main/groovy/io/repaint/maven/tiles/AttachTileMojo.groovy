package io.repaint.maven.tiles

import groovy.transform.CompileStatic
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.ResolutionScope
import org.apache.maven.project.MavenProjectHelper

/**
 * We are attaching the tile.pom file, and we don't care what the dependency resolution is.
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 * @author: Mark Derricutt - https://plus.google.com/+MarkDerricutt
 */
@CompileStatic
@Mojo(name = "attach-tile", requiresProject = true, requiresDependencyResolution = ResolutionScope.NONE, defaultPhase = LifecyclePhase.PACKAGE)
class AttachTileMojo extends AbstractTileMojo {

	@Component
	MavenProjectHelper projectHelper

	@Override
	void execute() throws MojoExecutionException, MojoFailureException {

		File tile = getTile()

		if (new TileValidator().loadModel(logger, tile, buildSmells)) {
			if ("tile".equals(project.getPackaging())) {
				project.getArtifact().setFile(tile);
			} else {
				projectHelper.attachArtifact(project, "tile", tile)
			}

			logger.info("Tile: attaching tile ${tile.path}")
		} else {
			throw new MojoFailureException("Unable to validate tile ${tile.path}!")
		}

	}
}
