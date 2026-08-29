package com.dropshipshop.api.supplierportal;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class AllowedWebOrigins {

	private final Set<String> origins;

	public AllowedWebOrigins(String configuredOrigins) {
		LinkedHashSet<String> parsed = new LinkedHashSet<>();
		Arrays.stream(configuredOrigins.split(","))
			.map(String::trim)
			.filter(value -> !value.isBlank())
			.forEach(value -> parsed.add(canonicalOrigin(value, false)
				.orElseThrow(() -> new IllegalStateException("Invalid configured CORS origin"))));
		this.origins = Set.copyOf(parsed);
	}

	public List<String> values() {
		return List.copyOf(origins);
	}

	public boolean allowsOriginHeader(String value) {
		return canonicalOrigin(value, false).filter(origins::contains).isPresent();
	}

	public boolean allowsReferer(String value) {
		return canonicalOrigin(value, true).filter(origins::contains).isPresent();
	}

	private Optional<String> canonicalOrigin(String value, boolean allowPath) {
		if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim())) {
			return Optional.empty();
		}
		try {
			URI uri = URI.create(value.trim());
			String scheme = uri.getScheme();
			if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
				|| uri.getHost() == null || uri.getUserInfo() != null
				|| uri.getQuery() != null || uri.getFragment() != null) {
				return Optional.empty();
			}
			String path = uri.getPath();
			if (!allowPath && path != null && !path.isEmpty() && !"/".equals(path)) {
				return Optional.empty();
			}
			int port = uri.getPort();
			boolean defaultPort = port < 0
				|| (scheme.equalsIgnoreCase("http") && port == 80)
				|| (scheme.equalsIgnoreCase("https") && port == 443);
			return Optional.of(scheme.toLowerCase() + "://" + uri.getHost().toLowerCase()
				+ (defaultPort ? "" : ":" + port));
		} catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
	}
}
