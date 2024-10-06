package io.repaint.maven.tiles;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.repaint.maven.tiles.AbstractTileMojo.nullIf;

import static java.util.stream.Collectors.toList;

/**
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 * @author: Mark Derricutt - https://plus.google.com/+MarkDerricutt
 */
public class TileValidator {
  public static final String SMELL_DEPENDENCYMANAGEMENT = "dependencymanagement";
  public static final String SMELL_DEPENDENCIES = "dependencies";
  public static final String SMELL_REPOSITORIES = "repositories";
  public static final String SMELL_PLUGINREPOSITORIES = "pluginrepositories";
  public static final String SMELL_PLUGINMANAGEMENT = "pluginmanagement";

  public static final List<String> SMELLS = Arrays.asList(
      SMELL_DEPENDENCIES, SMELL_DEPENDENCYMANAGEMENT, SMELL_PLUGINREPOSITORIES, SMELL_PLUGINMANAGEMENT, SMELL_REPOSITORIES);

  public Model loadModel(Logger log, File tilePom, String buildSmells) throws MavenExecutionException {
    TileModel modelLoader = new TileModel();
    Model validatedModel = null;

    Set<String> collectedBuildSmells = new HashSet<>();
    if (buildSmells != null) {
      List<String> smells = Arrays.stream(buildSmells.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(toList());

      // this is Mark's fault.
      Collection<String> okSmells = smells.stream().filter(TileValidator.SMELLS::contains).collect(toList());

      Collection<String> stinkySmells = smells.stream().filter(s -> !TileValidator.SMELLS.contains(s)).collect(toList());

      if (!stinkySmells.isEmpty()) {
        String smellyMessage = "Discovered bad smell configuration ${stinkySmells} from <buildSmells>${buildSmells}</buildSmells>.";
        throw new MavenExecutionException(smellyMessage, tilePom);
      }

      collectedBuildSmells.addAll(okSmells);
    }

    if (tilePom == null) {
      log.error("No tile exists");
    } else if (!tilePom.exists()) {
      log.error("Unable to file tile ${tilePom.getAbsolutePath()}");
    } else {
      modelLoader.loadTile(tilePom);
      validatedModel = validateModel(modelLoader.getModel(), log, collectedBuildSmells);
      if (validatedModel != null) {
        log.info("Tile passes basic validation.");
      }
    }

    return validatedModel;
  }

  /**
   * Display all the errors.
   *
   * Should we allow name? description? modelVersion?
   */
  protected Model validateModel(Model model, Logger log, Set<String> buildSmells) {
    Model validModel = model;

    if (model.getGroupId() != null) {
      log.error("Tile has a groupId and must not have");
      validModel = null;
    }

    if (model.getArtifactId() != null) {
      log.error("Tile has an artifactId and must not have");
      validModel = null;
    }

    if (model.getVersion() != null) {
      log.error("Tile has a version and must not have");
      validModel = null;
    }

    if (model.getParent() != null) {
      log.error("Tile has a parent and must not have");
      validModel = null;
    }

    if (!model.getRepositories().isEmpty() && !buildSmells.contains(SMELL_REPOSITORIES)) {
      log.error("Tile follows bad practice and has repositories section. Please use settings.xml.");
      validModel = null;
    }

    if (!model.getPluginRepositories().isEmpty() && !buildSmells.contains(SMELL_PLUGINREPOSITORIES)) {
      log.error("Tile follows bad practice and has pluginRepositories section. Please use settings.xml.");
      validModel = null;
    }

    if (model.getDependencyManagement() != null && !buildSmells.contains(SMELL_DEPENDENCYMANAGEMENT)) {
      log.error("Tile follows bad practice and has dependencyManagement. Please use composites.");
      validModel = null;
    }

    if (model.getBuild() != null && model.getBuild().getPluginManagement() != null &&
        !buildSmells.contains(SMELL_PLUGINMANAGEMENT)) {
      log.error("Plugin management is usually not required, if you want a plugin to always run, use plugins instead.");
      validModel = null;
    }

    if (!model.getDependencies().isEmpty() && !buildSmells.contains(SMELL_DEPENDENCIES)) {
      log.error("Tile includes dependencies - this will prevent consumers from adding exclusions, use composites instead.");
      validModel = null;
    }

    if (model.getBuild() != null && !nullIf(() -> model.getBuild().getExtensions().isEmpty())) {
      log.error("Tile has extensions and must not have");
      validModel = null;
    }

    if (model.getBuild() != null && model.getBuild().getPlugins() != null && !model.getBuild().getPlugins().isEmpty()) {
      for (Plugin plugin : model.getBuild().getPlugins()) {
        if (plugin.getExtensions() != null && !plugin.getExtensions().isEmpty()) {
          log.error("Tile has plugins with extensions and must not have");
          validModel = null;
          break;
        }
      }
    }

    return validModel;
  }
}
