package io.repaint.maven.tiles

import io.repaint.maven.tiles.TileModel
import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.DefaultArtifact
import org.apache.maven.artifact.handler.DefaultArtifactHandler
import org.apache.maven.artifact.versioning.VersionRange
import org.junit.Test

/**
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 */
class TileModelTest {
	@Test
	public void testLoad() {
		def loader = new TileModel()
		loader.loadTile(new File("src/test/resources/extended-syntax-tile.xml"))
		assert loader.tiles.size() == 2
	}

	@Test
	public void testReplaceExecutionId() {
		Artifact artifact = new DefaultArtifact("io.repaint.tiles",
			"execution-id-replacing-tile",
			VersionRange.createFromVersion("1.1-SNAPSHOT"),
			"compile",
			"xml",
			"",
			new DefaultArtifactHandler("xml"))

		TileModel tileModel = new TileModel(new File("src/test/resources/execution-id-tile.xml"), artifact)

		assert tileModel.model.build.plugins[0].executions[0].id == "io.repaint.tiles:execution-id-replacing-tile:1.1-SNAPSHOT::1"
		assert tileModel.model.build.plugins[0].executions[1].id == "2"
		assert tileModel.model.build.plugins[0].executions[2].id == "3"
		assert tileModel.model.profiles[0].build.plugins[0].executions[0].id == "io.repaint.tiles:execution-id-replacing-tile:1.1-SNAPSHOT::4"
		assert tileModel.model.profiles[0].build.plugins[0].executions[1].id == "5"
		assert tileModel.model.profiles[0].build.plugins[0].executions[2].id == "6"
	}

}
