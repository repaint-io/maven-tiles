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
package io.repaint.maven.tiles

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import io.repaint.maven.tiles.isolators.MavenVersionIsolator
import org.apache.maven.MavenExecutionException
import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.repository.ArtifactRepository
import org.apache.maven.artifact.resolver.ArtifactResolver
import org.apache.maven.execution.MavenSession
import org.apache.maven.model.Build
import org.apache.maven.model.Model
import org.apache.maven.model.Plugin
import org.apache.maven.model.building.ModelBuildingRequest
import org.apache.maven.model.building.ModelProblemCollector
import org.apache.maven.model.interpolation.ModelInterpolator
import org.apache.maven.model.management.DependencyManagementInjector
import org.apache.maven.project.MavenProject
import org.codehaus.plexus.logging.Logger
import org.codehaus.plexus.util.xml.Xpp3DomBuilder
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.runners.MockitoJUnitRunner

import static org.mockito.Mockito.mock
import static groovy.test.GroovyAssert.shouldFail

/**
 * If testMergeTile fails with java.io.FileNotFoundException: src/test/resources/licenses-tiles-pom.xml
 * (No such file or directory)) when running the test from your IDE, make sure you configure the Working
 * Directory as maven-tiles/tiles-maven-plugin (absolute path)
 */
@CompileStatic
@RunWith(MockitoJUnitRunner.class)
public class TilesMavenLifecycleParticipantTest {

	TilesMavenLifecycleParticipant participant
	ArtifactResolver mockResolver
	Logger logger
	ModelInterpolator modelInterpolator

	public final static String TILE_TEST_COORDINATES = "com.bluetrainsoftware.maven.tiles:session-license-tile:1.1-SNAPSHOT"

	public final static String PERFORM_RELEASE = "performRelease"
	static String performRelease

	@BeforeClass
	public static void storePerformRelease() {
		performRelease = System.getProperty(PERFORM_RELEASE)
	}

	@AfterClass
	public static void resetPerformRelease() {
		if (!performRelease) {
			System.clearProperty(PERFORM_RELEASE)
		} else {
			System.setProperty(PERFORM_RELEASE, performRelease)
		}
	}

	@Before
	public void setupParticipant() {
		this.participant = new TilesMavenLifecycleParticipant()

		createFakeIsolate()

		mockResolver = mock(ArtifactResolver.class)
		logger = [
		  warn: { String msg -> println msg },
			info:{ String msg -> println msg },
			debug: { String msg -> println msg },
			isDebugEnabled: { return true }
		] as Logger
		modelInterpolator = mock(ModelInterpolator.class)

		participant.resolver = mockResolver

		System.clearProperty(PERFORM_RELEASE)
	}

	protected MavenVersionIsolator createFakeIsolate() {
		return new MavenVersionIsolator() {
			@Override
			void resolveVersionRange(Artifact tileArtifact) {
			}

			@Override
			ModelProblemCollector createModelProblemCollector() {
				return [
					add: { req ->

					}
				] as ModelProblemCollector
			}
		}
	}

	public Artifact getTileTestCoordinates() {
		return participant.getArtifactFromCoordinates("it.session.maven.tiles", "session-license-tile", "0.8-SNAPSHOT")
	}

	@Test
	public void testGetArtifactFromCoordinates() {
		Artifact artifact = participant.getArtifactFromCoordinates("dummy", "dummy", "1")

		assert artifact != null

		artifact.with {
			assert groupId == "dummy"
			assert artifactId == "dummy"
			assert version == "1"
		}
	}

	@Test
	public void testNoTiles() throws MavenExecutionException {
		participant = new TilesMavenLifecycleParticipant() {
			@Override
			protected TileModel loadModel(Artifact artifact) throws MavenExecutionException {
				return new TileModel(model:new Model())
			}

			@Override
			protected MavenVersionIsolator discoverMavenVersion(MavenSession mavenSession) {
				return createFakeIsolate()
			}
		}
		createFakeIsolate()
		participant.logger = logger
		participant.modelInterpolator = modelInterpolator
		participant.orchestrateMerge(new MavenProject())
	}

	@Test
	public void testBadGav() {
		Model model = createBasicModel()
		addTileAndPlugin(model, "groupid:artifactid")
		participant = new TilesMavenLifecycleParticipant()
		MavenProject project = new MavenProject(model)

		Throwable failure = shouldFail {
			participant.orchestrateMerge(project)
		}

		assert failure.message == "groupid:artifactid does not have the form group:artifact:version-range"
	}

