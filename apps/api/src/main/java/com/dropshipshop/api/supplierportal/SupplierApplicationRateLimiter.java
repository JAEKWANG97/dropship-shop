package com.dropshipshop.api.supplierportal;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dropshipshop.api.common.error.ApiErrorCode;
import com.dropshipshop.api.common.error.ApiErrorException;

@Component
class SupplierApplicationRateLimiter {

	private final Map<String, ArrayDeque<Instant>> attempts = new ConcurrentHashMap<>();
	private final SupplierPortalHasher hasher;
	private final SupplierPortalProperties properties;
	private final Clock clock = Clock.systemUTC();

	SupplierApplicationRateLimiter(SupplierPortalHasher hasher, SupplierPortalProperties properties) {
		this.hasher = hasher;
		this.properties = properties;
	}

	void check(String remoteAddress) {
		Instant now = clock.instant();
		Instant cutoff = now.minus(properties.applicationRateWindow());
		String key = hasher.hmac("supplier-application-rate-limit", remoteAddress == null ? "unknown" : remoteAddress);
		attempts.compute(key, (ignored, existing) -> {
			ArrayDeque<Instant> bucket = existing == null ? new ArrayDeque<>() : existing;
			while (!bucket.isEmpty() && bucket.peekFirst().isBefore(cutoff)) {
				bucket.removeFirst();
			}
			if (bucket.size() >= properties.applicationRateLimit()) {
				throw new ApiErrorException(
					HttpStatus.TOO_MANY_REQUESTS,
					ApiErrorCode.RATE_LIMITED,
					"Please try again later"
				);
			}
			bucket.addLast(now);
			return bucket;
		});
	}

	@Scheduled(fixedDelayString = "${app.supplier-portal.application-rate-cleanup-ms:600000}")
	void cleanupExpiredBuckets() {
		Instant cutoff = clock.instant().minus(properties.applicationRateWindow());
		attempts.forEach((key, ignored) -> attempts.computeIfPresent(key, (candidate, bucket) -> {
				while (!bucket.isEmpty() && bucket.peekFirst().isBefore(cutoff)) {
					bucket.removeFirst();
				}
				return bucket.isEmpty() ? null : bucket;
			}));
	}
}
