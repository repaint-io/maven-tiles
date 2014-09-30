package io.repaint.maven.tiles

import org.apache.maven.model.Scm
import org.apache.maven.model.inheritance.DefaultInheritanceAssembler

/**
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 */
class ThunkedInheritanceAssembler extends DefaultInheritanceAssembler {
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
