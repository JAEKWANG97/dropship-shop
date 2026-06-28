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

- 주문은 결제 요청 전에 `PAYMENT_PENDING` 상태로 생성한다.
- 결제 성공 후 `SUPPLIER_ORDER_PENDING`으로 전환한다.
- 결제 전 주문은 일정 시간이 지나면 만료 처리한다.
- 주문 금액은 서버가 계산한다.
- 고객이 제출한 총액은 신뢰하지 않는다.
- 배송지 변경은 공급처 발주 전까지만 허용한다.

## Confirmed Policy

- 결제 그룹(PaymentGroup)과 배송 그룹별 주문은 결제 요청 전에 먼저 생성한다.
- 결제 전 배송 그룹 주문의 초기 상태는 `PAYMENT_PENDING`이다.
- `PAYMENT_PENDING` 주문은 아직 확정 주문이 아니다.
- PG 결제 요청은 서버에 생성된 결제 그룹(PaymentGroup) 식별자와 서버 계산 결제 그룹 총액을 기준으로 진행한다.
- 결제 성공 후 서버가 PG 승인 결과와 결제 그룹 총액을 검증한 뒤 포함 주문들을 확정한다.
- 결제 승인 검증이 성공하면 결제 그룹(PaymentGroup)에 포함된 배송 그룹 주문들은 `SUPPLIER_ORDER_PENDING` 상태로 진입한다.
- PG 결제가 승인됐지만 주문을 확정할 수 없으면 주문은 `PAYMENT_EXCEPTION` 상태로 진입하고 공급처 발주를 차단한다.
- `PAYMENT_EXCEPTION`은 결제 취소 처리 중 또는 결제 확인 필요 상태로 고객에게 노출한다.
- `PAYMENT_PENDING` 주문은 생성 후 30분이 지나면 만료 처리한다.
- 만료된 결제 대기 주문은 결제 재시도 대상이 아니며, 고객은 새 주문을 생성해야 한다.
- `PAYMENT_PENDING` 상태에서는 결제 전 주문서 수정으로 배송지를 변경할 수 있다.
- 단, 주문서 통합 확인에는 배송지가 포함되므로 checkout 정책 확인이 완료된 뒤에는 checkout 배송지 변경을 거절한다.
- 결제 완료 후에는 `SUPPLIER_ORDER_PENDING` 상태까지만 고객이 배송지를 직접 변경할 수 있다.
- 단, 관리자가 공급처 발주 작업을 시작해 `addressLockedAt`이 기록된 주문은 `SUPPLIER_ORDER_PENDING` 상태라도 고객 직접 배송지 변경을 거절한다.
- 주소 잠금은 별도 주문 상태를 추가하지 않고 `addressLockedAt`과 `supplierOrderStartedAt`으로 판단한다.
- `SUPPLIER_ORDERED` 이후에는 고객이 배송지를 직접 변경할 수 없다.
- `SUPPLIER_ORDERED` 이후 배송지 변경은 고객 문의와 관리자 수동 처리 대상으로 본다.
- 고객에게 내부 주문 상태를 그대로 노출하지 않는다.
- 고객 화면에는 내부 상태를 고객용 표시 상태로 매핑해서 노출한다.
- 고객 주문 내역에는 배송 그룹별 주문을 표시하되, 같은 결제 그룹(PaymentGroup)에서 생성된 주문임을 묶어 보여줄 수 있어야 한다.
- 고객 주문 내역은 결제 성공 후 확정된 주문과 고객에게 처리 상태를 보여줘야 하는 결제 예외 주문만 노출한다.
- `PAYMENT_PENDING`, `EXPIRED`, 결제 실패 주문은 일반 고객 주문 내역에 노출하지 않고 체크아웃 재시도/결제 결과 화면에서만 다룬다.
- `PREPARING_SHIPMENT`은 MVP 주문 상태에서 제거하고, 공급처 발주 완료 후 송장 입력 전 상태는 `SUPPLIER_ORDERED`로 표현한다.
- 내부 상태는 운영, 결제 검증, 공급처 발주, 환불 정합성을 위한 상태로 유지한다.

## System Impact

