# Project Log

## 2026-07-04 11:20 KST

- 관련 항목: B-050
- 작업: 첫 소셜 로그인 온보딩 추천인 코드 수집을 구현했다. 추천 코드는 `/api/me/referral` 조회 시 lazy 생성하고, 신규 OAuth 계정에만 callback success URL에 `onboarding=1`을 붙여 `/welcome`에서 추천인 코드 입력 또는 건너뛰기를 제공한다. 계정 화면에는 내 추천 코드와 추천인 등록 여부를 표시하고, 관리자에는 읽기 전용 추천 관계 목록을 추가했다.
- 문제·고민: 현재 서비스는 별도 회원가입 폼이 없어 추천인 코드를 받을 시점이 애매하다. 또한 추천 관계는 마케팅/보상으로 확장될 수 있지만, 포인트·정산까지 같이 넣으면 가격/주문 정책 범위가 커진다.
- 해결방안: 신규 계정 첫 로그인 직후 온보딩으로만 추천인 입력을 받고, 기존 회원 재로그인에는 온보딩을 띄우지 않는다. 1차 범위는 `referred_by_user_id`, `referred_at` 기록과 관리자 조회까지로 제한했다. 고객 화면에는 추천인 개인정보를 노출하지 않고 내 코드와 등록 여부만 보여준다.
- 후속작업: 추천 보상, 포인트, 추천인별 성과 집계, 악용 방지 정책은 실제 마케팅 정책이 정해진 뒤 별도 이슈로 분리한다.

## 2026-07-04 10:10 KST

- 관련 항목: B-049
- 작업: 상품 상세의 비로그인 구매 진입을 개선했다. 비로그인 상태에서도 옵션 select, 수량 input, 장바구니/바로구매 버튼을 보여주고, 제출 시 서버 액션에서 로그인 페이지로 보낸다.
- 문제·고민: 기존 화면은 구매 조건을 확인하기 전에 로그인부터 요구해 이탈을 만들 수 있었다. 다만 백엔드 장바구니 정책은 `CUSTOMER` 인증 필수라 게스트 장바구니를 새로 만들면 정책과 구현 범위가 커진다.
- 해결방안: 상품 상세 렌더링에서는 세션 분기를 제거하고, `addCartItem` 액션 초입에서 `getCurrentUser()`로 세션을 확인한 뒤 비로그인은 `/login?redirectTo=/products/{productId}`로 redirect한다. 이 redirect는 try-catch 밖에 둬 Next redirect 예외가 실패 메시지로 삼켜지지 않게 했다.
- 결정: 로그인 후 상품 상세 복귀는 유지하지만, 선택했던 옵션/수량 복원은 이번 범위에서 구현하지 않는다.
- 후속작업: B-051에서 상품 상세 구매 영역의 시각 계층과 모바일 밀도를 추가로 정리한다.

## 2026-07-04 01:10 KST

- 관련 항목: B-048
- 작업: 로컬 개발용 seed 고객/관리자 간편 로그인 엔드포인트를 추가했다. `/api/dev/login?role=CUSTOMER|ADMIN` 또는 JSON `providerUserId` 요청으로 기존 local seed 사용자를 찾아 OAuth 로그인과 같은 `ACCESS_TOKEN` HttpOnly cookie를 발급한다.
- 문제·고민: B-013 수동 QA와 Playwright smoke를 반복할 때마다 JWT를 직접 만들고 브라우저 쿠키를 심는 방식은 비효율적이고 실수 가능성이 높다. 반대로 이 기능이 운영에 노출되면 인증 우회 백도어가 된다.
- 해결방안: controller bean은 `@Profile({"local","dev"})`와 `app.dev-login.enabled=true`를 둘 다 만족해야만 로드되게 했다. `application-local.yml`에만 flag를 두고, prod 프로필 테스트는 flag를 강제로 true로 줘도 `/api/dev/login`이 404인지 확인한다.
- 결정: seed 로그인은 기존 사용자만 대상으로 하며 사용자 생성이나 권한 변경은 하지 않는다. Playwright helper는 로컬에서 명시 쿠키 env가 없으면 dev login API를 호출하고, 배포/비로컬 smoke는 기존처럼 cookie env를 명시할 수 있다.
- 후속작업: 배포 URL smoke에서는 dev login을 사용하지 않고 실제 인증 쿠키 또는 OAuth 실브라우저 검증(B-002)으로 확인한다.

## 2026-07-04 00:20 KST

- 관련 항목: B-047
- 작업: Playwright E2E 페이지 커버리지를 확장했다. 공통 helper를 분리하고 로그인/콜백 리다이렉트, 계좌입금 주문서, 고객 주문 상세와 클레임 화면, 정책 상세, 빈 상태/404/권한 없음 상태, 데스크톱 스크린샷 baseline을 추가했다.
- 문제·고민: 주문/체크아웃 화면은 B-003 local seed 주문을 써야 안정적으로 검증할 수 있고, 스크린샷은 생성 시각/입금 기한 같은 동적 값이 그대로 찍히면 flaky해진다. 클레임 증빙 파일 제출까지 E2E에 넣으면 업로드 저장소와 validation 상태에 따라 UI smoke가 불안정해질 수 있다.
- 해결방안: `apps/web/tests/e2e/helpers.ts`에 시드 주문 조회, 인증 쿠키 주입, overflow 검사를 모으고 B-003 seed 주문을 재사용했다. 스크린샷은 desktop project만 baseline을 추가하고 입금 기한/생성 시각 등 동적 영역을 `mask`로 가렸다.
- 결정: 증빙 파일 업로드 E2E는 제출하지 않고 input 노출까지만 확인한다. 실제 파일 저장/검증은 B-015/B-046 API 테스트와 수동 QA에서 확인한다. `/auth/callback/success`는 독립 화면이 아니라 safe redirect 흐름으로만 확인한다.
- 후속작업: B-013 수동 디자인 QA에서 발견되는 화면 깨짐은 Playwright screenshot baseline 또는 별도 UI 수정 이슈로 이어서 고정한다.

## 2026-07-03 16:00 KST

- 관련 항목: B-047
- 작업: Playwright E2E 페이지 커버리지 gap 분석 결과를 백로그에 반영했다. 전체 24개 페이지 중 `/login`, `/auth/callback/success`, `/checkout/[checkoutNumber]`, `/orders/[orderId]`, `/policies/[slug]`가 테스트 커버리지 없이 남아 있음을 확인했다.
- 문제·고민: `/checkout/[checkoutNumber]`(계좌입금 안내)와 `/orders/[orderId]`(클레임 접수/조회)는 실제 결제·CS와 직결되는 화면인데 자동 회귀 테스트가 없다. 데스크톱 스크린샷 baseline도 전혀 없다.
- 해결방안: B-013(수동 디자인 QA)과 역할을 분리해 B-047을 신설했다. 빈 페이지 커버 추가, 데스크톱 스크린샷 baseline, 빈 상태/오류 상태 렌더링 확인을 Tasks로 남겼다.
- 후속작업: B-047 착수 시 B-003에서 추가한 `LocalOrderSeedData`를 재사용해 checkout/orders 상세 테스트를 만든다.

## 2026-07-03 15:50 KST

