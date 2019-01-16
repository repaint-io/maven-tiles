package io.repaint.maven.tiles


import org.apache.maven.MavenExecutionException
import org.apache.maven.artifact.Artifact
import org.apache.maven.model.Dependency
import org.apache.maven.model.Plugin
import org.apache.maven.model.building.ModelSource
import org.apache.maven.project.DefaultProjectBuilder
import org.apache.maven.project.MavenProject
import org.apache.maven.project.ProjectBuilder
import org.apache.maven.project.ProjectBuildingException
import org.apache.maven.project.ProjectBuildingRequest
import org.apache.maven.project.ProjectBuildingResult
import org.codehaus.plexus.component.annotations.Component

import static io.repaint.maven.tiles.Constants.TILEPLUGIN_ARTIFACT
import static io.repaint.maven.tiles.Constants.TILEPLUGIN_GROUP

@Component(role = ProjectBuilder.class, hint = "TilesProjectBuilder")
class TilesProjectBuilder extends DefaultProjectBuilder {

	@Override
	ProjectBuildingResult build(File pomFile, ProjectBuildingRequest request) throws ProjectBuildingException {
		return injectTileDependecies(super.build(pomFile, request))
	}

	@Override
	ProjectBuildingResult build(ModelSource modelSource, ProjectBuildingRequest request) throws ProjectBuildingException {
		return injectTileDependecies(super.build(modelSource, request))
	}

	@Override
	ProjectBuildingResult build(Artifact artifact, ProjectBuildingRequest request) throws ProjectBuildingException {
		return injectTileDependecies(super.build(artifact, request))
	}

	@Override
	ProjectBuildingResult build(Artifact artifact, boolean allowStubModel, ProjectBuildingRequest request) throws ProjectBuildingException {
		return injectTileDependecies(super.build(artifact, allowStubModel, request))
	}

	@Override
	List<ProjectBuildingResult> build(List<File> pomFiles, boolean recursive, ProjectBuildingRequest request) throws ProjectBuildingException {
		return injectTileDependecies(super.build(pomFiles, recursive, request))
	}

    private static ProjectBuildingResult injectTileDependecies(ProjectBuildingResult result) {
        MavenProject project = result.project
        def configuration = project.build.plugins
            ?.find({ Plugin plugin -> plugin.groupId == TILEPLUGIN_GROUP && plugin.artifactId == TILEPLUGIN_ARTIFACT})
            ?.configuration

		if (configuration) {
			configuration.getChild("tiles")?.children?.each { tile ->
				String[] gav = tile.value.tokenize(":")

				if (gav.size() != 3 && gav.size() != 5) {
					throw new MavenExecutionException("${tile.value} does not have the form group:artifact:version-range or group:artifact:extension:classifier:version-range", project.file)
				}

				Dependency dependency = new Dependency()
				dependency.groupId = gav[0]
				dependency.artifactId = gav[1]
				dependency.scope = "provided"
				dependency.optional = "true"
				if (gav.size() == 3) {
					dependency.type = "xml"
					dependency.version = gav[2]
				} else {
					dependency.type = gav[2]
					dependency.classifier = gav[3]
					dependency.version = gav[4]
				}
				project.dependencies.add(dependency)
			}
		}
		return result
	}

    private static List<ProjectBuildingResult> injectTileDependecies(List<ProjectBuildingResult> list) {
        for (ProjectBuildingResult result : list) {
            injectTileDependecies(result)
        }
        return list
    }

}
