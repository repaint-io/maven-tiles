package io.repaint.maven.tiles.isolators

import org.apache.maven.MavenExecutionException
import org.apache.maven.RepositoryUtils
import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.repository.ArtifactRepository
import org.apache.maven.execution.MavenSession
import org.apache.maven.model.building.ModelBuildingRequest

/**
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 */
abstract class BaseMavenIsolator implements MavenVersionIsolator {
	Class versionRangeRequestClass
	Class versionRangeResultClass
	def versionRangeResolver
	def repositorySystemSession
	List<ArtifactRepository> remoteRepositories
	ArtifactRepository localRepository

	/**
	 * Sets the class level versionRange classes.
	 */
	abstract protected void setupIsolateClasses(MavenSession mavenSession)

	public BaseMavenIsolator(MavenSession mavenSession) throws MavenExecutionException {
		this.repositorySystemSession = mavenSession.repositorySession
		this.remoteRepositories = mavenSession.request.remoteRepositories
		this.localRepository = mavenSession.request.localRepository

		try {
			setupIsolateClasses(mavenSession)
		} catch (Exception ex) {
			throw new MavenExecutionException("Unable to load, try another Isolator.", ex)
		}

		if (!versionRangeResolver) {
			throw new MavenExecutionException("Unable to load, try another Isolator.", (Throwable)null)
		}
	}

	public void resolveVersionRange(Artifact tileArtifact) {
		def versionRangeRequest = versionRangeRequestClass.newInstance(RepositoryUtils.toArtifact(tileArtifact),
			RepositoryUtils.toRepos(remoteRepositories), null)

		def versionRangeResult = versionRangeResolver.resolveVersionRange(repositorySystemSession, versionRangeRequest)

		// Allow SNAPSHOT only if one of the bounds of version range is SNAPSHOT
		boolean allowSnaphsot = (tileArtifact.version && tileArtifact.version.contains("-SNAPSHOT")) \
			|| (tileArtifact.versionRange && tileArtifact.versionRange.toString().contains("-SNAPSHOT"))
		
		if (versionRangeResult.versions) {
			if(allowSnaphsot) {
				tileArtifact.version = versionRangeResult.highestVersion
			} else {
				versionRangeResult.versions.each { Object version ->
					if(!version.toString().endsWith("-SNAPSHOT")) {
						tileArtifact.version = version
					}
				}
			}
		}
	}
}
