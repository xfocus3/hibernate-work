package org.hibernate.orm.test.pagination;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

import org.hibernate.Hibernate;
import org.hibernate.cfg.QuerySettings;
import org.hibernate.testing.orm.junit.DialectFeatureChecks;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.JiraKey;
import org.hibernate.testing.orm.junit.RequiresDialectFeature;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression based on the reproducer by Simon Juresa.
 */
@DomainModel(annotatedClasses = {
		CollectionFetchPaginationSingleTableTest.Item.class,
		CollectionFetchPaginationSingleTableTest.Video.class,
		CollectionFetchPaginationSingleTableTest.Source.class,
		CollectionFetchPaginationSingleTableTest.Tag.class
})
@ServiceRegistry(settings = @Setting(name = QuerySettings.QUERY_PLAN_CACHE_ENABLED, value = "false"))
@SessionFactory
@RequiresDialectFeature(feature = DialectFeatureChecks.SupportsOffsetInSubquery.class)
@JiraKey("HHH-20942")
class CollectionFetchPaginationSingleTableTest {
	private static final String FETCH_QUERY =
			"select i from Item i join fetch i.source s left join fetch s.tags";

	@BeforeEach
	void setUp(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			final Source source = new Source();
			source.id = 1L;
			session.persist( source );
			for ( long id = 1; id <= 2; id++ ) {
				final Tag tag = new Tag();
				tag.id = id;
				tag.source = source;
				source.tags.add( tag );
				session.persist( tag );
			}
			for ( long id = 1; id <= 4; id++ ) {
				final Item item;
				if ( id <= 3 ) {
					final Video video = new Video();
					video.channelId = "channel-" + id;
					item = video;
				}
				else {
					item = new Item();
				}
				item.id = id;
				item.source = source;
				session.persist( item );
			}
		} );
	}

	@AfterEach
	void tearDown(SessionFactoryScope scope) {
		scope.getSessionFactory().getSchemaManager().truncate();
	}

	@Test
	void paginatedRootIncludesSubclassColumns(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			final List<Item> items = session.createSelectionQuery( FETCH_QUERY + " order by i.id", Item.class )
					.setMaxResults( 2 )
					.getResultList();
			assertEquals( List.of( 1L, 2L ), items.stream().map( item -> item.id ).toList() );
			for ( Item item : items ) {
				assertEquals( "channel-" + item.id, assertInstanceOf( Video.class, item ).channelId );
				assertTrue( Hibernate.isInitialized( item.source.tags ) );
				assertEquals( Set.of( 1L, 2L ), item.source.tags.stream().map( tag -> tag.id ).collect( java.util.stream.Collectors.toSet() ) );
			}
		} );
	}

	@Entity(name = "Item")
	@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
	static class Item {
		@Id
		Long id;

		@ManyToOne(optional = false)
		Source source;
	}

	@Entity(name = "Video")
	@DiscriminatorValue("Video")
	static class Video extends Item {
		@Column(name = "channel_id")
		String channelId;
	}

	@Entity(name = "Source")
	static class Source {
		@Id
		Long id;

		@OneToMany(mappedBy = "source")
		Set<Tag> tags = new HashSet<>();
	}

	@Entity(name = "Tag")
	static class Tag {
		@Id
		Long id;

		@ManyToOne
		Source source;
	}
}
