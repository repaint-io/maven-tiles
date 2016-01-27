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
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout
import org.apache.maven.artifact.resolver.ArtifactNotFoundException
import org.apache.maven.artifact.resolver.ArtifactResolutionException
import org.apache.maven.artifact.resolver.ArtifactResolver
import org.apache.maven.artifact.versioning.VersionRange
import org.apache.maven.execution.MavenSession
import org.apache.maven.model.DistributionManagement
import org.apache.maven.model.Model
import org.apache.maven.model.Parent
import org.apache.maven.model.Plugin
import org.apache.maven.model.Repository
import org.apache.maven.model.building.DefaultModelBuilder
import org.apache.maven.model.building.DefaultModelBuildingRequest
import org.apache.maven.model.building.ModelBuilder
import org.apache.maven.model.building.ModelBuildingListener
import org.apache.maven.model.building.ModelBuildingRequest
import org.apache.maven.model.building.ModelBuildingResult
import org.apache.maven.model.building.ModelProcessor
import org.apache.maven.model.building.ModelSource
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

import static io.repaint.maven.tiles.GavUtil.artifactGav
import static io.repaint.maven.tiles.GavUtil.artifactName
import static io.repaint.maven.tiles.GavUtil.modelGav
import static io.repaint.maven.tiles.GavUtil.parentGav
import static io.repaint.maven.tiles.GavUtil.getRealGroupId

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
	public static final String TILEPLUGIN_KEY = "${TILEPLUGIN_GROUP}:${TILEPLUGIN_ARTIFACT}"

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

	/**
	 * Component used to create a repository.
	 */
	ArtifactRepositoryFactory repositoryFactory;

	/**
	 * Map that contains the layouts.
	 */
	@Requirement( role = ArtifactRepositoryLayout.class )
	private Map<String, ArtifactRepositoryLayout> repositoryLayouts;

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
	String applyBeforeParent;

	/**
	 * This specifically goes and asks the repository for the "tile" attachment for this pom, not the
	 * pom itself (because we don't care about that).
	 */
	protected Artifact getArtifactFromCoordinates(String groupId, String artifactId, String type, String classifier, String version) {
		return new DefaultArtifact(groupId, artifactId, VersionRange.createFromVersion(version), "compile",
			type, classifier, new DefaultArtifactHandler(type))
	}

	/**
	 * Return the given Artifact's .pom artifact
	 */
	protected Artifact getPomArtifactForArtifact(Artifact artifact) {
		return getArtifactFromCoordinates(artifact.groupId, artifact.artifactId, 'pom', '', artifact.version)
	}

	protected Artifact resolveTile(Artifact tileArtifact) throws MavenExecutionException {
		try {
			mavenVersionIsolate.resolveVersionRange(tileArtifact)

			// Resolve the .xml file for the tile
			resolver.resolve(tileArtifact, remoteRepositories, localRepository)

			// Resolve the .pom file for the tile
			Artifact pomArtifact = getPomArtifactForArtifact(tileArtifact)
			resolver.resolve(pomArtifact, remoteRepositories, localRepository)

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

		if (gav.size() != 3 && gav.size() != 5) {
			throw new MavenExecutionException("${artifactGav} does not have the form group:artifact:version-range or group:artifact:extension:classifier:version-range", pomFile)
		}

		String groupId = gav[0]
		String artifactId = gav[1]
		String version
		String type = "xml"
		String classifier = ""
		if (gav.size() == 3) {
			version = gav[2]
		} else {
			type = gav[2]
			classifier = gav[3]
			version = gav[4]
		}

		return getArtifactFromCoordinates(groupId, artifactId, type, classifier, version)
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

		repositoryFactory = mavenSession.container.lookup(ArtifactRepositoryFactory)
		repositoryLayouts = mavenSession.lookupMap(ArtifactRepositoryLayout.class.getName()) as Map<String, ArtifactRepositoryLayout>

		List<MavenProject> allProjects = mavenSession.getProjects()
		if (allProjects != null) {
			for (MavenProject currentProject : allProjects) {
				List<String> subModules = currentProject.getModules()
				boolean containsTiles = currentProject.getPluginArtifactMap().keySet().contains(TILEPLUGIN_KEY)

				if (containsTiles) {
					Plugin plugin = currentProject.getPlugin(TILEPLUGIN_KEY);
					if (plugin.isInherited() && subModules != null && subModules.size() > 0) {
						Model currentModel = currentProject.getModel();
						for (MavenProject otherProject : allProjects) {
							Parent otherParent = otherProject.getModel().getParent()
							if(otherParent!=null && parentGav(otherParent).equals(modelGav(currentModel))) {
								//We're in project with children, fail the build immediate. This is both an opinionated choice, but also
								//one of project health - with tile definitions in parent POMs usage of -pl, -am, and -amd maven options
								//are limited.
								throw new MavenExecutionException("Usage of maven-tiles prohibited from multi-module builds where reactor is used as parent.", currentProject.getFile())
							}
						}
					}
					
					orchestrateMerge(currentProject)

					// did we expect but not get a distribution artifact repository?
					if (!currentProject.distributionManagementArtifactRepository) {
						discoverAndSetDistributionManagementArtifactoryRepositoriesIfTheyExist(currentProject)
					}
				}
			}
		}
	}

	/**
	 * If we get here, we have a Tiles project that might have a distribution management section but it is playing
	 * dicky-birds and hasn't set up the distribution management repositories.
	 *
	 * @param project
	 */
	void discoverAndSetDistributionManagementArtifactoryRepositoriesIfTheyExist(MavenProject project) {
		DistributionManagement distributionManagement = project.model.distributionManagement

		if (distributionManagement) {
			if (distributionManagement.repository) {
				project.setReleaseArtifactRepository(repositoryFactory.createDeploymentArtifactRepository(
					distributionManagement.repository.id, distributionManagement.repository.url,
					repositoryLayouts.get( distributionManagement.repository.layout ?: 'default' ), true ))
			}
			if (distributionManagement.snapshotRepository) {
				project.setSnapshotArtifactRepository(repositoryFactory.createDeploymentArtifactRepository(
					distributionManagement.snapshotRepository.id, distributionManagement.snapshotRepository.url,
					repositoryLayouts.get( distributionManagement.snapshotRepository.layout ?: 'default' ), true ))
			}
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
		// Clear collected tiles from previous project in reactor
		processedTiles.clear();
		tileDiscoveryOrder.clear();
		unprocessedTiles.clear();

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

		// Maven 3.2.5 doesn't let you have access to this (package private), 3.3.x does
		def modelBuildingListenerConstructor = Class.forName("org.apache.maven.project.DefaultModelBuildingListener").declaredConstructors[0]
		modelBuildingListenerConstructor.accessible = true
		ModelBuildingListener modelBuildingListener = modelBuildingListenerConstructor.newInstance(project,
			projectBuildingHelper, mavenSession.request.projectBuildingRequest)

		// new org.apache.maven.project.PublicDefaultModelBuildingListener( project,
		//projectBuildingHelper, mavenSession.request.projectBuildingRequest )
		// this allows us to know when the ModelProcessor is called that we should inject the tiles into the
		// parent structure
		ModelSource mainArtifactModelSource = createModelSource(project.file)
		ModelBuildingRequest request = new DefaultModelBuildingRequest(modelSource: mainArtifactModelSource,
			pomFile: project.file, modelResolver: createModelResolver(), modelCache: modelCache,
		  systemProperties: System.getProperties(), userProperties: mavenSession.request.userProperties,
			profiles: mavenSession.request.projectBuildingRequest.profiles,
		  activeProfileIds: mavenSession.request.projectBuildingRequest.activeProfileIds,
			inactiveProfileIds: mavenSession.request.projectBuildingRequest.inactiveProfileIds,
		  modelBuildingListener: modelBuildingListener,
			locationTracking: true, twoPhaseBuilding: true, processPlugins: true)

		boolean tilesInjected = false
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
				
				use(GavUtil) {
					if (model.artifactId == project.artifactId && model.realGroupId == project.groupId
						&& model.realVersion == project.version && model.packaging == project.packaging) {
						// apply after first parent, but only if no explicit parent is set
						if (!applyBeforeParent) {
							injectTilesIntoParentStructure(tiles, model, request)
							tilesInjected = true
						}
					} else if (modelGav(model) == applyBeforeParent) {
						// apply after specific parent
						injectTilesIntoParentStructure(tiles, model, request)
						tilesInjected = true
					} else if (model.packaging == 'tile' || model.packaging == 'pom') {
						TileModel oneOfUs = tiles.find { TileModel tm ->
							Model tileModel = tm.model
							return (model.artifactId == tileModel.artifactId && model.realGroupId == tileModel.realGroupId &&
							  model.realVersion == tileModel.realVersion)
						}

						if (oneOfUs) {
							model = oneOfUs.model
						}
					}
					if(applyBeforeParent && !tilesInjected && model.parent) {
						Artifact parentArtifact = getArtifactFromCoordinates(model.parent.groupId, model.parent.artifactId, 'pom', '', model.parent.version)
						resolver.resolve(parentArtifact, remoteRepositories, localRepository)

						// need to use an artifical ID for the parent (will be filtered out by the resolver)
						model.parent.artifactId += "#" + getRealGroupId(project.model) + "-" + project.artifactId;
						
						if (request.modelCache.get(model.parent.groupId, model.parent.artifactId, model.parent.version,
							org.apache.maven.model.building.ModelCacheTag.RAW.getName())) {
							// tile combination already cached
							tilesInjected = true
						}
					}
				}

				return model
			}
		}

		((DefaultModelBuilder)modelBuilder).setModelProcessor(delegateModelProcessor)

		ModelBuildingResult interimBuild = modelBuilder.build(request)

		ModelBuildingResult finalModel = modelBuilder.build(request, interimBuild)
		if (!tilesInjected && applyBeforeParent) {
			throw new MavenExecutionException("Cannot apply tiles, the expected parent ${applyBeforeParent} is not found.",
				project.file)
		}
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
				int artificialPart = artifactId.indexOf('#');
				if (artificialPart >= 0) {
					// remove artificial part
					artifactId = artifactId.substring(0, artificialPart)
				}
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
				return createModelResolver()
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

		// no setting parent, we have generated an effective model which is now all copied in
		projectModel.build = newModel.build
		projectModel.dependencyManagement = newModel.dependencyManagement
		projectModel.dependencies = newModel.dependencies
		projectModel.repositories = newModel.repositories
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
			applyBeforeParent = configuration.getChild("applyBefore")?.value;
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
