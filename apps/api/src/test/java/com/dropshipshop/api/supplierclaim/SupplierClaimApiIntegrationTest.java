package com.dropshipshop.api.supplierclaim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.dropshipshop.api.auth.security.AuthenticatedUser;
import com.dropshipshop.api.catalog.domain.InventoryMode;
import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductCategory;
import com.dropshipshop.api.catalog.domain.ProductManagementChannel;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductReviewStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierAvailability;
import com.dropshipshop.api.catalog.domain.SupplierStatus;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.claim.domain.Claim;
import com.dropshipshop.api.claim.domain.ClaimReason;
import com.dropshipshop.api.claim.domain.ClaimStatus;
import com.dropshipshop.api.claim.domain.ClaimType;
import com.dropshipshop.api.claim.domain.RequestedAction;
import com.dropshipshop.api.claim.repository.ClaimRepository;
import com.dropshipshop.api.fulfillment.domain.Fulfillment;
import com.dropshipshop.api.fulfillment.domain.FulfillmentOperationalOwner;
import com.dropshipshop.api.fulfillment.repository.FulfillmentRepository;
import com.dropshipshop.api.notification.NotificationLogRepository;
import com.dropshipshop.api.notification.domain.NotificationType;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItem;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.domain.ShippingAddressSnapshot;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderItemRepository;
import com.dropshipshop.api.payment.domain.PaymentGroup;
import com.dropshipshop.api.payment.repository.PaymentGroupRepository;
import com.dropshipshop.api.refund.domain.Refund;
import com.dropshipshop.api.refund.domain.RefundReason;
import com.dropshipshop.api.refund.repository.RefundRepository;
import com.dropshipshop.api.shipment.repository.ShipmentRepository;
import com.dropshipshop.api.supplierclaim.domain.SupplierClaimInstructionCode;
import com.dropshipshop.api.supplierclaim.domain.SupplierClaimRequestedType;
import com.dropshipshop.api.supplierclaim.domain.SupplierClaimTask;
import com.dropshipshop.api.supplierclaim.domain.SupplierClaimTaskCloseReasonCode;
import com.dropshipshop.api.supplierclaim.domain.SupplierClaimTaskStatus;
import com.dropshipshop.api.supplierclaim.repository.SupplierClaimFactRepository;
import com.dropshipshop.api.supplierclaim.repository.SupplierClaimTaskRepository;
import com.dropshipshop.api.supplierclaim.repository.SupplierShortageReportRepository;
import com.dropshipshop.api.supplierportal.SupplierPortalProperties;
import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.repository.UserAccountRepository;

import jakarta.persistence.EntityManagerFactory;

