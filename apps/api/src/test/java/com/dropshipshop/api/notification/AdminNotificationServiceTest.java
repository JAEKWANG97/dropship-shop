package com.dropshipshop.api.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.dropshipshop.api.notification.domain.NotificationLog;
import com.dropshipshop.api.notification.domain.NotificationStatus;

class AdminNotificationServiceTest {

	@Test
	void retryLocksTheNotificationBeforeChangingFailedSupplierEmailToPending() {
		NotificationLogRepository repository = mock(NotificationLogRepository.class);
		ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
		NotificationLog log = mock(NotificationLog.class);
		UUID id = UUID.randomUUID();
		when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(log));
		when(log.getSupplierInviteId()).thenReturn(null);
		when(log.isSupplierOperational()).thenReturn(true);
		when(log.getStatus()).thenReturn(NotificationStatus.FAILED);
		when(log.getRecipient()).thenReturn("supplier@example.com");
		when(log.getCreatedAt()).thenReturn(Instant.now());
		when(log.getId()).thenReturn(id);
		AdminNotificationService service = new AdminNotificationService(repository, publisher);

		assertThat(service.retry(id)).isSameAs(log);

		verify(repository).findByIdForUpdate(id);
		verify(repository, never()).findById(id);
		verify(log).markPendingForRetry();
		verify(publisher).publishEvent(new NotificationDispatchRequested(id));
	}
}
