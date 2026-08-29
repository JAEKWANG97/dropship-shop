package com.dropshipshop.api.notification;

import java.util.UUID;

record SupplierInviteDispatchRequested(UUID notificationId, String inviteToken) {

	@Override
	public String toString() {
		return "SupplierInviteDispatchRequested[notificationId=%s, inviteToken=<redacted>]".formatted(notificationId);
	}
}
