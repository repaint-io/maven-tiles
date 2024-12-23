package io.repaint.maven.tiles;

import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.eclipse.aether.artifact.Artifact;

public class GavUtil {
  public static String artifactName(Artifact artifact) {
    return String.format("%s:%s", artifact.getGroupId(), artifact.getArtifactId());
  }

  public static String artifactName(org.apache.maven.artifact.Artifact artifact) {
    return String.format("%s:%s", artifact.getGroupId(), artifact.getArtifactId());
  }

  public static String artifactGav(Artifact artifact) {
    return String.format("%s:%s:%s", artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
  }

  public static String artifactGav(org.apache.maven.artifact.Artifact artifact) {
    return String.format("%s:%s:%s", artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
  }

  public static String modelGav(Model model) {
    return String.format("%s:%s:%s", model.getGroupId(), model.getArtifactId(), model.getVersion());
  }

  public static String modelGa(Model model) {
    return String.format("%s:%s", model.getGroupId(), model.getArtifactId());
  }

  public static String modelRealGa(Model model) {
    return String.format("%s:%s", getRealGroupId(model), model.getArtifactId());
  }

  public static String parentGav(Parent model) {
    if (model == null) {
      return "(no parent)";
    } else {
      return String.format("%s:%s:%s", model.getGroupId(), model.getArtifactId(), model.getVersion());
    }
  }

  public static String getRealGroupId(Model model) {
    return model.getGroupId() != null ? model.getGroupId() : model.getParent() != null ? model.getParent().getGroupId() : null;
  }

  public static String getRealVersion(Model model) {
    return model.getVersion() != null ? model.getVersion() : model.getParent() != null ? model.getParent().getVersion() : null;
  }
}