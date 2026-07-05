# Production Readiness Baseline

Status: Implemented baseline by DS-17

## Purpose

운영 배포 전에 필요한 최소 설정, 검증 명령, 운영 체크리스트를 한곳에 둔다.

## Runtime Profiles

- `local`: 로컬 PostgreSQL과 디버그 SQL 로그를 사용한다.
- `test`: H2 in-memory DB와 `ddl-auto=create-drop`을 사용한다.
- `prod`: 운영 PostgreSQL, Flyway migration, JPA schema validation, INFO logging을 사용한다.

운영 실행 예:

```sh
cd apps/api
SPRING_PROFILES_ACTIVE=prod java -jar build/libs/dropship-shop-api-0.0.1-SNAPSHOT.jar
```

## Required Production Environment Variables

| Variable | Purpose |
| --- | --- |
| `SPRING_PROFILES_ACTIVE=prod` | 운영 profile 활성화 |
| `SERVER_PORT` | API 서버 포트. 기본값은 `8080` |
| `DATABASE_URL` | PostgreSQL JDBC URL |
| `DATABASE_USERNAME` | PostgreSQL 사용자 |
| `DATABASE_PASSWORD` | PostgreSQL 비밀번호 |
| `PAYMENTS_TOSS_SECRET_KEY` | Toss Payments secret key. 서버에서만 사용 |
| `PAYMENTS_TOSS_BASE_URL` | Toss Payments API URL. 기본값은 `https://api.tosspayments.com` |
| `SMS_SENS_ENABLED` | Naver Cloud SENS SMS 발송 활성화. 운영 기본값은 `false`; 실제 SENS 자격증명과 발신번호가 준비된 뒤 명시적으로 `true`로 켠다 |
| `SMS_SENS_ACCESS_KEY` | Naver Cloud API access key |
| `SMS_SENS_SECRET_KEY` | Naver Cloud API secret key. 서버에서만 사용 |
| `SMS_SENS_SERVICE_ID` | SENS SMS service id |
| `SMS_SENS_FROM_NUMBER` | SENS에 등록된 발신번호 |
| `NEXT_PUBLIC_TOSS_CLIENT_KEY` | Toss Payments client key. frontend 결제창 호출에 사용 |
| `APP_CORS_ALLOWED_ORIGINS` | 브라우저에서 API 호출을 허용할 origin 목록. 쉼표로 구분 |
| `APP_INTERNAL_SYNC_TOKEN` | 내부 배송조회 동기화 API 호출용 shared token. 서버/스케줄러에서만 사용 |
| `APP_AUTH_JWT_SECRET` | JWT access token 서명 secret. 충분히 긴 랜덤 값 사용 |
| `APP_AUTH_SUCCESS_REDIRECT_URI` | OAuth callback 성공 후 frontend로 보낼 URI |
| `APP_STORAGE_PUBLIC_BASE_URL` | 상품 이미지 공개 URL prefix. 기본값은 `/uploads/products` |
| `APP_STORAGE_LOCAL_UPLOAD_DIR` | local storage 사용 시 상품 이미지 저장 디렉터리. 테스트 배포 기본값은 `/var/app/uploads/products` |
| `OAUTH_GOOGLE_CLIENT_ID` | Google OAuth client id |
| `OAUTH_GOOGLE_CLIENT_SECRET` | Google OAuth client secret |
| `OAUTH_GOOGLE_REDIRECT_URI` | Google OAuth redirect URI |
| `OAUTH_KAKAO_CLIENT_ID` | Kakao REST API key/client id |
| `OAUTH_KAKAO_CLIENT_SECRET` | Kakao client secret. Kakao 설정에서 사용하지 않으면 빈 값 가능 |
| `OAUTH_KAKAO_REDIRECT_URI` | Kakao OAuth redirect URI |
| `OAUTH_NAVER_CLIENT_ID` | Naver OAuth client id |
| `OAUTH_NAVER_CLIENT_SECRET` | Naver OAuth client secret |
| `OAUTH_NAVER_REDIRECT_URI` | Naver OAuth redirect URI |

Frontend 또는 Toss Payments 위젯에서 쓰는 client key는 public key로 취급하되, backend secret key와 분리해서 배포 환경에 설정한다. 현재 개발과 sandbox 검증은 Toss Payments test key로 진행하고, live PG 심사와 live key 전환은 배포된 홈페이지 URL이 준비된 뒤 진행한다. `PAYMENTS_TOSS_SECRET_KEY`, `SMS_SENS_SECRET_KEY`, `APP_AUTH_JWT_SECRET`, `APP_INTERNAL_SYNC_TOKEN`, OAuth client secret, DB password, Linear/GitHub token은 커밋하지 않는다.

