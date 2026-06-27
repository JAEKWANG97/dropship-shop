# Development Workflow

## Purpose

이 문서는 Dropship Shop 프로젝트의 GitHub PR과 Linear 이슈 운영 방식을 정의한다.

## Core Rule

모든 구현 작업은 Linear 이슈 단위로 진행한다.

예외:

- 아주 작은 문서 오타 수정
- 긴급 운영 메모
- Linear 이슈를 만들기 전 임시 조사 메모

## Linear Issue Scope

좋은 이슈 크기:

- 하나의 PR로 완료 가능하다.
- 리뷰어가 변경 의도를 빠르게 이해할 수 있다.
- 테스트 또는 검증 방법이 명확하다.

너무 큰 이슈 예:

- "주문 시스템 전체 구현"
- "관리자 페이지 전체 구현"
- "결제 붙이기"

적절한 이슈 예:

- `DS-4 Scaffold Spring Boot backend`
- `DS-6 Implement catalog domain`
- `DS-8 Implement order creation`
- `DS-9 Integrate PG sandbox payment approval`

## Branch Naming

Branch names must include the Linear issue identifier.

Recommended format:

```text
feature/ds-4-backend-scaffold
feature/ds-6-catalog-domain
feature/ds-8-order-creation
fix/ds-9-payment-idempotency
docs/ds-2-order-state-policy
```

Rules:

- Use lowercase issue id in branch names.
- Keep branch names short.
- Use `feature/`, `fix/`, `docs/`, or `chore/` prefixes.

## Commit Messages

Commit messages should describe the actual change. Include the Linear issue id when useful.

Examples:

```text
feat(ds-4): scaffold Spring Boot backend
docs(ds-2): define order state policy
fix(ds-9): make payment confirmation idempotent
```

## Pull Request Titles

PR titles should start with the Linear issue id.

Examples:

```text
DS-4 Scaffold Spring Boot backend
DS-6 Implement catalog domain
DS-9 Integrate PG sandbox payment approval
```

## Pull Request Body

Use the repository PR template.

For PRs that complete an issue, use:

```text
Fixes DS-4
```

For PRs that only relate to an issue but should not close it, use:

```text
Refs DS-4
```

## Review And Merge

Default flow:

1. Create or pick a Linear issue.
2. Create a branch with the issue id.
3. Implement one issue-sized change.
4. Open a PR with the issue id in the title.
5. Link the issue in the PR body.
6. Merge after review and verification.

## Status Rules

Recommended Linear status usage:

- `Todo`: issue is ready but not started.
- `In Progress`: branch or implementation work has started.
- `In Review`: PR is open.
- `Done`: PR is merged and verification is complete.

If Linear GitHub integration is enabled, some of these transitions can be automated.

## GitHub And Linear Integration

Required setup:

1. Install or enable the Linear GitHub integration for the `Dropship Shop` organization.
2. Grant access to `JAEKWANG97/dropship-shop`.
3. Configure Linear automation so PR activity updates issue status where appropriate.
4. Keep `DS-*` issue identifiers in branch names, PR titles, and PR bodies.

GitHub repository autolink should map:

```text
DS-123 -> https://linear.app/dropship-shop/issue/DS-123
```