- 결제 전 주문과 결제 완료 주문을 명확히 구분해야 한다.
- PG 결제 승인 금액과 서버 결제 그룹(PaymentGroup) 총액을 비교해야 한다.
- 주문 상태 변경 이력이 필요하다.
- 결제 요청과 결제 검증은 결제 그룹(PaymentGroup) ID 또는 checkout number를 기준으로 연결되어야 한다.
- 각 배송 그룹 주문은 결제 그룹(PaymentGroup) ID를 저장해야 한다.
- `PAYMENT_PENDING` 주문은 관리자 공급처 발주 큐에 노출하지 않는다.
- 결제 대기 주문 만료 처리를 위한 스케줄러 또는 만료 검증 로직이 필요하다.
- 만료 이후 들어온 결제 승인 검증 요청은 주문 확정으로 처리하지 않아야 한다.
- 배송지 변경 가능 여부는 주문 상태와 `addressLockedAt`을 함께 기준으로 판단해야 한다.
- checkout 배송지 변경 가능 여부는 결제 그룹/주문 상태가 `PAYMENT_PENDING`이고 정책 확인 전인지 함께 판단해야 한다.
- `SUPPLIER_ORDERED` 이후 고객 배송지 변경 API는 거절해야 한다.
- `addressLockedAt`이 있는 주문의 고객 배송지 변경 API는 거절해야 한다.
- 고객용 주문 상태 매핑 계층이 필요하다.
- 관리자 화면은 내부 상태를 볼 수 있지만, 고객 화면은 고객 친화적인 표시 상태를 사용해야 한다.
- 고객 주문 내역 API와 체크아웃/결제 재시도 화면 API는 노출 대상 상태가 달라야 한다.
- 환불 표시 상태는 주문 상태뿐 아니라 `Refund.status`를 함께 보고 계산해야 한다.
- PG 취소/환불 실패 건은 고객에게 환불 완료로 표시하지 않아야 한다.
- 상태 전이 검증은 fromStatus, actor, action, guard, sideEffect, toStatus 기준으로 구현해야 한다.
- 알림 발송은 상태 전이와 별도 `NotificationLog`에 기록해야 한다.
- 주문 상품에는 상품/옵션 이름과 가격뿐 아니라 주문 시점의 상품 상세/고시 버전 참조를 스냅샷으로 남겨야 한다.

## Open Questions

None

## Customer Display Status Mapping

Initial mapping for checkout, admin support, and exceptional customer-facing states. Failed, pending, and expired payment orders are not shown in normal customer order history.

| Internal status | Customer display status |
| --- | --- |
| `PAYMENT_PENDING` | 결제 대기 |
| `EXPIRED` | 주문 만료 |
| `PAYMENT_EXCEPTION` | 결제 확인 중 |
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
| `REFUND_REQUESTED` | `PG_CANCEL_REQUESTED` / `PROCESSING` | 환불 처리 중 |
| `REFUND_REQUESTED` | `FAILED` / `RETRY_REQUIRED` / `MANUAL_REVIEW_REQUIRED` | 환불 확인 필요 |
| `REFUNDED` | `COMPLETED` | 환불 완료 |

## Order History Visibility

| Surface | Included statuses |
| --- | --- |
| Customer checkout/retry screen | `PAYMENT_PENDING`, `EXPIRED`, payment failure result for the current checkout |
| Customer order history | `SUPPLIER_ORDER_PENDING`, `SUPPLIER_ORDERED`, `SHIPPED`, `DELIVERED`, `OUT_OF_STOCK`, `REFUND_REQUESTED`, `REFUNDED`, customer-visible `PAYMENT_EXCEPTION` |
| Admin order queue | All internal statuses except deleted test data |

## State Transition Table

Initial MVP transitions:

