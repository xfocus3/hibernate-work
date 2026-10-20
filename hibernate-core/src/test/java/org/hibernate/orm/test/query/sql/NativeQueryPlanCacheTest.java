package org.hibernate.orm.test.query.sql;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.JiraKey;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DomainModel(annotatedClasses = NativeQueryPlanCacheTest.Item.class)
@SessionFactory(generateStatistics = true)
@JiraKey("HHH-20987")
class NativeQueryPlanCacheTest {
	private static final String SQL = "select id from cache_item where id = :id";

	@BeforeEach
	void setUp(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			for ( long id = 1; id <= 3; id++ ) {
				session.persist( new Item( id ) );
			}
		} );
		scope.getSessionFactory().getQueryEngine().getInterpretationCache().close();
		scope.getSessionFactory().getStatistics().clear();
	}

	@AfterEach
	void tearDown(SessionFactoryScope scope) {
		scope.dropData();
	}

	@Test
	void basicResultClassReusesPlan(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			for ( long id = 1; id <= 3; id++ ) {
				Long result = session.createNativeQuery( SQL, Long.class )
						.setParameter( "id", id )
						.getSingleResult();
				assertEquals( id, result.longValue() );
			}
		} );
		assertEquals( 1, scope.getSessionFactory().getStatistics().getQueryPlanCacheMissCount() );
		assertEquals( 2, scope.getSessionFactory().getStatistics().getQueryPlanCacheHitCount() );
	}

	@Entity(name = "CacheItem")
	@Table(name = "cache_item")
	static class Item {
		@Id
		private Long id;

		Item() {
		}

		Item(Long id) {
			this.id = id;
		}
	}
}
