package io.repaint.maven.tiles

import org.apache.maven.artifact.handler.ArtifactHandler
import org.apache.maven.artifact.handler.DefaultArtifactHandler

import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
@Named("tile")
final class TileArtifactHandlerProvider
		implements Provider<ArtifactHandler>
{
	private final DefaultArtifactHandler artifactHandler;

	@Inject
	TileArtifactHandlerProvider()
	{
		this.artifactHandler = new DefaultArtifactHandler("tile")
		this.artifactHandler.extension = "xml"
		this.artifactHandler.packaging = "tile" // Groovy magic again
		this.artifactHandler.language = "xml"
		this.artifactHandler.addedToClasspath = false
	}

	@Override
	ArtifactHandler get()
	{
		return artifactHandler;
	}
}