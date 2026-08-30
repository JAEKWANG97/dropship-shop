# Documentation Index

이 문서는 Dropship Shop 문서의 읽는 순서와 기준 문서를 정리한다.

AI agent 작업 규칙은 루트의 [Agent Operating Guide](../AGENTS.md)를 따른다.

## Source Of Truth

- 현재 제품 범위: [Product Brief](product-brief.md), [Requirements](requirements.md)
- 현재 운영 정책: [Policy Documents](policies/README.md)
- 정책 결정 이력과 이유: [Decision Log](decision-log.md)
- 출시 전 법적 고지 체크리스트: [Legal Launch Checklist](legal-launch-checklist.md)
- 상품 등록 운영 기준: [Product Registration Guide](product-registration-guide.md)
- 현재 작업 큐: [Backlog](BACKLOG.md)
- 완료 작업 보관: [Completed Backlog](BACKLOG_DONE.md)
- 구현 설계 초안: [Supplier Portal Design](supplier-portal-design.md), [Domain Model](domain-model.md), [MVP ERD](erd.md), [MVP API Specification](api-spec.md), [Order Flow](order-flow.md), [Architecture](architecture.md), [Production Readiness](production-readiness.md)
- 배포 전 기능 검증: [Test Checklist](TEST_CHECKLIST.md), [Test Log](TEST_LOG.md)

정책 파일에서는 `Confirmed Policy`가 현재 구현 기준이다. `Initial Direction`은 논의 초기에 잡은 방향이므로, 충돌이 있으면 `Confirmed Policy`와 `Decision Log`를 우선한다.

`PROJECT_LOG`, `BACKLOG_DONE`, 과거 `Decision Log` 항목은 당시 작업 이력이다. 현재 정책과 충돌할 수 있으며, 최신 `Decision Log`의 대체 결정과 `Confirmed Policy`를 우선한다. 과거 기록은 현재 요구사항처럼 해석하지 않는다.

## Current Operating Baseline

- 고객 결제는 계좌입금과 관리자 입금확인만 사용한다. Toss Payments를 포함한 PG 실행 경로는 제거됐다.
- 판매가는 공급처 상품가에 active 가격 정책을 적용하며, 현재 기본값은 25%와 100원 단위 반올림이다. 공급처 배송비는 판매가 계산에 더하지 않는다.
- 도매꾹 source snapshot 주문은 입금확인 후 Private API 자동 발주 대상이며, 그 외 현재 구현 주문은 Coreable이 수동 발주한다.
- 기존 수동/Domeggook 상품 옵션은 실제 주문 재고를 차감하지 않는 `UNTRACKED`이고 기존 주문은 관리자 발주 단계와 단일 Shipment 계약을 유지한다.
- 운영 판매는 구매안전서비스와 최종 정책 버전이 준비될 때까지 `APP_SALES_ENABLED=false`로 차단한다.
- 공개 정책의 `prelaunch-*` 버전, 세금계산서 안내, 상품별 고시·인증 검수는 출시 전 미완료 항목이다.

## Supplier Portal Baseline (`B-100`~`B-105` Implemented)

