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

import org.apache.maven.MavenExecutionException
import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.repository.ArtifactRepository
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy
import org.apache.maven.artifact.repository.Authentication
import org.apache.maven.artifact.repository.MavenArtifactRepository
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout2
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout
import org.apache.maven.artifact.resolver.ArtifactResolutionException
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest
import org.apache.maven.artifact.resolver.ArtifactResolutionResult
import org.apache.maven.artifact.resolver.DefaultResolutionErrorHandler
import org.apache.maven.execution.MavenExecutionRequest
import org.apache.maven.execution.MavenExecutionResult
import org.apache.maven.execution.MavenSession
import org.apache.maven.model.Build
import org.apache.maven.model.DeploymentRepository
import org.apache.maven.model.DistributionManagement
import org.apache.maven.model.Model
import org.apache.maven.model.Parent
import org.apache.maven.model.Plugin
import org.apache.maven.model.PluginExecution
import org.apache.maven.model.building.ModelBuildingRequest
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.apache.maven.project.MavenProject
import org.apache.maven.repository.RepositorySystem
import org.apache.maven.shared.filtering.DefaultMavenFileFilter
import org.apache.maven.shared.filtering.DefaultMavenResourcesFiltering
import org.codehaus.plexus.util.xml.Xpp3Dom
import org.codehaus.plexus.util.xml.Xpp3DomBuilder
import org.eclipse.aether.impl.VersionRangeResolver
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.slf4j.Logger
import org.sonatype.plexus.build.incremental.DefaultBuildContext

import static groovy.test.GroovyAssert.shouldFail
import static io.repaint.maven.tiles.Constants.TILEPLUGIN_ARTIFACT
import static io.repaint.maven.tiles.Constants.TILEPLUGIN_GROUP
import static io.repaint.maven.tiles.GavUtil.artifactName
import static org.apache.maven.artifact.repository.ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN
import static org.apache.maven.artifact.repository.ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS
import static org.apache.maven.artifact.repository.ArtifactRepositoryPolicy.UPDATE_POLICY_DAILY
import static org.junit.Assert.assertEquals
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when

/**
 * If testMergeTile fails with java.io.FileNotFoundException: src/test/resources/licenses-tiles-pom.xml
 * (No such file or directory)) when running the test from your IDE, make sure you configure the Working
 * Directory as maven-tiles/tiles-maven-plugin (absolute path)
 */
public class TilesMavenLifecycleParticipantTest {

	TilesMavenLifecycleParticipant participant
	MavenSession mockMavenSession
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

