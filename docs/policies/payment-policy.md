# Payment Policy

Status: Confirmed

## Purpose

결제 수단, 입금 확인, PG 검증, 결제 실패, 금액 불일치, 환불 실행 기준을 정한다.

## Policy Areas

- PG 제공자 선택
- 결제 수단 범위
- 무통장/가상계좌 포함 여부
- 결제 승인 검증 방식
- 중복 결제 콜백 처리
- 금액 불일치 처리
- 결제 예외 처리
- 결제 후 품절 환불 처리
- 복수 배송 그룹 결제 단위
- 배송 그룹 주문 단위 부분 환불
- PG 취소/환불 실패 처리
- 계좌입금 안내, 입금 확인, 미입금 취소
- 현금영수증 발급 운영
- 구매안전서비스 확보 상태

## Initial Direction

- MVP 결제 주 경로는 고객 직접 계좌입금으로 시작한다.
- Toss Payments 카드/간편결제/계좌이체 PG 연동은 코드에 남기되 고객 주 경로에서는 비활성화한다.
- 입금 확인은 관리자가 실제 입금 내역을 확인한 뒤 수동으로 수행한다.
- 입금 금액 불일치 시 주문을 확정하지 않고 관리자 메모와 고객 안내 대상으로 둔다.
- 중복 결제 확인 요청은 idempotent하게 처리한다.
- 공급처 품절 시 해당 배송 그룹 주문 금액 환불을 기본 정책으로 둔다.
- 주문은 서버에 생성된 결제 그룹(PaymentGroup)과 `PAYMENT_PENDING` 주문들을 기준으로 생성한다.

## Confirmed Policy

- MVP 고객 결제 주 경로는 직접 계좌입금이다.
- Toss Payments PG 결제는 Deferred 상태다. 기존 Toss 구현은 보존하지만 고객 체크아웃 주 경로에서 사용하지 않는다.
- 계좌입금 주문 생성 전에 서버 결제 그룹(PaymentGroup)과 배송 그룹별 `PAYMENT_PENDING` 주문을 생성한다.
- `PAYMENT_PENDING`은 MVP에서 `입금대기`를 의미한다.
- 고객 체크아웃 완료 화면에는 입금 계좌, 입금 금액, 입금자명, 입금 기한, 현금영수증 안내를 표시한다.
- 기본 입금 기한은 주문 생성 후 24시간이다.
- 입금 계좌 정보는 운영 환경 설정으로 관리하고 source control에 실제 계좌값을 커밋하지 않는다.
- MVP에서는 장바구니 전체를 한 번에 결제할 수 있다.
- 계좌입금 1건은 여러 배송 그룹 주문을 포함할 수 있다.
- 결제 금액은 포함된 배송 그룹 주문들의 합산 금액이다.
- 관리자 입금확인은 다음 조건을 모두 만족해야 성공한다.
  - 결제 그룹(PaymentGroup)과 대상 주문의 상태가 `PAYMENT_PENDING`이다.
  - 결제 그룹(PaymentGroup)의 주문서 정책 확인이 완료되어 있다.
  - 관리자가 실제 입금액과 주문 금액 일치를 확인했다.
  - 주문 상품과 옵션이 입금확인 시점에도 판매 가능하다.
