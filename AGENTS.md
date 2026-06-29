# AGENTS.md

AI agent가 Dropship Shop 저장소에서 작업할 때 따르는 운영 지침이다.

이 파일은 README가 아니라 작업 매뉴얼이다. 새 기능을 만들 때 반복해서 필요한 프로젝트 구조, 정책 확인 순서, 테스트 기준, 완료 조건을 짧고 명확하게 둔다.

## Quick Start

- Project: 공급처 출고형 자사몰. 운영자가 상품을 팔고 공급처가 출고한다.
- Backend: Spring Boot, PostgreSQL, JPA, Spring Security.
- Frontend: `apps/web`의 Next.js customer/admin web.
- Payment: Toss Payments.
- Work unit: 기본적으로 `docs/BACKLOG.md` 항목 1개와 git commit 1개가 구현/문서/테스트 단위다.
- Before coding: 관련 정책 문서, ERD, API 문서, `docs/BACKLOG.md`를 먼저 확인한다.
- Before completion: 테스트와 문서 동기화를 확인한다.

자주 쓰는 검증 명령:

```bash
cd apps/api && ./gradlew test
git diff --check
```

## Repository Map

```text
apps/api   Spring Boot backend
apps/web   Customer/admin web, planned
docs       Product, policy, architecture, API, ERD, workflow docs
infra      Local and deployment infrastructure
```

중요 문서:

- 제품 범위: `README.md`, `docs/product-brief.md`, `docs/requirements.md`
- 정책 기준: `docs/policies/README.md`, `docs/decision-log.md`
- 설계 기준: `docs/domain-model.md`, `docs/erd.md`, `docs/api-spec.md`, `docs/order-flow.md`, `docs/architecture.md`
- 실행 단위: `docs/BACKLOG.md`, `docs/PROJECT_LOG.md`, `docs/development-workflow.md`

## Instruction Model

`AGENTS.md`는 살아있는 작업 규칙이다.

- 루트 파일은 저장소 전체 공통 규칙을 담는다.
- 특정 하위 프로젝트의 규칙이 많아지면 그 디렉터리에 별도 `AGENTS.md`를 추가한다.
- 하위 디렉터리의 `AGENTS.md`가 있으면 더 가까운 파일의 지침을 우선한다.
- 사용자와 대화 중 새로 합의한 내용은 이 파일보다 우선한다.
- 반복되는 실수나 작업 기준은 이 파일에 반영한다.

## Source Of Truth

문서나 구현이 충돌하면 다음 순서를 따른다.

1. 사용자와 최근 합의한 명시적 결정
2. `docs/decision-log.md`
3. 정책 문서의 `Confirmed Policy`
4. `docs/erd.md`, `docs/api-spec.md`, `docs/domain-model.md`
5. 초기 방향, 예시, 오래된 설명

충돌을 발견하면 조용히 한쪽만 고치지 않는다. 관련 문서를 함께 정리하거나, 결정이 필요한 항목이면 사용자에게 먼저 확인한다.

## Policy Handling

이 프로젝트에서는 정책이 곧 구현 규칙이다. 특히 주문, 결제, 환불, 배송, 개인정보, 약관은 코드보다 정책 정합성이 더 중요하다.

정책 관련 작업 규칙:

- 정책이 정해지지 않은 기능은 구현 전에 먼저 결정한다.
- 고객 돈, 주문 상태, 환불 가능 조건, 배송비, 주소, 개인정보, 약관 동의에 영향이 있으면 반드시 정책 문서를 확인한다.
- 정책 결정이 생기면 `docs/policies/*`와 `docs/decision-log.md`를 함께 갱신한다.
- API, ERD, 도메인 모델에 영향이 있으면 해당 문서도 같이 갱신한다.
- 정책과 코드가 충돌하면 코드를 기준으로 정책을 덮지 않는다.
- 운영자만 보는 정책과 고객에게 노출되는 정책을 구분한다.
- 고객에게 고지되어야 하는 내용은 이미지 상세가 아니라 별도 텍스트/정책 영역으로 둔다.

정책 테스트에 반드시 반영할 항목:

- 주문서 필수 동의
- 결제 금액 스냅샷
- 부분 취소/부분 환불
- 배송 그룹 단위 처리
- 공급처 품절 처리
- 취소, 반품, 교환 가능 상태
- 개인정보 보관/삭제 경계

## Backlog And Git Workflow

