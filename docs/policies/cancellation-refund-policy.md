# Cancellation And Refund Policy

Status: Confirmed

## Purpose

고객 취소, 공급처 품절, 환불, 반품, 교환 기준을 정한다.

## Policy Areas

- 고객 직접 취소 가능 시점
- 공급처 발주 후 취소 기준
- 배송 후 반품/교환 지원 범위
- 고객 클레임 접수 범위
- 클레임 사유와 증빙 기준
- 반품/교환 배송비 부담 기준
- 공급처 품절 시 처리
- 부분 품절 처리
- 환불 사유 분류
- 환불 완료 고객 고지

## Initial Direction

- 공급처 발주 전에는 고객 직접 취소를 허용한다.
- 공급처 발주 후 취소는 관리자 승인 대상으로 둔다.
- 배송 후 반품/교환은 클레임 접수와 관리자 수동 심사로 처리한다.
- 공급처 품절 시 해당 배송 그룹 주문 금액을 환불한다.
- 부분 품절은 MVP에서 배송 그룹 주문 단위로 취소/환불한다.
- 배송 후 반품/교환은 MVP에서는 요청 접수와 수동 처리로 시작한다.

## Confirmed Policy

- 고객 셀프서비스 취소 버튼은 `SUPPLIER_ORDER_PENDING` 상태이면서 `supplierOrderStartedAt`과 `addressLockedAt`이 비어 있는 주문에만 노출한다.
- `SUPPLIER_ORDER_PENDING`은 결제 검증 완료 후 공급처 발주 전 상태를 의미한다.
- 관리자가 공급처 발주 작업을 시작했거나 `SUPPLIER_ORDERED` 이후에는 고객이 직접 취소할 수 없다.
- 공급처 발주 작업 시작 후 배송 전까지 고객은 취소 클레임만 접수할 수 있고, 관리자가 공급처 취소 가능 여부를 확인한 뒤 승인 또는 거절한다.
- 공급처 발주 후 취소 클레임이 승인되면 배송 그룹 주문 단위 환불 흐름으로 처리한다.
- 이미 출고되었거나 공급처 취소가 불가능한 경우 취소 클레임을 거절하거나 배송 후 반품 클레임으로 전환 안내한다.
- 공급처 품절 시 해당 배송 그룹 주문 취소와 해당 금액 환불로 처리한다.
- 하나의 결제 그룹에 여러 배송 그룹 주문이 포함된 경우, 특정 배송 그룹 주문만 부분 취소/부분 환불할 수 있다.
- MVP에서 허용하는 부분 취소/부분 환불의 최소 단위는 배송 그룹 주문이다.
- B-102의 `PAYMENT_AMOUNT_MISMATCH`는 예외다. 실제 수령액이 주문 합계와 다르므로 배송 그룹에 임의 배분하지 않고 결제그룹 단위 Refund 1건으로 실제 수령액 전부를 반환한다.
- 배송 그룹 주문 내부의 상품, 옵션, 수량 단위 부분 취소/부분 환불은 MVP에서 지원하지 않는다.
- 배송 그룹 주문 내부에서 일부 상품 또는 일부 수량만 품절이면 MVP에서는 해당 배송 그룹 주문 전체를 취소/환불한다.
- 배송 후 반품/교환은 MVP에서 자동 처리하지 않고 클레임 접수와 관리자 수동 심사로 처리한다. Implemented by DS-37.
- 단순 변심 반품/교환 클레임은 배송 완료일로부터 7일 이내 접수된 건만 심사한다. Implemented by DS-37.
- 상품 하자, 오배송, 상품 정보와 다름, 배송 문제 클레임은 배송 완료일로부터 3개월 이내이면서 고객이 그 사실을 안 날 또는 알 수 있었던 날부터 30일 이내 접수된 건만 심사한다. 현재 구현은 배송 완료일 기준 3개월을 강제하며, 인지일 입력은 planned로 남긴다.
- 단순 변심 반품/교환의 반환 또는 재배송 비용은 고객 부담을 기본으로 한다.
- 상품 하자, 오배송, 상품 정보와 다름, 판매자 또는 배송 귀책 배송 문제의 반환 또는 재배송 비용은 운영자 부담을 기본으로 한다.
- 단순 변심 클레임에서는 고객에게 별도 취소 위약금, 손해배상, 포장비, 보관비를 청구하지 않는다.
- 상품 하자, 오배송, 상품 정보와 다름, 배송 문제 클레임은 사진 증빙을 필수로 받고, 필요 시 운송장, 포장 상태 사진, 동영상, 추가 설명을 요청할 수 있다. 이미지 증빙 저장은 B-015에서 구현했다.
- 단순 변심 클레임은 사진 증빙을 기본 필수로 하지 않지만, 상품 상태 확인이 필요한 경우 관리자가 추가 증빙을 요청할 수 있다.
- 발주 후 취소, 관리자 환불, 반품/교환 승인 건은 관리자 승인 후 배송 그룹 주문 단위 환불을 기본으로 한다. Implemented by DS-38.
- 고객 셀프서비스 취소 조건을 만족하는 `SUPPLIER_ORDER_PENDING` 주문은 고객 셀프서비스 취소 흐름으로 처리하되, 실제 계좌환불 완료 전까지 최종 환불 완료로 표시하지 않는다.
- 반품이 필요한 환불은 반품 상품 입고와 관리자 검수 후 결제 수단에 맞는 환불을 실행한다.
- 계좌입금 MVP에서 반품이 필요한 환불은 반품 상품 입고와 관리자 검수 후 `Refund: REQUESTED`, `Order: REFUND_REQUESTED`, `Claim: REFUND_PROCESSING`으로 전환하고, 실제 이체 완료를 관리자가 수동 환불 완료로 기록한다. Implemented by B-044.
- 상품 미출고 취소처럼 반환받을 상품이 없는 환불은 취소 승인 후 결제 수단에 맞는 환불을 실행한다.
- 계좌입금 MVP에서 반품 상품 입고가 필요한 환불은 반품 상품 입고 확인일로부터 3영업일 이내 수동 계좌환불 완료를 목표로 한다.
- B-044 범위에서는 반품 배송비 차감 자동 계산을 하지 않는다. 배송비 부담 또는 차감이 필요한 건은 관리자가 고객 안내와 처리 메모로 별도 관리한다.
- 반환받을 상품이 없는 계좌입금 환불은 취소 승인일로부터 3영업일 이내 수동 계좌환불 완료를 목표로 한다.
- 결제 승인 완료 주문은 실제 계좌환불 완료 전까지 최종 취소 또는 환불 완료 상태로 전환하지 않는다.
- 결제 승인 완료 주문의 고객 취소 또는 관리자 환불은 `REFUND_REQUESTED` 상태를 거쳐 처리한다.
- `REFUNDED`는 실제 계좌환불 완료가 확인된 뒤에만 사용할 수 있다.
- 실제 계좌환불 완료 기록에는 환불 은행, 계좌번호, 예금주, 이체시각, 거래 식별 메모와 관리자 사유가 모두 필요하다. 이 계좌 증적은 관리자 주문 상세에만 표시하고 고객 주문·알림·환불 목록에는 노출하지 않는다.
- `CANCELLED`는 입금확정 전 주문 취소 또는 결제 미완료 주문 종료에 사용하고, 입금확인 완료 주문의 최종 환불 완료 상태는 `REFUNDED`로 사용한다.
- 환불 처리 중에는 고객에게 환불 접수 또는 환불 처리 중 상태를 노출한다.
- 계좌환불 미완료 시 고객에게 환불 완료 상태를 노출하지 않는다.
- 환불 실패 건은 관리자 재시도 또는 수동 확인 대상으로 전환한다.
- 환불 완료 후 고객에게 환불 완료 상태를 노출한다.

