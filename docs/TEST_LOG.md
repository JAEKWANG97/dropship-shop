# Test Log

실행한 검증만 기록한다. 실제 외부 서비스 검증은 자동 테스트 결과와 합치지 않는다.

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
