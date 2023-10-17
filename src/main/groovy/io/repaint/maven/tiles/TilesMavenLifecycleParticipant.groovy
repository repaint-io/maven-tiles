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
import org.apache.maven.AbstractMavenLifecycleParticipant
import org.apache.maven.MavenExecutionException
import org.apache.maven.RepositoryUtils
import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.DefaultArtifact
import org.apache.maven.artifact.handler.DefaultArtifactHandler
import org.apache.maven.artifact.repository.ArtifactRepository
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy
import org.apache.maven.artifact.repository.MavenArtifactRepository
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout
import org.apache.maven.artifact.resolver.ArtifactNotFoundException
import org.apache.maven.artifact.resolver.ArtifactResolutionException
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest
import org.apache.maven.artifact.resolver.ResolutionErrorHandler
import org.apache.maven.artifact.versioning.VersionRange
import org.apache.maven.execution.MavenSession
import org.apache.maven.model.Build
import org.apache.maven.model.Dependency
import org.apache.maven.model.DistributionManagement
import org.apache.maven.model.Model
import org.apache.maven.model.Parent
import org.apache.maven.model.Plugin
import org.apache.maven.model.PluginExecution
import org.apache.maven.model.PluginManagement
import org.apache.maven.model.Repository
import org.apache.maven.model.RepositoryPolicy
import org.apache.maven.model.building.DefaultModelBuilder
import org.apache.maven.model.building.DefaultModelBuildingRequest
import org.apache.maven.model.building.FileModelSource
import org.apache.maven.model.building.ModelBuilder
import org.apache.maven.model.building.ModelBuildingListener
import org.apache.maven.model.building.ModelBuildingRequest
import org.apache.maven.model.building.ModelBuildingResult
import org.apache.maven.model.building.ModelProcessor
import org.apache.maven.model.building.ModelSource2
import org.apache.maven.model.io.ModelParseException
import org.apache.maven.model.resolution.InvalidRepositoryException
import org.apache.maven.model.resolution.ModelResolver
import org.apache.maven.model.resolution.UnresolvableModelException
import org.apache.maven.plugin.MojoExecution
import org.apache.maven.plugin.PluginParameterExpressionEvaluator
import org.apache.maven.plugin.descriptor.MojoDescriptor
import org.apache.maven.project.DefaultModelBuildingListener
import org.apache.maven.project.MavenProject
import org.apache.maven.project.ProjectBuilder
import org.apache.maven.project.ProjectBuildingHelper
import org.apache.maven.project.ProjectBuildingRequest
import org.apache.maven.repository.RepositorySystem
import org.apache.maven.shared.filtering.MavenFileFilter
import org.apache.maven.shared.filtering.MavenResourcesFiltering
import org.codehaus.plexus.util.xml.Xpp3Dom
import org.codehaus.plexus.util.xml.pull.XmlPullParserException
import org.eclipse.aether.impl.VersionRangeResolver
import org.eclipse.aether.resolution.VersionRangeRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.xml.sax.SAXParseException

import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

import static io.repaint.maven.tiles.Constants.TILEPLUGIN_ARTIFACT
import static io.repaint.maven.tiles.Constants.TILEPLUGIN_GROUP
import static io.repaint.maven.tiles.Constants.TILE_POM
import static io.repaint.maven.tiles.GavUtil.artifactGav
import static io.repaint.maven.tiles.GavUtil.artifactName
import static io.repaint.maven.tiles.GavUtil.modelGav
import static io.repaint.maven.tiles.GavUtil.modelRealGa
import static io.repaint.maven.tiles.GavUtil.parentGav

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
@Singleton
@Named("TilesMavenLifecycleParticipant")
class TilesMavenLifecycleParticipant extends AbstractMavenLifecycleParticipant {

	public static final String TILEPLUGIN_KEY = "${TILEPLUGIN_GROUP}:${TILEPLUGIN_ARTIFACT}"

	Logger logger = LoggerFactory.getLogger(getClass())

	@Inject
	ResolutionErrorHandler resolutionErrorHandler

	@Inject
	ProjectBuilder projectBuilder

