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
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.merge.ModelMerger;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

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
	protected final ModelMerger modelMerger = new ModelMerger();

	@Requirement
	protected Logger logger;

	@Requirement
	ArtifactResolver resolver;

	@Parameter(property = "project.remoteArtifactRepositories", readonly = true, required = true)
	List<ArtifactRepository> remoteRepositories;

	@Parameter(property = "localRepository")
	ArtifactRepository localRepository;

	protected Artifact getArtifactFromCoordinates(String groupId, String artifactId, String version) {
		return new DefaultArtifact(groupId, artifactId, VersionRange.createFromVersion(version), "compile",
			TILE_EXTENSION, "", new DefaultArtifactHandler(TILE_EXTENSION));
	}

	protected File resolveArtifact(String groupId,
	                               String artifactId,
	                               String version) throws MojoExecutionException {
		Artifact tileArtifact = getArtifactFromCoordinates(groupId, artifactId, version);

		try {
			resolver.resolve(tileArtifact, remoteRepositories, localRepository);
		} catch (ArtifactResolutionException e) {
			throw new MojoExecutionException(e.getMessage(), e);
		} catch (ArtifactNotFoundException e) {
			throw new MojoExecutionException(e.getMessage(), e);
		}

		return tileArtifact.getFile();
	}

	protected void mergeTile(MavenProject currentProject, String propertyName) throws MavenExecutionException {
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
			File artifactFile = resolveArtifact(
				groupId,
				artifactId,
				version);

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
					mergeTiles(subModule);
				}
			}
		} else {
			mergeTiles(topLevelProject);
		}
	}

	private void mergeTiles(MavenProject currentProject) throws MavenExecutionException {
		Enumeration propertyNames = currentProject.getProperties().propertyNames();
		while (propertyNames.hasMoreElements()) {
			String propertyName = (String) propertyNames.nextElement();
			if (propertyName.startsWith(TILE_PROPERTY_PREFIX)) {
				mergeTile(currentProject, propertyName);
			}
		}
	}

}
