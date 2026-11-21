package org.hibernate.temporal;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import org.hibernate.SharedSessionContract;
import org.hibernate.annotations.Temporal;
import org.hibernate.temporal.spi.ChangesetIdentifierSupplier;
import org.hibernate.testing.orm.junit.DialectFeatureChecks;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.RequiresDialectFeature;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.Setting;

import org.junit.jupiter.api.Test;

import static org.hibernate.cfg.StateManagementSettings.CHANGESET_ID_SUPPLIER;
import static org.hibernate.cfg.StateManagementSettings.TEMPORAL_TABLE_STRATEGY;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TemporalAssignedIdTest {
	@Test
	@RequiresDialectFeature(feature = DialectFeatureChecks.SupportsPrimaryKeyUpdate.class)
	@SessionFactory
	@DomainModel(annotatedClasses = Item.class)
	@ServiceRegistry(settings = {
			@Setting(name = TEMPORAL_TABLE_STRATEGY, value = "SINGLE_TABLE"),
			@Setting(name = CHANGESET_ID_SUPPLIER, value = "org.hibernate.temporal.TemporalAssignedIdTest$Changesets")
	})
	void updatePreservesAssignedIdAndHistoricalState(SessionFactoryScope scope) {
		final long id = 1L;
		scope.inTransaction( session -> {
			final var item = new Item();
			item.id = id;
			item.name = "a";
			session.persist( item );
		} );

		scope.inTransaction( session -> session.find( Item.class, id ).name = "b" );
		scope.inSession( session -> assertEquals( "b", session.find( Item.class, id ).name ) );
		try ( var session = scope.getSessionFactory().withOptions().atChangeset( 1 ).open() ) {
			session.inTransaction( tx -> assertEquals( "a", session.find( Item.class, id ).name ) );
		}
	}

	public static class Changesets implements ChangesetIdentifierSupplier<Integer> {
		private int next;

		@Override
		public Integer generateIdentifier(SharedSessionContract session) {
			return ++next;
		}
	}

	@Entity(name = "AssignedItem")
	@Temporal
	static class Item {
		@Id
		Long id;
		String name;
	}
}
