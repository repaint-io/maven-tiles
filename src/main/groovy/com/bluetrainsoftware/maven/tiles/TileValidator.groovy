package com.bluetrainsoftware.maven.tiles

import groovy.transform.CompileStatic
import org.apache.maven.model.Model
import org.codehaus.plexus.logging.Logger

/**
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 */
@CompileStatic
class TileValidator {
	public Model loadModel(Logger log, File tilePom) {
		TileModel modelLoader = new TileModel()

		if (!tilePom) {
			log.error("No tile exists")
		} else if (!tilePom.exists()) {
			log.error("Unable to file tile ${tilePom.absolutePath}")
		} else {
			modelLoader.loadTile(tilePom)

			if (validateModel(modelLoader.model, log) != null) {
				log.info("Tile passes basic validation.")
			} else {
				log.error("Unable to load model")
			}
		}

		return modelLoader.model
	}

	/**
	 * Display all of the errors.
	 *
	 * Should we allow name? description? modelVersion?
	 */
	protected Model validateModel(Model model, Logger log) {
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

		if (model.repositories) {
			log.warn("Tile follows bad practice and has repositories section. Please use settings.xml.")
		}

		if (model.dependencyManagement) {
			log.warn("Tile follows bad practice and has dependencyManagement. Please use composites.")
		}

		if (model.build?.pluginManagement) {
			log.warn("Rethink your tiles if you feel you need pluginManagement.")
		}

		return validModel
	}
}
