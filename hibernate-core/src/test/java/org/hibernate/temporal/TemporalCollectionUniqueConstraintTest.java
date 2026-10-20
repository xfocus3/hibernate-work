package org.hibernate.temporal;

import java.util.Set;

import org.hibernate.annotations.Temporal;
import org.hibernate.cfg.StateManagementSettings;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.DomainModelScope;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.Setting;

import org.junit.jupiter.api.Test;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.UniqueConstraint;

import static org.assertj.core.api.Assertions.assertThat;

@DomainModel(annotatedClasses = TemporalCollectionUniqueConstraintTest.Owner.class)
@ServiceRegistry(settings = @Setting(name = StateManagementSettings.TEMPORAL_TABLE_STRATEGY, value = "SINGLE_TABLE"))
class TemporalCollectionUniqueConstraintTest {
	@Test
	void namedConstraintKeepsDeclaredColumns(DomainModelScope scope) {
		final var table = table( scope, "namedValues" );
		assertThat( table.getPrimaryKey() ).isNull();
		assertThat( table.getUniqueKeys().get( "uk_temporal_named" ).getColumns() )
				.extracting( Column::getName )
				.containsExactlyInAnyOrder( "owner_id", "entry_value" );
	}

	@Test
	void inferredConstraintIncludesPeriodStart(DomainModelScope scope) {
		final var table = table( scope, "inferredValues" );
		assertThat( table.getPrimaryKey() ).isNull();
		assertThat( table.getUniqueKeys() ).hasSize( 1 );
		assertThat( table.getUniqueKeys().values().iterator().next().getColumns() )
				.extracting( Column::getName )
				.containsExactlyInAnyOrder( "owner_id", "entry_value", "effective" );
	}

	private static Table table(DomainModelScope scope, String property) {
		return scope.getDomainModel().getCollectionBinding( Owner.class.getName() + "." + property )
				.getCollectionTable();
	}

	@Entity(name = "TemporalUniqueOwner")
	static class Owner {
		@Id
		Long id;

		@Temporal
		@ElementCollection
		@CollectionTable(name = "temporal_named", joinColumns = @JoinColumn(name = "owner_id"),
				uniqueConstraints = @UniqueConstraint(name = "uk_temporal_named", columnNames = {"owner_id", "entry_value"}))
		@jakarta.persistence.Column(name = "entry_value")
		Set<String> namedValues;

		@Temporal
		@ElementCollection
		@CollectionTable(name = "temporal_inferred", joinColumns = @JoinColumn(name = "owner_id"))
		@jakarta.persistence.Column(name = "entry_value")
		Set<String> inferredValues;
	}
}
