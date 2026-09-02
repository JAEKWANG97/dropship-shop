# Test Log

실행한 검증만 기록한다. 실제 외부 서비스 검증은 자동 테스트 결과와 합치지 않는다.

## 2026-09-03 B-106 Production Deploy Recovery And V40 Compatibility

- 판정: 복구·호환 보정·reviewed main 수동 배포와 독립 운영 검증까지 PASS하여 B-106을 완료했다. 배포 workflow와 사전 보정 SQL 독립 검토는 Blocker/Major 0이었다.
- PR/CI: PR #58을 merge commit `465a0f29e4d7772f41b1ea956f1e3a94896af105`로 병합했다. 최초 API CI에서 cutoff test가 scheduler와 DB `timestamp(6)` 반올림에 경합한 1건을 발견해 미래 시각·microsecond 정밀도로 고정했다. 대상 `cleanTest`와 최신 CI run `33648740285`의 API/Web/whitespace가 모두 통과했다.
- API: `cd apps/api && ./gradlew cleanTest test --no-daemon` → `83 suites / 380 tests / 0 failures / 0 errors / 0 skipped`; `./gradlew bootJar --no-daemon` PASS. 도매꾹 음수 option delta 정규화의 성공·경계·음수 총액·overflow를 포함한다.
- Web: `npm run lint` → `0 errors / 기존 <img> warnings 3`; `npm run build` → 34 pages production build PASS.
- DB rehearsal: S3 production dump `coreable-db-20260902-202331.dump`를 격리 PostgreSQL 17에 복원했다. V38→V39→사전 보정→V40~V44 적용, Hibernate schema validation과 앱 기동이 성공했다. 결과는 음수 option 0, repair audit 81, invalid actor/source range/order snapshot 0이었고 V44에서 보정 SQL 재실행은 version guard로 commit 전에 중단됐다. 임시 DB/container/dump는 검증 뒤 제거했다.
- 운영 V39 보정: 실행 직전 `coreable-db-20260902-220230.dump`를 백업하고 API를 중지했다. 34개 음수 option·13개 상품·68개 non-null option guard 아래 total source cost, 고객가와 주문 snapshot을 보존하며 상품 13개+option 68개 system audit을 한 transaction으로 기록하고 legacy audit NOT NULL을 선해제했다. API를 기존 SHA로 다시 띄운 뒤 V39, 음수 0, audit 81, readiness `UP`, 활성 주문 `SUPPLIER_ORDERED=1`/`SUPPLIER_ORDER_PENDING=1`을 확인했다.
- 운영 배포: manual-only Deploy run `33649335647`이 verify, preflight, build-and-push와 deploy를 모두 통과했다. Preflight backup은 `coreable-db-20260903-003828.dump`, mutation 직전 backup은 `coreable-db-20260903-004047.dump`이며 각각 1,358,390 bytes다. API/Web는 정확한 merge SHA image, API/PostgreSQL은 `healthy`, Web/nginx는 `running`, Flyway는 V44다.
- 운영 데이터: 음수 source option 0, repair audit 81, invalid repair actor/source range/order snapshot 0이며 활성 주문은 `SUPPLIER_ORDERED=1`/`SUPPLIER_ORDER_PENDING=1`로 유지됐다. 공개 `/`, `/products`, `/api/health`, `www`는 HTTP 200이고 `/api/supplier/orders`는 feature gate에서 `404`다.
- 운영 gate와 credential: `APP_SUPPLIER_PORTAL_ENABLED=false`를 유지했다. Per-run `/coreable/deploy/ghcr-token-*` Parameter Store 항목과 `.deploy-recovery-*` directory는 0개다.
- Catalog sync: 새 API에서 `DOMEGGOOK_CATALOG_SYNC_ENABLED=true`, dry-run `false`의 기존 운영 상태를 복원했다. 첫 20건의 `source_synced_at`이 갱신됐고 18건 성공, 2건은 upstream `해당 정보가 존재하지 않습니다`로 `source_sync_error`와 warning을 남겼다. 음수 option과 source 가격 범위 위반은 계속 0이고 API/public health는 유지됐다. 두 upstream 품목은 stable error code와 transient 오류를 구분하기 전 자동 품절로 변경하지 않았다.
- 정적 품질: `git diff --check`, workflow YAML, actionlint, ShellCheck, outer/embedded Bash syntax와 V40 guard mock branch를 통과했다. GitHub runner의 Actions Node 20/setup-java v4 deprecation과 기존 `<img>` 경고 3건은 비차단 후속 정리다.

