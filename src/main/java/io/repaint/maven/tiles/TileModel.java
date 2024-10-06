package io.repaint.maven.tiles;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import nu.xom.*;

public class TileModel {
  private Model model;
  private List<String> tiles = new ArrayList<>();
  private File tilePom;

  public Reader strippedPom() {
    Document document;
    try {
      document = new Builder().build(tilePom);
      XPathContext context = new XPathContext("mvn", "http://maven.apache.org/POM/4.0.0");

      for (Node node : document.query("//mvn:tile", context)) {
        tiles.add(node.getValue());
      }
      // TODO This _should_ be doable using //*[local-name()='tile']
      for (Node node : document.query("//tile", context)) {
        tiles.add(node.getValue());
      }

      for (Node node : document.query("//mvn:tiles", context)) {
        node.getParent().removeChild(node);
      }
      for (Node node : document.query("//tiles", context)) {
        node.getParent().removeChild(node);
      }

      return new StringReader(document.toXML());

    } catch (ParsingException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public void loadTile(File tilePom) {
    this.tilePom = tilePom;

    MavenXpp3Reader pomReader = new MavenXpp3Reader();

    try {
      model = pomReader.read(strippedPom());
    } catch (XmlPullParserException e) {
      throw new TileExecutionException(e);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public TileModel() {
  }

  public TileModel(File tilePom, Artifact artifact) {
    loadTile(tilePom);

    model.setModelVersion("4.0.0");
    model.setVersion(artifact.getVersion());
    model.setGroupId(artifact.getGroupId());
    model.setArtifactId(artifact.getArtifactId());
    model.setPackaging("pom");

    if (model.getBuild() != null && model.getBuild().getPlugins() != null) {
      rewritePluginExecutionIds(model.getBuild().getPlugins(), artifact);
    }
    if (model.getProfiles() != null) {
      for (org.apache.maven.model.Profile profile : model.getProfiles()) {
        if (profile.getBuild() != null && profile.getBuild().getPlugins() != null) {
          rewritePluginExecutionIds(profile.getBuild().getPlugins(), artifact);
        }
      }
    }
  }

  public Model getModel() {
    return model;
  }

  public File getTilePom() {
    return tilePom;
  }

  public List<String> getTiles() {
    return tiles;
  }

  private static void rewritePluginExecutionIds(List<Plugin> plugins, Artifact artifact) {
    for (Plugin plugin : plugins) {
      if (plugin.getExecutions() != null) {
        for (PluginExecution execution : plugin.getExecutions()) {
          Xpp3Dom configuration = (Xpp3Dom) execution.getConfiguration();

          if (configuration != null) {
            Xpp3Dom[] tileChildren = configuration.getChildren("tiles-keep-id");
            String keepTile = tileChildren.length == 1 ? tileChildren[0].getValue() : "false";
            String keepAttribute = configuration.getAttribute("tiles-keep-id");

            if (("true".equals(keepAttribute) || "true".equals(keepTile))) {
              continue;
            }
          }
          execution.setId(GavUtil.artifactGav(artifact).replaceAll(":", "_") + "__" + execution.getId());
        }
      }
    }
  }
}