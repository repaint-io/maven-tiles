package io.repaint.maven.tiles

import io.repaint.maven.tiles.TileValidator
import org.junit.Before
import org.junit.Test
import org.slf4j.Logger

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
		new TileValidator().loadModel(logger, new File("src/test/resources/bad-tile.xml"), "")

		assert errors.size() == 10
		assert warnings.size() == 0
		assert infos.size() == 0
	}

	@Test void testNoFile() {
		new TileValidator().loadModel(logger, null, "")

		assert errors.size() == 1
		assert warnings.size() == 0
		assert infos.size() == 0
	}

	@Test void noSuchFileExists() {
		new TileValidator().loadModel(logger, new File("skink.txt"), "")

		assert errors.size() == 1
		assert warnings.size() == 0
		assert infos.size() == 0
	}

	@Test void okFile() {
		new TileValidator().loadModel(logger, new File("src/test/resources/session-license-tile.xml"), "")
		assert errors.size() == 0
		assert warnings.size() == 0
		assert infos.size() == 1
	}
}
