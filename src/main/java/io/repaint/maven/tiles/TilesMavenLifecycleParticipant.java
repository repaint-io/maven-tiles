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
package io.repaint.maven.tiles;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ResolutionErrorHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.*;
import org.apache.maven.model.building.DefaultModelBuilder;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingListener;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingResult;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.model.building.ModelSource2;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.project.DefaultModelBuildingListener;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingHelper;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.maven.shared.filtering.MavenResourcesFiltering;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.impl.VersionRangeResolver;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import manifold.ext.rt.api.Jailbreak;

import static io.repaint.maven.tiles.Constants.TILEPLUGIN_ARTIFACT;
import static io.repaint.maven.tiles.Constants.TILEPLUGIN_GROUP;
import static io.repaint.maven.tiles.Constants.TILE_POM;
import static io.repaint.maven.tiles.GavUtil.artifactGav;
import static io.repaint.maven.tiles.GavUtil.artifactName;
import static io.repaint.maven.tiles.GavUtil.getRealGroupId;
import static io.repaint.maven.tiles.GavUtil.getRealVersion;
import static io.repaint.maven.tiles.GavUtil.modelGav;
import static io.repaint.maven.tiles.GavUtil.modelRealGa;
import static io.repaint.maven.tiles.GavUtil.parentGav;

import static org.apache.maven.artifact.repository.ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN;
import static org.apache.maven.artifact.repository.ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS;

import static java.lang.Boolean.parseBoolean;
import static java.util.stream.Collectors.toList;

/**
 * Fetches all dependencies defined in the POM `configuration`.
 *
 * Merging operation is delegated to {@link DefaultModelBuilder}
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 * @author: Mark Derricutt - https://plus.google.com/+MarkDerricutt
 *
 */
@Singleton
@Named("TilesMavenLifecycleParticipant")
class TilesMavenLifecycleParticipant extends AbstractMavenLifecycleParticipant {
  public static final String TILEPLUGIN_KEY = "${TILEPLUGIN_GROUP}:${TILEPLUGIN_ARTIFACT}";

  Logger logger = LoggerFactory.getLogger(getClass());

  @Inject
  ResolutionErrorHandler resolutionErrorHandler;

  @Inject
  ProjectBuilder projectBuilder;

  @Inject
  ModelBuilder modelBuilder;

  @Inject
  ModelProcessor modelProcessor;

  @Inject
  ProjectBuildingHelper projectBuildingHelper;

  @Inject
  MavenArtifactRepository repositoryFactory;

  @Inject
  RepositorySystem repository;

  @Inject
  VersionRangeResolver versionRangeResolver;

  @Inject
  MavenFileFilter mavenFileFilter;

  @Inject
  MavenResourcesFiltering mavenResourcesFiltering;

  NotDefaultModelCache modelCache;

  MavenSession mavenSession;

  class ArtifactModel {
    public Artifact artifact;
    public TileModel tileModel;

    public ArtifactModel(Artifact artifact, TileModel tileModel) {
      this.artifact = artifact;
      this.tileModel = tileModel;
    }
  }

  /**
   * We store the groupId:artifactId -> Artifact of those tiles we have discovered in our meanderings through
   * the
   */
  Map<String, ArtifactModel> processedTiles = new HashMap<>();
  List<String> tileDiscoveryOrder = new ArrayList<>();
  Map<String, Artifact> unprocessedTiles = new HashMap<>();
  Map<String, TileModel> tilesByExecution = new HashMap<>();

  String applyBeforeParent;

  /**
   * This specifically goes and asks the repository for the "tile" attachment for this pom, not the
   * pom itself (because we don't care about that).
   */
  protected static Artifact getArtifactFromCoordinates(
      String groupId, String artifactId, String type, String classifier, String version) {
    return new DefaultArtifact(
        groupId,
        artifactId,
        VersionRange.createFromVersion(version),
        "compile",
        type,
        classifier,
        new DefaultArtifactHandler(type));
  }

  /**
   * Return the given Artifact's .pom artifact
   */
  protected static Artifact getPomArtifactForArtifact(Artifact artifact) {
    return getArtifactFromCoordinates(artifact.getGroupId(), artifact.getArtifactId(), "pom", "", artifact.getVersion());
  }

