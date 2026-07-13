export type PolicySlug = "terms" | "privacy" | "shipping" | "cancellation-refund" | "stock-risk";

export type PolicyPage = {
  slug: PolicySlug;
  title: string;
  version: string;
  effectiveDate: string;
  summary: string;
  sections: { heading: string; paragraphs: string[] }[];
};

export const BUSINESS_PROFILE = {
  companyName: "가라사니",
  brandName: "코어블SAF",
  representativeName: "김문교",
  businessRegistrationNumber: "611-05-94564",
  mailOrderSalesRegistrationNumber: "통신판매업 신고 면제 사업자 (간이과세자)",
  mailOrderSalesRegistrationAuthority: "해당 없음 (신고 면제)",
  businessAddress: "서울특별시 송파구 동남로11길 4, 103동 1405호",
  returnAddress: "서울특별시 송파구 동남로11길 4, 103동 1405호",
  customerCenterPhone: "010-8277-7369",
  customerCenterEmail: "contact@coreable-saf.com",
  customerCenterHours: "평일 10:00 - 18:00 (주말·공휴일 휴무)",
  privacyOfficerName: "김문교",
  privacyOfficerEmail: "contact@coreable-saf.com",
  privacyOfficerPhone: "010-8277-7369",
  hostingProvider: "Amazon Web Services",
  purchaseSafetyNotice:
    "계좌입금 구매안전서비스는 실제 판매 시작 전에 제공 방식과 이용 방법을 안내합니다. 제공 전에는 실제 주문을 받지 않습니다.",
  businessRegistryUrl: "https://www.ftc.go.kr/www/bizContents.do?key=253",
} as const;

