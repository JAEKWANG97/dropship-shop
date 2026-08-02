package com.dropshipshop.api.checkout;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class CheckoutPolicyProperties {

	private final String orderPolicyVersion;
	private final String cancellationRefundPolicyVersion;
	private final String outOfStockNoticeVersion;
	private final String confirmedNoticeText;

	CheckoutPolicyProperties(
		@Value("${app.policies.required-order-policy-version:2026-08-02}") String orderPolicyVersion,
		@Value("${app.policies.required-cancellation-refund-policy-version:2026-08-02}") String cancellationRefundPolicyVersion,
		@Value("${app.policies.required-out-of-stock-notice-version:2026-08-02}") String outOfStockNoticeVersion,
		@Value("${app.policies.checkout-confirmed-notice-text}") String confirmedNoticeText
	) {
		this.orderPolicyVersion = orderPolicyVersion;
		this.cancellationRefundPolicyVersion = cancellationRefundPolicyVersion;
		this.outOfStockNoticeVersion = outOfStockNoticeVersion;
		this.confirmedNoticeText = confirmedNoticeText;
	}

	String orderPolicyVersion() {
		return orderPolicyVersion;
	}

	String cancellationRefundPolicyVersion() {
		return cancellationRefundPolicyVersion;
	}

	String outOfStockNoticeVersion() {
		return outOfStockNoticeVersion;
	}

	String confirmedNoticeText() {
		return confirmedNoticeText;
	}
}