	@Inject
	ModelBuilder modelBuilder

	@Inject
	ModelProcessor modelProcessor

	@Inject
	ProjectBuildingHelper projectBuildingHelper

	@Inject
	MavenArtifactRepository repositoryFactory

	@Inject
	RepositorySystem repository;

	@Inject
	Map<String, ArtifactRepositoryLayout> repositoryLayouts

	@Inject
	VersionRangeResolver versionRangeResolver

	@Inject
	MavenFileFilter mavenFileFilter

	@Inject
	MavenResourcesFiltering mavenResourcesFiltering

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
	Map<String,TileModel> tilesByExecution = [:];

	String applyBeforeParent;

	/**
	 * This specifically goes and asks the repository for the "tile" attachment for this pom, not the
	 * pom itself (because we don't care about that).
	 */
	protected static Artifact getArtifactFromCoordinates(String groupId, String artifactId, String type, String classifier, String version) {
		return new DefaultArtifact(groupId, artifactId, VersionRange.createFromVersion(version), "compile",
			type, classifier, new DefaultArtifactHandler(type))
	}

	/**
	 * Return the given Artifact's .pom artifact
	 */
	protected static Artifact getPomArtifactForArtifact(Artifact artifact) {
		return getArtifactFromCoordinates(artifact.groupId, artifact.artifactId, 'pom', '', artifact.version)
	}

	protected Artifact resolveTile(MavenSession mavenSession, MavenProject project, Artifact tileArtifact) throws MavenExecutionException {
		// try to find tile from reactor
		if (mavenSession != null) {
			List<MavenProject> allProjects = mavenSession.getProjects()
			if (allProjects != null) {
				for (MavenProject currentProject : allProjects) {
					if (currentProject.groupId == tileArtifact.groupId && currentProject.artifactId == tileArtifact.artifactId && currentProject.version == tileArtifact.version) {
						tileArtifact.file = FilteringHelper.getTile(currentProject, mavenSession, mavenFileFilter, mavenResourcesFiltering)
						return tileArtifact
					}
				}
			}
		}

		try {
			resolveVersionRange(project, tileArtifact)

			// Resolve the .xml file for the tile
			final def tileReq = new ArtifactResolutionRequest()
				.setArtifact(tileArtifact)
				.setRemoteRepositories(project?.remoteArtifactRepositories)
				.setLocalRepository(mavenSession?.localRepository)

			def tilesResult = repository.resolve(tileReq);
			resolutionErrorHandler.throwErrors(tileReq, tilesResult)

			// Resolve the .pom file for the tile
			Artifact pomArtifact = getPomArtifactForArtifact(tileArtifact)
			final def pomReq = new ArtifactResolutionRequest()
				.setArtifact(pomArtifact)
				.setRemoteRepositories(project?.remoteArtifactRepositories)
				.setLocalRepository(mavenSession?.localRepository)

			def pomResult = repository.resolve(pomReq)
			resolutionErrorHandler.throwErrors(pomReq, pomResult)

			// When resolving from workspace (e.g. m2e, intellij) we might receive the path to pom.xml instead of the attached tile
			if (tileArtifact.file && tileArtifact.file.name == "pom.xml") {
				File tileFile = new File(tileArtifact.file.parent, TILE_POM)
				if (!tileFile.exists()) {
					throw new MavenExecutionException("Tile ${artifactGav(tileArtifact)} cannot be resolved.",
						tileFile as File)
				}

				ProjectBuildingRequest pbr = mavenSession.request.projectBuildingRequest
				MavenProject tileProject = projectBuilder.build(pomResult.originatingArtifact, pbr).getProject()
				tileArtifact.file = FilteringHelper.getTile(tileProject, mavenSession, mavenFileFilter, mavenResourcesFiltering)
			}

			if (System.getProperty("performRelease")?.asBoolean()) {

				if (tileArtifact.version.endsWith("-SNAPSHOT")) {

					throw new MavenExecutionException("Tile ${artifactGav(tileArtifact)} is a SNAPSHOT and we are releasing.",
						tileArtifact.getFile() as File)

				}
			}

		} catch (ArtifactResolutionException e) {
			throw new MavenExecutionException(e.getMessage(), e)
		} catch (ArtifactNotFoundException e) {
			throw new MavenExecutionException(e.getMessage(), e)
		}

		return tileArtifact
	}

