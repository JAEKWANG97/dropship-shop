package com.dropshipshop.api.supplierportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class SupplierPortalRetentionSchedulerTest {

	@Test
	void isolatesEachFailureAndContinuesWithOtherApplicationsAndInvites() {
		SupplierPortalRetentionService service = mock(SupplierPortalRetentionService.class);
		SupplierPortalRetentionScheduler scheduler = new SupplierPortalRetentionScheduler(service);
		Instant now = Instant.parse("2026-08-30T00:00:00Z");
		UUID failedApplication = UUID.randomUUID();
		UUID cleanedApplication = UUID.randomUUID();
		UUID cleanedInvite = UUID.randomUUID();

		when(service.applicationCandidateIds(now)).thenReturn(List.of(failedApplication, cleanedApplication));
		when(service.inviteCandidateIds(now)).thenReturn(List.of(cleanedInvite));
		when(service.cleanupApplication(failedApplication, now)).thenThrow(new IllegalStateException("test failure"));
		when(service.cleanupApplication(cleanedApplication, now)).thenReturn(true);
		when(service.cleanupInvite(cleanedInvite, now)).thenReturn(true);

		SupplierPortalRetentionScheduler.CleanupSummary summary = scheduler.cleanupAt(now);

		assertThat(summary.applicationsCleaned()).isEqualTo(1);
		assertThat(summary.invitesCleaned()).isEqualTo(1);
		assertThat(summary.failures()).isEqualTo(1);
		verify(service).cleanupApplication(cleanedApplication, now);
		verify(service).cleanupInvite(cleanedInvite, now);
	}
}