- [Supplier Portal Design](supplier-portal-design.md)은 B-100~B-105 구현 설계 인덱스다. 정책 원문과 Decision Log가 우선한다.
- Coreable은 단일 판매자이며 고객 판매가, 결제, 환불, CS와 클레임 결정을 유지한다. 공급처 포털에는 판매자 정산이 없다.
- 공개 공급처 신청은 Coreable 승인, 1회용 이메일 초대, Kakao-only 로그인으로 이어지고 공급처당 활성 담당자는 1명이다.
- 첫 상품 기능은 개별 등록이다. 무옵션 상품은 내부 `기본` 옵션을 사용하고, 공급처는 공급가를 입력하지만 고객가는 Coreable 가격 정책으로 계산한다. 일반 유효상품은 자동 공개하고 flagged 상품만 검토한다.
- 신규 포털 옵션은 `TRACKED`가 기본이고 명시적 `UNTRACKED`를 허용한다. 기존 COREABLE 옵션은 `UNTRACKED`, B-101에서 B-102 전에 생성된 portal 옵션은 `TRACKED/onHand=0`으로 이관한다. Checkout은 24시간 재고 예약과 자동 만료를 사용한다. 금액 불일치는 실제 수령액의 결제그룹 Refund 한 건으로 원상복구하고, `EXPIRED` 뒤 정확한 입금은 원자적 재확보 성공 시에만 승인한다. 미입금 `CANCELLED` 뒤 발견된 입금은 금액이 정확해도 되살리지 않고 주문별 환불 후 새 checkout으로 끝낸다.
- 입금확인은 공급처 수락 단계 없이 즉시 출고 요청과 주소 잠금을 만든다. 공급처는 최소 배송 PII만 제한적으로 조회한다.
- 포털 주문은 주문 항목별 수량을 할당한 복수 Shipment와 `TRACKING_REGISTERED`를 사용한다. 고객용 공식 택배사 링크만 생성하고 실시간 택배사 API는 사용하지 않으며 allocation 오류는 void 후 재등록한다.
- 공급처는 송장 등록 전 배송 그룹 전체 품절과 Coreable이 만든 열린 task에서 요청한 사실만 기록한다. 고객 환불과 상태 결정은 Coreable만 수행한다.
- 공급처 운영 알림은 이메일만 사용하며 제목, 본문과 발송 이력에 고객 PII를 포함하지 않는다.
- 기존 Coreable 수동 발주, Domeggook 자동 발주·tracking sync와 고객/admin API는 expand-contract 방식으로 보존한다.

## Task-Based Reading

모든 문서를 순서대로 읽지 않는다. 먼저 [Backlog](BACKLOG.md)에서 현재 작업을 확인하고 변경 범위에 해당하는 문서만 읽는다.

- 제품 범위: [Product Brief](product-brief.md), [Requirements](requirements.md)
- 정책: 관련 [Policy Documents](policies/README.md), 필요한 결정만 [Decision Log](decision-log.md)에서 검색
- API/DB: [API Specification](api-spec.md), [ERD](erd.md)
- 주문 상태: [Order Flow](order-flow.md)
- 공급처 포털: [Supplier Portal Design](supplier-portal-design.md)에서 slice와 호환 경계를 확인한 뒤 관련 정책, [Requirements](requirements.md), [Order Flow](order-flow.md), [Architecture](architecture.md)를 읽는다.
- 배포/복구: [Architecture](architecture.md), [Production Readiness](production-readiness.md), [Backup And Restore](backup-restore.md)
- 과거 작업: `B-###`로 [Completed Backlog](BACKLOG_DONE.md)와 `PROJECT_LOG.md`를 검색

## Current Policy Hardening

- DS-23: 과거 PG 결제 예외 정책 이력이다. 현재 실행 경로는 B-067 이후 계좌입금과 수동 환불만 사용한다.
- DS-24: payment group and delivery-group order refund unit is reflected in payment, order, shipping, refund, legal notice, domain, and flow docs.
- DS-25: cancellation, return, exchange, and claim policy is reflected in cancellation/refund, legal notice, domain, requirements, and flow docs.
- DS-26: supplier fulfillment SLA, address lock, shipment unit, and tracking correction policy is reflected in fulfillment, order, admin, domain, requirements, and flow docs.
- DS-27: privacy, business notice, legal disclosure, account deletion, and marketing consent policy is reflected in account, legal notice, domain, requirements, and decision docs.
- DS-28: order transition table, forbidden transitions, notification log, visibility split, and order snapshot policy is reflected in order, admin, domain, requirements, flow, and decision docs.
- B-099: 공급처 포털 설계를 B-100~B-105로 분리했고 각 slice를 구현했다. 기존 legacy/Domeggook 동작을 보존하고 새 재고·출고·송장 계약만 expand-contract 방식으로 추가한다.
- Next policy work is tracked in [Backlog](BACKLOG.md).