@SpringBootTest(properties = {
	"app.supplier-portal.enabled=true",
	"app.cors.allowed-origins=http://localhost:3000",
	"spring.jpa.properties.hibernate.generate_statistics=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SupplierClaimApiIntegrationTest {

	private static final String ORIGIN = "http://localhost:3000";

	@Autowired MockMvc mockMvc;
	@Autowired UserAccountRepository userRepository;
	@Autowired SupplierRepository supplierRepository;
	@Autowired ProductRepository productRepository;
	@Autowired ProductOptionRepository optionRepository;
	@Autowired PaymentGroupRepository paymentGroupRepository;
	@Autowired CustomerOrderRepository orderRepository;
	@Autowired OrderItemRepository orderItemRepository;
	@Autowired FulfillmentRepository fulfillmentRepository;
	@Autowired ClaimRepository claimRepository;
	@Autowired RefundRepository refundRepository;
	@Autowired ShipmentRepository shipmentRepository;
	@Autowired SupplierShortageReportRepository shortageRepository;
	@Autowired SupplierClaimTaskRepository taskRepository;
	@Autowired SupplierClaimFactRepository factRepository;
	@Autowired NotificationLogRepository notificationRepository;
	@Autowired SupplierPortalProperties supplierPortalProperties;
	@Autowired SupplierClaimTaskDeadlineScheduler deadlineScheduler;
	@Autowired EntityManagerFactory entityManagerFactory;

	@Test
	void reportsAndApprovesWholeOrderShortageWithSafeIdempotentResponses() throws Exception {
		Fixture fixture = portalOrder("shortage-approve");

		mockMvc.perform(get("/api/supplier/orders/{orderNumber}/shipments", fixture.order().getOrderNumber())
				.with(authentication(supplier(fixture.manager().getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.canReportShortage", is(true)));

		mockMvc.perform(post("/api/admin/orders/{orderId}/out-of-stock", fixture.order().getId())
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"Bypass portal shortage\"}"))
			.andExpect(status().isConflict());

		mockMvc.perform(post("/api/supplier/orders/{orderNumber}/shortage-reports", fixture.order().getOrderNumber())
				.header("Idempotency-Key", "shortage-submit-key-1")
				.with(authentication(supplier(fixture.manager().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reasonCode\":\"OUT_OF_STOCK\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code", is("ORIGIN_NOT_ALLOWED")));

		MvcResult submitted = supplierPost(
			fixture, "/api/supplier/orders/{orderNumber}/shortage-reports", "shortage-submit-key-1",
			"{\"reasonCode\":\"OUT_OF_STOCK\"}", fixture.order().getOrderNumber())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("REPORTED")))
			.andExpect(jsonPath("$.nextAction", is("WAIT")))
			.andExpect(jsonPath("$.orderDetailAvailable").doesNotExist())
			.andExpect(jsonPath("$.recipient").doesNotExist())
			.andExpect(jsonPath("$.claimId").doesNotExist())
			.andReturn();
		String reportId = com.jayway.jsonpath.JsonPath.read(
			submitted.getResponse().getContentAsString(), "$.reportId");

		assertThat(orderRepository.findById(fixture.order().getId()).orElseThrow().getStatus())
			.isEqualTo(OrderStatus.SUPPLIER_ORDER_PENDING);
		assertThat(refundRepository.findByOrder_Id(fixture.order().getId())).isEmpty();
		assertThat(fulfillmentRepository.findByOrder_Id(fixture.order().getId()).orElseThrow()
			.getOperationalOwner()).isEqualTo(FulfillmentOperationalOwner.COREABLE);

		supplierPost(fixture, "/api/supplier/orders/{orderNumber}/shortage-reports", "shortage-submit-key-1",
			"{\"reasonCode\":\"OUT_OF_STOCK\"}", fixture.order().getOrderNumber())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reportId", is(reportId)))
			.andExpect(jsonPath("$.status", is("REPORTED")));
		supplierPost(fixture, "/api/supplier/orders/{orderNumber}/shortage-reports", "shortage-submit-key-1",
			"{\"reasonCode\":\"OPTION_UNAVAILABLE\"}", fixture.order().getOrderNumber())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("IDEMPOTENCY_CONFLICT")));
		supplierPost(fixture, "/api/supplier/orders/{orderNumber}/shortage-reports", "shortage-submit-key-2",
			"{\"reasonCode\":\"OUT_OF_STOCK\"}", fixture.order().getOrderNumber())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("SHORTAGE_ALREADY_REPORTED")));

		mockMvc.perform(get("/api/admin/supplier-shortage-reports")
				.param("status", "REPORTED")
				.param("orderId", fixture.order().getId().toString())
				.with(authentication(admin(fixture.admin().getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reports", hasSize(1)))
			.andExpect(jsonPath("$.reports[0].reportId", is(reportId)))
			.andExpect(jsonPath("$.reports[0].supplierName", is(fixture.supplier().getName())));

		String approval = "{\"expectedStatus\":\"REPORTED\",\"reviewReasonCode\":\"SHORTAGE_CONFIRMED\"}";
		mockMvc.perform(post("/api/admin/supplier-shortage-reports/{reportId}/approve", reportId)
				.header("Idempotency-Key", "shortage-review-key-1")
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(approval))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code", is("ORIGIN_NOT_ALLOWED")));
		adminPost(fixture, "/api/admin/supplier-shortage-reports/{id}/approve", reportId,
			"shortage-review-key-1", approval)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("APPROVED")))
			.andExpect(jsonPath("$.reviewReasonCode", is("SHORTAGE_CONFIRMED")));
		adminPost(fixture, "/api/admin/supplier-shortage-reports/{id}/approve", reportId,
			"shortage-review-key-1", approval)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("APPROVED")));

		assertThat(orderRepository.findById(fixture.order().getId()).orElseThrow().getStatus())
			.isEqualTo(OrderStatus.OUT_OF_STOCK);
		assertThat(refundRepository.findByOrder_Id(fixture.order().getId())).isPresent();
		assertThat(shortageRepository.findById(UUID.fromString(reportId)).orElseThrow().getStatus().name())
			.isEqualTo("APPROVED");
	}

	@Test
	void rejectsUnknownFieldsAndAnyShipmentIncludingVoidedBlocksShortage() throws Exception {
		Fixture fixture = portalOrder("shortage-shipment-ever");
		supplierPost(fixture, "/api/supplier/orders/{orderNumber}/shortage-reports", "shortage-unknown-1",
			"{\"reasonCode\":\"OUT_OF_STOCK\",\"memo\":\"secret\"}", fixture.order().getOrderNumber())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

		MvcResult shipment = supplierPost(fixture, "/api/supplier/orders/{orderNumber}/shipments",
			"shipment-before-shortage-1", "{\"carrierCode\":\"CJ_LOGISTICS\",\"trackingNumber\":\"1234567890\"}",
			fixture.order().getOrderNumber())
			.andExpect(status().isOk()).andReturn();
		String shipmentId = com.jayway.jsonpath.JsonPath.read(
			shipment.getResponse().getContentAsString(), "$.shipmentId");
		adminPost(fixture, "/api/admin/shipments/{id}/void", shipmentId, "shipment-void-before-shortage-1",
			"{\"expectedVersion\":0,\"reason\":\"Allocation must be replaced\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("VOIDED")));

		supplierPost(fixture, "/api/supplier/orders/{orderNumber}/shortage-reports", "shortage-after-void-1",
			"{\"reasonCode\":\"QUANTITY_UNAVAILABLE\"}", fixture.order().getOrderNumber())
			.andExpect(status().isConflict());
		assertThat(shortageRepository.findByOrder_Id(fixture.order().getId())).isEmpty();

		Fixture nonPending = portalOrder("shortage-non-pending");
		ReflectionTestUtils.setField(nonPending.order(), "status", OrderStatus.SUPPLIER_ORDERED);
		orderRepository.saveAndFlush(nonPending.order());
		mockMvc.perform(get("/api/supplier/orders/{orderNumber}/shipments", nonPending.order().getOrderNumber())
				.with(authentication(supplier(nonPending.manager().getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.canReportShortage", is(false)));
		supplierPost(nonPending, "/api/supplier/orders/{orderNumber}/shortage-reports", "shortage-non-pending-1",
			"{\"reasonCode\":\"OUT_OF_STOCK\"}", nonPending.order().getOrderNumber())
			.andExpect(status().isConflict());
	}

	@Test
	void rejectsReportedShortageWithoutRefundAndKeepsAdminReviewAvailableWhenFlagIsOff() throws Exception {
		Fixture fixture = portalOrder("shortage-reject");
		MvcResult submitted = supplierPost(
			fixture, "/api/supplier/orders/{orderNumber}/shortage-reports", "shortage-reject-submit-1",
			"{\"reasonCode\":\"OPTION_UNAVAILABLE\"}", fixture.order().getOrderNumber())
			.andExpect(status().isOk())
			.andReturn();
		String reportId = com.jayway.jsonpath.JsonPath.read(
			submitted.getResponse().getContentAsString(), "$.reportId");

		adminPost(fixture, "/api/admin/supplier-shortage-reports/{id}/reject", reportId,
			"shortage-reject-unknown-1",
			"{\"expectedStatus\":\"REPORTED\",\"reviewReasonCode\":\"FULFILLMENT_CAN_CONTINUE\",\"memo\":\"free text\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
		mockMvc.perform(get("/api/admin/supplier-shortage-reports")
				.param("status", "NOT_A_STATUS")
				.with(authentication(admin(fixture.admin().getId()))))
			.andExpect(status().isBadRequest());

		boolean enabled = supplierPortalProperties.enabled();
		try {
			ReflectionTestUtils.setField(supplierPortalProperties, "enabled", false);
			mockMvc.perform(get("/api/supplier/shortage-reports/{reportId}", reportId)
					.with(authentication(supplier(fixture.manager().getId()))))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")));
			mockMvc.perform(get("/api/admin/supplier-shortage-reports/{reportId}", reportId)
					.with(authentication(admin(fixture.admin().getId()))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("REPORTED")));
			adminPost(fixture, "/api/admin/supplier-shortage-reports/{id}/reject", reportId,
				"shortage-reject-review-1",
				"{\"expectedStatus\":\"REPORTED\",\"reviewReasonCode\":\"FULFILLMENT_CAN_CONTINUE\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("REJECTED")))
				.andExpect(jsonPath("$.reviewReasonCode", is("FULFILLMENT_CAN_CONTINUE")));
		} finally {
			ReflectionTestUtils.setField(supplierPortalProperties, "enabled", enabled);
		}

		assertThat(orderRepository.findById(fixture.order().getId()).orElseThrow().getStatus())
			.isEqualTo(OrderStatus.SUPPLIER_ORDER_PENDING);
		assertThat(refundRepository.findByOrder_Id(fixture.order().getId())).isEmpty();
		assertThat(fulfillmentRepository.findByOrder_Id(fixture.order().getId()).orElseThrow()
			.getOperationalOwner()).isEqualTo(FulfillmentOperationalOwner.COREABLE);
	}

	@Test
	void enforcesTaskTypeInstructionTemplatesDeadlineListsAndRoleBoundaries() throws Exception {
		Fixture fixture = portalOrder("task-contract");
		Claim claim = claim(fixture, "task contract secret");
		String[][] mappings = {
			{"SHIPMENT_STOP_RESULT", "CHECK_SHIPMENT_STOP", "상품 발송을 멈출 수 있는지 확인해 주세요."},
			{"RETURN_INSTRUCTIONS", "PROVIDE_RETURN_METHOD", "반품 수거 방법을 선택해 주세요."},
			{"RETURN_RECEIVED", "CONFIRM_RETURN_RECEIPT", "반품 상품 수령 여부를 확인해 주세요."},
			{"INSPECTION_RESULT", "INSPECT_RETURNED_ITEM", "반품 상품의 상태를 확인해 주세요."}
		};
		for (int index = 0; index < mappings.length; index++) {
			String[] mapping = mappings[index];
			adminPost(fixture, "/api/admin/claims/{id}/supplier-tasks", claim.getId().toString(),
				"task-contract-create-" + index,
				"{\"requestedType\":\"%s\",\"instructionCode\":\"%s\",\"dueAt\":\"%s\"}"
					.formatted(mapping[0], mapping[1], Instant.now().plus(index + 1L, ChronoUnit.DAYS)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.requestedType", is(mapping[0])))
				.andExpect(jsonPath("$.instructionCode", is(mapping[1])))
				.andExpect(jsonPath("$.instructions", is(mapping[2])))
				.andExpect(jsonPath("$.orderDetailAvailable").doesNotExist());
		}

		adminPost(fixture, "/api/admin/claims/{id}/supplier-tasks", claim.getId().toString(),
			"task-contract-mismatch-1",
			"{\"requestedType\":\"RETURN_RECEIVED\",\"instructionCode\":\"CHECK_SHIPMENT_STOP\",\"dueAt\":\"%s\"}"
				.formatted(Instant.now().plus(1, ChronoUnit.DAYS)))
			.andExpect(status().isBadRequest());
		adminPost(fixture, "/api/admin/claims/{id}/supplier-tasks", claim.getId().toString(),
			"task-contract-past-due-1",
			"{\"requestedType\":\"RETURN_RECEIVED\",\"instructionCode\":\"CONFIRM_RETURN_RECEIPT\",\"dueAt\":\"%s\"}"
				.formatted(Instant.now().minusSeconds(1)))
			.andExpect(status().isBadRequest());
		adminPost(fixture, "/api/admin/claims/{id}/supplier-tasks", claim.getId().toString(),
			"task-contract-too-far-1",
			"{\"requestedType\":\"RETURN_RECEIVED\",\"instructionCode\":\"CONFIRM_RETURN_RECEIPT\",\"dueAt\":\"%s\"}"
				.formatted(Instant.now().plus(31, ChronoUnit.DAYS)))
			.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/supplier/claim-tasks")
				.param("status", "OPEN")
				.with(authentication(supplier(fixture.manager().getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tasks", hasSize(4)))
			.andExpect(jsonPath("$.tasks[0].orderDetailAvailable", is(true)))
			.andExpect(jsonPath("$.tasks[0].facts").doesNotExist());
		mockMvc.perform(get("/api/admin/supplier-claim-tasks")
				.param("status", "OPEN")
				.param("claimId", claim.getId().toString())
				.param("orderId", fixture.order().getId().toString())
				.with(authentication(admin(fixture.admin().getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tasks", hasSize(4)))
			.andExpect(jsonPath("$.tasks[0].orderDetailAvailable").doesNotExist())
			.andExpect(jsonPath("$.tasks[0].facts").doesNotExist());
		mockMvc.perform(get("/api/supplier/claim-tasks")
				.param("status", "NOT_A_STATUS")
				.with(authentication(supplier(fixture.manager().getId()))))
			.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/supplier/claim-tasks"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/supplier/claim-tasks")
				.with(authentication(auth(fixture.customer().getId(), UserRole.CUSTOMER, "ROLE_CUSTOMER"))))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/supplier/claim-tasks")
				.with(authentication(admin(fixture.admin().getId()))))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/admin/supplier-claim-tasks")
				.with(authentication(supplier(fixture.manager().getId()))))
			.andExpect(status().isForbidden());
	}

	@Test
	void createsSafeTaskAppendsLinearFactsAndReplaysAcrossCloseAndFeatureGate() throws Exception {
		Fixture fixture = portalOrder("claim-facts");
		Fixture other = portalOrder("claim-facts-other");
		Claim claim = claim(fixture, "customer secret memo");
		Product foreignProduct = new Product(
			other.supplier(), "Foreign supplier item", "Must never cross the task tenant boundary",
			10_000, 12_000, ProductCategory.PPE_WORK_GLOVES, ProductStatus.ACTIVE,
			ProductManagementChannel.SUPPLIER_PORTAL
		);
		foreignProduct.updateReview(ProductReviewStatus.APPROVED, null, null);
		foreignProduct = productRepository.save(foreignProduct);
		ProductOption foreignOption = new ProductOption(
			foreignProduct, "Foreign option", 0, ProductOptionStatus.ACTIVE
		);
		foreignOption.updateInventory(SupplierAvailability.AVAILABLE, InventoryMode.TRACKED, 10L);
		foreignOption = optionRepository.save(foreignOption);
		orderItemRepository.saveAndFlush(new OrderItem(
			fixture.order(), foreignProduct, foreignOption, 1, 1, Instant.now()
		));
		assertThat(orderItemRepository.findAllByOrder_IdOrderByCreatedAtAsc(fixture.order().getId()))
			.hasSize(2);
		assertThat(orderItemRepository.findAllByOrderIdAndSupplierId(
			fixture.order().getId(), fixture.supplier().getId()
		)).singleElement().satisfies(item ->
			assertThat(item.getSupplier().getId()).isEqualTo(fixture.supplier().getId())
		);
		Instant dueAt = Instant.now().plus(2, ChronoUnit.DAYS);
		String createBody = """
			{"requestedType":"INSPECTION_RESULT","instructionCode":"INSPECT_RETURNED_ITEM","dueAt":"%s"}
			""".formatted(dueAt);

		adminPost(fixture, "/api/admin/claims/{id}/supplier-tasks", claim.getId().toString(),
			"claim-task-create-unknown-1", createBody.substring(0, createBody.length() - 2)
				+ ",\"instructions\":\"free text\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

		mockMvc.perform(post("/api/admin/claims/{claimId}/supplier-tasks", claim.getId())
				.header("Idempotency-Key", "claim-task-create-1")
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(createBody))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code", is("ORIGIN_NOT_ALLOWED")));

		MvcResult created = adminPost(fixture, "/api/admin/claims/{id}/supplier-tasks",
			claim.getId().toString(), "claim-task-create-1", createBody)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("OPEN")))
			.andExpect(jsonPath("$.instructionCode", is("INSPECT_RETURNED_ITEM")))
			.andExpect(jsonPath("$.instructions", is("반품 상품의 상태를 확인해 주세요.")))
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.orderDetailAvailable").doesNotExist())
			.andExpect(jsonPath("$.customerMemo").doesNotExist())
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("customer secret memo"))))
			.andReturn();
		String taskId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.taskId");
		assertThat(notificationRepository.findAll().stream()
			.filter(log -> log.getType() == NotificationType.SUPPLIER_CLAIM_WORK_REQUESTED)
			.filter(log -> fixture.order().getId().equals(log.getOrderId()))
			.filter(log -> claim.getId().equals(log.getClaimId()))
			.filter(log -> fixture.supplier().getId().equals(log.getSupplierId()))
			.count()).isEqualTo(1);

		mockMvc.perform(get("/api/supplier/claim-tasks/{taskId}", taskId)
				.with(authentication(supplier(other.manager().getId()))))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/supplier/claim-tasks/{taskId}", taskId)
				.with(authentication(supplier(fixture.manager().getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.orderDetailAvailable", is(true)))
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.facts", hasSize(0)));

		supplierPost(fixture, "/api/supplier/claim-tasks/{taskId}/facts", "claim-fact-bad-payload-1",
			"{\"type\":\"INSPECTION_RESULT\",\"payload\":{\"resultCode\":\"DEFECT_CONFIRMED\",\"inspectedAt\":\"%s\",\"memo\":\"secret\"},\"correctsFactId\":null}"
				.formatted(Instant.now()), taskId)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

		String firstBody = """
			{"type":"INSPECTION_RESULT","payload":{"resultCode":"UNDETERMINED","inspectedAt":"%s"},"correctsFactId":null}
			""".formatted(Instant.now());
		MvcResult first = supplierPost(fixture, "/api/supplier/claim-tasks/{taskId}/facts",
			"claim-fact-first-1", firstBody, taskId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("ANSWERED")))
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.facts", hasSize(1)))
			.andReturn();
		String firstFactId = com.jayway.jsonpath.JsonPath.read(first.getResponse().getContentAsString(), "$.facts[0].factId");
		supplierPost(other, "/api/supplier/claim-tasks/{taskId}/facts", "claim-fact-first-1", firstBody, taskId)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")));

		String correctionBody = """
			{"type":"INSPECTION_RESULT","payload":{"resultCode":"DEFECT_CONFIRMED","inspectedAt":"%s"},"correctsFactId":"%s"}
			""".formatted(Instant.now(), firstFactId);
		MvcResult corrected = supplierPost(fixture, "/api/supplier/claim-tasks/{taskId}/facts",
			"claim-fact-correction-1", correctionBody, taskId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.facts", hasSize(2)))
			.andExpect(jsonPath("$.facts[0].factId", is(firstFactId)))
			.andExpect(jsonPath("$.facts[1].correctsFactId", is(firstFactId)))
			.andReturn();
		String latestFactId = com.jayway.jsonpath.JsonPath.read(
			corrected.getResponse().getContentAsString(), "$.facts[1].factId");
		supplierPost(fixture, "/api/supplier/claim-tasks/{taskId}/facts", "claim-fact-branch-1",
			correctionBody.replace("DEFECT_CONFIRMED", "NO_DEFECT"), taskId)
			.andExpect(status().isConflict());

		String closeBody = "{\"expectedStatus\":\"ANSWERED\",\"closeReasonCode\":\"RESPONSE_ACCEPTED\"}";
		adminPost(fixture, "/api/admin/supplier-claim-tasks/{id}/close", taskId,
			"claim-task-close-unknown-1",
			"{\"expectedStatus\":\"ANSWERED\",\"closeReasonCode\":\"RESPONSE_ACCEPTED\",\"memo\":\"free text\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
		mockMvc.perform(post("/api/admin/supplier-claim-tasks/{taskId}/close", taskId)
				.header(HttpHeaders.REFERER, "https://evil.example/admin")
				.header("Idempotency-Key", "claim-task-close-origin-1")
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(closeBody))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code", is("ORIGIN_NOT_ALLOWED")));
		adminPost(fixture, "/api/admin/supplier-claim-tasks/{id}/close", taskId,
			"claim-task-close-1", closeBody)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("CLOSED")))
			.andExpect(jsonPath("$.closeReasonCode", is("RESPONSE_ACCEPTED")))
			.andExpect(jsonPath("$.orderDetailAvailable").doesNotExist())
			.andExpect(jsonPath("$.facts[1].factId", is(latestFactId)));

		supplierPost(fixture, "/api/supplier/claim-tasks/{taskId}/facts", "claim-fact-first-1", firstBody, taskId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("ANSWERED")));
		supplierPost(fixture, "/api/supplier/claim-tasks/{taskId}/facts", "claim-fact-after-close-1",
			correctionBody.replace(firstFactId, latestFactId), taskId)
			.andExpect(status().isConflict());

		String flagOffCloseTaskId = createTask(fixture, claim, "claim-task-flag-off-close-create-1");
		boolean enabled = supplierPortalProperties.enabled();
		try {
			ReflectionTestUtils.setField(supplierPortalProperties, "enabled", false);
			adminPost(fixture, "/api/admin/claims/{id}/supplier-tasks", claim.getId().toString(),
				"claim-task-create-1", createBody)
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status", is("OPEN")))
					.andExpect(jsonPath("$.orderDetailAvailable").doesNotExist());
			adminPost(fixture, "/api/admin/claims/{id}/supplier-tasks", claim.getId().toString(),
				"claim-task-create-flag-off-2", createBody)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code", is("SUPPLIER_PORTAL_NOT_RELEASED")));
			mockMvc.perform(get("/api/supplier/claim-tasks/{taskId}", flagOffCloseTaskId)
					.with(authentication(supplier(fixture.manager().getId()))))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")));
			mockMvc.perform(get("/api/admin/supplier-claim-tasks/{taskId}", flagOffCloseTaskId)
					.with(authentication(admin(fixture.admin().getId()))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status", is("OPEN")))
					.andExpect(jsonPath("$.orderDetailAvailable").doesNotExist());
			adminPost(fixture, "/api/admin/supplier-claim-tasks/{id}/close", flagOffCloseTaskId,
				"claim-task-flag-off-close-1",
				"{\"expectedStatus\":\"OPEN\",\"closeReasonCode\":\"NO_LONGER_NEEDED\"}")
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status", is("CLOSED")))
					.andExpect(jsonPath("$.orderDetailAvailable").doesNotExist());
		} finally {
			ReflectionTestUtils.setField(supplierPortalProperties, "enabled", enabled);
		}

		assertThat(orderRepository.findById(fixture.order().getId()).orElseThrow().getStatus())
			.isEqualTo(OrderStatus.SUPPLIER_ORDER_PENDING);
		assertThat(claimRepository.findById(claim.getId()).orElseThrow().getStatus())
			.isEqualTo(ClaimStatus.REQUESTED);
		assertThat(refundRepository.findByOrder_Id(fixture.order().getId())).isEmpty();
		assertThat(factRepository.findAllByTask_IdOrderByCreatedAtAscIdAsc(UUID.fromString(taskId)))
			.hasSize(2);
	}

	@Test
	void validatesFactTimeWindowsTopLevelKeysAndReturnInstructionAllowlists() throws Exception {
		Fixture fixture = portalOrder("fact-contract");
		Claim claim = claim(fixture, "fact contract secret");
		String shipmentTaskId = createTask(fixture, claim, "fact-contract-shipment-task-1");
		SupplierClaimTask shipmentTask = taskRepository.findById(UUID.fromString(shipmentTaskId)).orElseThrow();
		String shipmentFactPath = "/api/supplier/claim-tasks/{taskId}/facts";
		supplierPost(fixture, shipmentFactPath, "fact-contract-top-level-1",
			"{\"type\":\"SHIPMENT_STOP_RESULT\",\"payload\":{\"resultCode\":\"STOPPED\",\"checkedAt\":\"%s\"},\"correctsFactId\":null,\"memo\":\"free text\"}"
				.formatted(Instant.now()), shipmentTaskId)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
		supplierPost(fixture, shipmentFactPath, "fact-contract-checked-before-1",
			"{\"type\":\"SHIPMENT_STOP_RESULT\",\"payload\":{\"resultCode\":\"STOPPED\",\"checkedAt\":\"%s\"},\"correctsFactId\":null}"
				.formatted(shipmentTask.getRequestedAt().minusNanos(1)), shipmentTaskId)
			.andExpect(status().isBadRequest());
		supplierPost(fixture, shipmentFactPath, "fact-contract-checked-future-1",
			"{\"type\":\"SHIPMENT_STOP_RESULT\",\"payload\":{\"resultCode\":\"STOPPED\",\"checkedAt\":\"%s\"},\"correctsFactId\":null}"
				.formatted(Instant.now().plus(1, ChronoUnit.DAYS)), shipmentTaskId)
			.andExpect(status().isBadRequest());

		MvcResult inspectionCreated = adminPost(
			fixture, "/api/admin/claims/{id}/supplier-tasks", claim.getId().toString(),
			"fact-contract-inspection-task-1",
			"{\"requestedType\":\"INSPECTION_RESULT\",\"instructionCode\":\"INSPECT_RETURNED_ITEM\",\"dueAt\":\"%s\"}"
				.formatted(Instant.now().plus(1, ChronoUnit.DAYS)))
			.andExpect(status().isOk()).andReturn();
		String inspectionTaskId = com.jayway.jsonpath.JsonPath.read(
			inspectionCreated.getResponse().getContentAsString(), "$.taskId");
		SupplierClaimTask inspectionTask = taskRepository.findById(UUID.fromString(inspectionTaskId)).orElseThrow();
		supplierPost(fixture, shipmentFactPath, "fact-contract-inspected-before-1",
			"{\"type\":\"INSPECTION_RESULT\",\"payload\":{\"resultCode\":\"NO_DEFECT\",\"inspectedAt\":\"%s\"},\"correctsFactId\":null}"
				.formatted(inspectionTask.getRequestedAt().minusNanos(1)), inspectionTaskId)
			.andExpect(status().isBadRequest());
		supplierPost(fixture, shipmentFactPath, "fact-contract-inspected-future-1",
			"{\"type\":\"INSPECTION_RESULT\",\"payload\":{\"resultCode\":\"NO_DEFECT\",\"inspectedAt\":\"%s\"},\"correctsFactId\":null}"
				.formatted(Instant.now().plus(1, ChronoUnit.DAYS)), inspectionTaskId)
			.andExpect(status().isBadRequest());

		MvcResult instructionsCreated = adminPost(
			fixture, "/api/admin/claims/{id}/supplier-tasks", claim.getId().toString(),
			"fact-contract-return-task-1",
			"{\"requestedType\":\"RETURN_INSTRUCTIONS\",\"instructionCode\":\"PROVIDE_RETURN_METHOD\",\"dueAt\":\"%s\"}"
				.formatted(Instant.now().plus(1, ChronoUnit.DAYS)))
			.andExpect(status().isOk()).andReturn();
		String instructionsTaskId = com.jayway.jsonpath.JsonPath.read(
			instructionsCreated.getResponse().getContentAsString(), "$.taskId");
		supplierPost(fixture, shipmentFactPath, "fact-contract-return-method-bad-1",
			"{\"type\":\"RETURN_INSTRUCTIONS\",\"payload\":{\"methodCode\":\"FREE_TEXT\"},\"correctsFactId\":null}",
			instructionsTaskId)
			.andExpect(status().isBadRequest());
		supplierPost(fixture, shipmentFactPath, "fact-contract-return-carrier-bad-1",
			"{\"type\":\"RETURN_INSTRUCTIONS\",\"payload\":{\"methodCode\":\"COURIER_PICKUP\",\"carrierCode\":\"UNKNOWN\"},\"correctsFactId\":null}",
			instructionsTaskId)
			.andExpect(status().isBadRequest());
		supplierPost(fixture, shipmentFactPath, "fact-contract-return-valid-1",
			"{\"type\":\"RETURN_INSTRUCTIONS\",\"payload\":{\"methodCode\":\"COURIER_PICKUP\",\"carrierCode\":\"CJ_LOGISTICS\"},\"correctsFactId\":null}",
			instructionsTaskId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.facts[0].payload.methodCode", is("COURIER_PICKUP")))
			.andExpect(jsonPath("$.facts[0].payload.carrierCode", is("CJ_LOGISTICS")));
	}

	@Test
	void terminalClaimAndLazyDeadlineCloseTasksAtomically() throws Exception {
		Fixture terminalFixture = portalOrder("task-terminal");
		Claim terminalClaim = claim(terminalFixture, "terminal secret");
		String taskId = createTask(terminalFixture, terminalClaim, "terminal-task-create-1");

		mockMvc.perform(post("/api/admin/claims/{claimId}/reject", terminalClaim.getId())
				.with(authentication(admin(terminalFixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"Claim evidence rejected\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("REJECTED")));
		assertThat(taskRepository.findById(UUID.fromString(taskId)).orElseThrow())
			.satisfies(task -> {
				assertThat(task.getStatus()).isEqualTo(SupplierClaimTaskStatus.CLOSED);
				assertThat(task.getCloseReasonCode()).isEqualTo(SupplierClaimTaskCloseReasonCode.CLAIM_TERMINAL);
			});

		Fixture deadlineFixture = portalOrder("task-deadline");
		Claim deadlineClaim = claim(deadlineFixture, "deadline secret");
		Instant now = Instant.now();
		SupplierClaimTask expired = new SupplierClaimTask(
			deadlineClaim, deadlineFixture.supplier(), SupplierClaimRequestedType.RETURN_RECEIVED,
			SupplierClaimInstructionCode.CONFIRM_RETURN_RECEIPT, deadlineFixture.admin().getId(),
			"expired-task-hash", "expired-task-key-1", now.minus(2, ChronoUnit.DAYS),
			now.minus(1, ChronoUnit.DAYS)
		);
		taskRepository.saveAndFlush(expired);
		supplierPost(deadlineFixture, "/api/supplier/claim-tasks/{taskId}/facts", "expired-fact-key-1",
			"{\"type\":\"RETURN_RECEIVED\",\"payload\":{\"resultCode\":\"RECEIVED\",\"checkedAt\":\"%s\"},\"correctsFactId\":null}"
				.formatted(now), expired.getId().toString())
			.andExpect(status().isConflict());
		assertThat(taskRepository.findById(expired.getId()).orElseThrow())
			.satisfies(task -> {
				assertThat(task.getStatus()).isEqualTo(SupplierClaimTaskStatus.CLOSED);
				assertThat(task.getCloseReasonCode()).isEqualTo(SupplierClaimTaskCloseReasonCode.DUE_AT_EXPIRED);
			});

		SupplierClaimTask scheduledExpired = new SupplierClaimTask(
			deadlineClaim, deadlineFixture.supplier(), SupplierClaimRequestedType.RETURN_RECEIVED,
			SupplierClaimInstructionCode.CONFIRM_RETURN_RECEIPT, deadlineFixture.admin().getId(),
			"scheduled-expired-task-hash", "scheduled-expired-task-key-1", now.minus(2, ChronoUnit.DAYS),
			now.minus(1, ChronoUnit.DAYS)
		);
		taskRepository.saveAndFlush(scheduledExpired);
		assertThat(deadlineScheduler.closeExpiredAt(now)).isGreaterThanOrEqualTo(1);
		assertThat(taskRepository.findById(scheduledExpired.getId()).orElseThrow())
			.satisfies(task -> {
				assertThat(task.getStatus()).isEqualTo(SupplierClaimTaskStatus.CLOSED);
				assertThat(task.getCloseReasonCode()).isEqualTo(SupplierClaimTaskCloseReasonCode.DUE_AT_EXPIRED);
			});
	}

	@Test
	void keepsTaskAndShortageListQueryCountsConstantAndFailsClosedForPaymentExceptions() throws Exception {
		Fixture manyTaskFixture = portalOrder("task-list-batch-many");
		Claim manyTaskClaim = claim(manyTaskFixture, "many task list claim");
		for (int index = 0; index < 4; index++) {
			adminPost(manyTaskFixture, "/api/admin/claims/{id}/supplier-tasks",
				manyTaskClaim.getId().toString(), "task-list-batch-many-" + index,
				"{\"requestedType\":\"RETURN_RECEIVED\",\"instructionCode\":\"CONFIRM_RETURN_RECEIPT\",\"dueAt\":\"%s\"}"
					.formatted(Instant.now().plus(1, ChronoUnit.DAYS)))
				.andExpect(status().isOk());
		}
		Fixture oneTaskFixture = portalOrder("task-list-batch-one");
		Claim oneTaskClaim = claim(oneTaskFixture, "one task list claim");
		adminPost(oneTaskFixture, "/api/admin/claims/{id}/supplier-tasks",
			oneTaskClaim.getId().toString(), "task-list-batch-one-0",
			"{\"requestedType\":\"RETURN_RECEIVED\",\"instructionCode\":\"CONFIRM_RETURN_RECEIPT\",\"dueAt\":\"%s\"}"
				.formatted(Instant.now().plus(1, ChronoUnit.DAYS)))
			.andExpect(status().isOk());

		var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		mockMvc.perform(get("/api/supplier/claim-tasks")
				.with(authentication(supplier(manyTaskFixture.manager().getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tasks", hasSize(4)));
		long supplierManyTaskStatements = statistics.getPrepareStatementCount();
		statistics.clear();
		mockMvc.perform(get("/api/supplier/claim-tasks")
				.with(authentication(supplier(oneTaskFixture.manager().getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tasks", hasSize(1)));
		long supplierOneTaskStatements = statistics.getPrepareStatementCount();

		statistics.clear();
		mockMvc.perform(get("/api/admin/supplier-claim-tasks")
				.param("claimId", manyTaskClaim.getId().toString())
				.param("orderId", manyTaskFixture.order().getId().toString())
				.with(authentication(admin(manyTaskFixture.admin().getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tasks", hasSize(4)));
		long adminManyTaskStatements = statistics.getPrepareStatementCount();
		statistics.clear();
		mockMvc.perform(get("/api/admin/supplier-claim-tasks")
				.param("claimId", oneTaskClaim.getId().toString())
				.param("orderId", oneTaskFixture.order().getId().toString())
				.with(authentication(admin(oneTaskFixture.admin().getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tasks", hasSize(1)));
		long adminOneTaskStatements = statistics.getPrepareStatementCount();

		assertThat(supplierManyTaskStatements).isEqualTo(supplierOneTaskStatements);
		assertThat(adminManyTaskStatements).isEqualTo(adminOneTaskStatements);

		manyTaskFixture.order().getPaymentGroup().markPaymentException();
		paymentGroupRepository.saveAndFlush(manyTaskFixture.order().getPaymentGroup());
		mockMvc.perform(get("/api/supplier/claim-tasks")
				.with(authentication(supplier(manyTaskFixture.manager().getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tasks[0].orderDetailAvailable", is(false)));
		refundRepository.saveAndFlush(new Refund(oneTaskFixture.order(), RefundReason.LATE_DEPOSIT_EXCEPTION));
		mockMvc.perform(get("/api/supplier/claim-tasks")
				.with(authentication(supplier(oneTaskFixture.manager().getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tasks[0].orderDetailAvailable", is(false)));

		Fixture firstShortage = portalOrder("shortage-list-batch-first");
		Fixture secondShortage = portalOrder("shortage-list-batch-second");
		Fixture thirdShortage = portalOrder("shortage-list-batch-third");
		for (Fixture fixture : List.of(firstShortage, secondShortage, thirdShortage)) {
			supplierPost(fixture, "/api/supplier/orders/{orderNumber}/shortage-reports",
				"shortage-list-batch-" + fixture.order().getId(),
				"{\"reasonCode\":\"OUT_OF_STOCK\"}", fixture.order().getOrderNumber())
				.andExpect(status().isOk());
		}
		statistics.clear();
		mockMvc.perform(get("/api/admin/supplier-shortage-reports")
				.with(authentication(admin(firstShortage.admin().getId()))))
			.andExpect(status().isOk());
		long adminManyShortageStatements = statistics.getPrepareStatementCount();
		statistics.clear();
		mockMvc.perform(get("/api/admin/supplier-shortage-reports")
				.param("orderId", firstShortage.order().getId().toString())
				.with(authentication(admin(firstShortage.admin().getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reports", hasSize(1)));
		long adminOneShortageStatements = statistics.getPrepareStatementCount();
		assertThat(adminManyShortageStatements).isEqualTo(adminOneShortageStatements);
	}

	@Test
	void serializesFirstShipmentAgainstShortageReportForTheSameOrder() throws Exception {
		Fixture fixture = portalOrder("shortage-shipment-race");
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<Integer> shortage = executor.submit(() -> concurrentSupplierPostStatus(
				fixture,
				"/api/supplier/orders/{id}/shortage-reports",
				"shortage-race-key-1",
				"{\"reasonCode\":\"OUT_OF_STOCK\"}",
				fixture.order().getOrderNumber(),
				ready,
				start
			));
			Future<Integer> shipment = executor.submit(() -> concurrentSupplierPostStatus(
				fixture,
				"/api/supplier/orders/{id}/shipments",
				"shipment-race-key-1",
				"{\"carrierCode\":\"CJ_LOGISTICS\",\"trackingNumber\":\"9876543210\"}",
				fixture.order().getOrderNumber(),
				ready,
				start
			));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			List<Integer> statuses = List.of(
				shortage.get(15, TimeUnit.SECONDS),
				shipment.get(15, TimeUnit.SECONDS)
			);
			assertThat(statuses).contains(200);
			assertThat(statuses.stream().filter(status -> status == 200).count()).isEqualTo(1);
			assertThat(statuses.stream().filter(status -> status == 404 || status == 409).count())
				.isEqualTo(1);
		} finally {
			executor.shutdownNow();
		}

		long reportCount = shortageRepository.findByOrder_Id(fixture.order().getId()).stream().count();
		long shipmentCount = shipmentRepository.findAllByOrder_IdOrderByRegisteredAtAscIdAsc(
			fixture.order().getId()).size();
		assertThat(reportCount + shipmentCount).isEqualTo(1);
	}

	@Test
	void serializesConcurrentFirstFactsIntoOneLinearRoot() throws Exception {
		Fixture fixture = portalOrder("claim-fact-race");
		Claim claim = claim(fixture, "race secret");
		String taskId = createTask(fixture, claim, "fact-race-task-create-1");
		Instant checkedAt = Instant.now();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<Integer> first = executor.submit(() -> concurrentSupplierPostStatus(
				fixture,
				"/api/supplier/claim-tasks/{id}/facts",
				"fact-race-key-1",
				"{\"type\":\"SHIPMENT_STOP_RESULT\",\"payload\":{\"resultCode\":\"STOPPED\",\"checkedAt\":\"%s\"},\"correctsFactId\":null}"
					.formatted(checkedAt),
				taskId,
				ready,
				start
			));
			Future<Integer> second = executor.submit(() -> concurrentSupplierPostStatus(
				fixture,
				"/api/supplier/claim-tasks/{id}/facts",
				"fact-race-key-2",
				"{\"type\":\"SHIPMENT_STOP_RESULT\",\"payload\":{\"resultCode\":\"ALREADY_SHIPPED\",\"checkedAt\":\"%s\"},\"correctsFactId\":null}"
					.formatted(checkedAt),
				taskId,
				ready,
				start
			));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			List<Integer> statuses = List.of(
				first.get(15, TimeUnit.SECONDS),
				second.get(15, TimeUnit.SECONDS)
			);
			assertThat(statuses).contains(200);
			assertThat(statuses.stream().filter(status -> status == 200).count()).isEqualTo(1);
			assertThat(statuses.stream().filter(status -> status == 400 || status == 409).count())
				.isEqualTo(1);
		} finally {
			executor.shutdownNow();
		}

		assertThat(factRepository.findAllByTask_IdOrderByCreatedAtAscIdAsc(UUID.fromString(taskId)))
			.singleElement()
			.satisfies(fact -> assertThat(fact.getCorrectsFact()).isNull());
		assertThat(taskRepository.findById(UUID.fromString(taskId)).orElseThrow().getStatus())
			.isEqualTo(SupplierClaimTaskStatus.ANSWERED);
	}

	@Test
	void serializesFactInputAgainstTerminalClaimTransitionWithoutLeavingAnOpenTask() throws Exception {
		Fixture fixture = portalOrder("fact-terminal-race");
		Claim claim = claim(fixture, "terminal race secret");
		String taskId = createTask(fixture, claim, "fact-terminal-race-task-1");
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<Integer> fact = executor.submit(() -> concurrentSupplierPostStatus(
				fixture,
				"/api/supplier/claim-tasks/{id}/facts",
				"fact-terminal-race-input-1",
				"{\"type\":\"SHIPMENT_STOP_RESULT\",\"payload\":{\"resultCode\":\"STOPPED\",\"checkedAt\":\"%s\"},\"correctsFactId\":null}"
					.formatted(Instant.now()),
				taskId,
				ready,
				start
			));
			Future<Integer> rejection = executor.submit(() -> concurrentAdminClaimRejectStatus(
				fixture, claim.getId(), ready, start
			));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			int factStatus = fact.get(15, TimeUnit.SECONDS);
			assertThat(rejection.get(15, TimeUnit.SECONDS)).isEqualTo(200);
			assertThat(factStatus).isIn(200, 409);
		} finally {
			executor.shutdownNow();
		}

		assertThat(claimRepository.findById(claim.getId()).orElseThrow().getStatus())
			.isEqualTo(ClaimStatus.REJECTED);
		assertThat(taskRepository.findById(UUID.fromString(taskId)).orElseThrow())
			.satisfies(task -> {
				assertThat(task.getStatus()).isEqualTo(SupplierClaimTaskStatus.CLOSED);
				assertThat(task.getCloseReasonCode()).isEqualTo(SupplierClaimTaskCloseReasonCode.CLAIM_TERMINAL);
			});
		assertThat(factRepository.findAllByTask_IdOrderByCreatedAtAscIdAsc(UUID.fromString(taskId)).size())
			.isBetween(0, 1);
	}

	private String createTask(Fixture fixture, Claim claim, String key) throws Exception {
		MvcResult result = adminPost(fixture, "/api/admin/claims/{id}/supplier-tasks",
			claim.getId().toString(), key, """
				{"requestedType":"SHIPMENT_STOP_RESULT","instructionCode":"CHECK_SHIPMENT_STOP","dueAt":"%s"}
				""".formatted(Instant.now().plus(1, ChronoUnit.DAYS)))
			.andExpect(status().isOk()).andReturn();
		return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.taskId");
	}

	private Claim claim(Fixture fixture, String memo) {
		return claimRepository.save(new Claim(
			fixture.order(), fixture.customer(), ClaimType.CANCEL, ClaimReason.SIMPLE_CHANGE_OF_MIND,
			ClaimStatus.REQUESTED, RequestedAction.REFUND, memo
		));
	}

	private org.springframework.test.web.servlet.ResultActions supplierPost(
		Fixture fixture,
		String path,
		String key,
		String body,
		Object pathVariable
	) throws Exception {
		return mockMvc.perform(post(path, pathVariable)
			.header(HttpHeaders.ORIGIN, ORIGIN)
			.header("Idempotency-Key", key)
			.with(authentication(supplier(fixture.manager().getId())))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private org.springframework.test.web.servlet.ResultActions adminPost(
		Fixture fixture,
		String path,
		String id,
		String key,
		String body
	) throws Exception {
		return mockMvc.perform(post(path, id)
			.header(HttpHeaders.ORIGIN, ORIGIN)
			.header("Idempotency-Key", key)
			.with(authentication(admin(fixture.admin().getId())))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private int concurrentSupplierPostStatus(
		Fixture fixture,
		String path,
		String key,
		String body,
		Object pathVariable,
		CountDownLatch ready,
		CountDownLatch start
	) throws Exception {
		ready.countDown();
		if (!start.await(5, TimeUnit.SECONDS)) {
			throw new IllegalStateException("Concurrent request start was not released");
		}
		return mockMvc.perform(post(path, pathVariable)
			.header(HttpHeaders.ORIGIN, ORIGIN)
			.header("Idempotency-Key", key)
			.with(authentication(supplier(fixture.manager().getId())))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body))
			.andReturn()
			.getResponse()
			.getStatus();
	}

	private int concurrentAdminClaimRejectStatus(
		Fixture fixture,
		UUID claimId,
		CountDownLatch ready,
		CountDownLatch start
	) throws Exception {
		ready.countDown();
		if (!start.await(5, TimeUnit.SECONDS)) {
			throw new IllegalStateException("Concurrent request start was not released");
		}
		return mockMvc.perform(post("/api/admin/claims/{claimId}/reject", claimId)
			.with(authentication(admin(fixture.admin().getId())))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"Concurrent terminal review\"}"))
			.andReturn()
			.getResponse()
			.getStatus();
	}

	private Fixture portalOrder(String label) {
		String suffix = label + "-" + UUID.randomUUID().toString().substring(0, 8);
		Instant now = Instant.now();
		UserAccount admin = userRepository.save(new UserAccount(
			SocialProvider.KAKAO, "admin-" + suffix, "admin-" + suffix + "@example.com", "Admin", UserRole.ADMIN));
		UserAccount manager = userRepository.save(new UserAccount(
			SocialProvider.KAKAO, "manager-" + suffix, "manager-" + suffix + "@example.com", "Manager", UserRole.CUSTOMER));
		UserAccount customer = userRepository.save(new UserAccount(
			SocialProvider.GOOGLE, "customer-" + suffix, "customer-" + suffix + "@example.com", "Customer", UserRole.CUSTOMER));
		Supplier supplier = Supplier.portalApplicant(
			"Supplier " + suffix, "Manager", "010-2222-3333", manager.getEmail(), null);
		supplier.verifyPortalContract("contract-" + suffix, now.minusSeconds(60),
			now.plus(90, ChronoUnit.DAYS), now, admin.getId());
		supplier.bindManager(manager.getId(), now);
		supplier.changeSalesStatus(SupplierStatus.ACTIVE, now);
		supplier = supplierRepository.save(supplier);
		Product product = new Product(supplier, "Product " + suffix, "Portal product", 10_000, 12_000,
			ProductCategory.PPE_WORK_GLOVES, ProductStatus.ACTIVE, ProductManagementChannel.SUPPLIER_PORTAL);
		product.updateReview(ProductReviewStatus.APPROVED, null, null);
		product = productRepository.save(product);
		ProductOption option = new ProductOption(product, "Default", 0, ProductOptionStatus.ACTIVE);
		option.updateInventory(SupplierAvailability.AVAILABLE, InventoryMode.TRACKED, 10L);
		option = optionRepository.save(option);
		PaymentGroup paymentGroup = new PaymentGroup("P-" + UUID.randomUUID(), customer, 12_000,
			now.plusSeconds(3600));
		paymentGroup.confirmPolicy(now);
		paymentGroup.approve(12_000, now);
		paymentGroup = paymentGroupRepository.save(paymentGroup);
		CustomerOrder order = new CustomerOrder(
			"O-" + UUID.randomUUID(), customer, supplier, paymentGroup,
			new ShippingAddressSnapshot("Receiver", "010-1111-2222", "12345", "Seoul test road", "101"),
			12_000, paymentGroup.getExpiresAt());
		order.markSupplierOrderPending();
		order.lockAddressForSupplierPortal(now);
		order = orderRepository.save(order);
		orderItemRepository.save(new OrderItem(order, product, option, 1, 1, now));
		Fulfillment fulfillment = new Fulfillment(order);
		fulfillment.routeToSupplierPortal(now, now.plus(60, ChronoUnit.DAYS));
		fulfillmentRepository.save(fulfillment);
		return new Fixture(admin, manager, customer, supplier, order);
	}

	private Authentication supplier(UUID userId) {
		return auth(userId, UserRole.CUSTOMER, "ROLE_SUPPLIER");
	}

	private Authentication admin(UUID userId) {
		return auth(userId, UserRole.ADMIN, "ROLE_ADMIN");
	}

	private Authentication auth(UUID userId, UserRole role, String authority) {
		return new UsernamePasswordAuthenticationToken(
			new AuthenticatedUser(userId, role), null, List.of(new SimpleGrantedAuthority(authority))
		);
	}

	private record Fixture(
		UserAccount admin,
		UserAccount manager,
		UserAccount customer,
		Supplier supplier,
		CustomerOrder order
	) {
	}
}