- 입금확인 성공 후 결제 그룹(PaymentGroup)에 포함된 배송 그룹 주문들은 `SUPPLIER_ORDER_PENDING` 상태로 전환한다.
- 계좌입금 결제 레코드는 `PaymentProvider.BANK_TRANSFER`, `PaymentMethod.BANK_TRANSFER`, `providerPaymentKey = BANK-{checkoutNumber}`를 사용한다.
- 입금 금액, 입금자명, 입금 시각이 불일치하거나 확인이 필요한 경우 관리자는 입금 불일치 메모를 남기고 주문을 `PAYMENT_PENDING` 상태로 유지한다.
- 기한 내 입금이 확인되지 않으면 관리자가 미입금 취소 처리한다.
- 미입금 취소 주문은 `CANCELLED` 상태가 되며 공급처 발주 대상으로 전환하지 않는다.
- 입금확인, 미입금 취소, 입금 불일치 메모, 수동 환불 완료는 관리자 주체, 시각, 사유를 기록한다.
- 계좌입금 환불은 PG 취소가 아니라 관리자가 실제 환불을 완료한 뒤 수동 환불 완료로 기록한다.
- 고객에게 환불 완료로 노출하는 시점은 실제 환불 완료 기록 이후다.
- 현금영수증은 대표자/관리자가 입금 확인 후 홈택스에서 수동 발급한다. 고객 요청은 `/support`, 고객센터 전화, 이메일로 접수하고 자동 API 연동은 후속 작업으로 둔다.
- 사업자등록 업종이 현금영수증 의무발행업종에 해당하면 건당 10만원 이상 현금 거래는 고객 요청이 없어도 발급한다. 실제 판매 전 홈택스 가맹/발급 권한과 업종 적용 여부를 확인한다.
- 계좌입금은 현금성 결제이므로 실결제 오픈 전 구매안전서비스 방식을 확정해야 한다. 은행 에스크로, 소비자피해보상보험, PG/가상계좌 재도입 중 하나를 운영 선택지로 둔다.
- 계좌입금 구매안전서비스 계약과 고객 선택 화면이 준비되기 전에는 실제 판매 주문을 받지 않는다.
- MVP에서는 배송 그룹 주문 단위 부분 취소/부분 환불을 지원한다.
- 배송 그룹 주문 단위 부분 환불은 하나의 결제 그룹 중 특정 배송 그룹 주문 금액만 환불하는 것을 의미한다. 계좌입금은 수동 환불, Toss 재도입 후에는 PG 부분 취소로 실행한다.
- 배송 그룹 주문 내부의 상품, 옵션, 수량 단위 부분 취소/부분 환불은 MVP에서 지원하지 않는다.
- 특정 배송 그룹 주문이 공급처 품절이면 해당 배송 그룹 주문 금액만 부분 취소/환불한다.
- 하나의 배송 그룹 주문 내부에서 일부 상품 또는 일부 수량만 품절이면 MVP에서는 해당 배송 그룹 주문 전체를 취소/환불한다.
- 입금대기, 미입금 취소 주문은 일반 고객 주문 내역에 노출하지 않고 체크아웃 화면 또는 고객 문의 대상으로 다룬다.
- 입금확인 완료 주문부터 고객 주문 내역에 노출한다.
- PG 승인이 발생한 결제 예외 주문은 고객 주문 내역 또는 별도 결제 확인 화면에 노출한다.

## Deferred PG/Toss Policy

Toss Payments integration remains available for future reintroduction, but it is not the primary customer checkout path for the current MVP.

Deferred Toss policy:

- Toss Payments PG 제공자는 향후 재도입 후보로 유지한다.
- Toss 결제수단은 카드, 간편결제, 계좌이체로 제한한다.
- 가상계좌/무통장입금성 비동기 PG 결제, 휴대폰 결제, 상품권 결제는 아직 사용하지 않는다.
- PG 결제 승인 검증은 Spring Boot 서버에서 수행한다.
- PG 승인 금액과 서버 결제 그룹(PaymentGroup) 총액이 일치해야 주문들을 확정한다.
- Toss webhook은 `paymentKey`로 Toss 결제조회 API를 다시 호출해 검증한다.
- 중복 Toss webhook은 전송 ID 기반 idempotency key로 한 번만 처리한다.
- 서버 confirm 결과와 Toss webhook 검증 결과가 충돌하면 자동 상태 보정하지 않고 `REVIEW_REQUIRED`로 분리해 운영 확인 대상으로 둔다.

## Provider Selection

Direct bank transfer is selected for the current MVP customer checkout path.

Toss Payments remains the deferred PG provider candidate instead of PortOne.

Selection reasons:

- The MVP needs a direct domestic PG integration path for Korean commerce.
- Toss Payments provides client-side payment window/widget integration and server-side payment confirmation APIs.
- Toss Payments provides payment cancel APIs that can be used for full cancel and partial cancel/refund handling.
- Using the PG directly keeps the first integration path narrower than adding an aggregator abstraction before the product has real order volume.

Official references:

