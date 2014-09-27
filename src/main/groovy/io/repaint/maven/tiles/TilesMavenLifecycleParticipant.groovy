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
package io.repaint.maven.tiles

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import io.repaint.maven.tiles.isolators.AetherIsolator
import io.repaint.maven.tiles.isolators.Maven30Isolator
import io.repaint.maven.tiles.isolators.MavenVersionIsolator
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
import org.apache.maven.model.Parent
import org.apache.maven.model.Plugin
import org.apache.maven.model.Repository
import org.apache.maven.model.building.DefaultModelBuilder
import org.apache.maven.model.building.DefaultModelBuildingRequest
import org.apache.maven.model.building.FileModelSource
import org.apache.maven.model.building.ModelBuilder
import org.apache.maven.model.building.ModelBuildingRequest
import org.apache.maven.model.building.ModelBuildingResult
import org.apache.maven.model.building.ModelProcessor
import org.apache.maven.model.building.ModelSource
import org.apache.maven.model.io.ModelParseException
import org.apache.maven.model.resolution.InvalidRepositoryException
import org.apache.maven.model.resolution.ModelResolver
import org.apache.maven.model.resolution.UnresolvableModelException
import org.apache.maven.project.MavenProject
import org.codehaus.plexus.component.annotations.Component
import org.codehaus.plexus.component.annotations.Requirement
import org.codehaus.plexus.logging.Logger
import org.codehaus.plexus.util.xml.Xpp3Dom
import org.codehaus.plexus.util.xml.pull.XmlPullParserException

/**
 * Fetches all dependencies defined in the POM `configuration`.
 *
 * Merging operation is delegated to {@link TilesModelMerger} and (@link PropertyModelMerger}
 */
@CompileStatic
@Component(role = AbstractMavenLifecycleParticipant, hint = "TilesMavenLifecycleParticipant")
public class TilesMavenLifecycleParticipant extends AbstractMavenLifecycleParticipant {

	protected static final String TILE_EXTENSION = 'pom'
	public static final TILEPLUGIN_GROUP = 'io.repaint.maven'
	public static final TILEPLUGIN_ARTIFACT = 'tiles-maven-plugin'
	public static final String SMELL_DEPENDENCYMANAGEMENT = "dependencymanagement"
	public static final String SMELL_DEPENDENCIES = "dependencies"
	public static final String SMELL_REPOSITORIES = "repositories"
	public static final String SMELL_PLUGINREPOSITORIES = "pluginrepositories"

	public static final List<String> SMELLS = [SMELL_DEPENDENCIES, SMELL_DEPENDENCYMANAGEMENT,
	  SMELL_PLUGINREPOSITORIES, SMELL_REPOSITORIES]

	@Requirement
	Logger logger

	@Requirement
	ArtifactResolver resolver

	@Requirement
	ModelBuilder modelBuilder

	@Requirement
	ModelProcessor modelProcessor

	protected MavenVersionIsolator mavenVersionIsolate

	List<ArtifactRepository> remoteRepositories
	ArtifactRepository localRepository

	NotDefaultModelCache modelCache

	MavenSession mavenSession

	class ArtifactModel {
		public Artifact artifact
		public TileModel tileModel

		public ArtifactModel(Artifact artifact, TileModel tileModel) {
			this.artifact = artifact
			this.tileModel = tileModel
		}
	}

	/**
	 * We store the groupId:artifactId -> Artifact of those tiles we have discovered in our meanderings through
	 * the
	 */
	Map<String, ArtifactModel> processedTiles = [:]
	List<String> tileDiscoveryOrder = []
	Map<String, Artifact> unprocessedTiles = [:]
	Set<String> collectedBuildSmells = []

	/**
	 * reactor builds can/will have their own tile structures
	 */
	protected void resetTiles() {
		processedTiles = [:]
		tileDiscoveryOrder = []
		unprocessedTiles = [:]
		collectedBuildSmells = []
	}

	/**
	 * This specifically goes and asks the repository for the "tile" attachment for this pom, not the
	 * pom itself (because we don't care about that).
	 */
	protected Artifact getArtifactFromCoordinates(String groupId, String artifactId, String version) {
		return new DefaultArtifact(groupId, artifactId, VersionRange.createFromVersion(version), "compile",
			"tile", "tile-pom", new DefaultArtifactHandler("pom"))
	}



