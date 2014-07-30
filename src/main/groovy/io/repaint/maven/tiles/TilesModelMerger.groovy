package io.repaint.maven.tiles

import groovy.transform.CompileStatic
import org.apache.maven.model.*
import org.apache.maven.model.merge.ModelMerger


/**
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 */
@CompileStatic
public class TilesModelMerger extends ModelMerger {

	@Override
	protected void mergePluginContainer_Plugins(PluginContainer target, PluginContainer source,
	                                            boolean sourceDominant, Map<Object, Object> context) {
		List<Plugin> sourcePlugins = source.plugins

		if (!sourcePlugins.empty) {
			List<Plugin> targetPlugins = target.plugins

			Map<Plugin, Plugin> merged = new LinkedHashMap<Plugin, Plugin>((sourcePlugins.size() + targetPlugins.size()) * 2)

			for (Plugin element : targetPlugins) {
				merged[element] = element
			}

			for (Plugin sourcePlugin : sourcePlugins) {
				Plugin targetPlugin = merged.get(sourcePlugin) // its using the name only

				if (targetPlugin == null) {
					merged[sourcePlugin] = sourcePlugin
				} else if (sourceDominant) {
					mergePlugin(targetPlugin, sourcePlugin, sourceDominant, context)
				}
			}

			target.plugins = new ArrayList<Plugin>(merged.values())
		}
	}

	@Override
	protected void mergePlugin(Plugin targetPlugin, Plugin sourcePlugin, boolean sourceDominant, Map<Object, Object> context) {
		super.mergePlugin(targetPlugin, sourcePlugin, sourceDominant, context)

		if (sourcePlugin.isExtensions()) {
			targetPlugin.extensions = true
		}
	}

	@Override
	public void mergeModelBase_Properties(ModelBase target, ModelBase source, boolean sourceDominant, Map<Object, Object> context) {
		// never merge properties
	}

	/** from MavenModelMerger */

	@Override
	protected Object getDependencyKey(Dependency dependency) {
		return dependency.managementKey
	}

	@Override
	protected Object getPluginKey(Plugin plugin) {
		return plugin.key
	}

	@Override
	protected Object getPluginExecutionKey(PluginExecution pluginExecution) {
		return pluginExecution.id
	}

	@Override
	protected Object getReportPluginKey(ReportPlugin reportPlugin) {
		return reportPlugin.key
	}

	@Override
	protected Object getReportSetKey(ReportSet reportSet) {
		return reportSet.id
	}

	@Override
	protected Object getRepositoryBaseKey(RepositoryBase repositoryBase) {
		return repositoryBase.id
	}

	@Override
	protected Object getExtensionKey(Extension extension) {
		return extension.groupId + ':' + extension.artifactId
	}

	@Override
	protected Object getExclusionKey(Exclusion exclusion) {
		return exclusion.groupId + ':' + exclusion.artifactId
	}
}