## Health And Readiness

공개 health endpoints:

```sh
curl -fsS http://localhost:8080/api/health
curl -fsS http://localhost:8080/actuator/health
curl -fsS http://localhost:8080/actuator/health/readiness
curl -fsS http://localhost:8080/actuator/health/liveness
```

배포 플랫폼의 readiness probe는 `/actuator/health/readiness`를 사용한다. 단순 외부 uptime check는 `/api/health` 또는 `/actuator/health`를 사용할 수 있다.

## Database Migration And Backup

- 운영 profile은 Flyway migration을 활성화한다.
- 운영 profile은 Hibernate `ddl-auto=validate`를 사용한다. 운영 DB schema 변경은 migration 파일로만 반영한다.
- 배포 전 `./gradlew test --rerun-tasks`와 prod 환경 staging DB migration dry run을 확인한다.
- 현재 저비용 EC2 baseline은 `/opt/coreable/backup.sh`가 `pg_dump -Fc` dump를 `s3://coreable-backups-prod/db/`에 업로드한다.
- 상품 업로드 이미지는 `/var/lib/coreable/uploads/products`에서 `s3://coreable-backups-prod/uploads/products/`로 sync한다.
- EC2 root volume은 DLM weekly snapshot retain 4로 보호한다.
- 복구 절차는 [Backup And Restore Runbook](backup-restore.md)를 따른다.
- 장기 운영에서 RDS로 전환하면 managed database daily automated backup과 point-in-time recovery를 다시 기본 기준으로 둔다.
- 복구 리허설은 최소 월 1회 수행한다.

## Product Image Storage

- 테스트 배포는 EC2 EBS-backed local volume에 상품 이미지를 저장할 수 있다.
- 운영 전환 시에도 PostgreSQL에는 이미지 binary를 넣지 않는다. 상품/상세 API는 image URL metadata만 저장한다.
- local storage 사용 시 `APP_STORAGE_LOCAL_UPLOAD_DIR`는 애플리케이션 재배포로 지워지지 않는 persistent volume 경로로 둔다.
- `APP_STORAGE_PUBLIC_BASE_URL`은 API가 직접 `/uploads/products/**`를 서빙하면 `/uploads/products`, CDN/object storage로 이전하면 해당 public prefix로 바꾼다.

## AWS EC2 Docker Deployment

- 배포 baseline은 [AWS EC2 Docker Deployment](aws-ec2-docker-deployment.md)를 따른다.
- GitHub Actions가 API/Web Docker 이미지를 GHCR에 push하고, EC2는 이미지를 pull해 Docker Compose로 재시작한다.
- 서버에서 애플리케이션 소스 빌드는 하지 않는다.
- PostgreSQL data, 상품 이미지, Cloudflare Origin Certificate는 EC2 local persistent path에 둔다.
- Cloudflare SSL/TLS는 nginx origin TLS가 준비된 뒤 `Full (strict)`를 사용한다.
- 실결제 오픈 전에는 RDS와 S3-compatible image serving 전환 필요성을 다시 검토한다. Backup/restore 리허설은 [Backup And Restore Runbook](backup-restore.md) 기준으로 수행한다.

## CORS And Security

- `APP_CORS_ALLOWED_ORIGINS`에는 실제 customer/admin frontend origin만 넣는다.
- 여러 origin은 쉼표로 구분한다. 예: `https://shop.example.com,https://admin.example.com`
- `/api/products`, `/api/policies`, `/api/health`, actuator health/info만 public이다.
- `/api/auth/oauth2/**`는 OAuth 시작/콜백을 위해 public이다.
- `/api/internal/**`는 브라우저 UI에서 호출하지 않으며 `X-Internal-Sync-Token` header가 `APP_INTERNAL_SYNC_TOKEN`과 일치해야 한다.
- 배송조회 scheduler 또는 배포 플랫폼 cron은 `POST /api/internal/shipments/tracking-sync`를 호출하고, 요청 header에 `X-Internal-Sync-Token: ${APP_INTERNAL_SYNC_TOKEN}`을 넣는다.
- 배송조회 payload는 carrier/trackingNumber별 `trackingStatus` 또는 `failureReason`을 전달한다. `DELIVERED`는 주문을 배송완료로 전환하고, `failureReason`은 현재 주문 상태를 유지한 채 관리자 화면에 실패 사유로 남긴다.
- `/api/admin/**`는 `ADMIN` role만 접근할 수 있다.
- Session, form login, basic login은 사용하지 않는다.
- 인증은 `ACCESS_TOKEN` HttpOnly cookie의 stateless JWT로 처리한다.
- 운영에서는 `app.auth.cookie-secure=true`를 유지하고 HTTPS에서만 cookie가 전송되게 한다.
- 운영에서는 HTTPS 앞단 proxy 또는 load balancer를 사용한다.