	protected Artifact resolveTile(Artifact tileArtifact) throws MavenExecutionException {
		try {
			mavenVersionIsolate.resolveVersionRange(tileArtifact)

			resolver.resolve(tileArtifact, remoteRepositories, localRepository)

			if (System.getProperty("performRelease")?.asBoolean()) {
				if (tileArtifact.version.endsWith("-SNAPSHOT")) {
					throw new MavenExecutionException("Tile ${artifactGav(tileArtifact)} is a SNAPSHOT and we are releasing.",
						tileArtifact.getFile())
				}
			}

		} catch (ArtifactResolutionException e) {
			throw new MavenExecutionException(e.getMessage(), e)
		} catch (ArtifactNotFoundException e) {
			throw new MavenExecutionException(e.getMessage(), e)
		}

		return tileArtifact
	}

	protected Artifact turnPropertyIntoUnprocessedTile(String artifactGav, File pomFile)
	  throws MavenExecutionException {

		String[] gav = artifactGav.tokenize(":")

		if (gav.size() != 3) {
			throw new MavenExecutionException("${artifactGav} does not have the form group:artifact:version-range", pomFile)
		}

		String groupId = gav[0]
		String artifactId = gav[1]
		String version = gav[2]

		return getArtifactFromCoordinates(groupId, artifactId, version)
	}

	protected TileModel loadModel(Artifact artifact) throws MavenExecutionException {
		try {
			TileModel modelLoader = new TileModel(artifact.getFile(), artifact)

			logger.debug(String.format("Loaded Maven Tile %s", artifactGav(artifact)))

			return modelLoader
		} catch (FileNotFoundException e) {
			throw new MavenExecutionException(String.format("Error loading %s", artifactGav(artifact)), e)
		} catch (XmlPullParserException e) {
			throw new MavenExecutionException(String.format("Error building %s", artifactGav(artifact)), e)
		} catch (IOException e) {
			throw new MavenExecutionException(String.format("Error parsing %s", artifactGav(artifact)), e)
		}
	}

	protected MavenVersionIsolator discoverMavenVersion(MavenSession mavenSession) {
		MavenVersionIsolator isolator = null

		try {
			isolator = new AetherIsolator(mavenSession)
		} catch (MavenExecutionException mee) {
			isolator = new Maven30Isolator(mavenSession)
		}

		return isolator
	}

