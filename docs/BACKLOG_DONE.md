# Completed Backlog

완료된 backlog 항목을 보관한다. 현재 작업 큐는 `docs/BACKLOG.md`를 기준으로 본다.

## 2026-06-30

- B-032 도매꾹 수집 상품 관리자 API 적재 도구
  - 커밋: `feat: add domeggook product importer`
  - 완료 내용: 도매꾹 수집 산출물에서 manifest를 만들고, 선택된 상품을 기존 관리자 API로 공급처/상품/옵션/대표 이미지/상세 이미지 블록까지 적재하는 로컬 스크립트를 추가했다. 기본 상태는 `HIDDEN`이고 카테고리는 자동 추정하지 않는다.
  - 검증: `node scripts/import-domeggook-products.mjs --help`, `node scripts/import-domeggook-products.mjs --init-manifest`, `node scripts/import-domeggook-products.mjs --manifest tmp/domeggook-import-manifest.json`, `git diff --check`
- B-031 도매꾹 상품 이미지/상세이미지 수집 도구
  - 커밋: `feat: add domeggook product collector`
  - 완료 내용: 도매꾹 상품 URL에서 상품 후보 정보, 대표 이미지, 상세 이미지 URL을 파싱하고 `이미지사용: 허용`일 때만 이미지를 로컬 `tmp/domeggook-products/{상품번호}/`에 다운로드하는 스크립트를 추가했다. 결과는 JSON/CSV로 저장해 관리자 수동 등록 전에 검수하도록 했다.
  - 검증: `node scripts/collect-domeggook-product.mjs --help`, `node scripts/collect-domeggook-product.mjs https://mobile.domeggook.com/8667274`, `git diff --check`
- B-025 고객 문의 테이블 생성 migration 추가
  - 커밋: `fix: add customer inquiry migration`
  - 완료 내용: B-012에서 구현된 고객 문의 접수/관리자 문의 목록 기능이 실제 DB에서 동작하도록 `customer_inquiries` Flyway migration을 추가했다.
  - 검증: `cd apps/api && ./gradlew test --tests '*CustomerInquiry*'`, `cd apps/api && ./gradlew test`, `cd apps/web && npm run lint`, `cd apps/web && npm run build`, `git diff --check`, 로컬 Postgres Flyway V21 적용 및 `/admin/inquiries` SSR 확인
- B-007 송장 입력과 배송조회 운영 화면 연결
  - 커밋: `feat: connect admin shipment tracking controls`
  - 완료 내용: 관리자 주문 상세에서 배송조회 상태, 실패 사유, 수동 보정 사유를 확인하고 배송조회 결과/실패 사유 반영 및 수동 배송완료 보정을 기존 shipment API에 연결했다. 내부 배송조회 sync token 운영 문서도 보강했다.
  - 검증: `cd apps/web && npm run lint`, `cd apps/web && npm run build`, `cd apps/api && ./gradlew test --tests '*AdminOrder*'`, `git diff --check`
- B-012 사업자 정보/푸터/정책 페이지/고객 문의 운영 고지
  - 커밋: `feat: add legal footer and customer inquiries`, `docs: record legal notice decision`
  - 완료 내용: 푸터에 실제 사업자 정보와 정책 링크를 노출하고, 이용약관/개인정보처리방침/배송/취소환불/품절 안내 페이지, 회사 정보 페이지, 고객 문의 접수와 관리자 문의 목록을 연결했다.
  - 검증: `cd apps/web && npm run lint`, `cd apps/web && npm run build`, `cd apps/api && ./gradlew test`, `git diff --check`
- B-005 상품 상세 HTML/이미지 블록 관리
  - 커밋: `feat: connect admin product detail content`
  - 완료 내용: 관리자 상품 상세 화면에서 IMAGE/HTML 상세 블록을 전체 교체 방식으로 저장하고, 상세 이미지 파일 업로드와 상품 고시 저장을 기존 catalog API에 연결했다.
  - 검증: `cd apps/web && npm run lint`, `cd apps/web && npm run build`, `cd apps/api && ./gradlew test --tests '*Catalog*'`, `git diff --check`
- B-024 API 계약 리뷰 후속 정리
  - 커밋: `fix: align api contract followups`
  - 완료 내용: 관리자 계정의 고객 주문 화면 권한 안내를 분리하고, 약관 동의 응답 타입 선언, 옵션 생성 사유 입력, 고객 취소/클레임 API 문서 상태, 이미지 업로드 note를 실제 계약에 맞게 정리했다.
  - 검증: `cd apps/web && npm run lint`, `cd apps/web && npm run build`, `cd apps/api && ./gradlew test`, `git diff --check`, 브라우저 관리자 주문 화면 권한 안내 확인
- B-023 프론트엔드-백엔드 API 계약 검토
  - 커밋: `docs: record api contract review`
  - 완료 내용: 프론트 `/api/**` 호출과 백엔드 controller/DTO, `docs/api-spec.md`를 대조해 API path/method, 권한, DTO, 오류 처리 계약을 점검했다. 결과는 `docs/API_CONTRACT_REVIEW.md`에 정리하고 후속 정리 항목은 B-024로 분리했다.
  - 검증: `cd apps/web && npm run lint`, `cd apps/web && npm run build`, `cd apps/api && ./gradlew test`, `git diff --check`, 브라우저 샘플 확인
- B-006 상품 옵션/판매 상태 관리 화면 정리
  - 커밋: `feat: connect admin product option management`
  - 완료 내용: 관리자 상품 목록의 `관리` 링크를 상품 상세 관리 화면으로 연결하고, 상품 판매 상태 변경, 옵션 추가, 옵션 정보/상태 변경, 변경 이력 조회를 기존 admin catalog API에 연결했다.
  - 검증: `cd apps/web && npm run lint`, `cd apps/web && npm run build`, `cd apps/api && ./gradlew test --tests '*Catalog*'`, `git diff --check`, 브라우저 관리자 상세 화면 확인
- B-022 상품목록 모바일 필터/카드 UX 정리
  - 커밋: `71d60a9` `feat: improve mobile catalog ux`
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