  protected Artifact resolveTile(MavenSession mavenSession, MavenProject project, Artifact tileArtifact)
      throws MavenExecutionException, MavenFilteringException {
    // try to find tile from reactor
    if (mavenSession != null) {
      List<MavenProject> allProjects = mavenSession.getProjects();
      if (allProjects != null) {
        for (MavenProject currentProject : allProjects) {
          if (currentProject.getGroupId().equals(tileArtifact.getGroupId()) &&
              currentProject.getArtifactId().equals(tileArtifact.getArtifactId()) &&
              currentProject.getVersion().equals(tileArtifact.getVersion())) {
            tileArtifact.setFile(FilteringHelper.getTile(currentProject, mavenSession, mavenFileFilter, mavenResourcesFiltering));
            return tileArtifact;
          }
        }
      }
    }

    try {
      resolveVersionRange(project, tileArtifact);
      List<ArtifactRepository> remoteRepositories = project == null ? null : project.getRemoteArtifactRepositories();
      ArtifactRepository localRepository = mavenSession == null ? null : mavenSession.getLocalRepository();

      // Resolve the .xml file for the tile
      ArtifactResolutionRequest tileReq = new ArtifactResolutionRequest()
                                              .setArtifact(tileArtifact)
                                              .setRemoteRepositories(remoteRepositories)
                                              .setLocalRepository(localRepository);

      ArtifactResolutionResult tilesResult = repository.resolve(tileReq);
      resolutionErrorHandler.throwErrors(tileReq, tilesResult);

      // Resolve the .pom file for the tile
      Artifact pomArtifact = getPomArtifactForArtifact(tileArtifact);
      ArtifactResolutionRequest pomReq = new ArtifactResolutionRequest()
                                             .setArtifact(pomArtifact)
                                             .setRemoteRepositories(remoteRepositories)
                                             .setLocalRepository(localRepository);

      ArtifactResolutionResult pomResult = repository.resolve(pomReq);
      resolutionErrorHandler.throwErrors(pomReq, pomResult);

      // When resolving from workspace (e.g. m2e, intellij) we might receive the path to pom.xml instead of the attached tile
      if (tileArtifact.getFile() != null && tileArtifact.getFile().getName() == "pom.xml") {
        File tileFile = new File(tileArtifact.getFile().getParent(), TILE_POM);
        if (!tileFile.exists()) {
          throw new MavenExecutionException("Tile ${artifactGav(tileArtifact)} cannot be resolved.", tileFile);
        }

        ProjectBuildingRequest pbr = mavenSession.getRequest().getProjectBuildingRequest();
        MavenProject tileProject = projectBuilder.build(pomResult.getOriginatingArtifact(), pbr).getProject();
        tileArtifact.setFile(FilteringHelper.getTile(tileProject, mavenSession, mavenFileFilter, mavenResourcesFiltering));
      }

      if (Boolean.getBoolean("performRelease")) {
        if (tileArtifact.getVersion().endsWith("-SNAPSHOT")) {
          throw new MavenExecutionException(
              String.format("Tile %s is a SNAPSHOT and we are releasing.", artifactGav(tileArtifact)), tileArtifact.getFile());
        }
      }

    } catch (ProjectBuildingException | ArtifactResolutionException e) {
      throw new MavenExecutionException(e.getMessage(), e);
    }

    return tileArtifact;
  }

  protected static Artifact turnPropertyIntoUnprocessedTile(String artifactGav, File pomFile) throws MavenExecutionException {
    String[] gav = artifactGav.split(":");

    if (gav.length != 3 && gav.length != 5) {
      throw new MavenExecutionException(
          "${artifactGav} does not have the form group:artifact:version-range or group:artifact:extension:classifier:version-range",
          pomFile);
    }

    String groupId = gav[0];
    String artifactId = gav[1];
    String version;
    String type = "xml";
    String classifier = "";
    if (gav.length == 3) {
      version = gav[2];
    } else {
      type = gav[2];
      classifier = gav[3];
      version = gav[4];
    }

    return getArtifactFromCoordinates(groupId, artifactId, type, classifier, version);
  }

  protected TileModel loadModel(Artifact artifact) throws MavenExecutionException {
    try {
      TileModel modelLoader = new TileModel(artifact.getFile(), artifact);

      logger.debug(String.format("Loaded Maven Tile %s", artifactGav(artifact)));

      return modelLoader;

    } catch (Exception e) {
      throw new TileExecutionException(String.format("Error parsing %s", artifactGav(artifact)), e);
    }
  }

  /**
   * Invoked after all MavenProject instances have been created.
   *
   * This callback is intended to allow extensions to manipulate MavenProjects
   * before they are sorted and actual build execution starts.
   */
  public void afterProjectsRead(MavenSession mavenSession) throws MavenExecutionException {
    this.mavenSession = mavenSession;

    this.modelCache = new NotDefaultModelCache(mavenSession);

    List<MavenProject> allProjects = mavenSession.getProjects();
    if (allProjects != null) {
      for (MavenProject currentProject : allProjects) {
        boolean containsTiles = currentProject.getPluginArtifactMap().containsKey(TILEPLUGIN_KEY);

        if (containsTiles) {
          Plugin plugin = currentProject.getPlugin(TILEPLUGIN_KEY);
          List<String> subModules = currentProject.getModules();
          if (plugin.isInherited() && subModules != null && subModules.size() > 0) {
            Model currentModel = currentProject.getModel();
            for (MavenProject otherProject : allProjects) {
              Parent otherParent = otherProject.getModel().getParent();
              if (otherParent != null && parentGav(otherParent) == modelGav(currentModel)) {
                // We're in project with children, fail the build immediately. This is both an opinionated choice, but also
                // one of project health - with tile definitions in parent POMs usage of -pl, -am, and -amd maven options
                // are limited.
                throw new MavenExecutionException(
                    "Usage of maven-tiles prohibited from multi-module builds where reactor is used as parent.",
                    currentProject.getFile());
              }
            }
          }

          try {
            orchestrateMerge(mavenSession, currentProject);
          } catch (FileNotFoundException | MavenFilteringException | ModelBuildingException e) {
            throw new MavenExecutionException(e.getMessage(), e);
          }

          // did we expect but not get a distribution artifact repository?
          if (currentProject.getDistributionManagementArtifactRepository() != null) {
            discoverAndSetDistributionManagementArtifactoryRepositoriesIfTheyExist(currentProject);
          }
        }
      }
    }
  }