	public void addTileAndPlugin(Model model, String gav) {
		// add our plugin
		model.build = new Build()
		model.build.addPlugin(new Plugin())
		model.build.plugins[0].with {
			groupId = TilesMavenLifecycleParticipant.TILEPLUGIN_GROUP
			artifactId = TilesMavenLifecycleParticipant.TILEPLUGIN_ARTIFACT
			// bad GAV
			configuration = Xpp3DomBuilder.build(new StringReader("<configuration><tiles><tile>${gav}</tile></tiles></configuration>"))
		}
	}


	@Test
	public void allowSmellyBuildsToOccur() {
		Model model = runMergeTest("com.bluetrainsoftware.maven.tiles:smelly-tile:1.1-SNAPSHOT")

		assert model.repositories
		assert model.pluginRepositories
		assert model.dependencies
		assert model.dependencyManagement
		assert model.build.pluginManagement
	}

	@Test
	public void testPluginBasedMerge() throws MavenExecutionException {
		Model model = runMergeTest(TILE_TEST_COORDINATES)
		assertBuildSmellsRemoved(model)
	}

	@Test
	public void testExtendedSyntaxMerge() throws MavenExecutionException {
		Model model = runMergeTest("com.bluetrainsoftware.maven.tiles:extended-syntax-tile:1.1-SNAPSHOT")
		assertBuildSmellsRemoved(model)
	}

	@Test
	public void testBadSmellySyntaxMerge() throws MavenExecutionException {
		Throwable t = shouldFail() {
			runMergeTest("com.bluetrainsoftware.maven.tiles:bad-smelly-tile:1.1-SNAPSHOT")
		}

		assert t.message.startsWith("Discovered bad smell configuration [unknown] from <buildStink>dependencymanagement,dependencies,repositories,unknown</buildStink> in")

		println t.message
	}

	public void assertBuildSmellsRemoved(Model model) {

		assert !model.repositories
		assert !model.pluginRepositories
		assert !model.dependencies
		assert !model.dependencyManagement

	}

	@Test
	public void testSnapshotsTilesDuringRelease() {
		System.setProperty(PERFORM_RELEASE, "true")
		shouldFail(MavenExecutionException) {
			runMergeTest("com.bluetrainsoftware.maven.tiles:extended-syntax-tile:1.1-SNAPSHOT")
		}
	}

	public Model runMergeTest(String gav) {
		Model model = createBasicModel()
		addTileAndPlugin(model, gav)

		Model pureModel = model.clone()

		MavenProject project = new MavenProject(model)

		TilesMavenLifecycleParticipant oldParticipant = participant

		participant = new TilesMavenLifecycleParticipant() {
			@Override
			protected TileModel loadModel(Artifact artifact) throws MavenExecutionException {
				if (artifact.file == null) {
					return new TileModel(model:pureModel)
				} else {
					return super.loadModel(artifact)
				}
			}

			@Override
			protected MavenVersionIsolator discoverMavenVersion(MavenSession mavenSession) {
				return createFakeIsolate()
			}

		}

		createFakeIsolate()
		participant.resolver = [
		  resolve: { Artifact artifact, List<ArtifactRepository> remoteRepositories, ArtifactRepository localRepository ->
			  artifact.setFile(new File("src/test/resources/${artifact.artifactId}.xml"))
		  }
		] as ArtifactResolver

		DependencyManagementInjector dependencyManagementInjector = new DependencyManagementInjector() {
			@Override
			void injectManagement(Model model1, ModelBuildingRequest request, ModelProblemCollector problems) {
			}
		}

		participant.logger = logger
		participant.modelInterpolator = modelInterpolator
		participant.dependencyManagementInjector = dependencyManagementInjector

		participant.orchestrateMerge(project)

		assert participant.unprocessedTiles.size() == 0
		assert participant.processedTiles.size() == 3
		assert participant.tileDiscoveryOrder

		assert model.properties.size() == 3
		assert model.properties["one"] == "1"
		assert model.properties["two"] == "2"
		assert model.properties["property1"] == "property1"

		assert model.build.plugins.size() == 2 // tiles and ant-run

		Plugin antRun = model.build.plugins.find { Plugin plugin -> return plugin.artifactId == "maven-antrun-plugin"}

		assert antRun

		antRun.with {
			assert artifactId == 'maven-antrun-plugin'
			assert version == "1.7"
			assert executions.size() == 2
			assert executions*.id.intersect(["print-antrun1", "print-antrun2"])
		}

		return model
	}

	protected Model createBasicModel() {
		Model model = new Model()

		model.setGroupId("com.bluetrainsoftware.maven")
		model.setArtifactId("maven-tiles-example")
		model.setVersion("1.1-SNAPSHOT")

		Properties model1Properties = new Properties()
		model1Properties.setProperty("property1", "property1")
		model.setProperties(model1Properties)
		return model
	}
}