- 관련 항목: B-003
- 작업: 관리자 주문 처리 액션 최종 검증을 마무리했다. local/dev 시드에 관리자 주문 6종과 시드 고객/관리자 계정을 추가하고, 관리자 주문 서버 액션 실패 시 백엔드 오류 메시지를 그대로 배너에 전달하도록 했다.
- 문제·고민: 기존 Playwright smoke는 주문이 없으면 관리자 주문 상세 검증을 스킵해서 실제 액션 검증이 비어 있었다. 또 프론트 서버 액션이 `ApiError.message`를 버리고 고정 실패 문구만 보여 상태 가드 실패와 권한 실패를 운영자가 구분하기 어려웠다.
- 해결방안: 카탈로그 시드 뒤에 실행되는 `LocalOrderSeedData`를 추가해 `PAYMENT_PENDING`, `SUPPLIER_ORDER_PENDING`, `SUPPLIER_ORDERED`, `SHIPPED`, `DELIVERED`, `OUT_OF_STOCK` 주문을 만들었다. Playwright는 로컬 API 기준에서 시드 주문이 없으면 실패하게 하고, 입금 불일치 메모 저장 후 상세 갱신과 배송완료 주문의 발주 시작 실패 메시지를 확인한다. 로컬 API를 시드 활성화 상태로 띄운 뒤 시드 관리자 JWT 쿠키로 관리자 액션 smoke 2건을 실제 실행했다.
- 결정: E2E 성공 상태 변경은 반복 실행 가능한 입금 불일치 메모 갱신으로 검증한다. 주문 상태를 파괴적으로 전환하는 발주/입금확인 성공 시나리오는 브라우저 수동 검증과 백엔드 통합 테스트로 보완한다.
- 후속작업: 배포 URL smoke에서는 실제 운영 데이터가 없을 수 있으므로 `E2E_REQUIRE_ADMIN_SEED_ORDERS=false` 또는 기본 non-local 동작으로 주문 없음 스킵을 유지한다.

## 2026-07-03 15:40 KST

- 관련 항목: B-011
- 작업: 거래 알림을 SMS 우선 발송 구조로 전환했다.
- 문제·고민: 기존 `NotificationLog`는 실제 외부 발송 없이 `EMAIL/SENT`를 기록해 운영자가 알림 발송 성공 여부를 믿을 수 없었다. 또한 주문 관련 알림은 계정 전화번호가 아니라 실제 배송 수령인 전화번호로 보내야 한다.
- 해결방안: SENS HTTP 호출과 서명 로직을 공용 SMS 클라이언트로 분리하고, 인증번호 SMS는 기존 동작을 유지하면서 거래 SMS 발송 메서드를 추가했다. 알림 로그는 `PENDING`으로 만들고, 커밋 이후 발송 리스너가 `SENT`/`FAILED`/`SKIPPED`로 갱신한다. 체크아웃 생성 시 입금대기 안내를 추가하고, 관리자 실패 알림 retry API와 수동 지연안내 액션을 붙였다.
- 결정: 이메일 SMTP와 카카오 알림톡은 이번 범위에서 붙이지 않고, 실제 도달률과 비용을 보고 후속 이슈에서 검토한다. 기본 로컬/테스트 환경은 `sms.sens.enabled=false`로 발송 없이 `SKIPPED`를 기록한다.
- 후속작업: 운영 SENS 자격증명을 주입한 뒤 실제 SMS 1건을 수동 검증하고, 필요하면 관리자 알림 목록 화면을 별도 UX 이슈로 만든다.

## 2026-07-03 14:20 KST

- 관련 항목: B-015
- 작업: 고객 클레임 목록/상세 조회와 사진 증빙 저장을 구현했다. `claim_evidences` 테이블을 추가하고, 고객 클레임 생성은 multipart 증빙을 받을 수 있게 했으며, 상품 하자·오배송·상품 정보와 다름·배송 문제 사유는 증빙 사진이 없으면 400으로 거부한다. 고객 주문 상세와 관리자 주문 상세에는 클레임 상태와 증빙 사진 목록을 표시한다.
- 문제·고민: 기존 구현은 주문 상세에 최신 클레임 요약 하나만 붙어 있어 고객이 처리 이력을 확인하기 어렵고, 정책상 필수인 판매자 귀책 증빙을 저장할 구조가 없었다. 다만 인지일 기준과 교환 발송 완료까지 한 번에 구현하면 범위가 커진다.
- 해결방안: 증빙은 별도 `ClaimEvidence` 엔티티로 두고 기존 업로드 검증기를 공통화해 상품 이미지와 같은 확장자/매직바이트 검증을 사용했다. 주문 상세 응답은 `claims` 배열을 추가하고 기존 `claim` 필드는 호환용으로 유지했다.
- 결정: B-015는 고객 조회, 증빙 저장, 관리자 확인까지 닫는다. 인지일 입력/30일 기준 강제, 관리자 증빙 추가 요청, 교환 배송 송장·완료 처리는 후속 이슈로 분리한다.
- 후속작업: 출시 전 QA에서 증빙 이미지 업로드/노출을 브라우저로 재확인하고, 교환 배송 처리 정책을 별도 백로그로 잡는다.

## 2026-07-03 13:31 KST

- 관련 항목: B-014
- 작업: 고객 회원 탈퇴 요청 흐름을 구현했다. `users.deleted_at`, `users.anonymized_at` 컬럼을 추가하고, `POST /api/me/deletion-request`에서 진행 중 주문/환불/클레임 가드 후 `status=DELETED`와 개인정보 비식별화를 처리한다. 계정 화면에는 탈퇴 안내와 확인 체크박스를 추가했다.
- 문제·고민: 탈퇴는 개인정보 삭제 요구와 전자상거래 법정 보존 기록이 충돌할 수 있다. 또한 현재 구현은 별도 `SocialAccount` 테이블 없이 `users.provider/provider_user_id` unique 제약으로 소셜 식별자를 관리한다.
- 해결방안: MVP에서는 별도 `LegalRetentionRecord` 색인 테이블을 만들지 않고, 주문·결제·배송·환불·클레임·약관 동의 기록은 비식별화된 유저 row 참조로 보존한다. 탈퇴 시 `provider_user_id`를 `deleted-{userId}`로 바꾸고 OAuth 로그인은 `ACTIVE` 유저만 재사용하도록 해 같은 소셜 계정 재가입이 새 계정으로 생성되게 했다.
- 결정: 진행 중 주문은 `DELIVERED`, `CANCELLED`, `REFUNDED`, `EXPIRED`가 아닌 주문으로 본다. 진행 중 환불은 `COMPLETED`, `REJECTED`가 아닌 환불, 진행 중 클레임은 `COMPLETED`, `REJECTED`, `WITHDRAWN`이 아닌 클레임으로 보고 탈퇴를 막는다.
- 후속작업: 법정 보존 기간 만료 후 자동 완전 삭제, `LegalRetentionRecord` 색인, 관리자 탈퇴 회원 조회는 후속 운영 이슈로 분리한다.

## 2026-07-03 11:34 KST

- 관련 항목: B-046
- 작업: 상품 상세 HTML과 업로드 이미지 방어선을 강화했다. `CatalogService`의 정규식 blacklist sanitizer를 jsoup safelist로 교체하고, 이미지 업로드에 확장자별 매직 바이트 검증을 추가했으며 `/uploads/products/**` 응답에 `X-Content-Type-Options: nosniff`를 적용했다.
- 문제·고민: HTML 상세는 고객 화면에서 `dangerouslySetInnerHTML`로 렌더링되므로 저장 시점 sanitize가 실패하면 저장형 XSS가 된다. `ImageIO` 디코딩은 PNG/JPEG에는 단순하지만 기본 JDK에서 WebP 지원이 불확실하다.
- 해결방안: jsoup `Safelist`로 허용 태그/속성/protocol만 통과시키고 이벤트 속성, `javascript:`/`data:` URL, `script`, `iframe`, `svg`를 제거했다. 이미지는 새 의존성 없이 `jpg/jpeg`, `png`, `webp` 파일 시그니처를 직접 확인한다.
- 결정: 이미지 크기 제한은 현재 구현 기준인 10MB로 문서화하고, 바이러스 스캔/리사이징/CDN 전환은 이번 범위에서 제외한다. B-042~B-046 외부 리뷰 후속 보안/운영 이슈 큐는 이번 작업으로 모두 마무리한다.
- 후속작업: 배포 URL 기준 Playwright smoke와 실주문 전 운영 readiness 점검(B-016)을 이어서 닫는다.

