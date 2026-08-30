# Dropship Shop

단일 운영자가 상품을 판매하고, 실제 출고는 공급처가 처리하는 공급처 출고형 자사몰 프로젝트입니다.

## Product Definition

이 프로젝트는 쿠팡 같은 대형 마켓플레이스가 아니라, Coreable이 단일 판매자로 상품을 판매하고 공급처가 출고하는 자사몰입니다. 도매꾹 source snapshot이 있는 주문은 계좌입금 확인 후 Private API로 자동 발주하고, 그 외 현재 구현 주문은 관리자가 수동 처리합니다.

초기 목표는 다음입니다.

- 고객이 상품을 탐색하고 결제할 수 있다.
- 운영자가 상품, 주문, 배송, 취소, 환불을 관리할 수 있다.
- 기존 수동/Domeggook 상품은 실제 재고 수량을 보장하지 않고, 주문 후 공급처 품절이 발생할 수 있는 운영 모델을 지원한다.
- 결제 완료와 공급처 발주 완료를 명확히 분리해 주문 사고를 줄인다.

## MVP Scope

### Customer

- 회원가입/로그인
- 상품 목록 조회
- 상품 상세 조회
- 옵션 선택
- 장바구니
- 주문서 작성
- 배송/취소/환불/품절 정책 확인
- 계좌입금 주문과 입금 확인
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

### Supplier Portal — `B-100`~`B-103` Implemented, `B-104`~`B-105` Planned

- 공개 신청, Coreable 승인/거절, 1회용 이메일 초대, Kakao 담당자 연결과 포털 기본 화면은 `B-100`에서 구현했다.
- 공급처 개별 상품·옵션·이미지·상세·고시 등록과 Coreable 검토는 `B-101`에서 구현했다. 공급처는 공급가를 입력하지만 고객 판매가와 최종 판매 통제는 Coreable이 유지한다.
- 일반 유효상품은 자동 공개하고 인증·카테고리·법정 필수정보 위험만 Coreable이 검토한다. `B-102`는 `TRACKED`/`UNTRACKED` 옵션 재고와 24시간 예약을 구현한다.
- `B-103`은 입금확인 완료 주문을 공급처 수락 단계 없이 즉시 출고 요청으로 만들고, 공급처에는 기한이 제한된 최소 배송정보만 노출한다. 개인정보 cutoff·Coreable 인계·운영 이메일 기반도 함께 구현했다.
- `B-104`/`B-105` Planned: 공급처는 복수 송장과 수량 할당, 송장 전 주문 전체 품절, Coreable이 요청한 클레임 사실만 입력한다. 결제·환불·CS·클레임 결정과 정산은 제공하지 않는다.

`B-101`~`B-103` 구현 계약과 `B-104`~`B-105` 상세 Planned 계약, 기존 동작의 호환 경계는 [Supplier Portal Design](docs/supplier-portal-design.md)을 기준으로 한다. Production 포털은 active 공급처 신청 개인정보 고지, 실제 이메일 delivery, `B-098` 계약 증적과 `B-100`~`B-105` gate를 모두 검증할 때까지 기본 `off`다.

## Non-Goals For MVP

- 판매자 입점형 마켓플레이스
- 판매자 정산 시스템
- 기존 상품의 실시간 공급처 재고 보장·동기화와 도매꾹 외 다중 공급처 자동 발주 API 연동. 신규 포털 상품의 supplier-managed 재고만 `B-102` 구현 범위다.
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
- Frontend: Next.js
- Storage: EC2 EBS-backed local uploads with S3 backup; object storage serving is deferred
- Payment: Direct bank transfer with manual admin confirmation
- Deployment: Single EC2 Docker host with PostgreSQL and persistent EBS volumes

## Documentation

- [Agent Operating Guide](AGENTS.md)
- [Documentation Index](docs/README.md)
- [Product Brief](docs/product-brief.md)
- [Supplier Portal Design](docs/supplier-portal-design.md)
- [Glossary](docs/glossary.md)
- [Requirements](docs/requirements.md)
- [Policy Documents](docs/policies/README.md)
- [Order Flow](docs/order-flow.md)
- [Domain Model](docs/domain-model.md)
- [Architecture](docs/architecture.md)
- [Production Readiness](docs/production-readiness.md)
- [Backlog](docs/BACKLOG.md)
- [Development Workflow](docs/development-workflow.md)
- [Decision Log](docs/decision-log.md)

## Local Development

- Backend API: [apps/api](apps/api)
- Start local PostgreSQL: `docker compose -f infra/local/postgres/compose.yml up -d`
- Run backend tests: `cd apps/api && ./gradlew test`
