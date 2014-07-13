/***********************************************************************************************************************
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
package it.session.maven.plugin;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.merge.ModelMerger;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.resolution.ArtifactRequest;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.util.artifact.DefaultArtifact;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Fetches all dependencies defined in the POM `<properties>` as follows:
 *
 * [source,xml]
 * --
 *   <properties>
 *     <tiles.1>it.session.maven.tiles:maven-compile-tiles:0.8-SNAPSHOT</tiles.1>
 *     <tiles.2>it.session.maven.tiles:maven-eclipse-tiles:0.8-SNAPSHOT</tiles.2>
 *     <tiles.3>it.session.maven.tiles:maven-jetty-tiles:0.8-SNAPSHOT</tiles.3>
 *   </properties>
 * --
 *
 * Dependencies are fetched using Aether {@link RepositorySystem}
 * Merging operation is delegated to {@link ModelMerger}
 */
@Component(role = AbstractMavenLifecycleParticipant.class, hint = "TilesMavenLifecycleParticipant")
public class TilesMavenLifecycleParticipant extends AbstractMavenLifecycleParticipant {

  protected static final String TILE_EXTENSION = "pom";
  protected static final String TILE_PROPERTY_PREFIX = "tile.";

  protected final MavenXpp3Reader reader = new MavenXpp3Reader();
  protected final ModelMerger modelMerger = new TilesModelMerger();

  @Requirement
  protected Logger logger;

  @Requirement
  protected RepositorySystem repositorySystem;

  /**
   * Only used for unit testing dependency injection
   */
  public void setRepositorySystem(RepositorySystem repositorySystem) {
    this.repositorySystem = repositorySystem;
  }

  protected Artifact getArtifactFromCoordinates(String groupId, String artifactId, String version) {
    return new DefaultArtifact(groupId, artifactId, TILE_EXTENSION, version);
  }

  protected ArtifactRequest getArtifactRequestFromArtifact(Artifact tileArtifact, MavenProject mavenProject) {
    ArtifactRequest request = new ArtifactRequest();
    request.setArtifact(tileArtifact);
    request.setRepositories(mavenProject.getRemoteProjectRepositories());
    return request;
  }

  protected File resolveArtifact(MavenProject currentProject,
                                 String groupId,
                                 String artifactId,
                                 String version,
                                 RepositorySystemSession repositorySystemSession) throws MojoExecutionException {
    try {
      Artifact tileArtifact = getArtifactFromCoordinates(groupId, artifactId, version);
      ArtifactRequest request = getArtifactRequestFromArtifact(tileArtifact, currentProject);
      ArtifactResult result = this.repositorySystem.resolveArtifact(repositorySystemSession, request);
      return result.getArtifact().getFile();
    } catch (ArtifactResolutionException e) {
      throw new MojoExecutionException(String.format("Error resolving artifact %s:%s:%s", groupId, artifactId, version));
    }
  }

  protected void mergeTile(MavenProject currentProject, String propertyName, RepositorySystemSession repositorySystemSession) throws MavenExecutionException {
    String propertyValue = currentProject.getProperties().getProperty(propertyName);
    StringTokenizer propertyTokens = new StringTokenizer(propertyValue, ":");

    String groupId = propertyTokens.nextToken();
    String artifactId = propertyTokens.nextToken();
    String version = propertyTokens.nextToken();

    String currentTileInformation =
        String.format("'%s:%s:%s'",
            groupId,
            artifactId,
            version);

    try {
      File artifactFile = this.resolveArtifact(
          currentProject,
          groupId,
          artifactId,
          version,
          repositorySystemSession);

      Model tileModel = this.reader.read(new FileInputStream(artifactFile));
      this.modelMerger.merge(currentProject.getModel(), tileModel, false, null);

      //If invoked by tests, logger is null
      //@TODO properly inject logger on TilesMavenLifecycleParticipantTest.java
      if (logger != null) {
        logger.info(String.format("Loaded Maven Tile " + currentTileInformation));
      }

    } catch (FileNotFoundException e) {
      throw new MavenExecutionException("Error loading tiles " + currentTileInformation, e);
    } catch (XmlPullParserException e) {
      throw new MavenExecutionException("Error building tiles " + currentTileInformation, e);
    } catch (IOException e) {
      throw new MavenExecutionException("Error parsing tiles " + currentTileInformation, e);
    } catch (MojoExecutionException e) {
      throw new MavenExecutionException("Error retrieving tiles " + currentTileInformation, e);
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

    final MavenProject topLevelProject = mavenSession.getTopLevelProject();
    List<String> subModules = topLevelProject.getModules();

    if (subModules != null && subModules.size() > 0) {
      //We're in a multi-module build, we need to trigger model merging on all sub-modules
      for (MavenProject subModule : mavenSession.getProjects()) {
        if (subModule != topLevelProject) {
          mergeTiles(subModule, mavenSession);
        }
      }
    } else {
      mergeTiles(topLevelProject, mavenSession);
    }
  }

  private void mergeTiles(MavenProject currentProject, MavenSession mavenSession) throws MavenExecutionException {
    Enumeration propertyNames = currentProject.getProperties().propertyNames();
    while (propertyNames.hasMoreElements()) {
      String propertyName = (String) propertyNames.nextElement();
      if (propertyName.startsWith(TILE_PROPERTY_PREFIX)) {
        mergeTile(currentProject, propertyName, mavenSession.getRepositorySession());
      }
    }
  }

}
