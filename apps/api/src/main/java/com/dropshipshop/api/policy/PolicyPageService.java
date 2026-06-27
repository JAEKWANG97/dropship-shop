package com.dropshipshop.api.policy;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class PolicyPageService {

	private static final String VERSION = "2026-06-28";
	private static final List<PolicyDtos.PolicyLinkResponse> LINKS = List.of(
		new PolicyDtos.PolicyLinkResponse("SHIPPING_POLICY", "배송 정책", "/api/policies/shipping"),
		new PolicyDtos.PolicyLinkResponse("CANCELLATION_REFUND_POLICY", "취소/환불 정책", "/api/policies/cancellation-refund"),
		new PolicyDtos.PolicyLinkResponse("OUT_OF_STOCK_NOTICE", "결제 후 품절 안내", "/api/policies/stock-risk")
	);

	private static final Map<String, PolicyDtos.PolicyPageResponse> PAGES = Map.of(
		"shipping", new PolicyDtos.PolicyPageResponse(
			"SHIPPING_POLICY",
			"배송 정책",
			VERSION,
			"배송비는 상품 가격에 포함되며, 공급처 출고 후 송장번호로 배송 상태를 확인할 수 있습니다.",
			List.of(
				new PolicyDtos.PolicySectionResponse("배송비", List.of(
					"본 쇼핑몰은 고객에게 별도 배송비를 청구하지 않습니다.",
					"상품 가격에는 예상 배송비와 공급처 출고 비용이 포함되어 있습니다."
				)),
				new PolicyDtos.PolicySectionResponse("배송 방식", List.of(
					"주문 상품은 결제 확인 후 공급처 발주를 거쳐 출고됩니다.",
					"관리자가 택배사와 송장번호를 입력하면 고객 주문 상세에서 배송 정보를 확인할 수 있습니다."
				)),
				new PolicyDtos.PolicySectionResponse("배송 제한", List.of(
					"MVP에서는 주문당 하나의 배송 정보만 제공합니다.",
					"부분 배송과 분할 배송은 현재 지원하지 않습니다."
				))
			),
			LINKS
		),
		"cancellation-refund", new PolicyDtos.PolicyPageResponse(
			"CANCELLATION_REFUND_POLICY",
			"취소/환불 정책",
			VERSION,
			"공급처 발주 전에는 고객 직접 취소가 가능하며, 발주 작업 이후 취소는 관리자 검토를 거칩니다.",
			List.of(
				new PolicyDtos.PolicySectionResponse("고객 직접 취소", List.of(
					"결제 완료 후 공급처 발주 작업이 시작되기 전까지 고객이 직접 취소할 수 있습니다.",
					"직접 취소가 접수되면 주문은 환불 처리 중 상태로 전환되며, PG 취소/환불 성공 후 환불 완료로 표시됩니다."
				)),
				new PolicyDtos.PolicySectionResponse("발주 후 취소", List.of(
					"공급처 발주 작업이 시작되었거나 공급처 발주 완료 이후에는 고객 직접 취소가 제한됩니다.",
					"이 경우 취소 클레임을 접수하면 관리자가 공급처 취소 가능 여부를 확인한 뒤 승인 또는 거절합니다."
				)),
				new PolicyDtos.PolicySectionResponse("반품/교환 접수", List.of(
					"단순 변심 반품/교환은 배송 완료일로부터 7일 이내 접수된 건만 심사합니다.",
					"상품 하자, 오배송, 상품 정보와 다름, 배송 문제는 배송 완료일로부터 3개월 이내이면서 고객이 그 사실을 안 날 또는 알 수 있었던 날부터 30일 이내 접수된 건만 심사합니다.",
					"단순 변심의 반환 또는 재배송 비용은 고객 부담을 기본으로 하며, 상품 하자, 오배송, 상품 정보와 다름, 판매자 또는 배송 귀책 배송 문제는 운영자 부담을 기본으로 합니다."
				)),
				new PolicyDtos.PolicySectionResponse("환불 완료 기준", List.of(
					"결제 승인 완료 주문은 PG 취소/환불 성공이 확인된 뒤에만 환불 완료로 표시됩니다.",
					"PG 취소/환불 실패 시 환불 완료로 표시하지 않고 관리자 재시도 또는 확인 대상으로 처리합니다."
				))
			),
			LINKS
		),
		"stock-risk", new PolicyDtos.PolicyPageResponse(
			"OUT_OF_STOCK_NOTICE",
			"결제 후 품절 안내",
			VERSION,
			"공급처 출고형 상품은 결제 후 공급처 확인 과정에서 품절이 확인될 수 있습니다.",
			List.of(
				new PolicyDtos.PolicySectionResponse("품절 가능성", List.of(
					"상품은 사이트에서 판매 가능 상태여도 공급처 확인 시점에 품절될 수 있습니다.",
					"품절이 확인되면 해당 배송 그룹 주문은 품절 안내와 환불 처리 대상으로 전환됩니다."
				)),
				new PolicyDtos.PolicySectionResponse("부분 환불 단위", List.of(
					"하나의 결제에 여러 배송 그룹 주문이 포함된 경우 품절된 배송 그룹 주문 금액만 환불할 수 있습니다.",
					"배송 그룹 주문 내부의 일부 상품, 옵션, 수량만 따로 환불하는 기능은 MVP에서 지원하지 않습니다."
				)),
				new PolicyDtos.PolicySectionResponse("고객 안내", List.of(
					"품절 환불은 PG 취소/환불 성공 후 환불 완료로 표시됩니다.",
					"환불 처리 중에는 고객 주문 상세에서 환불 진행 상태를 확인할 수 있습니다."
				))
			),
			LINKS
		)
	);

	PolicyDtos.PolicyIndexResponse listPolicies() {
		return new PolicyDtos.PolicyIndexResponse(LINKS);
	}

	PolicyDtos.PolicyPageResponse getPolicy(String slug) {
		PolicyDtos.PolicyPageResponse page = PAGES.get(slug);
		if (page == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy not found");
		}
		return page;
	}
}