- Toss Payments payment window integration: `https://docs.tosspayments.com/guides/v2/payment-window/integration`
- Toss Payments payment widget integration: `https://docs.tosspayments.com/guides/v2/payment-widget/integration`
- Toss Payments API reference: `https://docs.tosspayments.com/reference`

## Sandbox And Production Readiness

Sandbox path:

- Use Toss Payments test client key in the frontend.
- Use Toss Payments test secret key only on the Spring Boot server.
- Toss test keys are needed only while executing B-001 sandbox verification; they are not required for the current bank-transfer checkout.
- Configure the Spring Boot server secret through `payments.toss.secret-key` or the equivalent environment variable, and never commit it.
- Create a server-side `PaymentGroup` before invoking the Toss payment UI.
- Confirm approved payment results on the server before moving orders to `SUPPLIER_ORDER_PENDING`.
- Record raw PG identifiers and normalized payment events for idempotency and reconciliation.

Production readiness:

- A deployed homepage URL is required before live Toss Payments review and live key switching.
- Toss Payments merchant account and contract must be ready before live keys are used.
- Live client key and live secret key must be configured outside source control.
- Enabled live payment methods must match the MVP method policy: card, easy payment, and account transfer only.
- Cancel and partial cancel permissions must be verified in the live Toss Payments merchant configuration before launch.
- Return, cancellation, privacy, and business disclosure pages must be reachable before production payment activation.

## System Impact

- 서버에는 PG secret key가 필요하다.
- 계좌입금 MVP에서는 입금 계좌 설정과 관리자 입금확인 권한이 필요하다.
- PG secret key는 Toss 재도입 시에만 필요하다.
- 결제 성공 처리는 반드시 서버 검증 또는 관리자 입금확인 후 확정한다.
- `Payment`와 `Order` 상태를 분리한다.
- `PaymentGroup` 또는 동등한 checkout payment aggregate가 필요하다.
- `PaymentGroup`은 계좌입금 1건 또는 PG 결제 1건과 여러 배송 그룹 주문을 연결한다.
- 환불 실행 결과를 별도 기록해야 한다.
- 환불 실행 결과는 `refunds`에 PG 취소 거래 키, 멱등 키, 실패 코드/메시지, 완료/실패 시각으로 기록한다. Implemented by DS-15.
- 계좌입금 환불 실행 결과는 `refunds`에 수동 환불 관리자, 완료 시각, 환불 사유, 환불 계좌 메모를 기록한다.
- PG 승인 이벤트, 취소 요청, 취소 결과는 멱등 처리를 위해 별도 이벤트 이력으로 기록한다.
- 결제 예외 큐가 관리자 화면에 필요하다. Implemented by DS-33 as a DB state-based queue.
- 결제 예외 자동 취소는 idempotency key를 사용해야 한다. Implemented by DS-33.
- 주문 확정 처리는 주문 단위 lock 또는 unique constraint로 한 번만 성공해야 한다.
- 계좌입금 MVP에서는 `PAYMENT_PENDING`이 입금대기이며, 입금 webhook은 사용하지 않는다.
- 미입금 취소는 관리자 수동 액션으로 처리한다.
- 입금확인, 미입금취소, 수동환불 완료는 `OrderStatusHistory`와 관리자 action history에 남겨야 한다.
- Toss 결제 상태 webhook은 서버 confirm 결과와 PG 상태 대사를 위한 보조 입력으로 처리한다. Implemented by DS-34.
- 환불 모델은 결제 그룹(PaymentGroup) 안의 배송 그룹 주문 단위 환불을 지원해야 한다.
- 환불 금액은 환불 대상 배송 그룹 주문 금액의 합계와 일치해야 한다.
- 배송 그룹 주문 단위 환불 성공 시 남은 환불 가능 금액에 따라 `PaymentGroup`과 `Payment`를 `REFUNDED` 또는 `PARTIALLY_REFUNDED`로 갱신한다. Implemented by DS-15.
- 고객 주문 내역 API는 결제 성공 후 확정된 주문부터 노출한다.
- 단, PG 승인이 발생한 결제 예외는 고객에게 처리 상태를 보여줘야 한다.

## Open Questions

None
