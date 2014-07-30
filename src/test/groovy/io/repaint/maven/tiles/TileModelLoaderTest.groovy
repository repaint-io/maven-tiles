package io.repaint.maven.tiles

import io.repaint.maven.tiles.TileModel
import org.junit.Test

/**
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 */
class TileModelLoaderTest {
	@Test
	public void testLoad() {
		def loader = new TileModel()
		loader.loadTile(new File("src/test/resources/extended-syntax-tile.xml"))
		assert loader.tiles.size() == 2
	}
}
