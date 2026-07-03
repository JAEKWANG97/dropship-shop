package com.dropshipshop.api.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.dropshipshop.api.auth.security.TestAuthentication;
import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.claim.repository.ClaimRepository;
import com.dropshipshop.api.order.domain.AdminOrderActionHistory;
import com.dropshipshop.api.order.domain.AdminOrderActionType;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItem;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.domain.OrderStatusHistory;
import com.dropshipshop.api.order.domain.ShippingAddressSnapshot;
import com.dropshipshop.api.order.repository.AdminOrderActionHistoryRepository;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderItemRepository;
import com.dropshipshop.api.order.repository.OrderStatusHistoryRepository;
import com.dropshipshop.api.payment.domain.Payment;
import com.dropshipshop.api.payment.domain.PaymentGroupStatus;
import com.dropshipshop.api.payment.domain.PaymentGroup;
import com.dropshipshop.api.payment.domain.PaymentMethod;
import com.dropshipshop.api.payment.domain.PaymentProvider;
import com.dropshipshop.api.payment.domain.PaymentStatus;
import com.dropshipshop.api.payment.repository.PaymentGroupRepository;
import com.dropshipshop.api.payment.repository.PaymentRepository;
import com.dropshipshop.api.refund.domain.RefundStatus;
import com.dropshipshop.api.refund.repository.RefundRepository;
import com.dropshipshop.api.shipment.domain.Shipment;
import com.dropshipshop.api.shipment.repository.ShipmentRepository;
import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.repository.UserAccountRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CustomerCancellationApiIntegrationTest {

	private static final byte[] PNG_BYTES = Base64.getDecoder().decode(
		"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="
	);

	@Autowired
	private MockMvc mockMvc;

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
	private PaymentRepository paymentRepository;

	@Autowired
	private CustomerOrderRepository orderRepository;

	@Autowired
	private OrderItemRepository orderItemRepository;

	@Autowired
	private ClaimRepository claimRepository;

	@Autowired
	private RefundRepository refundRepository;

	@Autowired
	private ShipmentRepository shipmentRepository;

	@Autowired
	private OrderStatusHistoryRepository orderStatusHistoryRepository;

	@Autowired
	private AdminOrderActionHistoryRepository adminOrderActionHistoryRepository;

	@Test
	void rejectsAnonymousAndAdminCustomerCancelAccess() throws Exception {
		UserAccount customer = createCustomer("cancel-auth-customer");
		CustomerOrder order = createApprovedOrder(customer, "CANCEL-AUTH-1", "CANCEL-AUTH-CO-1", 12000);

		mockMvc.perform(post("/api/orders/{orderId}/cancel", order.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Changed mind"
					}
					"""))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/orders/{orderId}/cancel", order.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Changed mind"
					}
					"""))
			.andExpect(status().isForbidden());
	}

	@Test
	void selfServiceCancelEligibleOrderMovesToRefundRequested() throws Exception {
		UserAccount customer = createCustomer("cancel-customer-1");
		CustomerOrder order = createApprovedOrder(customer, "CANCEL-ELIGIBLE-1", "CANCEL-ELIGIBLE-CO-1", 22000);

		mockMvc.perform(post("/api/orders/{orderId}/cancel", order.getId())
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Changed mind before supplier work"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.orderId", is(order.getId().toString())))
			.andExpect(jsonPath("$.orderStatus", is("REFUND_REQUESTED")))
			.andExpect(jsonPath("$.claimType", is("CANCEL")))
			.andExpect(jsonPath("$.claimReason", is("SIMPLE_CHANGE_OF_MIND")))
			.andExpect(jsonPath("$.status", is("APPROVED")))
			.andExpect(jsonPath("$.requestedAction", is("REFUND")));

		assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.REFUND_REQUESTED);

		mockMvc.perform(post("/api/orders/{orderId}/cancel", order.getId())
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Duplicate cancel"
					}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsSelfServiceCancelForOtherUserAndIneligibleStates() throws Exception {
		UserAccount customer = createCustomer("cancel-customer-2");
		UserAccount other = createCustomer("cancel-customer-3");
		CustomerOrder locked = createApprovedOrder(customer, "CANCEL-LOCKED-1", "CANCEL-LOCKED-CO-1", 23000);
		locked.startSupplierOrderWork(TestAuthentication.ADMIN_ID, Instant.now());
		orderRepository.saveAndFlush(locked);
		CustomerOrder pending = createPaymentPendingOrder(customer, "CANCEL-PENDING-1", "CANCEL-PENDING-CO-1", 10000);

		mockMvc.perform(post("/api/orders/{orderId}/cancel", locked.getId())
				.with(authentication(TestAuthentication.customer(other.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Not mine"
					}
					"""))
			.andExpect(status().isNotFound());

		mockMvc.perform(post("/api/orders/{orderId}/cancel", locked.getId())
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Already locked"
					}
					"""))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/orders/{orderId}/cancel", pending.getId())
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Payment pending"
					}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void createsCancellationClaimAfterSupplierWorkAndAdminApprovesIt() throws Exception {
		UserAccount customer = createCustomer("cancel-customer-4");
		CustomerOrder order = createApprovedOrder(customer, "CANCEL-CLAIM-1", "CANCEL-CLAIM-CO-1", 24000);
		order.startSupplierOrderWork(TestAuthentication.ADMIN_ID, Instant.now());
		orderRepository.saveAndFlush(order);

		MvcResult result = mockMvc.perform(post("/api/orders/{orderId}/claims", order.getId())
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "claimType": "CANCEL",
					  "claimReason": "SIMPLE_CHANGE_OF_MIND",
					  "customerMemo": "Please cancel if supplier allows it"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.orderStatus", is("SUPPLIER_ORDER_PENDING")))
			.andExpect(jsonPath("$.status", is("REQUESTED")))
			.andExpect(jsonPath("$.requestedAction", is("REFUND")))
			.andReturn();
		String claimId = fieldFrom(result, "claimId");

		mockMvc.perform(get("/api/admin/claims")
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.claims[?(@.claimId == '%s')]".formatted(claimId), hasSize(1)))
			.andExpect(jsonPath("$.claims[?(@.claimId == '%s')].status".formatted(claimId), hasItem("REQUESTED")));

		mockMvc.perform(post("/api/admin/claims/{claimId}/approve", claimId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Supplier cancellation possible"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("APPROVED")))
			.andExpect(jsonPath("$.orderStatus", is("REFUND_REQUESTED")))
			.andExpect(jsonPath("$.reviewedByAdminId", is(TestAuthentication.ADMIN_ID.toString())));

		assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.REFUND_REQUESTED);
	}

	@Test
	void validatesCancellationClaimAndAdminReviewRequests() throws Exception {
		UserAccount customer = createCustomer("cancel-customer-5");
		CustomerOrder order = createSupplierOrderedOrder(customer, "CANCEL-VALIDATION-1", "CANCEL-VALIDATION-CO-1", 25000);

		mockMvc.perform(post("/api/orders/{orderId}/cancel", order.getId())
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/orders/{orderId}/claims", order.getId())
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "claimType": "RETURN",
					  "claimReason": "SIMPLE_CHANGE_OF_MIND",
					  "customerMemo": "Return is not DS-14 scope"
					}
					"""))
			.andExpect(status().isBadRequest());

		MvcResult result = mockMvc.perform(post("/api/orders/{orderId}/claims", order.getId())
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "claimType": "CANCEL",
					  "claimReason": "SIMPLE_CHANGE_OF_MIND",
					  "customerMemo": "Cancel after supplier ordered"
					}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		String claimId = fieldFrom(result, "claimId");

		mockMvc.perform(post("/api/admin/claims/{claimId}/reject", claimId)
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Customer cannot review"
					}
					"""))
			.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/admin/claims/{claimId}/reject", claimId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/admin/claims/{claimId}/reject", claimId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Supplier already started shipment preparation"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("REJECTED")))
			.andExpect(jsonPath("$.orderStatus", is("SUPPLIER_ORDERED")));

		assertThat(claimRepository.findById(UUID.fromString(claimId)).orElseThrow().getStatus().name()).isEqualTo("REJECTED");
	}

	@Test
	void createsReturnAndExchangeClaimsAfterDelivery() throws Exception {
		UserAccount customer = createCustomer("return-customer-1");
		CustomerOrder returnOrder = createDeliveredOrder(customer, "RETURN-CLAIM-1", "RETURN-CLAIM-CO-1", 26000, Instant.now());
		CustomerOrder exchangeOrder = createDeliveredOrder(customer, "EXCHANGE-CLAIM-1", "EXCHANGE-CLAIM-CO-1", 27000, Instant.now());

		MvcResult returnResult = mockMvc.perform(post("/api/orders/{orderId}/claims", returnOrder.getId())
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "claimType": "RETURN",
					  "claimReason": "SIMPLE_CHANGE_OF_MIND",
					  "customerMemo": "I want to return it"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.orderStatus", is("DELIVERED")))
			.andExpect(jsonPath("$.claimType", is("RETURN")))
			.andExpect(jsonPath("$.status", is("REQUESTED")))
			.andExpect(jsonPath("$.requestedAction", is("REFUND")))
			.andReturn();
		String returnClaimId = fieldFrom(returnResult, "claimId");

		mockMvc.perform(post("/api/orders/{orderId}/claims", exchangeOrder.getId())
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "claimType": "EXCHANGE",
					  "claimReason": "SIMPLE_CHANGE_OF_MIND",
					  "customerMemo": "Please exchange item"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.orderStatus", is("DELIVERED")))
			.andExpect(jsonPath("$.claimType", is("EXCHANGE")))
			.andExpect(jsonPath("$.status", is("REQUESTED")))
			.andExpect(jsonPath("$.requestedAction", is("EXCHANGE")));

		mockMvc.perform(get("/api/admin/claims")
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.claims[?(@.claimId == '%s')]".formatted(returnClaimId), hasSize(1)))
			.andExpect(jsonPath("$.claims[?(@.claimId == '%s')].claimType".formatted(returnClaimId), hasItem("RETURN")));
	}

	@Test
	void listsAndShowsClaimEvidenceForCustomerAndAdmin() throws Exception {
		UserAccount customer = createCustomer("return-evidence-customer-1");
		UserAccount otherCustomer = createCustomer("return-evidence-other-customer");
		CustomerOrder order = createDeliveredOrder(customer, "RETURN-EVIDENCE-1", "RETURN-EVIDENCE-CO-1", 26010, Instant.now());
		MockMultipartFile evidence = new MockMultipartFile(
			"evidenceFiles",
			"defect.png",
			"image/png",
			PNG_BYTES
		);

		MvcResult created = mockMvc.perform(multipart("/api/orders/{orderId}/claims", order.getId())
				.file(evidence)
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.param("claimType", "RETURN")
				.param("claimReason", "DEFECT")
				.param("customerMemo", "Product arrived broken"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.claimReason", is("DEFECT")))
			.andExpect(jsonPath("$.customerStatus", is("REVIEWING")))
			.andExpect(jsonPath("$.evidenceFiles", hasSize(1)))
			.andExpect(jsonPath("$.evidenceFiles[0].originalFilename", is("defect.png")))
			.andReturn();
		String claimId = fieldFrom(created, "claimId");

		mockMvc.perform(get("/api/orders/{orderId}/claims", order.getId())
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.claims", hasSize(1)))
			.andExpect(jsonPath("$.claims[0].claimId", is(claimId)))
			.andExpect(jsonPath("$.claims[0].evidenceFiles", hasSize(1)));

		mockMvc.perform(get("/api/orders/{orderId}/claims/{claimId}", order.getId(), claimId)
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.claimId", is(claimId)))
			.andExpect(jsonPath("$.evidenceFiles[0].fileUrl", containsString("/uploads/products/claims/" + claimId)));

		mockMvc.perform(get("/api/orders/{orderId}/claims/{claimId}", order.getId(), claimId)
				.with(authentication(TestAuthentication.customer(otherCustomer.getId()))))
			.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/orders/{orderId}/claims", order.getId()))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/orders/{orderId}", order.getId())
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.claims", hasSize(1)))
			.andExpect(jsonPath("$.claims[0].evidenceFiles", hasSize(1)));

		mockMvc.perform(get("/api/admin/orders/{orderId}", order.getId())
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.claim.evidenceFiles", hasSize(1)))
			.andExpect(jsonPath("$.claim.evidenceFiles[0].originalFilename", is("defect.png")));
	}

	@Test
	void requiresValidEvidenceForSellerFaultClaims() throws Exception {
		UserAccount customer = createCustomer("return-evidence-customer-2");
		CustomerOrder orderWithoutEvidence = createDeliveredOrder(
			customer,
			"RETURN-EVIDENCE-REQUIRED-1",
			"RETURN-EVIDENCE-REQUIRED-CO-1",
			26020,
			Instant.now()
		);
		CustomerOrder orderWithFakeEvidence = createDeliveredOrder(
			customer,
			"RETURN-EVIDENCE-FAKE-1",
			"RETURN-EVIDENCE-FAKE-CO-1",
			26030,
			Instant.now()
		);

		mockMvc.perform(post("/api/orders/{orderId}/claims", orderWithoutEvidence.getId())
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "claimType": "RETURN",
					  "claimReason": "DEFECT",
					  "customerMemo": "Product arrived broken"
					}
					"""))
			.andExpect(status().isBadRequest());

		mockMvc.perform(multipart("/api/orders/{orderId}/claims", orderWithFakeEvidence.getId())
				.file(new MockMultipartFile(
					"evidenceFiles",
					"fake.png",
					"image/png",
					"not image".getBytes()
				))
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.param("claimType", "RETURN")
				.param("claimReason", "DEFECT")
				.param("customerMemo", "Product arrived broken"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void adminReviewsReturnAndExchangeClaimsWithoutChangingOrderStatus() throws Exception {
		UserAccount customer = createCustomer("return-customer-2");
		CustomerOrder returnOrder = createDeliveredOrder(customer, "RETURN-REVIEW-1", "RETURN-REVIEW-CO-1", 28000, Instant.now());
		CustomerOrder exchangeOrder = createDeliveredOrder(customer, "EXCHANGE-REVIEW-1", "EXCHANGE-REVIEW-CO-1", 29000, Instant.now());
		String returnClaimId = createClaim(customer, returnOrder, "RETURN", "SIMPLE_CHANGE_OF_MIND", "Return item");
		String exchangeClaimId = createClaim(customer, exchangeOrder, "EXCHANGE", "SIMPLE_CHANGE_OF_MIND", "Exchange item");

		mockMvc.perform(post("/api/admin/claims/{claimId}/approve", returnClaimId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Return approved after review"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("RETURN_WAITING")))
			.andExpect(jsonPath("$.orderStatus", is("DELIVERED")));

		mockMvc.perform(post("/api/admin/claims/{claimId}/reject", exchangeClaimId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Exchange rejected after review"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("REJECTED")))
			.andExpect(jsonPath("$.orderStatus", is("DELIVERED")));

		assertThat(orderRepository.findById(returnOrder.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.DELIVERED);
		assertThat(orderRepository.findById(exchangeOrder.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.DELIVERED);
	}

	@Test
	void completesDeliveredReturnRefundFlowWithManualBankTransferRefund() throws Exception {
		UserAccount customer = createCustomer("return-refund-customer-1");
		CustomerOrder order = createDeliveredBankTransferOrder(
			customer,
			"RETURN-REFUND-1",
			"RETURN-REFUND-CO-1",
			32000,
			Instant.now()
		);
		String claimId = createClaim(customer, order, "RETURN", "SIMPLE_CHANGE_OF_MIND", "Return after delivery");

		mockMvc.perform(post("/api/admin/claims/{claimId}/approve", claimId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Return approved"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("RETURN_WAITING")))
			.andExpect(jsonPath("$.orderStatus", is("DELIVERED")));

		mockMvc.perform(post("/api/admin/claims/{claimId}/return-received", claimId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "memo": "Returned product received and inspected"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("RETURN_RECEIVED")))
			.andExpect(jsonPath("$.returnReceivedByAdminId", is(TestAuthentication.ADMIN_ID.toString())))
			.andExpect(jsonPath("$.returnReceivedMemo", is("Returned product received and inspected")))
			.andExpect(jsonPath("$.orderStatus", is("DELIVERED")));

		MvcResult refundStart = mockMvc.perform(post("/api/admin/claims/{claimId}/return-refund", claimId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Start refund after return inspection"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("REFUND_PROCESSING")))
			.andExpect(jsonPath("$.orderStatus", is("REFUND_REQUESTED")))
			.andReturn();
		String refundId = fieldFrom(refundStart, "refundId");

		assertThat(refundRepository.findById(UUID.fromString(refundId)).orElseThrow().getStatus()).isEqualTo(RefundStatus.REQUESTED);

		approveRefund(UUID.fromString(refundId), "Return refund approved");

		mockMvc.perform(post("/api/admin/refunds/{refundId}/manual-complete", refundId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Manual bank transfer refund completed",
					  "bankName": "테스트은행",
					  "accountNumber": "123-456",
					  "accountHolder": "Receiver"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("COMPLETED")))
			.andExpect(jsonPath("$.orderStatus", is("REFUNDED")))
			.andExpect(jsonPath("$.paymentGroupStatus", is("REFUNDED")))
			.andExpect(jsonPath("$.paymentStatus", is("REFUNDED")));

		assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.REFUNDED);
		assertThat(claimRepository.findById(UUID.fromString(claimId)).orElseThrow().getStatus().name()).isEqualTo("COMPLETED");
		assertThat(paymentGroupRepository.findById(order.getPaymentGroup().getId()).orElseThrow().getStatus())
			.isEqualTo(PaymentGroupStatus.REFUNDED);
		assertThat(paymentRepository.findFirstByPaymentGroup_IdOrderByCreatedAtDesc(order.getPaymentGroup().getId()).orElseThrow().getStatus())
			.isEqualTo(PaymentStatus.REFUNDED);
		assertThat(paymentRepository.findFirstByPaymentGroup_IdOrderByCreatedAtDesc(order.getPaymentGroup().getId()).orElseThrow().getProvider())
			.isEqualTo(PaymentProvider.BANK_TRANSFER);
		assertThat(orderStatusHistoryRepository.findAllByOrder_IdOrderByCreatedAtAsc(order.getId()))
			.extracting(OrderStatusHistory::getActionType)
			.contains("RETURN_RECEIVED", "RETURN_REFUND_REQUESTED", "MANUAL_REFUND_COMPLETED");
		assertThat(adminOrderActionHistoryRepository.findAllByOrderByCreatedAtDesc())
			.filteredOn(action -> action.getOrder().getId().equals(order.getId()))
			.extracting(AdminOrderActionHistory::getActionType)
			.contains(
				AdminOrderActionType.RETURN_RECEIVED,
				AdminOrderActionType.RETURN_REFUND_REQUESTED,
				AdminOrderActionType.MANUAL_REFUND_COMPLETED
			);
	}

	@Test
	void guardsDeliveredReturnRefundTransitions() throws Exception {
		UserAccount customer = createCustomer("return-refund-customer-2");
		UserAccount otherCustomer = createCustomer("return-refund-other-customer");
		CustomerOrder order = createDeliveredBankTransferOrder(
			customer,
			"RETURN-GUARD-1",
			"RETURN-GUARD-CO-1",
			33000,
			Instant.now()
		);
		String claimId = createClaim(customer, order, "RETURN", "SIMPLE_CHANGE_OF_MIND", "Return guard claim");

		mockMvc.perform(post("/api/orders/{orderId}/claims", order.getId())
				.with(authentication(TestAuthentication.customer(otherCustomer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "claimType": "RETURN",
					  "claimReason": "DEFECT",
					  "customerMemo": "Other customer cannot claim this order"
					}
					"""))
			.andExpect(status().isNotFound());

		mockMvc.perform(post("/api/orders/{orderId}/cancel", order.getId())
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Direct refund without return claim path"
					}
					"""))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/admin/claims/{claimId}/return-received", claimId)
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "memo": "Customer cannot mark return received"
					}
					"""))
			.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/admin/claims/{claimId}/return-refund", claimId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Refund before approval"
					}
					"""))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/admin/claims/{claimId}/approve", claimId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Return approved"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("RETURN_WAITING")));

		mockMvc.perform(post("/api/admin/claims/{claimId}/return-refund", claimId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Refund before return received"
					}
					"""))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/admin/claims/{claimId}/return-received", claimId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "memo": "Return received"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("RETURN_RECEIVED")));

		MvcResult refundStart = mockMvc.perform(post("/api/admin/claims/{claimId}/return-refund", claimId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Start refund"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("REFUND_PROCESSING")))
			.andReturn();
		String refundId = fieldFrom(refundStart, "refundId");
		approveRefund(UUID.fromString(refundId), "Return refund approved");

		mockMvc.perform(post("/api/admin/refunds/{refundId}/manual-complete", refundId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Manual bank transfer refund completed"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("COMPLETED")));

		mockMvc.perform(post("/api/admin/claims/{claimId}/return-refund", claimId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Reprocess completed claim"
					}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsApprovedReturnAndKeepsDeliveredOrder() throws Exception {
		UserAccount customer = createCustomer("return-refund-customer-3");
		CustomerOrder order = createDeliveredOrder(customer, "RETURN-REJECT-1", "RETURN-REJECT-CO-1", 34000, Instant.now());
		String claimId = createClaim(customer, order, "RETURN", "SIMPLE_CHANGE_OF_MIND", "Return for inspection");

		mockMvc.perform(post("/api/admin/claims/{claimId}/approve", claimId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Return approved for inspection"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("RETURN_WAITING")));

		mockMvc.perform(post("/api/admin/claims/{claimId}/reject", claimId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Return item is not eligible after inspection"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("REJECTED")))
			.andExpect(jsonPath("$.orderStatus", is("DELIVERED")))
			.andExpect(jsonPath("$.adminReviewReason", is("Return item is not eligible after inspection")));

		assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.DELIVERED);
		assertThat(adminOrderActionHistoryRepository.findAllByOrderByCreatedAtDesc())
			.filteredOn(action -> action.getOrder().getId().equals(order.getId()))
			.extracting(AdminOrderActionHistory::getActionType)
			.contains(AdminOrderActionType.RETURN_REJECTED);
	}

	@Test
	void rejectsReturnExchangeClaimsBeforeDeliveryAndAfterSimpleChangeWindow() throws Exception {
		UserAccount customer = createCustomer("return-customer-3");
		CustomerOrder supplierOrdered = createSupplierOrderedOrder(customer, "RETURN-NOT-DELIVERED-1", "RETURN-NOT-DELIVERED-CO-1", 30000);
		CustomerOrder oldDelivered = createDeliveredOrder(
			customer,
			"RETURN-OLD-1",
			"RETURN-OLD-CO-1",
			31000,
			Instant.now().minusSeconds(8 * 24 * 60 * 60)
		);

		mockMvc.perform(post("/api/orders/{orderId}/claims", supplierOrdered.getId())
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "claimType": "RETURN",
					  "claimReason": "SIMPLE_CHANGE_OF_MIND",
					  "customerMemo": "Not delivered yet"
					}
					"""))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/orders/{orderId}/claims", oldDelivered.getId())
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "claimType": "RETURN",
					  "claimReason": "SIMPLE_CHANGE_OF_MIND",
					  "customerMemo": "Too late"
					}
					"""))
			.andExpect(status().isBadRequest());
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

	private CustomerOrder createApprovedOrder(UserAccount customer, String orderNumber, String checkoutNumber, long amount) {
		CustomerOrder order = createPaymentPendingOrder(customer, orderNumber, checkoutNumber, amount);
		order.getPaymentGroup().approve(amount, Instant.now());
		paymentGroupRepository.save(order.getPaymentGroup());
		order.markSupplierOrderPending();
		paymentRepository.save(Payment.approved(
			order.getPaymentGroup(),
			"pay-" + orderNumber,
			PaymentMethod.CARD,
			amount,
			amount,
			Instant.now(),
			"DONE",
			Instant.now()
		));
		return orderRepository.saveAndFlush(order);
	}

	private CustomerOrder createBankTransferApprovedOrder(
		UserAccount customer,
		String orderNumber,
		String checkoutNumber,
		long amount
	) {
		CustomerOrder order = createPaymentPendingOrder(customer, orderNumber, checkoutNumber, amount);
		order.getPaymentGroup().approve(amount, Instant.now());
		paymentGroupRepository.save(order.getPaymentGroup());
		order.markSupplierOrderPending();
		paymentRepository.save(Payment.bankTransferApproved(
			order.getPaymentGroup(),
			"BANK-" + checkoutNumber,
			amount,
			Instant.now()
		));
		return orderRepository.saveAndFlush(order);
	}

	private CustomerOrder createSupplierOrderedOrder(
		UserAccount customer,
		String orderNumber,
		String checkoutNumber,
		long amount
	) {
		CustomerOrder order = createApprovedOrder(customer, orderNumber, checkoutNumber, amount);
		order.startSupplierOrderWork(TestAuthentication.ADMIN_ID, Instant.now());
		order.markSupplierOrdered();
		return orderRepository.saveAndFlush(order);
	}

	private CustomerOrder createDeliveredOrder(
		UserAccount customer,
		String orderNumber,
		String checkoutNumber,
		long amount,
		Instant deliveredAt
	) {
		CustomerOrder order = createSupplierOrderedOrder(customer, orderNumber, checkoutNumber, amount);
		order.markShipped();
		order.markDeliveredByTracking();
		Shipment shipment = new Shipment(order, "CJ대한통운", "TRACK-" + orderNumber, deliveredAt.minusSeconds(3600));
		shipment.markDeliveredByTracking(deliveredAt);
		orderRepository.saveAndFlush(order);
		shipmentRepository.saveAndFlush(shipment);
		return order;
	}

	private CustomerOrder createDeliveredBankTransferOrder(
		UserAccount customer,
		String orderNumber,
		String checkoutNumber,
		long amount,
		Instant deliveredAt
	) {
		CustomerOrder order = createBankTransferApprovedOrder(customer, orderNumber, checkoutNumber, amount);
		order.startSupplierOrderWork(TestAuthentication.ADMIN_ID, Instant.now());
		order.markSupplierOrdered();
		order.markShipped();
		order.markDeliveredByTracking();
		Shipment shipment = new Shipment(order, "CJ대한통운", "TRACK-" + orderNumber, deliveredAt.minusSeconds(3600));
		shipment.markDeliveredByTracking(deliveredAt);
		orderRepository.saveAndFlush(order);
		shipmentRepository.saveAndFlush(shipment);
		return order;
	}

	private void approveRefund(UUID refundId, String reason) throws Exception {
		mockMvc.perform(post("/api/admin/refunds/{refundId}/approve", refundId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "%s"
					}
					""".formatted(reason)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("APPROVED")));
	}

	private String createClaim(
		UserAccount customer,
		CustomerOrder order,
		String claimType,
		String claimReason,
		String customerMemo
	) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/orders/{orderId}/claims", order.getId())
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "claimType": "%s",
					  "claimReason": "%s",
					  "customerMemo": "%s"
					}
					""".formatted(claimType, claimReason, customerMemo)))
			.andExpect(status().isCreated())
			.andReturn();
		return fieldFrom(result, "claimId");
	}

	private CustomerOrder createPaymentPendingOrder(
		UserAccount customer,
		String orderNumber,
		String checkoutNumber,
		long amount
	) {
		Supplier supplier = supplierRepository.save(new Supplier(
			"Supplier " + orderNumber,
			"Manager",
			"010-0000-0000",
			orderNumber + "@supplier.example",
			null
		));
		Product product = productRepository.save(new Product(
			supplier,
			"Order Product " + orderNumber,
			"Order Product Summary",
			amount,
			ProductStatus.ACTIVE
		));
		ProductOption option = productOptionRepository.save(new ProductOption(product, "Default", 0, ProductOptionStatus.ACTIVE));
		PaymentGroup paymentGroup = paymentGroupRepository.save(new PaymentGroup(
			checkoutNumber,
			customer,
			amount,
			Instant.now().plusSeconds(1800)
		));
		CustomerOrder order = orderRepository.save(new CustomerOrder(
			orderNumber,
			customer,
			supplier,
			paymentGroup,
			new ShippingAddressSnapshot("Receiver", "010-1111-2222", "12345", "Seoul test road", "101"),
			amount,
			paymentGroup.getExpiresAt()
		));
		orderItemRepository.save(new OrderItem(order, product, option, 1, 1));
		return order;
	}

	private String fieldFrom(MvcResult result, String fieldName) throws Exception {
		String body = result.getResponse().getContentAsString();
		String marker = "\"" + fieldName + "\":\"";
		int start = body.indexOf(marker);
		if (start < 0) {
			throw new IllegalStateException("Field not found: " + fieldName + " in " + body);
		}
		int valueStart = start + marker.length();
		int valueEnd = body.indexOf("\"", valueStart);
		return body.substring(valueStart, valueEnd);
	}
}