## Logging And Error Monitoring

- 운영 logging 기본값은 root `INFO`다.
- Hibernate SQL debug logging은 운영에서 끈다.
- 결제 secret, 개인정보, raw PG payload는 로그에 남기지 않는다.
- MVP error monitoring은 배포 플랫폼 로그 수집과 알림으로 시작한다.
- 운영 전 Sentry, OpenTelemetry collector, 또는 cloud provider error alert 중 하나를 선택해 uncaught exception과 5xx rate alert를 연결한다.
- 5xx 급증, payment exception 증가, payment webhook 검증 실패, `REVIEW_REQUIRED` 증가, refund retry 증가, DB connection failure는 운영 알림 대상으로 둔다.

## Error Response Baseline

- API 오류 응답은 `timestamp`, `status`, `code`, `message`, `path`, `fields`를 가진 공통 JSON 포맷을 사용한다.
- Validation 오류는 `VALIDATION_FAILED`와 field-level details로 반환한다.
- 도메인 정책 또는 상태 전이 guard 위반은 `BUSINESS_RULE_VIOLATION`으로 반환한다.
- 인증/권한 오류도 같은 JSON 포맷으로 반환한다.
- 결제/환불/주문 상태 전이 오류는 잘못된 성공 응답으로 숨기지 않는다.
- 운영 알림은 우선 5xx와 결제/환불 실패 큐 증가를 기준으로 시작한다.

## Admin Operating Checklist

매일 확인:

- `GET /actuator/health/readiness`가 `UP`인지 확인한다.
- 관리자 주문 큐에서 `SUPPLIER_ORDER_PENDING` 지연 건을 확인한다.
- 환불 큐에서 `RETRY_REQUIRED` 또는 `FAILED` 건을 확인한다.
- 품절 처리 건의 환불 상태가 고객에게 완료로 잘못 노출되지 않았는지 확인한다.
- 송장번호 입력 누락, 배송조회 실패 사유, 장기 미조회 배송 건을 확인한다.
- 배송조회 실패가 반복되는 건은 관리자 주문 상세에서 조회 실패 사유를 갱신하거나 수동 배송완료 보정 사유를 남긴다.

배포 전 확인:

- `cd apps/api && ./gradlew test --rerun-tasks`
- `git diff --check`
- staging 또는 운영 동일 profile에서 `/api/health`, `/actuator/health/readiness`, `/actuator/health/liveness` 확인
- `DATABASE_*`, `PAYMENTS_TOSS_SECRET_KEY`, `APP_CORS_ALLOWED_ORIGINS`, `APP_AUTH_*`, `OAUTH_*` 설정 확인
- `SMS_SENS_*` 설정과 SENS 발신번호 승인 상태 확인
- Flyway migration 적용 순서 확인
- PostgreSQL backup/snapshot 상태 확인

## Deployment Smoke Verification - 2026-07-05

대상:

- 배포 commit: `15ab8e9` (`feat: fill business profile legal values`)
- 배포 URL: `https://coreable-saf.com`
- 운영 서버 점검은 읽기 전용 명령으로만 수행했다. 서버 설정 변경, container restart, DB write는 하지 않았다.

결과:

- GitHub Actions: Pass
  - Deploy run `28726833792` 성공.
  - 실행 시간: `verify 3m10s`, `build-and-push 5m43s`, `deploy 1m09s`.
  - B-040 기준값 `verify 2m58s`, `build-and-push 7m39s`, `deploy 1m12s`와 비교하면 `build-and-push`가 약 1m56s 감소했다.
- Container health: Pass
  - `api`, `web`, `postgres`, `nginx` 모두 Up.
  - 배포 이미지는 API/Web 모두 `15ab8e9` tag로 기동 중이다.
  - EC2 내부 `http://localhost:8080/api/health`, `http://localhost:8080/actuator/health/readiness` 성공.
  - Cloudflare 경유 `https://coreable-saf.com/api/health`, `https://coreable-saf.com/actuator/health/readiness` 성공.
