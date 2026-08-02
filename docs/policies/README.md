# Policy Documents

이 폴더는 쇼핑몰의 운영 정책을 정리한다.

정책은 ERD, API, 화면, 관리자 기능보다 먼저 확정되어야 한다. 특히 이 프로젝트는 실제 재고를 보유하지 않는 공급처 출고형 자사몰이므로, 주문 후 품절, 발주 지연, 환불 같은 운영 예외가 시스템 설계의 중심이 된다.

## Policy Files

- [Account Policy](account-policy.md)
- [Catalog And Inventory Policy](catalog-inventory-policy.md)
- [Pricing Policy](pricing-policy.md)
- [Order Policy](order-policy.md)
- [Payment Policy](payment-policy.md)
- [Fulfillment And Shipping Policy](fulfillment-shipping-policy.md)
- [Cancellation And Refund Policy](cancellation-refund-policy.md)
- [Admin Operations Policy](admin-operations-policy.md)
- [Legal And Customer Notice Policy](legal-and-customer-notice-policy.md)

## Source Of Truth

- 각 정책 파일의 `Confirmed Policy`가 현재 제품/구현 기준이다.
- `Initial Direction`은 논의 초기의 방향성 기록이며, 현재 기준과 충돌하면 `Confirmed Policy`를 우선한다.
- 결정 이유와 변경 이력은 [Decision Log](../decision-log.md)에 남긴다.
- 실행 단위는 [Backlog](../BACKLOG.md)를 기준으로 한다.

## Decision Status

Use these statuses in policy files:

- `Draft`: 아직 논의 중
- `Proposed`: 추천안은 있으나 확정 전
- `Confirmed`: 제품/구현 기준으로 확정
- `Deferred`: MVP 이후로 미룸

## How To Use

1. 변경할 정책 파일의 `Confirmed Policy`를 갱신한다.
2. 결정 이유는 `docs/decision-log.md`에 남긴다.
3. 정책 변경이 ERD, API, 주문 상태, 관리자 기능에 영향을 주면 관련 문서도 함께 갱신한다.
4. 실제 법률 문구는 출시 전 별도 검토한다.
