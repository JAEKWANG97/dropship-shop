package com.dropshipshop.api.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import com.dropshipshop.api.auth.security.TestAuthentication;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.domain.ShippingAddressSnapshot;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.payment.domain.PaymentGroup;
import com.dropshipshop.api.payment.repository.PaymentGroupRepository;
import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.repository.UserAccountRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.RollbackException;

@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:order_optimistic_locking_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderOptimisticLockingIntegrationTest {

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private SupplierRepository supplierRepository;

	@Autowired
	private CustomerOrderRepository orderRepository;

	@Autowired
	private PaymentGroupRepository paymentGroupRepository;

	@Test
	void staleCustomerOrderCommitFailsInsteadOfOverwritingSupplierWorkStart() {
		CustomerOrder order = createSupplierOrderPendingOrder("LOCK-ORDER-1", "LOCK-CO-1", 12000);
		EntityManager first = entityManagerFactory.createEntityManager();
		EntityManager second = entityManagerFactory.createEntityManager();
		EntityTransaction firstTx = first.getTransaction();
		EntityTransaction secondTx = second.getTransaction();

		try {
			firstTx.begin();
			secondTx.begin();
			CustomerOrder adminView = first.find(CustomerOrder.class, order.getId());
			CustomerOrder staleCustomerView = second.find(CustomerOrder.class, order.getId());

			adminView.startSupplierOrderWork(TestAuthentication.ADMIN_ID, Instant.now());
			firstTx.commit();

			staleCustomerView.markRefundRequested();
			assertThatThrownBy(secondTx::commit).isInstanceOf(RollbackException.class);
		} finally {
			rollbackIfActive(firstTx);
			rollbackIfActive(secondTx);
			first.close();
			second.close();
		}

		CustomerOrder saved = orderRepository.findById(order.getId()).orElseThrow();
		assertThat(saved.getStatus()).isEqualTo(OrderStatus.SUPPLIER_ORDER_PENDING);
		assertThat(saved.getAddressLockedAt()).isNotNull();
	}

	@Test
	void stalePaymentGroupCommitFailsInsteadOfOverwritingPolicyConfirmation() {
		PaymentGroup paymentGroup = createPaymentGroup("LOCK-PG-CO-1", 13000);
		EntityManager first = entityManagerFactory.createEntityManager();
		EntityManager second = entityManagerFactory.createEntityManager();
		EntityTransaction firstTx = first.getTransaction();
		EntityTransaction secondTx = second.getTransaction();

		try {
			firstTx.begin();
			secondTx.begin();
			PaymentGroup checkoutView = first.find(PaymentGroup.class, paymentGroup.getId());
			PaymentGroup staleAdminView = second.find(PaymentGroup.class, paymentGroup.getId());

			checkoutView.confirmPolicy(Instant.now());
			firstTx.commit();

			staleAdminView.recordDepositMismatch(TestAuthentication.ADMIN_ID, "Stale mismatch memo", Instant.now());
			assertThatThrownBy(secondTx::commit).isInstanceOf(RollbackException.class);
		} finally {
			rollbackIfActive(firstTx);
			rollbackIfActive(secondTx);
			first.close();
			second.close();
		}

		PaymentGroup saved = paymentGroupRepository.findById(paymentGroup.getId()).orElseThrow();
		assertThat(saved.getPolicyConfirmedAt()).isNotNull();
		assertThat(saved.getDepositMismatchMemo()).isNull();
	}

	private CustomerOrder createSupplierOrderPendingOrder(String orderNumber, String checkoutNumber, long amount) {
		UserAccount customer = createCustomer(orderNumber);
		Supplier supplier = supplierRepository.save(new Supplier(
			"Supplier " + orderNumber,
			"Manager",
			"010-0000-0000",
			orderNumber + "@supplier.example",
			null
		));
		PaymentGroup paymentGroup = createPaymentGroup(customer, checkoutNumber, amount);
		CustomerOrder order = new CustomerOrder(
			orderNumber,
			customer,
			supplier,
			paymentGroup,
			new ShippingAddressSnapshot("Receiver", "010-1111-2222", "12345", "Seoul test road", "101"),
			amount,
			Instant.now().plusSeconds(86400)
		);
		order.markSupplierOrderPending();
		return orderRepository.saveAndFlush(order);
	}

	private PaymentGroup createPaymentGroup(String checkoutNumber, long amount) {
		return createPaymentGroup(createCustomer(checkoutNumber), checkoutNumber, amount);
	}

	private PaymentGroup createPaymentGroup(UserAccount customer, String checkoutNumber, long amount) {
		return paymentGroupRepository.saveAndFlush(new PaymentGroup(
			checkoutNumber,
			customer,
			amount,
			Instant.now().plusSeconds(86400)
		));
	}

	private UserAccount createCustomer(String providerUserId) {
		return userAccountRepository.saveAndFlush(new UserAccount(
			SocialProvider.GOOGLE,
			providerUserId,
			providerUserId + "@example.com",
			providerUserId,
			UserRole.CUSTOMER
		));
	}

	private void rollbackIfActive(EntityTransaction transaction) {
		if (transaction.isActive()) {
			transaction.rollback();
		}
	}
}
