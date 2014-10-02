package io.repaint.maven.tiles.isolators

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.apache.maven.MavenExecutionException
import org.apache.maven.execution.MavenSession
import org.apache.maven.model.*
import org.apache.maven.model.building.ModelProblem
import org.apache.maven.model.building.ModelProblemCollector
import org.apache.maven.model.merge.MavenModelMerger

/**
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 * @author: Mark Derricutt - https://plus.google.com/+MarkDerricutt
 */
class Maven30Isolator extends BaseMavenIsolator {
	Maven30Isolator(MavenSession mavenSession) throws MavenExecutionException {
		super(mavenSession)
	}

	protected void setupIsolateClasses(MavenSession mavenSession) {
		// lets fail fast
		Class versionRangeResolverClass = Class.forName("org.sonatype.aether.impl.VersionRangeResolver")
		versionRangeResultClass = Class.forName("org.sonatype.aether.resolution.VersionRangeResult")
		versionRangeRequestClass = Class.forName("org.sonatype.aether.resolution.VersionRangeRequest")

		versionRangeResolver = mavenSession.container.lookup(versionRangeResolverClass)
	}

	@Override
	ModelProblemCollector createModelProblemCollector() {
		def collected = []

		return [
			problems: collected,
			add: { ModelProblem.Severity severity, String message, InputLocation location, Exception cause ->
				collected.add([severity: severity, message: message, location: location, cause: cause])
			}
		] as ModelProblemCollector
	}

	@Override
	@CompileStatic(TypeCheckingMode.SKIP)
	def createModelData(Model model, File pomFile) {
		return org.apache.maven.model.building.ModelData.newInstance(model, model.groupId, model.artifactId, model.version)
	}

}
