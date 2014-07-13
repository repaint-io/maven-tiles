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

import org.apache.maven.MavenExecutionException;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

/**
 * If testMergeTile fails with java.io.FileNotFoundException: src/test/resources/licenses-tiles-pom.xml
 * (No such file or directory)) when running the test from your IDE, make sure you configure the Working
 * Directory as maven-tiles/tiles-maven-plugin (absolute path)
 */
@RunWith(MockitoJUnitRunner.class)
public class TilesMavenLifecycleParticipantTest {

	TilesMavenLifecycleParticipant participant;
	ArtifactResolver mockResolver;

	private final static String TILE_TEST_COORDINATES = "it.session.maven.tiles:session-license-tile:0.8-SNAPSHOT";
	private final static String TILE_TEST_POM_PATH = "src/test/resources/licenses-tile-pom.xml";
	private final static String TILE_TEST_PROPERTY_NAME = "tile.test";

	@Before
	public void setupParticipant() {
		this.participant = new TilesMavenLifecycleParticipant();
		mockResolver = mock(ArtifactResolver.class);
		participant.resolver = mockResolver;
	}

	public Artifact getTileTestCoordinates() {
		return participant.getArtifactFromCoordinates("it.session.maven.tiles", "session-license-tile", "0.8-SNAPSHOT");
	}

	@Test
	public void testGetArtifactFromCoordinates() {
		Artifact artifact = participant.getArtifactFromCoordinates("dummy", "dummy", "1");
		assertNotNull(artifact);
		assertEquals(artifact.getGroupId(), "dummy");
		assertEquals(artifact.getArtifactId(), "dummy");
		assertEquals(artifact.getVersion(), "1");
	}

	@Test
	public void testResolveArtifact() throws MojoExecutionException, IOException, ArtifactNotFoundException, ArtifactResolutionException {
		Artifact dummyArtifact = participant.getArtifactFromCoordinates("dummy", "dummy", "1");

		this.mockRepositoryWithProvidedArtifact(dummyArtifact);

		File artifactFile = this.participant.resolveArtifact("dummy", "dummy", "1");
		assertNotNull(artifactFile);
	}

	@Test
	public void testMergeTile() throws MavenExecutionException, IOException, ArtifactNotFoundException, ArtifactResolutionException {
		MavenProject mavenProject = new MavenProject();
		mavenProject.getProperties().setProperty(TILE_TEST_PROPERTY_NAME, TILE_TEST_COORDINATES);

		Artifact dummyArtifact = getTileTestCoordinates();

		this.mockRepositoryWithProvidedArtifact(dummyArtifact);

		assertTrue(mavenProject.getLicenses().size() == 0);
		participant.mergeTile(mavenProject, TILE_TEST_PROPERTY_NAME);
		assertTrue(mavenProject.getLicenses().size() != 0);
	}

	private void mockRepositoryWithProvidedArtifact(Artifact artifact) throws ArtifactNotFoundException, ArtifactResolutionException {
		ArtifactResolutionResult expectedResult = new ArtifactResolutionResult();
		expectedResult.addArtifact(artifact);

		doNothing().when(this.mockResolver).resolve(argThat(new MatchesArtifact(artifact)),
			anyList(), Mockito.any(ArtifactRepository.class));
	}

	class MatchesArtifact extends ArgumentMatcher<Artifact> {

		private Artifact myArtifact;

		public MatchesArtifact(Artifact myArtifact) {
			this.myArtifact = myArtifact;
		}

		@Override
		public boolean matches(Object oRequest) {
			Artifact theirArtifact = (Artifact) oRequest;

			if (myArtifact.getGroupId().equals(theirArtifact.getGroupId()) &&
				myArtifact.getArtifactId().equals(theirArtifact.getArtifactId()) &&
				(myArtifact.getVersion().equals(theirArtifact.getVersion()) ||
					myArtifact.getVersionRange().equals(theirArtifact.getVersionRange()))) {
				theirArtifact.setFile(new File(TILE_TEST_POM_PATH));
				return true;
			}

			return false;
		}

	}
}
