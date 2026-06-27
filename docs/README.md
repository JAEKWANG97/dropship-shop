# Documentation Index

이 문서는 Dropship Shop 문서의 읽는 순서와 기준 문서를 정리한다.

## Source Of Truth

- 현재 제품 범위: [Product Brief](product-brief.md), [Requirements](requirements.md)
- 현재 운영 정책: [Policy Documents](policies/README.md)
- 정책 결정 이력과 이유: [Decision Log](decision-log.md)
- 실행 단위와 Linear 이슈 기준: [Linear Backlog](linear-backlog.md)
- 구현 설계 초안: [Domain Model](domain-model.md), [Order Flow](order-flow.md), [Architecture](architecture.md)

정책 파일에서는 `Confirmed Policy`가 현재 구현 기준이다. `Initial Direction`은 논의 초기에 잡은 방향이므로, 충돌이 있으면 `Confirmed Policy`와 `Decision Log`를 우선한다.

## Recommended Reading Order

1. [Product Brief](product-brief.md)
2. [Glossary](glossary.md)
3. [Requirements](requirements.md)
4. [Policy Documents](policies/README.md)
5. [Order Flow](order-flow.md)
6. [Domain Model](domain-model.md)
7. [Architecture](architecture.md)
8. [Roadmap](roadmap.md)
9. [Linear Backlog](linear-backlog.md)
10. [Development Workflow](development-workflow.md)
11. [Decision Log](decision-log.md)
12. [GitHub And Linear Setup](github-linear-setup.md)

## Current Policy Hardening

- DS-23: payment exception and refund failure policy is reflected in payment, order, refund, domain, and flow docs.
- DS-24: payment group and delivery-group order refund unit is reflected in payment, order, shipping, refund, legal notice, domain, and flow docs.
- DS-25: cancellation, return, exchange, and claim policy is reflected in cancellation/refund, legal notice, domain, requirements, and flow docs.
- DS-26: supplier fulfillment SLA, address lock, shipment unit, and tracking correction policy is reflected in fulfillment, order, admin, domain, requirements, and flow docs.
- Next policy issues are tracked in [Linear Backlog](linear-backlog.md) and Linear.
