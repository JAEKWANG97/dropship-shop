# Admin Operations Policy

Status: Confirmed

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

## Initial Direction

- MVP에서는 `ADMIN` 단일 관리자 권한으로 시작한다.
- 관리자 작업 이력은 주문 상태 변경에 대해서 우선 기록한다.
- 상품 변경 이력은 가격, 판매 상태, 공급처, 상품 상세 운영 변경처럼 운영 영향이 큰 항목부터 기록한다.
- 환불 실행은 관리자 액션으로만 가능하다.
- 관리자는 주문 상태를 임의 변경하지 않고 정해진 액션 버튼으로 처리한다.

## Confirmed Policy

- MVP 관리자 권한은 `ADMIN` 단일 역할로 시작한다.
- 관리자 계정은 별도 가입 화면 없이 DB seed 또는 수동 등록으로만 부여한다.
- 고객 소셜 계정이 존재하더라도 DB에 `ADMIN` 권한이 없으면 관리자 기능에 접근할 수 없다.
- `ADMIN` 계정은 고객 상품 조회, 장바구니, 주문 흐름도 사용할 수 있다. 단, 관리자 계정 탈퇴는 고객 셀프서비스에서 허용하지 않는다.
- 관리자는 주문 상태를 임의 드롭다운으로 변경하지 않는다.
- 주문 처리는 현재 상태에서 허용된 다음 액션이 확보된 경우에만 진행한다.
- 관리자 주문 액션은 정해진 버튼으로 제공한다.
  - 공급처 발주 작업 시작: Implemented by DS-12
  - 공급처 발주 완료: Implemented by DS-12
  - 공급처 품절: Implemented by DS-12
  - 택배사/송장번호 입력: Implemented by DS-13
  - 배송 상태 수동 보정
  - 취소/환불 승인: cancellation claim review implemented by DS-14, refund approval hardening implemented by DS-38
  - 취소/환불 거절: cancellation claim review implemented by DS-14, refund execution planned by DS-15
  - 클레임 증빙 요청
  - 클레임 승인
  - 클레임 거절
  - 반품 입고 확인
  - 교환 발송 처리
- 자동 상태 되돌리기 버튼은 MVP에서 제공하지 않는다.
- 잘못된 상태 변경은 되돌리기가 아니라 관리자 정정 액션으로 처리한다.
- 관리자 정정 액션은 사유를 필수로 입력하고 이력을 남긴다.
- 공급처 발주 작업 시작 액션은 배송지를 잠그고 작업 시작 관리자와 시각을 기록한다.
- 공급처 발주 완료 액션은 공급처 주문번호, 발주 주소 스냅샷, 예상 출고일, 공급처 응답 메모를 기록한다.
- 출고 예정일이 불명확하거나 지연 기준에 도달한 주문은 관리자 지연 안내 대상으로 표시한다.
- 반품/교환/취소 클레임은 정해진 관리자 액션으로 처리하고 처리 사유와 고객 안내 문구를 기록한다. Cancellation claim review is implemented by DS-14; return/exchange claim review is implemented by DS-37.
- 주문 상태 변경 이력은 MVP부터 기록한다. Implemented by DS-36 for admin fulfillment/shipment actions and shipment tracking/manual correction delivery completion.
- 취소, 환불, 품절, 배송 수동 보정, 관리자 정정 액션은 사유 입력을 필수로 한다. Shipment manual correction implemented by DS-36.
- 상품 변경 이력은 MVP에서 다음 항목부터 기록한다.
  - 상품 가격 변경
  - 상품 판매 상태 변경
  - 상품 옵션 판매 상태 변경
  - 상품 공급처 변경
  - 상품/옵션 기본 정보 변경
  - 이미지, 상세 블록, 상품 고시 변경
- 상품 상세 HTML diff, 이미지 교체/정렬 diff, 상품명/요약문 상세 diff처럼 필드별 상세 diff는 MVP 이후로 미룬다.

## System Impact

- 관리자 API는 역할 검증이 필요하다.
- 관리자 주문 큐는 `SUPPLIER_ORDER_PENDING` 주문만 보여주고 `PAYMENT_PENDING`, `EXPIRED` 주문은 공급처 작업 큐에서 제외한다. Implemented by DS-11.
- 공급처 발주 작업 시작, 발주 완료, 품절 액션은 상태 전이를 검증하고 `admin_order_action_histories`에 사유와 전후 상태를 기록한다. Implemented by DS-12.
- 택배사/송장번호 입력 액션은 `SUPPLIER_ORDERED` 주문에만 허용하고 주문당 shipment 1개만 생성한다. Implemented by DS-13.
- 취소 클레임 승인/거절은 관리자 권한과 사유를 검증하고 claim review fields를 기록한다. Implemented by DS-14.
- 입금확인은 실제 입금자명, 실제 입금액, 입금시각, 거래 식별 메모, 사유를 기록하며 실제 입금액이 checkout 총액과 정확히 일치할 때만 승인한다. 불일치는 입금확인이 아니라 불일치 처리 메모로 남긴다. Implemented by B-068.
- 환불 승인/거절과 실제 계좌이체 후 수동 환불 완료는 관리자 권한 API로만 수행한다. 수동 환불 완료에는 은행명, 계좌번호, 예금주, 이체시각, 거래 식별 메모, 사유가 필요하다. 계좌정보와 입금자명은 관리자 주문 상세에만 표시하며 고객 응답·알림·작업 이력에는 복사하지 않는다. Implemented by DS-15, DS-38, B-044, and B-068.
- 주문 상태 변경은 action 기반으로 제한해야 한다.
- 주문 상태 변경 이력 테이블이 필요하다. Implemented by DS-36.
- 주요 관리자 액션 이력 테이블과 관리자 조회 API가 필요하다. Implemented by DS-44.
- 주요 상품 변경 이력 테이블과 관리자 조회 API가 필요하다. Implemented by DS-43.
- 주문 상태 변경 API는 현재 상태와 요청 액션의 유효한 전이 여부를 검증해야 한다.
- 주문 상태 변경 API는 fromStatus, actor, action, guard, sideEffect, toStatus 전이표를 기준으로 검증해야 한다.
- 관리자 수동 정정은 금지 전이를 우회하는 기능이 아니며, 허용된 정정 액션과 사유, 이력을 남겨야 한다.
- 주요 고객 알림은 `NotificationLog`에 발송 대상, 채널, 템플릿, 결과를 기록해야 한다. Implemented by DS-39.

## Open Questions

None for MVP.
