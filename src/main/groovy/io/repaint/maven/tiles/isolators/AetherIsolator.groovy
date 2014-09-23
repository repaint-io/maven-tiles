package io.repaint.maven.tiles.isolators

import org.apache.maven.MavenExecutionException
import org.apache.maven.execution.MavenSession
import org.apache.maven.model.building.ModelProblemCollector

/**
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 */
class AetherIsolator extends BaseMavenIsolator {

	@Override
	ModelProblemCollector createModelProblemCollector() {
		def collected = []

		return [
			problems: collected,
		  add: { req ->
			  collected.add(req)
		  }
		] as ModelProblemCollector
	}

	AetherIsolator(MavenSession mavenSession) throws MavenExecutionException {
		super(mavenSession)
	}

	/**
	 * Yes, these mutate state but I need to fail fast if this isn't the right match.
	 *
	 * @param mavenSession - we need to ask Plexus for the range resolver.
	 */
	protected void setupIsolateClasses(MavenSession mavenSession) {
		// lets fail fast
		Class versionRangeResolverClass = Class.forName("org.eclipse.aether.impl.VersionRangeResolver")
		versionRangeResultClass = Class.forName("org.eclipse.aether.resolution.VersionRangeResult")
		versionRangeRequestClass = Class.forName("org.eclipse.aether.resolution.VersionRangeRequest")

		versionRangeResolver = mavenSession.container.lookup(versionRangeResolverClass)
	}

}