## 2026-08-30 B-105 Supplier Shortage And Claim Facts

- 판정: V44/API/Web 구현과 문서 동기화, 전체 회귀와 독립 backend/Web/호환성 검토를 완료해 `Review Ready`다. 최종 audit은 blocker/major/minor 0건이다. Task list는 facts 없는 summary이고 detail만 fact history를 반환하며, `orderDetailAvailable`은 supplier claim-task DTO에만 존재하고 supplier resource 조회는 resource id와 supplier id를 같은 DB predicate에 포함한다.
- API 전체: `cd apps/api && ./gradlew cleanTest test --no-daemon` → `83 suites / 379 tests / 0 failures / 0 errors / 0 skipped` (`BUILD SUCCESSFUL`, 1분 14초). 품절 capability·submit/review, 기존 out-of-stock/refund 위임, task/fact/correction/close, idempotency, tenant/role/PII, flag-off replay-first와 기존 Claim/Refund/Shipment 회귀를 확인했다. Supplier/admin task list와 admin shortage list는 1건/다건의 prepared-statement count가 같음을 검증했고, `PAYMENT_EXCEPTION` 또는 late-deposit Refund 주문은 supplier task의 `orderDetailAvailable=false`로 fail closed함을 확인했다.
- PostgreSQL: 전체 suite의 `PostgresMigrationSmokeTest` `13/13 passed` (13.422초). V44 fresh/latest JPA validate와 V43→V44 upgrade, composite FK·CHECK, task별 single root와 predecessor별 single child 위반 차단을 PostgreSQL 17 Testcontainers에서 확인했다.
- Web 정적 검증: `npx tsc --noEmit` PASS, `npm run lint` → `0 errors / 기존 <img> warnings 3`, `npm run build` → 34 pages production build PASS.
- Web 계약: `supplier-shortage-claim-contract.spec.ts` Desktop/Mobile `24 passed`; 기존 `supplier-fulfillment-contract.spec.ts` Desktop/Mobile `26 passed`, 합계 `50/50 passed`. Supplier/admin shortage·claim-task 화면, exact mutation body/Origin/Idempotency-Key, fail-closed capability·reason·instruction, 불확실한 실패의 key 재사용과 기존 fulfillment 회귀를 확인했다. 독립 Web 재검토의 blocker/major는 0건이다.
- 정적 품질: API 변경 범위와 전체 저장소 `git diff --check`, Markdown fence parity를 통과했다.
- 미실행·잔여: 실제 supplier/admin 로그인 session과 live API hydration E2E, 실제 `SUPPLIER_CLAIM_WORK_REQUESTED` email 도착, 같은 Order/task에 대한 실제 PostgreSQL application-level 경합·unpaged 목록·scheduler 성능 부하는 검증하지 않았다. 자동 계약 테스트를 실제 외부 서비스 증거로 보지 않는다.
- 운영 경계: 배포, 운영 데이터, 외부 연락, 실제 email과 production flag를 변경하지 않았다. `APP_SUPPLIER_PORTAL_ENABLED=false`를 유지하며 B-105 완료만으로 외부 supplier route나 portal 상품 판매를 열지 않는다.

## 2026-08-30 B-104 Supplier Multiple Shipments And Tracking Links

