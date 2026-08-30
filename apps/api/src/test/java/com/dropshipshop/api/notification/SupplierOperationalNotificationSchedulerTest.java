package com.dropshipshop.api.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class SupplierOperationalNotificationSchedulerTest {

	@Test
	void recoversPendingOutboxRowsAndIsolatesEachDispatchFailure() {
		NotificationLogRepository repository = mock(NotificationLogRepository.class);
		NotificationDispatchListener listener = mock(NotificationDispatchListener.class);
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		when(repository.findPendingSupplierOperationalIds(ArgumentMatchers.any()))
			.thenReturn(List.of(first, second));
		doThrow(new IllegalStateException("process stopped"))
			.when(listener).dispatchNow(first);
		SupplierOperationalNotificationScheduler scheduler =
			new SupplierOperationalNotificationScheduler(repository, listener);

		assertThat(scheduler.recoverPendingBatch()).isEqualTo(1);
		verify(listener).dispatchNow(first);
		verify(listener).dispatchNow(second);
	}
}
