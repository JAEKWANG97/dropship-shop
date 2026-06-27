# Cancellation And Refund Policy

Status: Draft

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

TBD

## System Impact

- 취소 가능 여부는 주문 상태 기반으로 계산한다.
- `SUPPLIER_ORDER_PENDING`까지는 고객 직접 취소가 가능하다.
- `SUPPLIER_ORDERED` 이후는 관리자 승인 또는 수동 처리 흐름이 필요하다.
- 부분 환불을 제외하면 주문/결제/환불 모델이 단순해진다.

## Open Questions

- 고객 취소 버튼은 어느 상태까지 노출할 것인가?
- 반품/교환 요청 화면을 MVP에 넣을 것인가?
- 환불 사유 enum은 어떤 값으로 시작할 것인가?

