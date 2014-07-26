/*
 * **********************************************************************************************************************
 *
 * Maven Tiles
 *
 ***********************************************************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 **********************************************************************************************************************/
package com.bluetrainsoftware.maven.tiles

import groovy.transform.CompileStatic
import org.apache.maven.AbstractMavenLifecycleParticipant
import org.apache.maven.MavenExecutionException
import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.DefaultArtifact
import org.apache.maven.artifact.handler.DefaultArtifactHandler
import org.apache.maven.artifact.repository.ArtifactRepository
import org.apache.maven.artifact.resolver.ArtifactNotFoundException
import org.apache.maven.artifact.resolver.ArtifactResolutionException
import org.apache.maven.artifact.resolver.ArtifactResolver
import org.apache.maven.artifact.versioning.VersionRange
import org.apache.maven.execution.MavenSession
import org.apache.maven.model.Model
import org.apache.maven.model.Plugin
import org.apache.maven.model.building.DefaultModelBuildingRequest
import org.apache.maven.model.building.ModelBuildingRequest
import org.apache.maven.model.building.ModelProblemCollector
import org.apache.maven.model.building.ModelProblemCollectorRequest
import org.apache.maven.model.interpolation.ModelInterpolator
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.apache.maven.model.merge.ModelMerger
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import org.codehaus.plexus.component.annotations.Component
import org.codehaus.plexus.component.annotations.Requirement
import org.codehaus.plexus.logging.Logger
import org.codehaus.plexus.util.xml.Xpp3Dom
import org.codehaus.plexus.util.xml.pull.XmlPullParserException


/**
 * Fetches all dependencies defined in the POM `&lt;properties&gt;` as follows:
 *
 * <pre>
 *   &lt;properties&gt;
 *     &lt;tiles.1&gt;com.bluetrainsoftware.maven.tiles:maven-compile-tiles:0.8-SNAPSHOT&lt;/tiles.1&gt;
 *     &lt;tiles.2&gt;com.bluetrainsoftware.maven.tiles:maven-eclipse-tiles:0.8-SNAPSHOT&lt;/tiles.2&gt;
 *     &lt;tiles.3&gt;com.bluetrainsoftware.maven.tiles:maven-jetty-tiles:0.8-SNAPSHOT&lt;/tiles.3&gt;
 *   &lt;/properties&gt;
 * </pre>
 *
 * Merging operation is delegated to {@link ModelMerger}
 */
@CompileStatic
@Component(role = AbstractMavenLifecycleParticipant, hint = "TilesMavenLifecycleParticipant")
public class TilesMavenLifecycleParticipant extends AbstractMavenLifecycleParticipant {

	protected static final String TILE_EXTENSION = 'pom'
	public static final TILEPLUGIN_GROUP = 'com.bluetrainsoftware.maven'
	public static final TILEPLUGIN_ARTIFACT = 'tiles-maven-plugin'

	protected final MavenXpp3Reader reader = new MavenXpp3Reader()

	@Requirement
	Logger logger

	@Requirement
	ArtifactResolver resolver

	@Requirement
	ModelInterpolator modelInterpolator

	@Parameter(property = "project.remoteArtifactRepositories", readonly = true, required = true)
	List<ArtifactRepository> remoteRepositories

	@Parameter(property = "localRepository")
	ArtifactRepository localRepository

	class ArtifactModel {
		public Artifact artifact
		public Model model

		public ArtifactModel(Artifact artifact, Model model) {
			this.artifact = artifact
			this.model = model
		}
	}

	/**
	 * We store the groupId:artifactId -> Artifact of those tiles we have discovered in our meanderings through
	 * the
	 */
	Map<String, ArtifactModel> processedTiles = new HashMap<String, ArtifactModel>()
	List<String> tileDiscoveryOrder = new ArrayList<String>()
	Map<String, Artifact> unprocessedTiles = new HashMap<String, Artifact>()

	protected Artifact getArtifactFromCoordinates(String groupId, String artifactId, String version) {
		return new DefaultArtifact(groupId, artifactId, VersionRange.createFromVersion(version), "compile",
			TILE_EXTENSION, "", new DefaultArtifactHandler(TILE_EXTENSION))
	}

	protected Artifact resolveTile(Artifact tileArtifact) throws MavenExecutionException {

		try {
			resolver.resolve(tileArtifact, remoteRepositories, localRepository)
		} catch (ArtifactResolutionException e) {
			throw new MavenExecutionException(e.getMessage(), e)
		} catch (ArtifactNotFoundException e) {
			throw new MavenExecutionException(e.getMessage(), e)
		}

		return tileArtifact
	}

