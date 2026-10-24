package org.hibernate.orm.test.graph;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.hibernate.graph.EntityGraphs;
import org.hibernate.orm.test.graph.entity.GraphParsingTestEntity;
import org.hibernate.orm.test.graph.entity.GraphParsingTestSubentity;
import org.hibernate.testing.orm.junit.EntityManagerFactoryScope;
import org.hibernate.testing.orm.junit.JiraKey;
import org.hibernate.testing.orm.junit.Jpa;

import jakarta.persistence.Subgraph;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Jpa(annotatedClasses = { GraphParsingTestEntity.class, GraphParsingTestSubentity.class })
@JiraKey("HHH-16428")
@SuppressWarnings("rawtypes")
class SubgraphMapEqualityTest {
	@Test
	void nullMapsAreEqual() {
		assertThat( EntityGraphs.areEqual( (Map<Class, Subgraph>) null, null ) ).isTrue();
	}

	@Test
	void nullFirstMapIsNotEqualToEmptyMap() {
		assertThat( EntityGraphs.areEqual( null, new HashMap<Class, Subgraph>() ) ).isFalse();
	}

	@Test
	void nullSecondMapIsNotEqualToEmptyMap() {
		assertThat( EntityGraphs.areEqual( new HashMap<Class, Subgraph>(), null ) ).isFalse();
	}

	@Test
	void identicalMapIsEqual() {
		final Map<Class, Subgraph> graph = mapWithNull( GraphParsingTestEntity.class );
		assertThat( EntityGraphs.areEqual( graph, graph ) ).isTrue();
	}

	@Test
	void emptyMapsAreEqual() {
		assertThat( EntityGraphs.areEqual( new HashMap<Class, Subgraph>(), new HashMap<Class, Subgraph>() ) ).isTrue();
	}

	@Test
	void matchingNullValuesAreEqual() {
		assertThat( EntityGraphs.areEqual(
				mapWithNull( GraphParsingTestEntity.class ), mapWithNull( GraphParsingTestEntity.class )
		) ).isTrue();
	}

	@Test
	void nullValueDoesNotEqualAbsentKey() {
		assertThat( EntityGraphs.areEqual(
				mapWithNull( GraphParsingTestEntity.class ), new HashMap<Class, Subgraph>()
		) ).isFalse();
	}

	@Test
	void differentKeysWithNullValuesAreNotEqual() {
		assertThat( EntityGraphs.areEqual(
				mapWithNull( GraphParsingTestEntity.class ), mapWithNull( GraphParsingTestSubentity.class )
		) ).isFalse();
	}

	@Test
	void nullValueDoesNotEqualSubgraph(EntityManagerFactoryScope scope) {
		assertThat( EntityGraphs.areEqual(
				mapWithNull( GraphParsingTestEntity.class ),
				Map.of( GraphParsingTestEntity.class, subgraph( scope, GraphParsingTestEntity.class, "name" ) )
		) ).isFalse();
	}

	@Test
	void subgraphDoesNotEqualNullValue(EntityManagerFactoryScope scope) {
		assertThat( EntityGraphs.areEqual(
				Map.of( GraphParsingTestEntity.class, subgraph( scope, GraphParsingTestEntity.class, "name" ) ),
				mapWithNull( GraphParsingTestEntity.class )
		) ).isFalse();
	}

	@Test
	void equivalentSubgraphsIgnoreAttributeOrder(EntityManagerFactoryScope scope) {
		assertThat( EntityGraphs.areEqual(
				Map.of( GraphParsingTestEntity.class, subgraph( scope, GraphParsingTestEntity.class, "name", "description" ) ),
				Map.of( GraphParsingTestEntity.class, subgraph( scope, GraphParsingTestEntity.class, "description", "name" ) )
		) ).isTrue();
	}

	@Test
	void differentSubgraphAttributesAreNotEqual(EntityManagerFactoryScope scope) {
		assertThat( EntityGraphs.areEqual(
				Map.of( GraphParsingTestEntity.class, subgraph( scope, GraphParsingTestEntity.class, "name" ) ),
				Map.of( GraphParsingTestEntity.class, subgraph( scope, GraphParsingTestEntity.class, "description" ) )
		) ).isFalse();
	}

	@Test
	void equivalentMapsIgnoreEntryOrder(EntityManagerFactoryScope scope) {
		final Map<Class, Subgraph> first = new LinkedHashMap<>();
		first.put( GraphParsingTestEntity.class, subgraph( scope, GraphParsingTestEntity.class, "name" ) );
		first.put( GraphParsingTestSubentity.class, subgraph( scope, GraphParsingTestSubentity.class, "sub" ) );
		final Map<Class, Subgraph> second = new LinkedHashMap<>();
		second.put( GraphParsingTestSubentity.class, subgraph( scope, GraphParsingTestSubentity.class, "sub" ) );
		second.put( GraphParsingTestEntity.class, subgraph( scope, GraphParsingTestEntity.class, "name" ) );
		assertThat( EntityGraphs.areEqual( first, second ) ).isTrue();
	}

	private static Map<Class, Subgraph> mapWithNull(Class key) {
		final Map<Class, Subgraph> map = new HashMap<>();
		map.put( key, null );
		return map;
	}

	private static <T extends GraphParsingTestEntity> Subgraph<T> subgraph(
			EntityManagerFactoryScope scope, Class<T> type, String... attributes) {
		return scope.fromEntityManager( entityManager -> {
			final var graph = entityManager.createEntityGraph( GraphParsingTestEntity.class );
			final var subgraph = graph.addSubgraph( "linkToOne", type );
			subgraph.addAttributeNodes( attributes );
			return subgraph;
		} );
	}
}
