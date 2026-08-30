package com.dropshipshop.api.checkout;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dropshipshop.api.payment.repository.PaymentGroupRepository;

@Component
class CheckoutExpiryScheduler {

	private static final Logger log = LoggerFactory.getLogger(CheckoutExpiryScheduler.class);
	private static final int BATCH_SIZE = 100;
	private final PaymentGroupRepository paymentGroupRepository;
	private final CheckoutExpiryService checkoutExpiryService;

	CheckoutExpiryScheduler(
		PaymentGroupRepository paymentGroupRepository,
		CheckoutExpiryService checkoutExpiryService
	) {
		this.paymentGroupRepository = paymentGroupRepository;
		this.checkoutExpiryService = checkoutExpiryService;
	}

	@Scheduled(fixedDelayString = "${app.checkout.expiry-interval-ms:60000}")
	void expirePendingCheckouts() {
		Instant now = Instant.now();
		for (UUID paymentGroupId : paymentGroupRepository.findExpiryCandidateIds(now, PageRequest.of(0, BATCH_SIZE))) {
			try {
				checkoutExpiryService.expire(paymentGroupId, now);
			} catch (RuntimeException exception) {
				log.warn("Failed to expire checkout payment group {}", paymentGroupId, exception);
			}
		}
	}
}
