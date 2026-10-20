package org.hibernate.orm.test.stateless;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.JiraKey;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OrderColumn;

import static jakarta.persistence.FetchType.EAGER;
import static org.assertj.core.api.Assertions.assertThat;

@DomainModel(annotatedClasses = StatelessEmbeddedCollectionTest.Employee.class)
@SessionFactory
@JiraKey("HHH-20997")
class StatelessEmbeddedCollectionTest {
	@AfterEach
	void cleanup(SessionFactoryScope scope) {
		scope.dropData();
	}

	@Test
	void testInsert(SessionFactoryScope scope) {
		final var employee = new Employee();
		employee.skillSet.skills.addAll( List.of( new Skill( "Java", 3 ), new Skill( "JPA", 2 ), new Skill( "SQL", 1 ) ) );
		scope.inStatelessTransaction( session -> session.insert( employee ) );
		assertThat( employee.id ).isNotNull();
		assertSkills( scope, employee, "Java", "JPA", "SQL" );
	}

	private static void assertSkills(SessionFactoryScope scope, Employee employee, String... names) {
		scope.inTransaction( session -> assertThat( session.createSelectionQuery(
				"select s.name from Employee e join e.skillSet.skills s where e.id = :id order by index(s)",
				String.class
		).setParameter( "id", employee.id ).getResultList() ).containsExactly( names ) );
	}

	@Entity(name = "Employee")
	static class Employee {
		@Id
		@GeneratedValue
		Long id;

		@Embedded
		SkillSet skillSet = new SkillSet();
	}

	@Embeddable
	static class SkillSet {
		@ElementCollection(fetch = EAGER)
		@CollectionTable(name = "employee_skills")
		@OrderColumn(name = "skill_order")
		List<Skill> skills = new ArrayList<>();
	}

	@Embeddable
	static class Skill {
		String name;
		int skillLevel;

		Skill() {
		}

		Skill(String name, int skillLevel) {
			this.name = name;
			this.skillLevel = skillLevel;
		}
	}
}