## 2026-07-03 11:17 KST

- 관련 항목: B-045
- 작업: 단일 EC2 운영 기준의 DB/업로드 이미지 백업과 복구 리허설 절차를 실제 AWS 리소스로 구성했다. `coreable-backups-prod` S3 버킷, `coreable-backup-writer` 최소권한 IAM user, EC2 `/opt/coreable/backup.sh`, 매일 03:10 KST cron, DLM weekly snapshot retain 4 정책을 준비했다.
- 문제·고민: 초저비용 구성을 유지하려면 RDS/S3 이미지 서빙으로 바로 올리지 않고도 EC2 local Postgres와 upload volume 장애 리스크를 줄여야 한다. 또 root/admin AWS credential을 EC2에 남기면 백업 자동화보다 보안 리스크가 더 커진다.
- 해결방안: 백업 전용 IAM user는 `s3://coreable-backups-prod/db/*`, `s3://coreable-backups-prod/uploads/*` read/write와 제한된 list만 허용했다. EC2에는 root cron용 최소권한 credential만 두고 `/opt/coreable`과 `ubuntu` home에는 AWS credential을 남기지 않았다. 최신 dump는 임시 PostgreSQL 컨테이너에 `pg_restore --no-owner`로 복구 리허설했다.
- 결정: 현재 운영 baseline은 DB dump 30일 S3 보관, local dump 3개 보관, uploads S3 sync, EC2 root volume `DeleteOnTermination=false`, 주 1회 root volume snapshot retain 4로 둔다. uploads sync는 삭제 전파 없이 보수적으로 보관한다.
- 후속작업: 실주문 전 DB migration dry run, 배포 URL Playwright smoke, sanitizer/업로드 검증 강화(B-046)를 이어서 닫는다.

## 2026-07-03 10:31 KST

- 관련 항목: B-044
- 작업: 배송완료 후 반품 클레임이 `RETURN_WAITING`에서 멈추지 않도록 관리자 반품 수령, 반품 환불 시작, 수동 계좌환불 완료 시 클레임 완료까지 연결했다.
- 문제·고민: `CustomerOrder.markRefundRequested()`는 기존에 `DELIVERED`를 거부했지만, 무조건 열면 배송완료 주문이 클레임 검수 없이 환불 요청으로 들어갈 수 있다.
- 해결방안: 주문 도메인은 `DELIVERED -> REFUND_REQUESTED` 전이 자체만 허용하고, 서비스에서 `RETURN_RECEIVED` 상태의 RETURN 클레임이 있을 때만 반품 환불을 시작하도록 제한했다. 클레임과 환불은 `claims.refund_id`로 연결했다.
- 결정: 계좌입금 MVP에서는 반품 배송비 차감 자동 계산을 하지 않는다. 필요한 차감/고객 안내는 관리자가 수동 메모와 운영 절차로 처리하고, 자동 차감은 후속 정책/정산 이슈로 분리한다.
- 후속작업: 교환 배송 흐름, 반송 송장/증빙 업로드, 반품 배송비 자동 차감은 별도 이슈로 다룬다.

## 2026-07-03 10:01 KST

- 관련 항목: B-043, B-041
- 작업: 체크아웃 중복 제출과 주문/결제 상태 경합 방지를 구현했다. 체크아웃 생성은 고객 cart row를 비관적 잠금으로 잡은 뒤 cart item을 읽고, 첫 요청이 cart를 비운 뒤 들어오는 중복 요청은 추가 주문을 만들지 않고 명확한 오류로 반환한다. `orders`와 `payment_groups`에는 `version` 컬럼과 JPA `@Version`을 추가했다.
- 문제·고민: 계좌입금 흐름에서는 중복 입금대기 주문이 생기면 고객 입금과 관리자 입금확인이 꼬인다. 또한 고객 취소와 관리자 입금확인/발주 시작이 동시에 들어오면 상태가 덮어써지는 lost update 위험이 있다.
- 해결방안: 고객당 활성 checkout 1개 같은 강한 정책은 도입하지 않고, cart row 잠금으로 같은 cart 기반 중복 submit만 막았다. 주문/결제그룹 stale update는 자동 재시도하지 않고 `409 CONFLICT`로 알려 새로고침 후 다시 판단하게 했다.
- 검증: `cd apps/api && ./gradlew test --tests '*Checkout*' --tests '*OrderOptimisticLocking*' --tests '*ApiExceptionHandlerTest*' --tests '*PostgresMigrationSmoke*'`, `cd apps/api && ./gradlew test`, `cd apps/web && npm run lint`, `cd apps/web && npm run build`.
- 후속작업: 실제 배포 smoke에서 checkout 더블클릭 UI 메시지와 관리자 주문 액션 충돌 안내가 운영자가 이해할 수 있는지 확인한다.

## 2026-07-03 09:42 KST

- 관련 항목: B-042, B-039, B-016
- 작업: 즉시 보안/운영 핫픽스를 적용했다. OAuth `redirectTo`는 백슬래시와 인코딩된 `%5C`, CRLF를 차단하고, Toss 환불 retry는 저장된 동일 idempotency key를 재사용하게 했다. 운영 SMS 기본값은 `SMS_SENS_ENABLED=false`로 바꾸고, deploy workflow에는 production concurrency와 readiness 성공 후 Docker image prune을 추가했다.
- 문제·고민: EC2 `t4g.micro` 단일 서버는 디스크와 메모리 여유가 작아, SHA-tag image가 쌓이거나 JVM heap이 무제한으로 커지면 배포 후 안정성이 떨어질 수 있다.
- 해결방안: compose에 API/PostgreSQL/Web/nginx memory limit을 두고 API JVM에 `-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError`를 지정했다. 배포 스크립트는 readiness 성공 후 168시간보다 오래된 Docker image를 prune한다.
- 검증: `cd apps/api && ./gradlew test --tests '*OAuthLogin*' --tests '*Refund*'`, `cd apps/api && ./gradlew test`, `cd apps/web && npm run lint`, `cd apps/web && npm run build`, workflow/compose YAML parse, `COREABLE_ENV_FILE=env.example docker compose --env-file infra/aws/ec2/env.example -f infra/aws/ec2/compose.prod.yml config`, `git diff --check`.
- 후속작업: 다음 실제 배포에서 image prune, memory limit, `SMS_SENS_ENABLED=false`가 운영 env와 충돌하지 않는지 확인한다.

## 2026-07-03 09:24 KST

- 관련 항목: B-041, B-016, B-043, B-044
- 작업: 고객 결제 주 경로를 계좌입금으로 전환했다. 체크아웃 응답/화면에 입금 계좌, 금액, 입금자명, 입금 기한, 현금영수증 안내를 추가하고, 관리자 주문 화면에 입금대기 필터와 입금확인/미입금취소/입금 불일치 메모 액션을 연결했다. 입금확인 시 `BANK_TRANSFER` payment를 생성하고 `PAYMENT_PENDING` 주문을 `SUPPLIER_ORDER_PENDING`으로 전환하며, 계좌입금 환불은 관리자 수동 환불 완료 액션으로 기록한다.
- 문제·고민: 계좌입금은 PG 자동 승인/취소가 없어 운영자 확인과 이력 품질이 중요하다. 그래서 입금확인, 미입금취소, 수동환불 완료는 관리자 주체/시각/사유와 `OrderStatusHistory`를 남기게 했다.
- 해결방안: `PaymentProvider.BANK_TRANSFER`, `PaymentMethod.BANK_TRANSFER`, `BANK-{checkoutNumber}` provider key를 사용해 기존 payment unique invariant를 유지했다. Toss 코드는 삭제하지 않고 deferred PG 경로로 남겼다.
- 검증: `cd apps/api && ./gradlew test --tests '*Checkout*' --tests '*AdminOrder*' --tests '*Refund*'`, `cd apps/api && ./gradlew test`, `cd apps/web && npm run lint`, `cd apps/web && npm run build`.
- 후속작업: 실주문 전 실제 입금 계좌 env, 구매안전서비스 방식, 현금영수증 운영 절차, 중복 checkout/동시 상태 전이 방지(B-043), 배송 후 반품 환불 플로우(B-044)를 닫는다.