- Memory snapshot: Pass with caution
  - `free -m`: total 906MB, used 634MB, available 271MB, swap 2047MB 중 201MB 사용.
  - `docker stats --no-stream`: API 약 260MiB/512MiB, Web 약 60MiB/192MiB, Postgres 약 17MiB/256MiB, nginx 약 4MiB/64MiB.
  - 현재 smoke 기준 즉시 장애는 없지만 `t4g.micro` 메모리 여유는 작으므로 5xx, OOM, swap 증가를 계속 확인한다.
- Env key completeness: Pass
  - 서버 `/opt/coreable/.env` key 목록은 `infra/aws/ec2/env.example` key 목록과 일치한다.
  - 값은 출력하거나 복사하지 않았다.
- Storage path: Pass
  - `APP_STORAGE_*`는 서버 `.env`가 아니라 compose environment로 API container에 주입된다.
  - host upload path `/var/lib/coreable/uploads/products`와 API container path `/var/app/uploads/products`가 존재한다.
  - `/uploads/products` public prefix는 compose에 설정되어 있다.
- Migration validation: Pass
  - 서버 Flyway schema history는 성공 migration 29개, latest `V29__add_product_option_source_metadata.sql`.
  - 로컬 migration 최신 파일과 서버 적용 최신 version이 일치한다.
- Public/legal routes: Pass
  - `/`, `/products`, `/policies`, `/policies/terms`, `/policies/privacy`, `/policies/shipping`, `/policies/cancellation-refund`, `/policies/stock-risk`, `/company`, `/support` 모두 200.
  - `/company`에 상호, 대표자, 사업자등록번호, 통신판매업 신고 면제, 사업장/반품 주소, AWS 호스팅, 고객센터 전화번호, 고객센터 이메일, 운영 시간이 노출된다.
  - footer에서 이용약관, 개인정보처리방침, 배송 정책, 취소/환불 정책, 결제 후 품절 안내, 고객 문의 링크가 노출된다.
  - `https://coreable-saf.com/api/dev/login?role=ADMIN`은 404로 응답해 dev login이 운영 URL에 노출되지 않는다.
- Playwright deployment smoke: Fail
  - 명령: `E2E_WEB_BASE_URL=https://coreable-saf.com E2E_API_BASE_URL=https://coreable-saf.com npm run test:e2e`
  - 결과: `15 passed`, `41 skipped`, `8 failed`.
  - Skip은 배포 URL에서 `E2E_CUSTOMER_COOKIE`, `E2E_ADMIN_COOKIE`, local seed order가 없어서 발생한 예상 skip이다.
  - Fail 원인:
    - `/login` 테스트가 예전 heading `로그인`을 기대하지만 운영 화면은 `현장에 필요한 안전용품을 바로 주문하세요` heading으로 변경됐다.
    - 모바일 상품상세에 하단 고정 구매바가 추가되어 `장바구니` button locator가 2개 요소와 매칭된다.
    - 운영 데이터와 상세 이미지 길이가 로컬 snapshot baseline과 달라 full-page screenshot snapshot이 실패한다.

후속:

- 배포 URL 전용 Playwright smoke는 snapshot test와 상태 변경/seed 의존 test를 분리해야 한다.
- 로그인/상품상세 모바일 테스트 locator는 현재 UI 기준으로 갱신한다.
- 운영 데이터가 바뀌는 환경에서는 full-page snapshot 대신 핵심 viewport/overflow/CTA 존재 여부 중심으로 검증한다.

## Beta OAuth And Payment Readiness

DS-76 local verification on 2026-06-29:

- Google, Kakao, and Naver OAuth authorize endpoints returned `302` redirects to their provider domains.
- Cookie-based customer login was verified with `/api/me`.
- Checkout preflight was verified through required account agreement, cart item add, checkout creation, and checkout policy confirmation.
- Local Toss confirmation with a fake key reached the payment exception path.

Remaining beta gates:

- Complete real browser OAuth login and callback for Google, Kakao, and Naver with provider accounts.
- Configure Toss Payments sandbox secret key on the API and public client key on the web app.
- Verify real Toss sandbox success redirect, server confirmation, and order transition to `SUPPLIER_ORDER_PENDING`.
- Verify Toss failure/cancel redirect and payment exception monitoring before production payment opening.
