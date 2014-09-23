package io.repaint.maven.tiles.isolators

import org.apache.maven.MavenExecutionException
import org.apache.maven.execution.MavenSession
import org.apache.maven.model.InputLocation
import org.apache.maven.model.building.ModelProblem
import org.apache.maven.model.building.ModelProblemCollector

/**
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
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
}
