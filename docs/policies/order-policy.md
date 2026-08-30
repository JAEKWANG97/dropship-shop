# Order Policy

Status: Confirmed

## Purpose

주문 생성, 주문 만료, 배송지 변경, 주문 상태 전이 기준을 정한다.

## Policy Areas

- 주문 생성 시점
- 결제 전 주문 보관 기간
- 주문 금액 계산 기준
- 배송지 변경 가능 시점
- 주문 상태 정의
- 주문 상태 변경 주체
- 고객에게 노출할 주문 상태
- 주문 상태 전이표
- 금지 전이
- 주문 내역 노출 범위
- 알림 트리거

## Initial Direction

- 주문은 입금 안내 전에 `PAYMENT_PENDING` 상태로 생성한다.
- 관리자 입금확인 후 `SUPPLIER_ORDER_PENDING`으로 전환한다.
- 입금대기 주문은 일정 시간이 지나면 미입금 취소 처리한다.
- 주문 금액은 서버가 계산한다.
- 고객이 제출한 총액은 신뢰하지 않는다.
- 고객 직접 배송지 변경은 주문서 정책 확인 전까지만 허용한다.

## Confirmed Policy

- 결제 그룹(PaymentGroup)과 배송 그룹별 주문은 계좌입금 안내 전에 먼저 생성한다.
- 입금 전 배송 그룹 주문의 초기 상태는 `PAYMENT_PENDING`이다.
- `PAYMENT_PENDING` 주문은 아직 확정 주문이 아니다.
- 계좌입금 안내는 서버에 생성된 결제 그룹(PaymentGroup) 식별자와 서버 계산 결제 그룹 총액을 기준으로 표시한다.
- 고객은 안내된 계좌로 주문 금액을 입금하고, 관리자가 실제 입금 내역을 확인한 뒤 포함 주문들을 확정한다.
- 관리자 입금확인이 성공하면 결제 그룹(PaymentGroup)에 포함된 배송 그룹 주문들은 `SUPPLIER_ORDER_PENDING` 상태로 진입한다.
- `PAYMENT_PENDING` 주문의 기본 입금 기한은 생성 후 24시간이다.
- 기한 내 입금이 확인되지 않으면 관리자가 미입금 취소 처리한다.
- 미입금 취소된 주문은 재입금/재시도 대상이 아니며, 고객은 새 주문을 생성해야 한다.
- `PAYMENT_PENDING` 상태에서는 입금확인 전 주문서 수정으로 배송지를 변경할 수 있다.
- 단, 주문서 통합 확인에는 배송지가 포함되므로 checkout 정책 확인이 완료된 뒤에는 checkout 배송지 변경을 거절한다.
- 주문서 정책 확인 후에는 고객 직접 배송지 변경을 거절한다.
- 변경이 필요하면 고객 문의를 통해 공급처 발주 전에 취소·재주문 또는 운영 보정을 판단한다.
- 관리자가 공급처 발주 작업을 시작해 `addressLockedAt`이 기록된 주문은 운영 보정도 거절한다.
- 주소 잠금은 별도 주문 상태를 추가하지 않고 `addressLockedAt`과 `supplierOrderStartedAt`으로 판단한다.
- `SUPPLIER_ORDERED` 이후에는 고객이 배송지를 직접 변경할 수 없다.
- `SUPPLIER_ORDERED` 이후 배송지 변경은 고객 문의와 관리자 수동 처리 대상으로 본다.
- 고객에게 내부 주문 상태를 그대로 노출하지 않는다.
- 고객 화면에는 내부 상태를 고객용 표시 상태로 매핑해서 노출한다.
- 고객 주문 내역에는 배송 그룹별 주문을 표시하되, 같은 결제 그룹(PaymentGroup)에서 생성된 주문임을 묶어 보여줄 수 있어야 한다.
- 고객 주문 내역은 입금확인 후 확정된 주문만 노출한다.
- `PAYMENT_PENDING`, `EXPIRED`, 미입금 취소 주문은 일반 고객 주문 내역에 노출하지 않고 체크아웃 화면 또는 고객 문의로 다룬다.
- `PREPARING_SHIPMENT`은 MVP 주문 상태에서 제거하고, 공급처 발주 완료 후 송장 입력 전 상태는 `SUPPLIER_ORDERED`로 표현한다.
- 내부 상태는 운영, 결제 검증, 공급처 발주, 환불 정합성을 위한 상태로 유지한다.

## System Impact

