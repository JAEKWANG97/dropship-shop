package com.dropshipshop.api.notification;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.dropshipshop.api.claim.domain.Claim;
import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.notification.domain.NotificationChannel;
import com.dropshipshop.api.notification.domain.NotificationLog;
import com.dropshipshop.api.notification.domain.NotificationType;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.payment.domain.PaymentGroup;
import com.dropshipshop.api.refund.domain.Refund;
import com.dropshipshop.api.support.domain.CustomerInquiry;
import com.dropshipshop.api.user.domain.UserAccount;

@Service
public class NotificationService {

	private static final DateTimeFormatter DEADLINE_FORMATTER = DateTimeFormatter
		.ofPattern("MM/dd HH:mm")
		.withZone(ZoneId.of("Asia/Seoul"));

	private final NotificationLogRepository notificationLogRepository;
	private final ApplicationEventPublisher eventPublisher;

	NotificationService(
		NotificationLogRepository notificationLogRepository,
		ApplicationEventPublisher eventPublisher
	) {
		this.notificationLogRepository = notificationLogRepository;
		this.eventPublisher = eventPublisher;
	}

	public void transactionalSms(UserAccount user, CustomerOrder order, PaymentGroup paymentGroup, Claim claim, Refund refund, NotificationType type) {
		createTransactionalSms(user, order, paymentGroup, claim, refund, type);
	}

	public NotificationLog paymentPending(UserAccount user, CustomerOrder order, PaymentGroup paymentGroup) {
		return createTransactionalSms(user, order, paymentGroup, null, null, NotificationType.PAYMENT_PENDING);
	}

	private NotificationLog createTransactionalSms(
		UserAccount user,
		CustomerOrder order,
		PaymentGroup paymentGroup,
		Claim claim,
		Refund refund,
		NotificationType type
	) {
		String message = message(order, paymentGroup, type);
		NotificationLog log = notificationLogRepository.saveAndFlush(new NotificationLog(
			user == null ? null : user.getId(),
			order == null ? null : order.getId(),
			paymentGroup == null ? null : paymentGroup.getId(),
			claim == null ? null : claim.getId(),
			refund == null ? null : refund.getId(),
			null,
			type,
			NotificationChannel.SMS,
			recipient(user, order),
			type.name().toLowerCase(),
			payload(order, paymentGroup, claim, refund, type, message)
		));
		eventPublisher.publishEvent(new NotificationDispatchRequested(log.getId()));
		return log;
	}

	public NotificationLog customerInquiryAnswered(CustomerInquiry inquiry) {
		NotificationLog log = notificationLogRepository.saveAndFlush(new NotificationLog(
			null,
			null,
			null,
			null,
			null,
			inquiry.getId(),
			NotificationType.CUSTOMER_INQUIRY_ANSWERED,
			NotificationChannel.EMAIL,
			inquiry.getEmail(),
			"customer_inquiry_answered",
			"message=" + inquiry.getAnswer()
		));
		eventPublisher.publishEvent(new NotificationDispatchRequested(log.getId()));
		return log;
	}

	public NotificationLog supplierInvitation(
		UUID supplierId,
		UUID supplierInviteId,
		String recipient,
		java.time.Instant expiresAt,
		String rawToken
	) {
		NotificationLog log = notificationLogRepository.saveAndFlush(NotificationLog.supplierInvitation(
			supplierId,
			supplierInviteId,
			recipient,
			"supplierInviteId=%s, expiresAt=%s".formatted(supplierInviteId, expiresAt)
		));
		eventPublisher.publishEvent(new SupplierInviteDispatchRequested(log.getId(), rawToken));
		return log;
	}

	public NotificationLog supplierFulfillmentRequested(Supplier supplier, CustomerOrder order) {
		return supplierOperational(
			supplier,
			order.getId(),
			null,
			NotificationType.SUPPLIER_FULFILLMENT_REQUESTED,
			"supplier_fulfillment_requested",
			"event=FULFILLMENT_REQUESTED, orderNumber=%s, portalPath=/supplier/orders/%s"
				.formatted(order.getOrderNumber(), order.getOrderNumber())
		);
	}

	public NotificationLog supplierProductReviewResult(Supplier supplier, Product product) {
		return supplierOperational(
			supplier,
			null,
			null,
			NotificationType.SUPPLIER_PRODUCT_REVIEW_RESULT,
			"supplier_product_review_result",
			"event=PRODUCT_REVIEW_RESULT, productId=%s, reviewStatus=%s, portalPath=/supplier/products/%s"
				.formatted(product.getId(), product.getReviewStatus(), product.getId())
		);
	}

