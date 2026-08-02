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
- 구현 설계 초안: [Domain Model](domain-model.md), [MVP ERD](erd.md), [MVP API Specification](api-spec.md), [Order Flow](order-flow.md), [Architecture](architecture.md), [Production Readiness](production-readiness.md)
- 배포 전 기능 검증: [Test Checklist](TEST_CHECKLIST.md), [Test Log](TEST_LOG.md)

정책 파일에서는 `Confirmed Policy`가 현재 구현 기준이다. `Initial Direction`은 논의 초기에 잡은 방향이므로, 충돌이 있으면 `Confirmed Policy`와 `Decision Log`를 우선한다.

`PROJECT_LOG`, `BACKLOG_DONE`, 과거 `Decision Log` 항목은 당시 작업 이력이다. 현재 정책과 충돌할 수 있으며, 최신 `Decision Log`의 대체 결정과 `Confirmed Policy`를 우선한다. 과거 기록은 현재 요구사항처럼 해석하지 않는다.

## Current Operating Baseline

- 고객 결제는 계좌입금과 관리자 입금확인만 사용한다. Toss Payments를 포함한 PG 실행 경로는 제거됐다.
- 판매가는 공급처 상품가에 active 가격 정책을 적용하며, 현재 기본값은 25%와 100원 단위 반올림이다. 공급처 배송비는 판매가 계산에 더하지 않는다.
- 도매꾹 source snapshot 주문은 입금확인 후 Private API 자동 발주 대상이며, 그 외 주문은 수동 발주한다.
- 운영 판매는 구매안전서비스와 최종 정책 버전이 준비될 때까지 `APP_SALES_ENABLED=false`로 차단한다.
- 공개 정책의 `prelaunch-*` 버전, 세금계산서 안내, 상품별 고시·인증 검수는 출시 전 미완료 항목이다.

## Task-Based Reading

모든 문서를 순서대로 읽지 않는다. 먼저 [Backlog](BACKLOG.md)에서 현재 작업을 확인하고 변경 범위에 해당하는 문서만 읽는다.

- 제품 범위: [Product Brief](product-brief.md), [Requirements](requirements.md)
- 정책: 관련 [Policy Documents](policies/README.md), 필요한 결정만 [Decision Log](decision-log.md)에서 검색
- API/DB: [API Specification](api-spec.md), [ERD](erd.md)
- 주문 상태: [Order Flow](order-flow.md)
- 배포/복구: [Architecture](architecture.md), [Production Readiness](production-readiness.md), [Backup And Restore](backup-restore.md)
- 과거 작업: `B-###`로 [Completed Backlog](BACKLOG_DONE.md)와 `PROJECT_LOG.md`를 검색

## Current Policy Hardening

- DS-23: 과거 PG 결제 예외 정책 이력이다. 현재 실행 경로는 B-067 이후 계좌입금과 수동 환불만 사용한다.
- DS-24: payment group and delivery-group order refund unit is reflected in payment, order, shipping, refund, legal notice, domain, and flow docs.
- DS-25: cancellation, return, exchange, and claim policy is reflected in cancellation/refund, legal notice, domain, requirements, and flow docs.
- DS-26: supplier fulfillment SLA, address lock, shipment unit, and tracking correction policy is reflected in fulfillment, order, admin, domain, requirements, and flow docs.
- DS-27: privacy, business notice, legal disclosure, account deletion, and marketing consent policy is reflected in account, legal notice, domain, requirements, and decision docs.
- DS-28: order transition table, forbidden transitions, notification log, visibility split, and order snapshot policy is reflected in order, admin, domain, requirements, flow, and decision docs.
- Next policy work is tracked in [Backlog](BACKLOG.md).
