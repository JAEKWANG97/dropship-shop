package com.dropshipshop.api.shipment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class PortalShipmentDtos {

	private PortalShipmentDtos() {
	}

	public record CarrierListResponse(List<CarrierResponse> carriers) {
	}

	public record CarrierResponse(
		String carrierCode,
		String carrierName,
		boolean officialTrackingSupported
	) {
	}

	public record ShipmentCreateRequest(
		@NotBlank @Size(max = 40) String carrierCode,
		@NotBlank @Size(max = 100) String trackingNumber,
		List<@Valid AllocationRequest> allocations
	) {
	}

	public record AllocationRequest(
		@NotNull UUID orderItemId,
		@Positive int quantity
	) {
	}

	public record TrackingCorrectionRequest(
		@NotNull @PositiveOrZero Long expectedVersion,
		@NotBlank @Size(max = 40) String carrierCode,
		@NotBlank @Size(max = 100) String trackingNumber,
		@NotBlank @Size(max = 200) String reason
	) {
	}

	public record ShipmentVoidRequest(
		@NotNull @PositiveOrZero Long expectedVersion,
		@NotBlank @Size(max = 200) String reason
	) {
	}

	public record DeliveryCompleteRequest(
		@NotNull @PositiveOrZero Long expectedVersion,
		@NotNull Instant deliveredAt,
		@NotNull Instant evidenceObservedAt,
		@NotBlank @Size(max = 200) String reason
	) {
	}

	public enum DeliveryCorrectionType {
		REOPEN_TRACKING,
		CORRECT_DELIVERED_AT
	}

	public record DeliveryCorrectionRequest(
		@NotNull @PositiveOrZero Long expectedVersion,
		@NotNull DeliveryCorrectionType correctionType,
		Instant correctedDeliveredAt,
		Instant evidenceObservedAt,
		@NotBlank @Size(max = 200) String reason
	) {
	}

	public record AllocationResponse(UUID orderItemId, int quantity) {
	}

	public record UnallocatedItemResponse(
		UUID orderItemId,
		String productName,
		String optionName,
		int orderedQuantity,
		int allocatedQuantity,
		int remainingQuantity
	) {
	}

	public record CustomerShipmentListResponse(
		List<CustomerShipmentResponse> shipments,
		boolean allocationComplete
	) {
	}

	public record CustomerShipmentResponse(
		UUID shipmentId,
		String carrierCode,
		String carrierName,
		String trackingNumber,
		String officialTrackingUrl,
		String displayStatus,
		Instant registeredAt,
		Instant deliveredAt,
		List<AllocationResponse> allocations
	) {
	}

	public record SupplierShipmentListResponse(
		List<SupplierShipmentResponse> shipments,
		List<UnallocatedItemResponse> unallocatedItems,
		boolean allocationComplete,
		boolean canRegisterShipment,
		String nextAction
	) {
	}

	public record SupplierShipmentResponse(
		UUID shipmentId,
		long version,
		String status,
		String carrierCode,
		String carrierName,
		String trackingNumber,
		String officialTrackingUrl,
		boolean editable,
		boolean countsTowardAllocation,
		Instant registeredAt,
		Instant deliveredAt,
		List<AllocationResponse> allocations
	) {
	}

	public record AdminShipmentListResponse(
		List<AdminShipmentResponse> shipments,
		List<UnallocatedItemResponse> unallocatedItems,
		boolean allocationComplete
	) {
	}

	public record AdminShipmentResponse(
		UUID shipmentId,
		long version,
		String status,
		String carrierCode,
		String carrierName,
		String trackingNumber,
		String officialTrackingUrl,
		boolean countsTowardAllocation,
		Instant registeredAt,
		Instant deliveredAt,
		Instant evidenceObservedAt,
		List<AllocationResponse> allocations,
		List<ChangeHistoryResponse> histories
	) {
	}

	public record ChangeHistoryResponse(
		UUID historyId,
		String actorType,
		String action,
		String beforeSnapshot,
		String afterSnapshot,
		String reason,
		Instant evidenceObservedAt,
		Instant createdAt
	) {
	}
}
