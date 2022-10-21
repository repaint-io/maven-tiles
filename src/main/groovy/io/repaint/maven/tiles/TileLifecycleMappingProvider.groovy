package io.repaint.maven.tiles

import org.apache.maven.lifecycle.mapping.DefaultLifecycleMapping
import org.apache.maven.lifecycle.mapping.Lifecycle
import org.apache.maven.lifecycle.mapping.LifecycleMapping
import org.apache.maven.lifecycle.mapping.LifecyclePhase

import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
@Named("tile")
final class TileLifecycleMappingProvider
		implements Provider<LifecycleMapping>
{
	private final LifecycleMapping lifecycleMapping;

	@Inject
	TileLifecycleMappingProvider()
	{
		Lifecycle lifecycle = new Lifecycle(
                id: "default",
                lifecyclePhases: [
                        "package" : new LifecyclePhase("io.repaint.maven:tiles-maven-plugin:attach-tile"),
                        "install" : new LifecyclePhase( "org.apache.maven.plugins:maven-install-plugin:install"),
                        "deploy" : new LifecyclePhase( "org.apache.maven.plugins:maven-deploy-plugin:deploy")
                ]
		)
		lifecycleMapping = new DefaultLifecycleMapping()
		lifecycleMapping.lifecycles = Collections.singletonList( lifecycle ) // this hack is sadly must, or reimplement LifecycleMapping class
	}

	@Override
	LifecycleMapping get()
	{
		return lifecycleMapping
	}
}