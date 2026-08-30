package com.dropshipshop.api.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.dropshipshop.api.auth.JwtAccessTokenService;
import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.claim.domain.Claim;
import com.dropshipshop.api.claim.domain.ClaimReason;
import com.dropshipshop.api.claim.domain.ClaimStatus;
import com.dropshipshop.api.claim.domain.ClaimType;
import com.dropshipshop.api.claim.domain.RequestedAction;
import com.dropshipshop.api.claim.repository.ClaimRepository;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItem;
import com.dropshipshop.api.order.domain.ShippingAddressSnapshot;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderItemRepository;
import com.dropshipshop.api.payment.domain.PaymentGroup;
import com.dropshipshop.api.payment.repository.PaymentGroupRepository;
import com.dropshipshop.api.refund.domain.Refund;
import com.dropshipshop.api.refund.domain.RefundReason;
import com.dropshipshop.api.refund.repository.RefundRepository;
import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.domain.UserStatus;
import com.dropshipshop.api.user.repository.UserAccountRepository;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AccountDeletionApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtAccessTokenService jwtAccessTokenService;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private SupplierRepository supplierRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ProductOptionRepository productOptionRepository;

	@Autowired
	private PaymentGroupRepository paymentGroupRepository;

	@Autowired
	private CustomerOrderRepository orderRepository;

	@Autowired
	private OrderItemRepository orderItemRepository;

	@Autowired
	private RefundRepository refundRepository;

	@Autowired
	private ClaimRepository claimRepository;

	@Test
	void deletesAndAnonymizesCustomerAccountThenRejectsExistingToken() throws Exception {
		UserAccount customer = createCustomer("delete-success");
		customer.verifyPhone("01012345678", Instant.now());
		customer = userAccountRepository.saveAndFlush(customer);
		Cookie accessToken = accessToken(customer);

		mockMvc.perform(post("/api/me/deletion-request").cookie(accessToken))
			.andExpect(status().isNoContent())
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("ACCESS_TOKEN=;")))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

		UserAccount deleted = userAccountRepository.findById(customer.getId()).orElseThrow();
		assertThat(deleted.getStatus()).isEqualTo(UserStatus.DELETED);
		assertThat(deleted.getProviderUserId()).isEqualTo("deleted-" + customer.getId());
		assertThat(deleted.getEmail()).isEqualTo("deleted-" + customer.getId() + "@deleted.local");
		assertThat(deleted.getDisplayName()).isEqualTo("탈퇴회원");
		assertThat(deleted.getPhoneNumber()).isNull();
		assertThat(deleted.getPhoneVerifiedAt()).isNull();
		assertThat(deleted.getDeletedAt()).isNotNull();
		assertThat(deleted.getAnonymizedAt()).isNotNull();

		mockMvc.perform(get("/api/me").cookie(accessToken))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsAnonymousDeletionRequest() throws Exception {
		mockMvc.perform(post("/api/me/deletion-request"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void blocksDeletionWhenOrderRefundOrClaimIsInProgress() throws Exception {
		UserAccount customer = createCustomer("delete-blocked");
		CustomerOrder activeOrder = createPendingOrder(customer, "ORD-DELETE-BLOCK-1", "CK-DELETE-BLOCK-1");
		CustomerOrder refundOrder = createDeliveredOrder(customer, "ORD-DELETE-BLOCK-2", "CK-DELETE-BLOCK-2");
		CustomerOrder claimOrder = createDeliveredOrder(customer, "ORD-DELETE-BLOCK-3", "CK-DELETE-BLOCK-3");
		refundRepository.save(new Refund(refundOrder, RefundReason.RETURN_REQUESTED));
		claimRepository.save(new Claim(
			claimOrder,
			customer,
			ClaimType.RETURN,
			ClaimReason.DEFECT,
			ClaimStatus.REQUESTED,
			RequestedAction.REFUND,
			"반품 요청"
		));

		mockMvc.perform(post("/api/me/deletion-request").cookie(accessToken(customer)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message", containsString("진행 중인 주문/환불/클레임")))
			.andExpect(jsonPath("$.message", containsString(activeOrder.getOrderNumber())))
			.andExpect(jsonPath("$.message", containsString(refundOrder.getOrderNumber())))
			.andExpect(jsonPath("$.message", containsString(claimOrder.getOrderNumber())));

		assertThat(userAccountRepository.findById(customer.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
	}

	@Test
	void blocksDeletionForPaymentGroupRefundWithoutDereferencingAnOrder() throws Exception {
		UserAccount customer = createCustomer("delete-group-refund-blocked");
		PaymentGroup paymentGroup = paymentGroupRepository.save(new PaymentGroup(
			"CK-DELETE-GROUP-REFUND",
			customer,
			10000,
			Instant.now().plusSeconds(1800)
		));
		refundRepository.save(Refund.receivedPaymentGroup(paymentGroup, null, 11000, Instant.now()));

		mockMvc.perform(post("/api/me/deletion-request").cookie(accessToken(customer)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message", containsString("진행 중인 주문/환불/클레임")))
			.andExpect(jsonPath("$.message", containsString("결제그룹 CK-DELETE-GROUP-REFUND")));

		assertThat(userAccountRepository.findById(customer.getId()).orElseThrow().getStatus())
			.isEqualTo(UserStatus.ACTIVE);
	}

	@Test
	void blocksDeletionUntilSupplierManagerIsDisconnected() throws Exception {
		UserAccount manager = createCustomer("supplier-manager-delete-blocked");
		Supplier supplier = Supplier.portalApplicant(
			"Deletion Guard Supplier",
			"Deletion Guard Manager",
			"010-0000-0000",
			"deletion-guard@supplier.example",
			null
		);
		supplier.bindManager(manager.getId(), Instant.now());
		supplierRepository.saveAndFlush(supplier);

		mockMvc.perform(post("/api/me/deletion-request").cookie(accessToken(manager)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message", containsString("disconnected by an administrator")));

		assertThat(userAccountRepository.findById(manager.getId()).orElseThrow().getStatus())
			.isEqualTo(UserStatus.ACTIVE);
		assertThat(supplierRepository.findById(supplier.getId()).orElseThrow().getManagerUserId())
			.isEqualTo(manager.getId());
	}

	private Cookie accessToken(UserAccount user) {
		return new Cookie("ACCESS_TOKEN", jwtAccessTokenService.issue(user));
	}

	private UserAccount createCustomer(String providerUserId) {
		return userAccountRepository.save(new UserAccount(
			SocialProvider.GOOGLE,
			providerUserId,
			providerUserId + "@example.com",
			providerUserId,
			UserRole.CUSTOMER
		));
	}

	private CustomerOrder createDeliveredOrder(UserAccount customer, String orderNumber, String checkoutNumber) {
		CustomerOrder order = createPendingOrder(customer, orderNumber, checkoutNumber);
		order.markSupplierOrderPending();
		order.startSupplierOrderWork(TestAdminIds.ADMIN_ID, Instant.now());
		order.markSupplierOrdered();
		order.markShipped();
		order.markDeliveredByTracking();
		return orderRepository.saveAndFlush(order);
	}

	private CustomerOrder createPendingOrder(UserAccount customer, String orderNumber, String checkoutNumber) {
		Supplier supplier = supplierRepository.save(new Supplier(
			"Supplier " + orderNumber,
			"Manager",
			"010-0000-0000",
			orderNumber + "@supplier.example",
			null
		));
		Product product = productRepository.save(new Product(
			supplier,
			"Deletion Product " + orderNumber,
			"Deletion Product Summary",
			10000,
			ProductStatus.ACTIVE
		));
		ProductOption option = productOptionRepository.save(new ProductOption(product, "Default", 0, ProductOptionStatus.ACTIVE));
		PaymentGroup paymentGroup = paymentGroupRepository.save(new PaymentGroup(
			checkoutNumber,
			customer,
			10000,
			Instant.now().plusSeconds(1800)
		));
		CustomerOrder order = orderRepository.save(new CustomerOrder(
			orderNumber,
			customer,
			supplier,
			paymentGroup,
			new ShippingAddressSnapshot("Receiver", "010-1111-2222", "12345", "Seoul test road", "101"),
			10000,
			paymentGroup.getExpiresAt()
		));
		orderItemRepository.save(new OrderItem(order, product, option, 1, 10000));
		return order;
	}

	private static final class TestAdminIds {

		private static final java.util.UUID ADMIN_ID = java.util.UUID.fromString("00000000-0000-0000-0000-000000000002");
	}
}