export const POLICY_PAGES: PolicyPage[] = [
  {
    slug: "terms",
    title: "이용약관",
    version: "prelaunch-2026-06-30",
    effectiveDate: "2026-06-30",
    summary: "본 약관은 코어블SAF 쇼핑몰 이용과 상품 주문, 결제, 배송, 취소 및 클레임 처리의 기본 조건을 정합니다.",
    sections: [
      {
        heading: "서비스 이용",
        paragraphs: [
          "고객은 상품 목록, 상품 상세, 장바구니, 주문서, 결제, 주문 내역과 배송 상태 확인 기능을 이용할 수 있습니다.",
          "소셜 로그인 후 주문, 배송, 클레임 처리를 위해 이름, 연락 가능한 이메일, 인증된 휴대폰 번호 등 필수 정보를 입력해야 할 수 있습니다.",
        ],
      },
      {
        heading: "주문과 결제",
        paragraphs: [
          "상품 판매가는 배송비를 포함한 가격으로 운영하며, 결제 단계에서 별도 배송비를 청구하지 않습니다.",
          "현재 결제 수단은 계좌입금이며, 주문 생성 후 24시간 안에 입금이 확인되지 않으면 주문이 취소될 수 있습니다.",
          "주문서에 안내된 입금자명 또는 금액과 실제 입금 내역이 다르면 입금 확인이 보류되며 고객 확인 후 처리합니다.",
          "현금영수증은 고객 요청 시 홈택스에서 발급하며, 의무발행 대상 거래는 요청 여부와 관계없이 발급합니다.",
          "여러 배송 그룹 상품을 한 번에 결제할 수 있으나 주문은 배송 그룹별로 분리될 수 있습니다.",
        ],
      },
      {
        heading: "운영 예외",
        paragraphs: [
          "공급처 출고형 상품 특성상 결제 후 공급처 확인 과정에서 품절 또는 출고 지연이 발생할 수 있습니다.",
          "품절이 확인되면 해당 배송 그룹 주문 금액을 기준으로 환불 흐름을 진행합니다.",
        ],
      },
    ],
  },
  {
    slug: "privacy",
    title: "개인정보처리방침",
    version: "prelaunch-2026-06-30",
    effectiveDate: "2026-06-30",
    summary: "본 방침은 회원 식별, 주문, 배송, 결제, 환불, 클레임 처리를 위해 필요한 개인정보 처리 기준을 설명합니다.",
    sections: [
      {
        heading: "처리 목적과 수집 항목",
        paragraphs: [
          "소셜 로그인 정보는 회원 식별과 로그인에 사용합니다. 제공자 user id, 표시 이름, 제공자가 제공한 이메일을 저장할 수 있습니다.",
          "주문과 배송 처리를 위해 이름, 연락 가능한 이메일, 휴대폰 번호, 수령인, 전화번호, 주소를 수집합니다.",
          "비로그인 고객 문의 답변과 분쟁 처리를 위해 이름, 이메일, 제목, 문의 내용을 필수로 수집하고 연락처는 선택으로 수집합니다. 문의 기록과 동의 증적은 접수일로부터 3년간 보관합니다.",
          "결제와 분쟁 대응을 위해 주문 상품, 결제 금액, 결제 수단, 입금자명, 입금 확인 시각, 환불 및 클레임 처리 이력을 보관합니다. 향후 PG 결제를 사용하는 경우 PG 거래 식별자를 추가로 처리할 수 있습니다.",
        ],
      },
      {
        heading: "보유 기간",
        paragraphs: [
          "회원 프로필과 연락처는 회원 탈퇴 시까지 보유하되, 법정 보존 대상 기록은 관련 법령에 따른 기간 동안 분리 보관합니다.",
          "표시/광고 기록은 6개월, 계약 또는 청약철회 기록과 대금결제 및 재화 공급 기록은 5년, 소비자 불만 또는 분쟁 처리 기록은 3년 보존을 기준으로 시작합니다.",
        ],
      },
      {
        heading: "제3자 제공과 처리 위탁",
        paragraphs: [
          "현재 운영 범위에서는 개인정보를 기본적으로 제3자에게 제공하지 않으며, 법령상 필요하거나 별도 고지/동의한 경우에만 제공합니다.",
          "개인정보 처리 위탁 및 국외 이전 내역은 처리자, 처리 목적, 보유 기간과 함께 공개하며, 처리자가 변경되면 본 방침을 통해 안내합니다.",
        ],
      },
    ],
  },
  {
    slug: "shipping",
    title: "배송 정책",
    version: "prelaunch-2026-06-30",
    effectiveDate: "2026-06-30",
    summary: "배송비는 상품 가격에 포함되어 있으며, 주문은 배송 그룹 단위로 처리됩니다.",
    sections: [
      {
        heading: "배송비와 배송 그룹",
        paragraphs: [
          "고객에게 별도 배송비를 청구하지 않고 상품 판매가에 배송비를 포함합니다.",
          "한 주문은 하나의 배송 그룹을 기준으로 처리하며, 장바구니에 여러 배송 그룹이 있으면 결제 후 배송 그룹별 주문으로 분리될 수 있습니다.",
        ],
      },
      {
        heading: "발주와 배송 상태",
        paragraphs: [
          "결제 확정 후 공급처 발주 작업 시작은 영업일 기준 당일 또는 다음 영업일을 목표로 합니다.",
          "관리자가 택배사와 송장번호를 입력하면 고객 주문 상세에서 배송 정보를 확인할 수 있습니다.",
        ],
      },
    ],
  },
  {
    slug: "cancellation-refund",
    title: "취소/환불 정책",
    version: "prelaunch-2026-06-30",
    effectiveDate: "2026-06-30",
    summary: "취소, 반품, 교환, 환불은 주문 상태와 공급처 발주 여부에 따라 처리됩니다.",
    sections: [
      {
        heading: "취소 기준",
        paragraphs: [
          "공급처 발주 전에는 고객 직접 취소가 가능하며, 발주 작업 이후 취소는 관리자 검토를 거칩니다.",
          "이미 출고되었거나 공급처 취소가 불가능한 경우 취소 클레임이 거절되거나 배송 후 반품 클레임으로 전환 안내될 수 있습니다.",
        ],
      },
      {
        heading: "반품/교환과 배송비",
        paragraphs: [
          "단순 변심 반품/교환은 배송 완료일로부터 7일 이내 접수된 건만 심사합니다.",
          "상품 하자, 오배송, 상품 정보와 다름, 배송 문제는 배송 완료일로부터 3개월 이내이면서 고객이 그 사실을 안 날 또는 알 수 있었던 날부터 30일 이내 접수 기준으로 시작합니다.",
          "단순 변심의 반환 또는 재배송 비용은 고객 부담, 판매자 또는 배송 귀책 사유는 운영자 부담을 기본으로 합니다.",
        ],
      },
      {
        heading: "환불 기준",
        paragraphs: [
          "반품이 필요한 환불은 반품 상품 입고와 관리자 검수 후 결제 수단에 맞는 환불을 진행합니다. 계좌입금 환불은 입고 확인일로부터 3영업일 이내 처리를 목표로 합니다.",
          "계좌입금 주문은 실제 환불 이체 후 관리자가 완료를 기록한 때에만 환불 완료로 표시합니다. 향후 PG 결제를 사용하는 주문은 PG 취소/환불 성공이 확인된 뒤에만 환불 완료로 표시합니다.",
        ],
      },
    ],
  },
  {
    slug: "stock-risk",
    title: "결제 후 품절 안내",
    version: "prelaunch-2026-06-30",
    effectiveDate: "2026-06-30",
    summary: "공급처 출고형 상품은 결제 후 공급처 확인 과정에서 품절이 확인될 수 있습니다.",
    sections: [
      {
        heading: "품절 처리",
        paragraphs: [
          "공급처 품절이 확인되면 즉시 품절 안내와 환불 흐름으로 전환합니다.",
          "품절된 배송 그룹 주문 금액만 환불할 수 있습니다.",
          "배송 그룹 주문 내부의 일부 상품, 옵션, 수량만 따로 환불하는 기능은 현재 운영 범위에서 지원하지 않습니다.",
        ],
      },
    ],
  },
];

export function getPolicyPage(slug: string) {
  return POLICY_PAGES.find((policy) => policy.slug === slug) ?? null;
}

export function policyHref(policyType: string) {
  return (
    {
      TERMS_OF_SERVICE: "/policies/terms",
      PRIVACY_POLICY: "/policies/privacy",
      SHIPPING_POLICY: "/policies/shipping",
      CANCELLATION_REFUND_POLICY: "/policies/cancellation-refund",
      OUT_OF_STOCK_NOTICE: "/policies/stock-risk",
    }[policyType] ?? "/policies"
  );
}