	/**
	 * Invoked after all MavenProject instances have been created.
	 *
	 * This callback is intended to allow extensions to manipulate MavenProjects
	 * before they are sorted and actual build execution starts.
	 */
	public void afterProjectsRead(MavenSession mavenSession)
		throws MavenExecutionException {

		this.mavenSession = mavenSession

		this.remoteRepositories = mavenSession.request.remoteRepositories
		this.localRepository = mavenSession.request.localRepository

		this.mavenVersionIsolate = discoverMavenVersion(mavenSession)

		this.modelCache = new NotDefaultModelCache(mavenSession)

		final MavenProject topLevelProject = mavenSession.getTopLevelProject()
		List<String> subModules = topLevelProject.getModules()

		if (subModules != null && subModules.size() > 0) {
			//We're in a multi-module build, we need to trigger model merging on all sub-modules
			for (MavenProject subModule : mavenSession.getProjects()) {
				if (subModule != topLevelProject) {
					resetTiles()
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
		parseConfiguration(propertyCollectionModel, project.getFile())

		// collect any unprocessed tiles, and process them causing them to potentially load more unprocessed ones
		loadAllDiscoveredTiles()

		// don't do anything if there are no tiles
		if (processedTiles) {
			thunkModelBuilder(project)
		}

		// last discovered tile should be first to be overridden, i.e. create new parent chain
//		Collections.reverse(tileDiscoveryOrder)

		// get our own model in pure, naked form - this will cause property usage to be unresolved when
		// we try and process it
		// we need to
//		Artifact artifact = getArtifactFromCoordinates(project.groupId, project.artifactId, project.version)
//		artifact.setFile(project.getFile())
//
//		TileModel ourPureModel = loadModel(artifact)
//
//		mergePropertyModels(ourPureModel, propertyCollectionModel, project.getModel())

//		interpolateModels(ourPureModel, project)

		// now we are at a point where we know what all of our tiles are, we have them in the least
		// important to most important sequence and we have collected all of the properties across all
		// of the tiles.
		// We are ignoring mismatching version ranges or clashing versions of tiles. First in, First Served.
		// Warnings otherwise.

//		mergeModel(project.model, ourPureModel)

		// now make sure our dependencies have versions if we have dependency management, same if plugin mgmt
//		injectManagementSections(project.model)
	}

	@CompileStatic(TypeCheckingMode.SKIP)
	protected void thunkModelBuilder(MavenProject project) {
		List<TileModel> tiles = processedTiles.values().collect({it.tileModel})

		if (!tiles) return

		// this allows us to know when the ModelProcessor is called that we should inject the tiles into the
		// parent structure
		ModelSource mainArtifactModelSource = createModelSource(project.file)
		ModelBuildingRequest request = new DefaultModelBuildingRequest(modelSource: mainArtifactModelSource,
			pomFile: project.file, modelResolver: createModelResolver(), modelCache: modelCache,
		  systemProperties: System.getProperties(), userProperties: mavenSession.request.userProperties,
		  activeProfileIds: mavenSession.request.activeProfiles, inactiveProfileIds: mavenSession.request.inactiveProfiles)

		boolean injected = false

		ModelProcessor fakeModelProcessor = new ModelProcessor() {
			@Override
			File locatePom(File projectDirectory) {
				return modelProcessor.locatePom(projectDirectory)
			}

			@Override
			Model read(File input, Map<String, ?> options) throws IOException, ModelParseException {
				return modelProcessor.read(input, options)
			}

			@Override
			Model read(Reader input, Map<String, ?> options) throws IOException, ModelParseException {
				return modelProcessor.read(input, options)
			}

			@Override
			Model read(InputStream input, Map<String, ?> options) throws IOException, ModelParseException {
				Model model = modelProcessor.read(input, options)

				if (!injected && input == mainArtifactModelSource.inputStream) {
					injected = true

					injectTilesIntoParentStructure(tiles, model, request)
				}

				return model
			}
		}

		((DefaultModelBuilder)modelBuilder).setModelProcessor(fakeModelProcessor)

		ModelBuildingResult build = modelBuilder.build(
			request)

		((DefaultModelBuilder)modelBuilder).setModelProcessor(modelProcessor)

		copyModel(project.model, build.effectiveModel)
	}

	ModelSource createModelSource(File pomFile) {
		return new ModelSource() {
			InputStream stream = pomFile.newInputStream()

			@Override
			InputStream getInputStream() throws IOException {
				return stream
			}

			@Override
			String getLocation() {
				return pomFile.absolutePath
			}
		}
	}

	ModelResolver createModelResolver() {
		// this is for resolving parents, so always poms

		return new ModelResolver() {
			@Override
			ModelSource resolveModel(String groupId, String artifactId, String version) throws UnresolvableModelException {
				Artifact artifact = new DefaultArtifact(groupId, artifactId, VersionRange.createFromVersion(version), "compile",
					"pom", null, new DefaultArtifactHandler("pom"))

				mavenVersionIsolate.resolveVersionRange(artifact)
				resolver.resolve(artifact, remoteRepositories, localRepository)

				return createModelSource(artifact.file)
			}

			@Override
			ModelSource resolveModel(Parent parent) throws UnresolvableModelException {
				return resolveModel(parent.groupId, parent.artifactId, parent.version)
			}

			@Override
			void addRepository(Repository repository) throws InvalidRepositoryException {

			}

			@Override
			void resetRepositories() {

			}

			@Override
			ModelResolver newCopy() {
				return null
			}
		}

	}



	@CompileStatic(TypeCheckingMode.SKIP)
	protected void putModelInCache(Model model, ModelBuildingRequest request, File pomFile) {
		// stuff it in the cache so it is ready when requested rather than it trying to be resolved.
		modelBuilder.putCache(request.modelCache, model.groupId, model.artifactId, model.version,
			org.apache.maven.model.building.ModelCacheTag.RAW,
			mavenVersionIsolate.createModelData(model, pomFile));
//				new org.apache.maven.model.building.ModelData(new FileModelSource(tileModel.tilePom), model));
	}

	public void injectTilesIntoParentStructure(List<TileModel> tiles, Model pomModel,
	                                            ModelBuildingRequest request) {
		Parent originalParent = pomModel.parent
		Model lastPom = pomModel
		File lastPomFile = request.pomFile

		tiles.each { TileModel tileModel ->
			Model model = tileModel.model

			Parent modelParent = new Parent(groupId: model.groupId, version: model.version, artifactId: model.artifactId)
			lastPom.parent = modelParent

			if (pomModel != lastPom) {
				println "LastPom Model is ${modelGav(lastPom)} new parent is ${parentGav(modelParent)}"
				putModelInCache(lastPom, request, lastPomFile)
			}

			lastPom = model
			lastPomFile = tileModel.tilePom
		}

		lastPom.parent = originalParent

		if (pomModel != lastPom) {
			println "LastPom Model is ${modelGav(lastPom)} new parent is ${parentGav(originalParent)}"
			putModelInCache(lastPom, request, lastPomFile)
		}
	}

	protected void copyModel(Model projectModel, Model newModel) {
		projectModel.build = newModel.build
		projectModel.dependencyManagement = newModel.dependencyManagement
		projectModel.dependencies = newModel.dependencies
		projectModel.pluginRepositories = newModel.pluginRepositories
		projectModel.licenses = newModel.licenses
		projectModel.scm = newModel.scm
		projectModel.developers = newModel.developers
		projectModel.contributors = newModel.contributors
		projectModel.organization = newModel.organization
		projectModel.mailingLists = newModel.mailingLists
		projectModel.issueManagement = newModel.issueManagement
		projectModel.ciManagement = newModel.ciManagement
		projectModel.profiles = newModel.profiles
		projectModel.prerequisites = newModel.prerequisites
		projectModel.properties = newModel.properties
	}

	/*
	void injectManagementSections(Model model) {
		ModelProblemCollector problemCollector = mavenVersionIsolate.createModelProblemCollector()
		DefaultModelBuildingRequest modelBuildingRequest = new DefaultModelBuildingRequest()

		modelBuildingRequest.setSystemProperties(System.getProperties())
		modelBuildingRequest.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MAVEN_3_1)

		if (model.build?.pluginManagement) {
			pluginManagementInjector.injectManagement(model, modelBuildingRequest, problemCollector)
		}

		if (model.dependencyManagement) {

			dependencyManagementInjector.injectManagement(model, modelBuildingRequest, problemCollector)
		}
	}
	*/

	/*
	protected void interpolateModels(TileModel pureModel, MavenProject project) {
		ModelProblemCollector problemCollector = mavenVersionIsolate.createModelProblemCollector()
		DefaultModelBuildingRequest modelBuildingRequest = new DefaultModelBuildingRequest()

		modelBuildingRequest.setSystemProperties(System.getProperties())
		modelBuildingRequest.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MAVEN_3_1)

		modelInterpolator.interpolateModel(pureModel.model, project.getBasedir(), modelBuildingRequest, problemCollector)

		for (String artifactName : tileDiscoveryOrder) {
			ArtifactModel artifactModel = processedTiles.get(artifactName)

			// all tile properties have been merged into our project now, so lets push them into the tile
			// we have to do this otherwise we get duplicates appearing - particularly in dependencies
			artifactModel.tileModel.model.setProperties(project.getProperties())

			// now we do our string substitution
			modelInterpolator.interpolateModel(artifactModel.tileModel.model, project.getBasedir(),
				modelBuildingRequest, problemCollector)
		}

	}
	*/

	/**
	 * Cleans the model of untagged bad build smells.
	 * @param model
	 */
	protected void cleanModel(Model model) {
		// should be using composities
		if (!collectedBuildSmells.contains(SMELL_DEPENDENCYMANAGEMENT)) {
			model.dependencyManagement = null
		}

		// can't use exclusions if this is used, so should use composites
		if (!collectedBuildSmells.contains(SMELL_DEPENDENCIES)) {
			model.dependencies = null
		}

		// does this even need explanation? http://blog.sonatype.com/2009/02/why-putting-repositories-in-your-poms-is-a-bad-idea/
		if (!collectedBuildSmells.contains(SMELL_REPOSITORIES)) {
			model.repositories = null
		}

		if (!collectedBuildSmells.contains(SMELL_PLUGINREPOSITORIES)) {
			model.pluginRepositories = null
		}
	}

	protected void mergeModel(Model confusedModel, TileModel pureModel) {
		TilesModelMerger modelMerger = new TilesModelMerger()

		if (collectedBuildSmells) {
			logger.info(("Build is smelly, remaining dirty: ${collectedBuildSmells}"))
		}

		// start with this project and override it with tiles (now in reverse order)
		for (String artifactName : tileDiscoveryOrder) {
			ArtifactModel artifactModel = processedTiles.get(artifactName)

			logger.info("Merging ${artifactGav(artifactModel.artifact)}")

			cleanModel(artifactModel.tileModel.model)

			modelMerger.merge(confusedModel, artifactModel.tileModel.model, true, null)
		}

		// finally override it with out own properties
		modelMerger.merge(confusedModel, pureModel.model, true, null)
	}


	protected void mergePropertyModels(TileModel pureModel, Model propertyCollectionModel, Model projectModel) {
		PropertyModelMerger modelMerger = new PropertyModelMerger()

		// start with this project and override it with tiles (now in reverse order)
		for (String artifactName : tileDiscoveryOrder) {
			ArtifactModel artifactModel = processedTiles.get(artifactName)

			modelMerger.mergeModelBase_Properties(propertyCollectionModel, artifactModel.tileModel.model, true, null)
		}

		// finally override it with out own properties from our pure model (no inheritance or
		// packaging interference)
		modelMerger.mergeModelBase_Properties(pureModel.model, propertyCollectionModel, false, null)

		// this should now replace all properties in our model with the correct properties
		modelMerger.mergeModelBase_Properties(projectModel, pureModel.model, true, null)

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

	protected void loadAllDiscoveredTiles() throws MavenExecutionException {
		while (unprocessedTiles.size() > 0) {
			String unresolvedTile = unprocessedTiles.keySet().iterator().next()

			Artifact resolvedTile = resolveTile(unprocessedTiles.remove(unresolvedTile))

			TileModel tileModel = loadModel(resolvedTile)

			// ensure we have resolved the tile (it could come from a non-tile model)
			if (tileModel) {
				processedTiles.put(artifactName(resolvedTile), new ArtifactModel(resolvedTile, tileModel))

				parseForExtendedSyntax(tileModel, resolvedTile.getFile())
			}
		}

		ensureAllTilesDiscoveredAreAccountedFor()
	}

	/**
	 * removes all invalid tiles from the discovery order
	 */
	void ensureAllTilesDiscoveredAreAccountedFor() {
		List<String> missingTiles = []

		tileDiscoveryOrder.each { String tile ->
			if (!processedTiles[tile]) {
				missingTiles.add(tile)
			}
		}

		tileDiscoveryOrder.removeAll(missingTiles)
	}

	protected String artifactName(Artifact artifact) {
		return String.format("%s:%s", artifact.groupId, artifact.artifactId)
	}

	protected String artifactGav(Artifact artifact) {
		return String.format("%s:%s:%s", artifact.groupId, artifact.artifactId, artifact.versionRange ?: artifact.version)
	}

	protected String modelGav(Model model) {
		return String.format("%s:%s:%s", model.groupId, model.artifactId, model.version)
	}

	protected String parentGav(Parent model) {
		return String.format("%s:%s:%s", model.groupId, model.artifactId, model.version)
	}

	/**
	 * Normally used inside the current project's pom file when declaring the tile plugin. People may prefer this
	 * to use to include tiles however in a tile.xml
	 */
	protected void parseConfiguration(Model model, File pomFile) {
		Xpp3Dom configuration = model?.build?.plugins?.
			find({ Plugin plugin ->
				return plugin.groupId == TILEPLUGIN_GROUP &&
						plugin.artifactId == TILEPLUGIN_ARTIFACT})?.configuration as Xpp3Dom

		if (configuration) {
			configuration.getChild("tiles")?.children?.each { Xpp3Dom tile ->
				processConfigurationTile(model, tile.value, pomFile)
			}

			String buildStink = configuration.getChild("buildSmells")?.value

			if (buildStink) {
				Collection<String> smells = buildStink.tokenize(',')*.trim().findAll({String tok -> return tok.size()>0})

				// this is Mark's fault.
				Collection<String> okSmells = smells.collect({it.toLowerCase()}).intersect(SMELLS)

				Collection<String> stinkySmells = new ArrayList(smells).minus(okSmells)

				if (stinkySmells) {
					throw new MavenExecutionException("Discovered bad smell configuration ${stinkySmells} from <buildStink>${buildStink}</buildStink> in ${pomFile.absolutePath}", pomFile)
				}

				collectedBuildSmells.addAll(okSmells)
			}
		}
	}

	/**
	 * Used for when we have a TileModel (we have read directly) so we support the extra syntax.
	 */
	protected void parseForExtendedSyntax(TileModel model, File pomFile) {
		model.tiles.each { String tileGav ->
			processConfigurationTile(model.model, tileGav, pomFile)
		}

		parseConfiguration(model.model, pomFile)
	}

	protected void processConfigurationTile(Model model, String tileDependencyName, File pomFile) {
		Artifact unprocessedTile = turnPropertyIntoUnprocessedTile(tileDependencyName, pomFile)

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