| From status | Actor | Action | Guard | Side effect | To status |
| --- | --- | --- | --- | --- | --- |
| none | Customer | Create checkout | Authenticated user, sellable product/option, calculated amount | Create payment group and delivery-group orders | `PAYMENT_PENDING` |
| `PAYMENT_PENDING` | System | Expire checkout | 30 minutes passed without verified payment | Mark checkout expired | `EXPIRED` |
| `PAYMENT_PENDING` | System | Confirm payment | PG approved, amount matches, policy confirmed, order unexpired, sellable product/option | Store payment approval, expose order history, notify payment completed | `SUPPLIER_ORDER_PENDING` |
| `PAYMENT_PENDING` | System | Payment confirmation exception | PG approved but validation fails | Block supplier order, attempt full PG cancel, create payment event | `PAYMENT_EXCEPTION` |
| `PAYMENT_EXCEPTION` | System/Admin | Payment exception cancel succeeds | PG full cancel success, payment group cancel recorded | Notify payment cancel completed | `CANCELLED` |
| `PAYMENT_EXCEPTION` | Admin | Manual payment exception resolved | Admin confirms final PG/refund outcome | Record admin action and customer notice | `CANCELLED` |
| `SUPPLIER_ORDER_PENDING` | Customer | Self-service cancel | Supplier order work not started, PG cancel/refund request accepted | Create refund, request PG cancel/refund | `REFUND_REQUESTED` |
| `SUPPLIER_ORDER_PENDING` | Admin | Start supplier order work | Next operational step confirmed | Set `supplierOrderStartedAt`, `addressLockedAt` | `SUPPLIER_ORDER_PENDING` |
| `SUPPLIER_ORDER_PENDING` | Admin | Supplier ordered | Supplier order placed, evidence recorded | Record fulfillment evidence, notify preparing | `SUPPLIER_ORDERED` |
| `SUPPLIER_ORDER_PENDING` | Admin | Supplier out of stock | Supplier confirmed unavailable | Notify out of stock, create refund | `OUT_OF_STOCK` |
| `SUPPLIER_ORDERED` | Admin | Supplier out of stock before shipment | Supplier confirmed unavailable before carrier/tracking | Notify out of stock, create refund | `OUT_OF_STOCK` |
| `SUPPLIER_ORDERED` | Admin | Enter shipment | Carrier and tracking number exist | Create shipment, notify shipped | `SHIPPED` |
| `SHIPPED` | System/Admin | Mark delivered | Shipment tracking delivered or admin correction reason exists | Set delivered time, notify delivered | `DELIVERED` |
| `OUT_OF_STOCK` | System/Admin | Refund requested | Refund amount equals delivery-group order amount | Request PG cancel/refund | `REFUND_REQUESTED` |
| `REFUND_REQUESTED` | System/Admin | Refund completed | PG cancel/refund success recorded | Notify refund completed | `REFUNDED` |
| `SUPPLIER_ORDERED` / `SHIPPED` / `DELIVERED` | Customer/Admin | Claim opened | Claim request valid for order state | Create claim; order status may stay unchanged until refund | unchanged |
| any active status | Admin | Manual correction | Correction action allowed, reason required | Record admin action and status history | defined corrected status |

## Forbidden Transitions

- Paid order to `REFUNDED` without `Refund.status = COMPLETED` and PG cancel/refund success.
- Any order to `SHIPPED` without carrier and tracking number.
- Any order to `DELIVERED` without a `Shipment` record and delivered tracking/admin correction evidence.
- `SHIPPED` or `DELIVERED` to `OUT_OF_STOCK` except through claim/manual correction handling.
- `PAYMENT_PENDING` directly to supplier ordering without server payment verification.
- `PAYMENT_EXCEPTION` to supplier ordering.
- `EXPIRED` to confirmed order; customer must create a new checkout.
- Product/option/quantity-level partial refund inside one delivery-group order in MVP.

## Notification Triggers

Initial transaction notification triggers:

| Trigger | Channel | Required log |
| --- | --- | --- |
| Payment completed | Email, order detail status | `NotificationLog` |
| Payment exception or cancel processing | Email, order detail status | `NotificationLog` |
| Supplier out of stock | Email, order detail status | `NotificationLog` |
| Shipment started | Email, order detail status | `NotificationLog` |
| Delivery completed | Email, order detail status | `NotificationLog` |
| Delay notice | Email, order detail status | `NotificationLog` |
| Claim status changed | Email, order detail status | `NotificationLog` |
| Refund completed | Email, order detail status | `NotificationLog` |

DS-39 implements transactional email notification logs for payment completed, payment exception, supplier out-of-stock, shipment started, delivery completed, claim status changed, and refund completed. Delay notice remains a trigger without automated sender logic.
