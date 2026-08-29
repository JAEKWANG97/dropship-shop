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
| `SMS_SENS_ENABLED` | Naver Cloud SENS SMS 발송 활성화. 운영 기본값은 `false`; 실제 SENS 자격증명과 발신번호가 준비된 뒤 명시적으로 `true`로 켠다 |
| `SMS_SENS_ACCESS_KEY` | Naver Cloud API access key |
| `SMS_SENS_SECRET_KEY` | Naver Cloud API secret key. 서버에서만 사용 |
| `SMS_SENS_SERVICE_ID` | SENS SMS service id |
| `SMS_SENS_FROM_NUMBER` | SENS에 등록된 발신번호 |
| `EMAIL_SES_ENABLED` | 고객 문의 답변 AWS SES 발송 활성화. SES 도메인 인증과 production access 후 `true` |
| `EMAIL_SES_REGION` | SES region. 기본값 `ap-northeast-2` |
| `EMAIL_FROM_ADDRESS` | 문의 답변 발신/회신 주소. 기본값 `contact@coreable-saf.com` |
| `APP_PUBLIC_BASE_URL` | 문의 조회 링크에 사용하는 공개 웹 origin. 운영값 `https://coreable-saf.com` |
| `DROPSHIP_WEB_ORIGIN` | Web server action이 B-100 mutation에 보내는 canonical Origin. 운영값은 API CORS allowlist와 같은 `https://coreable-saf.com`; 미설정 시 `APP_PUBLIC_BASE_URL` 사용 |
| `APP_INQUIRY_LOOKUP_SECRET` | 문의 조회 HMAC secret. 32자 이상 랜덤 값이며 변경 시 기존 조회 링크가 무효화됨 |
| `APP_SUPPLIER_PORTAL_ENABLED` | 공급처 신청·초대·포털 외부 경로 활성화. Production 기본값은 `false` |
| `APP_SUPPLIER_PORTAL_HMAC_SECRET` | 공급처 신청·초대 명령 HMAC secret. 32자 이상 랜덤 값이며 실제 값은 커밋하거나 로그에 출력하지 않음 |
| `APP_SUPPLIER_PORTAL_SUCCESS_REDIRECT_URI` | 공급처 Kakao 연결 성공 후 이동할 frontend URI. 운영값 `https://coreable-saf.com/supplier` |
| `APP_CORS_ALLOWED_ORIGINS` | 브라우저에서 API 호출을 허용할 origin 목록. 쉼표로 구분 |
| `APP_INTERNAL_SYNC_TOKEN` | 내부 배송조회 동기화 API 호출용 shared token. 서버/스케줄러에서만 사용 |
| `APP_AUTH_JWT_SECRET` | JWT access token 서명 secret. 충분히 긴 랜덤 값 사용 |
| `APP_AUTH_SUCCESS_REDIRECT_URI` | OAuth callback 성공 후 frontend로 보낼 URI |
| `APP_STORAGE_PUBLIC_BASE_URL` | 상품 이미지 공개 URL prefix. 기본값은 `/uploads/products` |
| `APP_STORAGE_LOCAL_UPLOAD_DIR` | local storage 사용 시 상품 이미지 저장 디렉터리. EC2 기본값은 `/var/app/uploads/products` |
| `OAUTH_GOOGLE_CLIENT_ID` | Google OAuth client id |
| `OAUTH_GOOGLE_CLIENT_SECRET` | Google OAuth client secret |
| `OAUTH_GOOGLE_REDIRECT_URI` | Google OAuth redirect URI |
| `OAUTH_KAKAO_CLIENT_ID` | Kakao REST API key/client id |
| `OAUTH_KAKAO_CLIENT_SECRET` | Kakao client secret. Kakao 설정에서 사용하지 않으면 빈 값 가능 |
| `OAUTH_KAKAO_REDIRECT_URI` | Kakao OAuth redirect URI |
| `OAUTH_KAKAO_SUPPLIER_REDIRECT_URI` | 공급처 초대 수락 전용 Kakao OAuth redirect URI |
| `OAUTH_NAVER_CLIENT_ID` | Naver OAuth client id |
| `OAUTH_NAVER_CLIENT_SECRET` | Naver OAuth client secret |
| `OAUTH_NAVER_REDIRECT_URI` | Naver OAuth redirect URI |
| `DOMEGGOOK_PURCHASE_ENABLED` | Private API 읽기·동기화 활성화. 실주문 검증 전 기본값 `false` |
| `DOMEGGOOK_AUTO_ORDER_ENABLED` | 입금확인 주문의 `setOrder` 자동 실행. e-money 충전과 실주문·취소 검증 후에만 `true` |
| `DOMEGGOOK_CATALOG_SYNC_ENABLED` | ACTIVE 상품의 공급가·옵션·재고 조회 스케줄 활성화 |
| `DOMEGGOOK_CATALOG_SYNC_DRY_RUN` | `true`면 공급처 조회와 로그만 수행하고 DB를 변경하지 않음 |
| `DOMEGGOOK_CATALOG_SYNC_BATCH_SIZE` | 한 시간 실행당 상품 수. 기본 20, 최대 100 |
| `DOMEGGOOK_CATALOG_SYNC_INTERVAL_MS` | 상품 동기화 주기. 기본 3,600,000ms |
| `DOMEGGOOK_OPEN_API_KEY` | 승인된 Domeggook API key |
| `DOMEGGOOK_PURCHASE_USER_ID` | 일반 Domeggook 구매 계정 ID |
| `DOMEGGOOK_PURCHASE_USER_PASSWORD` | 일반 Domeggook 구매 계정 비밀번호. 서버에서만 사용 |
| `DOMEGGOOK_PURCHASE_CLIENT_IP` | Private API에 등록한 고정 client IP |

