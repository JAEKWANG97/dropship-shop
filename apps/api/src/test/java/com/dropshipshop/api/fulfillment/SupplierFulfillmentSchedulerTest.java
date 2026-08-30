package com.dropshipshop.api.fulfillment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.dropshipshop.api.notification.SupplierNotificationRetentionService;
import com.dropshipshop.api.supplierfulfillment.SupplierPiiAccessRetentionService;

class SupplierFulfillmentSchedulerTest {

	@Test
	void isolatesEachCutoffCandidateAndChangesEachOwnerAtMostOnce() {
		SupplierFulfillmentHandoverService handover = mock(SupplierFulfillmentHandoverService.class);
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		Instant now = Instant.parse("2026-08-30T00:00:00Z");
		when(handover.cutoffCandidateIds(now)).thenReturn(List.of(first, second));
		when(handover.enforceCutoff(first, now)).thenReturn(true);
		when(handover.enforceCutoff(second, now)).thenThrow(new IllegalStateException("race"));
		SupplierFulfillmentScheduler scheduler = new SupplierFulfillmentScheduler(
			handover,
			mock(SupplierPiiAccessRetentionService.class),
			mock(SupplierNotificationRetentionService.class)
		);

		assertThat(scheduler.handOverAt(now)).isEqualTo(1);
		verify(handover).enforceCutoff(first, now);
		verify(handover).enforceCutoff(second, now);
	}

	@Test
	void notificationCleanupStillRunsWhenAccessLogRetentionFails() {
		SupplierFulfillmentHandoverService handover = mock(SupplierFulfillmentHandoverService.class);
		SupplierPiiAccessRetentionService accessRetention = mock(SupplierPiiAccessRetentionService.class);
		SupplierNotificationRetentionService notificationRetention = mock(SupplierNotificationRetentionService.class);
		UUID notificationId = UUID.randomUUID();
		doThrow(new IllegalStateException("access cleanup failed"))
			.when(accessRetention).cleanupBefore(org.mockito.ArgumentMatchers.any());
		when(notificationRetention.candidateIds(org.mockito.ArgumentMatchers.any()))
			.thenReturn(List.of(notificationId));
		SupplierFulfillmentScheduler scheduler = new SupplierFulfillmentScheduler(
			handover, accessRetention, notificationRetention
		);

		scheduler.cleanupAccessLogs();

		verify(notificationRetention).cleanup(
			org.mockito.ArgumentMatchers.eq(notificationId), org.mockito.ArgumentMatchers.any());
	}
}
