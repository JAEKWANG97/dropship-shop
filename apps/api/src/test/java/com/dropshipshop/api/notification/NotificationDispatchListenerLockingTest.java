package com.dropshipshop.api.notification;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.email.EmailSender;
import com.dropshipshop.api.notification.domain.NotificationChannel;
import com.dropshipshop.api.notification.domain.NotificationLog;
import com.dropshipshop.api.notification.domain.NotificationStatus;
import com.dropshipshop.api.sms.SmsSender;
import com.dropshipshop.api.supplierportal.SupplierPortalFeatureGate;
import com.dropshipshop.api.supplierportal.SupplierPortalHasher;
import com.dropshipshop.api.supplierportal.domain.SupplierInvite;
import com.dropshipshop.api.supplierportal.repository.SupplierInviteRepository;
import com.dropshipshop.api.support.InquiryLookupTokenService;
import com.dropshipshop.api.user.repository.UserAccountRepository;

class NotificationDispatchListenerLockingTest {

	@Test
	void inviteDispatchLocksSupplierThenInviteThenNotification() {
		NotificationLogRepository notificationRepository = mock(NotificationLogRepository.class);
		SupplierRepository supplierRepository = mock(SupplierRepository.class);
		SupplierInviteRepository inviteRepository = mock(SupplierInviteRepository.class);
		SupplierPortalFeatureGate featureGate = mock(SupplierPortalFeatureGate.class);
		UUID notificationId = UUID.randomUUID();
		UUID supplierId = UUID.randomUUID();
		UUID inviteId = UUID.randomUUID();
		NotificationLogRepository.SupplierInviteDispatchScope scope =
			mock(NotificationLogRepository.SupplierInviteDispatchScope.class);
		Supplier supplier = mock(Supplier.class);
		SupplierInvite invite = mock(SupplierInvite.class);
		NotificationLog log = mock(NotificationLog.class);
		when(scope.getSupplierId()).thenReturn(supplierId);
		when(scope.getSupplierInviteId()).thenReturn(inviteId);
		when(notificationRepository.findSupplierInviteDispatchScope(notificationId)).thenReturn(Optional.of(scope));
		when(supplierRepository.findByIdForUpdate(supplierId)).thenReturn(Optional.of(supplier));
		when(inviteRepository.findByIdForUpdate(inviteId)).thenReturn(Optional.of(invite));
		when(notificationRepository.findByIdForUpdate(notificationId)).thenReturn(Optional.of(log));
		when(log.getStatus()).thenReturn(NotificationStatus.PENDING);
		when(log.getChannel()).thenReturn(NotificationChannel.EMAIL);
		when(log.getSupplierInviteId()).thenReturn(inviteId);
		when(featureGate.isEnabled()).thenReturn(false);
		NotificationDispatchListener listener = new NotificationDispatchListener(
			notificationRepository,
			mock(SmsSender.class),
			mock(EmailSender.class),
			mock(InquiryLookupTokenService.class),
			featureGate,
			mock(SupplierPortalHasher.class),
			supplierRepository,
			inviteRepository,
			mock(UserAccountRepository.class),
			"http://localhost:3000"
		);

		listener.dispatchSupplierInvite(new SupplierInviteDispatchRequested(notificationId, "raw-token"));

		InOrder locks = inOrder(notificationRepository, supplierRepository, inviteRepository);
		locks.verify(notificationRepository).findSupplierInviteDispatchScope(notificationId);
		locks.verify(supplierRepository).findByIdForUpdate(supplierId);
		locks.verify(inviteRepository).findByIdForUpdate(inviteId);
		locks.verify(notificationRepository).findByIdForUpdate(notificationId);
		verify(log).markSkipped("PORTAL_NOT_RELEASED");
	}
}
