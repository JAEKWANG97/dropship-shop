package com.dropshipshop.api.shipment;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class CarrierRegistry {

	private static final Map<String, Carrier> CARRIERS = carriersByCode();

	public List<Carrier> carriers() {
		return List.copyOf(CARRIERS.values());
	}

	public Optional<Carrier> find(String carrierCode) {
		if (carrierCode == null || carrierCode.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(CARRIERS.get(normalizeCode(carrierCode)));
	}

	public Carrier require(String carrierCode) {
		return find(carrierCode)
			.orElseThrow(() -> new IllegalArgumentException("Unsupported carrier code: " + carrierCode));
	}

	public String officialTrackingUrl(String carrierCode, String trackingNumber) {
		Carrier carrier = require(carrierCode);
		String tracking = Objects.requireNonNull(trackingNumber, "trackingNumber").trim();
		if (tracking.isEmpty() || tracking.length() > 100) {
			throw new IllegalArgumentException("trackingNumber must be non-blank and at most 100 characters");
		}
		return switch (carrier.carrierCode()) {
			case "CJ_LOGISTICS" -> UriComponentsBuilder
				.fromUriString("https://www.cjlogistics.com/ko/tool/parcel/newTracking")
				.queryParam("gnbInvcNo", tracking)
				.build().encode().toUriString();
			case "LOTTE" -> UriComponentsBuilder
				.fromUriString("https://www.lotteglogis.com/home/reservation/tracking/linkView")
				.queryParam("InvNo", tracking)
				.build().encode().toUriString();
			case "HANJIN" -> UriComponentsBuilder
				.fromUriString("https://www.hanjin.com/kor/CMS/DeliveryMgr/WaybillResult.do")
				.queryParam("mCode", "MN038")
				.queryParam("schLang", "KR")
				.queryParam("wblnumText2", tracking)
				.build().encode().toUriString();
			case "KOREA_POST" -> UriComponentsBuilder
				.fromUriString("https://service.epost.go.kr/trace.RetrieveDomRigiTraceList.comm")
				.queryParam("sid1", tracking)
				.queryParam("displayHeader", "N")
				.build().encode().toUriString();
			default -> throw new IllegalStateException("Carrier registry is incomplete");
		};
	}

	private static Map<String, Carrier> carriersByCode() {
		Map<String, Carrier> carriers = new LinkedHashMap<>();
		carriers.put("CJ_LOGISTICS", new Carrier("CJ_LOGISTICS", "CJ대한통운"));
		carriers.put("LOTTE", new Carrier("LOTTE", "롯데택배"));
		carriers.put("HANJIN", new Carrier("HANJIN", "한진택배"));
		carriers.put("KOREA_POST", new Carrier("KOREA_POST", "우체국택배"));
		return Collections.unmodifiableMap(carriers);
	}

	private static String normalizeCode(String carrierCode) {
		return carrierCode.trim().toUpperCase(Locale.ROOT);
	}

	public record Carrier(String carrierCode, String carrierName) {
		public Carrier {
			Objects.requireNonNull(carrierCode, "carrierCode");
			Objects.requireNonNull(carrierName, "carrierName");
		}
	}
}
