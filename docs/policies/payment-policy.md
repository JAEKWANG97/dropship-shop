# Payment Policy

Status: Draft

## Purpose

결제 수단, PG 검증, 결제 실패, 금액 불일치, 환불 실행 기준을 정한다.

## Policy Areas

- PG 제공자 선택
- 결제 수단 범위
- 무통장/가상계좌 포함 여부
- 결제 승인 검증 방식
- 중복 결제 콜백 처리
- 금액 불일치 처리
- 결제 후 품절 환불 처리

## Proposed MVP Direction

- 카드/간편결제 중심으로 시작한다.
- 무통장입금과 가상계좌는 MVP에서 제외한다.
- 결제 승인 검증은 Spring Boot 서버에서 수행한다.
- 결제 금액 불일치 시 주문을 확정하지 않는다.
- 중복 결제 확인 요청은 idempotent하게 처리한다.
- 공급처 품절 시 전액 환불을 기본 정책으로 둔다.
- 결제는 서버에 생성된 `PAYMENT_PENDING` 주문을 기준으로 요청한다.

## Confirmed Policy

- 결제 요청 전에 서버 주문을 `PAYMENT_PENDING` 상태로 생성한다.
- 결제 승인 검증은 Spring Boot 서버에서 수행한다.
- PG 승인 금액과 서버 주문 금액이 일치해야 주문을 확정한다.
- 결제 검증 성공 후 주문은 `SUPPLIER_ORDER_PENDING` 상태로 전환한다.
- 결제 검증 실패 또는 금액 불일치 시 주문을 확정하지 않는다.

## System Impact

- 서버에는 PG secret key가 필요하다.
- 결제 성공 처리는 반드시 서버 검증 후 확정한다.
- `Payment`와 `Order` 상태를 분리한다.
- 환불 실행 결과를 별도 기록해야 한다.

## Open Questions

- PG는 Toss Payments로 시작할 것인가, PortOne으로 시작할 것인가?
- 부분 취소를 MVP에서 지원할 것인가?
- 결제 실패 주문은 고객 주문 내역에 보여줄 것인가?
