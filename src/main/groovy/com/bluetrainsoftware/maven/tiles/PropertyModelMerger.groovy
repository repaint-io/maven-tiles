package com.bluetrainsoftware.maven.tiles

import groovy.transform.CompileStatic
import org.apache.maven.model.ModelBase
import org.apache.maven.model.merge.ModelMerger


/**
 * We only wish to merge properties
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 */
@CompileStatic
public class PropertyModelMerger extends ModelMerger {
	@Override
	public void mergeModelBase_Properties(ModelBase target, ModelBase source, boolean sourceDominant, Map<Object, Object> context) {
		super.mergeModelBase_Properties(target, source, sourceDominant, context)
	}
}
