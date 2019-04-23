package io.repaint.maven.tiles

import groovy.transform.CompileStatic
import org.apache.maven.MavenExecutionException
import org.apache.maven.model.Model
import org.codehaus.plexus.logging.Logger

/**
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 * @author: Mark Derricutt - https://plus.google.com/+MarkDerricutt
 */
@CompileStatic
class TileValidator {

	public static final String SMELL_DEPENDENCYMANAGEMENT = "dependencymanagement"
	public static final String SMELL_DEPENDENCIES = "dependencies"
	public static final String SMELL_REPOSITORIES = "repositories"
	public static final String SMELL_PLUGINREPOSITORIES = "pluginrepositories"
	public static final String SMELL_PLUGINMANAGEMENT = "pluginmanagement"

	public static final List<String> SMELLS = [SMELL_DEPENDENCIES, SMELL_DEPENDENCYMANAGEMENT,
	                                           SMELL_PLUGINREPOSITORIES, SMELL_PLUGINMANAGEMENT,
	                                           SMELL_REPOSITORIES]

	public Model loadModel(Logger log, File tilePom, String buildSmells) {
		TileModel modelLoader = new TileModel()
		Model validatedModel = null

		Set<String> collectedBuildSmells = []
		if (buildSmells) {
			Collection<String> smells = buildSmells.tokenize(',')*.trim().findAll({ String tok -> return tok.size() > 0 })

			// this is Mark's fault.
			Collection<String> okSmells = smells.collect({ it.toLowerCase() }).intersect(TileValidator.SMELLS)

			Collection<String> stinkySmells = new ArrayList(smells).minus(okSmells)

			if (stinkySmells) {
				throw new MavenExecutionException("Discovered bad smell configuration ${stinkySmells} from <buildSmells>${buildSmells}</buildSmells>.", tilePom)
			}

			collectedBuildSmells.addAll(okSmells)
		}


		if (!tilePom) {
			log.error("No tile exists")
		} else if (!tilePom.exists()) {
			log.error("Unable to file tile ${tilePom.absolutePath}")
		} else {
			modelLoader.loadTile(tilePom)
			validatedModel = validateModel(modelLoader.model, log, collectedBuildSmells)
			if (validatedModel) {
				log.info("Tile passes basic validation.")
			}
		}

		return validatedModel
	}

	/**
	 * Display all of the errors.
	 *
	 * Should we allow name? description? modelVersion?
	 */
	protected Model validateModel(Model model, Logger log, Set<String> buildSmells) {
		Model validModel = model

		if (model.groupId) {
			log.error("Tile has a groupid and must not have")
			validModel = null
		}

		if (model.artifactId) {
			log.error("Tile has an artifactid and must not have")
			validModel = null
		}

		if (model.version) {
			log.error("Tile has a version and must not have")
			validModel = null
		}

		if (model.parent) {
			log.error("Tile has a parent and must not have")
			validModel = null
		}

		if (model.repositories && !buildSmells.contains(SMELL_REPOSITORIES)) {
			log.error("Tile follows bad practice and has repositories section. Please use settings.xml.")
			validModel = null
		}

		if (model.pluginRepositories && !buildSmells.contains(SMELL_PLUGINREPOSITORIES)) {
			log.error("Tile follows bad practice and has pluginRepositories section. Please use settings.xml.")
			validModel = null
		}

		if (model.dependencyManagement && !buildSmells.contains(SMELL_DEPENDENCYMANAGEMENT)) {
			log.error("Tile follows bad practice and has dependencyManagement. Please use composites.")
			validModel = null
		}

		if (model.build?.pluginManagement && !buildSmells.contains(SMELL_PLUGINMANAGEMENT)) {
			log.error("Plugin management is usually not required, if you want a plugin to always run, use plugins instead.")
			validModel = null
		}

		if (model.dependencies && !buildSmells.contains(SMELL_DEPENDENCIES)) {
			log.error("Tile includes dependencies - this will prevent consumers from adding exclusions, use composites instead.")
			validModel = null
		}

		if (model.build?.extensions) {
			log.error("Tile has extensions and must not have")
			validModel = null
		}

		return validModel
	}
}