- 결제 전 주문과 결제 완료 주문을 명확히 구분해야 한다.
- 관리자 입금확인 시 실제 입금 금액과 서버 결제 그룹(PaymentGroup) 총액을 비교해야 한다.
- 주문 상태 변경 이력이 필요하다.
- 결제 요청과 결제 검증은 결제 그룹(PaymentGroup) ID 또는 checkout number를 기준으로 연결되어야 한다.
- 각 배송 그룹 주문은 결제 그룹(PaymentGroup) ID를 저장해야 한다.
- `PAYMENT_PENDING` 주문은 관리자 입금대기 큐에는 노출하지만 공급처 발주 큐에는 노출하지 않는다.
- 입금대기 주문 만료 처리는 MVP에서 관리자 미입금 취소 수동 액션으로 처리한다.
- 입금 기한 이후 관리자가 입금확인하려면 운영 사유를 남겨야 하며, 자동 확정하지 않는다.
- 고객 직접 배송지 변경 가능 여부는 checkout 정책 확인 시각을 기준으로 판단해야 한다.
- checkout 배송지 변경 가능 여부는 결제 그룹/주문 상태가 `PAYMENT_PENDING`이고 정책 확인 전인지 함께 판단해야 한다.
- checkout 정책 확인 이후 고객 주문 배송지 변경 API는 거절해야 한다.
- 고객용 주문 상태 매핑 계층이 필요하다.
- 관리자 화면은 내부 상태를 볼 수 있지만, 고객 화면은 고객 친화적인 표시 상태를 사용해야 한다.
- 고객 주문 내역 API와 체크아웃/결제 재시도 화면 API는 노출 대상 상태가 달라야 한다.
- 환불 표시 상태는 주문 상태뿐 아니라 `Refund.status`를 함께 보고 계산해야 한다.
- 상태 전이 검증은 fromStatus, actor, action, guard, sideEffect, toStatus 기준으로 구현해야 한다.
- 알림 발송은 상태 전이와 별도 `NotificationLog`에 기록해야 한다.
- 주문 상품에는 상품/옵션 이름과 가격뿐 아니라 주문 시점의 상품 상세/고시 버전 참조를 스냅샷으로 남겨야 한다.

## Open Questions

None

## Customer Display Status Mapping

Initial mapping for checkout, admin support, and exceptional customer-facing states. Failed, pending, and expired payment orders are not shown in normal customer order history.

| Internal status | Customer display status |
| --- | --- |
| `PAYMENT_PENDING` | 입금 대기 |
| `EXPIRED` | 주문 만료 |
| `SUPPLIER_ORDER_PENDING` | 결제 완료 |
| `SUPPLIER_ORDERED` | 상품 준비 중 |
| `SHIPPED` | 배송 중 |
| `DELIVERED` | 배송 완료 |
| `OUT_OF_STOCK` | 품절 안내 |
| `CANCELLED` | 취소 완료 |
| `REFUND_REQUESTED` | 환불 처리 중 |
| `REFUNDED` | 환불 완료 |

Refund-derived display statuses:

| Order status | Refund status | Customer display status |
| --- | --- | --- |
| `REFUND_REQUESTED` | `REQUESTED` / `APPROVED` | 환불 접수 |
| `REFUNDED` | `COMPLETED` | 환불 완료 |

## Order History Visibility

| Surface | Included statuses |
| --- | --- |
| Customer checkout/retry screen | `PAYMENT_PENDING`, `EXPIRED` |
| Customer order history | `SUPPLIER_ORDER_PENDING`, `SUPPLIER_ORDERED`, `SHIPPED`, `DELIVERED`, `OUT_OF_STOCK`, `REFUND_REQUESTED`, `REFUNDED` |
| Admin deposit queue | `PAYMENT_PENDING` |
| Admin supplier order queue | `SUPPLIER_ORDER_PENDING` |
| Admin order queue | All internal statuses except deleted test data |

## State Transition Table

Initial MVP transitions:

