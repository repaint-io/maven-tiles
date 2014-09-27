package io.repaint.maven.tiles

import org.apache.maven.execution.MavenSession
import org.apache.maven.model.building.ModelCache

/**
 * Because the Default one is package private *sigh*
 *
 * We have the org.aether / org.sonatype issue
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 */
class NotDefaultModelCache implements ModelCache {
	def session
	def cache

	public NotDefaultModelCache(MavenSession mavenSession) {
		this.session = mavenSession.repositorySession
		this.cache = mavenSession.repositorySession.cache
	}

	public Object get( String groupId, String artifactId, String version, String tag )
	{
		return cache.get( session, new Key( groupId, artifactId, version, tag ) );
	}

	public void put( String groupId, String artifactId, String version, String tag, Object data )
	{
		cache.put( session, new Key( groupId, artifactId, version, tag ), data );
	}

	static class Key
	{

		private final String groupId;

		private final String artifactId;

		private final String version;

		private final String tag;

		private final int hash;

		public Key( String groupId, String artifactId, String version, String tag )
		{
			this.groupId = groupId;
			this.artifactId = artifactId;
			this.version = version;
			this.tag = tag;

			int h = 17;
			h = h * 31 + this.groupId.hashCode();
			h = h * 31 + this.artifactId.hashCode();
			h = h * 31 + this.version.hashCode();
			h = h * 31 + this.tag.hashCode();
			hash = h;
		}

		@Override
		public boolean equals( Object obj )
		{
			if ( this.is(obj) )
			{
				return true;
			}
			if ( null == obj || !getClass().equals( obj.getClass() ) )
			{
				return false;
			}

			Key that = (Key) obj;
			return artifactId.equals( that.artifactId ) && groupId.equals( that.groupId ) &&
				version.equals( that.version ) && tag.equals( that.tag );
		}

		@Override
		public int hashCode()
		{
			return hash;
		}

	}
}
