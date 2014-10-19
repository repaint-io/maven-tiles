package io.repaint.maven.tiles
import groovy.transform.CompileStatic
import org.apache.maven.execution.MavenSession
import org.apache.maven.model.Resource
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import org.apache.maven.shared.filtering.MavenFileFilter
import org.apache.maven.shared.filtering.MavenFileFilterRequest
import org.apache.maven.shared.filtering.MavenResourcesExecution
import org.apache.maven.shared.filtering.MavenResourcesFiltering
import org.apache.maven.shared.utils.io.FileUtils
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

	@Parameter(property = "filtering", readonly = false, required = false, defaultValue = "false")
	boolean filtering

	@Parameter(required = true, defaultValue = "\${project.build.directory}/generated-sources")
	protected File generatedSourcesDirectory;

	@Component
	Logger logger

	@Component
	MavenSession mavenSession

	@Component
	MavenFileFilter mavenFileFilter

	@Component
	MavenResourcesFiltering mavenResourcesFiltering

	File getTile() {
		File baseTile = new File(project.basedir, TILE_POM)
		if (filtering) {
			File processedTileDirectory = new File(generatedSourcesDirectory, "tiles")
			processedTileDirectory.mkdirs()
			File processedTile = new File(processedTileDirectory, TILE_POM);


			Resource tileResource = new Resource()
			tileResource.setDirectory(project.basedir.absolutePath);
			tileResource.includes.add(TILE_POM)
			tileResource.setFiltering(true)

			MavenFileFilterRequest req = new MavenFileFilterRequest(baseTile, processedTile, true, project, [], true, "UTF-8", mavenSession, new Properties())
			req.setDelimiters(new LinkedHashSet(["@"]))

			List<FileUtils.FilterWrapper> filterWappers = mavenFileFilter.getDefaultFilterWrappers(req)

			MavenResourcesExecution execution = new MavenResourcesExecution(
					[tileResource], processedTileDirectory, "UTF-8", filterWappers, project.basedir,
					mavenResourcesFiltering.defaultNonFilteredFileExtensions)

			mavenResourcesFiltering.filterResources(execution);

			return new File(processedTileDirectory, TILE_POM);
		} else {
			return new File(project.basedir, TILE_POM);
		}
	}
}
