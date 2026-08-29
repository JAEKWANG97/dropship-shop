# Payment Policy

Status: Confirmed

## Purpose

계좌입금, 입금 확인, 금액 불일치, 미입금 취소, 환불 실행 기준을 정한다.

## Policy Areas

- 결제 수단 범위
- 계좌입금 안내와 입금 확인
- 금액 불일치 처리
- 결제 후 품절 환불 처리
- 복수 배송 그룹 결제 단위
- 배송 그룹 주문 단위 부분 환불
- 미입금 취소와 수동 환불
- 현금영수증 발급 운영
- 구매안전서비스 확보 상태

## Initial Direction

- 고객 결제 수단은 직접 계좌입금으로 한정한다.
- Toss Payments를 포함한 PG 결제는 도입하지 않는다.
- 입금 확인은 관리자가 실제 입금 내역을 확인한 뒤 수동으로 수행한다.
- 입금 금액 불일치 시 주문을 확정하지 않고 실제 수령액 전액을 결제그룹 단위 환불 대상으로 둔다.
- 중복 결제 확인 요청은 idempotent하게 처리한다.
- 공급처 품절 시 해당 배송 그룹 주문 금액 환불을 기본 정책으로 둔다.
- 주문은 서버에 생성된 결제 그룹(PaymentGroup)과 `PAYMENT_PENDING` 주문들을 기준으로 생성한다.

## Confirmed Policy

- 고객 결제 수단은 직접 계좌입금이며 관리자가 실제 입금 내역을 확인한다.
- 카드, 간편결제, PG 계좌이체·가상계좌, 휴대폰 결제, 상품권 결제는 제공하지 않는다.
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
- 현재 B-068 구현은 입금 금액·입금자명·입금 시각이 불일치하거나 확인이 필요한 경우 관리자 메모를 남기고 `PAYMENT_PENDING`을 유지한다. `B-102`가 배포되면 실제 수령액이 확인된 금액 불일치는 아래 Planned 결제예외·전액환불 계약으로 대체한다. 어느 PaymentGroup인지 아직 식별하지 못한 은행 거래는 주문 상태를 추측해 바꾸지 않고 외부 은행 대사 대상으로 남긴다.
- 기한 내 입금이 확인되지 않으면 관리자가 미입금 취소 처리한다.
- 미입금 취소 주문은 `CANCELLED` 상태가 되며 공급처 발주 대상으로 전환하지 않는다.
- 입금확인, 미입금 취소, 입금 불일치 처리, 환불 승인과 수동 환불 완료는 관리자 주체, 시각, 사유를 기록한다.
- 계좌입금 환불은 PG 취소가 아니라 관리자가 실제 환불을 완료한 뒤 수동 환불 완료로 기록한다.
- 고객에게 환불 완료로 노출하는 시점은 실제 환불 완료 기록 이후다.
- 현금영수증은 대표자/관리자가 입금 확인 후 홈택스에서 수동 발급한다. 고객 요청은 `/support`, 고객센터 전화, 이메일로 접수하고 자동 API 연동은 후속 작업으로 둔다.
- 사업자등록 업종이 현금영수증 의무발행업종에 해당하면 건당 10만원 이상 현금 거래는 고객 요청이 없어도 발급한다. 실제 판매 전 홈택스 가맹/발급 권한과 업종 적용 여부를 확인한다.
- 계좌입금은 현금성 결제이므로 실결제 오픈 전 은행 에스크로 또는 소비자피해보상보험을 구매안전서비스로 확보한다.
- 계좌입금 구매안전서비스 계약과 고객 선택 화면이 준비되기 전에는 실제 판매 주문을 받지 않는다.
- 운영 환경은 `APP_SALES_ENABLED=false`를 기본값으로 사용하고, 이 상태에서는 상품 상세와 장바구니가 `판매 준비 중`을 표시하며 장바구니 추가와 주문서 생성을 서버에서도 거절한다.
- 구매안전서비스 계약과 고객 선택 흐름을 실제로 확인한 뒤에만 `APP_SALES_ENABLED=true`로 전환한다.
- MVP에서는 배송 그룹 주문 단위 부분 취소/부분 환불을 지원한다.
- 배송 그룹 주문 단위 부분 환불은 하나의 결제 그룹 중 특정 배송 그룹 주문 금액만 관리자가 계좌로 환불하는 것을 의미한다.
- 단, 실제 입금액이 결제그룹 총액과 다른 `PAYMENT_AMOUNT_MISMATCH`는 금액을 배송 그룹별로 임의 배분하지 않고 실제 수령액 전부를 `PAYMENT_GROUP` 환불 1건으로 처리한다. Planned in B-102.
- 배송 그룹 주문 내부의 상품, 옵션, 수량 단위 부분 취소/부분 환불은 MVP에서 지원하지 않는다.
- 특정 배송 그룹 주문이 공급처 품절이면 해당 배송 그룹 주문 금액만 부분 취소/환불한다.
- 하나의 배송 그룹 주문 내부에서 일부 상품 또는 일부 수량만 품절이면 MVP에서는 해당 배송 그룹 주문 전체를 취소/환불한다.
- 입금대기, 미입금 취소 주문은 일반 고객 주문 내역에 노출하지 않고 체크아웃 화면 또는 고객 문의 대상으로 다룬다.
- 입금확인 완료 주문부터 고객 주문 내역에 노출한다.

