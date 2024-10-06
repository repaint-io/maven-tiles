package io.repaint.maven.tiles;

import org.apache.maven.lifecycle.mapping.DefaultLifecycleMapping;
import org.apache.maven.lifecycle.mapping.Lifecycle;
import org.apache.maven.lifecycle.mapping.LifecycleMapping;
import org.apache.maven.lifecycle.mapping.LifecyclePhase;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
@Named("tile")
public final class TileLifecycleMappingProvider implements Provider<LifecycleMapping> {
  private final LifecycleMapping lifecycleMapping;

  @Inject
  TileLifecycleMappingProvider() {
    Map<String, LifecyclePhase> phases = new HashMap<>();
    phases.put("package", new LifecyclePhase("io.repaint.maven:tiles-maven-plugin:attach-tile"));
    phases.put("install", new LifecyclePhase("org.apache.maven.plugins:maven-install-plugin:install"));
    phases.put("deploy", new LifecyclePhase("org.apache.maven.plugins:maven-deploy-plugin:deploy"));

    Lifecycle lifecycle = new Lifecycle();
    lifecycle.setId("default");
    lifecycle.setLifecyclePhases(phases);

    lifecycleMapping = new DefaultLifecycleMapping(Collections.singletonList(lifecycle));
  }

  @Override
  public LifecycleMapping get() {
    return lifecycleMapping;
  }
}