  ArtifactRepositoryPolicy getArtifactRepositoryPolicy(RepositoryPolicy policy) {
    if (policy != null) {
      return new ArtifactRepositoryPolicy(policy.isEnabled(), policy.getUpdatePolicy(), policy.getChecksumPolicy());
    } else {
      return new ArtifactRepositoryPolicy(true, UPDATE_POLICY_ALWAYS, CHECKSUM_POLICY_WARN);
    }
  }

  /**
   * If we get here, we have a Tiles project that might have a distribution management section but it is playing
   * dicky-birds and hasn't set up the distribution management repositories.
   *
   * @param project
   */
  void discoverAndSetDistributionManagementArtifactoryRepositoriesIfTheyExist(MavenProject project) {
    DistributionManagement distributionManagement = project.getModel().getDistributionManagement();

    if (distributionManagement != null) {
      if (distributionManagement.getRepository() != null) {
        ArtifactRepository candidate = project.getRemoteArtifactRepositories()
                                           .stream()
                                           .filter(it -> it.getId().equals(distributionManagement.getRepository().getId()))
                                           .findFirst()
                                           .orElse(null);

        if (candidate != null && candidate instanceof MavenArtifactRepository) {
          project.setReleaseArtifactRepository(candidate);
        } else {
          ArtifactRepository repo = repository.createArtifactRepository(
              distributionManagement.getRepository().getId(),
              getReleaseDistributionManagementRepositoryUrl(project),
              new DefaultRepositoryLayout(),
              getArtifactRepositoryPolicy(distributionManagement.getRepository().getSnapshots()),
              getArtifactRepositoryPolicy(distributionManagement.getRepository().getReleases()));
          project.setReleaseArtifactRepository(repo);
        }
      }

      if (distributionManagement.getSnapshotRepository() != null) {
        ArtifactRepository candidate = project.getRemoteArtifactRepositories()
                                           .stream()
                                           .filter(it -> it.getId() == distributionManagement.getSnapshotRepository().getId())
                                           .findFirst()
                                           .orElse(null);
        if (candidate != null && candidate instanceof MavenArtifactRepository) {
          project.setSnapshotArtifactRepository(candidate);
        } else {
          ArtifactRepository repo = repository.createArtifactRepository(
              distributionManagement.getSnapshotRepository().getId(),
              getSnapshotDistributionManagementRepositoryUrl(project),
              new DefaultRepositoryLayout(),
              getArtifactRepositoryPolicy(distributionManagement.getSnapshotRepository().getSnapshots()),
              getArtifactRepositoryPolicy(distributionManagement.getSnapshotRepository().getReleases()));
          project.setSnapshotArtifactRepository(repo);
        }
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
    DistributionManagement distributionManagement = project.getModel().getDistributionManagement();
    Properties properties = project.getProperties();

    String url = distributionManagement.getRepository().getUrl();
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
    DistributionManagement distributionManagement = project.getModel().getDistributionManagement();
    Properties properties = project.getProperties();

    String url = distributionManagement.getSnapshotRepository().getUrl();
    String altSnapshotUrl = properties.getProperty("altSnapshotDeploymentRepository");
    String altUrl = properties.getProperty("altDeploymentRepository");

    return altSnapshotUrl != null ? altSnapshotUrl : (altUrl != null ? altUrl : url);
  }

  /**
   * Merges the files over the top of the project, and then the individual project back over the top.
   * The reason for this is that the super pom and packaging can set plugin versions. This allows the tiles
   * to overwrite those, and then if they are manually specified in the pom, they then get set again.

   * @param project - the currently evaluated project
     */
  protected void orchestrateMerge(MavenSession mavenSession, MavenProject project)
      throws MavenExecutionException, FileNotFoundException, ModelBuildingException, MavenFilteringException {
    // Clear collected tiles from previous project in reactor
    processedTiles.clear();
    tileDiscoveryOrder.clear();
    unprocessedTiles.clear();
    tilesByExecution.clear();

    // collect the first set of tiles
    parseConfiguration(project.getModel(), project.getFile());

    // collect any unprocessed tiles, and process them causing them to potentially load more unprocessed ones
    loadAllDiscoveredTiles(mavenSession, project);

    // don't do anything if there are no tiles
    if (processedTiles != null) {
      thunkModelBuilder(project);
    }
  }

  protected void thunkModelBuilder(MavenProject project)
      throws MavenExecutionException, ModelBuildingException, FileNotFoundException {
    List<TileModel> tiles = processedTiles.values().stream().map(a -> a.tileModel).collect(toList());

    if (tiles == null) {
      return;
    }

    if (mavenSession.getRequest() == null) {
      return;
    }

    ModelBuildingListener modelBuildingListener =
        new DefaultModelBuildingListener(project, projectBuildingHelper, mavenSession.getRequest().getProjectBuildingRequest());

    // new org.apache.maven.project.PublicDefaultModelBuildingListener( project,
    //projectBuildingHelper, mavenSession.request.projectBuildingRequest )
    // this allows us to know when the ModelProcessor is called that we should inject the tiles into the
    // parent structure
    ModelSource2 mainArtifactModelSource = createModelSource(project.getFile());
    ModelBuildingRequest request = new DefaultModelBuildingRequest();
    request.setModelSource(mainArtifactModelSource);
    request.setPomFile(project.getFile());
    request.setModelResolver(createModelResolver(project));
    request.setModelCache(modelCache);
    request.setSystemProperties(mavenSession.getRequest().getSystemProperties());
    request.setUserProperties(mavenSession.getRequest().getUserProperties());
    request.setProfiles(
        mavenSession.getRequest().getProjectBuildingRequest().getProfiles()); // TODO should this just be request.getProfiles?
    request.setActiveProfileIds(mavenSession.getRequest().getProjectBuildingRequest().getActiveProfileIds());
    request.setInactiveProfileIds(mavenSession.getRequest().getProjectBuildingRequest().getInactiveProfileIds());
    request.setModelBuildingListener(modelBuildingListener);
    request.setLocationTracking(true);
    request.setTwoPhaseBuilding(true);
    request.setProcessPlugins(true);

    final boolean[] tilesInjected = {false};
    ModelProcessor delegateModelProcessor = new ModelProcessor() {
      @Override
      public File locatePom(File projectDirectory) {
        return modelProcessor.locatePom(projectDirectory);
      }

      @Override
      public Model read(File input, Map<String, ?> options) throws IOException {
        return modelProcessor.read(input, options);
      }

      @Override
      public Model read(Reader input, Map<String, ?> options) throws IOException {
        return modelProcessor.read(input, options);
      }

      @Override
      public Model read(InputStream input, Map<String, ?> options) throws IOException {
        Model model = modelProcessor.read(input, options);

        // when we reference a submodule of a CI Friendly module in a pom (i.e. a workspace pom in Eclipse)
        // we have no version in the submodule.
        // I.E. module A1 has parent A. Both use CI Friendly version ${revision}. A has a property "revision" with value "MAIN-SNAPSHOT".
        // we have a pom for our Eclipse workspace that includes A1.
        // If the workspace pom includes only A1 it works. But if it contains B and B has a dependency to A1 it
        // fails with NPE.
        // Here we have to ask the project for its version
        //
        if (model.getVersion() == null && "$\\{revision}".equals(getRealVersion(model))) { // TODO should this configurable
          model.getParent().setVersion(project.getVersion());
        }

        // evaluate the model version to deal with CI friendly build versions.
        // "0-SNAPSHOT" indicates an undefined property.
        try {
          if (model.getArtifactId().equals(project.getArtifactId()) && getRealGroupId(model).equals(project.getGroupId()) &&
              (evaluateString(getRealVersion(model)).equals(project.getVersion()) ||
               evaluateString(getRealVersion(model)).equals("0-SNAPSHOT") || evaluateString(getRealVersion(model)) == null) &&
              model.getPackaging().equals(project.getPackaging())) {
            // we're at the first (project) level. Apply tiles here if no explicit parent is set
            if (!parseBoolean(applyBeforeParent)) {
              injectTilesIntoParentStructure(tiles, model, request);
              tilesInjected[0] = true;
            }
          } else if (modelRealGa(model).equals(applyBeforeParent)) {
            // we're at the level with the explicitly selected parent. Apply the tiles here
            injectTilesIntoParentStructure(tiles, model, request);
            tilesInjected[0] = true;
          } else if (model.getPackaging().equals("tile") || model.getPackaging().equals("pom")) {
            // we could be at a parent that is a tile. In this case return the precomputed model
            Model finalModel = model;
            TileModel oneOfUs = tiles.stream()
                                    .filter(tm -> {
                                      Model tileModel = tm.getModel();
                                      return finalModel.getArtifactId() == tileModel.getArtifactId() &&
                                          getRealGroupId(finalModel) == getRealGroupId(tileModel) &&
                                          getRealVersion(finalModel).equals(getRealVersion(tileModel));
                                    })
                                    .findFirst()
                                    .orElse(null);

            if (oneOfUs != null) {
              model = oneOfUs.getModel();
            }
          }
        } catch (ExpressionEvaluationException e) {
          throw new RuntimeException(e.getMessage(), e);
        }

        // if we want to apply tiles at a specific parent and have not come by it yet, we need
        // to make the parent reference project specific, so that it will not pick up a cached
        // version. We do this by adding a project specific suffix, which will later be removed
        // when actually loading that parent.
        if (parseBoolean(applyBeforeParent) && !tilesInjected[0] && model.getParent() != null) {
          // remove the parent from the cache which causes it to be reloaded through our ModelProcessor
          request.getModelCache().put(
              model.getParent().getGroupId(), model.getParent().getArtifactId(), model.getParent().getVersion(), "raw", null);
        }

        return model;
      }
    };

    DefaultModelBuilder mb = ((DefaultModelBuilder) modelBuilder).setModelProcessor(delegateModelProcessor);
    try {
      ModelBuildingResult interimBuild = mb.build(request);

      // this will revert the tile dependencies inserted by TilesProjectBuilder, which is fine since by now they
      // served their purpose of correctly ordering projects, so we can now do without them
      ModelBuildingResult finalModel = mb.build(request, interimBuild);
      if (!tilesInjected[0] && parseBoolean(applyBeforeParent)) {
        throw new MavenExecutionException(
            "Cannot apply tiles, the expected parent ${applyBeforeParent} is not found.", project.getFile());
      }
      copyModel(project, finalModel.getEffectiveModel());
    } finally {
      // restore original ModelProcessor
      ((DefaultModelBuilder) modelBuilder).setModelProcessor(modelProcessor);
    }
  }

  ModelSource2 createModelSource(File pomFile) throws FileNotFoundException {
    return new ModelSource2() {
      InputStream stream = new FileInputStream(pomFile);

      @Override
      public InputStream getInputStream() throws IOException {
        return stream;
      }

      @Override
      public String getLocation() {
        return pomFile.getAbsolutePath();
      }

      @Override
      public URI getLocationURI() {
        return pomFile.toURI();
      }

      @Override
      public ModelSource2 getRelatedSource(String relPath) {
        File relatedPom = new File(pomFile.getParentFile(), relPath);
        if (relatedPom.isDirectory()) {
          relatedPom = new File(relatedPom, "pom.xml");
        }
        if (relatedPom.isFile() && relatedPom.canRead()) {
          try {
            return createModelSource(relatedPom.getCanonicalFile());
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        }
        return null;
      }
    };
  }

  protected ModelResolver createModelResolver(MavenProject project) {
    // this is for resolving parents, so always poms

    return new ModelResolver() {
      @Override
      public ModelSource2 resolveModel(String groupId, String artifactId, String version) throws UnresolvableModelException {
        Artifact artifact = new DefaultArtifact(
            groupId,
            artifactId,
            VersionRange.createFromVersion(version),
            "compile",
            "pom",
            null,
            new DefaultArtifactHandler("pom"));

        resolveVersionRange(project, artifact);

        List<ArtifactRepository> remoteRepositories = project == null ? null : project.getRemoteArtifactRepositories();
        ArtifactRepository localRepository = mavenSession.getLocalRepository();

        ArtifactResolutionRequest req = new ArtifactResolutionRequest()
                                            .setArtifact(artifact)
                                            .setRemoteRepositories(remoteRepositories)
                                            .setLocalRepository(localRepository);

        repository.resolve(req);

        try {
          return createModelSource(artifact.getFile());
        } catch (FileNotFoundException e) {
          throw new UncheckedIOException(e);
        }
      }

      @Override
      public ModelSource2 resolveModel(Parent parent) throws UnresolvableModelException {
        return resolveModel(parent.getGroupId(), parent.getArtifactId(), parent.getVersion());
      }

      // this exists in later versions of maven
      public ModelSource2 resolveModel(Dependency dependency) throws UnresolvableModelException {
        return resolveModel(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion());
      }

      @Override
      public void addRepository(Repository repository) {
      }

      @Override
      public void addRepository(Repository repository, boolean wat) {
      }

      @Override
      public ModelResolver newCopy() {
        return createModelResolver(project);
      }
    };
  }

  /**
   * This is out of static type checking because the method is private and the class ModelCacheTag
   * is package-private.
   *
   * @param model - the model we are inserting into the cache
   * @param request - the building request, it holds the cache reference
   * @param pomFile - the pomFile is required for model data for Maven 3.2.x not for 3.0.x
   */
  protected void putModelInCache(Model model, ModelBuildingRequest request, File pomFile) {
    // stuff it in the cache so it is ready when requested rather than it trying to be resolved.

    org.apache.maven.model.building.@Jailbreak ModelData modelData = new org.apache.maven.model.building.@Jailbreak ModelData(
        new FileModelSource(pomFile), model, model.getGroupId(), model.getArtifactId(), model.getVersion());

    request.getModelCache().put(model.getGroupId(), model.getArtifactId(), evaluateString(model.getVersion()), "raw", modelData);
  }

  /**
   * Creates a chain of tile parents based on how we discovered them and inserts them into the parent
   * chain, above this project and before this project's parent (if any)
   *
   * @param tiles - tiles that should make up part of the collection
   * @param pomModel - the current project
   * @param request - the request to build the current project
   */
  void injectTilesIntoParentStructure(List<TileModel> tiles, Model pomModel, ModelBuildingRequest request)
      throws ExpressionEvaluationException {
    Parent originalParent = pomModel.getParent();
    final Model[] lastPom = {pomModel};
    final File[] lastPomFile = {request.getPomFile()};

    // fix up the version of the originalParent
    if (originalParent != null) {
      originalParent.setVersion(evaluateString(originalParent.getVersion()));
    }

    if (tiles != null && !tiles.isEmpty()) {
      // evaluate the model version to deal with CI friendly build versions
      logger.info(
          "--- tiles-maven-plugin: Injecting ${tiles.size()} tiles as intermediary parent artifacts for " +
          "${evaluateString(modelRealGa(pomModel))}...");
      logger.info(
          "Mixed '${evaluateString(modelGav(pomModel))}' with tile '${evaluateString(modelGav(tiles.get(0).getModel()))}' as its " +
          "new parent.");

      // if there is a parent make sure the inherited groupId / version is correct
      if (pomModel.getGroupId() == null) {
        pomModel.setGroupId(originalParent.getGroupId());
        logger.info("Explicitly set groupId to '${pomModel.getGroupId()}' from original parent '${parentGav(originalParent)}'.");
      }
      if (pomModel.getVersion() == null) {
        pomModel.setVersion(originalParent.getVersion());
        logger.info("Explicitly set version to '${pomModel.getVersion()}' from original parent '${parentGav(originalParent)}'.");
      }
    }

    tiles.forEach(tileModel -> {
      Model model = tileModel.getModel();

      Parent modelParent = new Parent();
      modelParent.setGroupId(model.getGroupId());
      modelParent.setArtifactId(model.getArtifactId());
      modelParent.setVersion(model.getVersion());

      lastPom[0].setParent(modelParent);

      if (pomModel != lastPom[0]) {
        putModelInCache(lastPom[0], request, lastPomFile[0]);
        logger.info(
            "Mixed '${evaluateString(modelGav(lastPom[0]))}' with tile '${evaluateString(parentGav(modelParent))}' as its new " +
            "parent.");
      }

      lastPom[0] = model;
      lastPomFile[0] = tileModel.getTilePom();
    });

    lastPom[0].setParent(originalParent);

    if (originalParent != null) {
      if (originalParent.getRelativePath() != null && !originalParent.getRelativePath().isEmpty()) {
        logger.info(
            "Mixed '${evaluateString(modelGav(lastPom[0]))}' with original parent '${parentGav(originalParent)}' via " +
            "${originalParent.getRelativePath()} as its new top level parent.");
      } else {
        logger.info(
            "Mixed '${evaluateString(modelGav(lastPom[0]))}' with original parent '${parentGav(originalParent)}' as its new top " +
            "level parent.");
      }
      logger.info("");
    }

    if (pomModel != lastPom[0]) {
      putModelInCache(lastPom[0], request, lastPomFile[0]);
    }
  }

  protected static void copyModel(MavenProject project, Model newModel) {
    // no setting parent, we have generated an effective model which is now all copied in
    Model projectModel = project.getModel();
    projectModel.setBuild(newModel.getBuild());
    projectModel.setDependencyManagement(newModel.getDependencyManagement());
    projectModel.setDependencies(newModel.getDependencies());
    projectModel.setRepositories(newModel.getRepositories());
    projectModel.setPluginRepositories(newModel.getPluginRepositories());
    projectModel.setLicenses(newModel.getLicenses());
    projectModel.setScm(newModel.getScm());
    projectModel.setDistributionManagement(newModel.getDistributionManagement());
    projectModel.setDevelopers(newModel.getDevelopers());
    projectModel.setContributors(newModel.getContributors());
    projectModel.setOrganization(newModel.getOrganization());
    projectModel.setMailingLists(newModel.getMailingLists());
    projectModel.setIssueManagement(newModel.getIssueManagement());
    projectModel.setCiManagement(newModel.getCiManagement());
    projectModel.setProfiles(newModel.getProfiles());
    projectModel.setPrerequisites(newModel.getPrerequisites());
    projectModel.setProperties(newModel.getProperties());
    projectModel.setReporting(newModel.getReporting());

    // update model (test) source directory, which is the first entry and might have been set through a tile
    if (projectModel.getBuild().getSourceDirectory() != null) {
      List<String> roots = new ArrayList(project.getCompileSourceRoots());
      roots.remove(0);
      roots.add(0, projectModel.getBuild().getSourceDirectory());
      project.getCompileSourceRoots().clear();
      project.getCompileSourceRoots().addAll(roots);
    }
    if (projectModel.getBuild().getTestSourceDirectory() != null) {
      List<String> roots = new ArrayList(project.getTestCompileSourceRoots());
      roots.remove(0);
      roots.add(0, projectModel.getBuild().getTestSourceDirectory());
      project.getTestCompileSourceRoots().clear();
      project.getTestCompileSourceRoots().addAll(roots);
    }

    // for tile provided LifecycleMapping in m2e we need to modifiy the original model
    // TODO Handle nulls - elvisNull(() -> blah.getBlah().getNullable());
    Plugin m2ePlugin = projectModel.getBuild().getPluginManagement().getPluginsAsMap().get("org.eclipse.m2e:lifecycle-mapping");
    if (m2ePlugin != null) {
      Build build = project.getOriginalModel().getBuild();
      if (build == null) {
        build = new Build();
        project.getOriginalModel().setBuild(build);
      }
      if (build.getPluginManagement() != null) {
        build.setPluginManagement(build.getPluginManagement().clone()); // TODO this seems.... odd
      } else {
        build.setPluginManagement(new PluginManagement());
      }
      build.getPluginManagement().addPlugin(m2ePlugin);
    }
  }

  protected void loadAllDiscoveredTiles(MavenSession mavenSession, MavenProject project)
      throws MavenExecutionException, MavenFilteringException {
    List<TileModel> mergeSourceTiles = new ArrayList<>();
    Map<String, Artifact> rootTiles = new HashMap<>();
    rootTiles.putAll(unprocessedTiles);
    unprocessedTiles.clear();

    for (String rootTile : rootTiles.keySet()) {
      unprocessedTiles.put(rootTile, rootTiles.get(rootTile));

      while (unprocessedTiles.size() > 0) {
        String unresolvedTile = unprocessedTiles.keySet().iterator().next();

        Artifact resolvedTile = resolveTile(mavenSession, project, unprocessedTiles.remove(unresolvedTile));

        TileModel tileModel = loadModel(resolvedTile);

        // ensure we have resolved the tile (it could come from a non-tile model)
        if (tileModel != null) {
          if (hasProperty(tileModel, "tile-merge-source")) {
            // hold and merge into target later
            mergeSourceTiles.add(tileModel);
          } else {
            if (hasProperty(tileModel, "tile-merge-target")) {
              registerTargetTile(tileModel);
            }
            String tileName = artifactName(resolvedTile);
            processedTiles.put(tileName, new ArtifactModel(resolvedTile, tileModel));
            parseForExtendedSyntax(tileModel, resolvedTile.getFile());
          }
        }
      }
    }

    // merge all the source tiles last
    for (TileModel mergeTile : mergeSourceTiles) {
      mergeTileIntoTarget(mergeTile);
    }

    ensureAllTilesDiscoveredAreAccountedFor();
  }

  private static boolean hasProperty(TileModel tileModel, String propertyKey) {
    // remove these properties, we don't want them in the merged result
    // TODO yuck, hasProperty inplies a check, not a check if removed
    // TODO handle elvisNulls
    Properties properties = tileModel.getModel().getProperties();
    return properties != null && "true".equals(properties.remove(propertyKey));
  }

  private void registerTargetTile(TileModel targetTile) {
    mergeTile(targetTile, false);
  }

  private void mergeTileIntoTarget(TileModel fragmentTile) {
    mergeTile(fragmentTile, true);
  }

  private void mergeTile(TileModel tileModel, boolean mergeIntoTarget) {
    // TODO handle nulls
    tileModel.getModel().getBuild().getPlugins().forEach(plugin -> {
      plugin.getExecutions().forEach(execution -> {
        String eid = "${plugin.getGroupId()}:${plugin.getArtifactId()}:${execution.getId()}";
        if (!mergeIntoTarget) {
          tilesByExecution.put(eid, tileModel);
        } else {
          String fragmentId = "${tileModel.getModel().getGroupId()}:${tileModel.getModel().getArtifactId()}";
          TileModel targetTile = tilesByExecution.get(eid);
          if (targetTile != null) {
            String targetId = "${targetTile.getModel().getGroupId()}:${targetTile.getModel().getArtifactId()}";
            logger.info("Merged tile ${fragmentId} into ${targetId} plugin:${eid}");
            mergeProperties(targetTile, tileModel);
            mergeExecutionConfiguration(targetTile, execution, eid);
          } else {
            String missingTileId = tileModel.getModel().getProperties().getProperty("tile-merge-expected-target");
            if (missingTileId != null) {
              throw new TileExecutionException(
                  "Please add missing tile ${missingTileId}. This is required for tile ${fragmentId}, plugin:${eid}",
                  (Throwable) null);
            } else {
              throw new TileExecutionException(
                  "Error with tile ${fragmentId} - Missing target tile required with plugin:${eid}. Please check the " +
                  "documentation for this tile.",
                  (Throwable) null);
            }
          }
        }
      });
    });
  }

  /**
   * Merge the properties from the mergeTile into targetTile.
   */
  private static void mergeProperties(TileModel targetTile, TileModel mergeTile) {
    if (mergeTile.getModel().getProperties() != null) {
      targetTile.getModel().getProperties().putAll(mergeTile.getModel().getProperties());
    }
  }

  /**
   * Merge the execution configuration from mergeExecution into the target tile.
   */
  private void mergeExecutionConfiguration(TileModel targetTile, PluginExecution mergeExecution, String eid) {
    // TODo handle nulls
    targetTile.getModel().getBuild().getPlugins().forEach(plugin -> {
      plugin.getExecutions().forEach(execution -> {
        String targetEid = "${plugin.getGroupId()}:${plugin.getArtifactId()}:${execution.getId()}";
        if (targetEid.equals(eid)) {
          Xpp3Dom configuration = (Xpp3Dom) execution.getConfiguration();
          String appendElementName = configuration.getAttribute("tiles-append");
          if (appendElementName != null) {
            Xpp3Dom target = configuration.getChild(appendElementName);
            Xpp3Dom source = ((Xpp3Dom) mergeExecution.getConfiguration()).getChild(appendElementName);
            // append from source into target
            Xpp3Dom.mergeXpp3Dom(target, source, false);

            logger.debug("merged execution configuration - ${eid}");
          }
        }
      });
    });
  }

  /**
   * removes all invalid tiles from the discovery order
   */
  void ensureAllTilesDiscoveredAreAccountedFor() {
    List<String> missingTiles = new ArrayList<>();

    tileDiscoveryOrder.forEach(tile -> {
      if (processedTiles.get(tile) == null) {
        missingTiles.add(tile);
      }
    });

    tileDiscoveryOrder.removeAll(missingTiles);
  }

  /**
   * Normally used inside the current project's pom file when declaring the tile plugin. People may prefer this
   * to use to include tiles however in a tile.xml
   */
  protected void parseConfiguration(Model model, File pomFile) throws MavenExecutionException {
    // TODO handle nulls

    if (model.getBuild() != null) {
      List<Plugin> plugins = model.getBuild().getPlugins();
      Xpp3Dom configuration = plugins.stream()
                                  .filter(TilesMavenLifecycleParticipant::idTilesPlugin)
                                  .flatMap(plugin -> {
                                    // TODO Use Stream.ofNullable if we limit JDK >9
                                    Xpp3Dom config = (Xpp3Dom) plugin.getConfiguration();
                                    return config == null ? Stream.empty() : Stream.of(config);
                                  })
                                  .findFirst()
                                  .orElse(null);

      if (configuration != null && configuration.getChild("tiles") != null) {
        for (Xpp3Dom tiles : configuration.getChild("tiles").getChildren()) {
          processConfigurationTile(model, tiles.getValue(), pomFile);
        }
        Xpp3Dom applyBefore = configuration.getChild("applyBefore");
        applyBeforeParent = applyBefore == null ? "false" : applyBefore.getValue();
      }
    }
  }

  private static boolean idTilesPlugin(Plugin plugin) {
    return plugin.getGroupId().equals(TILEPLUGIN_GROUP) && plugin.getArtifactId().equals(TILEPLUGIN_ARTIFACT);
  }

  /**
   * Used for when we have a TileModel (we have read directly) so we support the extra syntax.
   */
  protected void parseForExtendedSyntax(TileModel model, File pomFile) throws MavenExecutionException {
    for (String tileGav : model.getTiles()) {
      processConfigurationTile(model.getModel(), tileGav, pomFile);
    }

    parseConfiguration(model.getModel(), pomFile);
  }

  protected void processConfigurationTile(Model model, String tileDependencyName, File pomFile) throws MavenExecutionException {
    Artifact unprocessedTile = turnPropertyIntoUnprocessedTile(tileDependencyName, pomFile);

    String depName = artifactName(unprocessedTile);

    if (!processedTiles.containsKey(depName)) {
      if (unprocessedTiles.containsKey(depName)) {
        logger.warn(
            "tiles-maven-plugin in project ${modelGav(model)} requested for same tile dependency ${artifactGav(unprocessedTile)}");

        // move the entry to the end of the map
        unprocessedTiles.put(depName, unprocessedTiles.remove(depName));
      } else {
        logger.debug("Adding tile ${artifactGav(unprocessedTile)}");

        unprocessedTiles.put(depName, unprocessedTile);
        tileDiscoveryOrder.add(depName);
      }
    } else {
      logger.warn(
          "tiles-maven-plugin in project ${modelGav(model)} requested for same tile dependency ${artifactGav(unprocessedTile)}");

      // move the entry to the end of the map
      processedTiles.put(depName, processedTiles.remove(depName));
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
      try {
        return (String) new PluginParameterExpressionEvaluator(mavenSession, new MojoExecution(new MojoDescriptor()))
            .evaluate(value, String.class);
      } catch (ExpressionEvaluationException e) {
        return value;
      }
    } else {
      return value;
    }
  }

  void resolveVersionRange(MavenProject project, Artifact tileArtifact) {
    List<ArtifactRepository> repositories = project == null ? null : project.getRemoteArtifactRepositories();
    VersionRangeRequest versionRangeRequest =
        new VersionRangeRequest(RepositoryUtils.toArtifact(tileArtifact), RepositoryUtils.toRepos(repositories), null);

    VersionRangeResult versionRangeResult = null;
    try {
      versionRangeResult = versionRangeResolver.resolveVersionRange(mavenSession.getRepositorySession(), versionRangeRequest);
    } catch (VersionRangeResolutionException e) {
      logger.error("Unable to resolve version ranges: " + artifactGav(tileArtifact));
    }

    if (versionRangeResult != null && versionRangeResult.getVersions() != null) {
      tileArtifact.setVersion(versionRangeResult.getHighestVersion().toString());
    }
  }
}