## 2026-07-03 08:44 KST

- 관련 항목: B-041, B-043, B-030
- 작업: 계좌입금 전환 백로그 리뷰에서 확인된 누락 4건을 반영했다. B-041에 구매안전서비스(에스크로/소비자피해보상보험) 확보 방안 결정, 현금영수증 발급 준비, 입금확인/미입금취소/수동환불의 `OrderStatusHistory` 기록 태스크를 추가했다. B-043은 체크아웃 중복 제출에 더해 `@Version` 낙관적 잠금 도입까지 포함하도록 확장했다. B-030에 구매안전서비스 이용확인증과 현금영수증 출시 차단 항목을 추가했다.
- 문제·고민: 계좌입금은 현금성 결제라 카드 결제에는 없던 법적 의무(구매안전서비스, 현금영수증)가 새로 생기고, 입금확인이 관리자 수동 액션이 되면서 상태 이력과 동시 액션 경합의 중요도가 올라간다.
- 후속작업: B-041 착수 시 구매안전서비스 확보 방안 결정을 다른 구현보다 먼저 처리한다.

## 2026-07-03 00:18 KST

- 관련 항목: B-001, B-041, B-042, B-043, B-044, B-045, B-046
- 작업: 결제 MVP 방향을 Toss Payments 우선 연동에서 고객 직접 계좌입금과 관리자 입금확인 흐름으로 전환하고, 기존 이슈 번호를 재사용하지 않도록 백로그를 정리했다.
- 문제·고민: Toss live 심사와 PG 연동을 지금 붙이면 출시 준비 범위가 커지고, 사용자는 우선 계좌입금 기반으로 운영해도 된다고 판단했다. 다만 계좌입금 전환 후에도 중복 주문, 수동 입금확인, 수동 환불, 반품 처리, 백업/복구, 입력 검증 리스크는 남는다.
- 해결방안: B-001은 기존 Toss Payments sandbox 이슈로 보존하고 Deferred로 내렸다. 계좌입금 주문/입금확인 전환은 새 이슈 B-041로 추가했다. 리뷰 후속은 B-042 즉시 보안/운영 핫픽스, B-043 체크아웃 중복 방지, B-044 배송 후 반품/환불 플로우, B-045 백업/복구 최소 운영, B-046 sanitizer/업로드 검증 강화로 분리했다.
- 후속작업: B-041에서 정책/도메인/API/화면을 계좌입금 기준으로 먼저 정리한 뒤, B-042의 작은 보안/운영 핫픽스를 빠르게 닫는다.

## 2026-07-02 17:29 KST

- 관련 항목: B-039
- 작업: Cloudflare proxied DNS 기준으로 EC2 origin HTTPS를 nginx + Cloudflare Origin Certificate로 전환하고 SSL/TLS 모드를 `Full (strict)`로 변경했다.
- 문제·고민: 기존 Caddy 구성은 Cloudflare proxy 상태에서 ACME 인증서 발급이 막혀 `525`가 발생했고, 쇼핑몰 운영 기준에서는 Cloudflare와 origin 사이 인증서 검증이 필요했다.
- 해결방안: EC2 Docker Compose의 reverse proxy를 nginx로 바꾸고 `/api/**`, `/actuator/**`, `/uploads/products/**`는 API로, 나머지는 Web으로 라우팅했다. Cloudflare Origin Certificate와 private key는 서버 local path에만 배치하고 git에는 남기지 않았다.
- 검증: EC2 `docker compose ps`에서 API/PostgreSQL/Web/nginx가 정상 동작했고, origin 직접 HTTPS는 `curl -k --resolve coreable-saf.com:443:43.200.135.171 https://coreable-saf.com/api/health`로 성공했다. Cloudflare 경유 `https://coreable-saf.com`, `https://www.coreable-saf.com`, `/api/health`, `/actuator/health/readiness`, `/products`도 200 응답을 확인했다.
- 후속작업: 장기 운영 전 SSH 보안그룹을 SSM 또는 fixed egress runner로 좁히고, RDS/S3/backup 전환 필요성을 트래픽과 운영 부담 기준으로 재검토한다.

## 2026-07-02 16:23 KST

- 관련 항목: B-040, B-039
- 작업: GitHub Actions Docker image build에 BuildKit GitHub Actions cache를 추가했다.
- 문제·고민: AWS 테스트 배포에서 EC2 pull/up은 약 1분대지만, `build-and-push`가 약 7분 39초로 전체 배포 시간을 지배했다. 특히 Web ARM64 Docker image build가 반복 배포마다 오래 걸렸다.
- 해결방안: API와 Web Docker build에 각각 `type=gha` cache scope를 추가했다. 문서-only 변경은 이미 Deploy skip 처리되어 있으므로, 이번 cache는 실제 app/infra/workflow 변경 배포 시간을 줄이는 목적이다.
- 결정: Next.js build 중복 제거, self-hosted ARM runner, amd64 EC2 전환은 이번 범위에서 제외하고 cache 효과를 먼저 측정한다.
- 검증: 첫 cache 적용 배포는 성공했다. 실행 시간은 `verify 3m05s`, `build-and-push 8m16s`, `deploy 1m27s`였고 EC2 readiness는 `UP`이었다. 첫 실행은 cache export를 포함한 warm-up으로 본다.
- 후속작업: cache warm-up 이후 앱 변경 배포에서 `build-and-push` 시간을 비교하고, 여전히 길면 별도 후속 이슈로 Docker build 중복 제거 또는 runner 전략을 검토한다.

## 2026-07-02 14:12 KST

- 관련 항목: B-039, B-016
- 작업: GitHub Actions 기반 AWS EC2 Docker 테스트 배포를 실제로 실행했다.
- 문제·고민: 첫 deploy run은 API 컨테이너가 정상 기동했지만 host의 `localhost:8080`에 포트가 바인딩되지 않아 Actions readiness check가 실패했다. 또한 `coreable-saf.com`과 `www.coreable-saf.com` DNS A 레코드가 아직 없어 Caddy 인증서 발급은 실패 중이다.
- 해결방안: API 컨테이너를 EC2 loopback `127.0.0.1:8080`에만 바인딩해 host-local readiness check를 통과하게 했다. EC2 `43.200.135.171`에서 API/PostgreSQL/Web/Caddy 컨테이너 상태와 `/actuator/health/readiness`, `/api/health` 응답을 확인했다.
- 결정: B-039는 CI/CD와 EC2 배포 baseline은 완료로 보고, Cloudflare DNS 연결과 도메인 HTTPS/browser smoke는 남은 작업으로 유지한다.
- 후속작업: Cloudflare에서 `coreable-saf.com`, `www.coreable-saf.com` A 레코드를 `43.200.135.171`로 연결한 뒤 Caddy 인증서 발급, public health, 브라우저 smoke를 확인한다.

## 2026-07-02 11:35 KST

