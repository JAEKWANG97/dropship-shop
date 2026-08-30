package com.dropshipshop.api.fulfillment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.dropshipshop.api.fulfillment.domain.Fulfillment;
import com.dropshipshop.api.fulfillment.domain.FulfillmentChannel;
import com.dropshipshop.api.fulfillment.domain.FulfillmentHandoverReasonCode;
import com.dropshipshop.api.fulfillment.repository.FulfillmentRepository;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.supplierportal.domain.FulfillmentHandoverHistory;
import com.dropshipshop.api.supplierportal.repository.FulfillmentHandoverHistoryRepository;

@Service
public class SupplierFulfillmentHandoverService {

	private final FulfillmentRepository fulfillmentRepository;
	private final FulfillmentHandoverHistoryRepository historyRepository;

	SupplierFulfillmentHandoverService(
		FulfillmentRepository fulfillmentRepository,
		FulfillmentHandoverHistoryRepository historyRepository
	) {
		this.fulfillmentRepository = fulfillmentRepository;
		this.historyRepository = historyRepository;
	}

	@Transactional(readOnly = true)
	public List<UUID> cutoffCandidateIds(Instant now) {
		return fulfillmentRepository.findTopExpiredPortalCandidateIds(now, PageRequest.of(0, 100));
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean enforceCutoff(UUID fulfillmentId, Instant now) {
		return enforceCutoffLocked(fulfillmentId, now);
	}

	@Transactional
	public boolean enforceCutoffLazy(UUID fulfillmentId, Instant now) {
		return enforceCutoffLocked(fulfillmentId, now);
	}

	private boolean enforceCutoffLocked(UUID fulfillmentId, Instant now) {
		Fulfillment fulfillment = fulfillmentRepository.findByIdForUpdate(fulfillmentId).orElse(null);
		if (fulfillment == null || fulfillment.getPiiAccessCutoffAt() == null
			|| now.isBefore(fulfillment.getPiiAccessCutoffAt())) {
			return false;
		}
		if (!fulfillment.handOverToCoreable(now, FulfillmentHandoverReasonCode.PII_CUTOFF_REACHED, null)) {
			return false;
		}
		historyRepository.save(FulfillmentHandoverHistory.system(
			fulfillment, FulfillmentHandoverReasonCode.PII_CUTOFF_REACHED, now
		));
		return true;
	}

	@Transactional
	public boolean takeOverTerminal(CustomerOrder order, Instant now) {
		if (fulfillmentRepository.findChannelByOrderId(order.getId())
			.filter(channel -> channel == FulfillmentChannel.SUPPLIER_PORTAL)
			.isEmpty()) {
			return false;
		}
		Fulfillment fulfillment = fulfillmentRepository.findByOrderIdForUpdate(order.getId()).orElseThrow();
		if (fulfillment.getChannel() != FulfillmentChannel.SUPPLIER_PORTAL) {
			return false;
		}
		if (!fulfillment.handOverTerminalToCoreable(now)) {
			return false;
		}
		historyRepository.save(FulfillmentHandoverHistory.system(
			fulfillment, FulfillmentHandoverReasonCode.TERMINAL_STATE, now
		));
		return true;
	}

	@Transactional
	public boolean takeOverSupplierShortage(Fulfillment fulfillment, Instant now) {
		if (!fulfillment.handOverToCoreable(
			now, FulfillmentHandoverReasonCode.SUPPLIER_SHORTAGE_REPORTED, null
		)) {
			return false;
		}
		historyRepository.save(FulfillmentHandoverHistory.system(
			fulfillment, FulfillmentHandoverReasonCode.SUPPLIER_SHORTAGE_REPORTED, now
		));
		return true;
	}
}
