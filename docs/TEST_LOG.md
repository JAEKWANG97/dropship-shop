# Test Log

실행한 검증만 기록한다. 실제 외부 서비스 검증은 자동 테스트 결과와 합치지 않는다.

## 2026-08-06 B-095 Full Operational QA

- 상태: 운영 read-only 점검과 독립 로컬 전체 회귀 검증 완료. 빈 카테고리 노출 수정은 배포 재검증 대기.
- 운영 고객 화면: Desktop/Mobile 홈, 상품 목록·검색·카테고리, 상품 상세, 로그인 경계, 고객 문의, 회사·정책, 404를 확인했다. 실제 주문·입금·개인정보 전송은 하지 않았다.
- 운영 API·데이터: 공개 상품 999개, 썸네일 URL 실패 0, 잘못된 MOQ·주문단위 0, 상품번호 중복 0을 확인했다. 공개 상품이 없는 카테고리 코드는 39개였고 UI에서 노출하지 않도록 수정했다.
- 운영 인프라: API readiness `UP`, EC2와 시스템 상태 검사 정상, 컨테이너 재시작·OOM 0, CloudWatch 백업·CPU credit·EC2 상태 알람 `OK`, 최신 DB 백업을 확인했다.
- 자동 검증:
  - API: `./gradlew test` 성공.
  - Web: `npm audit` 취약점 0, lint 오류 0·기존 `<img>` 경고 3, production build 성공.
  - Local Playwright: Desktop/Mobile 전체 `85 passed / 25 skipped / 0 failed`를 동일 환경에서 2회 연속 확인했다.
  - Production deploy smoke: Desktop/Mobile `8 passed / 0 failed`.
- 수정: 전역 카테고리 메뉴와 홈 추천 링크는 기존 `categoryCounts`가 1 이상인 카테고리만 노출하고, 빈 결과 안내에서 관리자용 문구를 제거했다. 현재 UI와 불일치하던 인증·정책 버전·모바일·visual 회귀 기대값을 동기화했다.
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
