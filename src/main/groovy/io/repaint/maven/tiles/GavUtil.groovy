package io.repaint.maven.tiles

import groovy.transform.CompileStatic
import org.apache.maven.artifact.Artifact
import org.apache.maven.model.Model
import org.apache.maven.model.Parent

@CompileStatic
class GavUtil {

	public static String artifactName(Artifact artifact) {
		return String.format("%s:%s", artifact.groupId, artifact.artifactId)
	}

	public static String artifactGav(Artifact artifact) {
		return String.format("%s:%s:%s", artifact.groupId, artifact.artifactId, artifact.versionRange ?: artifact.version)
	}

	public static String modelGav(Model model) {
		return String.format("%s:%s:%s", model.groupId, model.artifactId, model.version)
	}

	public static String parentGav(Parent model) {
		if (!model) {
			return "(no parent)"
		} else {
			return String.format("%s:%s:%s", model.groupId, model.artifactId, model.version)
		}
	}

	public static String getRealGroupId(Model model) {
		return model.groupId ?: model.parent?.groupId
	}

	public static String getRealVersion(Model model) {
		return model.version ?: model.parent?.version
	}
}
