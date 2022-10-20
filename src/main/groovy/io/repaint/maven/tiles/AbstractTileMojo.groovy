package io.repaint.maven.tiles

import groovy.transform.CompileStatic
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import org.apache.maven.shared.filtering.MavenFileFilter
import org.apache.maven.shared.filtering.MavenResourcesFiltering
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 * @author: Mark Derricutt - https://plus.google.com/+MarkDerricutt
 */
@CompileStatic
abstract class AbstractTileMojo extends AbstractMojo {

	@Parameter(property = "project", readonly = true, required = true)
	MavenProject project

	@Parameter(property = "tiles", readonly = false, required = false)
	List<String> tiles
	
	@Parameter(property = "applyBefore", readonly = false, required = false)
	String applyBefore;

	@Parameter(property = "buildSmells", readonly = false, required = false)
	String buildSmells

	@Parameter(property = "filtering", readonly = false, required = false, defaultValue = "false")
	boolean filtering

	@Parameter(required = true, defaultValue = '${project.build.directory}/generated-sources')
	File generatedSourcesDirectory

	@Parameter( defaultValue = "\${session}", readonly = true )
	MavenSession mavenSession

	@Component
	MavenFileFilter mavenFileFilter

	@Component
	MavenResourcesFiltering mavenResourcesFiltering

	Logger logger = LoggerFactory.getLogger(getClass())

	File getTile() {
		return FilteringHelper.getTile(project, filtering, generatedSourcesDirectory, mavenSession, mavenFileFilter, mavenResourcesFiltering)
	}

}
