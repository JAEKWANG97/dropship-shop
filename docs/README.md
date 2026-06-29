# Documentation Index

이 문서는 Dropship Shop 문서의 읽는 순서와 기준 문서를 정리한다.

AI agent 작업 규칙은 루트의 [Agent Operating Guide](../AGENTS.md)를 따른다.

## Source Of Truth

- 현재 제품 범위: [Product Brief](product-brief.md), [Requirements](requirements.md)
- 현재 운영 정책: [Policy Documents](policies/README.md)
- 정책 결정 이력과 이유: [Decision Log](decision-log.md)
- 현재 작업 큐: [Backlog](BACKLOG.md)
- 구현 설계 초안: [Domain Model](domain-model.md), [MVP ERD](erd.md), [MVP API Specification](api-spec.md), [Order Flow](order-flow.md), [Architecture](architecture.md), [Production Readiness](production-readiness.md)

정책 파일에서는 `Confirmed Policy`가 현재 구현 기준이다. `Initial Direction`은 논의 초기에 잡은 방향이므로, 충돌이 있으면 `Confirmed Policy`와 `Decision Log`를 우선한다.

## Recommended Reading Order

1. [Product Brief](product-brief.md)
2. [Glossary](glossary.md)
3. [Requirements](requirements.md)
4. [Policy Documents](policies/README.md)
5. [Order Flow](order-flow.md)
6. [Domain Model](domain-model.md)
7. [MVP ERD](erd.md)
8. [MVP API Specification](api-spec.md)
9. [Architecture](architecture.md)
10. [Production Readiness](production-readiness.md)
11. [Roadmap](roadmap.md)
12. [Backlog](BACKLOG.md)
13. [Development Workflow](development-workflow.md)
14. [Decision Log](decision-log.md)
15. [GitHub And Linear Setup](github-linear-setup.md)

## Current Policy Hardening

- DS-23: payment exception and refund failure policy is reflected in payment, order, refund, domain, and flow docs.
- DS-24: payment group and delivery-group order refund unit is reflected in payment, order, shipping, refund, legal notice, domain, and flow docs.
- DS-25: cancellation, return, exchange, and claim policy is reflected in cancellation/refund, legal notice, domain, requirements, and flow docs.
- DS-26: supplier fulfillment SLA, address lock, shipment unit, and tracking correction policy is reflected in fulfillment, order, admin, domain, requirements, and flow docs.
- DS-27: privacy, business notice, legal disclosure, account deletion, and marketing consent policy is reflected in account, legal notice, domain, requirements, and decision docs.
- DS-28: order transition table, forbidden transitions, notification log, visibility split, and order snapshot policy is reflected in order, admin, domain, requirements, flow, and decision docs.
- Next policy work is tracked in [Backlog](BACKLOG.md).
