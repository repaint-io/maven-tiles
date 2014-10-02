package io.repaint.maven.tiles.isolators

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.apache.maven.MavenExecutionException
import org.apache.maven.execution.MavenSession
import org.apache.maven.model.Model
import org.apache.maven.model.Scm
import org.apache.maven.model.building.FileModelSource
import org.apache.maven.model.building.ModelProblemCollector
import org.apache.maven.model.inheritance.DefaultInheritanceAssembler
import org.apache.maven.model.merge.MavenModelMerger

/**
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 * @author: Mark Derricutt - https://plus.google.com/+MarkDerricutt
 */
class AetherIsolator extends BaseMavenIsolator {

	@Override
	ModelProblemCollector createModelProblemCollector() {
		def collected = []

		return [
			problems: collected,
		  add: { req ->
			  collected.add(req)
		  }
		] as ModelProblemCollector
	}

	@Override
	@CompileStatic(TypeCheckingMode.SKIP)
	def createModelData(Model model, File pomFile) {
		return org.apache.maven.model.building.ModelData.newInstance(new FileModelSource(pomFile), model, model.groupId, model.artifactId, model.version)
	}

	AetherIsolator(MavenSession mavenSession) throws MavenExecutionException {
		super(mavenSession)
	}

	/**
	 * Yes, these mutate state but I need to fail fast if this isn't the right match.
	 *
	 * @param mavenSession - we need to ask Plexus for the range resolver.
	 */
	protected void setupIsolateClasses(MavenSession mavenSession) {
		// lets fail fast
		Class versionRangeResolverClass = Class.forName("org.eclipse.aether.impl.VersionRangeResolver")
		versionRangeResultClass = Class.forName("org.eclipse.aether.resolution.VersionRangeResult")
		versionRangeRequestClass = Class.forName("org.eclipse.aether.resolution.VersionRangeRequest")

		versionRangeResolver = mavenSession.container.lookup(versionRangeResolverClass)
	}

	@Override
	public MavenModelMerger createInheritanceModelMerger() {
		return new FixedInheritanceAssembler();
	}

	/**
	 * Maven 3.2.x added support for automatically appending the artifactId of the current artifact to that of an
	 * inherited SCM section if not present in the current model, however this was written with the assumption of
	 * a single artifact in the inheritance 'lineage', however due to how the tiles-maven-plugin now injects each tile
	 * as a new parent, the lineage now includes multiple artifacts - so the appendPath() calls add all tiles to the
	 * SCM sections developerConnection, connection and url - this appears to only affect the effective-pom however.
	 *
	 * This subclass simply restores the parent versions copies of the methods, if this proves problematic we can remove
	 * it later.
	 */
	public static class FixedInheritanceAssembler extends DefaultInheritanceAssembler.InheritanceModelMerger {
		@Override
		protected void mergeScm_Url(Scm target, Scm source, boolean sourceDominant, Map<Object, Object> context) {
			String src = source.getUrl();
			if ( src != null )
			{
				if ( sourceDominant || target.getUrl() == null )
				{
					target.setUrl( src );
					target.setLocation( "url", source.getLocation( "url" ) );
				}
			}
		}

		@Override
		protected void mergeScm_Connection(Scm target, Scm source, boolean sourceDominant, Map<Object, Object> context) {
			String src = source.getConnection();
			if ( src != null )
			{
				if ( sourceDominant || target.getConnection() == null )
				{
					target.setConnection( src );
					target.setLocation( "connection", source.getLocation( "connection" ) );
				}
			}
		}

		@Override
		protected void mergeScm_DeveloperConnection(Scm target, Scm source, boolean sourceDominant, Map<Object, Object> context) {
			String src = source.getDeveloperConnection();
			if ( src != null )
			{
				if ( sourceDominant || target.getDeveloperConnection() == null )
				{
					target.setDeveloperConnection( src );
					target.setLocation( "developerConnection", source.getLocation( "developerConnection" ) );
				}
			}
		}

		@Override
		protected void mergeScm_Tag( Scm target, Scm source, boolean sourceDominant, Map<Object, Object> context )
		{
			String src = source.getTag();
			if ( src != null )
			{
				if ( sourceDominant || target.getTag() == null )
				{
					target.setTag( src );
					target.setLocation( "tag", source.getLocation( "tag" ) );
				}
			}
		}

	}


}
