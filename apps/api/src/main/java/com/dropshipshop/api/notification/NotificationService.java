package com.dropshipshop.api.notification;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.dropshipshop.api.claim.domain.Claim;
import com.dropshipshop.api.notification.domain.NotificationLog;
import com.dropshipshop.api.notification.domain.NotificationType;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.payment.domain.PaymentGroup;
import com.dropshipshop.api.refund.domain.Refund;
import com.dropshipshop.api.user.domain.UserAccount;

@Service
public class NotificationService {

	private final NotificationLogRepository notificationLogRepository;

	NotificationService(NotificationLogRepository notificationLogRepository) {
		this.notificationLogRepository = notificationLogRepository;
	}

	public void email(UserAccount user, CustomerOrder order, PaymentGroup paymentGroup, Claim claim, Refund refund, NotificationType type) {
		notificationLogRepository.save(new NotificationLog(
			user == null ? null : user.getId(),
			order == null ? null : order.getId(),
			paymentGroup == null ? null : paymentGroup.getId(),
			claim == null ? null : claim.getId(),
			refund == null ? null : refund.getId(),
			type,
			user == null ? "" : user.getEmail(),
			type.name().toLowerCase(),
			payload(order, paymentGroup, claim, refund, type)
		));
	}

	public boolean exists(CustomerOrder order, NotificationType type) {
		return notificationLogRepository.existsByOrderIdAndType(order.getId(), type);
	}

	private String payload(CustomerOrder order, PaymentGroup paymentGroup, Claim claim, Refund refund, NotificationType type) {
		UUID orderId = order == null ? null : order.getId();
		UUID paymentGroupId = paymentGroup == null ? null : paymentGroup.getId();
		UUID claimId = claim == null ? null : claim.getId();
		UUID refundId = refund == null ? null : refund.getId();
		return "type=%s, orderId=%s, paymentGroupId=%s, claimId=%s, refundId=%s"
			.formatted(type, orderId, paymentGroupId, claimId, refundId);
	}
}
