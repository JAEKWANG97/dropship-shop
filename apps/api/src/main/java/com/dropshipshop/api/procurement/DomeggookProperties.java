package com.dropshipshop.api.procurement;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class DomeggookProperties {

	private final boolean enabled;
	private final boolean autoOrderEnabled;
	private final boolean catalogSyncEnabled;
	private final boolean catalogSyncDryRun;
	private final int catalogSyncBatchSize;
	private final String apiKey;
	private final String userId;
	private final String password;
	private final String clientIp;
	private final String endpoint;

	DomeggookProperties(
		@Value("${app.domeggook.enabled:false}") boolean enabled,
		@Value("${app.domeggook.auto-order-enabled:false}") boolean autoOrderEnabled,
		@Value("${app.domeggook.catalog-sync-enabled:false}") boolean catalogSyncEnabled,
		@Value("${app.domeggook.catalog-sync-dry-run:true}") boolean catalogSyncDryRun,
		@Value("${app.domeggook.catalog-sync-batch-size:20}") int catalogSyncBatchSize,
		@Value("${app.domeggook.api-key:}") String apiKey,
		@Value("${app.domeggook.user-id:}") String userId,
		@Value("${app.domeggook.password:}") String password,
		@Value("${app.domeggook.client-ip:}") String clientIp,
		@Value("${app.domeggook.endpoint:https://domeggook.com/ssl/api/}") String endpoint
	) {
		this.enabled = enabled;
		this.autoOrderEnabled = autoOrderEnabled;
		this.catalogSyncEnabled = catalogSyncEnabled;
		this.catalogSyncDryRun = catalogSyncDryRun;
		this.catalogSyncBatchSize = catalogSyncBatchSize;
		this.apiKey = apiKey;
		this.userId = userId;
		this.password = password;
		this.clientIp = clientIp;
		this.endpoint = endpoint;
	}

	boolean enabled() {
		return enabled;
	}

	boolean autoOrderEnabled() {
		return enabled && autoOrderEnabled;
	}

	boolean catalogSyncEnabled() {
		return enabled && catalogSyncEnabled;
	}

	boolean catalogSyncDryRun() {
		return catalogSyncDryRun;
	}

	int catalogSyncBatchSize() {
		return Math.max(1, Math.min(catalogSyncBatchSize, 100));
	}

	void requireConfigured() {
		if (!enabled || apiKey.isBlank() || userId.isBlank() || password.isBlank() || clientIp.isBlank()) {
			throw new DomeggookApiException("NOT_CONFIGURED", "Domeggook purchase API is not configured", false);
		}
	}

	String apiKey() {
		return apiKey;
	}

	String userId() {
		return userId;
	}

	String password() {
		return password;
	}

	String clientIp() {
		return clientIp;
	}

	String endpoint() {
		return endpoint;
	}
}
