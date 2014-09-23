package io.repaint.maven.tiles.isolators

import org.apache.maven.artifact.Artifact
import org.apache.maven.model.building.ModelProblemCollector

/**
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 */
interface MavenVersionIsolator {
	public void resolveVersionRange(Artifact tileArtifact)
	public ModelProblemCollector createModelProblemCollector()
}
