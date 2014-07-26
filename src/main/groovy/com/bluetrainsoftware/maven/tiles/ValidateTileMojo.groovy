package com.bluetrainsoftware.maven.tiles

import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.ResolutionScope

/**
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 */
@Mojo(name = "validate", requiresProject = true, requiresDependencyResolution = ResolutionScope.NONE)
class ValidateTileMojo extends AbstractTileMojo {
	@Component
	MavenSession session

	@Override
	void execute() throws MojoExecutionException, MojoFailureException {
		if (project.modules) {
			logger.info("Ignoring reactor for tile check.")
		} else {
			new TileValidator().loadModel(logger, getTile())
		}
	}
}
