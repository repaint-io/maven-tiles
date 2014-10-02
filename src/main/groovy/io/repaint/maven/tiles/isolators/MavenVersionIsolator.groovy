package io.repaint.maven.tiles.isolators
import org.apache.maven.artifact.Artifact
import org.apache.maven.model.Model
import org.apache.maven.model.building.ModelProblemCollector
import org.apache.maven.model.merge.MavenModelMerger
/**
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 */
interface MavenVersionIsolator {
	public void resolveVersionRange(Artifact tileArtifact)
	public ModelProblemCollector createModelProblemCollector()
	public MavenModelMerger createInheritanceModelMerger()
	public def createModelData(Model model, File pomFile)
}