	protected static Artifact turnPropertyIntoUnprocessedTile(String artifactGav, File pomFile)
	  throws MavenExecutionException {

		String[] gav = artifactGav.tokenize(":")

		if (gav.size() != 3 && gav.size() != 5) {
			throw new MavenExecutionException("${artifactGav} does not have the form group:artifact:version-range or group:artifact:extension:classifier:version-range", pomFile as File)
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

	/**
	 * Invoked after all MavenProject instances have been created.
	 *
	 * This callback is intended to allow extensions to manipulate MavenProjects
	 * before they are sorted and actual build execution starts.
	 */
	void afterProjectsRead(MavenSession mavenSession)
		throws MavenExecutionException {

		this.mavenSession = mavenSession

		this.modelCache = new NotDefaultModelCache(mavenSession)

		// disabled explicit lookup as these seem to be injected just fine. Are these required for eclipse m2e>
		//repositoryFactory = mavenSession.container.lookup(ArtifactRepositoryFactory)
		//repositoryLayouts = mavenSession.lookupMap(ArtifactRepositoryLayout.class.getName()) as Map<String, ArtifactRepositoryLayout>

		List<MavenProject> allProjects = mavenSession.getProjects()
		if (allProjects != null) {
			for (MavenProject currentProject : allProjects) {
				boolean containsTiles = currentProject.getPluginArtifactMap().keySet().contains(TILEPLUGIN_KEY)

				if (containsTiles) {
					Plugin plugin = currentProject.getPlugin(TILEPLUGIN_KEY);
					List<String> subModules = currentProject.getModules()
					if (plugin.isInherited() && subModules != null && subModules.size() > 0) {
						Model currentModel = currentProject.getModel();
						for (MavenProject otherProject : allProjects) {
							Parent otherParent = otherProject.getModel().getParent()
							if (otherParent != null && parentGav(otherParent) == modelGav(currentModel)) {
								// We're in project with children, fail the build immediately. This is both an opinionated choice, but also
								// one of project health - with tile definitions in parent POMs usage of -pl, -am, and -amd maven options
								// are limited.
								throw new MavenExecutionException("Usage of maven-tiles prohibited from multi-module builds where reactor is used as parent.", currentProject.getFile())
							}
						}
					}

					orchestrateMerge(mavenSession, currentProject)

					// did we expect but not get a distribution artifact repository?
					if (!currentProject.distributionManagementArtifactRepository) {
						discoverAndSetDistributionManagementArtifactoryRepositoriesIfTheyExist(currentProject)
					}
				}
			}
		}
	}

	ArtifactRepositoryPolicy getArtifactRepositoryPolicy(RepositoryPolicy policy) {
		return new ArtifactRepositoryPolicy(Boolean.valueOf(policy.enabled),
				policy.updatePolicy, policy.checksumPolicy)
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

				ArtifactRepository repo = new MavenArtifactRepository(
						distributionManagement.repository.id,
						getReleaseDistributionManagementRepositoryUrl(project),
						repositoryFactory.layout,
						getArtifactRepositoryPolicy(distributionManagement.repository.snapshots),
						getArtifactRepositoryPolicy(distributionManagement.repository.releases))
				project.setReleaseArtifactRepository(repo)

			}
			if (distributionManagement.snapshotRepository) {

				ArtifactRepository repo = new MavenArtifactRepository(
						distributionManagement.snapshotRepository.id,
						getSnapshotDistributionManagementRepositoryUrl(project),
						repositoryFactory.layout,
						getArtifactRepositoryPolicy(distributionManagement.snapshotRepository.snapshots),
						getArtifactRepositoryPolicy(distributionManagement.snapshotRepository.releases))
				project.setReleaseArtifactRepository(repo)

			}
		}
	}

