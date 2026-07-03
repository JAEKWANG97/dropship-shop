package com.dropshipshop.api.dev;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderItemRepository;
import com.dropshipshop.api.payment.domain.PaymentGroupStatus;
import com.dropshipshop.api.payment.repository.PaymentRepository;
import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.repository.UserAccountRepository;

@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.MOCK,
	properties = {
	"app.seed.enabled=true",
	"spring.datasource.url=jdbc:h2:mem:local_order_seed_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.flyway.enabled=false",
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"app.storage.local.upload-dir=build/test-product-images-order-seed",
	"app.catalog.image-storage-path=build/test-product-images-order-seed"
})
@ActiveProfiles("local")
class LocalOrderSeedDataTest {

	private final UserAccountRepository userAccountRepository;
	private final CustomerOrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final PaymentRepository paymentRepository;

	@Autowired
	LocalOrderSeedDataTest(
		UserAccountRepository userAccountRepository,
		CustomerOrderRepository orderRepository,
		OrderItemRepository orderItemRepository,
		PaymentRepository paymentRepository
	) {
		this.userAccountRepository = userAccountRepository;
		this.orderRepository = orderRepository;
		this.orderItemRepository = orderItemRepository;
		this.paymentRepository = paymentRepository;
	}

	@Test
	@Transactional
	void seedsLocalAdminOrderFlowData() {
		assertThat(userAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "local-b003-customer"))
			.hasValueSatisfying(user -> assertThat(user.getRole()).isEqualTo(UserRole.CUSTOMER));
		assertThat(userAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "local-b003-admin"))
			.hasValueSatisfying(user -> assertThat(user.getRole()).isEqualTo(UserRole.ADMIN));

		assertThat(orderRepository.findAll())
			.extracting(order -> order.getStatus())
			.containsAll(EnumSet.of(
				OrderStatus.PAYMENT_PENDING,
				OrderStatus.SUPPLIER_ORDER_PENDING,
				OrderStatus.SUPPLIER_ORDERED,
				OrderStatus.SHIPPED,
				OrderStatus.DELIVERED,
				OrderStatus.OUT_OF_STOCK
			));
		assertThat(orderRepository.findAll())
			.filteredOn(order -> order.getOrderNumber().startsWith("LOCAL-B003-"))
			.hasSize(6)
			.allSatisfy(order -> assertThat(orderItemRepository.findAllByOrder_IdOrderByCreatedAtAsc(order.getId())).hasSize(1));
		assertThat(orderRepository.findAll())
			.filteredOn(order -> order.getStatus() != OrderStatus.PAYMENT_PENDING)
			.allSatisfy(order -> assertThat(order.getPaymentGroup().getStatus()).isEqualTo(PaymentGroupStatus.APPROVED));
		assertThat(paymentRepository.findAll())
			.filteredOn(payment -> payment.getProviderPaymentKey().startsWith("BANK-LOCAL-B003-"))
			.hasSize(5);
	}
}
