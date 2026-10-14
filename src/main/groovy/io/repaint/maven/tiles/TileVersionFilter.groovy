package io.repaint.maven.tiles

import com.google.inject.internal.Nullable
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.collection.VersionFilter
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.internal.impl.collect.DefaultVersionFilterContext
import org.eclipse.aether.resolution.VersionRangeRequest
import org.eclipse.aether.resolution.VersionRangeResult
import org.eclipse.aether.util.version.GenericVersionScheme
import org.eclipse.aether.version.Version

import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
@Named("tileVersionFilter")
class TileVersionFilter {

    static final INITIALIZE_METHOD_NAME = "initialize"

    static final DEFAULT_SCOPE = "scope"

    @Inject
    @Nullable
    RepositorySystemSession repositorySystemSession

    GenericVersionScheme versionScheme = new GenericVersionScheme();

    VersionFilter versionFilter

    boolean isApplicable() {
        return repositorySystemSession != null &&
                DefaultVersionFilterContext.metaClass.getMetaMethod(INITIALIZE_METHOD_NAME,
                        new Object[]{Dependency.class, VersionRangeResult.class})

    }

    boolean init(String filter) {
        if (!versionFilter) {
            def versionFilterBuilder = getClass().getClassLoader()
                    .loadClass("org.eclipse.aether.internal.impl.collect.DefaultVersionFilterBuilder")
                    .getConstructor().newInstance()
            versionFilter = versionFilterBuilder
                    .buildVersionFilter(filter, { spec -> versionScheme.parseVersionConstraint(spec) })
                    .get()
        }
    }

    String latestFilteredVersion(VersionRangeRequest request, VersionRangeResult result) {
        Dependency dependency = new Dependency(request.getArtifact(), DEFAULT_SCOPE)

        def context = new DefaultVersionFilterContext(repositorySystemSession)
                .initialize(dependency, result)

        versionFilter.filterVersions(context)

        def versions = context.get()
        if (versions.size() > 0) {
            Version version = versions.get(versions.size() - 1)
            return version.toString()
        } else {
            return null
        }
    }

}