	protected Artifact turnPropertyIntoUnprocessedTile(String propertyValue) {

		StringTokenizer propertyTokens = new StringTokenizer(propertyValue, ":")

		String groupId = propertyTokens.nextToken()
		String artifactId = propertyTokens.nextToken()
		String version = propertyTokens.nextToken()

		return getArtifactFromCoordinates(groupId, artifactId, version)
	}

	protected Model loadModel(Artifact artifact) throws MavenExecutionException {
		try {
			Model tileModel = this.reader.read(new FileInputStream(artifact.getFile()))

			logger.info(String.format("Loaded Maven Tile %s", modelGav(tileModel)))

			return tileModel
		} catch (FileNotFoundException e) {
			throw new MavenExecutionException(String.format("Error loading %s", artifactGav(artifact)), e)
		} catch (XmlPullParserException e) {
			throw new MavenExecutionException(String.format("Error building %s", artifactGav(artifact)), e)
		} catch (IOException e) {
			throw new MavenExecutionException(String.format("Error parsing %s", artifactGav(artifact)), e)
		}
	}

	/**
	 * Invoked after all MavenProject instances have been created.
	 *
	 * This callback is intended to allow extensions to manipulate MavenProjects
	 * before they are sorted and actual build execution starts.
	 */
	public void afterProjectsRead(MavenSession mavenSession)
		throws MavenExecutionException {

		final MavenProject topLevelProject = mavenSession.getTopLevelProject()
		List<String> subModules = topLevelProject.getModules()

		if (subModules != null && subModules.size() > 0) {
			//We're in a multi-module build, we need to trigger model merging on all sub-modules
			for (MavenProject subModule : mavenSession.getProjects()) {
				if (subModule != topLevelProject) {
					orchestrateMerge(subModule)
				}
			}
		} else {
			orchestrateMerge(topLevelProject)
		}
	}

	/**
	 * Merges the files over the top of the project, and then the individual project back over the top.
	 * The reason for this is that the super pom and packaging can set plugin versions. This allows the tiles
	 * to overwrite those, and then if they are manually specified in the pom, they then get set again.

	 * @param project - the currently evaluated project
	 * @throws MavenExecutionException
	 */
	protected void orchestrateMerge(MavenProject project) throws MavenExecutionException {

		// take a copy so we can mutate it and collect the properties across all the tiles
		Model propertyCollectionModel = project.getModel().clone()

		// collect the first set of tiles
		collectTiles(propertyCollectionModel)

		// collect any unprocessed tiles, and process them causing them to potentially load more unprocessed ones
		resolveTiles()

		// last discovered tile should be first to be overridden
		Collections.reverse(tileDiscoveryOrder)

		// get our own model in pure, naked form - this will cause property usage to be unresolved when
		// we try and process it
		// we need to
		Artifact artifact = getArtifactFromCoordinates(project.groupId, project.artifactId, project.version)
		artifact.setFile(project.getFile())

		Model ourPureModel = loadModel(artifact)

		mergePropertyModels(ourPureModel, propertyCollectionModel, project.getModel())

		interpolateModels(ourPureModel, project)

		// now we are at a point where we know what all of our tiles are, we have them in the least
		// important to most important sequence and we have collected all of the properties across all
		// of the tiles.
		// We are ignoring mismatching version ranges or clashing versions of tiles. First in, First Served.
		// Warnings otherwise.

		mergeModel(project.model, ourPureModel)
	}

	class OurModelProblemCollector implements ModelProblemCollector {
		public List<ModelProblemCollectorRequest> problems = new ArrayList<ModelProblemCollectorRequest>()

		@Override
		public void add(ModelProblemCollectorRequest req) {
			problems.add(req)
		}
	}

	protected void interpolateModels(Model pureModel, MavenProject project) {
		ModelProblemCollector problemCollector = new OurModelProblemCollector()
		DefaultModelBuildingRequest modelBuildingRequest = new DefaultModelBuildingRequest()

		modelBuildingRequest.setSystemProperties(System.getProperties())
		modelBuildingRequest.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MAVEN_3_1)

		modelInterpolator.interpolateModel(pureModel, project.getBasedir(), modelBuildingRequest, problemCollector)

