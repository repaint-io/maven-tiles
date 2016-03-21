package io.repaint.maven.tiles

import groovy.transform.CompileStatic
import org.apache.maven.model.Model
import org.codehaus.plexus.logging.Logger

import static io.repaint.maven.tiles.AbstractTileMojo.*

/**
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 * @author: Mark Derricutt - https://plus.google.com/+MarkDerricutt
 */
@CompileStatic
class TileValidator {

	public Model loadModel(Logger log, File tilePom, Collection<BuildSmell> buildSmells) {
		TileModel modelLoader = new TileModel()
		Model validatedModel = null

		if (!tilePom) {
			log.error("No tile exists")
		} else if (!tilePom.exists()) {
			log.error("Unable to file tile ${tilePom.absolutePath}")
		} else {
			modelLoader.loadTile(tilePom)
			validatedModel = validateModel(modelLoader.model, log, buildSmells)
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
	protected Model validateModel(Model model, Logger log, Collection<BuildSmell> buildSmells) {
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

		if (model.repositories && !buildSmells.contains(BuildSmell.Repositories)) {
			log.error("Tile follows bad practice and has repositories section. Please use settings.xml.")
			validModel = null
		}

		if (model.pluginRepositories && !buildSmells.contains(BuildSmell.PluginRepositories)) {
			log.error("Tile follows bad practice and has pluginRepositories section. Please use settings.xml.")
			validModel = null
		}

		if (model.dependencyManagement && !buildSmells.contains(BuildSmell.DependencyManagement)) {
			log.error("Tile follows bad practice and has dependencyManagement. Please use composites.")
			validModel = null
		}

		if (model.build?.pluginManagement && !buildSmells.contains(BuildSmell.PluginManagement)) {
			log.error("Plugin management is usually not required, if you want a plugin to always run, use plugins instead.")
			validModel = null
		}

		if (model.dependencies && !buildSmells.contains(BuildSmell.Dependencies)) {
			log.error("Tile includes dependencies - this will prevent consumers from adding exclusions, use composites instead.")
			validModel = null
		}

		return validModel
	}
}