- 관련 항목: B-039, B-016
- 작업: AWS EC2 저비용 테스트 배포를 Docker 기반 CI/CD로 진행하기로 정리했다.
- 문제·고민: Oracle Always Free A1 인스턴스는 capacity 부족으로 생성이 막혔고, Lightsail/EC2/RDS/S3를 한 번에 도입하면 비용과 운영면이 커진다.
- 해결방안: `t4g.micro` 단일 EC2에 Caddy, Web, API, PostgreSQL, local uploads를 Docker Compose로 올리고, GitHub Actions가 GHCR 이미지를 빌드/푸시한 뒤 EC2에서 pull/up만 수행하게 한다.
- 결정: 서버에서 소스 빌드는 하지 않는다. S3/RDS/CloudFront는 테스트 URL 확보 뒤 이미지 용량, 백업, 트래픽 리스크가 현실화될 때 전환한다.
- 후속작업: AWS 리소스 생성, EC2 env 등록, GitHub Secrets 등록, Cloudflare DNS 연결, 배포 URL smoke 확인을 닫는다.

## 2026-07-02 10:12 KST

- 관련 항목: B-016
- 작업: 테스트 배포에서 상품 이미지를 Oracle VM local volume에 저장할 수 있도록 backend storage 경계를 추가했다.
- 문제·고민: 지금 바로 R2/S3 SDK를 붙이면 테스트 배포보다 저장소 연동 작업이 커진다. 반대로 `CatalogService`가 local path에 직접 쓰는 구조를 유지하면 운영 전환 때 코드 영향이 커진다.
- 해결방안: `FileStorage` 경계를 두고 현재 구현은 local filesystem만 제공한다. `APP_STORAGE_LOCAL_UPLOAD_DIR`, `APP_STORAGE_PUBLIC_BASE_URL`로 배포 경로와 공개 URL prefix를 분리한다.
- 결정: 테스트 배포는 Oracle VM persistent volume을 사용하고, 이미지 용량/백업/트래픽 부담이 커지면 R2/S3 호환 object storage 구현체를 추가한다.
- 후속작업: B-016 배포 아키텍처에서 이미지 volume mount와 backup 범위를 확정한다.

## 2026-07-02 09:40 KST

- 관련 항목: B-034, B-036
- 작업: 배포 전 회귀 확인을 위해 Playwright UI smoke와 Testcontainers PostgreSQL smoke를 도입한다.
- 문제·고민: 수동 스크린샷 QA와 H2 기반 통합 테스트만으로는 모바일 레이아웃 깨짐, 관리자 화면 회귀, Flyway/PostgreSQL drift를 배포 전마다 놓칠 수 있다.
- 해결방안: Playwright는 공개/고객/관리자 핵심 화면과 모바일 screenshot만 최소로 확인하고, Testcontainers는 기존 H2 테스트를 대체하지 않고 PostgreSQL migration/JPA validate/public API 계약만 smoke로 확인한다.
- 결정: k6, Lighthouse, ZAP은 테스트 배포 후 별도 이슈로 미룬다. OAuth 실계정 로그인과 Toss sandbox 성공 결제는 기존 B-002, B-001 수동 검증 범위로 유지한다.
- 후속작업: 배포 URL이 준비되면 같은 Playwright smoke를 `coreable-saf.com` 기준으로 실행한다.

## 2026-07-01 17:12 KST

- 관련 항목: B-033
- 작업: 상품 원가(`sourcePrice`)와 고객 판매가(`basePrice`)를 분리하고 기본 가격 정책을 추가했다.
- 문제·고민: 도매꾹 수집 가격을 그대로 판매가에 넣으면 마진, 세금/부가비 버퍼, 운영비를 관리자가 검수하기 어렵다.
- 해결방안: active 가격 정책 1개를 두고 기본 25% 증액, 100원 단위 올림 기준을 관리자 화면과 import 스크립트에서 재사용한다.
- 결정: 정산/세금 신고/공급처별 마진율은 만들지 않고, MVP에서는 상품별 공급가와 판매가 검수까지만 관리한다.
- 후속작업: 기존 등록 상품의 공급가와 판매가를 관리자 화면에서 검수한다.

## 2026-06-30 22:45 KST

- 관련 항목: B-032, B-026
- 작업: 도매꾹 수집 산출물을 기존 관리자 API로 DB에 적재하는 로컬 import 스크립트를 추가했다.
- 문제·고민: 직접 DB insert는 상품 생성, 이미지 업로드, 상세 블록 저장, 옵션 생성 검증을 우회해 운영 데이터가 깨질 수 있다. 반대로 바로 `ACTIVE`로 넣으면 인증/KC, 상품 고시, 가격 검수 전 고객에게 노출된다.
- 해결방안: manifest를 먼저 생성하고 운영자가 `import`, `categoryCode`, 가격, 요약을 확인한 뒤 `--apply`로 관리자 API를 호출하게 했다. 기본 상태는 `HIDDEN`이고, 실제 공개는 관리자 화면에서 수동 검수 후 전환한다.
- 결정: 카테고리는 자동 추정하지 않는다. 관리자 자동 공개, 마진 자동 계산, 상품 고시 자동 생성은 이번 범위에서 제외한다.
- 후속작업: 수집 후보 중 실제 등록할 상품만 manifest에서 선택하고, 관리자 쿠키로 1개를 먼저 import해 화면 확인 후 나머지를 진행한다.

## 2026-06-30 22:15 KST

- 관련 항목: B-031, B-026
- 작업: 도매꾹 상품 URL에서 상품 후보 정보, 대표 이미지, 상세 이미지를 로컬로 수집하는 스크립트를 추가했다.
- 문제·고민: 초기 상품을 빠르게 채워야 하지만 외부 상품 이미지와 상세 설명은 사용 허용 여부와 상품 고시 확인이 필요하므로 자동 등록하면 운영 리스크가 크다.
- 해결방안: 페이지의 `이미지사용` 값이 `허용`인 경우에만 이미지를 `tmp/domeggook-products/{상품번호}/`에 다운로드하고, JSON/CSV 결과를 수동 검수용으로 남기도록 했다.
- 결정: 관리자 자동 등록, CSV 일괄 업로드, 이미지 crop UI는 만들지 않는다. 최종 등록 전 상품명, 가격, 카테고리, 인증/KC, 상품고시, 이미지 품질은 사람이 확인한다.
- 후속작업: 초기 판매 상품 URL 목록을 모아 10~20개만 먼저 수집하고, 관리자 화면에서 수동 등록 후 고객 목록/상세/장바구니 노출을 확인한다.

## 2026-06-30 20:50 KST

- 관련 항목: B-030
- 작업: 출시 전 법적/소비자 고지 체크리스트를 만들고 회사 정보, 정책 페이지, 상품 등록 기준의 법적 고지 문구를 정리했다.
- 문제·고민: 통신판매업 신고번호, 고객센터 연락처, 호스팅 제공자, 결제/구매안전서비스 정보는 아직 실제 값이 없어 화면에서 임의로 확정할 수 없다.
- 해결방안: 준비중 값은 개발/오픈 전 상태에서만 허용하고, 실결제 오픈 전 교체해야 하는 항목은 `docs/legal-launch-checklist.md`와 B-030 checklist에 출시 차단 항목으로 남겼다.
- 결정: 이번 범위는 법률 자문 대체가 아니라 개발/운영 체크리스트와 고객-facing 개발 단계 표현 제거로 제한한다.
- 후속작업: 통신판매업 신고, 고객센터 전화/이메일, 호스팅 제공자, Toss live 구매안전/결제 안내, 상품별 안전인증/KC 확인을 실제 값으로 닫는다.

## 2026-06-30 20:09 KST