	/**
	 * Distribution management repositories don't have to define the URL.  They may delegate to the to
	 * 'altReleaseDeploymentRepository' or 'altDeploymentRepository' property.  According to Maven documentation
	 * at https://maven.apache.org/plugins/maven-deploy-plugin/deploy-mojo.html, 'altReleaseDeploymentRepository' if
	 * defined is used first, then 'altDeploymentRepository', and then whatever is specified in the distribution
	 * management section.
	 *
	 * @param project
	 *
	 * @return the correct URL to use for the release distribution or NULL if no URL is specified
	 */
	private static String getReleaseDistributionManagementRepositoryUrl(MavenProject project) {
		DistributionManagement distributionManagement = project.model.distributionManagement
		Properties properties = project.properties;

		String url = distributionManagement.repository.url
		String altReleaseUrl = properties.getProperty("altReleaseDeploymentRepository");
		String altUrl = properties.getProperty("altDeploymentRepository");

		return altReleaseUrl != null ? altReleaseUrl : (altUrl != null ? altUrl : url);
	}

	/**
	 * Distribution management repositories don't have to define the URL.  They may delegate to the to
	 * 'altSnapshotDeploymentRepository' or 'altDeploymentRepository' property.  According to Maven documentation
	 * at https://maven.apache.org/plugins/maven-deploy-plugin/deploy-mojo.html, 'altSnapshotDeploymentRepository' if
	 * defined is used first, then 'altDeploymentRepository', and then whatever is specified in the distribution
	 * management section.
	 *
	 * @param project
	 *
	 * @return the correct URL to use for the snapshot distribution or NULL if no URL is specified
	 */
	private static String getSnapshotDistributionManagementRepositoryUrl(MavenProject project) {
		DistributionManagement distributionManagement = project.model.distributionManagement
		Properties properties = project.properties;

		String url = distributionManagement.snapshotRepository.url
		String altSnapshotUrl = properties.getProperty("altSnapshotDeploymentRepository");
		String altUrl = properties.getProperty("altDeploymentRepository");

		return altSnapshotUrl != null ? altSnapshotUrl : (altUrl != null ? altUrl : url);
	}

	/**
	 * Merges the files over the top of the project, and then the individual project back over the top.
	 * The reason for this is that the super pom and packaging can set plugin versions. This allows the tiles
	 * to overwrite those, and then if they are manually specified in the pom, they then get set again.

	 * @param project - the currently evaluated project
	 * @throws MavenExecutionException
	 */
	protected void orchestrateMerge(MavenSession mavenSession, MavenProject project) throws MavenExecutionException {
		// Clear collected tiles from previous project in reactor
		processedTiles.clear()
		tileDiscoveryOrder.clear()
		unprocessedTiles.clear()
		tilesByExecution.clear()

		// collect the first set of tiles
		parseConfiguration(project.model, project.file)

		// collect any unprocessed tiles, and process them causing them to potentially load more unprocessed ones
		loadAllDiscoveredTiles(mavenSession, project)

		// don't do anything if there are no tiles
		if (processedTiles) {
			thunkModelBuilder(project)
		}
	}

