# Admin Operations Policy

Status: Draft

## Purpose

운영자가 어떤 작업을 어떤 권한과 기록으로 처리하는지 정한다.

## Policy Areas

- 관리자 권한 범위
- 상품 관리 권한
- 주문 처리 권한
- 품절 처리 권한
- 환불 처리 권한
- 관리자 작업 이력
- 운영 실수 복구 기준

## Proposed MVP Direction

- MVP에서는 `ADMIN` 단일 관리자 권한으로 시작한다.
- 관리자 작업 이력은 주문 상태 변경에 대해서 우선 기록한다.
- 상품 변경 이력은 MVP 이후로 미룬다.
- 환불 실행은 관리자 액션으로만 가능하다.
- 관리자는 주문 상태를 임의 변경하지 않고 정해진 액션 버튼으로 처리한다.

## Confirmed Policy

TBD

## System Impact

- 관리자 API는 역할 검증이 필요하다.
- 주문 상태 변경은 action 기반으로 제한해야 한다.
- 주문 상태 변경 이력 테이블이 필요하다.

## Open Questions

- 관리자 역할을 `ADMIN` 하나로 둘 것인가, `OPERATOR`, `OWNER`로 나눌 것인가?
- 모든 관리자 액션에 사유 입력을 요구할 것인가?
- 운영 실수로 잘못 상태 변경했을 때 되돌리기 기능이 필요한가?

