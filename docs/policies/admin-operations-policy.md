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

## Supplier Portal Operations Boundary — Planned (B-100, B-102, B-103, B-104, B-105)

Status: Planned (B-100, B-102, B-103, B-104, B-105). Existing implemented admin actions remain authoritative for legacy and Coreable-managed flows until each slice ships.

- Coreable 관리자는 공개 공급처 신청을 승인 또는 거절하고, 승인된 연락 이메일에만 1회용 초대를 발급한다. 신청·승인·초대와 중복 신청 방지는 B-100에서 구현한다.
- B-102 이후 확인된 금액 불일치는 단순 메모가 아니다. 관리자는 실제 입금자·금액·시각·거래 식별값과 사유를 결제그룹 전체 명령으로 기록하고, 시스템은 실제 수령액의 `PAYMENT_GROUP` Refund 한 건과 모든 포함 Order의 `REFUND_REQUESTED`를 원자적으로 만든다. `PAYMENT_PENDING`, `EXPIRED`뿐 아니라 수령 Payment/Refund/Fulfillment 없이 미입금 취소만 된 `CANCELLED` 그룹도 이 경로로 반환한다. Coreable만 승인·실제 계좌이체 완료를 기록할 수 있고 공급처에는 노출하지 않는다.
- 신청/초대와 portal/contact/manager/sales lifecycle 명령은 idempotency key, actor, reason과 전후 상태를 append-only 감사 이력에 남긴다. LINK_EXISTING은 신청 email을 기존 Supplier 연락처에 동기화하고 재검증한다. Planned in B-100.
- 포털 주문은 입금확인과 동시에 공급처에 노출되고 배송지가 잠기며 별도 공급처 수락 액션을 두지 않는다. 기존 Coreable 수동 발주와 Domeggook 주문의 관리자 발주 시작·완료 액션은 유지한다. Planned in B-103.
- 공급처 주문 목록 응답에는 고객 PII를 넣지 않는다. 주문 상세는 인증 principal에 연결된 본인 공급처 주문에 한해 수령인 이름, 수령인 전화번호, 우편번호, 주소1, 주소2, 배송 메모만 제공한다.
- 공급처 주문 상세에는 고객 이메일, 회원 식별자·프로필, 결제·입금·은행·금액 정보, 환불 계좌·실행 정보, 관리자 메모, 다른 공급처와 그 상품을 제공하지 않는다. Time-valid VERIFIED contract가 없으면 최소 PII 상세와 Claim grant 접근도 제공하지 않는다.
- 공급처 주문 상세의 기본 PII 접근 종료시각은 `fulfillment.requestedAt + 60일`로 저장해 시작하고 각 송장 등록마다 `min(현재 저장 cutoff, registeredAt + 30일)`로만 단축한다. void·교체·추가 송장이 cutoff를 늘리지 않는다.
- 주문이 `OUT_OF_STOCK`, `CANCELLED`, `REFUND_REQUESTED` 또는 `REFUNDED`가 되면 non-voided 송장 유무와 관계없이 기본 종료시각을 기다리지 않고 즉시 마스킹한다.
- 기본 종료시각부터(`now >= cutoff`) 한 글자 이름은 `*`, 두 글자 이상 이름은 첫 Unicode code point와 고정 `**`로 반환한다. 전화번호는 숫자만 정규화한 뒤 4자리 이하면 전부 `*`, 5자리 이상이면 마지막 4자리만 남기고 앞자리를 `*`로 반환한다. 우편번호, 주소1, 주소2와 배송 메모는 `null`로 반환하고 비PII 주문·상품 정보는 계속 제공할 수 있다.
- 공급처 PII 응답에는 `Cache-Control: no-store`를 적용한다. 공급처 주문 상세 접근 로그는 actor, order, access reason, accessed-at만 저장하고 실제 PII, 응답 본문, 결제·환불 정보는 복제하지 않는다. 로그는 관리자만 조회하고 1년 뒤 삭제한다. Planned in B-103.
- Claim PII grant/extension은 각각 요청시점부터 최대 30일이며 `APPROVED`, `RETURN_WAITING`, `RETURN_RECEIVED`, `REFUND_PROCESSING`, `EXCHANGE_SHIPPING` 상태와 time-valid VERIFIED supplier contract가 함께 있을 때만 유효하다. 다른/terminal 상태나 contract expiry/revoke는 별도 revoke 없이 즉시 무효화한다. grant/extension/revoke는 관리자·사유·시각을 append-only로 남기며 공급처는 만들거나 연장할 수 없다. Planned in B-103 and B-105.
- Coreable 관리자는 Shipment별 택배사·송장을 정정 또는 무효화하고 배송완료를 수동 확정할 수 있다. 각 액션은 idempotency/version guard, 사유와 필요한 근거시각, 전후값을 남기고 주문의 할당·배송 집계 상태를 다시 계산한다. allocation 오류는 void 후 재등록한다. Planned in B-104.
- 잘못된 portal 수동 배송완료는 이후 Claim/Refund가 없을 때만 tracking 재개 또는 완료시각 정정을 허용하고 원래 증적을 보존한다. 후속 처리가 있으면 `409`로 막고 incident/claim 절차를 사용한다. Planned in B-104.
- Coreable로 인계된 portal 주문에는 supplier와 같은 plural/allocation 계약의 admin shipment 생성 action을 사용한다. 기존 발주 시작/완료·단일 shipment·tracking-sync/manual-correction은 portal channel에서 차단한다. 배송완료와 시각정정은 등록시각, 완료시각, 증거확인시각, 현재시각 순서를 검증한다. Planned in B-104.
- 공급처는 첫 송장 전 배송 그룹 주문 전체 품절과 Coreable이 만든 열린 claim task가 요청한 유형의 append-only fact만 입력할 수 있다. Claim/Refund 최종 판단, 실제 계좌환불, 고객 CS와 주문·배송 관리자 보정은 Coreable 전용이다. Planned in B-105.
- Supplier task 생성은 idempotency key/request hash로 중복 workflow를 막는다. Coreable admin은 task list/detail에서 Claim/order linkage, 내부 context와 전체 same-task fact history를 읽은 뒤 별도 명시적 Claim/Refund action으로 판단한다. Planned in B-105.
- 최초 초대는 아직 검증되지 않은 신청 이메일로 보내는 1회성 연락처 검증 메일이며 token과 일반 안내만 포함한다. 그 외 공급처 운영 이메일은 검증된 연락 이메일에만 보내고 제목, 본문과 payload snapshot에 고객 PII, 결제 또는 환불 정보를 포함하지 않는다. 실제 초대·운영 이메일 도착과 신규 개인정보처리방침 시행 버전을 검증하기 전에는 production supplier activation을 허용하지 않는다. Planned in B-100 and B-103.