- 판정: 구현·문서 동기화와 독립 backend/migration/test/Web 검토를 완료해 `Review Ready`다. 검토에서 발견한 DB parent reassignment, PostgreSQL UUID 잠금 정렬, Web stale 개인정보·행동 권한과 관리자 version fail-open 위험까지 보강했으며 최종 blocker와 major는 0건이다.
- API 전체: `cd apps/api && ./gradlew cleanTest test --no-daemon` → `82 suites / 367 tests / 0 failures / 0 errors / 0 skipped`.
- Portal API: `PortalShipmentApiIntegrationTest` `11 passed`. 기본·분할 할당, 중복·과할당·타 주문 item, H2 동일 Order 동시 등록 1건 성공/1건 `409`, actor-safe replay, owner takeover, expected version, cutoff, void/replacement, 배송완료·재개·후속 Refund 차단을 확인했다. Coreable 관리자 생성의 기본 전체 할당·ADMIN 이력, flag-off stored replay·기존 정정·신규 생성 차단, stale version 무변경과 활성 2건/void 1건의 customer/admin/legacy projection도 확인했다.
- PostgreSQL: 전체 suite의 `PostgresMigrationSmokeTest` `12 passed`. V43 legacy backfill, V42-shaped old writer의 commit-time allocation, legacy partial unique, portal key unique, cross-order allocation과 Shipment/OrderItem parent reassignment trigger, preflight 실패를 PostgreSQL 17 Testcontainers에서 확인했다.
- Web 정적 검증: `npm run lint` → `0 errors / 기존 <img> warnings 3`; `npm run build` → 30 pages production build PASS.
- Web 계약: `supplier-fulfillment-contract.spec.ts` Desktop/Mobile `26 passed`. carrier·shipment fail-closed normalizer, supplier/admin 누락·비정상 version의 정정 차단, 공급처·Coreable owner의 관리자 기존 송장 액션, `403/404/409` refresh, 부분 refresh의 최신 MASKED 주문 보존·stale 송장 행동 제거, 불확실한 실패의 idempotency key 재사용과 확정적 거절 뒤 교체, supplier/admin request method·path·allocation과 고객 직접취소 차단 계약을 확인했다. 실제 로그인 session과 live API를 포함한 end-to-end 증거는 아니다.
- 공식 링크 확인: registry의 CJ대한통운·롯데·한진·우체국 공식 URL 템플릿은 임의 송장번호로 HTTP 200 응답을 확인했다. 실제 배송건 조회 결과나 실시간 상태 정확성을 검증한 것은 아니다.
- 정적 품질: `git diff --check`, Markdown fence parity와 B-104 Implemented/production flag-off 문서 대조를 통과했다.
- 미실행·잔여: 실제 PostgreSQL application-level supplier/admin 동일 Order 경합과 다중 Order 반대 순서 batch 부하, 실제 supplier/admin 로그인 live E2E, 실제 택배 배송건 조회는 검증하지 않았다.
- 운영 경계: 배포, 운영 데이터, 실제 송장과 production flag를 변경하지 않았다. `APP_SUPPLIER_PORTAL_ENABLED=false`를 유지하며 B-105·B-098·개인정보 고지·실 email gate 전에는 외부 포털을 열지 않는다.

## 2026-08-30 B-103 Supplier Fulfillment And Minimum PII

- 판정: 로컬 구현·문서 동기화와 독립 backend/test/Web 검토를 완료해 `Review Ready`다. 최종 구현 검토에서 P0/P1/P2 finding은 0건이며 production 공급처 포털은 계속 비활성이다.
- API 전체: `cd apps/api && ./gradlew cleanTest test --no-daemon` → `78 suites / 342 tests / 0 failures / 0 errors / 0 skipped`.
- CI 이식성 회귀: PR #55 최초 API run은 Linux/JDK 21에서 retention cleanup 시각의 나노초 완전일치 assertion 1건만 실패했다. 운영 코드는 바꾸지 않고 DB timestamp 정밀도에 맞춘 1 microsecond 이내 비교로 수정했으며, 동일 JDK 21의 알림 suite와 전체 `78 suites / 342 tests`가 다시 통과했다.
- PostgreSQL: 전체 suite의 `PostgresMigrationSmokeTest` `10 passed`. V42 fresh/upgrade, 기존 null 호환, delivery memo 길이, Claim PII grant/access-log FK·unique·index와 action별 reason constraint를 PostgreSQL 17 Testcontainers에서 확인했다.
- API 회귀: portal/fallback/legacy/결제 예외 routing, outbox 실패 전체 rollback, tenant·role·PII allowlist, cutoff/terminal/takeover, Claim grant, 배송 메모 HTTP projection, PII-free email·retry·retention·redaction과 B-105 전 claim-work producer 미호출을 확인했다.
- Web 정적 검증: `npm run lint` → `0 errors / 기존 <img> warnings 3`; `npm run build` → 30 pages production build PASS.
- Web 계약: `supplier-fulfillment-contract.spec.ts` Desktop/Mobile `12 passed`. normalize된 목록 PII 비노출과 상세 FULL/MASKED 최소정보를 실제 DOM으로 렌더했다. `page.setContent` 기반이라 실제 supplier session, route fetch, hydration과 앱 CSS를 포함한 live E2E 증거는 아니다.
- 정적 품질: `git diff --check` PASS. README와 설계 문서의 B-103 구현 상태, contract expiry/revoke `403`, reason-code allowlist와 Markdown fence parity를 대조했다.
- 미실행·잔여: 실제 PostgreSQL 동시 트랜잭션의 detail/takeover/claim/lifecycle lock-order, 100건 초과 scheduler·notification batch 부하, 실제 supplier 로그인/live API, 실제 email 도착은 검증하지 않았다. Email provider 수락 후 DB commit 실패 시 복구는 중복 전달 가능한 at-least-once 특성이 남는다.
- 운영 경계: 배포, 외부 연락, 운영 데이터와 입금은 변경하지 않았다. `APP_SUPPLIER_PORTAL_ENABLED=false`를 유지하며 실제 email·개인정보 고지·계약 및 B-104/B-105 release gate를 모두 검증하기 전에는 열지 않는다.

