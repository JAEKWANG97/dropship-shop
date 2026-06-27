# Fulfillment And Shipping Policy

Status: Draft

## Purpose

공급처 발주, 출고, 송장 입력, 배송비, 배송 상태 기준을 정한다.

## Policy Areas

- 공급처 발주 방식
- 발주 처리 주체
- 배송비 계산 방식
- 복수 공급처 주문 처리
- 송장번호 입력 방식
- 배송 상태 변경 방식
- 출고 지연 처리

## Proposed MVP Direction

- 공급처 발주는 관리자가 수동 처리한다.
- 송장번호도 관리자가 수동 입력한다.
- 배송비는 고정 배송비 또는 일정 금액 이상 무료 중 하나로 시작한다.
- 복수 공급처가 섞인 주문은 MVP에서 제한하거나 전체 주문 단위로 단순 처리한다.
- 배송 완료 자동 연동은 MVP 이후로 미룬다.

## Confirmed Policy

TBD

## System Impact

- `SUPPLIER_ORDER_PENDING` 주문을 관리자 작업 큐로 보여줘야 한다.
- 관리자 액션으로 `SUPPLIER_ORDERED`, `OUT_OF_STOCK`, `SHIPPED` 상태가 변경된다.
- 송장번호 없는 주문은 `SHIPPED` 상태로 전환할 수 없다.

## Open Questions

- 배송비는 고정 배송비로 시작할 것인가, 일정 금액 이상 무료로 시작할 것인가?
- 한 주문에 여러 공급처 상품이 섞이는 것을 MVP에서 허용할 것인가?
- 배송 완료는 관리자가 수동 처리할 것인가, 고객에게 `배송중`까지만 보여줄 것인가?