- 관련 항목: B-026
- 작업: 초기 판매 상품 등록을 위한 이미지 규격 안내와 상품 등록 기준 문서를 추가했다.
- 문제·고민: 운영자가 직접 상품을 올려야 하지만 브라우저 crop UI나 일괄 업로드를 지금 만들면 범위가 커진다.
- 해결방안: 대표 이미지는 1:1 1200x1200px, 상세 이미지는 16:9 1600x900px 또는 1920x1080px 권장으로 정하고 관리자 화면과 문서에 안내했다.
- 결정: 상품 데이터는 코드에 하드코딩하지 않고 관리자 화면에서 등록한다. CSV/엑셀 일괄 등록과 crop UI는 후속으로 미룬다.
- 후속작업: 초기 판매 상품 10~20개를 등록하고 고객 상품 목록, 상세, 장바구니에서 노출을 확인한다.

## 2026-06-30 19:50 KST

- 관련 항목: B-025, B-013
- 작업: 고객 문의 기능이 실제 DB에서 동작하도록 `customer_inquiries` 테이블 migration을 추가했다.
- 문제·고민: B-013 스크린샷 QA에서 관리자 문의 화면이 API 실패 상태로 보였고, 확인 결과 B-012에서 엔티티/API/UI는 추가됐지만 Flyway migration이 없어 로컬 DB에 테이블이 없었다.
- 해결방안: 기존 `CustomerInquiry` 엔티티와 맞춰 고객명, 이메일, 연락처, 제목, 내용, 생성시각 컬럼을 가진 테이블을 만들고 최신순 조회용 `created_at DESC` index를 추가했다.
- 결정: 문의 답변 상태, 관리자 메모, 알림 발송은 이번 범위에서 제외하고 기존 접수/목록 기능만 정상화한다.
- 후속작업: 고객센터 답변 처리는 B-011 또는 후속 고객센터 고도화에서 다룬다.

## 2026-06-30 19:16 KST

- 관련 항목: B-007
- 작업: 관리자 주문 상세에 배송조회 상태와 수동 배송조회/배송완료 보정 액션을 연결했다.
- 문제·고민: 송장 입력, 배송조회 sync, 배송완료 전환 backend는 이미 구현되어 있었지만 관리자 화면은 송장 입력 이후의 조회 실패 사유와 보정 기능을 보여주지 못했다.
- 해결방안: 주문 상세의 배송 영역에 택배사, 송장번호, 배송 상태, 출고/배송완료/조회 시각, 실패 사유, 수동 보정 사유를 표시하고 기존 `/api/admin/shipments/{shipmentId}/tracking-sync`, `/manual-correction` API를 form으로 연결했다.
- 결정: 실제 택배사 API 연동과 scheduler 구현은 이번 범위에서 제외하고, 기존 internal sync API를 외부 scheduler 또는 배포 플랫폼 cron이 호출하는 구조로 유지한다.
- 후속작업: 배포 환경에서 `APP_INTERNAL_SYNC_TOKEN`과 scheduler 호출 주기를 확정하고, 실제 택배사 조회 provider 선택은 별도 운영 준비 작업으로 진행한다.

## 2026-06-30 18:53 KST

- 관련 항목: B-012
- 작업: 실제 운영 쇼핑몰 기준으로 푸터, 정책 페이지, 회사 정보, 고객 문의 접수 흐름을 연결했다.
- 문제·고민: 기존 정책/사업자 공개 API는 일부 구현되어 있었지만 프론트 공개 경로가 부족했고, 고객 문의는 실제 접수 기록 없이는 운영 흐름이 성립하지 않았다.
- 해결방안: 푸터에 가라사니 사업자 정보를 노출하고 이용약관, 개인정보처리방침, 배송 정책, 취소/환불 정책, 품절 안내 페이지를 프론트 상수 기반 MVP 초안으로 제공했다. 고객 문의는 public form으로 접수해 `customer_inquiries`에 저장하고, 관리자 문의 목록에서 확인하도록 했다.
- 결정: 집 주소 공개는 허용한다. 고객센터 전화/이메일, 호스팅 제공자, 통신판매업 신고번호, 결제/구매안전서비스 정보는 준비중으로 표시하고 실결제 오픈 전 확정한다.
- 후속작업: Toss live 심사 전 통신판매업 신고번호, 호스팅 제공자, 고객센터 연락처, 구매안전서비스 안내 문구를 실제 값으로 교체한다.

## 2026-06-30 17:15 KST

- 관련 항목: B-005
- 작업: 관리자 상품 상세 화면에 상품 상세 IMAGE/HTML 블록과 상품 고시 편집 기능을 연결했다.
- 문제·고민: 백엔드 상세 블록/고시 API와 고객 상세 렌더링은 이미 구현되어 있었으므로 새 API나 editor dependency를 추가하면 범위가 커진다.
- 해결방안: 기존 `/detail-blocks`, `/notice`, `/images/upload` API를 재사용하고, 관리자 화면에서는 native form으로 기존 블록 포함/정렬/내용 수정, 새 이미지/HTML 블록 추가, 상품 고시 저장을 처리했다.
- 결정: 상세 블록 저장은 백엔드 계약대로 전체 교체 방식으로 유지한다. 정책/배송/환불/품절 고지는 상세 HTML/이미지에 묻지 않고 상품 고시와 정책 영역에 둔다.
- 후속작업: 출시 전 QA에서 실제 이미지 업로드, HTML sanitize 결과, 고객 상세 표시 순서, 변경 이력 기록을 브라우저로 확인한다.

## 2026-06-30 16:30 KST

- 관련 항목: B-024
- 작업: B-023 API 계약 리뷰 후속 정리를 진행했다.
- 문제·고민: 고객 주문 API는 `CUSTOMER` 전용인데 관리자 계정 접근이 빈 주문 또는 API 장애처럼 보였고, 일부 문서/프론트 타입은 실제 구현과 어긋나 있었다.
- 해결방안: `/orders`, `/orders/{orderId}`에 관리자 계정 안내를 추가하고, 약관 동의 응답 타입 generic 제거, 옵션 생성 사유 입력 제거, `docs/api-spec.md`의 고객 취소/클레임 권한과 이미지 업로드 note를 정리했다.
- 결정: 옵션 생성 변경 이력은 MVP에서 만들지 않고, 사유 입력도 노출하지 않는다. 옵션 수정/상태 변경 사유와 이력은 유지한다.
- 후속작업: 출시 전 QA에서 CUSTOMER 계정의 빈 주문 목록과 ADMIN 계정의 고객 주문 접근 안내를 각각 확인한다.

## 2026-06-30 16:15 KST

- 관련 항목: B-023
- 작업: 프론트엔드 API 호출과 백엔드 controller/DTO, `docs/api-spec.md`의 API 계약을 대조했다.
- 문제·고민: 실제 path/method 불일치 같은 P0는 없었지만, 관리자 계정으로 고객 주문 화면에 접근할 때 403 처리가 빈 주문 목록처럼 보이는 문제와 API 문서 drift가 있었다.
- 해결방안: 검토 결과를 `docs/API_CONTRACT_REVIEW.md`에 P1/P2로 정리하고, 코드 수정이 필요한 항목은 B-024 후속 백로그로 분리했다.
- 결정: B-023은 검토/문서화로 완료하고, 실제 수정은 별도 작업에서 작은 범위로 처리한다.
- 후속작업: B-024에서 `/orders` 권한 안내, API spec 정리, 약관 동의 응답 타입, 옵션 생성 이력 정책을 정리한다.

## 2026-06-30 16:01 KST

- 관련 항목: B-006
- 작업: 관리자 상품 목록에서 상품별 상세 관리 화면으로 진입해 판매 상태와 옵션 상태/가격을 운영자가 수정할 수 있게 연결했다.
- 문제·고민: 백엔드 API는 이미 있었으므로 새 API나 DB를 추가하면 범위가 커진다. 공급처명은 상세 응답에 없어 관리자 목록 응답과 함께 조회해 화면 표시만 보강했다.
- 해결방안: `/admin/products/{productId}` 화면을 추가하고 기존 admin catalog API로 상품 상태 변경, 옵션 추가, 옵션 기본 정보 변경, 옵션 상태 변경, 변경 이력 조회를 연결했다. 모든 변경 form은 사유 입력을 받도록 했다.
- 결정: B-006은 운영자가 상태와 옵션을 관리할 수 있는 최소 화면까지 완료로 보고, 상품 기본 정보 전체 수정, 이미지 교체, 상세 HTML/이미지 블록 관리는 후속 B-005/B-004 범위로 둔다.
- 후속작업: 출시 전 수동 QA에서 실제 상태 변경 후 고객 목록/상세/장바구니 구매 가능 상태가 정책대로 반영되는지 최종 확인한다.