	public NotificationLog supplierClaimWorkRequested(Supplier supplier, CustomerOrder order, Claim claim) {
		return supplierOperational(
			supplier,
			order.getId(),
			claim.getId(),
			NotificationType.SUPPLIER_CLAIM_WORK_REQUESTED,
			"supplier_claim_work_requested",
			"event=CLAIM_WORK_REQUESTED, orderNumber=%s, portalPath=/supplier/claim-tasks"
				.formatted(order.getOrderNumber())
		);
	}

	private NotificationLog supplierOperational(
		Supplier supplier,
		UUID orderId,
		UUID claimId,
		NotificationType type,
		String templateKey,
		String payload
	) {
		NotificationLog log = notificationLogRepository.saveAndFlush(NotificationLog.supplierOperational(
			supplier.getId(), orderId, claimId, type, supplier.getEmail(), templateKey, payload
		));
		eventPublisher.publishEvent(new NotificationDispatchRequested(log.getId()));
		return log;
	}

	public boolean exists(CustomerOrder order, NotificationType type) {
		return notificationLogRepository.existsByOrderIdAndType(order.getId(), type);
	}

	private String recipient(UserAccount user, CustomerOrder order) {
		if (order != null && !isBlank(order.getRecipientPhone())) {
			return order.getRecipientPhone();
		}
		if (user != null && !isBlank(user.getPhoneNumber())) {
			return user.getPhoneNumber();
		}
		return "";
	}

	private String payload(
		CustomerOrder order,
		PaymentGroup paymentGroup,
		Claim claim,
		Refund refund,
		NotificationType type,
		String message
	) {
		UUID orderId = order == null ? null : order.getId();
		UUID paymentGroupId = paymentGroup == null ? null : paymentGroup.getId();
		UUID claimId = claim == null ? null : claim.getId();
		UUID refundId = refund == null ? null : refund.getId();
		return "type=%s, orderId=%s, paymentGroupId=%s, claimId=%s, refundId=%s, message=%s"
			.formatted(type, orderId, paymentGroupId, claimId, refundId, message);
	}

	private String message(CustomerOrder order, PaymentGroup paymentGroup, NotificationType type) {
		return switch (type) {
			case PAYMENT_PENDING -> "[코어블SAF] 입금대기 %s원 %s %s 기한 %s".formatted(
				formatAmount(paymentGroup == null ? 0 : paymentGroup.getTotalAmount()),
				nullToBlank(paymentGroup == null ? null : paymentGroup.getBankTransferBankName()),
				nullToBlank(paymentGroup == null ? null : paymentGroup.getBankTransferAccountNumber()),
				paymentGroup == null ? "" : DEADLINE_FORMATTER.format(paymentGroup.getExpiresAt())
			).trim();
			case PAYMENT_COMPLETED -> "[코어블SAF] 입금 확인 완료. 주문 %s 발주 준비".formatted(orderNumber(order));
			case PAYMENT_EXCEPTION -> "[코어블SAF] 결제 확인이 필요합니다. 주문상세를 확인해 주세요";
			case OUT_OF_STOCK -> "[코어블SAF] 품절로 환불 안내 예정입니다. 주문상세 확인";
			case SHIPMENT_STARTED -> "[코어블SAF] 출고되었습니다. 주문상세에서 운송장을 확인해 주세요";
			case DELIVERY_COMPLETED -> "[코어블SAF] 배송완료 처리되었습니다. 주문상세 확인";
			case DELAY_NOTICE -> "[코어블SAF] 출고 지연 중입니다. 확인 후 안내드리겠습니다";
			case CLAIM_STATUS_CHANGED -> "[코어블SAF] 클레임 처리 상태가 변경되었습니다. 주문상세 확인";
			case REFUND_COMPLETED -> "[코어블SAF] 환불 완료 처리되었습니다. 주문상세 확인";
			case CUSTOMER_INQUIRY_ANSWERED -> "[코어블SAF] 고객 문의 답변이 등록되었습니다";
			case SUPPLIER_INVITATION -> "[코어블SAF] 공급처 포털 초대";
			case SUPPLIER_FULFILLMENT_REQUESTED -> "[코어블SAF] 새 출고 요청";
			case SUPPLIER_PRODUCT_REVIEW_RESULT -> "[코어블SAF] 상품 검토 결과";
			case SUPPLIER_CLAIM_WORK_REQUESTED -> "[코어블SAF] 클레임 처리 요청";
			case MARKETING -> "[코어블SAF] 안내 메시지입니다";
		};
	}

	private String orderNumber(CustomerOrder order) {
		return order == null ? "" : order.getOrderNumber();
	}

	private String formatAmount(long amount) {
		return "%,d".formatted(amount);
	}

	private String nullToBlank(String value) {
		return value == null ? "" : value;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