현재 고객 결제 경로는 계좌입금이며, PG 결제 key는 사용하지 않는다. `SMS_SENS_SECRET_KEY`, `APP_AUTH_JWT_SECRET`, `APP_SUPPLIER_PORTAL_HMAC_SECRET`, `APP_INTERNAL_SYNC_TOKEN`, OAuth client secret, DB password, Linear/GitHub token은 커밋하지 않는다.

`APP_SUPPLIER_PORTAL_ENABLED`는 production에서 `false`를 유지한다. `B-100`~`B-105`, active managed 공급처 신청 개인정보 고지, 실제 초대·운영 email 도착, `B-098`의 공급처별 time-valid contract evidence를 모두 검증한 뒤에만 연다. `B-102` 재고·checkout guard 완료만으로는 이 flag를 켜지 않는다.

`DOMEGGOOK_PURCHASE_ENABLED=true`, `DOMEGGOOK_AUTO_ORDER_ENABLED=false`로 먼저 로그인·상품 검증·자산 조회와 관리자 상태 화면을 확인한다. 최저가 실제 상품의 주문 생성·조회·취소와 e-money 반환까지 확인하기 전에는 자동 주문을 켜지 않는다.

상품 동기화는 `DOMEGGOOK_CATALOG_SYNC_ENABLED=true`, `DOMEGGOOK_CATALOG_SYNC_DRY_RUN=true`로 먼저 실행한다. 관리자 상품 상세와 로그에서 가격·옵션 수·판매 상태를 확인한 뒤 dry-run을 `false`로 전환한다. 기본 설정은 상품당 호출 간격 1초, 시간당 20개로 하루 최대 약 480회다.

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

### Payment Legacy Data Check

Before deploying the account-transfer-only release, check whether the production database still has a Toss payment or an unfinished former PG refund. If either query returns rows, stop the deployment and resolve the records manually; the executable Toss paths are removed by this release.

```sql
SELECT provider, status, COUNT(*)
FROM payments
WHERE provider = 'TOSS_PAYMENTS'
GROUP BY provider, status;

SELECT status, COUNT(*)
FROM refunds
WHERE status IN ('PG_CANCEL_REQUESTED', 'RETRY_REQUIRED', 'FAILED', 'MANUAL_REVIEW_REQUIRED')
GROUP BY status;
```

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

- 현재 저비용 배포는 EC2 EBS-backed local volume에 상품 이미지를 저장하고 S3에 백업한다.
- 운영 전환 시에도 PostgreSQL에는 이미지 binary를 넣지 않는다. 상품/상세 API는 image URL metadata만 저장한다.
- local storage 사용 시 `APP_STORAGE_LOCAL_UPLOAD_DIR`는 애플리케이션 재배포로 지워지지 않는 persistent volume 경로로 둔다.
- `APP_STORAGE_PUBLIC_BASE_URL`은 API가 직접 `/uploads/products/**`를 서빙하면 `/uploads/products`, CDN/object storage로 이전하면 해당 public prefix로 바꾼다.

