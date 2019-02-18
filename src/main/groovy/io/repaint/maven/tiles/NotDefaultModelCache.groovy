package io.repaint.maven.tiles

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.apache.maven.execution.MavenSession
import org.apache.maven.model.building.ModelCache
import org.eclipse.aether.RepositoryCache
import org.eclipse.aether.RepositorySystemSession

/**
 * Because the Default one is package private *sigh*
 *
 * We have the org.aether / org.sonatype issue
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 */
@CompileStatic
class NotDefaultModelCache implements ModelCache {

	RepositorySystemSession session
	RepositoryCache cache

	NotDefaultModelCache(MavenSession mavenSession) {
		this.session = mavenSession.repositorySession
		this.cache = mavenSession.repositorySession.cache
	}

	Object get( String groupId, String artifactId, String version, String tag) {
		return cache.get(session, new Key( groupId, artifactId, version, tag))
	}

	void put(String groupId, String artifactId, String version, String tag, Object data) {
		cache.put(session, new Key(groupId, artifactId, version, tag), data)
	}

	@CompileStatic(TypeCheckingMode.SKIP)
	static class Key {

		private final String groupId
		private final String artifactId
		private final String version
		private final String tag
		private final int hash

		Key(String groupId, String artifactId, String version, String tag) {
			this.groupId = groupId
			this.artifactId = artifactId
			this.version = version
			this.tag = tag

			int h = 17
			h = h * 31 + this.groupId.hashCode()
			h = h * 31 + this.artifactId.hashCode()
			h = h * 31 + this.version.hashCode()
			h = h * 31 + this.tag.hashCode()
			hash = h
		}

		@Override
		boolean equals(Object obj) {
			if (this.is(obj)) {
				return true
			}
			if (null == obj || getClass() != obj.getClass()) {
				return false
			}

			return artifactId == obj.artifactId && groupId == obj.groupId &&
				version == obj.version && tag == obj.tag
		}

		@Override
		int hashCode() {
			return hash
		}

	}
}