## Supplier Portal Cancellation, Shortage, And Claim Boundary — B-102/B-103 Implemented, B-105 Planned

Status: The B-102 received-payment exception/refund boundary and B-103 portal fulfillment/PII boundary are Implemented. B-105 shortage/claim facts remain Planned. Existing DS-14, DS-15, DS-37, DS-38, B-015, and B-044 behavior remains authoritative for legacy and Coreable-managed flows outside those implemented slices.

- 공급처 포털 주문은 관리자 입금확인과 동시에 공급처에 노출되고 배송지가 잠기므로, 그 시점부터 고객 셀프서비스 취소를 허용하지 않는다. 고객 취소 요청은 Coreable 클레임 검토로 처리한다. Implemented in B-103.
- 기존 Coreable 수동 발주와 Domeggook 주문에는 현재의 `SUPPLIER_ORDER_PENDING`, `supplierOrderStartedAt`, `addressLockedAt` 기반 셀프서비스 취소 규칙을 그대로 적용한다.
- 공급처는 VOIDED 포함 Shipment가 한 번도 등록되지 않은 자기 배송 그룹 주문에 대해서만 품절을 보고할 수 있다. 일부 상품, 옵션 또는 수량만 품절이어도 보고와 환불 검토 단위는 배송 그룹 주문 전체다. Planned in B-105.
- 공급처 품절 제출은 배송 그룹 주문 전체에 REPORTED 사실을 만들고 Fulfillment만 Coreable에 인계하며 Order/Refund는 바꾸지 않는다. Coreable 승인만 기존 `OUT_OF_STOCK`/환불 서비스를 실행하고 거절은 Refund 없이 Coreable owner를 유지한다. 공급처에는 환불 승인·거절·완료 권한이 없다.
- Claim의 승인·거절·완료, Refund의 생성·승인·거절·실제 계좌환불 완료, 주문·배송 상태의 관리자 보정과 고객 CS 판단은 Coreable만 수행한다.
- 공급처는 Coreable이 해당 Claim에 열린 supplier task를 만든 경우에만 task requested type과 일치하는 `SHIPMENT_STOP_RESULT`, `RETURN_INSTRUCTIONS`, `RETURN_RECEIVED`, `INSPECTION_RESULT` 사실을 idempotent·append-only로 추가할 수 있다. task detail은 자기 safe fact id/type/payload/correction reference/time만 제공하고, 정정은 같은 task의 앞선 사실을 참조하는 새 사실로 추가한다. Planned in B-105.
- Coreable의 supplier task 생성은 idempotency key/request hash로 재시도 안전해야 하고, admin list/detail은 Claim/order linkage, 내부 context와 complete same-task fact history를 제공해야 한다. 사실을 읽는 것만으로 Claim/Refund 상태를 바꾸지 않는다.
- 공급처 claim fact는 Claim, Order, Shipment 또는 Refund 상태를 직접 변경하지 않으며 Coreable 판단의 근거로만 사용한다.
- 승인된 Claim의 처리를 위해 공급처 PII 접근을 다시 열 때는 Coreable 관리자가 grant/extension용 `RETURN_COORDINATION_REQUIRED|EXCHANGE_COORDINATION_REQUIRED|REFUND_COORDINATION_REQUIRED` 또는 revoke용 `CLAIM_ACCESS_NO_LONGER_REQUIRED` reason code와 요청시점부터 최대 30일인 만료시각을 입력해 append-only 이력을 남긴다. 자유문은 받지 않으며 `APPROVED`, `RETURN_WAITING`, `RETURN_RECEIVED`, `REFUND_PROCESSING`, `EXCHANGE_SHIPPING` 외 상태가 되거나 만료되면 즉시 masking한다. Implemented in B-103; B-105 task/fact input은 grant를 만들거나 연장하지 않는다.
- qualifying 미입금 `CANCELLED`가 아닌 portal-origin PaymentGroup의 exact receipt는 판매/계약/mode guard 실패면 먼저 `SALE_UNAVAILABLE_AT_DEPOSIT`을 선택하고, 그 guard가 모두 통과한 뒤의 늦은 입금시각 또는 재고 재확보 실패만 `LATE_DEPOSIT_EXCEPTION`으로 분류한다. 같은 transaction에서 reason-matched `REQUESTED` Refund를 주문당 한 번만 만들고 Order를 `REFUND_REQUESTED`로 끝낸다. 공급처는 두 예외 결제와 환불을 조회하거나 처리할 수 없다. Implemented in B-102.
- 실제 입금액이 PaymentGroup 총액과 다르면 위 portal reason보다 `PAYMENT_AMOUNT_MISMATCH`가 먼저다. Portal/legacy 공통으로 실제 수령액 전체를 `PAYMENT_GROUP` scope Refund 한 건에 기록하고 모든 포함 Order를 `REFUND_REQUESTED`로 보내며, 공급처에는 Fulfillment·PII·알림·환불 존재를 만들거나 노출하지 않는다. 정상 주문 재개는 없고 실제 계좌환불 완료 뒤 전체를 `REFUNDED`로 끝낸다. Implemented in B-102.
- 미입금 취소 뒤 금액 불일치 실입금이 발견된 경우에도, 기존 수령 Payment/Refund/Fulfillment가 없고 모든 포함 Order가 미입금 취소 결과라면 같은 결제그룹 전액 환불 경로를 사용한다. Implemented in B-102.
- 미입금 취소 뒤 정확한 금액의 실입금이 발견된 경우에도 주문을 되살리지 않는다. Portal/legacy 공통으로 immutable 배송 그룹 금액의 `LATE_DEPOSIT_EXCEPTION` Refund를 Order마다 하나씩 만들고 전액 환불 뒤 고객이 새 checkout을 만들게 한다. Implemented in B-102.
- 복수 송장 portal 주문의 7일/3개월 Claim 기간은 legacy singular shipment가 아니라 주문의 마지막 유효 배송완료인 `max(non-voided Shipment.deliveredAt)`부터 계산한다.

