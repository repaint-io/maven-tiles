package io.repaint.maven.tiles

import org.apache.maven.MavenExecutionException
import org.apache.maven.RepositoryUtils
import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.repository.ArtifactRepository
import org.apache.maven.execution.MavenSession

/**
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 */
class AetherIsolator implements MavenVersionIsolator {

	Class versionRangeRequestClass
	Class versionRangeResultClass
	def versionRangeResolver
	def repositorySystemSession
	List<ArtifactRepository> remoteRepositories
	ArtifactRepository localRepository

	public AetherIsolator(MavenSession mavenSession) throws MavenExecutionException {
		this.repositorySystemSession = mavenSession.repositorySession
		this.remoteRepositories = mavenSession.request.remoteRepositories
		this.localRepository = mavenSession.request.localRepository

		// lets fail fast
		Class versionRangeResolverClass = Class.forName("org.eclipse.aether.impl.VersionRangeResolver")
		versionRangeResultClass = Class.forName("org.eclipse.aether.resolution.VersionRangeResult")
		versionRangeRequestClass = Class.forName("org.eclipse.aether.resolution.VersionRangeRequest")

		versionRangeResolver = mavenSession.container.lookup(versionRangeResolverClass)

		if (!versionRangeResolver) {
			throw new RuntimeException()
		}
	}

	public void discoverVersionRange(Artifact tileArtifact) {
		def versionRangeRequest = versionRangeRequestClass.newInstance(RepositoryUtils.toArtifact(tileArtifact),
			RepositoryUtils.toRepos(remoteRepositories), null)

		def versionRangeResult = versionRangeResolver.resolveVersionRange(repositorySystemSession, versionRangeRequest)

		if (versionRangeResult.versions) {
			tileArtifact.version = versionRangeResult.highestVersion
		}
	}
}
