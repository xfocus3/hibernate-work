/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.orm.test.columntransformer;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.dialect.MySQLDialect;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.JiraKey;
import org.hibernate.testing.orm.junit.RequiresDialect;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import static org.assertj.core.api.Assertions.assertThat;

@DomainModel(annotatedClasses = ColumnTransformerMySqlSelfJoinTest.PaymentRequestLog.class)
@SessionFactory
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
