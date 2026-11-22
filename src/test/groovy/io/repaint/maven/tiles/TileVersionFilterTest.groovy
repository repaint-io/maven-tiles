package io.repaint.maven.tiles

import org.junit.Test

import javax.inject.Inject
import java.lang.reflect.AnnotatedElement

/**
 * Regression test for https://github.com/repaint-io/maven-tiles/issues/219
 */
class TileVersionFilterTest {

	/**
	 * TileVersionFilter is a singleton injected into the lifecycle participant, which tools such as
	 * IntelliJ IDEA instantiate outside a Maven session scope. It must not inject anything (in particular
	 * the session-scoped RepositorySystemSession), otherwise provisioning fails with an IllegalStateException.
	 */
	@Test
	void hasNoInjectionPoints() {
		List<AnnotatedElement> members = []
		members.addAll(TileVersionFilter.declaredFields)
		members.addAll(TileVersionFilter.declaredConstructors)
		members.addAll(TileVersionFilter.declaredMethods)
		def injected = members.findAll { it.isAnnotationPresent(Inject) || it.isAnnotationPresent(com.google.inject.Inject) }
		assert injected.isEmpty()
	}

	@Test
	void notApplicableWithoutRepositorySession() {
		assert !new TileVersionFilter().isApplicable(null)
	}
}