## 2026-06-30 14:55 KST

- 관련 항목: B-022
- 작업: 고객 헤더의 `바로 구매` CTA를 제거하고 OAuth 성공 중간 화면을 `/account` redirect로 단순화했다.
- 문제·고민: 헤더의 `바로 구매`는 `/products` 링크와 역할이 겹쳤고, 로그인 완료 안내 페이지는 사용자가 추가로 선택해야 하는 중간 화면을 만들었다.
- 해결방안: 헤더에서는 장바구니, 주문조회, 계정, 로그인/로그아웃만 유지했다. `/auth/callback/success` 라우트는 OAuth 설정 호환을 위해 남기되 즉시 `/account`로 redirect한다.
- 결정: 로그인 후에는 별도 완료 안내보다 계정/필수 정보 확인 화면으로 바로 이동한다.
- 후속작업: OAuth provider 설정을 바꿀 수 있는 시점에는 `APP_AUTH_SUCCESS_REDIRECT_URI`를 `/account`로 직접 조정할지 검토한다.

## 2026-06-30 14:42 KST

- 관련 항목: B-018, B-022
- 작업: 상품 카테고리 체계를 최신 운영 분류에 맞춰 조정하고 Pretendard CDN을 적용했다.
- 문제·고민: 기존 카테고리에는 현재 판매 범위에서 빠진 `안전교육` 대분류가 남아 있었고, `스마트 안전장비 > 작업자 안전관리`에는 새 분류인 `위치·출입 관리`가 없었다. 또한 시스템 폰트 fallback만으로는 Pretendard가 없는 환경에서 동일한 글꼴을 보장하지 못했다.
- 해결방안: 프론트 카테고리 상수와 백엔드 `ProductCategory` enum에서 `안전교육` 코드를 제거하고 `WORKER_LOCATION_ACCESS_MANAGEMENT`를 추가했다. 전역 CSS에는 Pretendard jsDelivr CDN import를 추가했다.
- 결정: 카테고리는 MVP 고정 enum 방식 유지, 폰트는 우선 CDN으로 제공한다.
- 후속작업: 운영 배포 전 CDN 의존을 줄여야 하면 Pretendard woff2 self-host로 전환한다.

## 2026-06-30 14:18 KST

- 관련 항목: B-022
- 작업: 상품목록 모바일 필터와 상품 카드 밀도를 정리했다.
- 문제·고민: 모바일 `/products`에서 카테고리 사이드바가 상품보다 먼저 길게 노출되어 첫 상품이 약 817px 아래에서 시작했다. 쿠팡 모바일 검색 결과처럼 구매 판단 화면은 검색/필터 다음 바로 상품이 보여야 한다.
- 해결방안: 데스크톱 좌측 필터는 유지하고, 모바일에서는 필터를 기본 접힘 `<details>`로 전환했다. 대분류는 드롭다운 대신 링크형 선택 UI로 바꾸고, 상품 카드는 이미지와 핵심 정보를 2열 리스트형으로 압축했다.
- 결정: 실제 데이터가 없는 리뷰, 별점, 판매량은 넣지 않고 확정 정책인 `배송비 포함` 배지만 노출한다.
- 후속작업: 실제 상품 옵션/재고/리뷰 데이터가 생기면 목록 카드 정보 위계를 다시 조정한다.

## 2026-06-30 13:54 KST

- 관련 항목: B-021
- 작업: 메인페이지에 현장별 구매 묶음을 추가했다.
- 문제·고민: 쇼핑형 홈으로 압축한 뒤에도 실제 운영 쇼핑몰처럼 보이려면 상품 탐색 동기와 구매 전 신뢰 정보가 더 필요했다. 다만 고객센터 운영 시간, 사업자 정보처럼 아직 확정되지 않은 정보는 노출하면 안 된다.
- 해결방안: 정적 링크 카드로 `기본 보호구 준비`, `추락 작업 준비`, `안전 통제 구역 설치` 묶음을 추가했다. 홈 하단 신뢰 정보는 화면 밀도 대비 효용이 낮아 후속 UX 정리에서 제거했다.
- 결정: 홈 보강은 정적 콘텐츠와 기존 `/products?category=...` 링크만 사용하고, 정책/사업자/고객센터 상세 정보는 B-012 및 출시 전 법적 고지 작업에서 처리한다.
- 후속작업: 실제 고객센터와 사업자 정보가 확정되면 footer와 정책 페이지를 정식 연결한다.

## 2026-06-30 13:28 KST

- 관련 항목: B-020
- 작업: 메인페이지를 쇼핑 전환형 홈으로 압축했다.
- 문제·고민: 기존 홈은 히어로와 중복 상품 타일이 커서 첫 상품이 모바일에서 약 1634px 아래에 나왔고, 고객이 바로 상품을 탐색하기 어려웠다.
- 해결방안: 히어로를 작은 쇼핑 배너로 줄이고, 대분류 select와 주요 소분류 링크를 별도 진입 영역으로 정리했다. 모바일에서는 utility/category nav를 숨겨 첫 상품 시작 위치를 767px까지 앞당겼다.
- 결정: 메인에서는 브랜드 소개보다 상품 탐색과 가격 스캔을 우선한다.
- 후속작업: 실제 상품 사진이 준비되면 카드 이미지 asset을 교체한다.

## 2026-06-30 13:11 KST

- 관련 항목: B-019
- 작업: 브라우저 UX 리뷰에서 확인한 구매 흐름 오류 표시, 긴 카테고리 필터, 계정 UUID 노출을 핵심 범위로 정리했다.
- 문제·고민: 관리자 계정으로 고객 장바구니와 주문서에 접근하면 백엔드 API 장애처럼 보여 실제 장애와 권한 제한을 구분하기 어려웠다. 확장된 카테고리 목록은 상품 카드보다 먼저 너무 길게 노출됐다.
- 해결방안: 관리자 권한 오류는 별도 안내와 `상품 보기`/`관리자 홈` CTA로 분리하고, 상품 목록 필터는 상위 그룹과 선택 그룹의 하위 카테고리만 보여주도록 줄였다. 계정 화면과 관리자 topbar에서는 내부 user UUID를 숨기고 휴대폰 인증 안내 문구를 보강했다.
- 결정: 이번 B-019는 P0와 핵심 P1만 처리하고, 관리자 모바일 레이아웃 전면 개선과 이미지 asset polish는 B-013 디자인 QA 후속 작업으로 남긴다.
- 후속작업: 브라우저에서 desktop/mobile 주요 화면을 재확인한 뒤 B-019를 완료 보관으로 옮긴다.

## 2026-06-30 12:45 KST

- 관련 항목: B-018
- 작업: 상품 카테고리 체계를 실제 상품 분류 필드로 추가했다.
- 문제·고민: 카테고리 목록은 커졌지만, 운영자가 카테고리를 직접 추가·수정하는 관리자 기능까지 만들면 상품 완성 흐름보다 범위가 커진다.
- 해결방안: 상품은 하나의 고정 `categoryCode`만 저장하고, 백엔드는 enum으로 허용값을 제한했다. 프론트는 같은 코드 목록을 상수로 두고 관리자 등록 select, 고객 목록 필터, 홈/헤더 대표 카테고리 링크에 재사용했다.
- 결정: MVP에서는 카테고리 DB 테이블, 다중 카테고리, 태그 검색, 카테고리 관리자 화면을 만들지 않는다.
- 후속작업: 상품 수정 화면을 만들 때 categoryCode 변경과 변경 이력 확인을 함께 연결한다.

