package io.repaint.maven.tiles

import io.repaint.maven.tiles.AttachTileMojo
import org.apache.maven.artifact.Artifact
import org.apache.maven.project.MavenProject
import org.apache.maven.project.MavenProjectHelper
import org.codehaus.plexus.logging.Logger
import org.junit.Test

/**
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 * @author: Mark Derricutt - https://plus.google.com/+MarkDerricutt
 */
class AttachTileMojoTest {


	private File makeAttachTileMojo(String packaging) {

		File foundTile = null

		AttachTileMojo attach = new AttachTileMojo() {
			@Override
			File getTile() {
				return new File("src/test/resources/session-license-tile.xml")
			}
		}

		attach.project = [
				getPackaging: { -> return packaging },
				getArtifact: { -> return [
						setFile: { File tile ->
							foundTile = tile;
						}
				] as Artifact}
		] as MavenProject

		attach.projectHelper = [
				attachArtifact: { MavenProject project, String attachedPackaging, File tile ->
					foundTile = tile
				}
		] as MavenProjectHelper

		attach.logger = [
				info: { String msg -> println msg },
				error: { String msg, Throwable t = null ->
					println msg
					if (t) { t.printStackTrace() }
				},
				warn: { String msg -> println msg }
		] as Logger

		attach.execute()

		foundTile
	}

	@Test
	public void attachingTileWithJarProject() {
		assert makeAttachTileMojo("jar").exists()
	}

	@Test
	public void attachingTileWithTileProject() {
		assert makeAttachTileMojo("tile").exists()
	}
}
