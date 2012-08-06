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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.runners.MockitoJUnitRunner;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.resolution.ArtifactRequest;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.util.DefaultRepositorySystemSession;
import org.sonatype.aether.util.artifact.DefaultArtifact;

/**
 * If testMergeTile fails with java.io.FileNotFoundException: src/test/resources/licenses-tiles-pom.xml
 * (No such file or directory)) when running the test from your IDE, make sure you configure the Working
 * Directory as maven-tiles/tiles-maven-plugin (absolute path)
 */
@RunWith(MockitoJUnitRunner.class)
public class TilesMavenLifecycleParticipantTest {

  TilesMavenLifecycleParticipant participant;
  RepositorySystem mockRepositorySystem;
  RepositorySystemSession defaultRepositorySystemSession;

  private final static String TILE_TEST_COORDINATES = "it.session.maven.tiles:session-repositories-tile:0.8-SNAPSHOT";
  private final static String TILE_TEST_POM_PATH = "src/test/resources/licenses-tile-pom.xml";
  private final static String TILE_TEST_PROPERTY_NAME = "tile.test";

  @Before
  public void setupParticipant() {
    this.participant = new TilesMavenLifecycleParticipant();
    this.mockRepositorySystem = mock(RepositorySystem.class);
    this.defaultRepositorySystemSession = new DefaultRepositorySystemSession();
    participant.setRepositorySystem(mockRepositorySystem);
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
  public void testGetArtifactRequestFromArtifact() {
    Artifact artifact = participant.getArtifactFromCoordinates("dummy", "dummy", "1");
    MavenProject mockMavenProject = mock(MavenProject.class);
    ArtifactRequest request = participant.getArtifactRequestFromArtifact(artifact, mockMavenProject);
    assertNotNull(request);
  }

  @Test
  public void testResolveArtifact() throws ArtifactResolutionException, MojoExecutionException, IOException {
    MavenProject emptyMavenProject = new MavenProject();

    DefaultArtifact dummyArtifact = new DefaultArtifact("dummy:dummy:1");

    this.mockRepositoryWithProvidedArtifact(dummyArtifact);

    File artifactFile = this.participant.resolveArtifact(emptyMavenProject, "dummy", "dummy", "1", this.defaultRepositorySystemSession);
    assertNotNull(artifactFile);
  }

  @Test
  public void testMergeTile() throws MavenExecutionException, IOException, ArtifactResolutionException {
    MavenProject mavenProject = new MavenProject();
    mavenProject.getProperties().setProperty(TILE_TEST_PROPERTY_NAME,TILE_TEST_COORDINATES);

    DefaultArtifact dummyArtifact = new DefaultArtifact(TILE_TEST_COORDINATES);

    this.mockRepositoryWithProvidedArtifact(dummyArtifact);

    assertTrue(mavenProject.getLicenses().size() == 0);
    participant.mergeTile(mavenProject,TILE_TEST_PROPERTY_NAME,defaultRepositorySystemSession);
    assertTrue(mavenProject.getLicenses().size() != 0);
  }

	private void mockRepositoryWithProvidedArtifact(Artifact artifact)
			throws ArtifactResolutionException {
		ArtifactResult expectedResult = new ArtifactResult(new ArtifactRequest());
		expectedResult.setArtifact(artifact.setFile(new File(TILE_TEST_POM_PATH)));

		when(
			this.mockRepositorySystem.resolveArtifact(
				same(defaultRepositorySystemSession),
				argThat(new MatchesArtifact(artifact))))
			.thenReturn(expectedResult);
	}
  
	class MatchesArtifact extends ArgumentMatcher<ArtifactRequest> {

		private Artifact myArtifact;
		
		public MatchesArtifact(Artifact myArtifact) {
			this.myArtifact = myArtifact;
		}

		@Override
		public boolean matches(Object oRequest) {
			ArtifactRequest artifactRequest = (ArtifactRequest) oRequest;
			Artifact theirArtifact = artifactRequest.getArtifact();
			return myArtifact.getGroupId().equals(theirArtifact.getGroupId()) && 
					myArtifact.getArtifactId().equals(theirArtifact.getArtifactId()) && 
					myArtifact.getVersion().equals(theirArtifact.getVersion());
		}

	}
}