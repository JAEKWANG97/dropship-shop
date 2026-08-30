package com.dropshipshop.api.supplierportal;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.fulfillment.domain.Fulfillment;
import com.dropshipshop.api.fulfillment.domain.FulfillmentHandoverReasonCode;
import com.dropshipshop.api.fulfillment.repository.FulfillmentRepository;
import com.dropshipshop.api.notification.NotificationLogRepository;
import com.dropshipshop.api.supplierportal.domain.FulfillmentHandoverHistory;
import com.dropshipshop.api.supplierportal.domain.SupplierInvite;
import com.dropshipshop.api.supplierportal.repository.FulfillmentHandoverHistoryRepository;
import com.dropshipshop.api.supplierportal.repository.SupplierInviteRepository;

@Service
public class SupplierContractTerminalService {

	private final SupplierInviteRepository inviteRepository;
	private final FulfillmentRepository fulfillmentRepository;
	private final FulfillmentHandoverHistoryRepository handoverHistoryRepository;
	private final NotificationLogRepository notificationLogRepository;

	SupplierContractTerminalService(
		SupplierInviteRepository inviteRepository,
		FulfillmentRepository fulfillmentRepository,
		FulfillmentHandoverHistoryRepository handoverHistoryRepository,
		NotificationLogRepository notificationLogRepository
	) {
		this.inviteRepository = inviteRepository;
		this.fulfillmentRepository = fulfillmentRepository;
		this.handoverHistoryRepository = handoverHistoryRepository;
		this.notificationLogRepository = notificationLogRepository;
	}

	/** Caller owns the locked Supplier and the repository lock-order prefix. */
	public boolean expireIfOverdue(
		Supplier supplier,
		UUID adminId,
		String reason,
		Instant now
	) {
		if (!supplier.lazilyExpireContract(now)) {
			return false;
		}
		revokeOpenInvite(supplier, adminId, now);
		for (Fulfillment fulfillment : fulfillmentRepository.findOpenPortalSupplierOwnedForUpdate(supplier.getId())) {
			if (!fulfillment.handOverToCoreable(now, FulfillmentHandoverReasonCode.CONTRACT_EXPIRED, adminId)) {
				continue;
			}
			FulfillmentHandoverHistory history = adminId == null
				? FulfillmentHandoverHistory.system(
					fulfillment,
					FulfillmentHandoverReasonCode.CONTRACT_EXPIRED,
					now
				)
				: FulfillmentHandoverHistory.admin(
					fulfillment,
					adminId,
					FulfillmentHandoverReasonCode.CONTRACT_EXPIRED,
					reason,
					null,
					null,
					null,
					now
				);
			handoverHistoryRepository.save(history);
		}
		return true;
	}

	private void revokeOpenInvite(Supplier supplier, UUID adminId, Instant now) {
		SupplierInvite invite = inviteRepository.findOpenBySupplierIdForUpdate(supplier.getId()).orElse(null);
		if (invite == null) {
			return;
		}
		invite.revokeForLifecycle(adminId, now);
		notificationLogRepository.findFirstBySupplierInviteIdOrderByCreatedAtDesc(invite.getId())
			.ifPresent(log -> log.scheduleRecipientCleanup(invite.getRecipientRetentionExpiresAt()));
	}
}