## Supplier Portal Payment And Inventory — Planned (B-102)

Status: Planned (B-102). Existing `UNTRACKED` and legacy payment expiry behavior remains unchanged until this slice ships.

- `TRACKED` option checkout은 주문 생성과 함께 24시간 `HELD` 재고 예약을 만들고, scheduler가 미입금 만료 주문의 예약을 해제한다. 관리자는 기한 전 미입금 취소를 별도로 처리할 수 있다.
- `B-102`는 portal/legacy 여부와 무관하게 식별된 계좌입금의 `actualAmount != PaymentGroup.totalAmount`를 최우선 `PAYMENT_AMOUNT_MISMATCH`로 처리한다. 전체 입금 증적, `Payment.status=PAYMENT_EXCEPTION`, `PaymentGroup.status=PAYMENT_EXCEPTION`과 실제 수령액을 exactly once 저장하고, 남은 portal `HELD` 예약은 재확보·소비 없이 정확히 한 번 해제한다.
- 이 명령은 `PAYMENT_PENDING`, `EXPIRED`와 미입금 취소만 기록된 `CANCELLED` 결제그룹에 적용한다. `CANCELLED`는 기존 수령 Payment, Refund와 Fulfillment가 없어야 하며 모든 포함 Order가 미입금 취소 결과여야 한다. 따라서 취소 뒤 은행에서 발견된 실입금도 주문 재개 없이 같은 전액 환불 경로로 회수한다.
- 금액 불일치는 모든 배송 그룹 Order를 `REFUND_REQUESTED`로 보내되 Refund는 주문별로 나누지 않는다. `orderId=null`, `refundScope=PAYMENT_GROUP`, `reason=PAYMENT_AMOUNT_MISMATCH`, `refundAmount=actualDepositAmount`, `status=REQUESTED`인 Refund를 결제그룹당 정확히 하나만 만든다.
- 이 경로는 Fulfillment, 주소 잠금, 공급처 PII window·알림·조회 결과를 만들지 않고 정상 주문으로 재개하지 않는다. Coreable이 환불을 승인하고 실제 계좌이체 증적을 idempotent하게 완료하면 Refund, Payment, PaymentGroup과 포함 Order 전부를 `REFUNDED`로 끝내며 고객이 계속 구매하려면 새 checkout을 만든다.
- 미입금 취소만 완료된 qualifying `CANCELLED` 그룹에서 정확한 금액을 뒤늦게 확인해도 주문을 살리지 않는다. Portal/legacy 공통으로 immutable total을 유지하고 approved amount/time은 null로 두며 미입금 취소가 0으로 만든 refundable amount를 `totalAmount=actualAmount`로 복구한 뒤, 실제 입금 증적과 Payment/PaymentGroup `PAYMENT_EXCEPTION`을 저장한다. Immutable 배송 그룹 Order 금액마다 `LATE_DEPOSIT_EXCEPTION` Refund를 하나씩 만들어 모든 Order를 `REFUND_REQUESTED`로 보내고, 입금시각이 원래 기한 안이어도 재고 재확보·소비·Fulfillment를 시도하지 않으며 전액 환불 뒤 새 checkout만 허용한다.
- B-102 received-payment exception Refund는 실제 수령금 반환이므로 금액 변경·거절·정상 주문 재개를 허용하지 않는다. Order별 수동 완료도 Refund id를 포함한 별도 key/hash/result replay로 중복 송금을 막고 Payment/PaymentGroup 부분·전체 환불 집계를 원자적으로 갱신한다.
- portal 입금확인은 immutable OrderItem management-channel snapshot을 기준으로 Supplier 거래 상태, 상품·옵션·compliance·supplier availability와 각 `TRACKED` 주문 항목의 유효한 `HELD` 예약을 추가로 검증한다. Portal-origin Supplier의 overdue contract는 Supplier lock 아래 sales INACTIVE·portal suspension/open-work handover를 포함한 공통 terminal routine으로 EXPIRED 처리하고 time-valid VERIFIED가 아니면 판매불가 예외로 보낸다.
- Checkout은 Supplier -> Product -> 모든 Option(UNTRACKED 포함), 만료·입금확인은 PaymentGroup -> Supplier -> Product -> 모든 Option -> Order/Fulfillment 순서로 잠근다. Catalog/inventory writer도 Product -> Option을 따르고 Product 뒤 Supplier 역순 잠금을 금지해 중복 재고 처리와 stale saleability commit을 막는다.
- 만료 뒤 발견한 입금의 실제 입금시각이 원래 기한 이내면 동일 판매가능 guard와 모든 `TRACKED` option 재고를 한 트랜잭션에서 검증·재확보한다. 전부 성공한 경우에만 정상 입금확정하고 일부만 확보하는 결과는 허용하지 않는다.
- 실제 입금시각이 기한을 지났거나 원자적 재확보가 실패하면 Payment와 PaymentGroup에 `PAYMENT_EXCEPTION` 증적을 기록하고 Order는 같은 transaction에서 `REFUND_REQUESTED`로 끝낸다.
- 예외 처리는 실제 수령한 `BANK_TRANSFER` Payment와 관리자 입금 증적을 exactly once 저장하되 주문 승인, 배송지 노출 또는 공급처 출고 요청을 만들지 않는다. 예외 주문을 정상 주문으로 재개하는 액션은 제공하지 않는다.
- portal snapshot 항목이 하나라도 있는 PaymentGroup에서 실제 입금은 확인됐지만 현재 판매가능 guard가 실패하면 normal/late 경로 모두 whole-group Payment/PaymentGroup `PAYMENT_EXCEPTION`을 기록하고 배송 그룹마다 `RefundReason.SALE_UNAVAILABLE_AT_DEPOSIT`, `RefundStatus.REQUESTED` Refund를 만든다. Normal pre-expiry HELD 예약은 같은 transaction에서 exactly once RELEASED로 끝내고, late tentative 재확보는 rollback해 기존 RELEASED를 유지한다. Fulfillment/PII/supplier work는 만들지 않으며 portal 항목이 전혀 없는 legacy group의 기존 validation error는 유지한다.
- 늦은 시각 또는 재고 재확보 실패는 배송 그룹마다 `RefundReason.LATE_DEPOSIT_EXCEPTION`, `RefundStatus.REQUESTED` Refund를 자동 생성한다. 두 reason 모두 order별 unique/idempotency guard로 재시도나 중복 요청이 Refund를 두 번 만들지 않게 한다.
- Reason priority는 금액 불일치가 첫 번째다. 금액이 정확하지만 qualifying 미입금 `CANCELLED`이면 terminal cancellation을 두 번째로 평가하고 `LATE_DEPOSIT_EXCEPTION` 주문별 환불로 끝낸다. 나머지 pending/expired portal 경로에서만 current saleability/time-valid contract/mode 실패를 `SALE_UNAVAILABLE_AT_DEPOSIT`으로 먼저 분류하고, 그 guard가 모두 통과한 뒤의 늦은 timestamp 또는 재확보 실패를 `LATE_DEPOSIT_EXCEPTION`으로 분류한다.
- Portal 입금 예외는 Payment와 PaymentGroup에 `PAYMENT_EXCEPTION` 증적을 남기되 같은 transaction에서 Order를 최종 `REFUND_REQUESTED`로 보낸다. 두 planned reason 모두 checkout과 주문 내역에서 `REFUND_PROCESSING` / `입금 확인 및 환불 처리 중`으로 표시한다.
- 공급처 주문·알림 API에는 `PAYMENT_EXCEPTION`, 늦은 입금 Payment와 Refund를 노출하지 않는다.
- 정확한 금액의 late/saleability/qualifying unpaid-cancelled 예외는 Order별 환불 완료 범위에 따라 PaymentGroup이 `PARTIALLY_REFUNDED` 또는 `REFUNDED`가 될 수 있다. 금액 불일치의 단일 결제그룹 Refund는 실제 수령액 전체만 완료하며 PaymentGroup과 모든 포함 Order를 항상 `REFUNDED`로 끝내고 `PARTIALLY_REFUNDED`를 사용하지 않는다.

