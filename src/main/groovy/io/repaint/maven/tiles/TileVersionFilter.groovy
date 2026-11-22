package io.repaint.maven.tiles

import org.apache.maven.MavenExecutionException
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.collection.VersionFilter
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.internal.impl.collect.DefaultVersionFilterContext
import org.eclipse.aether.resolution.VersionRangeRequest
import org.eclipse.aether.resolution.VersionRangeResult
import org.eclipse.aether.util.version.GenericVersionScheme
import org.eclipse.aether.version.InvalidVersionSpecificationException
import org.eclipse.aether.version.Version

import javax.inject.Named
import javax.inject.Singleton

@Singleton
@Named("tileVersionFilter")
class TileVersionFilter {

    static final INITIALIZE_METHOD_NAME = "initialize"

    static final DEFAULT_SCOPE = "scope"

    GenericVersionScheme versionScheme = new GenericVersionScheme();

    /**
     * The repository session is passed in rather than injected: this component is a singleton created
     * when the extension is loaded, which may happen outside a Maven session scope (e.g. IntelliJ IDEA
     * instantiating lifecycle participants during import), where injecting the session-scoped
     * RepositorySystemSession throws.
     */
    boolean isApplicable(RepositorySystemSession repositorySystemSession) {
        return repositorySystemSession != null &&
                DefaultVersionFilterContext.metaClass.getMetaMethod(INITIALIZE_METHOD_NAME,
                        new Object[]{Dependency.class, VersionRangeResult.class})

    }

    /**
     * Applies the resolver version filter expression (e.g. "h", "l", "e([1,2))", "nosnapshot") to the
     * resolved range. The filter is built per call as different projects in the reactor may configure
     * different expressions.
     *
     * @return the highest version accepted by the filter, or null if the expression contains no filters
     */
    String latestFilteredVersion(String filter, VersionRangeRequest request, VersionRangeResult result,
                                 RepositorySystemSession repositorySystemSession)
            throws MavenExecutionException {
        def artifact = request.getArtifact()
        def gav = "${artifact.groupId}:${artifact.artifactId}:${artifact.version}"

        Optional<VersionFilter> versionFilter
        try {
            def versionFilterBuilder = getClass().getClassLoader()
                    .loadClass("org.eclipse.aether.internal.impl.collect.DefaultVersionFilterBuilder")
                    .getConstructor().newInstance()
            versionFilter = versionFilterBuilder
                    .buildVersionFilter(filter, { spec -> versionScheme.parseVersionConstraint(spec) })
        } catch (IllegalArgumentException | InvalidVersionSpecificationException e) {
            throw new MavenExecutionException("Invalid tileVersionFilter '${filter}' for tile ${gav}: ${e.message}", e)
        }

        if (!versionFilter.isPresent()) {
            return null
        }

        Dependency dependency = new Dependency(artifact, DEFAULT_SCOPE)

        def context = new DefaultVersionFilterContext(repositorySystemSession)
                .initialize(dependency, result)

        versionFilter.get().filterVersions(context)

        def versions = context.get()
        if (versions.isEmpty()) {
            throw new MavenExecutionException("tileVersionFilter '${filter}' excluded all versions of tile ${gav} " +
                    "(available: ${result.versions.join(', ')})", (Throwable) null)
        }

        Version version = versions.get(versions.size() - 1)
        return version.toString()
    }

}
