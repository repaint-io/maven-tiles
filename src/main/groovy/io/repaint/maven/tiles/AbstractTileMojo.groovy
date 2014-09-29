package io.repaint.maven.tiles

import groovy.transform.CompileStatic
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import org.codehaus.plexus.logging.Logger

/**
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 * @author: Mark Derricutt - https://plus.google.com/+MarkDerricutt
 */
@CompileStatic
abstract class AbstractTileMojo extends AbstractMojo {
	public static final String TILE_POM = "tile.xml"

	@Parameter(property = "project", readonly = true, required = true)
	MavenProject project

	@Parameter(property = "tiles", readonly = false, required = false)
	List<String> tiles

	@Parameter(property = "buildSmells", readonly = false, required = false)
	String buildSmells

	@Component
	Logger logger

	File getTile() {
		return new File(project.getBasedir(), TILE_POM)
	}
}
