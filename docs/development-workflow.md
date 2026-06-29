# Development Workflow

## Purpose

이 문서는 Dropship Shop 프로젝트의 현재 개발 운영 방식을 정의한다.

## Core Rule

혼자 개발하는 동안 기본 작업 관리는 `docs/BACKLOG.md`, `docs/PROJECT_LOG.md`, git commit으로 한다.

기본값:

- Linear 사용 안 함
- GitHub Issues 사용 안 함
- PR 기본 생략
- 큰 기능, 정책, 결제, 주문 상태, DB 변경만 backlog에 기록
- 작은 확인사항, env 설정, 수동 검증, 운영 전 체크리스트는 backlog 항목의 `Tasks:`에 기록
- 결정 이유와 작업 맥락은 project log에 기록
- 작은 버그, 문구, 스타일 수정은 바로 커밋

## Work Unit

좋은 작업 크기:

- 하나의 git commit으로 설명 가능하다.
- 테스트 또는 확인 방법이 명확하다.
- 정책/DB/API 변경이면 관련 문서까지 함께 갱신 가능하다.

너무 큰 작업 예:

- "결제 전체 붙이기"
- "관리자 페이지 전체 구현"
- "상품 관리 다 만들기"

적절한 작업 예:

- "Toss sandbox success redirect 처리"
- "소셜 로그인 callback 검증"
- "관리자 주문 발주 시작 액션 연결"

## Repository Model

```text
apps/web   Next.js customer/admin web
apps/api   Spring Boot backend
docs       product, policy, architecture, workflow docs
infra      local and deployment infrastructure
```

frontend, backend, docs가 같은 목적이면 한 커밋에 포함할 수 있다.

## Branch And PR

기본은 `main`에서 작은 커밋으로 진행한다.

branch 또는 PR을 쓰는 경우:

- 결제/주문/환불/배송 상태 전이 변경
- DB migration이 크거나 되돌리기 어렵다
- 배포 직전 리뷰가 필요하다
- 팀원이 생겨 리뷰 흐름이 필요하다

브랜치를 쓸 때는 짧게 쓴다.

```text
feature/toss-sandbox-payment
feature/oauth-browser-check
fix/kakao-oauth-profile
docs/backlog-workflow
```

## Commit Messages

커밋 메시지는 실제 변경을 설명한다. 외부 이슈 ID는 기본으로 붙이지 않는다.

```text
feat: connect admin order actions
fix: hide checkout actions after payment exception
docs: switch workflow to markdown backlog
```

## Backlog Rules

`docs/BACKLOG.md` 사용 기준:

- `Now`: 바로 할 일 1-3개만 둔다.
- `Next`: 가까운 다음 작업을 둔다.
- `Later`: 지금 하지 않을 아이디어를 둔다.
- `Done`: 완료한 큰 작업만 옮긴다.
- 각 큰 작업에는 `B-001` 같은 ID를 붙인다.

작업을 마치면 필요한 경우 backlog 항목을 `Done`으로 옮기고 commit에 포함한다.

## Task And Log Rules

`docs/BACKLOG.md` task 사용 기준:

- 각 큰 작업 아래 `Tasks:`로 하위 checklist를 둔다.
- 키 입력, 수동 QA, 배포 전 확인처럼 작지만 잊으면 안 되는 항목도 관련 backlog 항목 아래에 둔다.
- 완료한 task는 체크하고, 관련 구현이나 검증 커밋에 포함한다.

`docs/PROJECT_LOG.md` 사용 기준:

- 중요한 결정, 막힌 이유, 해결방안, 후속작업을 시간순으로 남긴다.
- backlog와 연결되는 로그에는 `관련 항목: B-001` 줄을 남긴다.
- 단순 작업 목록이나 체크리스트는 project log가 아니라 backlog task에 둔다.

## Verification

변경 범위에 맞는 최소 검증을 한다.

API 변경:

```sh
cd apps/api
./gradlew test --tests '패키지또는테스트명'
```

Web 변경:

```sh
cd apps/web
npm run lint
npm run build
```

공통:

```sh
git diff --check
```

운영 설정, 보안 설정, DB migration, 결제 설정, health endpoint를 바꾸면 더 넓게 확인한다.

```sh
cd apps/api
./gradlew test --rerun-tasks
cd ../..
git diff --check
```

필요하면 API를 실행하고 health endpoints를 직접 확인한다.

```sh
curl -fsS http://localhost:8080/api/health
curl -fsS http://localhost:8080/actuator/health/readiness
curl -fsS http://localhost:8080/actuator/health/liveness
```

운영 환경 변수와 체크리스트 기준은 [Production Readiness](production-readiness.md)를 따른다.

## Completion

완료 전 확인:

- 변경이 backlog 또는 사용자 요청과 맞는가
- 정책 결정이 필요한 부분을 임의로 정하지 않았는가
- 필요한 테스트를 실행했는가
- `git diff --check`를 통과했는가
- secret, access token, OAuth secret, PG secret, DB password가 커밋에 없는가

완료 보고에는 다음을 포함한다.

- 완료한 작업명
- 주요 변경 내용
- 실행한 테스트와 결과
- 커밋 여부
- 남은 리스크 또는 다음 작업
