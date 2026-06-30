# Completed Backlog

완료된 backlog 항목을 보관한다. 현재 작업 큐는 `docs/BACKLOG.md`를 기준으로 본다.

## 2026-06-30

- B-022 상품목록 모바일 필터/카드 UX 정리
  - 커밋: `pending` `feat: improve mobile catalog ux`
  - 완료 내용: 모바일 상품목록 필터를 접힘 구조로 바꾸고, 상품 카드를 리스트형으로 압축했다. 헤더 중복 카테고리와 `바로 구매` CTA를 제거하고, Pretendard CDN과 최신 카테고리 체계를 반영했다.
  - 검증: `cd apps/web && npm run lint`, `cd apps/web && npm run build`, `cd apps/api && ./gradlew test --tests '*Catalog*'`, `git diff --check`, 브라우저 desktop/mobile 확인
- B-021 메인페이지 신뢰 정보와 현장별 구매 묶음 추가
  - 커밋: `7cf44da` `feat: add homepage purchase guidance`
  - 완료 내용: 홈 추천 상품 문구를 `현장에서 자주 찾는 상품`으로 바꾸고, 현장별 구매 묶음을 추가했다. 홈 하단 신뢰 정보는 후속 UX 정리에서 제거했다.
  - 검증: `cd apps/web && npm run lint`, `cd apps/web && npm run build`, `git diff --check`, 브라우저 desktop/mobile 확인
- B-020 메인페이지 쇼핑 전환형 UX 개선
  - 커밋: `feat: streamline shopping home`
  - 완료 내용: 메인페이지 히어로를 압축하고, 필요한 품목 찾기와 자주 찾는 품목 링크를 히어로 안으로 정리해 첫 상품 노출 위치를 앞당겼다.
  - 검증: `cd apps/web && npm run lint`, `cd apps/web && npm run build`, `git diff --check`, 브라우저 desktop/mobile 확인
- B-019 UX 리뷰 핵심 개선
  - 커밋: `d387e61` `feat: improve core ux flows`
  - 완료 내용: 관리자 계정의 고객 구매 흐름 접근을 API 장애와 분리하고, 상품 목록 카테고리 필터와 계정 인증 화면의 핵심 UX를 정리했다.
  - 검증: `cd apps/web && npm run lint`, `cd apps/web && npm run build`, `git diff --check`, 브라우저 desktop/mobile 확인
- B-018 상품 카테고리 체계
  - 커밋: `feat: add product category taxonomy`
  - 완료 내용: 상품에 고정 `categoryCode`를 추가하고, 관리자 등록/목록, 고객 목록/상세, 홈/헤더 카테고리 링크를 실제 카테고리 코드 기준으로 연결했다.
  - 검증: `cd apps/api && ./gradlew test`, `cd apps/web && npm run lint`, `cd apps/web && npm run build`, `git diff --check`

## 2026-06-29

- B-004 상품 이미지 업로드
  - 커밋: `feat: connect product image upload`
  - 완료 내용: 관리자 상품 등록 시 대표 이미지 파일을 업로드하고, 반환된 URL을 상품 대표 이미지 metadata에 저장하도록 연결했다.
  - 검증: `cd apps/web && npm run lint`, `cd apps/web && npm run build`, `cd apps/api && ./gradlew test --tests '*Catalog*'`, `git diff --check`
- B-017 로그인 후 고객 필수 정보와 휴대폰 번호 인증
  - 커밋: `feat: add customer phone verification`
  - 완료 내용: 이름, 연락 가능한 이메일, SMS OTP 휴대폰 번호 인증을 고객 필수 정보로 구현했다.
  - 검증: `cd apps/api && ./gradlew test`, `cd apps/web && npm run lint`, `cd apps/web && npm run build`, `git diff --check`
- 내부 배송조회 동기화 API 토큰 보호 및 관리자 API 실패 표시
- 카카오 OAuth 로그인 수정
- 관리자 mock operational data 제거
- 로컬 상품 이미지 fixture를 API upload URL 기준으로 정리
- 고객/관리자 로그인 상태별 헤더 정책 정리
