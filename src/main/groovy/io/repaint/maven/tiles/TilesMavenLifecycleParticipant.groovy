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
import static io.repaint.maven.tiles.GavUtil.artifactName
import static io.repaint.maven.tiles.GavUtil.artifactGav
import static io.repaint.maven.tiles.GavUtil.modelGav
import static io.repaint.maven.tiles.GavUtil.parentGav
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
import org.apache.maven.model.building.*
import org.apache.maven.model.io.ModelParseException
import org.apache.maven.model.resolution.InvalidRepositoryException
import org.apache.maven.model.resolution.ModelResolver
import org.apache.maven.model.resolution.UnresolvableModelException
import org.apache.maven.project.MavenProject
import org.apache.maven.project.ProjectBuildingHelper
import org.codehaus.plexus.component.annotations.Component
import org.codehaus.plexus.component.annotations.Requirement
import org.codehaus.plexus.logging.Logger
import org.codehaus.plexus.util.xml.Xpp3Dom
import org.codehaus.plexus.util.xml.pull.XmlPullParserException
import org.xml.sax.SAXParseException

/**
 * Fetches all dependencies defined in the POM `configuration`.
 *
 * Merging operation is delegated to {@link DefaultModelBuilder}
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 * @author: Mark Derricutt - https://plus.google.com/+MarkDerricutt
 *
 */
@CompileStatic
@Component(role = AbstractMavenLifecycleParticipant, hint = "TilesMavenLifecycleParticipant")
public class TilesMavenLifecycleParticipant extends AbstractMavenLifecycleParticipant {

	protected static final String TILE_EXTENSION = 'pom'
	public static final TILEPLUGIN_GROUP = 'io.repaint.maven'
	public static final TILEPLUGIN_ARTIFACT = 'tiles-maven-plugin'

	@Requirement
	Logger logger

	@Requirement
	ArtifactResolver resolver

	@Requirement
	ModelBuilder modelBuilder

	@Requirement
	ModelProcessor modelProcessor

	@Requirement
	ProjectBuildingHelper projectBuildingHelper

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