### Mixed System Impact

- B-102 late/saleability/qualifying unpaid-cancelled refund 생성은 order별 unique/idempotency guard로, amount-mismatch 환불은 payment-group scope unique/idempotency guard로 중복 Refund를 막는다.
- B-103은 `fulfillment.channel = SUPPLIER_PORTAL`이거나 `addressLockedAt`이 있는 주문을 기존 셀프서비스 취소 API에서 거절한다. 현재 셀프서비스 취소 규칙은 `COREABLE_MANUAL`과 `DOMEGGOOK_API` channel에만 이어 적용한다. Implemented.
- B-105 품절 제출·Coreable 검토·portal Shipment는 같은 Order/Fulfillment lock을 사용하고 첫 송장 존재 여부와 supplier tenant를 재검증해야 한다. 중복 제출은 첫 safe report를 반환하며 승인된 경우에만 배송 그룹 주문 전체 환불 처리를 한 번 시작한다.
- B-105 supplier claim fact 저장소는 append-only actor, claim, fact type, payload, created-at 이력을 보존하되 기존 Claim/Refund 관리자 전이 API를 재사용하거나 우회하지 않아야 한다.

## System Impact

- 취소 가능 여부는 주문 상태와 `supplierOrderStartedAt`, `addressLockedAt`을 함께 기준으로 계산한다.
- `SUPPLIER_ORDER_PENDING`이면서 공급처 발주 작업이 시작되지 않은 주문만 고객 직접 취소가 가능하다. Implemented by DS-14.
- `SUPPLIER_ORDERED` 이후는 관리자 승인 또는 수동 처리 흐름이 필요하다. Implemented for cancellation claims by DS-14.
- `Claim` 모델이 필요하다. Implemented by DS-14.
- 클레임 처리 상태와 환불 상태는 분리한다.
- 클레임 승인 후 실제 환불은 `Refund` 모델과 결제 수단별 완료 기준을 따른다.
- 배송 그룹 주문 단위 환불을 허용하되, 상품/옵션/수량 단위 환불을 제외해 주문/결제/환불 모델 복잡도를 제한한다.
- 고객 취소 버튼 노출 여부는 주문 상태로 판단해야 한다.
- 발주 후 취소/반품/교환은 고객 셀프서비스가 아니라 클레임 접수와 관리자 처리 흐름으로 모델링해야 한다. Cancellation claim flow is implemented by DS-14; return/exchange claim creation and review are implemented by DS-37; delivered return receive/refund completion flow is implemented by B-044; customer claim list/detail and image evidence storage are implemented by B-015.
- 환불 사유 enum이 필요하다. Implemented by DS-15.
- 환불 상태는 요청, 관리자 승인/거절, 실제 계좌환불 완료를 구분한다. Implemented by DS-15 and B-044.
- 실제 계좌이체가 완료되지 않은 환불은 `COMPLETED`로 처리하지 않고 관리자 확인 대상으로 유지한다.
- 환불 완료 고객 고지는 실제 계좌환불 완료 이후에만 발송한다.
- 고객 취소/환불 정책 페이지는 `GET /api/policies/cancellation-refund`으로 노출한다. Implemented by DS-16.
- 결제 후 공급처 품절 가능성과 배송 그룹 주문 단위 환불 안내는 `GET /api/policies/stock-risk`으로 노출한다. Implemented by DS-16.