| From status | Actor | Action | Guard | Side effect | To status |
| --- | --- | --- | --- | --- | --- |
| none | Customer | Create checkout | Authenticated user, sellable product/option, calculated amount | Create payment group and delivery-group orders | `PAYMENT_PENDING` |
| `PAYMENT_PENDING` | Admin | Confirm bank transfer deposit | Actual deposit amount matches, policy confirmed, sellable product/option, reason recorded | Store bank transfer payment, expose order history, notify payment completed | `SUPPLIER_ORDER_PENDING` |
| `PAYMENT_PENDING` | Admin | Cancel unpaid checkout | Deposit not confirmed by operating deadline, reason recorded | Mark checkout unpaid cancelled | `CANCELLED` |
| `PAYMENT_PENDING` | Admin | Record deposit mismatch | Deposit amount/name/time does not match or needs review | Keep order pending and record admin memo | unchanged |
| `SUPPLIER_ORDER_PENDING` | Customer | Self-service cancel | Supplier order work not started | Create refund | `REFUND_REQUESTED` |
| `SUPPLIER_ORDER_PENDING` | Admin | Start supplier order work | Next operational step confirmed | Set `supplierOrderStartedAt`, `addressLockedAt` | `SUPPLIER_ORDER_PENDING` |
| `SUPPLIER_ORDER_PENDING` | Admin | Supplier ordered | Supplier order placed, evidence recorded | Record fulfillment evidence, notify preparing | `SUPPLIER_ORDERED` |
| `SUPPLIER_ORDER_PENDING` | Admin | Supplier out of stock | Supplier confirmed unavailable | Notify out of stock, create refund | `OUT_OF_STOCK` |
| `SUPPLIER_ORDERED` | Admin | Supplier out of stock before shipment | Supplier confirmed unavailable before carrier/tracking | Notify out of stock, create refund | `OUT_OF_STOCK` |
| `SUPPLIER_ORDERED` | Admin | Enter shipment | Carrier and tracking number exist | Create shipment, notify shipped | `SHIPPED` |
| `SHIPPED` | System/Admin | Mark delivered | Shipment tracking delivered or admin correction reason exists | Set delivered time, notify delivered | `DELIVERED` |
| `OUT_OF_STOCK` | System/Admin | Refund requested | Refund amount equals delivery-group order amount | Create refund | `REFUND_REQUESTED` |
| `REFUND_REQUESTED` | Admin | Manual bank-transfer refund completed | Actual refund completed, reason and account memo recorded | Notify refund completed | `REFUNDED` |
| `SUPPLIER_ORDERED` / `SHIPPED` / `DELIVERED` | Customer/Admin | Claim opened | Claim request valid for order state | Create claim; order status may stay unchanged until refund | unchanged |
| any active status | Admin | Manual correction | Correction action allowed, reason required | Record admin action and status history | defined corrected status |

## Forbidden Transitions

- Paid order to `REFUNDED` without `Refund.status = COMPLETED` and actual manual refund completion.
- Any order to `SHIPPED` without carrier and tracking number.
- Any order to `DELIVERED` without a `Shipment` record and delivered tracking/admin correction evidence.
- `SHIPPED` or `DELIVERED` to `OUT_OF_STOCK` except through claim/manual correction handling.
- `PAYMENT_PENDING` directly to supplier ordering without admin bank-transfer deposit confirmation.
- `EXPIRED` to confirmed order; customer must create a new checkout.
- Product/option/quantity-level partial refund inside one delivery-group order in MVP.

The memo-only deposit-mismatch row above is retained as B-068 history. Implemented B-102 replaces new memo-only writes after admin-web cutover when an identified nonzero bank receipt has an amount different from the PaymentGroup total. An unattributed transfer remains external bank reconciliation and does not mutate a guessed Order.

## Supplier Portal Order Extension — B-102/B-103 Implemented, B-104 Planned

Status: B-102 inventory reservation, expiry, deposit revalidation and received-payment exception behavior and B-103 portal fulfillment exposure/address locking are Implemented. B-104 multi-shipment behavior remains Planned.

