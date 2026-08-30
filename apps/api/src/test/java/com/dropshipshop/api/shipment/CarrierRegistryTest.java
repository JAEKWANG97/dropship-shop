package com.dropshipshop.api.shipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import org.junit.jupiter.api.Test;

class CarrierRegistryTest {

	private final CarrierRegistry registry = new CarrierRegistry();

	@Test
	void exposesSupportedCarriersInStableOrder() {
		assertThat(registry.carriers())
			.extracting(CarrierRegistry.Carrier::carrierCode, CarrierRegistry.Carrier::carrierName)
			.containsExactly(
				tuple("CJ_LOGISTICS", "CJ대한통운"),
				tuple("LOTTE", "롯데택배"),
				tuple("HANJIN", "한진택배"),
				tuple("KOREA_POST", "우체국택배")
			);
	}

	@Test
	void buildsOnlyRegistryOwnedOfficialTrackingUrls() {
		assertThat(registry.officialTrackingUrl("cj_logistics", "1234567890"))
			.isEqualTo("https://www.cjlogistics.com/ko/tool/parcel/newTracking?gnbInvcNo=1234567890");
		assertThat(registry.officialTrackingUrl("LOTTE", "123456789012"))
			.isEqualTo("https://www.lotteglogis.com/home/reservation/tracking/linkView?InvNo=123456789012");
		assertThat(registry.officialTrackingUrl("HANJIN", "1234567890"))
			.isEqualTo(
				"https://www.hanjin.com/kor/CMS/DeliveryMgr/WaybillResult.do"
					+ "?mCode=MN038&schLang=KR&wblnumText2=1234567890"
			);
		assertThat(registry.officialTrackingUrl("KOREA_POST", "1234567890123"))
			.isEqualTo(
				"https://service.epost.go.kr/trace.RetrieveDomRigiTraceList.comm"
					+ "?sid1=1234567890123&displayHeader=N"
			);
	}

	@Test
	void rejectsUnsupportedCarrierAndEncodesTrackingAsAQueryValue() {
		assertThat(registry.officialTrackingUrl("CJ_LOGISTICS", "12 34&redirect=evil"))
			.endsWith("gnbInvcNo=12%2034%26redirect%3Devil");
		assertThatThrownBy(() -> registry.require("UNKNOWN"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Unsupported carrier code");
	}
}
