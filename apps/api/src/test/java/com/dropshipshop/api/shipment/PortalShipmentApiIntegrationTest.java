package com.dropshipshop.api.shipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.dropshipshop.api.refund.repository.RefundRepository;
import com.dropshipshop.api.shipment.domain.Shipment;
import com.dropshipshop.api.shipment.domain.ShipmentActorType;
import com.dropshipshop.api.shipment.domain.ShipmentItem;
import com.dropshipshop.api.shipment.domain.ShipmentStatus;
import com.dropshipshop.api.shipment.repository.ShipmentChangeHistoryRepository;
import com.dropshipshop.api.shipment.repository.ShipmentItemRepository;
import com.dropshipshop.api.shipment.repository.ShipmentRepository;
import com.dropshipshop.api.supplierportal.SupplierPortalProperties;
import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.repository.UserAccountRepository;

@SpringBootTest(properties = {
	"app.supplier-portal.enabled=true",
	"app.cors.allowed-origins=http://localhost:3000"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PortalShipmentApiIntegrationTest {

	@Autowired MockMvc mockMvc;
	@Autowired UserAccountRepository userRepository;
	@Autowired SupplierRepository supplierRepository;
	@Autowired ProductRepository productRepository;
	@Autowired ProductOptionRepository optionRepository;
	@Autowired PaymentGroupRepository paymentGroupRepository;
	@Autowired CustomerOrderRepository orderRepository;
	@Autowired OrderItemRepository orderItemRepository;
	@Autowired FulfillmentRepository fulfillmentRepository;
	@Autowired ShipmentRepository shipmentRepository;
	@Autowired ShipmentItemRepository shipmentItemRepository;
	@Autowired ShipmentChangeHistoryRepository shipmentChangeHistoryRepository;
	@Autowired RefundRepository refundRepository;
	@Autowired NotificationLogRepository notificationLogRepository;
	@Autowired SupplierPortalProperties supplierPortalProperties;

	@Test
	void registersDefaultAllocationAndReplaysOnlyTheSameActorAndBody() throws Exception {
		Fixture fixture = portalOrder("default-allocation");
		String body = """
			{"carrierCode":"CJ_LOGISTICS","trackingNumber":"1234567890"}
			""";

		mockMvc.perform(get("/api/supplier/carriers")
				.with(authentication(supplier(fixture.manager().getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.carriers", hasSize(4)))
			.andExpect(jsonPath("$.carriers[0].officialTrackingSupported", is(true)));
		mockMvc.perform(get("/api/admin/carriers")
				.with(authentication(admin(fixture.admin().getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.carriers", hasSize(4)));
		mockMvc.perform(get("/api/admin/carriers")
				.with(authentication(supplier(fixture.manager().getId()))))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/supplier/orders/{orderNumber}/shipments", fixture.order().getOrderNumber())
				.header(HttpHeaders.ORIGIN, "http://localhost:3000")
				.with(authentication(supplier(fixture.manager().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code", is("MALFORMED_REQUEST")));

		MvcResult created = supplierPost(fixture, "shipment-create-default-1", body)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("TRACKING_REGISTERED")))
			.andExpect(jsonPath("$.allocations", hasSize(2)))
			.andExpect(jsonPath("$.officialTrackingUrl", containsString("cjlogistics.com")))
			.andReturn();
		String shipmentId = com.jayway.jsonpath.JsonPath.read(
			created.getResponse().getContentAsString(), "$.shipmentId");

		mockMvc.perform(post("/api/supplier/orders/{orderNumber}/shipments", fixture.order().getOrderNumber())
				.header(HttpHeaders.ORIGIN, "http://localhost:3000")
				.header("Idempotency-Key", "shipment-create-default-1")
				.with(authentication(supplier(fixture.manager().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.shipmentId", is(shipmentId)));

		supplierPost(fixture, "shipment-create-default-1",
			"{\"carrierCode\":\"CJ_LOGISTICS\",\"trackingNumber\":\"9999999999\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("IDEMPOTENCY_CONFLICT")));

		CustomerOrder stored = orderRepository.findById(fixture.order().getId()).orElseThrow();
		Fulfillment fulfillment = fulfillmentRepository.findByOrder_Id(stored.getId()).orElseThrow();
		assertThat(stored.getStatus()).isEqualTo(OrderStatus.TRACKING_REGISTERED);
		assertThat(shipmentItemRepository.findAllByOrder_IdOrderByOrderItem_IdAsc(stored.getId()))
			.extracting(item -> item.getQuantity()).containsExactlyInAnyOrder(2, 1);
		assertThat(fulfillment.getPiiAccessCutoffAt())
			.isBeforeOrEqualTo(Instant.now().plus(30, ChronoUnit.DAYS).plusSeconds(2));

		mockMvc.perform(get("/api/orders/{orderId}/shipments", stored.getId())
				.with(authentication(customer(fixture.customer().getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.allocationComplete", is(true)))
			.andExpect(jsonPath("$.shipments[0].shipmentId", is(shipmentId)))
			.andExpect(jsonPath("$.shipments[0].displayStatus", is("TRACKING_REGISTERED")));
		mockMvc.perform(get("/api/orders/{orderId}/shipments", stored.getId())
				.with(authentication(customer(fixture.otherCustomer().getId()))))
			.andExpect(status().isNotFound());
	}

	@Test
	void supportsSplitAllocationVoidAndReplacementWithoutExtendingPiiCutoff() throws Exception {
		Fixture fixture = portalOrder("split-void");
		Fixture otherOrder = portalOrder("split-void-other-order");
		String firstBody = """
			{"carrierCode":"LOTTE","trackingNumber":"1111111111","allocations":[
			  {"orderItemId":"%s","quantity":1}
			]}
			""".formatted(fixture.firstItem().getId());
		MvcResult first = supplierPost(fixture, "shipment-split-first-1", firstBody)
			.andExpect(status().isOk()).andReturn();
		String firstShipmentId = com.jayway.jsonpath.JsonPath.read(
			first.getResponse().getContentAsString(), "$.shipmentId");

		supplierPost(fixture, "shipment-split-omitted-1",
			"{\"carrierCode\":\"HANJIN\",\"trackingNumber\":\"2222222222\"}")
			.andExpect(status().isBadRequest());
		supplierPost(fixture, "shipment-split-over-1", """
			{"carrierCode":"HANJIN","trackingNumber":"2222222222","allocations":[
			  {"orderItemId":"%s","quantity":2}
			]}
			""".formatted(fixture.firstItem().getId()))
			.andExpect(status().isConflict());
		supplierPost(fixture, "shipment-split-duplicate-item-1", """
			{"carrierCode":"HANJIN","trackingNumber":"2222222222","allocations":[
			  {"orderItemId":"%s","quantity":1},
			  {"orderItemId":"%s","quantity":1}
			]}
			""".formatted(fixture.firstItem().getId(), fixture.firstItem().getId()))
			.andExpect(status().isBadRequest());
		supplierPost(fixture, "shipment-split-other-order-item-1", """
			{"carrierCode":"HANJIN","trackingNumber":"2222222222","allocations":[
			  {"orderItemId":"%s","quantity":1}
			]}
			""".formatted(otherOrder.firstItem().getId()))
			.andExpect(status().isBadRequest());

		Instant cutoffAfterFirst = fulfillmentRepository.findByOrder_Id(fixture.order().getId())
			.orElseThrow().getPiiAccessCutoffAt();
		mockMvc.perform(post("/api/admin/shipments/{shipmentId}/void", firstShipmentId)
				.header("Idempotency-Key", "shipment-admin-void-1")
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"expectedVersion\":0,\"reason\":\"Duplicate tracking registration\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("VOIDED")));
		assertThat(orderRepository.findById(fixture.order().getId()).orElseThrow().getStatus())
			.isEqualTo(OrderStatus.SUPPLIER_ORDER_PENDING);

		String replacement = """
			{"carrierCode":"KOREA_POST","trackingNumber":"3333333333333","allocations":[
			  {"orderItemId":"%s","quantity":2},
			  {"orderItemId":"%s","quantity":1}
			]}
			""".formatted(fixture.firstItem().getId(), fixture.secondItem().getId());
		supplierPost(fixture, "shipment-split-replace-1", replacement)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.allocations", hasSize(2)));
		assertThat(fulfillmentRepository.findByOrder_Id(fixture.order().getId()).orElseThrow()
			.getPiiAccessCutoffAt()).isBeforeOrEqualTo(cutoffAfterFirst);

		mockMvc.perform(get("/api/supplier/orders/{orderNumber}/shipments", fixture.order().getOrderNumber())
				.with(authentication(supplier(fixture.manager().getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.shipments", hasSize(2)))
			.andExpect(jsonPath("$.shipments[0].countsTowardAllocation", is(false)))
			.andExpect(jsonPath("$.allocationComplete", is(true)));
		mockMvc.perform(get("/api/orders/{orderId}/shipments", fixture.order().getId())
				.with(authentication(customer(fixture.customer().getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.shipments", hasSize(1)));
	}

	@Test
	void serializesConcurrentRegistrationsAndRejectsTheOverAllocation() throws Exception {
		Fixture fixture = portalOrder("concurrent-registration");
		String firstBody = """
			{"carrierCode":"CJ_LOGISTICS","trackingNumber":"4444444444","allocations":[
			  {"orderItemId":"%s","quantity":2}
			]}
			""".formatted(fixture.firstItem().getId());
		String secondBody = """
			{"carrierCode":"LOTTE","trackingNumber":"5555555555","allocations":[
			  {"orderItemId":"%s","quantity":2}
			]}
			""".formatted(fixture.firstItem().getId());
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<Integer> first = executor.submit(() -> concurrentSupplierPostStatus(
				fixture, "shipment-concurrent-first-1", firstBody, ready, start));
			Future<Integer> second = executor.submit(() -> concurrentSupplierPostStatus(
				fixture, "shipment-concurrent-second-1", secondBody, ready, start));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
				.containsExactlyInAnyOrder(200, 409);
		} finally {
			start.countDown();
			executor.shutdownNow();
		}

		assertThat(shipmentRepository.findAllByOrder_IdOrderByRegisteredAtAscIdAsc(fixture.order().getId()))
			.hasSize(1);
		assertThat(shipmentItemRepository.sumNonVoidedQuantityByOrderItemId(fixture.firstItem().getId()))
			.isEqualTo(2);
	}

	@Test
	void deliversOnlyTheCompleteAggregateAndReopensWithCustomerNotification() throws Exception {
		Fixture fixture = portalOrder("delivery-aggregate");
		MvcResult first = supplierPost(fixture, "shipment-delivery-first-1", """
			{"carrierCode":"CJ_LOGISTICS","trackingNumber":"4444444444","allocations":[
			  {"orderItemId":"%s","quantity":2}
			]}
			""".formatted(fixture.firstItem().getId())).andExpect(status().isOk()).andReturn();
		MvcResult second = supplierPost(fixture, "shipment-delivery-second-1", """
			{"carrierCode":"HANJIN","trackingNumber":"5555555555","allocations":[
			  {"orderItemId":"%s","quantity":1}
			]}
			""".formatted(fixture.secondItem().getId())).andExpect(status().isOk()).andReturn();
		String firstId = com.jayway.jsonpath.JsonPath.read(first.getResponse().getContentAsString(), "$.shipmentId");
		String secondId = com.jayway.jsonpath.JsonPath.read(second.getResponse().getContentAsString(), "$.shipmentId");
		Instant firstRegistered = shipmentRepository.findById(UUID.fromString(firstId)).orElseThrow().getRegisteredAt();
		Instant secondRegistered = shipmentRepository.findById(UUID.fromString(secondId)).orElseThrow().getRegisteredAt();

		completeDelivery(fixture, firstId, "delivery-complete-first-1", firstRegistered)
			.andExpect(status().isOk());
		assertThat(orderRepository.findById(fixture.order().getId()).orElseThrow().getStatus())
			.isEqualTo(OrderStatus.TRACKING_REGISTERED);
		MvcResult completed = completeDelivery(fixture, secondId, "delivery-complete-second-1", secondRegistered)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("DELIVERED")))
			.andReturn();
		long deliveredVersion = ((Number) com.jayway.jsonpath.JsonPath.read(
			completed.getResponse().getContentAsString(), "$.version")).longValue();
		assertThat(orderRepository.findById(fixture.order().getId()).orElseThrow().getStatus())
			.isEqualTo(OrderStatus.DELIVERED);

		mockMvc.perform(post("/api/admin/shipments/{shipmentId}/delivery-correction", secondId)
				.header("Idempotency-Key", "delivery-reopen-second-1")
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"expectedVersion":%d,"correctionType":"REOPEN_TRACKING","reason":"Carrier evidence was misread"}
					""".formatted(deliveredVersion)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("TRACKING_REGISTERED")));
		assertThat(orderRepository.findById(fixture.order().getId()).orElseThrow().getStatus())
			.isEqualTo(OrderStatus.TRACKING_REGISTERED);
		assertThat(notificationLogRepository.existsByOrderIdAndType(
			fixture.order().getId(), NotificationType.DELIVERY_STATUS_CORRECTED)).isTrue();
	}

	@Test
	void replaysSupplierCreationAfterTakeoverButRejectsTheSameKeyFromAdmin() throws Exception {
		Fixture fixture = portalOrder("takeover-replay");
		String body = "{\"carrierCode\":\"CJ_LOGISTICS\",\"trackingNumber\":\"6666666666\"}";
		MvcResult created = supplierPost(fixture, "shipment-shared-replay-1", body)
			.andExpect(status().isOk()).andReturn();
		String shipmentId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.shipmentId");

		mockMvc.perform(post("/api/admin/orders/{orderId}/portal-takeover", fixture.order().getId())
				.header("Idempotency-Key", "shipment-takeover-replay-1")
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"COREABLE_FULFILLMENT_TAKEOVER\"}"))
			.andExpect(status().isOk());
		assertThat(fulfillmentRepository.findByOrder_Id(fixture.order().getId()).orElseThrow()
			.getOperationalOwner()).isEqualTo(FulfillmentOperationalOwner.COREABLE);
		mockMvc.perform(get("/api/supplier/orders/{orderNumber}/shipments", fixture.order().getOrderNumber())
				.header(HttpHeaders.ORIGIN, "http://localhost:3000")
				.with(authentication(supplier(fixture.manager().getId()))))
			.andExpect(status().isNotFound());

		supplierPost(fixture, "shipment-shared-replay-1", body)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.shipmentId", is(shipmentId)));
		mockMvc.perform(post("/api/admin/orders/{orderId}/portal-shipments", fixture.order().getId())
				.header("Idempotency-Key", "shipment-shared-replay-1")
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("IDEMPOTENCY_CONFLICT")));
	}

	@Test
	void createsCoreableShipmentAndReplaysStoredResultBeforeReleaseGate() throws Exception {
		Fixture fixture = portalOrder("admin-create-replay");
		mockMvc.perform(post("/api/admin/orders/{orderId}/portal-takeover", fixture.order().getId())
				.header("Idempotency-Key", "admin-create-takeover-1")
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"COREABLE_FULFILLMENT_TAKEOVER\"}"))
			.andExpect(status().isOk());

		String key = "admin-create-shipment-1";
		String body = "{\"carrierCode\":\"CJ_LOGISTICS\",\"trackingNumber\":\"6767676767\"}";
		MvcResult created = mockMvc.perform(post(
				"/api/admin/orders/{orderId}/portal-shipments", fixture.order().getId())
				.header("Idempotency-Key", key)
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("TRACKING_REGISTERED")))
			.andExpect(jsonPath("$.allocations", hasSize(2)))
			.andReturn();
		String createdBody = created.getResponse().getContentAsString();
		UUID shipmentId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(createdBody, "$.shipmentId"));
		Shipment stored = shipmentRepository.findById(shipmentId).orElseThrow();
		assertThat(stored.getRegisteredActorType()).isEqualTo(ShipmentActorType.ADMIN);
		assertThat(stored.getRegisteredByUserId()).isEqualTo(fixture.admin().getId());
		assertThat(stored.getCreationResultSnapshot()).isEqualTo(createdBody);
		assertThat(shipmentItemRepository.findAllByShipment_IdOrderByOrderItem_IdAsc(shipmentId))
			.extracting(ShipmentItem::getQuantity)
			.containsExactlyInAnyOrder(2, 1);
		assertThat(orderRepository.findById(fixture.order().getId()).orElseThrow().getStatus())
			.isEqualTo(OrderStatus.TRACKING_REGISTERED);

		boolean enabledBeforeTest = supplierPortalProperties.enabled();
		try {
			ReflectionTestUtils.setField(supplierPortalProperties, "enabled", false);
			assertThat(supplierPortalProperties.enabled()).isFalse();
			mockMvc.perform(post("/api/admin/orders/{orderId}/portal-shipments", fixture.order().getId())
					.header("Idempotency-Key", key)
					.with(authentication(admin(fixture.admin().getId())))
					.contentType(MediaType.APPLICATION_JSON)
					.content(body))
				.andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(createdBody));
			mockMvc.perform(post("/api/admin/orders/{orderId}/portal-shipments", fixture.order().getId())
					.header("Idempotency-Key", "admin-create-while-gated-1")
					.with(authentication(admin(fixture.admin().getId())))
					.contentType(MediaType.APPLICATION_JSON)
					.content(body))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code", is("SUPPLIER_PORTAL_NOT_RELEASED")));
			mockMvc.perform(patch("/api/admin/shipments/{shipmentId}/tracking-correction", shipmentId)
					.header("Idempotency-Key", "admin-correction-while-gated-1")
					.with(authentication(admin(fixture.admin().getId())))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
						{"expectedVersion":0,"carrierCode":"LOTTE","trackingNumber":"6868686868","reason":"Verified correction while intake is gated"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version", is(1)))
				.andExpect(jsonPath("$.carrierCode", is("LOTTE")));
			mockMvc.perform(post("/api/admin/orders/{orderId}/portal-shipments", fixture.order().getId())
					.header("Idempotency-Key", key)
					.with(authentication(admin(fixture.admin().getId())))
					.contentType(MediaType.APPLICATION_JSON)
					.content(body))
				.andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(createdBody));
		} finally {
			ReflectionTestUtils.setField(supplierPortalProperties, "enabled", enabledBeforeTest);
		}

		assertThat(supplierPortalProperties.enabled()).isTrue();
		assertThat(shipmentRepository.findAllByOrder_IdOrderByRegisteredAtAscIdAsc(fixture.order().getId()))
			.extracting(Shipment::getId)
			.containsExactly(shipmentId);
	}

	@Test
	void correctsTrackingWithVersionedActorSafeReplay() throws Exception {
		Fixture fixture = portalOrder("tracking-correction");
		MvcResult created = supplierPost(fixture, "shipment-correction-create-1",
			"{\"carrierCode\":\"CJ_LOGISTICS\",\"trackingNumber\":\"7777777777\"}")
			.andExpect(status().isOk()).andReturn();
		String shipmentId = com.jayway.jsonpath.JsonPath.read(
			created.getResponse().getContentAsString(), "$.shipmentId");
		String firstBody = """
			{"expectedVersion":0,"carrierCode":"LOTTE","trackingNumber":"8888888888","reason":"Corrected carrier selection"}
			""";
		supplierPatch(fixture, shipmentId, "shipment-correction-missing-version-1", """
			{"carrierCode":"LOTTE","trackingNumber":"8888888888","reason":"Corrected carrier selection"}
			""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
		MvcResult firstCorrection = supplierPatch(fixture, shipmentId, "shipment-correction-first-1", firstBody)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version", is(1)))
			.andExpect(jsonPath("$.carrierCode", is("LOTTE")))
			.andReturn();
		String secondBody = """
			{"expectedVersion":1,"carrierCode":"HANJIN","trackingNumber":"9999999999","reason":"Corrected tracking entry"}
			""";
		supplierPatch(fixture, shipmentId, "shipment-correction-second-1", secondBody)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version", is(2)));

		UUID storedShipmentId = UUID.fromString(shipmentId);
		Shipment beforeStaleRequest = shipmentRepository.findById(storedShipmentId).orElseThrow();
		long versionBeforeStaleRequest = beforeStaleRequest.getVersion();
		String carrierBeforeStaleRequest = beforeStaleRequest.getCarrierCode();
		String trackingBeforeStaleRequest = beforeStaleRequest.getTrackingNumber();
		int historyCountBeforeStaleRequest = shipmentChangeHistoryRepository
			.findAllByShipment_IdOrderByCreatedAtAscIdAsc(storedShipmentId).size();
		supplierPatch(fixture, shipmentId, "shipment-correction-stale-new-key-1", """
			{"expectedVersion":1,"carrierCode":"KOREA_POST","trackingNumber":"1010101010101","reason":"Stale correction must not apply"}
			""")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("CONFLICT")));
		Shipment afterStaleRequest = shipmentRepository.findById(storedShipmentId).orElseThrow();
		assertThat(afterStaleRequest.getVersion()).isEqualTo(versionBeforeStaleRequest);
		assertThat(afterStaleRequest.getCarrierCode()).isEqualTo(carrierBeforeStaleRequest);
		assertThat(afterStaleRequest.getTrackingNumber()).isEqualTo(trackingBeforeStaleRequest);
		assertThat(shipmentChangeHistoryRepository
			.findAllByShipment_IdOrderByCreatedAtAscIdAsc(storedShipmentId)).hasSize(historyCountBeforeStaleRequest);
		assertThat(shipmentChangeHistoryRepository.findByShipment_IdAndIdempotencyKey(
			storedShipmentId, "shipment-correction-stale-new-key-1")).isEmpty();

		supplierPatch(fixture, shipmentId, "shipment-correction-first-1", firstBody)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version", is(1)))
			.andExpect(jsonPath("$.carrierCode", is("LOTTE")))
			.andExpect(result -> assertThat(result.getResponse().getContentAsString())
				.isEqualTo(firstCorrection.getResponse().getContentAsString()));
		mockMvc.perform(patch("/api/admin/shipments/{shipmentId}/tracking-correction", shipmentId)
				.header("Idempotency-Key", "shipment-correction-first-1")
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"expectedVersion":2,"carrierCode":"KOREA_POST","trackingNumber":"1111111111111","reason":"Admin verified correction"}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("IDEMPOTENCY_CONFLICT")));
		supplierPatch(fixture, shipmentId, "shipment-correction-pii-1", """
			{"expectedVersion":2,"carrierCode":"HANJIN","trackingNumber":"9999999999","reason":"customer test@example.com"}
			""")
			.andExpect(status().isBadRequest());
	}

	@Test
	void projectsPluralShipmentsWithoutBreakingTheEarliestActiveLegacyField() throws Exception {
		Fixture fixture = portalOrder("plural-compatibility");
		Instant firstRegisteredAt = Instant.now().minus(3, ChronoUnit.HOURS);
		Shipment first = Shipment.portal(
			fixture.order(), "CJ_LOGISTICS", "CJ대한통운", "COMPAT-FIRST", firstRegisteredAt,
			fixture.manager().getId(), ShipmentActorType.SUPPLIER, "compat-first-key", "compat-first-hash");
		first.storeCreationResult("{}");
		first = shipmentRepository.saveAndFlush(first);
		Shipment second = Shipment.portal(
			fixture.order(), "HANJIN", "한진택배", "COMPAT-SECOND",
			firstRegisteredAt.plus(1, ChronoUnit.HOURS), fixture.manager().getId(), ShipmentActorType.SUPPLIER,
			"compat-second-key", "compat-second-hash");
		second.storeCreationResult("{}");
		second = shipmentRepository.saveAndFlush(second);
		Shipment voided = Shipment.portal(
			fixture.order(), "LOTTE", "롯데택배", "COMPAT-VOIDED",
			firstRegisteredAt.minus(1, ChronoUnit.HOURS), fixture.admin().getId(), ShipmentActorType.ADMIN,
			"compat-voided-key", "compat-voided-hash");
		voided.voidShipment();
		voided.storeCreationResult("{}");
		voided = shipmentRepository.saveAndFlush(voided);
		shipmentItemRepository.saveAllAndFlush(List.of(
			new ShipmentItem(first, fixture.firstItem(), 2),
			new ShipmentItem(second, fixture.secondItem(), 1),
			new ShipmentItem(voided, fixture.firstItem(), 1)
		));
		fixture.order().markTrackingRegistered();
		orderRepository.saveAndFlush(fixture.order());

		mockMvc.perform(get("/api/orders/{orderId}", fixture.order().getId())
				.with(authentication(customer(fixture.customer().getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.shipment.trackingNumber", is("COMPAT-FIRST")))
			.andExpect(jsonPath("$.shipments", hasSize(2)))
			.andExpect(jsonPath("$.shipments[0].trackingNumber", is("COMPAT-FIRST")))
			.andExpect(jsonPath("$.shipments[1].trackingNumber", is("COMPAT-SECOND")))
			.andExpect(jsonPath("$.shipments[?(@.displayStatus == 'VOIDED')]", hasSize(0)))
			.andExpect(jsonPath("$.shipmentAllocationComplete", is(true)))
			.andExpect(jsonPath("$.shipmentCompatibilityTruncated", is(true)));

		mockMvc.perform(get("/api/admin/orders/{orderId}", fixture.order().getId())
				.with(authentication(admin(fixture.admin().getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.shipment.trackingNumber", is("COMPAT-FIRST")))
			.andExpect(jsonPath("$.shipments", hasSize(3)))
			.andExpect(jsonPath("$.shipments[0].trackingNumber", is("COMPAT-VOIDED")))
			.andExpect(jsonPath("$.shipments[1].trackingNumber", is("COMPAT-FIRST")))
			.andExpect(jsonPath("$.shipments[2].trackingNumber", is("COMPAT-SECOND")))
			.andExpect(jsonPath("$.shipments[?(@.status == 'VOIDED')]", hasSize(1)))
			.andExpect(jsonPath("$.shipmentAllocationComplete", is(true)))
			.andExpect(jsonPath("$.shipmentCompatibilityTruncated", is(true)));
	}

	@Test
	void requiresExpectedVersionForEveryVersionedAdminAction() throws Exception {
		Fixture fixture = portalOrder("required-action-version");
		MvcResult created = supplierPost(fixture, "required-version-create-1",
			"{\"carrierCode\":\"CJ_LOGISTICS\",\"trackingNumber\":\"1010101010\"}")
			.andExpect(status().isOk()).andReturn();
		String shipmentId = com.jayway.jsonpath.JsonPath.read(
			created.getResponse().getContentAsString(), "$.shipmentId");
		Instant registeredAt = shipmentRepository.findById(UUID.fromString(shipmentId)).orElseThrow().getRegisteredAt();
		Instant observedAt = registeredAt.plusSeconds(1);

		mockMvc.perform(post("/api/admin/shipments/{shipmentId}/void", shipmentId)
				.header("Idempotency-Key", "required-version-void-1")
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"Duplicate registration\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
		mockMvc.perform(post("/api/admin/shipments/{shipmentId}/delivery-complete", shipmentId)
				.header("Idempotency-Key", "required-version-delivery-1")
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"deliveredAt":"%s","evidenceObservedAt":"%s","reason":"Verified carrier evidence"}
					""".formatted(registeredAt, observedAt)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
		mockMvc.perform(post("/api/admin/shipments/{shipmentId}/delivery-correction", shipmentId)
				.header("Idempotency-Key", "required-version-correction-1")
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"correctionType":"REOPEN_TRACKING","reason":"Carrier evidence was misread"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
	}

	@Test
	void handsExpiredSupplierMutationToCoreableWithoutCreatingShipment() throws Exception {
		Fixture fixture = portalOrder("cutoff-handover");
		Fulfillment fulfillment = fulfillmentRepository.findByOrder_Id(fixture.order().getId()).orElseThrow();
		Instant now = Instant.now();
		fulfillment.routeToSupplierPortal(now.minus(2, ChronoUnit.DAYS), now.minusSeconds(1));
		fulfillmentRepository.saveAndFlush(fulfillment);

		supplierPost(fixture, "shipment-after-cutoff-1",
			"{\"carrierCode\":\"CJ_LOGISTICS\",\"trackingNumber\":\"1212121212\"}")
			.andExpect(status().isConflict());

		assertThat(fulfillmentRepository.findByOrder_Id(fixture.order().getId()).orElseThrow()
			.getOperationalOwner()).isEqualTo(FulfillmentOperationalOwner.COREABLE);
		assertThat(shipmentRepository.findAllByOrder_IdOrderByRegisteredAtAscIdAsc(fixture.order().getId()))
			.isEmpty();
	}

	@Test
	void blocksDeliveryCorrectionAfterPaymentGroupRefundEvidence() throws Exception {
		Fixture fixture = portalOrder("delivery-refund-guard");
		MvcResult created = supplierPost(fixture, "shipment-refund-create-1",
			"{\"carrierCode\":\"CJ_LOGISTICS\",\"trackingNumber\":\"1313131313\"}")
			.andExpect(status().isOk()).andReturn();
		String shipmentId = com.jayway.jsonpath.JsonPath.read(
			created.getResponse().getContentAsString(), "$.shipmentId");
		Instant registeredAt = shipmentRepository.findById(UUID.fromString(shipmentId)).orElseThrow().getRegisteredAt();
		MvcResult completed = completeDelivery(fixture, shipmentId, "shipment-refund-complete-1", registeredAt)
			.andExpect(status().isOk()).andReturn();
		long version = ((Number) com.jayway.jsonpath.JsonPath.read(
			completed.getResponse().getContentAsString(), "$.version")).longValue();
		refundRepository.saveAndFlush(Refund.receivedPaymentGroup(
			fixture.order().getPaymentGroup(), null, 1, Instant.now()));

		mockMvc.perform(post("/api/admin/shipments/{shipmentId}/delivery-correction", shipmentId)
				.header("Idempotency-Key", "shipment-refund-reopen-1")
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"expectedVersion":%d,"correctionType":"REOPEN_TRACKING","reason":"Carrier evidence was misread"}
					""".formatted(version)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message", containsString("claim or refund")));
	}

	private org.springframework.test.web.servlet.ResultActions supplierPost(
		Fixture fixture,
		String key,
		String body
	) throws Exception {
		return mockMvc.perform(post("/api/supplier/orders/{orderNumber}/shipments", fixture.order().getOrderNumber())
			.header(HttpHeaders.ORIGIN, "http://localhost:3000")
			.header("Idempotency-Key", key)
			.with(authentication(supplier(fixture.manager().getId())))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private org.springframework.test.web.servlet.ResultActions supplierPatch(
		Fixture fixture,
		String shipmentId,
		String key,
		String body
	) throws Exception {
		return mockMvc.perform(patch(
				"/api/supplier/orders/{orderNumber}/shipments/{shipmentId}",
				fixture.order().getOrderNumber(), shipmentId)
			.header(HttpHeaders.ORIGIN, "http://localhost:3000")
			.header("Idempotency-Key", key)
			.with(authentication(supplier(fixture.manager().getId())))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private int concurrentSupplierPostStatus(
		Fixture fixture,
		String key,
		String body,
		CountDownLatch ready,
		CountDownLatch start
	) throws Exception {
		ready.countDown();
		if (!start.await(5, TimeUnit.SECONDS)) {
			throw new AssertionError("Concurrent shipment registration start was not released");
		}
		return supplierPost(fixture, key, body).andReturn().getResponse().getStatus();
	}

	private org.springframework.test.web.servlet.ResultActions completeDelivery(
		Fixture fixture,
		String shipmentId,
		String key,
		Instant registeredAt
	) throws Exception {
		Instant observedAt = Instant.now();
		return mockMvc.perform(post("/api/admin/shipments/{shipmentId}/delivery-complete", shipmentId)
			.header("Idempotency-Key", key)
			.with(authentication(admin(fixture.admin().getId())))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"expectedVersion":0,"deliveredAt":"%s","evidenceObservedAt":"%s","reason":"Verified official carrier evidence"}
				""".formatted(registeredAt, observedAt)));
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
		UserAccount other = userRepository.save(new UserAccount(
			SocialProvider.GOOGLE, "other-" + suffix, "other-" + suffix + "@example.com", "Other", UserRole.CUSTOMER));
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
		ProductOption firstOption = new ProductOption(product, "First", 0, ProductOptionStatus.ACTIVE);
		firstOption.updateInventory(SupplierAvailability.AVAILABLE, InventoryMode.TRACKED, 10L);
		firstOption = optionRepository.save(firstOption);
		ProductOption secondOption = new ProductOption(product, "Second", 0, ProductOptionStatus.ACTIVE);
		secondOption.updateInventory(SupplierAvailability.AVAILABLE, InventoryMode.TRACKED, 10L);
		secondOption = optionRepository.save(secondOption);
		PaymentGroup paymentGroup = new PaymentGroup("P-" + UUID.randomUUID(), customer, 36_000,
			now.plusSeconds(3600));
		paymentGroup.confirmPolicy(now);
		paymentGroup.approve(36_000, now);
		paymentGroup = paymentGroupRepository.save(paymentGroup);
		CustomerOrder order = new CustomerOrder(
			"O-" + UUID.randomUUID(), customer, supplier, paymentGroup,
			new ShippingAddressSnapshot("Receiver", "010-1111-2222", "12345", "Seoul test road", "101"),
			36_000, paymentGroup.getExpiresAt());
		order.markSupplierOrderPending();
		order.lockAddressForSupplierPortal(now);
		order = orderRepository.save(order);
		OrderItem firstItem = orderItemRepository.save(new OrderItem(order, product, firstOption, 1, 2, now));
		OrderItem secondItem = orderItemRepository.save(new OrderItem(order, product, secondOption, 1, 1, now));
		Fulfillment fulfillment = new Fulfillment(order);
		fulfillment.routeToSupplierPortal(now, now.plus(60, ChronoUnit.DAYS));
		fulfillmentRepository.save(fulfillment);
		return new Fixture(admin, manager, customer, other, order, firstItem, secondItem);
	}

	private Authentication supplier(UUID userId) {
		return auth(userId, UserRole.CUSTOMER, "ROLE_SUPPLIER");
	}

	private Authentication customer(UUID userId) {
		return auth(userId, UserRole.CUSTOMER, "ROLE_CUSTOMER");
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
		UserAccount otherCustomer,
		CustomerOrder order,
		OrderItem firstItem,
		OrderItem secondItem
	) {
	}
}
