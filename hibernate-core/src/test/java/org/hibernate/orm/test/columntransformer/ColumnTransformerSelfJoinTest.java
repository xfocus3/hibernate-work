package org.hibernate.orm.test.columntransformer;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.ColumnTransformer;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.JiraKey;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import static org.assertj.core.api.Assertions.assertThat;

@DomainModel(annotatedClasses = ColumnTransformerSelfJoinTest.PaymentRequestLog.class)
@SessionFactory
@JiraKey("HHH-20354")
class ColumnTransformerSelfJoinTest {

	@BeforeAll
	void setUp(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			final PaymentRequestLog parent = new PaymentRequestLog( 1L, "PARENT@EXAMPLE.COM" );
			parent.upgrades.add( new PaymentRequestLog( 2L, "CHILD@EXAMPLE.COM" ) );
			session.persist( parent );
		} );
	}

	@Test
	void testSelfJoin(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			final PaymentRequestLog parent = session.createQuery(
					"select p from PaymentRequestLog p left join fetch p.upgrades where p.id = 1",
					PaymentRequestLog.class
			).getSingleResult();
			assertThat( parent.email ).isEqualTo( "parent@example.com" );
			assertThat( parent.upgrades ).hasSize( 1 );
			assertThat( parent.upgrades.get( 0 ).email ).isEqualTo( "child@example.com" );
		} );
	}

	@Entity(name = "PaymentRequestLog")
	@Table(name = "payment_request_log")
	static class PaymentRequestLog {
		@Id
		Long id;

		@Column(name = "email")
		@ColumnTransformer(read = "lower(email)", write = "upper(?)")
		String email;

		@OneToMany(cascade = CascadeType.ALL)
		@JoinColumn(name = "upgrade_payment_request_log_id")
		List<PaymentRequestLog> upgrades = new ArrayList<>();

		PaymentRequestLog() {
		}

		PaymentRequestLog(Long id, String email) {
			this.id = id;
			this.email = email;
		}
	}
}