- 구현 작업 전 `docs/BACKLOG.md`의 `Now` 항목을 확인한다.
- `docs/BACKLOG.md`의 큰 작업은 `B-001` 같은 ID를 붙인다.
- `docs/BACKLOG.md`의 각 큰 작업 아래 `Tasks:`로 하위 checklist를 관리한다.
- `docs/PROJECT_LOG.md`의 관련 기록에는 `관련 항목: B-001`처럼 연결 ID를 남긴다.
- `docs/PROJECT_LOG.md`는 결정 이유, 문제와 해결방안, 후속 맥락을 기록한다.
- 작은 버그, 문구, 스타일 수정은 backlog 항목 없이 바로 처리할 수 있다.
- 큰 기능, 정책, 결제, 주문 상태, DB 변경은 backlog에 남긴다.
- 기본 완료 단위는 git commit이다.
- PR은 팀 리뷰, 배포 전 검토, 큰 리스크 변경에만 사용한다.
- Linear와 GitHub Issues는 기본으로 사용하지 않는다.

커밋 메시지는 실제 변경을 설명한다.

```text
feat: implement catalog domain
docs: define order state policy
fix: make payment confirmation idempotent
```

## Implementation Rules

- 기존 패키지 구조와 네이밍을 따른다.
- 상태값은 enum으로 관리한다.
- 주문, 결제, 환불, 배송 상태 전이는 명시적으로 제한한다.
- 가격, 주문 스냅샷, 결제 승인 정보처럼 감사가 필요한 데이터는 당시 값을 보존한다.
- 외부 연동은 idempotency와 재시도 가능성을 고려한다.
- 관리자 API와 고객 API의 권한 경계를 분리한다.
- 공개 API는 노출 가능한 필드만 반환한다.
- secret, access token, OAuth secret, PG secret, DB password는 문서나 커밋에 남기지 않는다.

## Testing Standard

테스트는 "있다"가 아니라 "위험한 동작을 막는다"를 기준으로 작성한다.

새 도메인 또는 API를 추가할 때 기본 테스트:

- 성공 경로
- 권한 경계: 비로그인, 일반 회원, 관리자
- 필수값, 형식, 범위 validation 실패
- 존재하지 않는 리소스 접근
- 상태별 허용/거부 동작
- 공개 API와 관리자 API의 노출 차이
- 문서화된 정책과 맞는 예외 처리

주문, 결제, 환불, 배송 영역 추가 테스트:

- 상태 전이 허용/금지
- 중복 요청 idempotency
- 결제 금액과 주문 스냅샷 불변성
- 부분 취소/부분 환불 단위
- 배송 그룹 단위 처리
- 외부 연동 실패 후 재처리 가능성

상품/카탈로그 영역 추가 테스트:

- 상품 상태별 공개 노출
- 옵션 상태별 구매 가능 여부
- 이미지 개수, 썸네일 유일성, 확장자 제한
- 상세 HTML sanitize
- 상품 고시 버전
- 상품 변경 이력 저장

기본 전략:

- 핵심 사용자 흐름은 통합 테스트로 막는다.
- 복잡한 정책, 계산, 상태 전이는 서비스 단위 테스트를 추가한다.
- 버그를 고치면 같은 문제가 다시 발생하지 않도록 회귀 테스트를 추가한다.

## Documentation Rules

구현 변경은 관련 문서와 함께 움직인다.

- 정책 결정: `docs/policies/*`, `docs/decision-log.md`
- API 변경: `docs/api-spec.md`
- 테이블/관계 변경: `docs/erd.md`
- 도메인 개념 변경: `docs/domain-model.md`
- 주문/결제/배송 흐름 변경: `docs/order-flow.md`
- 작업 범위 변경: `docs/BACKLOG.md`

구현이 아직 없으면 `Planned`, 구현되었으면 `Implemented`로 상태를 명확히 쓴다.

## Review And Completion

완료 전 확인:

- 관련 문서가 구현과 맞는가
- 정책 결정이 필요한 부분을 임의로 정하지 않았는가
- 테스트가 성공/권한/실패/상태 케이스를 포함하는가
- DB migration 순서, nullable, unique, foreign key, index가 정책과 맞는가
- `cd apps/api && ./gradlew test`를 통과했는가
- `git diff --check`를 통과했는가

완료 보고에는 다음을 포함한다.

- 완료한 작업명
- 주요 변경 내용
- 문서 반영 여부
- 실행한 테스트와 결과
- 커밋/푸시 여부
- 남은 리스크 또는 다음 작업