	/**
	 * reactor builds can/will have their own tile structures
	 */
	protected void resetTiles() {
		processedTiles = [:]
		tileDiscoveryOrder = []
		unprocessedTiles = [:]
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
		} catch (SAXParseException sax) {
			throw new MavenExecutionException(String.format("Error building %s", artifactGav(artifact)), sax)
		} catch (IOException e) {
			throw new MavenExecutionException(String.format("Error parsing %s", artifactGav(artifact)), e)
		}
	}

	protected MavenVersionIsolator discoverMavenVersion(MavenSession mavenSession) {
		MavenVersionIsolator isolator

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
		// collect the first set of tiles
		parseConfiguration(project.model, project.getFile(), true)

		// collect any unprocessed tiles, and process them causing them to potentially load more unprocessed ones
		loadAllDiscoveredTiles()

		// don't do anything if there are no tiles
		if (processedTiles) {
			thunkModelBuilder(project)
		}
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
			profiles: mavenSession.request.projectBuildingRequest.profiles,
		  activeProfileIds: mavenSession.request.projectBuildingRequest.activeProfileIds,
			inactiveProfileIds: mavenSession.request.projectBuildingRequest.inactiveProfileIds,
		  modelBuildingListener: new org.apache.maven.project.DefaultModelBuildingListener( project,
			  projectBuildingHelper, mavenSession.request.projectBuildingRequest ),
			locationTracking: true, twoPhaseBuilding: true, processPlugins: true)

		ModelProcessor delegateModelProcessor = new ModelProcessor() {
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

				if (model.artifactId == project.artifactId && model.groupId == project.groupId
						&& model.version == project.version && model.packaging == project.packaging) {
					injectTilesIntoParentStructure(tiles, model, request)
				}

				return model
			}
		}

		((DefaultModelBuilder)modelBuilder).setModelProcessor(delegateModelProcessor)

		ModelBuildingResult interimBuild = modelBuilder.build(request)

		ModelBuildingResult finalModel = modelBuilder.build(request, interimBuild)
		copyModel(project.model, finalModel.effectiveModel)
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

	protected ModelResolver createModelResolver() {
		// this is for resolving parents, so always poms

		return new ModelResolver() {
			ModelSource resolveModel(String groupId, String artifactId, String version) throws UnresolvableModelException {
				Artifact artifact = new DefaultArtifact(groupId, artifactId, VersionRange.createFromVersion(version), "compile",
					"pom", null, new DefaultArtifactHandler("pom"))

				mavenVersionIsolate.resolveVersionRange(artifact)
				resolver.resolve(artifact, remoteRepositories, localRepository)

				return createModelSource(artifact.file)
			}

			ModelSource resolveModel(Parent parent) throws UnresolvableModelException {
				return resolveModel(parent.groupId, parent.artifactId, parent.version)
			}

			void addRepository(Repository repository) throws InvalidRepositoryException {
			}

			void addRepository(Repository repository, boolean wat) throws InvalidRepositoryException {
			}

			void resetRepositories() {
			}

			ModelResolver newCopy() {
				return null
			}
		}

	}

	/**
	 * This is out of static type checking because the method is private and the class ModelCacheTag
	 * is package-private.
	 *
	 * @param model - the model we are inserting into the cache
	 * @param request - the building request, it holds the cache reference
	 * @param pomFile - the pomFile is required for model data for Maven 3.2.x not for 3.0.x
	 */
	@CompileStatic(TypeCheckingMode.SKIP)
	protected void putModelInCache(Model model, ModelBuildingRequest request, File pomFile) {
		// stuff it in the cache so it is ready when requested rather than it trying to be resolved.
		modelBuilder.putCache(request.modelCache, model.groupId, model.artifactId, model.version,
			org.apache.maven.model.building.ModelCacheTag.RAW,
			mavenVersionIsolate.createModelData(model, pomFile));
//				new org.apache.maven.model.building.ModelData(new FileModelSource(tileModel.tilePom), model));
	}

	/**
	 * Creates a chain of tile parents based on how we discovered them and inserts them into the parent
	 * chain, above this project and before this project's parent (if any)
	 *
	 * @param tiles - tiles that should make up part of the collection
	 * @param pomModel - the current project
	 * @param request - the request to build the current project
	 */
	public void injectTilesIntoParentStructure(List<TileModel> tiles, Model pomModel,
	                                            ModelBuildingRequest request) {
		Parent originalParent = pomModel.parent
		Model lastPom = pomModel
		File lastPomFile = request.pomFile

		if (tiles) {
			logger.info("--- tiles-maven-plugin: Injecting ${tiles.size()} tiles as intermediary parent artifact's...")
			logger.info("Mixed '${modelGav(pomModel)}' with tile '${modelGav(tiles.first().model)}' as it's new parent.")
		}

		tiles.each { TileModel tileModel ->
			Model model = tileModel.model

			Parent modelParent = new Parent(groupId: model.groupId, version: model.version, artifactId: model.artifactId)
			lastPom.parent = modelParent

			if (pomModel != lastPom) {
				putModelInCache(lastPom, request, lastPomFile)
				logger.info("Mixed '${modelGav(lastPom)}' with tile '${parentGav(modelParent)}' as it's new parent.")
			}

			lastPom = model
			lastPomFile = tileModel.tilePom
		}

		lastPom.parent = originalParent
		logger.info("Mixed '${modelGav(lastPom)}' with original parent '${parentGav(originalParent)}' as it's  new top level parent.")
		logger.info("")

		if (pomModel != lastPom) {
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
		projectModel.distributionManagement = newModel.distributionManagement
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

	/**
	 * Normally used inside the current project's pom file when declaring the tile plugin. People may prefer this
	 * to use to include tiles however in a tile.xml
	 */
	protected void parseConfiguration(Model model, File pomFile, boolean projectModel) {
		Xpp3Dom configuration = model?.build?.plugins?.
			find({ Plugin plugin ->
				return plugin.groupId == TILEPLUGIN_GROUP &&
						plugin.artifactId == TILEPLUGIN_ARTIFACT})?.configuration as Xpp3Dom

		if (configuration) {
			configuration.getChild("tiles")?.children?.each { Xpp3Dom tile ->
				processConfigurationTile(model, tile.value, pomFile)
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

		parseConfiguration(model.model, pomFile, false)
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
