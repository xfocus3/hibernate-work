/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.orm.test.refresh;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.JiraKey;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.Test;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import static org.assertj.core.api.Assertions.assertThat;

@DomainModel(annotatedClasses = {
		NestedCascadeRefreshTest.Root.class,
		NestedCascadeRefreshTest.Parent.class,
		NestedCascadeRefreshTest.Child.class
})
@SessionFactory
@JiraKey("HHH-21009")
class NestedCascadeRefreshTest {
	@Test
	void refreshInitializedNestedCollection(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			final Root root = new Root();
			root.id = 1;
			final Parent parent = new Parent();
			parent.id = 2;
			parent.root = root;
			root.parents.add( parent );
			final Child child = new Child();
			child.id = 3;
			child.parent = parent;
			child.label = "old";
			parent.children.add( child );
			session.persist( root );
			session.flush();

			assertThat( session.createNativeMutationQuery( "update nested_refresh_child set label = 'new' where id = 3" )
					.executeUpdate() ).isEqualTo( 1 );
			assertThat( child.label ).isEqualTo( "old" );

			session.refresh( root );

			assertThat( child.label ).isEqualTo( "new" );
			assertThat( root.parents.get( 0 ) ).isSameAs( parent );
			assertThat( parent.children.get( 0 ) ).isSameAs( child );
		} );
	}

	@Entity(name = "NestedRefreshRoot")
	@Table(name = "nested_refresh_root")
	public static class Root {
		@Id
		private long id;

		@OneToMany(mappedBy = "root", cascade = CascadeType.ALL)
		private List<Parent> parents = new ArrayList<>();
	}

	@Entity(name = "NestedRefreshParent")
	@Table(name = "nested_refresh_parent")
	public static class Parent {
		@Id
		private long id;

		@ManyToOne(fetch = FetchType.LAZY)
		private Root root;

		@OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
		private List<Child> children = new ArrayList<>();
	}

	@Entity(name = "NestedRefreshChild")
	@Table(name = "nested_refresh_child")
	public static class Child {
		@Id
		private long id;

		private String label;

		@ManyToOne(fetch = FetchType.LAZY)
		private Parent parent;
	}
}

