package io.repaint.maven.tiles;

import org.apache.maven.building.Source;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.building.ModelCache;
import org.eclipse.aether.RepositoryCache;
import org.eclipse.aether.RepositorySystemSession;

import java.util.function.Supplier;

/**
 * Because the Default one is package private *sigh*
 *
 * We have the org.aether / org.sonatype issue
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 */
public class NotDefaultModelCache implements ModelCache {
  RepositorySystemSession session;
  RepositoryCache cache;

  public NotDefaultModelCache(MavenSession mavenSession) {
    this.session = mavenSession.getRepositorySession();
    this.cache = mavenSession.getRepositorySession().getCache();
  }

  public Object get(String groupId, String artifactId, String version, String tag) {
    return cache.get(session, new Key(groupId, artifactId, version, tag));
  }

  public void put(String groupId, String artifactId, String version, String tag, Object data) {
    cache.put(session, new Key(groupId, artifactId, version, tag), data);
  }

  public static class Key {
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String tag;
    private final int hash;

    Key(String groupId, String artifactId, String version, String tag) {
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
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (null == obj || getClass() != obj.getClass()) {
        return false;
      }

      Key other = (Key) obj;
      return artifactId.equals(other.artifactId) && groupId.equals(other.groupId) && version.equals(other.version) &&
          tag.equals(other.tag);
    }

    @Override
    public int hashCode() {
      return hash;
    }
  }
}
