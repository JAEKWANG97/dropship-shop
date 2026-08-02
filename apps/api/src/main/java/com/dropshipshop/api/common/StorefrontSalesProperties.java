package com.dropshipshop.api.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class StorefrontSalesProperties {

	private final boolean enabled;
	private final String closedNotice;

	public StorefrontSalesProperties(
		@Value("${app.sales.enabled:false}") boolean enabled,
		@Value("${app.sales.closed-notice:판매 준비 중입니다.}") String closedNotice
	) {
		this.enabled = enabled;
		this.closedNotice = closedNotice;
	}

	public boolean enabled() {
		return enabled;
	}

	public String closedNotice() {
		return closedNotice;
	}

	public void requireEnabled() {
		if (!enabled) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, closedNotice);
		}
	}
}