	@CompileStatic(TypeCheckingMode.SKIP)
	protected void thunkModelBuilder(MavenProject project) {
		List<TileModel> tiles = processedTiles.values().collect({it.tileModel})

		if (!tiles) return

		ModelBuildingListener modelBuildingListener = new DefaultModelBuildingListener(project,
			projectBuildingHelper, mavenSession.request.projectBuildingRequest)

		// new org.apache.maven.project.PublicDefaultModelBuildingListener( project,
		//projectBuildingHelper, mavenSession.request.projectBuildingRequest )
		// this allows us to know when the ModelProcessor is called that we should inject the tiles into the
		// parent structure
		ModelSource2 mainArtifactModelSource = createModelSource(project.file)
		ModelBuildingRequest request = new DefaultModelBuildingRequest(modelSource: mainArtifactModelSource,
			pomFile: project.file, modelResolver: createModelResolver(project), modelCache: modelCache,
		  systemProperties: mavenSession.request.systemProperties, userProperties: mavenSession.request.userProperties,
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

					// when we reference a submodule of a CI Friendly module in a pom (i.e. a workspace pom in Eclipse)
					// we have no version in the submodule.
					// I.E. module A1 has parent A. Both use CI Friendly version ${revision}. A has a property "revision" with value "MAIN-SNAPSHOT".
					// we have a pom for our Eclipse workspace that includes A1.
					// If the workspace pom includes only A1 it works. But if it contains B and B has a dependency to A1 it
					// fails with NPE.
					// Here we have to ask the project for its version
					//
					if (model.version == null && '${revision}'.equals(model.realVersion)) {
						model.parent.version = project.version;
					}


					// evaluate the model version to deal with CI friendly build versions.
					// "0-SNAPSHOT" indicates an undefined property.
					if (model.artifactId == project.artifactId && model.realGroupId == project.groupId
						&& (evaluateString(model.realVersion) == project.version || evaluateString(model.realVersion) == "0-SNAPSHOT" || evaluateString(model.realVersion) == null)
						&& model.packaging == project.packaging) {
						// we're at the first (project) level. Apply tiles here if no explicit parent is set
						if (!applyBeforeParent) {
							injectTilesIntoParentStructure(tiles, model, request)
							tilesInjected = true
						}
					} else if (modelRealGa(model) == applyBeforeParent) {
						// we're at the level with the explicitly selected parent. Apply the tiles here
						injectTilesIntoParentStructure(tiles, model, request)
						tilesInjected = true
					} else if (model.packaging == 'tile' || model.packaging == 'pom') {
						// we could be at a parent that is a tile. In this case return the precomputed model
						TileModel oneOfUs = tiles.find { TileModel tm ->
							Model tileModel = tm.model
							return (model.artifactId == tileModel.artifactId && model.realGroupId == tileModel.realGroupId &&
							  model.realVersion == tileModel.realVersion)
						}

						if (oneOfUs) {
							model = oneOfUs.model
						}
					}

					// if we want to apply tiles at a specific parent and have not come by it yet, we need
					// to make the parent reference project specific, so that it will not pick up a cached
					// version. We do this by adding a project specific suffix, which will later be removed
					// when actually loading that parent.
					if (applyBeforeParent && !tilesInjected && model.parent) {
						// remove the parent from the cache which causes it to be reloaded through our ModelProcessor
						request.modelCache.put(model.parent.groupId, model.parent.artifactId, model.parent.version,
							org.apache.maven.model.building.ModelCacheTag.RAW.getName(), null)
					}
				}

				return model
			}

		}

		DefaultModelBuilder mb = ((DefaultModelBuilder)modelBuilder).setModelProcessor(delegateModelProcessor)
		try {
			ModelBuildingResult interimBuild = mb.build(request)

			// this will revert the tile dependencies inserted by TilesProjectBuilder, which is fine since by now they
			// served their purpose of correctly ordering projects, so we can now do without them
			ModelBuildingResult finalModel = mb.build(request, interimBuild)
			if (!tilesInjected && applyBeforeParent) {
				throw new MavenExecutionException("Cannot apply tiles, the expected parent ${applyBeforeParent} is not found.",
					project.file)
			}
			copyModel(project, finalModel.effectiveModel)
		} finally {
			// restore original ModelProcessor
			((DefaultModelBuilder)modelBuilder).setModelProcessor(modelProcessor)
		}
	}

	ModelSource2 createModelSource(File pomFile) {
		return new ModelSource2() {

			InputStream stream = pomFile.newInputStream()

			@Override
			InputStream getInputStream() throws IOException {
				return stream
			}

			@Override
			String getLocation() {
				return pomFile.absolutePath
			}

			@Override
			URI getLocationURI() {
				return pomFile.toURI()
			}

			@Override
			ModelSource2 getRelatedSource(String relPath) {
				File relatedPom = new File(pomFile.parentFile, relPath)
				if (relatedPom.isDirectory()) {
					relatedPom = new File(relatedPom, "pom.xml")
				}
				if (relatedPom.isFile()&& relatedPom.canRead()) {
					return createModelSource(relatedPom.canonicalFile)
				}
				return null
			}

		}
	}

	protected ModelResolver createModelResolver(MavenProject project) {
		// this is for resolving parents, so always poms

		return new ModelResolver() {

			@Override
			ModelSource2 resolveModel(String groupId, String artifactId, String version) throws UnresolvableModelException {
				Artifact artifact = new DefaultArtifact(groupId, artifactId, VersionRange.createFromVersion(version), "compile",
					"pom", null, new DefaultArtifactHandler("pom"))

				resolveVersionRange(project, artifact)
				final def req = new ArtifactResolutionRequest()
					.setArtifact(artifact)
					.setRemoteRepositories(project?.remoteArtifactRepositories)
					.setLocalRepository(mavenSession?.localRepository)

				repository.resolve(req)

				return createModelSource(artifact.file)
			}

			@Override
			ModelSource2 resolveModel(Parent parent) throws UnresolvableModelException {
				return resolveModel(parent.groupId, parent.artifactId, parent.version)
			}

			// this exists in later versions of maven
			ModelSource2 resolveModel(Dependency dependency) throws UnresolvableModelException {
				return resolveModel(dependency.groupId, dependency.artifactId, dependency.version)
			}

			@Override
			void addRepository(Repository repository) throws InvalidRepositoryException {
			}

			@Override
			void addRepository(Repository repository, boolean wat) throws InvalidRepositoryException {
			}

			@Override
			ModelResolver newCopy() {
				return createModelResolver(project)
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
		request.modelCache.put(model.groupId, model.artifactId, evaluateString(model.version),
			org.apache.maven.model.building.ModelCacheTag.RAW.getName(),
			org.apache.maven.model.building.ModelCacheTag.RAW.intoCache(
				new org.apache.maven.model.building.ModelData(new FileModelSource(pomFile), model, model.groupId, model.artifactId, model.version)))
	}

	/**
	 * Creates a chain of tile parents based on how we discovered them and inserts them into the parent
	 * chain, above this project and before this project's parent (if any)
	 *
	 * @param tiles - tiles that should make up part of the collection
	 * @param pomModel - the current project
	 * @param request - the request to build the current project
	 */
	void injectTilesIntoParentStructure(List<TileModel> tiles, Model pomModel, ModelBuildingRequest request) {
		Parent originalParent = pomModel.parent
		Model lastPom = pomModel
		File lastPomFile = request.pomFile

		// fix up the version of the originalParent
		if (originalParent != null) {
			originalParent.version = evaluateString(originalParent.version)
		}

		if (tiles) {
			// evaluate the model version to deal with CI friendly build versions
			logger.info("--- tiles-maven-plugin: Injecting ${tiles.size()} tiles as intermediary parent artifacts for ${evaluateString(modelRealGa(pomModel))}...")
			logger.info("Mixed '${evaluateString(modelGav(pomModel))}' with tile '${evaluateString(modelGav(tiles.first().model))}' as its new parent.")

			// if there is a parent make sure the inherited groupId / version is correct
			if (!pomModel.groupId) {
				pomModel.groupId = originalParent.groupId
				logger.info("Explicitly set groupId to '${pomModel.groupId}' from original parent '${parentGav(originalParent)}'.")
			}
			if (!pomModel.version) {
				pomModel.version = originalParent.version
				logger.info("Explicitly set version to '${pomModel.version}' from original parent '${parentGav(originalParent)}'.")
			}
		}

		tiles.each { TileModel tileModel ->
			Model model = tileModel.model

			Parent modelParent = new Parent(groupId: model.groupId, version: evaluateString(model.version), artifactId: model.artifactId)
			lastPom.parent = modelParent

			if (pomModel != lastPom) {
				putModelInCache(lastPom, request, lastPomFile)
				logger.info("Mixed '${evaluateString(modelGav(lastPom))}' with tile '${evaluateString(parentGav(modelParent))}' as its new parent.")
			}

			lastPom = model
			lastPomFile = tileModel.tilePom
		}

		lastPom.parent = originalParent
		if (originalParent) {
			if (originalParent.relativePath != null && !originalParent.relativePath.isBlank()) {
				logger.info("Mixed '${evaluateString(modelGav(lastPom))}' with original parent '${parentGav(originalParent)}' via ${originalParent.relativePath} as its new top level parent.")
			} else {
				logger.info("Mixed '${evaluateString(modelGav(lastPom))}' with original parent '${parentGav(originalParent)}' as its new top level parent.")
			}
			logger.info("")
		}

		if (pomModel != lastPom) {
			putModelInCache(lastPom, request, lastPomFile)
		}

	}

	protected static void copyModel(MavenProject project, Model newModel) {
		// no setting parent, we have generated an effective model which is now all copied in
		Model projectModel = project.model
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
		projectModel.reporting = newModel.reporting


		// update model (test) source directory, which is the first entry and might have been set through a tile
		if (projectModel.build.sourceDirectory) {
			project.compileSourceRoots[0] = projectModel.build.sourceDirectory;
		}
		if (projectModel.build.testSourceDirectory) {
			project.testCompileSourceRoots[0] = projectModel.build.testSourceDirectory;
		}

		// for tile provided LifecycleMapping in m2e we need to modifiy the original model
		Plugin m2ePlugin = projectModel.build.pluginManagement?.getPluginsAsMap()?.get("org.eclipse.m2e:lifecycle-mapping")
		if (m2ePlugin) {
			Build build = project.originalModel.build
			if (!build) {
				build = new Build()
				project.originalModel.build = build
			}
			if (build.pluginManagement) {
				build.pluginManagement = build.pluginManagement.clone()
			} else {
				build.pluginManagement = new PluginManagement()
			}
			build.pluginManagement.addPlugin(m2ePlugin)
		}
	}

	protected void loadAllDiscoveredTiles(MavenSession mavenSession, MavenProject project) throws MavenExecutionException {

		List<TileModel> mergeSourceTiles = []
		Map<String, Artifact> rootTiles = [:]
		rootTiles.putAll(unprocessedTiles)
		unprocessedTiles.clear()

		for (String rootTile : rootTiles.keySet()) {
			unprocessedTiles.put(rootTile, rootTiles.get(rootTile))

			while (unprocessedTiles.size() > 0) {
				String unresolvedTile = unprocessedTiles.keySet().iterator().next()

				Artifact resolvedTile = resolveTile(mavenSession, project, unprocessedTiles.remove(unresolvedTile))

				TileModel tileModel = loadModel(resolvedTile)

				// ensure we have resolved the tile (it could come from a non-tile model)
				if (tileModel) {
					if (hasProperty(tileModel, 'tile-merge-source')) {
						// hold and merge into target later
						mergeSourceTiles.add(tileModel)
					} else {
						if (hasProperty(tileModel, 'tile-merge-target')) {
							registerTargetTile(tileModel)
						}
						String tileName = artifactName(resolvedTile)
						processedTiles.put(tileName, new ArtifactModel(resolvedTile, tileModel))
						parseForExtendedSyntax(tileModel, resolvedTile.getFile())
					}
				}
			}
		}

		// merge all the source tiles last
		for (TileModel mergeTile : mergeSourceTiles) {
			mergeTileIntoTarget(mergeTile)
		}

		ensureAllTilesDiscoveredAreAccountedFor()
	}

	private static boolean hasProperty(TileModel tileModel, String propertyKey) {
		// remove these properties, we don't want them in the merged result
		return 'true' == tileModel.model?.properties?.remove(propertyKey)
	}

	private List<Plugin> registerTargetTile(TileModel targetTile) {
		return mergeTile(targetTile, false)
	}

	private List<Plugin> mergeTileIntoTarget(TileModel fragmentTile) {
		return mergeTile(fragmentTile, true)
	}

	private List<Plugin> mergeTile(TileModel tileModel, boolean mergeIntoTarget) {

		tileModel.model?.build?.plugins?.each { plugin ->
			plugin.executions.each { execution ->
				String eid = "$plugin.groupId:$plugin.artifactId:$execution.id"
				if (!mergeIntoTarget) {
					tilesByExecution.put(eid, tileModel)
				} else {
					String fragmentId = "$tileModel.model.groupId:$tileModel.model.artifactId"
					TileModel targetTile = tilesByExecution.get(eid)
					if (targetTile) {
						String targetId = "$targetTile.model.groupId:$targetTile.model.artifactId"
						logger.info("Merged tile $fragmentId into $targetId plugin:$eid")
						mergeProperties(targetTile, tileModel)
						mergeExecutionConfiguration(targetTile, execution, eid)
					} else {
						String missingTileId = tileModel.model?.properties?.getProperty('tile-merge-expected-target')
						if (missingTileId) {
							throw new MavenExecutionException("Please add missing tile $missingTileId. This is required for tile $fragmentId, plugin:$eid", (Throwable)null)
						} else {
							throw new MavenExecutionException("Error with tile $fragmentId - Missing target tile required with plugin:$eid. Please check the documentation for this tile.", (Throwable)null)
						}
					}
				}
			}
		}
	}

	/**
	 * Merge the properties from the mergeTile into targetTile.
	 */
	private static void mergeProperties(TileModel targetTile, TileModel mergeTile) {
		if (mergeTile.model.properties) {
			targetTile.model.properties.putAll(mergeTile.model.properties)
		}
	}

	/**
	 * Merge the execution configuration from mergeExecution into the target tile.
	 */
	private void mergeExecutionConfiguration(TileModel targetTile, PluginExecution mergeExecution, String eid) {

		targetTile.model?.build?.plugins?.each { plugin ->
			plugin.executions.each { execution ->
				String targetEid = "$plugin.groupId:$plugin.artifactId:$execution.id"
				if (targetEid.equals(eid)) {
					Xpp3Dom configuration = (Xpp3Dom)execution.configuration
					String appendElementName = configuration.getAttribute('tiles-append')
					if (appendElementName) {
						Xpp3Dom target = configuration.getChild(appendElementName)
						Xpp3Dom source = ((Xpp3Dom)mergeExecution.configuration).getChild(appendElementName)
						// append from source into target
						Xpp3Dom.mergeXpp3Dom(target, source, false)

						logger.debug("merged execution configuration - $eid")
					}
				}
			}
		}
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
	@CompileStatic(TypeCheckingMode.SKIP)
	protected void parseConfiguration(Model model, File pomFile) {
		def configuration = model.build?.plugins
			?.find({ Plugin plugin -> plugin.groupId == TILEPLUGIN_GROUP && plugin.artifactId == TILEPLUGIN_ARTIFACT})
			?.configuration

		if (configuration) {
			configuration.getChild("tiles")?.children?.each { tile ->
				processConfigurationTile(model, tile.value, pomFile)
			}
			applyBeforeParent = configuration.getChild("applyBefore")?.value
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

				// move the entry to the end of the map
				unprocessedTiles.put(depName, unprocessedTiles.remove(depName))
			} else {
				logger.debug("Adding tile ${artifactGav(unprocessedTile)}")

				unprocessedTiles.put(depName, unprocessedTile)
				tileDiscoveryOrder.add(depName)
			}
		} else {
			logger.warn(String.format("tiles-maven-plugin in project %s requested for same tile dependency %s",
				modelGav(model), artifactGav(unprocessedTile)))

			// move the entry to the end of the map
			processedTiles.put(depName, processedTiles.remove(depName))
		}
	}

	/**
	 * Evaluate a string for property substitution.  This method is null tolerant and utilizes the mavenSession
	 * class member if set.
	 *
	 * @param value The String to evaluate
	 * @return The evaluated String
	 */
	protected String evaluateString(String value) {
		if ((value != null) && (mavenSession != null)) {
			return new PluginParameterExpressionEvaluator(mavenSession, new MojoExecution(new MojoDescriptor()))
				.evaluate(value, String.class)
		} else {
			return value
		}
	}

	void resolveVersionRange(MavenProject project, Artifact tileArtifact) {
		def versionRangeRequest = new VersionRangeRequest(RepositoryUtils.toArtifact(tileArtifact),
			RepositoryUtils.toRepos(project?.remoteArtifactRepositories), null)

		def versionRangeResult = versionRangeResolver.resolveVersionRange(mavenSession?.repositorySession, versionRangeRequest)

		if (versionRangeResult?.versions) {
			tileArtifact.version = versionRangeResult.highestVersion
		}
	}

}
