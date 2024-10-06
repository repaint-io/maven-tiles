package io.repaint.maven.tiles

import org.apache.maven.artifact.Artifact
import org.apache.maven.project.MavenProject
import org.apache.maven.project.MavenProjectHelper
import org.junit.Test
import org.slf4j.Logger

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
