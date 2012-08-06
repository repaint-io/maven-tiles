package it.session.maven.plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.merge.ModelMerger;

/**
 * TilesModelMerger by-passes the invocation to ModelMerger.merge() by adding the merge of Plugin configuration.
 */
public class TilesModelMerger extends ModelMerger {

  public void merge( Model target, Model source, boolean sourceDominant, Map<?, ?> hints ) {

    Map<Object, Object> context = new HashMap<Object, Object>();
    if ( hints != null )
    {
      context.putAll( hints );
    }

    super.merge(target, source, sourceDominant, hints);

    if (source.getBuild() != null) {
      super.merge(target, source, sourceDominant,context);
      for(Plugin sourcePlugin : source.getBuild().getPlugins()) {
        Plugin targetPlugin = target.getBuild().getPluginsAsMap().get(sourcePlugin.getKey());
        super.mergePlugin(targetPlugin, sourcePlugin, sourceDominant, context);
        Set<Entry<String, PluginExecution>> entrySet = targetPlugin.getExecutionsAsMap().entrySet();
        for (Entry<String, PluginExecution> entry : entrySet) {
          PluginExecution execution = entry.getValue();
          if (execution.getConfiguration() == null) {
            execution.setConfiguration(sourcePlugin.getConfiguration());
          }
        }
      }
    }
  }
}