		for (String artifactName : tileDiscoveryOrder) {
			ArtifactModel artifactModel = processedTiles.get(artifactName)

			// all tile properties have been merged into our project now, so lets push them into the tile
			// we have to do this otherwise we get duplicates appearing - particularly in dependencies
			artifactModel.model.setProperties(project.getProperties())

			// now we do our string substitution
			modelInterpolator.interpolateModel(artifactModel.model, project.getBasedir(),
				modelBuildingRequest, problemCollector)
		}
	}

	protected void mergeModel(Model confusedModel, Model pureModel) {
		TilesModelMerger modelMerger = new TilesModelMerger()

		// start with this project and override it with tiles (now in reverse order)
		for (String artifactName : tileDiscoveryOrder) {
			ArtifactModel artifactModel = processedTiles.get(artifactName)

			modelMerger.merge(confusedModel, artifactModel.model, true, null)
		}

		// finally override it with out own properties
		modelMerger.merge(confusedModel, pureModel, true, null)
	}


	protected void mergePropertyModels(Model pureModel, Model propertyCollectionModel, Model projectModel) {
		PropertyModelMerger modelMerger = new PropertyModelMerger()

		// start with this project and override it with tiles (now in reverse order)
		for (String artifactName : tileDiscoveryOrder) {
			ArtifactModel artifactModel = processedTiles.get(artifactName)

			modelMerger.mergeModelBase_Properties(propertyCollectionModel, artifactModel.model, true, null)
		}

		// finally override it with out own properties from our pure model (no inheritance or
		// packaging interference)
		modelMerger.mergeModelBase_Properties(pureModel, propertyCollectionModel, false, null)

		// this should now replace all properties in our model with the correct properties
		modelMerger.mergeModelBase_Properties(projectModel, pureModel, true, null)

		if (logger.isDebugEnabled()) {
			logPropertyNames(projectModel)
		}
	}

	protected void logPropertyNames(Model model) {
		StringBuilder sb = new StringBuilder()

		model.properties.each { Object key, Object value ->
			sb.append("\t<" + key.toString() + ">" + value.toString() + "</" + key.toString() + ">\n")
		}

		logger.debug("Tiles properties:\n " + sb.toString())
	}

	protected void resolveTiles() throws MavenExecutionException {
		while (unprocessedTiles.size() > 0) {
			String unresolvedTile = unprocessedTiles.keySet().iterator().next()

			Artifact resolvedTile = resolveTile(unprocessedTiles.remove(unresolvedTile))

			Model tileModel = loadModel(resolvedTile)

			processedTiles.put(artifactName(resolvedTile), new ArtifactModel(resolvedTile, tileModel))

			collectTiles(tileModel)
		}
	}

	protected String artifactName(Artifact artifact) {
		return String.format("%s:%s", artifact.groupId, artifact.artifactId)
	}

	protected String artifactGav(Artifact artifact) {
		return String.format("%s:%s:%s", artifact.groupId, artifact.artifactId, artifact.versionRange.toString())
	}

	protected String modelGav(Model model) {
		return String.format("%s:%s:%s", model.groupId, model.artifactId, model.version)
	}

	protected void collectTiles(Model model) {
		Xpp3Dom configuration = model?.build?.plugins?.
			find({ Plugin plugin ->
				return plugin.groupId == TILEPLUGIN_GROUP &&
						plugin.artifactId == TILEPLUGIN_ARTIFACT})?.configuration as Xpp3Dom

		if (configuration) {
			configuration.getChild("tiles")?.children?.each { Xpp3Dom tile ->
				if (tile.getName() == "tile") {
					collectConfigurationTile(model, tile.getValue())
				}
			}
		}
	}

	protected void collectConfigurationTile(Model model, String tileDependencyName) {
		Artifact unprocessedTile = turnPropertyIntoUnprocessedTile(tileDependencyName)

		String depName = artifactName(unprocessedTile)

		if (!processedTiles.containsKey(depName)) {
			if (unprocessedTiles.containsKey(depName)) {
				logger.warn(String.format("tiles-maven-plugin in project %s requested for same tile dependency %s",
					modelGav(model), artifactGav(unprocessedTile)))
			} else {
				logger.debug("Adding tile ${artifactGav(unprocessedTile)}")

				unprocessedTiles.put(depName, unprocessedTile)
				tileDiscoveryOrder.add(depName)
			}
		} else {
			logger.warn(String.format("tiles-maven-plugin in project %s requested for same tile dependency %s",
				modelGav(model), artifactGav(unprocessedTile)))
		}
	}
}
