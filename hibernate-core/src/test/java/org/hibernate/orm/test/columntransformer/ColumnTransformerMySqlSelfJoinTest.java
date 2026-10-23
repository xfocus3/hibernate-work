package org.hibernate.orm.test.columntransformer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.dialect.MySQLDialect;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.JiraKey;
import org.hibernate.testing.orm.junit.RequiresDialect;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hibernate.jpa.SpecHints.HINT_SPEC_FETCH_GRAPH;

@DomainModel(annotatedClasses = ColumnTransformerMySqlSelfJoinTest.PaymentRequestLog.class)
@SessionFactory(useCollectingStatementInspector = true)
@RequiresDialect(MySQLDialect.class)
@JiraKey("HHH-20354")
class ColumnTransformerMySqlSelfJoinTest {

	@BeforeAll
	void setUp(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			final PaymentRequestLog parent = new PaymentRequestLog( "parent", "parent@example.com" );
			final PaymentRequestLog child = new PaymentRequestLog( "child", "child@example.com" );
			session.persist( parent );
			session.persist( child );
			parent.upgradePaymentRequestLogs.add( child );
		} );
	}

	@Test
	void testSelfJoin(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			final PaymentRequestLog parent = session.createQuery(
					"select p from PaymentRequestLog p left join fetch p.upgradePaymentRequestLogs where p.id = 'parent'",
					PaymentRequestLog.class
			).getSingleResult();
			assertThat( parent.email ).isEqualTo( "parent@example.com" );
			assertThat( parent.upgradePaymentRequestLogs ).hasSize( 1 );
			final PaymentRequestLog child = parent.upgradePaymentRequestLogs.get( 0 );
			assertThat( child.email ).isEqualTo( "child@example.com" );
			assertThat( child.upgradePaymentRequestLogId ).isEqualTo( "parent" );
		} );
	}

	@ParameterizedTest(name = "find = {0}")
	@ValueSource(booleans = { true, false })
	void testEntityGraph(boolean find, SessionFactoryScope scope) {
		final var statementInspector = scope.getCollectingStatementInspector();
		statementInspector.clear();
		scope.inTransaction( session -> {
			final var graph = session.createEntityGraph( PaymentRequestLog.class );
			graph.addAttributeNodes( "upgradePaymentRequestLogs" );
			final PaymentRequestLog parent = find
					? session.find( PaymentRequestLog.class, "parent", Map.of( HINT_SPEC_FETCH_GRAPH, graph ) )
					: session.createQuery(
							"select p from PaymentRequestLog p where p.id = :id",
							PaymentRequestLog.class
					)
					.setParameter( "id", "parent" )
					.setHint( HINT_SPEC_FETCH_GRAPH, graph )
					.getSingleResult();
			assertThat( parent.email ).isEqualTo( "parent@example.com" );
			assertThat( parent.upgradePaymentRequestLogs ).hasSize( 1 );
			final PaymentRequestLog child = parent.upgradePaymentRequestLogs.get( 0 );
			assertThat( child.email ).isEqualTo( "child@example.com" );
			assertThat( child.upgradePaymentRequestLogId ).isEqualTo( "parent" );
			statementInspector.assertExecutedCount( 1 );
			statementInspector.assertNumberOfJoins( 0, 1 );
		} );
	}

	@Entity(name = "PaymentRequestLog")
	@Table(name = "payment_request_log")
	static class PaymentRequestLog {
		@Id
		String id;

		@Column(name = "email")
		@ColumnTransformer(
				read = "CAST(AES_DECRYPT(UNHEX(email), 'KEY') AS CHAR)",
				write = "HEX(AES_ENCRYPT(?, 'KEY'))"
		)
		String email;

		@Column(name = "upgrade_payment_request_log_id")
		String upgradePaymentRequestLogId;

		@OneToMany
		@JoinColumn(name = "upgrade_payment_request_log_id", referencedColumnName = "id")
		List<PaymentRequestLog> upgradePaymentRequestLogs = new ArrayList<>();

		PaymentRequestLog() {
		}

		PaymentRequestLog(String id, String email) {
			this.id = id;
			this.email = email;
		}
	}
}
