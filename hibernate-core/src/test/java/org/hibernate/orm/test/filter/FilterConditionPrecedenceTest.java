package org.hibernate.orm.test.filter;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.JiraKey;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DomainModel(annotatedClasses = FilterConditionPrecedenceTest.Item.class)
@SessionFactory
@JiraKey("HHH-21130")
class FilterConditionPrecedenceTest {
	@BeforeEach
	void prepare(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			session.persist( new Item( 1, false ) );
			session.persist( new Item( 2, false ) );
			session.persist( new Item( 3, true ) );
		} );
	}

	@AfterEach
	void cleanup(SessionFactoryScope scope) {
		scope.getSessionFactory().getSchemaManager().truncate();
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void queryRetainsIdRestriction(boolean includeDeleted, SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			session.enableFilter( "visible" ).setParameter( "includeDeleted", includeDeleted );
			assertThat( session.createQuery(
					"select i.id from FilterItem i where i.id = :id", Integer.class )
					.setParameter( "id", 2 ).getResultList() ).containsExactly( 2 );
		} );
	}

	@Entity(name = "FilterItem")
	@Table(name = "filter_item")
	@FilterDef(
			name = "visible",
			defaultCondition = ":includeDeleted = true or deleted = false",
			parameters = @ParamDef(name = "includeDeleted", type = Boolean.class)
	)
	@Filter(name = "visible")
	static class Item {
		@Id
		Integer id;
		boolean deleted;

		Item() {
		}

		Item(Integer id, boolean deleted) {
			this.id = id;
			this.deleted = deleted;
		}
	}
}
