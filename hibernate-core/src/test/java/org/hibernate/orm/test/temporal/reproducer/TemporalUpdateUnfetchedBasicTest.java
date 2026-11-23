package org.hibernate.orm.test.temporal.reproducer;

import jakarta.persistence.Basic;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;

import org.hibernate.Hibernate;
import org.hibernate.SharedSessionContract;
import org.hibernate.annotations.Temporal;
import org.hibernate.temporal.spi.ChangesetIdentifierSupplier;
import org.hibernate.testing.bytecode.enhancement.extension.BytecodeEnhanced;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.Test;

import static org.hibernate.cfg.StateManagementSettings.CHANGESET_ID_SUPPLIER;
import static org.hibernate.cfg.StateManagementSettings.TEMPORAL_TABLE_STRATEGY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@BytecodeEnhanced
@SessionFactory
@DomainModel(annotatedClasses = TemporalUpdateUnfetchedBasicTest.Article.class)
@ServiceRegistry(settings = {
		@Setting(name = TEMPORAL_TABLE_STRATEGY, value = "HISTORY_TABLE"),
		@Setting(name = CHANGESET_ID_SUPPLIER, value = "org.hibernate.orm.test.temporal.reproducer.TemporalUpdateUnfetchedBasicTest$ChangesetIds")
})
class TemporalUpdateUnfetchedBasicTest {

	@Test
	void updateTitleWhileBodyIsUnfetched(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			var article = new Article();
			article.id = 1;
			article.title = "before";
			article.body = "unchanged";
			session.persist( article );
		} );
		scope.inTransaction( session -> {
			final var article = session.find( Article.class, 1 );
			assertFalse( Hibernate.isPropertyInitialized( article, "body" ) );
			article.title = "after";
		} );
		scope.inSession( session -> {
			final var article = session.find( Article.class, 1 );
			assertEquals( "after", article.title );
			assertEquals( "unchanged", article.body );
		} );
	}

	@Entity(name = "Article")
	@Temporal
	static class Article {
		@Id int id;
		String title;
		@Basic(fetch = FetchType.LAZY) String body;
	}

	public static class ChangesetIds implements ChangesetIdentifierSupplier<Integer> {
		private int next;

		@Override
		public Integer generateIdentifier(SharedSessionContract session) {
			return ++next;
		}
	}
}