## AWS EC2 Docker Deployment

- 배포 baseline은 [AWS EC2 Docker Deployment](aws-ec2-docker-deployment.md)를 따른다.
- 현재 EC2는 비용 절감을 위해 정지됐고 자동 시작/정지 schedule도 비활성화됐다. 개발 재개 시 수동 시작 후 schedule 복구 여부를 결정한다.
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
- 입금자명, 입금·환불 계좌정보, 개인정보, 외부 연동 원문은 로그에 남기지 않는다.
- 현재 CloudWatch alarm은 아직 구성되지 않았다. B-062에서 EC2 status check, CPU credit, memory/swap, container restart, backup freshness 알림을 최소 범위로 연결한다.
- Sentry와 OpenTelemetry는 실제 장애 분석에 AWS 기본 알림만으로 부족할 때 검토한다.
- 5xx 급증, 장기 입금대기 증가, 입금 불일치 증가, 장기 미완료 환불 증가, DB connection failure는 운영 알림 대상으로 둔다.

## Error Response Baseline

- API 오류 응답은 `timestamp`, `status`, `code`, `message`, `path`, `fields`를 가진 공통 JSON 포맷을 사용한다.
- Validation 오류는 `VALIDATION_FAILED`와 field-level details로 반환한다.
- 도메인 정책 또는 상태 전이 guard 위반은 `BUSINESS_RULE_VIOLATION`으로 반환한다.
- 인증/권한 오류도 같은 JSON 포맷으로 반환한다.
- 결제/환불/주문 상태 전이 오류는 잘못된 성공 응답으로 숨기지 않는다.
- 운영 알림은 우선 5xx, 장기 입금대기, 미완료 환불 증가를 기준으로 시작한다.

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
- `DATABASE_*`, `APP_CORS_ALLOWED_ORIGINS`, `APP_AUTH_*`, `OAUTH_*` 설정 확인
- `APP_SUPPLIER_PORTAL_ENABLED=false`와 `APP_SUPPLIER_PORTAL_HMAC_SECRET` 32자 이상 설정을 값 출력 없이 확인
- Kakao 개발자 설정에 `OAUTH_KAKAO_SUPPLIER_REDIRECT_URI`를 등록하고 `APP_SUPPLIER_PORTAL_SUCCESS_REDIRECT_URI`를 확인
- 공급처 포털 활성화 전 `B-100`~`B-105`, active managed 개인정보 고지, 실제 email delivery와 `B-098` contract evidence 검증 완료 확인. `B-102`만으로 활성화하지 않음
- `SMS_SENS_*` 설정과 SENS 발신번호 승인 상태 확인
- SES `coreable-saf.com` identity/DKIM과 production access, EC2 role의 제한된 `ses:SendEmail` 권한 확인
- 실제 문의 답변 이메일 도착, 조회 링크, `FAILED`/`SKIPPED` 재시도 확인
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

Follow-up on 2026-07-05:

- E2E drift를 수리하고 배포 URL 전용 `deploy-smoke.spec.ts`를 추가했다.
- `E2E_WEB_BASE_URL=https://coreable-saf.com npx playwright test deploy-smoke` 결과 `6 passed`.
- 배포 URL에서 `visual-regression`과 screenshot 기반 readiness test는 명시 skip되며, 공개 readiness는 `2 passed`, snapshot/auth/seed 의존 test는 `28 skipped`로 실패 없이 종료된다.

## OAuth And Payment Readiness

DS-76 local verification on 2026-06-29:

- Google, Kakao, and Naver OAuth authorize endpoints returned `302` redirects to their provider domains.
- Cookie-based customer login was verified with `/api/me`.
- Checkout preflight was verified through required account agreement, cart item add, checkout creation, and checkout policy confirmation.

Remaining beta gates:

- Complete real browser OAuth login and callback for Google, Kakao, and Naver with provider accounts.
- Before accepting bank-transfer orders, finalize purchase-safety service, cash-receipt operations, public refund wording, and initial product certification review.