## 2026-08-30 B-102 Supplier Inventory And Reservation

- 판정: 로컬 구현·문서 동기화·독립 코드/Web/문서 리뷰 완료. P0/P1 잔여 없음. Production 공급처 포털은 계속 비활성이며 외부 입금·이메일·운영 데이터는 변경하지 않음.
- API 전체: `cd apps/api && ./gradlew cleanTest test --no-daemon` → `303 tests / 0 failures / 0 errors / 0 skipped`.
- PostgreSQL: 전체 suite의 `PostgresMigrationSmokeTest` `10 passed`. V41 fresh migration, legacy/portal backfill preflight, old-writer defaults, composite FK/partial unique, option row-lock 대기 후 checkout 재검증을 PostgreSQL 17 Testcontainers에서 확인.
- Web 정적 검증: `npm run lint` → `0 errors / 기존 <img> warnings 3`; `npm run build` → 29 pages production build PASS.
- Web 계약: `supplier-products-contract.spec.ts` Desktop/Mobile `40 passed`. 재고 표시/비노출, command evidence/replay key, customer/admin refund projection, `REQUESTED → APPROVED → manual-complete`, 재고 conflict canonical recovery·version 동기화·이미지 재업로드 방지를 확인.
- 로컬 화면: 임시 PostgreSQL 17에 V1~V41과 로컬 seed를 적용하고 전용 API/Web 포트에서 관리자 `REQUESTED` 환불 승인 form 및 `EXPIRED`/`CANCELLED` 주문 필터 smoke `2 passed`. 검증 후 API, Web, 임시 DB container와 port를 모두 종료·제거.
- 정적 품질: `git diff --check` PASS. B-102 stale Planned/B-068 current/Fulfillment ownership/잘못된 `payment_events.order_id not null` 문구와 Markdown fence parity를 검색해 문서 정합성 PASS.
- 리뷰 후 회귀: legacy Coreable 주문 자동 만료, supplier reassignment, account deletion group Refund, migration rollback defaults, PaymentEvent order FK, PostgreSQL 잠금 경합, 환불 승인 화면, 재고 409 재시도, 만료/취소 탐색을 추가 검증.

## 2026-08-06 B-095 Full Operational QA

- 상태: QA `0.87 PASS`. 운영 read-only 점검, 독립 로컬 전체 회귀, 수정 배포와 운영 재검증 완료.
- 운영 고객 화면: Desktop/Mobile 홈, 상품 목록·검색·카테고리, 상품 상세, 로그인 경계, 고객 문의, 회사·정책, 404를 확인했다. 실제 주문·입금·개인정보 전송은 하지 않았다.
- 배포 후 케이스 매트릭스:

  | 대상 | Desktop 실제 결과 | Mobile 실제 결과 | 판정 |
  | --- | --- | --- | --- |
  | `/`, `/products`, 정책·회사·고객지원 | 10개 공개 경로 200, 헤더·검색·가로 overflow 정상 | 동일 | PASS |
  | `/products?q=안전` | 관련 카테고리 영역 1개, 상품 카드 24개 | 검색 결과 필터 1개, 상품 카드 24개 | PASS |
  | `?category=PPE_SAFETY_HELMET` | `안전모 상품`, 검색 관련 사이드바 0개 | `안전모 상품`, 검색 결과 필터 0개 | PASS |
  | 공개 상품 상세 | 200, Desktop 장바구니 CTA 1개 | 200, Mobile 구매바 장바구니 CTA 1개 | PASS |
  | 비로그인 장바구니·관리자 | `/login?redirectTo=/cart`, 관리자 API 401 | 동일 권한 경계 | PASS |
  | 빈 카테고리 노출 | 전역 `일반 작업장갑` 링크 0, 빈 홈 추천 0 | 전역 링크 0 | PASS |