### Planned System Impact

- scheduler 만료, 금액 불일치 그룹 환불, normal/late 판매불가 입금 기록과 자동 Refund 생성은 각각 idempotent해야 하고 중단 후 안전하게 재시도할 수 있어야 한다.
- 금액 불일치·late/saleability·qualifying unpaid-cancelled 예외 생성과 각 실제 수동 환불 완료는 idempotency key/request hash/immutable result replay를 가져야 한다. DB replay는 실제 은행 송금 중복을 되돌릴 수 없으므로 완료 응답을 잃은 운영자는 새로 송금하지 않고 같은 key로 기록을 재조회·재시도한다.
- 예외 결제의 고객 안내와 Coreable 환불 작업 큐를 제공하되 supplier tenant query에서는 항상 제외해야 한다.

## Excluded Payment Methods

- Toss Payments를 포함한 PG 계약과 연동은 진행하지 않는다.
- 계좌입금 외 결제 수단을 추가하려면 기존 결정을 변경하고 별도 정책 검토부터 다시 수행한다.

## System Impact

- 서버에는 입금 계좌 설정과 관리자 입금확인 권한이 필요하다.
- 판매 개시 여부는 서버의 `app.sales.enabled`가 단일 기준이며 운영 기본값은 비활성이다.
- 결제 완료 처리는 관리자 입금확인 후에만 확정한다.
- `Payment`와 `Order` 상태를 분리한다.
- `PaymentGroup`은 계좌입금 1건과 여러 배송 그룹 주문을 연결한다.
- 환불 실행 결과를 별도 기록해야 한다.
- 계좌입금 환불 실행 결과는 `refunds`에 수동 환불 관리자, 완료 시각, 환불 사유, 환불 계좌 메모를 기록한다.
- 주문 확정 처리는 주문 단위 lock 또는 unique constraint로 한 번만 성공해야 한다.
- `PAYMENT_PENDING`은 입금대기이며 입금 webhook은 사용하지 않는다.
- 미입금 취소는 관리자 수동 액션으로 처리한다.
- 입금확인, 미입금취소, 수동환불 완료는 `OrderStatusHistory`와 관리자 action history에 남겨야 한다.
- 환불 모델은 결제 그룹(PaymentGroup) 안의 배송 그룹 주문 단위 환불을 지원해야 한다.
- 환불 금액은 환불 대상 배송 그룹 주문 금액의 합계와 일치해야 한다.
- 배송 그룹 주문 단위 환불 성공 시 남은 환불 가능 금액에 따라 `PaymentGroup`과 `Payment`를 `REFUNDED` 또는 `PARTIALLY_REFUNDED`로 갱신한다. Implemented by DS-15.
- 고객 주문 내역 API는 결제 성공 후 확정된 주문부터 노출한다.

## Open Questions

None