### Planned System Impact

- B-100은 공급처 신청 승인·거절, 초대 발급과 중복 방지 액션을 관리자 이력으로 남겨야 한다.
- B-102 금액 불일치와 그룹 환불 완료는 각각 idempotency key/request hash/immutable result를 남기고 같은 key/hash 재요청에는 최초 결과를 반환해야 한다. 실제 송금 응답을 잃은 경우 새 송금 대신 같은 key로 완료 결과를 조회·재시도하도록 관리자 UI가 경고해야 한다.
- B-102 late-deposit 명령은 portal `EXPIRED`와 portal/legacy qualifying 미입금 `CANCELLED`를 구분한다. `CANCELLED`에서 정확한 입금을 확인하면 입금시각이나 재고를 근거로 주문을 되살리지 않고 Order별 환불 큐와 새 checkout 안내로 끝낸다.
- B-103 supplier order query는 인증 principal의 supplier tenant와 order supplier를 함께 검증하고 목록·상세 DTO를 분리해야 한다.
- B-103은 PII 기본 종료시각, 필드별 masking, `no-store`, 한시 claim grant와 최소 접근 로그를 서버에서 강제해야 한다.
- B-104 shipment 정정·무효화·배송완료 액션은 삭제 대신 append-only 이력을 사용하고 전체 주문 상태를 재계산해야 한다.
- B-105는 공급처 사실 입력과 Coreable 관리자 전이를 서로 다른 API와 권한으로 분리해야 한다.

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
