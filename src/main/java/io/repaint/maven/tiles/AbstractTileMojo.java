package io.repaint.maven.tiles;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.maven.shared.filtering.MavenResourcesFiltering;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.function.Supplier;

public abstract class AbstractTileMojo extends AbstractMojo {
  @Parameter(property = "project", readonly = true, required = true)
  protected MavenProject project;

  @Parameter(property = "tiles", readonly = false, required = false)
  protected List<String> tiles;

  @Parameter(property = "applyBefore", readonly = false, required = false)
  protected String applyBefore;

  @Parameter(property = "buildSmells", readonly = false, required = false)
  protected String buildSmells;

  @Parameter(property = "filtering", readonly = false, required = false, defaultValue = "false")
  protected boolean filtering;

  @Parameter(required = true, defaultValue = "${project.build.directory}/generated-sources")
  protected File generatedSourcesDirectory;

  @Parameter(defaultValue = "${session}", readonly = true)
  protected MavenSession mavenSession;

  @Component
  protected MavenFileFilter mavenFileFilter;

  @Component
  protected MavenResourcesFiltering mavenResourcesFiltering;

  protected Logger logger = LoggerFactory.getLogger(getClass());

  protected File getTile() throws MavenFilteringException {
    return FilteringHelper.getTile(
        project, filtering, generatedSourcesDirectory, mavenSession, mavenFileFilter, mavenResourcesFiltering);
  }

  public static <T> T nullIf(Supplier<T> supp) {
    try {
      return supp.get();
    } catch (NullPointerException e) {
      return null;
    }
  }
}