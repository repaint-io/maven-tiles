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
import io.repaint.maven.tiles.isolators.MavenVersionIsolator
import org.apache.maven.MavenExecutionException
import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.repository.ArtifactRepository
import org.apache.maven.artifact.resolver.ArtifactNotFoundException
import org.apache.maven.artifact.resolver.ArtifactResolutionException
import org.apache.maven.artifact.resolver.ArtifactResolver
import org.apache.maven.execution.MavenSession
import org.apache.maven.model.*
import org.apache.maven.model.building.ModelBuildingRequest
import org.apache.maven.model.building.ModelData
import org.apache.maven.model.building.ModelProblemCollector
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.apache.maven.project.MavenProject
import org.codehaus.plexus.logging.Logger
import org.codehaus.plexus.util.xml.Xpp3DomBuilder
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.runners.MockitoJUnitRunner

import static groovy.test.GroovyAssert.shouldFail
import static org.mockito.Mockito.mock

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

		mockResolver = mock(ArtifactResolver.class)
		logger = [
		  warn: { String msg -> println msg },
			info:{ String msg -> println msg },
			debug: { String msg -> println msg },
			isDebugEnabled: { return true }
		] as Logger

		stuffParticipant()

		System.clearProperty(PERFORM_RELEASE)
	}

	void stuffParticipant() {
		participant.logger = logger
		participant.resolver = mockResolver
		participant.mavenVersionIsolate = createFakeIsolate()

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

			@Override
			ModelData createModelData(Model model, File pomFile) {
				return null
			}
		}
	}

	public Artifact getTileTestCoordinates() {
		return participant.getArtifactFromCoordinates("it.session.maven.tiles", "session-license-tile", "0.8-SNAPSHOT")
	}


	@Test
	public void ensureSnapshotFailsOnRelease() {
		Artifact snapshot = getTileTestCoordinates()
		System.setProperty(PERFORM_RELEASE, "true")
		shouldFail(MavenExecutionException) {
			participant.resolveTile(snapshot)
		}
	}

	@Test
	public void ensureBadArtifactsFail() {
		Artifact badbadbad = participant.getArtifactFromCoordinates("bad", "bad", "bad")

		participant.resolver = [
			resolve: { Artifact artifact, List<ArtifactRepository> remoteRepositories, ArtifactRepository localRepository ->
				throw new ArtifactResolutionException("failed", badbadbad)
			}
		] as ArtifactResolver

		shouldFail(MavenExecutionException) {
			participant.resolveTile(badbadbad)
		}
		participant.resolver = [
			resolve: { Artifact artifact, List<ArtifactRepository> remoteRepositories, ArtifactRepository localRepository ->
				throw new ArtifactNotFoundException("failed", badbadbad)
			}
		] as ArtifactResolver

		shouldFail(MavenExecutionException) {
			participant.resolveTile(badbadbad)
		}
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
	public void testGavFromString() {
		Artifact dummy = participant.turnPropertyIntoUnprocessedTile("my:long:feet", null)

		assert dummy.version == 'feet'
		assert dummy.artifactId == 'long'
		assert dummy.groupId == 'my'
		assert dummy.classifier == 'tile-pom'
		assert dummy.type == 'tile'

		// too short
		shouldFail(MavenExecutionException) {
			participant.turnPropertyIntoUnprocessedTile("my:long", null)
		}

		// too long
		shouldFail(MavenExecutionException) {
			participant.turnPropertyIntoUnprocessedTile("my:long:feet:and:shoes", null)
		}
	}

	@Test
	public void canLoadExtendedTiles() {
		Artifact artifact = participant.turnPropertyIntoUnprocessedTile("io.repaint.tiles:extended-syntax:1.1", null)
		artifact.file = new File("src/test/resources/extended-syntax-tile.xml")
		assert participant.loadModel(artifact)
		artifact.file = new File("src/test/resources/session-license-tile.xml")
		assert participant.loadModel(artifact)
		artifact.file = new File("src/test/resources/bad-smelly-tile.xml")
		assert participant.loadModel(artifact)

		shouldFail(MavenExecutionException) {
			artifact.file = new File("src/test/resources/extended-syntax-tile1.xml")
			participant.loadModel(artifact)
		}

		shouldFail(MavenExecutionException) {
			artifact.file = new File("src/test/resources/invalid-tile.xml")
			participant.loadModel(artifact)
		}

		shouldFail(MavenExecutionException) {
			artifact.file = new File("src/test/resources/not-a-file-file.xml")
			participant.loadModel(artifact)
		}
	}

	@Test
	public void canUseModelResolver() {
		File licensePom = new File('src/test/resources/session-license-pom.xml')

		participant.mavenVersionIsolate = [
			resolveVersionRange: { Artifact artifact ->
				artifact.file = licensePom
			}
		] as MavenVersionIsolator

		def resolver = participant.createModelResolver()
		def model = resolver.resolveModel('my', 'left', 'foot')

		assert model.inputStream.text == licensePom.text
		assert model.location == licensePom.absolutePath

		model.inputStream.close()
	}

	protected Model readModel(File pomFile) {
		MavenXpp3Reader modelReader = new MavenXpp3Reader()
		Model pomModel

		pomFile.withReader { Reader r ->
			pomModel = modelReader.read(r)
		}

		return pomModel
	}

	@Test
	public void injectModelLayerTiles() {
		TileModel sessionLicenseTile = new TileModel(new File('src/test/resources/session-license-tile.xml'),
			participant.turnPropertyIntoUnprocessedTile('io.repaint.tiles:session-license:1', null))

		TileModel extendedSyntaxTile = new TileModel(new File('src/test/resources/extended-syntax-tile.xml'),
			participant.turnPropertyIntoUnprocessedTile('io.repaint.tiles:extended-syntax:1', null))

		TileModel antrunTile = new TileModel(new File('src/test/resources/antrun1-tile.xml'),
			participant.turnPropertyIntoUnprocessedTile('io.repaint.tiles:antrun1:1', null))

		List<TileModel> tiles = [
			sessionLicenseTile,
			extendedSyntaxTile,
			antrunTile,
		]

		File pomFile = new File('src/test/resources/empty-pom.xml')
		Model pomModel = readModel(pomFile)

		participant = new TilesMavenLifecycleParticipant() {
			@Override
			protected void putModelInCache(Model model, ModelBuildingRequest request, File pFile) {
			}
		}

		stuffParticipant()

		participant.injectTilesIntoParentStructure(tiles, pomModel, [getPomFile: { return pomFile }] as ModelBuildingRequest)

		assert pomModel.parent.artifactId == 'session-license'
		assert sessionLicenseTile.model.parent.artifactId == 'extended-syntax'
		assert extendedSyntaxTile.model.parent.artifactId == 'antrun1'
		assert antrunTile.model.parent == null

		pomModel.parent = new Parent(groupId: 'io.repaint.tiles', artifactId: 'fake-parent', version: '1')

		participant.injectTilesIntoParentStructure(tiles, pomModel, [getPomFile: { return pomFile }] as ModelBuildingRequest)
		assert antrunTile.model.parent.artifactId == 'fake-parent'
	}

	@Test
	public void cleanModel() {
		Model model = new Model()
		model.dependencies = [new Dependency()]
		model.pluginRepositories = [new Repository()]
		model.repositories = [new Repository()]
		model.dependencyManagement = new DependencyManagement()

		participant.cleanModel(model)

		assert model.dependencies.size() == 0
		assert model.pluginRepositories.size() == 0
		assert model.repositories.size() == 0
		assert model.dependencyManagement == null
	}

	@Test
	public void testNoTiles() throws MavenExecutionException {
		participant = new TilesMavenLifecycleParticipant() {
			@Override
			protected TileModel loadModel(Artifact artifact) throws MavenExecutionException {
				return new TileModel(model:new Model())
			}
		}

		stuffParticipant()

		participant.orchestrateMerge(new MavenProject())
	}

	@Test
	public void testBadGav() {
		Model model = createBasicModel()
		addTileAndPlugin(model, "groupid:artifactid")

		participant = new TilesMavenLifecycleParticipant()
		stuffParticipant()

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

	protected resetParticipantToLoadTilesFromDisk() {
		participant = new TilesMavenLifecycleParticipant() {
			@Override
			protected void thunkModelBuilder(MavenProject project1) {
			}

			@Override
			protected Artifact resolveTile(Artifact tileArtifact) throws MavenExecutionException {
				tileArtifact.file = new File("src/test/resources/${tileArtifact.artifactId}.xml")

				return tileArtifact
			}
		}

		stuffParticipant()
	}

	protected MavenProject fakeProjectFromFile(String pom) {
		File pomFile = new File("src/test/resources/${pom}.xml")

		return [
			getModel: { return readModel(pomFile)},
			getPomFile: { return pomFile }
		] as MavenProject
	}

	@Test
	public void testTileResolve() {
		MavenProject project = fakeProjectFromFile("full-tile-load-pom")

		resetParticipantToLoadTilesFromDisk()

		participant.orchestrateMerge(project)

		assert participant.processedTiles.size() == 4
	}

	@Test
	public void testCascadeIgnore() {
		MavenProject project = fakeProjectFromFile("cascade-check-pom")

		resetParticipantToLoadTilesFromDisk()

		participant.orchestrateMerge(project)

		assert participant.processedTiles.size() == 4
		assert participant.processedTiles['io.repaint.tiles:release-tile'] == null
	}

	@Test
	public void testDuplicateTilesIgnored() {
		MavenProject project = fakeProjectFromFile("duplicate-tile-pom")

		resetParticipantToLoadTilesFromDisk()

		participant.orchestrateMerge(project)
		assert participant.processedTiles.size() == 4
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
