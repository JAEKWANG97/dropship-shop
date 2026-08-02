# Test Log

실행한 검증만 기록한다. 실제 외부 서비스 검증은 자동 테스트 결과와 합치지 않는다.

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
