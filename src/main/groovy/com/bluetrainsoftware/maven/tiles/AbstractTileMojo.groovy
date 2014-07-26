package com.bluetrainsoftware.maven.tiles

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import org.apache.maven.project.MavenProjectHelper
import org.codehaus.plexus.logging.Logger

/**
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 */
abstract class AbstractTileMojo extends AbstractMojo {
	public static final String TILE_POM = "tile.xml"

	@Parameter(property = "project", readonly = true, required = true)
	MavenProject project

	@Component
	Logger logger

	File getTile() {
		return new File(project.getBasedir(), TILE_POM)
	}
}