## 2026-06-29 23:24 KST

- 관련 항목: B-004
- 작업: 관리자 상품 등록 화면에서 대표 이미지 파일 업로드를 기존 admin product image upload API와 연결했다.
- 문제·고민: 백엔드 업로드 API와 이미지 metadata 저장 API는 이미 있으므로, 새 이미지 저장소나 프론트 미리보기 상태를 추가하면 범위가 커진다.
- 해결방안: 상품 생성 후 파일이 있을 때만 multipart upload를 실행하고, 반환된 imageUrl을 기존 이미지 metadata API에 THUMBNAIL로 저장했다. 상품 목록은 기존 ProductImage 컴포넌트를 재사용해 대표 이미지 미리보기를 표시한다.
- 결정: B-004는 상품 등록 시 대표 이미지 업로드까지 완료로 보고, 갤러리 다중 이미지·수정 화면·정렬 UI는 상세 블록/상품 관리 후속 작업에서 다룬다.
- 후속작업: 실제 브라우저에서 jpg/png/webp 업로드와 5MB 초과/미지원 확장자 실패 메시지를 확인한다.

## 2026-06-29 22:16 KST

- 관련 항목: B-017
- 작업: 로그인 후 고객 필수 정보와 SMS OTP 휴대폰 번호 인증 1차 구현을 진행했다.
- 문제·고민: 사업자회원/사업자 프로필은 필요 없지만, 실제 운영에서는 주문·배송·클레임 연락 가능한 고객 정보가 필요하다. NICE/PASS 본인확인은 비용과 개인정보 부담이 커서 현재 쇼핑몰 목적에는 과하다.
- 해결방안: 고객 회원 유형은 하나로 유지하고, 필수 정보는 이름, 연락 가능한 이메일, 인증된 휴대폰 번호로 제한했다. 휴대폰 번호는 SMS OTP로 소유 확인하고, 인증번호는 hash 저장, 5분 만료, 재발송 제한, 시도 제한을 적용했다.
- 결정: MVP는 SMS OTP 번호 인증으로 진행하고, CI/DI 기반 본인확인은 성인인증, 중복가입 방지, 실명확인이 필요해질 때 검토한다.
- 후속작업: 실제 운영 SMS provider 설정과 구현을 연결하고, 개인정보 수집/보관 고지와 운영 env 목록을 정리한다.

## 2026-06-29 16:24 KST

- 관련 항목: B-001
- 작업: Toss Payments 연동 진행 기준을 테스트 키 우선 개발로 정리했다.
- 문제·고민: live PG 심사에는 홈페이지 주소와 사업자/정책 정보가 필요하지만, 현재는 배포 전이라 실운영 연동을 완료할 수 없다.
- 해결방안: 로컬/스테이징은 Toss Payments test client key와 test secret key로 결제창과 서버 confirm을 검증하고, live 전환은 배포 URL 확보 이후로 미룬다.
- 결정: 현재 개발은 테스트 키 기준으로 진행하며 test/live key 모두 커밋하지 않는다.
- 후속작업: 테스트 키를 로컬 env에 넣은 뒤 sandbox 결제창, 성공 redirect, 서버 승인, 실패/예외 화면을 실제 브라우저에서 확인한다.

## 2026-06-29 16:28 KST

- 작업: 서비스명을 `코어블SAF`로 확정하고 고객-facing 브랜드 표기를 갱신했다.
- 문제·고민: 기존 화면에는 임시명 `SafeHub Pro`가 남아 있었고, 저장소명까지 바꾸면 불필요한 변경 범위가 커진다.
- 해결방안: 웹 레이아웃과 제품/결정 문서의 서비스명만 교체하고 기술 식별자는 유지했다.
- 결정: 고객에게 노출되는 서비스명은 `코어블SAF`로 한다.
- 후속작업: 이후 디자인/배포/PG 심사 문서에는 `코어블SAF` 명칭을 사용한다.

## 2026-06-29 17:46 KST

- 관련 항목: B-001
- 작업: Toss Payments 연동 상태와 작업 관리 기준을 다시 정리했다.
- 문제·고민: Toss Payments live 승인이 아직 나지 않아 live key 또는 실제 운영 결제로는 검증할 수 없다.
- 해결방안: 승인 전까지는 Toss Payments test/sandbox key만 사용해 결제창, success redirect, backend confirm, 실패/예외 화면을 검증한다.
- 결정: live 승인이 완료되기 전에는 테스트 키 기준으로만 개발하고, live key 전환은 별도 배포/PG 승인 작업으로 미룬다.
- 후속작업: Toss test client key와 test secret key를 로컬 env에 넣은 뒤 `docs/BACKLOG.md`의 Toss sandbox 결제 플로우 항목을 이어서 검증한다.

## 2026-06-29 17:54 KST

- 관련 항목: WORKFLOW
- 작업: markdown 기반 작업 관리 문서의 연결 방식을 정했다.
- 문제·고민: `BACKLOG`, `TODO`, `PROJECT_LOG`가 따로 관리되면 큰 작업, 하위 체크리스트, 결정 기록이 분리되어 추적이 어려워진다.
- 해결방안: backlog 작업에 `B-001` 같은 ID를 붙이고, TODO 섹션과 PROJECT_LOG의 `관련 항목`이 같은 ID를 참조하도록 한다.
- 결정: `BACKLOG = 큰 작업`, `TODO = 하위 task/checklist`, `PROJECT_LOG = 결정 이유와 작업 맥락`으로 사용한다.
- 후속작업: 새 goal을 시작할 때 관련 backlog ID를 먼저 확인하고, 필요한 TODO와 PROJECT_LOG를 같은 ID로 갱신한다.

## 2026-06-29 17:57 KST

- 관련 항목: WORKFLOW
- 작업: 현재 남은 제품/운영 작업을 backlog story와 TODO checklist로 재정리했다.
- 문제·고민: backlog에는 큰 항목만 있고 TODO에는 Toss 결제 항목만 있어, 다음 작업을 고를 때 세부 실행 단위가 부족했다.
- 해결방안: 출시 전 필요한 인증, 결제, 관리자 주문 처리, 상품 관리, 배송, 법적 고지, 모바일 QA, 배포 readiness 항목을 `B-###` 기준으로 나눴다.
- 결정: 당장 개발할 큰 작업은 `docs/BACKLOG.md`, 각 작업의 하위 실행 항목은 `docs/TODO.md`에서 관리한다.
- 후속작업: 다음 goal을 시작할 때 `docs/BACKLOG.md`의 `Now` 항목 중 하나를 선택하고, 해당 `B-###`의 TODO를 완료 기준으로 사용한다.

## 2026-06-29 18:01 KST

- 관련 항목: WORKFLOW
- 작업: `BACKLOG`와 `TODO`를 한 파일로 합쳤다.
- 문제·고민: 혼자 개발하는 상황에서 backlog와 TODO를 분리하면 story와 하위 task를 계속 왕복해야 하고, goal 시작 시 읽을 문서가 늘어난다.
- 해결방안: `docs/TODO.md`를 제거하고 `docs/BACKLOG.md`의 각 `B-###` 항목 아래 `Tasks:` checklist를 둔다.
- 결정: 작업 관리는 `docs/BACKLOG.md`, 결정 이유와 맥락 기록은 `docs/PROJECT_LOG.md`로 단순화한다.
- 후속작업: 새 goal은 관련 `B-###`의 `Tasks:`를 완료 기준으로 사용한다.