		mockMavenSession = mock(MavenSession.class)
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
		this.participant.logger = logger
		this.participant.mavenSession = mockMavenSession
		this.participant.repository = [
			resolve: { ArtifactResolutionRequest req ->
				new ArtifactResolutionResult()
			}
		] as RepositorySystem
		this.participant.resolutionErrorHandler = new DefaultResolutionErrorHandler()
		this.participant.versionRangeResolver = [
			resolveVersionRange: { session, request ->
				return null
			}
		] as VersionRangeResolver

	}

	public Artifact getTileTestCoordinates() {
		return this.participant.getArtifactFromCoordinates("it.session.maven.tiles", "session-license-tile", "xml", "", "0.8-SNAPSHOT")
	}

	@Test
	public void ensureSnapshotFailsOnRelease() {
		Artifact snapshot = getTileTestCoordinates()
		System.setProperty(PERFORM_RELEASE, "true")
		shouldFail(MavenExecutionException) {
			this.participant.resolveTile(null, null, snapshot)
		}
	}

	@Test
	public void ensureBadArtifactsFail() {
		Artifact badbadbad = this.participant.getArtifactFromCoordinates("bad", "bad", "bad", "bad", "bad")

		this.participant.repository = [
			resolve: { ArtifactResolutionRequest request ->
				new ArtifactResolutionResult().addErrorArtifactException(new ArtifactResolutionException("failed", badbadbad))
			}
		] as RepositorySystem

		shouldFail(MavenExecutionException) {
			this.participant.resolveTile(null, null, badbadbad)
		}
		this.participant.repository = [
			resolve: { ArtifactResolutionRequest request ->
				new ArtifactResolutionResult().addMissingArtifact(badbadbad)
			}
		] as RepositorySystem

		shouldFail(MavenExecutionException) {
			this.participant.resolveTile(null, null, badbadbad)
		}
	}

	@Test
	void testTileMerge() {

		Model model = new Model()
		model.setGroupId("io.repaint.tiles")
		model.setArtifactId("test-merge-tile")
		model.setVersion("1.1-SNAPSHOT")

		model.build = new Build()
		model.build.directory = "target/test-merge-tile"

		MavenProject project = new MavenProject(model)
		project.setFile(new File("src/test/resources/test-merge-tile/pom.xml"))

		MavenExecutionRequest req = mock(MavenExecutionRequest.class)
		when(req.getUserProperties()).thenReturn(new Properties())
		when(req.getSystemProperties()).thenReturn(new Properties())

		MavenSession session = new MavenSession(null, req, mock(MavenExecutionResult.class), Arrays.asList(project))

		addUnprocessedTile('test-merge-tile/kapt-tile.xml', 'kapt-tile')
		addUnprocessedTile('test-merge-tile/kapt-dinject-tile.xml', 'kapt-dinject-tile')
		addUnprocessedTile('test-merge-tile/kapt-javalin-tile.xml', 'kapt-javalin-tile')

		// act
		this.participant.loadAllDiscoveredTiles(session, project)


		Model tileModel = this.participant.processedTiles['io.repaint.tiles:kapt-tile'].tileModel.model
		PluginExecution pluginExecution = tileModel.build.plugins[0].executions[0]
		assert pluginExecution.id == 'kapt'

		// assert properties have been merged
		assert tileModel.properties['dinject-generator.version'] == '1.8'
		assert tileModel.properties['kotlin.version'] == '1.3.31'

		String expectedAnnotationProcessorPaths = '''
<?xml version="1.0" encoding="UTF-8"?>
<annotationProcessorPaths>
  <annotationProcessorPath>
    <groupId>io.dinject</groupId>
    <artifactId>javalin-generator</artifactId>
    <version>1.6</version>
  </annotationProcessorPath>
  <annotationProcessorPath>
    <groupId>io.dinject</groupId>
    <artifactId>dinject-generator</artifactId>
    <version>${dinject-generator.version}</version>
  </annotationProcessorPath>
</annotationProcessorPaths>
'''.trim()

		// assert the annotationProcessorPaths have been appended
		Xpp3Dom paths = ((Xpp3Dom)pluginExecution.configuration).getChild('annotationProcessorPaths')
		assertEquals(paths.toString().trim(), expectedAnnotationProcessorPaths)
	}

	def addUnprocessedTile(String testResourceName, String tileName) {
		Artifact kaptTile = this.participant.turnPropertyIntoUnprocessedTile("io.repaint.tiles:$tileName:1.1", null)
		kaptTile.file = new File("src/test/resources/$testResourceName")
		this.participant.unprocessedTiles.put(artifactName(kaptTile), kaptTile)
	}

	@Test
	public void testFiltering() {
		final def context = new DefaultBuildContext()

		this.participant.mavenFileFilter = new DefaultMavenFileFilter(context)
		this.participant.mavenResourcesFiltering = new DefaultMavenResourcesFiltering(this.participant.mavenFileFilter, context)

		Artifact filteredTile = this.participant.getArtifactFromCoordinates("io.repaint.tiles", "filtering-tile", "xml", "", "1.1-SNAPSHOT")

		Model model = new Model()
		model.setGroupId("io.repaint.tiles")
		model.setArtifactId("filtering-tile")
		model.setVersion("1.1-SNAPSHOT")

		model.build = new Build()
		model.build.directory = "target/filtering"
		model.build.addPlugin(new Plugin())
		model.build.plugins[0].with {
			groupId = TILEPLUGIN_GROUP
			artifactId = TILEPLUGIN_ARTIFACT
			configuration = Xpp3DomBuilder.build(new StringReader("<configuration><filtering>true</filtering><generatedSourcesDirectory>target/filtering/generated-tiles</generatedSourcesDirectory></configuration>"))
		}

		MavenProject project = new MavenProject(model)
		project.setFile(new File("src/test/resources/filtering/pom.xml"))

		MavenExecutionRequest req = mock(MavenExecutionRequest.class)
		when(req.getUserProperties()).thenReturn(new Properties())
		when(req.getSystemProperties()).thenReturn(new Properties())

		MavenSession session = new MavenSession(null, req, mock(MavenExecutionResult.class), Arrays.asList(project))

		Artifact tile = this.participant.resolveTile(session, project, filteredTile)
		assert tile.file == new File("target/filtering/generated-tiles/tiles/tile.xml")

		TileModel tileModel = new TileModel(tile.file, tile)

		assert tileModel.tiles[0] == "groupid:antrun1-tile:1.1-SNAPSHOT"
	}

	@Test
	public void testNoFiltering() {
		Artifact filteredTile = this.participant.getArtifactFromCoordinates("io.repaint.tiles", "filtering-tile", "xml", "", "1.1-SNAPSHOT")

		Model model = new Model()
		model.setGroupId("io.repaint.tiles")
		model.setArtifactId("filtering-tile")
		model.setVersion("1.1-SNAPSHOT")

		model.build = new Build()
		model.build.directory = "target/filtering"
		model.build.addPlugin(new Plugin())
		model.build.plugins[0].with {
			groupId = TILEPLUGIN_GROUP
			artifactId = TILEPLUGIN_ARTIFACT
		}

		MavenProject project = new MavenProject(model)
		project.setFile(new File("src/test/resources/filtering/pom.xml"))

		MavenExecutionRequest req = mock(MavenExecutionRequest.class)
		when(req.getUserProperties()).thenReturn(new Properties())
		when(req.getSystemProperties()).thenReturn(new Properties())

		MavenSession session = new MavenSession(null, req, mock(MavenExecutionResult.class), Arrays.asList(project))

		Artifact tile = this.participant.resolveTile(session, project, filteredTile)

		assert tile.file == new File("src/test/resources/filtering/tile.xml")

		TileModel tileModel = new TileModel(tile.file, tile)

		assert tileModel.tiles[0] == "groupid:antrun1-tile:@project.version@"
	}

	@Test
	public void testGetArtifactFromCoordinates() {
		Artifact artifact = this.participant.getArtifactFromCoordinates("dummy", "dummy", "xml", "classy", "1")

		assert artifact != null

		artifact.with {
			assert groupId == "dummy"
			assert artifactId == "dummy"
			assert type == "xml"
			assert classifier == "classy"
			assert version == "1"
		}
	}

	@Test
	public void testGavFromString() {
		Artifact dummy = this.participant.turnPropertyIntoUnprocessedTile("my:long:feet", null)

		assert dummy.version == 'feet'
		assert dummy.artifactId == 'long'
		assert dummy.groupId == 'my'
		assert dummy.classifier == ''
		assert dummy.type == 'xml'

		Artifact dummy2 = this.participant.turnPropertyIntoUnprocessedTile("my:long:sore:smelly:feet", null)

		assert dummy2.version == 'feet'
		assert dummy2.artifactId == 'long'
		assert dummy2.groupId == 'my'
		assert dummy2.classifier == 'smelly'
		assert dummy2.type == 'sore'

		// too short
		shouldFail(MavenExecutionException) {
			this.participant.turnPropertyIntoUnprocessedTile("my:long", null)
		}

		// too long
		shouldFail(MavenExecutionException) {
			this.participant.turnPropertyIntoUnprocessedTile("my:long:feet:and:smelly:shoes", null)
		}
	}

	@Test
	public void canLoadExtendedTiles() {
		Artifact artifact = this.participant.turnPropertyIntoUnprocessedTile("io.repaint.tiles:extended-syntax:1.1", null)
		artifact.file = new File("src/test/resources/extended-syntax-tile.xml")
		assert this.participant.loadModel(artifact)
		artifact.file = new File("src/test/resources/session-license-tile.xml")
		assert this.participant.loadModel(artifact)
		artifact.file = new File("src/test/resources/bad-smelly-tile.xml")
		assert this.participant.loadModel(artifact)

		shouldFail(MavenExecutionException) {
			artifact.file = new File("src/test/resources/extended-syntax-tile1.xml")
			this.participant.loadModel(artifact)
		}

		shouldFail(MavenExecutionException) {
			artifact.file = new File("src/test/resources/invalid-tile.xml")
			this.participant.loadModel(artifact)
		}

		shouldFail(MavenExecutionException) {
			artifact.file = new File("src/test/resources/not-a-file-file.xml")
			this.participant.loadModel(artifact)
		}
	}

	@Test
	public void canUseModelResolver() {
		File licensePom = new File('src/test/resources/session-license-pom.xml')

		this.participant = new TilesMavenLifecycleParticipant() {
			@Override
			void resolveVersionRange(MavenProject project, Artifact tileArtifact) {
				tileArtifact.file = licensePom
			}
		}

		stuffParticipant()

		def resolver = this.participant.createModelResolver(null)
		def model = resolver.resolveModel('my', 'left', 'foot')

		assert model.inputStream.text == licensePom.text
		assert model.location == licensePom.absolutePath

		model.inputStream.close()
	}

	protected static Model readModel(File pomFile) {
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
				this.participant.turnPropertyIntoUnprocessedTile('io.repaint.tiles:session-license:1', null))

		TileModel extendedSyntaxTile = new TileModel(new File('src/test/resources/extended-syntax-tile.xml'),
				this.participant.turnPropertyIntoUnprocessedTile('io.repaint.tiles:extended-syntax:1', null))

		TileModel antrunTile = new TileModel(new File('src/test/resources/antrun1-tile.xml'),
				this.participant.turnPropertyIntoUnprocessedTile('io.repaint.tiles:antrun1:1', null))

		List<TileModel> tiles = [
			sessionLicenseTile,
			extendedSyntaxTile,
			antrunTile,
		]

		File pomFile = new File('src/test/resources/empty-pom.xml')
		Model pomModel = readModel(pomFile)

		this.participant = new TilesMavenLifecycleParticipant() {
			@Override
			protected void putModelInCache(Model model, ModelBuildingRequest request, File pFile) {
			}
		}

		stuffParticipant()
		when(mockMavenSession.getUserProperties()).thenReturn(new Properties())
		when(mockMavenSession.getSystemProperties()).thenReturn(new Properties())

		this.participant.injectTilesIntoParentStructure(tiles, pomModel, [getPomFile: { return pomFile }] as ModelBuildingRequest)

		assert pomModel.parent.artifactId == 'session-license'
		assert sessionLicenseTile.model.parent.artifactId == 'extended-syntax'
		assert extendedSyntaxTile.model.parent.artifactId == 'antrun1'
		assert antrunTile.model.parent == null

		pomModel.parent = new Parent(groupId: 'io.repaint.tiles', artifactId: 'fake-parent', version: '1')

		this.participant.injectTilesIntoParentStructure(tiles, pomModel, [getPomFile: { return pomFile }] as ModelBuildingRequest)
		assert antrunTile.model.parent.artifactId == 'fake-parent'
	}

	@Test
	public void testNoTiles() throws MavenExecutionException {
		this.participant = new TilesMavenLifecycleParticipant() {
			@Override
			protected TileModel loadModel(Artifact artifact) throws MavenExecutionException {
				return new TileModel(model:new Model())
			}
		}

		stuffParticipant()

		this.participant.orchestrateMerge(null, new MavenProject())
	}

	@Test
	public void testBadGav() {
		Model model = createBasicModel()
		addTileAndPlugin(model, "groupid:artifactid")

		this.participant = new TilesMavenLifecycleParticipant()
		stuffParticipant()

		MavenProject project = new MavenProject(model)

		Throwable failure = shouldFail {
			this.participant.orchestrateMerge(null, project)
		}

		assert failure.message == "groupid:artifactid does not have the form group:artifact:version-range or group:artifact:extension:classifier:version-range"
	}

	public void addTileAndPlugin(Model model, String gav) {
		// add our plugin
		model.build = new Build()
		model.build.addPlugin(new Plugin())
		model.build.plugins[0].with {
			groupId = TILEPLUGIN_GROUP
			artifactId = TILEPLUGIN_ARTIFACT
			// bad GAV
			configuration = Xpp3DomBuilder.build(new StringReader("<configuration><tiles><tile>${gav}</tile></tiles></configuration>"))
		}
	}

	protected resetParticipantToLoadTilesFromDisk() {
		this.participant = new TilesMavenLifecycleParticipant() {
			@Override
			protected void thunkModelBuilder(MavenProject project1) {
			}

			@Override
			protected Artifact resolveTile(MavenSession mavenSession, MavenProject project, Artifact tileArtifact) throws MavenExecutionException {
				tileArtifact.file = new File("src/test/resources/${tileArtifact.artifactId}.xml")
				return tileArtifact
			}
		}

		stuffParticipant()
	}

	protected static MavenProject fakeProjectFromFile(String pom) {
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

		this.participant.orchestrateMerge(null, project)

		assert this.participant.processedTiles.size() == 4
	}

	@Test
	public void testDuplicateTilesIgnored() {
		MavenProject project = fakeProjectFromFile("duplicate-tile-pom")

		resetParticipantToLoadTilesFromDisk()

		this.participant.orchestrateMerge(null, project)
		assert this.participant.processedTiles.size() == 4
	}

	@Test
	public void testAuthenticationSettingsArePresent() {
		Model model = createBasicModel()
		MavenProject project = new MavenProject(model)

		def repository = new MavenArtifactRepository("central", "uri://nowhere/", new DefaultRepositoryLayout(), new ArtifactRepositoryPolicy(true, ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS, ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN), new ArtifactRepositoryPolicy(true, ArtifactRepositoryPolicy.UPDATE_POLICY_DAILY, ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN))
		repository.setAuthentication(new Authentication("username", "secret"))
		project.setRemoteArtifactRepositories([
				repository
		])

		project.model.distributionManagement = new DistributionManagement()

		project.model.distributionManagement.repository = new DeploymentRepository()
		project.model.distributionManagement.repository.setId("central");

		project.model.distributionManagement.snapshotRepository = new DeploymentRepository()
		project.model.distributionManagement.snapshotRepository.setId("central");


		def participant = new TilesMavenLifecycleParticipant()
		participant.discoverAndSetDistributionManagementArtifactoryRepositoriesIfTheyExist(project);


		def releaseAuthentication = project.releaseArtifactRepository.authentication
		assert releaseAuthentication !=null;
		assert releaseAuthentication.username == "username"
		assert releaseAuthentication.password == "secret"

		def snapshotAuthentication = project.snapshotArtifactRepository.authentication
		assert snapshotAuthentication !=null;
		assert snapshotAuthentication.username == "username"
		assert snapshotAuthentication.password == "secret"
	}

	protected static Model createBasicModel() {
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
