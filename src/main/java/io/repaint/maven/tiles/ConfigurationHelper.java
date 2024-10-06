package io.repaint.maven.tiles;

import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ConfigurationHelper {
  static String safeConfig(Object configurationObject, String child, String defaultValue) {
    if (configurationObject == null) {
      return defaultValue;
    } else {
      Xpp3Dom configuration = (Xpp3Dom) configurationObject;
      Xpp3Dom[] tileChildren = configuration.getChildren(child);
      return tileChildren.length == 1 ? tileChildren[0].getValue() : defaultValue;
    }
  }

  static List<String> safeTiles(Object configurationObject) {
    if (configurationObject == null) {
      return Collections.emptyList();
    } else {
      Xpp3Dom configuration = (Xpp3Dom) configurationObject;
      Xpp3Dom[] tilesChildren = configuration.getChildren("tiles");
      List<String> tiles = new ArrayList<>();
      for (Xpp3Dom tilesChild : tilesChildren) {
        tiles.add(tilesChild.getValue());
      }

      return tiles;
    }
  }
}
