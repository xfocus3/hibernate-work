package org.hibernate.orm.test.constraint;

import java.util.Collection;

import org.hibernate.mapping.ForeignKey;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.JiraKey;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

import static jakarta.persistence.ConstraintMode.NO_CONSTRAINT;
import static jakarta.persistence.FetchType.LAZY;
import static org.assertj.core.api.Assertions.assertThat;

@JiraKey("HHH-20763")
@DomainModel(annotatedClasses = {
		PrimaryKeyJoinColumnForeignKeyTest.Target.class,
		PrimaryKeyJoinColumnForeignKeyTest.NoConstraint.class,
		PrimaryKeyJoinColumnForeignKeyTest.DefaultConstraint.class
})
@SessionFactory
class PrimaryKeyJoinColumnForeignKeyTest {
	@BeforeAll
	void exportSchema(SessionFactoryScope scope) {
		scope.getSessionFactory();
	}

	@Test
	void testNoConstraint(SessionFactoryScope scope) {
		assertThat( foreignKeys( scope, NoConstraint.class ) ).isEmpty();
	}

	@Test
	void testDefaultConstraint(SessionFactoryScope scope) {
		assertThat( foreignKeys( scope, DefaultConstraint.class ) ).hasSize( 1 );
	}

	private Collection<ForeignKey> foreignKeys(SessionFactoryScope scope, Class<?> entityClass) {
		return scope.getMetadataImplementor().getEntityBinding( entityClass.getName() )
				.getTable().getForeignKeyCollection();
	}

	@Entity(name = "PrimaryKeyForeignKeyTarget")
	@Table(name = "pk_fk_target")
	static class Target {
		@Id
		Integer id;
	}

	@Entity(name = "PrimaryKeyNoConstraint")
	@Table(name = "pk_fk_none")
	static class NoConstraint {
		@Id
		Integer id;

		@OneToOne(fetch = LAZY, optional = false)
		@PrimaryKeyJoinColumn(foreignKey = @jakarta.persistence.ForeignKey(NO_CONSTRAINT))
		Target target;
	}

	@Entity(name = "PrimaryKeyDefaultConstraint")
	@Table(name = "pk_fk_default")
	static class DefaultConstraint {
		@Id
		Integer id;

		@OneToOne(fetch = LAZY, optional = false)
		@PrimaryKeyJoinColumn
		Target target;
	}
}