- `SUPPLIER_PORTAL` 주문은 입금확인과 동시에 공급처 출고 큐에 노출되고 `addressLockedAt`이 기록된다. 공급처 수락 액션과 고객 셀프서비스 취소·배송지 변경은 제공하지 않는다. Implemented in B-103.
- 셀프서비스 취소 전이는 `fulfillment.channel in (COREABLE_MANUAL, DOMEGGOOK_API)`이고 `addressLockedAt`이 없는 주문에만 이어 적용한다. `SUPPLIER_PORTAL` 주문은 기존 취소 API에서 거절하고 Coreable 클레임으로 처리한다. Implemented in B-103.
- 만료된 tracked 주문의 실제 입금시각이 원래 기한 이내이고 current saleability/compliance guard와 모든 재고 재확보가 원자적으로 성공한 경우에만 `SUPPLIER_ORDER_PENDING`으로 확정한다. Implemented in B-102; Fulfillment 생성과 공급처 노출은 포함하지 않는다.
- portal-origin 항목이 포함된 PaymentGroup의 actual receipt 뒤 current saleability 실패는 normal/late 모두 `SALE_UNAVAILABLE_AT_DEPOSIT`, 실제 입금시각이 기한을 지났거나 재확보가 실패하면 `LATE_DEPOSIT_EXCEPTION`으로 Payment/PaymentGroup `PAYMENT_EXCEPTION` 증적을 exactly once 저장하고 Order는 같은 transaction에서 `REFUND_REQUESTED`로 끝낸다. 정상 주문으로 재개하거나 공급처 큐에 노출하지 않는다. Implemented in B-102.
- Portal 입금 예외는 `PAYMENT_EXCEPTION`을 별도 Order 최종 상태로 커밋하지 않고 exception 이력과 reason별 Refund를 같은 transaction에 남긴 뒤 `REFUND_REQUESTED`로 끝낸다. 두 reason 모두 checkout과 주문 내역에서 `REFUND_PROCESSING` / `입금 확인 및 환불 처리 중`으로 표시한다. Implemented in B-102.
- 포털 송장 등록은 실제 출고와 다르므로 `TRACKING_REGISTERED`를 사용한다. 공급처는 배송완료를 설정할 수 없다. Planned in B-104.

### Payment And Portal Visibility — B-102/B-103 Implemented, B-104 Planned

| Surface | Included states |
| --- | --- |
| Customer checkout/status | Implemented B-102: portal/legacy amount mismatch, portal exact-amount exceptions and portal/legacy qualifying unpaid-cancelled exact receipts expose only `REFUND_PROCESSING` / `입금 확인 및 환불 처리 중` plus the applicable refund amount |
| Customer order history | Implemented B-102: `SUPPLIER_ORDER_PENDING` and every received-payment exception's `REFUND_PROCESSING` projection without depositor, transaction reference, admin reason or account evidence. Planned B-104: `TRACKING_REGISTERED` (`송장 등록 · 배송조회 가능`) |
| Admin payment exception/refund queue | Implemented B-102: `PaymentGroup=PAYMENT_EXCEPTION`; either one `PAYMENT_GROUP/PAYMENT_AMOUNT_MISMATCH` Refund or the exact-amount exception's per-Order Refunds, with scope-correct identifiers |
| Supplier fulfillment queue | Implemented B-103: paid orders with `fulfillment.channel = SUPPLIER_PORTAL`; exclude `PAYMENT_EXCEPTION` and refund-only data |

### Portal Transitions — B-102/B-103 Implemented, B-104 Planned

The reservation, expiry and received-payment rows through qualifying unpaid `CANCELLED` are Implemented in B-102. Supplier queue exposure/address locking is Implemented in B-103; tracking and delivery rows belong to Planned B-104.

