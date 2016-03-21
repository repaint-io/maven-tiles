package io.repaint.maven.tiles

import org.codehaus.plexus.logging.Logger
import org.junit.Before
import org.junit.Test
/**
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 */
class TileValidatorTest {
	List<String> errors
	List<String> warnings
	List<String> infos
	Logger logger

	@Before
	public void before() {
		errors = []
		warnings = []
		infos = []
		logger = [
		  info: { String msg -> infos << msg },
			error: { String msg, Throwable t = null -> errors << msg },
			warn: { String msg -> warnings << msg }
		] as Logger
	}

	@Test
	public void testValidation() {
		new TileValidator().loadModel(logger, new File("src/test/resources/bad-tile.xml"), [])

		assert errors.size() == 8
		assert warnings.size() == 0
		assert infos.size() == 0
	}

	@Test void testNoFile() {
		new TileValidator().loadModel(logger, null, [])

		assert errors.size() == 1
		assert warnings.size() == 0
		assert infos.size() == 0
	}

	@Test void noSuchFileExists() {
		new TileValidator().loadModel(logger, new File("skink.txt"), [])

		assert errors.size() == 1
		assert warnings.size() == 0
		assert infos.size() == 0
	}

	@Test void okFile() {
		new TileValidator().loadModel(logger, new File("src/test/resources/session-license-tile.xml"), [])
		assert errors.size() == 0
		assert warnings.size() == 0
		assert infos.size() == 1
	}

	@Test void smellyFile() {
		new TileValidator().loadModel(logger, new File("src/test/resources/antrun2-tile.xml"), [
				AbstractTileMojo.BuildSmell.Repositories,
				AbstractTileMojo.BuildSmell.PluginRepositories,
				AbstractTileMojo.BuildSmell.PluginManagement,
				AbstractTileMojo.BuildSmell.DependencyManagement,
				AbstractTileMojo.BuildSmell.Dependencies
		])
		assert errors.size() == 0
		assert warnings.size() == 0
		assert infos.size() == 1
	}

	@Test void badSmellyFile() {
		new TileValidator().loadModel(logger, new File("src/test/resources/antrun2-tile.xml"), [])
		assert errors.size() == 5
		assert warnings.size() == 0
		assert infos.size() == 0
	}

}
