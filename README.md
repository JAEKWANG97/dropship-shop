# Dropship Shop

단일 운영자가 상품을 판매하고, 실제 출고는 공급처가 처리하는 위탁/드롭쉬핑형 쇼핑몰 프로젝트입니다.

## Product Definition

이 프로젝트는 쿠팡 같은 대형 마켓플레이스가 아니라, 운영자가 직접 상품을 등록하고 주문을 받은 뒤 공급처에 수동 또는 반자동으로 발주하는 자사몰입니다.

초기 목표는 다음입니다.

- 고객이 상품을 탐색하고 결제할 수 있다.
- 운영자가 상품, 주문, 배송, 취소, 환불을 관리할 수 있다.
- 실제 재고 수량을 보장하지 않고, 주문 후 공급처 품절이 발생할 수 있는 운영 모델을 지원한다.
- 결제 완료와 공급처 발주 완료를 명확히 분리해 주문 사고를 줄인다.

## MVP Scope

### Customer

- 회원가입/로그인
- 상품 목록 조회
- 상품 상세 조회
- 옵션 선택
- 장바구니
- 주문서 작성
- 실제 결제
- 주문 내역 조회
- 배송 상태 조회
- 취소/환불 요청

### Admin

- 상품 등록/수정/숨김
- 상품 옵션 관리
- 상품 이미지 관리
- 공급처 정보 관리
- 주문 목록/상세 조회
- 공급처 발주 상태 변경
- 품절 처리
- 송장번호 입력
- 취소/환불 처리

## Non-Goals For MVP

- 판매자 입점형 마켓플레이스
- 판매자 정산 시스템
- 실시간 공급처 재고 동기화
- 공급처 자동 발주 API 연동
- 고급 쿠폰/포인트 시스템
- AI 추천
- 복잡한 검색엔진
- 모바일 앱
- 물류센터/WMS 연동

## Recommended Stack

- Backend: Spring Boot
- Database: PostgreSQL
- ORM: JPA
- Auth: Spring Security
- Frontend: React or Next.js
- Storage: S3-compatible object storage
- Payment: Toss Payments, PortOne, or another Korean PG
- Deployment: Single application server + managed PostgreSQL at first

## Documentation

- [Product Brief](docs/product-brief.md)
- [Requirements](docs/requirements.md)
- [Domain Model](docs/domain-model.md)
- [Order Flow](docs/order-flow.md)
- [Architecture](docs/architecture.md)
- [Roadmap](docs/roadmap.md)
- [Decision Log](docs/decision-log.md)
- [Glossary](docs/glossary.md)
- [GitHub And Linear Setup](docs/github-linear-setup.md)
- [Linear Backlog](docs/linear-backlog.md)
- [Policy Documents](docs/policies/README.md)
- [Development Workflow](docs/development-workflow.md)
