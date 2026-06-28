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
| `APP_CORS_ALLOWED_ORIGINS` | 브라우저에서 API 호출을 허용할 origin 목록. 쉼표로 구분 |
| `APP_AUTH_JWT_SECRET` | JWT access token 서명 secret. 충분히 긴 랜덤 값 사용 |
| `APP_AUTH_SUCCESS_REDIRECT_URI` | OAuth callback 성공 후 frontend로 보낼 URI |
| `OAUTH_GOOGLE_CLIENT_ID` | Google OAuth client id |
| `OAUTH_GOOGLE_CLIENT_SECRET` | Google OAuth client secret |
| `OAUTH_GOOGLE_REDIRECT_URI` | Google OAuth redirect URI |
| `OAUTH_KAKAO_CLIENT_ID` | Kakao REST API key/client id |
| `OAUTH_KAKAO_CLIENT_SECRET` | Kakao client secret. Kakao 설정에서 사용하지 않으면 빈 값 가능 |
| `OAUTH_KAKAO_REDIRECT_URI` | Kakao OAuth redirect URI |
| `OAUTH_NAVER_CLIENT_ID` | Naver OAuth client id |
| `OAUTH_NAVER_CLIENT_SECRET` | Naver OAuth client secret |
| `OAUTH_NAVER_REDIRECT_URI` | Naver OAuth redirect URI |

Frontend 또는 Toss Payments 위젯에서 쓰는 client key는 public key로 취급하되, backend secret key와 분리해서 배포 환경에 설정한다. `PAYMENTS_TOSS_SECRET_KEY`, `APP_AUTH_JWT_SECRET`, OAuth client secret, DB password, Linear/GitHub token은 커밋하지 않는다.

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
- PostgreSQL은 managed database의 daily automated backup을 기본으로 사용한다.
- 결제/주문 운영 전 point-in-time recovery 사용 가능 여부를 확인한다.
- 주요 배포 전에는 수동 snapshot을 생성하고 복구 절차를 문서화한다.
- 복구 리허설은 최소 월 1회 수행한다.

## CORS And Security

- `APP_CORS_ALLOWED_ORIGINS`에는 실제 customer/admin frontend origin만 넣는다.
- 여러 origin은 쉼표로 구분한다. 예: `https://shop.example.com,https://admin.example.com`
- `/api/products`, `/api/policies`, `/api/health`, actuator health/info만 public이다.
- `/api/auth/oauth2/**`는 OAuth 시작/콜백을 위해 public이다.
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
- 송장번호 입력 누락과 배송조회 실패 건을 확인한다.

배포 전 확인:

- `cd apps/api && ./gradlew test --rerun-tasks`
- `git diff --check`
- staging 또는 운영 동일 profile에서 `/api/health`, `/actuator/health/readiness`, `/actuator/health/liveness` 확인
- `DATABASE_*`, `PAYMENTS_TOSS_SECRET_KEY`, `APP_CORS_ALLOWED_ORIGINS`, `APP_AUTH_*`, `OAUTH_*` 설정 확인
- Flyway migration 적용 순서 확인
- PostgreSQL backup/snapshot 상태 확인
