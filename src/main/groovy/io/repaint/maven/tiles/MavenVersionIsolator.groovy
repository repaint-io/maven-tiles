package io.repaint.maven.tiles

import org.apache.maven.artifact.Artifact

/**
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 */
interface MavenVersionIsolator {
	public void discoverVersionRange(Artifact tileArtifact)
}
