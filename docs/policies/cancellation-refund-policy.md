# Cancellation And Refund Policy

Status: Confirmed

## Purpose

고객 취소, 공급처 품절, 환불, 반품, 교환 기준을 정한다.

## Policy Areas

- 고객 직접 취소 가능 시점
- 공급처 발주 후 취소 기준
- 배송 후 반품/교환 지원 범위
- 공급처 품절 시 처리
- 부분 품절 처리
- 환불 사유 분류
- 환불 완료 고객 고지

## Proposed MVP Direction

- 공급처 발주 전에는 고객 직접 취소를 허용한다.
- 공급처 발주 후 취소는 관리자 승인 대상으로 둔다.
- 공급처 품절 시 전액 환불한다.
- 부분 품절은 MVP에서 전체 주문 취소/환불로 처리한다.
- 배송 후 반품/교환은 MVP에서는 요청 접수와 수동 처리로 시작한다.

## Confirmed Policy

- 고객 직접 취소는 `SUPPLIER_ORDER_PENDING` 상태까지만 허용한다.
- `SUPPLIER_ORDER_PENDING`은 결제 검증 완료 후 공급처 발주 전 상태를 의미한다.
- `SUPPLIER_ORDERED` 이후에는 고객이 직접 취소할 수 없다.
- 공급처 발주 후 고객은 취소/문의 요청만 할 수 있고, 관리자가 공급처 상황을 확인한 뒤 수동 처리한다.
- 공급처 품절 시 전체 주문 취소와 전액 환불로 처리한다.
- 부분 품절도 MVP에서는 전체 주문 취소와 전액 환불로 처리한다.
- MVP에서는 부분 취소와 부분 환불을 지원하지 않는다.
- 배송 후 반품/교환은 MVP에서 자동 처리하지 않는다.
- 배송 후 고객은 반품/교환 문의 요청만 할 수 있고, 관리자가 수동 처리한다.
- PG 환불은 관리자 승인 후 주문 단위 전액 환불을 기본으로 한다.
- 환불 완료 후 고객에게 환불 완료 상태를 노출한다.

## System Impact

- 취소 가능 여부는 주문 상태 기반으로 계산한다.
- `SUPPLIER_ORDER_PENDING`까지는 고객 직접 취소가 가능하다.
- `SUPPLIER_ORDERED` 이후는 관리자 승인 또는 수동 처리 흐름이 필요하다.
- 부분 환불을 제외하면 주문/결제/환불 모델이 단순해진다.
- 고객 취소 버튼 노출 여부는 주문 상태로 판단해야 한다.
- 발주 후 취소/반품/교환은 고객 셀프서비스가 아니라 문의/관리자 처리 흐름으로 모델링해야 한다.
- 환불 사유 enum이 필요하다.

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