## Open Questions

None

## Refund Reason Values

Initial values:

- `CUSTOMER_CANCEL`
- `SUPPLIER_OUT_OF_STOCK`
- `ADMIN_CANCEL`
- `PAYMENT_AMOUNT_MISMATCH`
- `RETURN_REQUESTED`
- `EXCHANGE_REQUESTED`
- `DELIVERY_GROUP_OUT_OF_STOCK`

Implemented B-102 addition:

- `LATE_DEPOSIT_EXCEPTION`: 늦은 timestamp, 재고 재확보 실패 또는 qualifying 미입금취소 뒤 발견된 exact receipt의 Order별 terminal refund
- `SALE_UNAVAILABLE_AT_DEPOSIT`

## Claim Types

Initial values:

- `CANCEL`
- `RETURN`
- `EXCHANGE`

## Claim Reason Values

Initial values:

- `SIMPLE_CHANGE_OF_MIND`
- `DEFECT`
- `WRONG_DELIVERY`
- `DIFFERENT_FROM_PRODUCT_INFO`
- `DELIVERY_ISSUE`

## Claim Status Values

Initial values:

- `REQUESTED`
- `UNDER_REVIEW`
- `EVIDENCE_REQUESTED`
- `APPROVED`
- `REJECTED`
- `RETURN_WAITING`
- `RETURN_RECEIVED`
- `REFUND_PROCESSING`
- `EXCHANGE_SHIPPING`
- `COMPLETED`
- `WITHDRAWN`

## Claim Shipping Cost Bearer

Initial values:

- `CUSTOMER`
- `SELLER`
- `UNDECIDED`

## Refund Status Values

Initial values:

- `REQUESTED`
- `APPROVED`
- `PG_CANCEL_REQUESTED`
- `PROCESSING`
- `COMPLETED`
- `FAILED`
- `RETRY_REQUIRED`
- `REJECTED`
- `MANUAL_REVIEW_REQUIRED`
