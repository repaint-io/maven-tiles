package io.repaint.maven.tiles

import com.google.inject.Singleton
import groovy.transform.CompileStatic
import org.apache.maven.artifact.handler.ArtifactHandler
import org.apache.maven.artifact.handler.DefaultArtifactHandler

import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider

@CompileStatic
@Singleton
@Named("tile")
final class TileArtifactHandlerProvider
		implements Provider<ArtifactHandler>
{
	private final ArtifactHandler artifactHandler;

	@Inject
	TileArtifactHandlerProvider()
	{
		this.artifactHandler = new DefaultArtifactHandler(
				type: "tile",
				extension: "xml",
				packaging: "tile",
				language: "xml",
				addedToClasspath: false
		);
	}

	@Override
	ArtifactHandler get()
	{
		return artifactHandler;
	}
}