- 운영 API·데이터: 공개 상품 999개, 썸네일 URL 실패 0, 잘못된 MOQ·주문단위 0, 상품번호 중복 0을 확인했다. 공개 상품이 없는 카테고리 코드는 39개였고 UI에서 노출하지 않도록 수정했다.
- 보안 헤더: 운영 `/`에서 HSTS `max-age=31536000; includeSubDomains`, `nosniff`, `DENY`, `strict-origin-when-cross-origin`, 카메라·마이크·위치 차단과 CSP `default-src 'self'`, `frame-ancestors 'none'`을 확인했고 `X-Powered-By`는 없었다.
- 읽기 전용 불변성: QA 시작 `2026-08-06 01:11:23 UTC` 이후 운영 주문·결제 생성은 각각 0건이다. 상품 변경 이력 83건은 모두 시스템 관리자 `00000000-0000-0000-0000-000000000000`의 `공급처 상품 정기 동기화`였고 QA 조작 이력은 없었다.
- 운영 인프라: API readiness `UP`, EC2와 시스템 상태 검사 정상, 컨테이너 재시작·OOM 0, CloudWatch 백업·CPU credit·EC2 상태 알람 `OK`, 최신 DB 백업을 확인했다.
- 자동 검증:
  - API: `./gradlew test` 성공.
  - Web: `npm audit` 취약점 0, lint 오류 0·기존 `<img>` 경고 3, production build 성공.
  - Local Playwright: Desktop/Mobile 전체 `85 passed / 25 skipped / 0 failed`를 동일 환경에서 2회 연속 확인했다.
  - Production deploy smoke: 수정 전 Desktop/Mobile `8 passed / 0 failed`, 배포 후 메뉴 회귀 포함 `10 passed / 0 failed`.
- 수정: 전역 카테고리 메뉴와 홈 추천 링크는 기존 `categoryCounts`가 1 이상인 카테고리만 노출하고, 빈 결과 안내에서 관리자용 문구를 제거했다. 현재 UI와 불일치하던 인증·정책 버전·모바일·visual 회귀 기대값을 동기화했다.
- 배포 재검증: PR #45와 Actions run `31064199472` 성공 후 Desktop/Mobile에서 `일반 작업장갑` 링크 0개, 홈의 빈 추천 링크 0개, 직접 빈 카테고리 URL의 관리자용 문구 0개를 확인했다.
- 배포 안전성: Actions를 exit-status와 15~30초 polling으로 끝까지 추적해 verify, ARM build/push, SSM deploy가 중단·재시도 없이 완료됨을 확인했다. EC2 API/Web 이미지 태그는 merge SHA `922dcecae5cc9a88d8462e1860d8a15e41b8b871`와 일치하고 API·PostgreSQL은 healthy, Web은 running이다.
- 남은 P2: Cloudflare Web Analytics 스크립트 CSP 차단, 기본 영문 404, 푸터 반품 주소 직접 노출(B-030), 이름·가격이 같은 상품 43개, 상품고시 placeholder 540개와 검수 대기 상품 972개, 운영 MOQ 2 이상 실데이터 부재.
- 제한: 운영 관리자 인증 세션이 없어 배포 관리자 화면은 비로그인 401 경계만 확인했다. 관리자 전체 화면·상태 전이·문의·주문·MOQ는 격리된 로컬 DB에서 검증했다.

## 2026-08-01 - 2026-08-02 Full Functional QA

- 상태: 자동 검증과 운영 read-only 검증 완료
- 범위: API 전체 테스트, Web lint/build, Desktop/Mobile Playwright, 운영 read-only smoke
- 결과:
  - API: `133 tests`, 실패·오류·skip 0. PostgreSQL Testcontainers smoke 포함.
  - Web: lint 오류 0, 기존 `<img>` 경고 3, production build 성공.
  - Local Playwright: `83 passed`, viewport 조건부 `19 skipped`, 실패 0.
  - Production Playwright: Desktop/Mobile `8 passed`, 실패 0.
  - 운영 확인: 상품 이미지 200, 고객·관리자 API 비로그인 401, Kakao authorize 302와 운영 callback URI 확인.
- 발견 사항:
  - 운영 판매 중단 상태를 정상 상태로 처리하지 못하던 deploy smoke를 수정했다.
  - 공유 로컬 DB를 변경하는 E2E와 snapshot의 병렬 충돌을 막기 위해 Playwright 기본 worker를 1개로 고정했다.
  - 이미지 로딩 완료 후 snapshot을 찍도록 해 상품 이미지 유무에 따른 visual test 변동을 제거했다.
  - 관리자 전체 페이지 순회에 `/admin/referrals`를 추가했다.
  - Server Action form의 불필요한 `encType` 지정으로 발생하던 React 경고를 제거했다.
- 외부 차단: 실제 송금, 도매꾹 e-money 발주, 실택배, SES 실메일은 별도 운영 검증이 필요하다.