| From status | Actor | Action | Guard | Side effect | To status |
| --- | --- | --- | --- | --- | --- |
| `PAYMENT_PENDING` | System | Expire portal reservation | Deadline reached, deposit not confirmed, reservation still `HELD` | Release tracked reservation and expire payment group/orders | `EXPIRED` |
| `PAYMENT_PENDING` / `EXPIRED` / qualifying unpaid `CANCELLED` | Admin | Record amount-mismatched bank receipt | Identified positive actual receipt differs from immutable PaymentGroup total; replay checked first; a cancelled group must have only the unpaid-cancel outcome and no prior receipt Payment, Refund or Fulfillment | Preserve full receipt, release remaining HELD once, create one actual-amount `PAYMENT_GROUP` Refund, no fulfillment/supplier exposure | `REFUND_REQUESTED` for every included Order |
| `PAYMENT_PENDING` | Admin | Record received but now-unsellable portal payment | Exact amount received; any portal snapshot item fails current saleability/compliance | Whole-group Payment/PaymentGroup exception, one `SALE_UNAVAILABLE_AT_DEPOSIT` Refund per Order, no fulfillment | `REFUND_REQUESTED` |
| `PAYMENT_PENDING` | Admin | Record exact portal payment whose actual timestamp is late before scheduler expiry | Exact amount received; current saleability/contract/mode guards pass; `depositedAt` is after the original deadline | Release every remaining HELD reservation exactly once, record whole-group Payment/PaymentGroup exception, create one `LATE_DEPOSIT_EXCEPTION` Refund per Order, no fulfillment | `REFUND_REQUESTED` |
| `EXPIRED` | Admin | Record eligible late deposit | Exact amount received within original deadline; saleability guards pass; all tracked stock reacquired atomically | Store Payment and evidence once and consume the reacquired reservation; B-102 creates or routes no Fulfillment | `SUPPLIER_ORDER_PENDING` |
| `EXPIRED` | Admin | Record exceptional exact late deposit | Exact amount received; saleability failure, amount received after deadline, or tracked stock reacquisition failed | In one transaction store Payment/PaymentGroup exception and Order history, create one order-scoped `REQUESTED` Refund with the matching B-102 reason, notify customer, create no fulfillment | `REFUND_REQUESTED` |
| qualifying unpaid `CANCELLED` | Admin | Record exact receipt found after cancellation | Exact group amount; unpaid cancellation is sole terminal outcome; no received Payment, Refund or Fulfillment exists | Store Payment/PaymentGroup exception, release any stray HELD once, create one immutable-amount `LATE_DEPOSIT_EXCEPTION` Refund per Order; never reacquire, fulfill or expose supplier data | `REFUND_REQUESTED` |
| `SUPPLIER_ORDER_PENDING` | Supplier/Admin takeover | Register first portal tracking | Portal channel, matching operational owner, positive allocation within remaining quantity | Create registered Shipment and official tracking link | `TRACKING_REGISTERED` |
| `TRACKING_REGISTERED` | Supplier | Register or correct tracking | Own portal order; positive remaining allocation for new Shipment, or existing undelivered Shipment for carrier/tracking-only correction | Create another Shipment or append correction history; allocation errors require admin void + new registration | unchanged |
| `TRACKING_REGISTERED` | Admin | Void or correct portal shipment | Non-delivered Shipment, idempotency key, expected version and reason recorded | Preserve history and recalculate to `SUPPLIER_ORDER_PENDING` if no valid Shipment remains, otherwise `TRACKING_REGISTERED`/`DELIVERED` | recalculated portal status |
| `TRACKING_REGISTERED` | Admin | Mark portal shipment delivered | `registeredAt <= deliveredAt <= evidenceObservedAt <= now` and reason recorded | Mark Shipment delivered and recalculate order aggregate | `TRACKING_REGISTERED` or `DELIVERED` |
| `DELIVERED` | Admin | Correct manual portal delivery | Planned admin-completed portal Shipment, no later Claim/Refund, idempotency key, expected version and reason | Reopen tracking or correct delivered time, preserve original evidence, notify customer on rollback | `TRACKING_REGISTERED` or `DELIVERED` |

### Portal Guards — B-102/B-103 Implemented, B-104 Planned

The first three payment-exception guards are Implemented in B-102. Fulfillment exposure/self-cancel is Implemented in B-103, and multi-shipment aggregate guards are Planned B-104.

- Late-deposit Refund creation is automatic and idempotent per delivery-group order; retry must not create a second Refund.
- Amount mismatch has reason priority over saleability, deadline, and stock. It creates one payment-group Refund for the exact received amount, never one Refund per Order, and cannot resume to normal confirmation.
- Portal exception evidence can never resume an Order to `SUPPLIER_ORDER_PENDING`.
- `SUPPLIER_PORTAL` self-cancel, address change and supplier accept transitions are forbidden after fulfillment exposure.
- `TRACKING_REGISTERED` is included in customer cancellation-Claim eligibility and every admin Claim/refund/status allowlist, but not in direct self-cancel. An approved cancellation may move to refund only after active tracking is voided/stopped; evidence of actual shipment routes the case to return handling.
- `DELIVERED` requires all ordered quantities allocated to non-voided Shipments and Coreable delivery evidence for every such Shipment.
- Customer Claim eligibility for a multi-shipment portal Order uses aggregate delivery time `max(non-voided Shipment.deliveredAt)`, never the compatibility singular Shipment.

## Notification Triggers

Initial transaction notification triggers:

| Trigger | Channel | Required log |
| --- | --- | --- |
| Payment pending | SMS, checkout detail status | `NotificationLog` |
| Payment completed | SMS, order detail status | `NotificationLog` |
| Supplier out of stock | SMS, order detail status | `NotificationLog` |
| Shipment started | SMS, order detail status | `NotificationLog` |
| Delivery completed | SMS, order detail status | `NotificationLog` |
| Delay notice | SMS, order detail status | `NotificationLog` |
| Claim status changed | SMS, order detail status | `NotificationLog` |
| Refund completed | SMS, order detail status | `NotificationLog` |

B-011 implements transactional SMS notification logs for payment pending, payment completed, supplier out-of-stock, shipment started, delivery completed, manual delay notice, claim status changed, and refund completed. Delay notice is sent by manual admin action; automatic scheduler logic is deferred.
