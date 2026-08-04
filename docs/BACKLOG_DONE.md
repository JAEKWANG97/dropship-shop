# Completed Backlog

완료된 backlog 항목을 보관한다. 현재 작업 큐는 `docs/BACKLOG.md`를 기준으로 본다.

## 2026-08-04

- B-086 ACTIVE 상품 가격·옵션·재고 동기화
  - 동기화: 승인된 상품 조회 API로 공급가, 공급처 최저 판매가격, 옵션, 원본 재고와 판매 상태를 시간당 20개씩 갱신한다. 호출 간격은 최소 1초다.
  - 가격·상태: 현재 가격 정책과 최저 판매가격 하한을 적용하며 공급처 판매 중지·활성 옵션 부재는 `SOLD_OUT`으로 반영한다. 공급처 사유로 자동 품절된 상품만 판매 회복 시 `ACTIVE`로 복구한다.
  - 보호: 기존 주문 snapshot과 운영자가 설정한 `HIDDEN`, `STOPPED`, 수동 `SOLD_OUT`은 변경하지 않는다. 실패 시 기존 가격·옵션을 유지하고 관리자 화면에 마지막 시각과 오류를 표시한다.
  - 운영: PR #28과 Actions run `30871283747` 배포 후 V37, readiness와 20개 dry-run을 확인했다. 첫 실제 배치는 `ACTIVE 20 / 오류 0 / 공급처 판매 가능 20`이었다.
  - 검증: 파서·멱등 동기화 단위 테스트, Catalog API 회귀, PostgreSQL migration smoke, API 전체 CI, Web lint/build와 `git diff --check`를 통과했다.

- B-072 도매꾹 Private API 자동 발주와 e-money 결제
  - 실제 검증: 도매꾹 주문 `75118255`로 e-money 3,450원 결제, 상태 조회, 구매취소와 잔액 반환을 확인했다.
  - 중복 방지: Coreable 주문번호를 공급처 주문 메모와 정확히 대조하고 결제금액까지 일치할 때만 응답 유실 주문을 확정한다. 일치 주문이 없음을 확인한 경우에만 재시도를 허용한다.
  - 배송 동기화: 실제 중첩 배송 응답에서 택배사와 송장번호를 읽는다. 단일 배송 모델과 맞지 않는 복수 송장 또는 기존 송장 불일치는 관리자 오류 큐로 보낸다.
  - 개인정보: 수령인 이름, 이메일, 전화번호, 우편번호와 배송지의 도매꾹 공급처·택배사 제공 목적을 고지하고 필수 개인정보처리방침 버전을 `2026-08-04`로 갱신했다.
  - 운영: PR #26과 GitHub Actions run `30868879429` 배포 후 readiness와 공개 고지를 확인했다. 발주 대기·진행·대사 필요 주문 0건을 확인한 뒤 자동 발주를 활성화했다.
  - 검증: Private API fixture, 주문 메모 대사·재시도 회귀 테스트, API 전체 테스트, Web lint/build, `git diff --check`를 통과했다.

## 2026-08-03

- B-087 B-085 상품 데이터 운영 반영
  - 보호: 적용 직전 운영 DB dump와 상품 이미지 1,827개를 S3에 백업했다.
  - 적용: 운영 dry-run에서 기존 292개 갱신, 신규 186개, 기존 제외 8개 숨김, 실패 0건을 확인한 뒤 기존 관리자 API importer로 반영했다.
  - 복구: 첫 실행은 2시간 관리자 토큰 만료로 일부 중단됐으며, 실패 114건만 멱등 재시도해 `UPDATED 72 / IMPORTED 39 / HIDDEN 3 / 실패 0`으로 완료했다.
  - 결과: 운영 DB `ACTIVE 478 / HIDDEN 133`, 상품번호 중복 0건, 원본 옵션 966개를 확인했다. 대표 신규 상품의 공개 상세, 썸네일, 옵션, 상세 블록과 상품정보제공고시가 정상 응답했다.

- B-085 공급처 원문 상품 데이터 재수집
  - 수집: 81개 카테고리를 도매꾹랭킹순과 사업자 낱개구매 조건으로 조회하고 기존 수집본을 공식 상세 API로 갱신했다.
  - 선별: 공급처 상품번호를 식별자로 사용해 `IMPORT 478 / EXCLUDE 74`로 자동 분류했다. 조건부 배송비, 낱개구매 불가, 비안전용품, 불완전 상품과 명시적 인증 부적합만 제외했다.
  - 적재: 기존 292개를 원문 상품명·공급가·옵션·상품정보제공고시로 갱신하고 신규 186개를 추가했다. 기존 제외 상품 8개는 삭제하지 않고 `HIDDEN` 처리했다.
  - 가격: 배송비를 더하지 않고 공급가에 현재 25% 가격 정책을 적용했으며 공급처 최저 판매가격은 100원 단위 올림 하한으로 적용했다.
  - 결과: 로컬 DB 상품번호 중복 0건, `ACTIVE 486 / HIDDEN 10 / SOLD_OUT 1`, import 실패 0건을 확인했다.
  - 검증: 수집·검수·import·판매 감사 self-check, API 전체 테스트, Web lint/build, Desktop 상품 탐색·장바구니와 Mobile 구매바 Playwright, `git diff --check`.

## 2026-08-02

- B-026 초기 판매 상품 데이터 준비
  - 완료 내용: 대표·상세 이미지 규격과 상품 등록 가이드를 정하고, 수집·검수·import 흐름으로 초기 판매 상품 300개를 적재해 고객 상품 목록·상세·장바구니 노출을 확인했다.
  - 후속: 공급처 원문 기준 재수집은 B-085, ACTIVE 상품의 가격·옵션·재고 정기 동기화는 B-086으로 분리했다.

- B-083 공급처 상품 중복 등록 방지
  - 식별 기준: 도매꾹 상품 주소에서 상품번호를 추출해 `sourceItemNo`로 저장하고, 요청값과 주소의 번호가 다르거나 주소에서 번호를 확인할 수 없으면 거부한다.
  - 중복 차단: 기존 중복은 최초 상품만 식별번호를 유지하고 나머지는 원본 URL과 이력을 보존한 채 `HIDDEN` 처리하며, NULL을 제외한 DB unique index와 API 사전 검증으로 재등록·동시 생성을 막는다.
  - import: 이미 등록된 `sourceItemNo`는 기존 상품 ID와 함께 `SKIPPED` 결과를 남긴다.
  - 검증: 동일 번호 재등록, 동일 이름의 다른 번호, 번호 불일치·누락, 동시 생성, importer self-check, PostgreSQL migration smoke를 확인했다.

- B-084 상품정보제공고시 구조화와 상세 최소화
  - 공개 범위: 공급처 상품정보제공고시를 `{ label, value }` 행으로 저장·표시하고 공급사 정보와 거래조건은 고객 화면에서 제외했다.
  - 상세 구성: 상세 이미지, 구매 패널의 옵션·가격·수량, 접을 수 있는 상품정보제공고시, 코어블 정책 요약·링크만 유지했다. 기존 문자열 고시는 이전 데이터 fallback으로 남겼다.
  - 검증: Catalog API 통합 테스트, PostgreSQL migration smoke, 수집·import self-check, Web lint/build, `git diff --check`.

## 2026-08-01

- B-081 현재 구현 기준 문서 정합성 감사
  - 현재 기준: 계좌입금 전용 결제, 공급가 기준 25% 가격 정책, 도매꾹 자동 발주와 수동 fallback, 판매 차단 조건을 제품·요구사항·아키텍처·주문 흐름 문서에 동기화했다.
  - 이력 보존: 과거 Toss/PG와 배송비 가산 결정은 삭제하지 않고 `Decision Log`의 현재 대체 결정에서 역사 기록임을 명시했다.
  - 후속: 최종 정책 버전, 구매안전서비스, 세금계산서 안내는 B-030, 공급처 원문 재수집은 B-085에서 처리한다.

- B-080 주문 동의 증적과 배송지 정합성
  - 서버 기준: Checkout 응답에 공통 배송지와 현재 정책 증적을 포함하고, 제출된 버전을 서버 값과 검증한 뒤 서버의 고정 확인 문구를 저장한다.
  - 주소 잠금: 주문서에 실제 배송지를 표시하고 정책 확인 전까지만 수정할 수 있으며, 확인 후 Checkout과 고객 주문의 직접 주소 변경을 차단한다.
  - 정책 버전: 공개 정책과 필수 동의 버전을 `prelaunch-2026-06-30`으로 통일하고 실오픈 버전은 B-030에서 다시 확정한다.
  - 검증: Checkout/ShippingAddress API 통합 테스트, API 전체 테스트, Web lint/build, Checkout Playwright, `git diff --check`.

## 2026-07-31

- B-078 상품 상세 UI QA 마감
  - 구매 패널: 배송·반품 전문 대신 확정된 정책 요약과 상세 정책 링크만 표시하고 전체 고시는 기존 하단 영역에 유지했다.
  - 모바일: 고정 구매바가 푸터의 마지막 정책 링크를 가리지 않도록 안전 영역을 포함한 하단 여백을 추가했다.
  - 시각 회귀: 실제 화면과 달라진 모바일 상품 상세와 오래된 관리자 주문 상세 snapshot만 갱신했다.
  - 검증: API 전체 테스트 131건, Web lint/build, Playwright `67 passed / 25 skipped / 0 failed`, Desktop/Mobile 캡처, `git diff --check`.

## 2026-07-30

- B-077 관리자 주문 처리 화면 정리
  - 상태별 액션: 입금대기, 발주대기, 발주 진행, 발주완료, 환불 승인 상태에서 현재 실행 가능한 다음 액션만 표시한다.
  - 배송: 배송중 주문에서만 조회 결과, 실패 사유, 수동 배송완료 보정 form을 표시한다.
  - 관리자 화면: 고객용 utility, 검색 헤더, footer를 숨기고 관리자 내비게이션과 주문 정보가 먼저 보이게 했다.
  - 검증: Web lint/build, Playwright 관리자 주문 상세 상태별 액션과 overflow 확인, `git diff --check`.
- B-076 판매 정책과 상품 정보 신뢰도 정리
  - 판매 차단: 구매안전서비스 준비 전 운영 판매를 기본 비활성화하고 상품 상세·장바구니 안내와 장바구니 추가·주문서 생성 API를 같은 서버 설정으로 차단했다.
  - 상품 정보: 공개 상세에 인증 상태와 기존 배송·반품 고시 요약을 가격 가까이에 표시했다.
  - 데이터 감사: `ACTIVE` 308개에서 placeholder, 중복, 과도한 상품명과 상품 고시 누락을 검사해 추천 후보 20개를 선별했다. 인증 `PENDING`은 기존 정책대로 경고만 남기고 판매 차단에는 사용하지 않는다.
  - 검증: Catalog/Cart/판매 차단 API 통합 테스트, 감사 도구 self-check와 실데이터 실행, Web lint/build, `git diff --check`.
- B-075 고객 구매 UX 신뢰 오류 수정
  - 고객 신뢰: 장바구니의 상품·옵션 판매 중지 사유를 고객용 한국어 안내로 제한하고 상품 상태별 API 회귀 테스트를 추가했다.
  - 탐색·구매: 전체 상품과 대분류 선택 표시를 실제 query에 맞췄고 모바일 상품 상세 구매바를 버튼 한 줄로 줄였다. primary/accent 버튼은 대비가 확보된 `--accent-strong`을 사용한다.
  - 화면 정리: 고객 헤더 중복 링크, 상품 카드의 관리 화면 같은 테두리·상세 버튼, 마이페이지의 긴 단일 열 구조를 정리했다.
  - 검증: Cart API 통합 테스트, Web lint/build, Playwright 상품 필터·모바일 구매바 테스트, `git diff --check`.

## 2026-07-28

- B-002 카카오 소셜 로그인 실브라우저 검증
  - 완료 내용: 운영 `/login?redirectTo=/account`에서 카카오 로그인만 노출하고, 실제 카카오 계정의 이메일 제공 동의부터 callback, HttpOnly 로그인 세션, `/account` 이동까지 확인했다.
  - 계정 반영: 기존 `@oauth.local` placeholder 이메일이 인증된 카카오 이메일로 교체됐고, 실제 배송 연락처 저장 후 이름·이메일·휴대폰 번호 기준 `필수 정보 완료` 상태를 확인했다.
  - 실패 흐름: 카카오 authorize 요청에 `profile_nickname account_email` scope와 운영 callback URI가 포함되며, `error=access_denied` callback은 빈 화면 대신 구조화된 `400 BUSINESS_RULE_VIOLATION` 응답을 반환한다.
  - 배포: 커밋 `f47493b`를 GitHub Actions run `30374501413`으로 배포했고 API/Web 새 이미지, EC2 컨테이너와 공개 readiness `UP`을 확인했다.
- B-074 카카오 로그인 단일 노출과 휴대폰 OTP 필수 제거
  - 완료 내용: 고객 로그인 화면에는 카카오만 노출하고 Google/Naver OAuth 백엔드는 기존 계정 호환용으로 유지한다. 카카오 닉네임·이메일 동의를 요청하고 유효·인증된 이메일을 저장하며, 프로필 저장에 배송 연락처를 포함하고 이름, 연락 가능한 이메일, 형식이 유효한 휴대폰 번호만으로 주문 필수 정보를 완료한다.
  - 호환성: 기존 SMS OTP API, 인증 기록, `phone_verified_at`은 삭제하지 않는다. 전화번호가 바뀌면 이전 인증 시각만 초기화하며 checkout은 인증 여부를 요구하지 않는다.
  - 검증: Account/Checkout API 통합 테스트, 전체 API 테스트, Web lint/build, Playwright desktop 로그인 smoke 3건, `git diff --check`.
- B-033 상품 원가/판매가/마진 정책 관리
  - 완료 내용: 공급가와 고객 판매가를 분리하고 active 가격 정책을 관리자에서 관리한다. 기본 판매가는 공급가에만 25%를 적용하고 100원 단위로 반올림하며 공급처 배송비는 더하지 않는다.
  - 운영 반영: 원본 수집 데이터와 운영 상품 300개를 모두 매칭해 과거 배송비 포함 원가 231개를 공급처 상품가 기준으로 복구했다. 전체 상품 중 판매가 329개와 옵션 추가금 46개를 재계산하고 변경 이력을 저장했다.
  - 검증: 운영 DB의 전체 상품과 source metadata가 있는 옵션이 가격 공식과 일치하며, 대상 장갑은 공급가 `3,700원`, 판매가 `4,600원`으로 공개되는 것을 확인했다.
- B-073 고객 상품 목록 서버 페이지네이션
  - 완료 내용: 공개 상품 목록을 전체 배열 조회에서 24개 단위 서버 페이지 조회로 전환했다. 검색어, 카테고리·대분류, 가격, 정렬 조건을 API에서 처리하고 Web은 URL query를 유지하며 이전·다음과 페이지 번호를 표시한다.
  - 정합성: 홈과 관련 상품은 첫 6개만 요청하고, 공개 API 응답은 `{ products, page, size, totalElements, totalPages, categoryCounts }`로 통일했다.
  - 검증: 로컬 실제 데이터 `ACTIVE 308개` 기준 13페이지, 마지막 페이지 20개, 안전모 10개 필터, 가격 정렬, 범위 밖 페이지 보정, Desktop/Mobile overflow 없음, API 테스트와 Web lint/build를 확인했다.

## 2026-07-27

- B-070 KOSHA 보호구 인증 증적 자동 검증
  - 완료 내용: 상세 이미지 OCR에서 인증 문구·번호를 추출하고 공공데이터포털 보호구 인증현황 API에서 번호를 조회한 뒤 상품명·옵션·OCR의 모델명까지 대조한다. 작업발판과 안전난간도 가설기자재 검토 범위에 포함했다.
  - 자동 공개: 공식 모델 일치는 `VERIFIED`, 명시적 비대상 근거나 단순 비인증 품목 규칙은 `NOT_REQUIRED`, 나머지는 `PENDING`으로 분류한다. importer는 상품을 항상 `HIDDEN`으로 완성한 뒤 기존 판매 준비 검증을 통과한 상품만 마지막에 `ACTIVE`로 전환한다.
  - 결과: 수집본 352개에서 인증번호 23개를 공식 조회했고 등록번호 확인 9개, 모델 일치 4개, 취소 인증 1개를 판정했다. 최종 선별은 `IMPORT 11 / EXCLUDE 341`, 현재 자동 공개 대상은 2개이고 인증 대기 9개다.
  - 검증: OCR 산출물 408개 재사용, 공식 API 23건 조회, 인증 감사·리뷰·import self-check, 전체 manifest dry-run과 로컬 관리자 API 실제 적재를 실행했다. 자동 공개 2개는 `saleReady=true`, 인증 대기 상품은 `HIDDEN`으로 확인했다.
- B-071 판매자 후기 기반 상품 자동 선별
  - 완료 내용: 기존 수집본 347개에 이미지를 다시 받지 않고 도매꾹 상품 상세 API의 최근 180일 판매자 후기 수와 만족도를 보강했다. 신규 수집도 같은 값을 저장하며, 후기 10건 미만 또는 만족도 90% 미만은 수동 `REVIEW` 없이 자동 제외한다.
  - 결과: `IMPORT 7 / EXCLUDE 340`이다. 주요 제외 사유는 후기 10건 미만 307개, 만족도 90% 미만 12개다. 판매 종료로 상세 API가 `ITEM_ERROR`를 반환한 2개도 자동 제외했다.
  - 검증: 수집기와 선별기 self-check, 347개 metadata backfill, filtered manifest 재생성, 전체 manifest dry-run, `git diff --check`를 실행했다.
  - 후속 변경: 2026-07-28부터 판매자 후기·만족도는 참고 metadata로만 유지하고 자동 제외 기준에서는 제거했다.

## 2026-07-24

- B-069 도매꾹 Open API 카테고리별 판매 후보 수집
  - 완료 내용: 공식 Open API로 81개 카테고리를 정확도순 30개와 인기순 30개 기준으로 조회하고, 목표 10개 미달 카테고리만 동의어를 추가 조회했다. 상품번호 중복 제거 후 상세 API에서 가격, 배송비, 옵션, 이미지 사용 권한, 원산지·제조사 정보를 보강했다.
  - 안전장치: API 호출 간격을 최소 1초로 제한하고 일일 자체 한도를 5,000회로 설정했다. 날짜별 호출 원장을 `tmp/domeggook-api-usage-YYYY-MM-DD.json`에 기록하며 `429`가 발생하면 재시도하지 않고 즉시 중단한다.
  - 결과: 81개 중 28개 카테고리가 목표 10개를 충족했고 53개는 유효 후보가 부족했다. 수집 후보 347개에서 조건부 배송과 비완제품을 제외하고 브랜드명과 이미지 품질은 허용했다. 카테고리·기성 옵션 정책 자동화 후 `IMPORT 301`, `REVIEW 0`, `EXCLUDE 46`이며 자동 import 후보는 모두 `HIDDEN` 상태다.
  - 검증: 전체 coverage 보고서와 filtered manifest를 재생성하고 347개 manifest dry-run을 완료했다. 이번 작업에서는 DB 적재와 `ACTIVE` 전환을 실행하지 않았다.

## 2026-07-19

- B-068 계좌입금 및 수동 환불 증적 강화
  - 완료 내용: 관리자 입금확인은 실제 입금자명, 실제 입금액, 입금시각, 거래 식별 메모와 사유를 필수로 받고, 실제 입금액이 checkout 총액과 정확히 같을 때만 승인한다. 불일치는 상태나 결제 기록을 바꾸지 않는다.
  - 환불/조회: 수동 계좌환불 완료는 은행명, 계좌번호, 예금주, 이체시각, 거래 식별 메모와 사유를 필수로 저장한다. 관리자 주문 상세는 입금·환불 증적과 주문별 작업 이력을 보여주며, 환불 목록·고객 주문 응답·알림에는 계좌/입금자/거래 메모를 노출하지 않는다.
  - 검증: 입금 증적 누락·미래 시각·금액 불일치·중복 확인, 관리자 권한, 고객 API 비노출, 수동 환불 증적 저장, 주문별 action history를 API 통합 테스트로 검증했다. 전체 API 테스트, PostgreSQL migration smoke, Web lint/build, Playwright 관리자 주문 흐름, `git diff --check`를 실행한다.

## 2026-07-18

- B-067 Toss Payments 미사용 코드와 문서 제거
  - 완료 내용: 고객 Web의 Toss confirm/fail/예외 화면과 helper, 백엔드 confirm·webhook·Toss REST client·결제 예외·PG 환불 호출을 제거했다. 관리자와 고객의 결제 경로는 계좌입금 생성, 관리자 입금확인, 수동 계좌환불만 남긴다.
  - 설정/문서: API와 Web 예제 환경변수, 배포 설정, 공개 정책과 개인정보 처리 항목, API·주문 흐름·아키텍처 문서를 계좌입금 전용으로 정리했다.
  - 호환성: 기존 migration, `TOSS_PAYMENTS` enum과 과거 상태값은 DB 기록 해석을 위해 보존했다. 배포 전에는 production readiness의 legacy payment query로 미처리 과거 데이터를 확인한다.
  - 검증: 전체 API 테스트, Web lint/build, 계좌입금 주문·취소·수동 환불 회귀와 삭제 endpoint의 인증 후 `404` 회귀 테스트를 통과했다.

## 2026-07-17

- B-001 Toss Payments sandbox 결제 플로우 완성 (취소)
  - 결정: Toss Payments와 다른 PG 결제는 도입하지 않고 계좌입금과 관리자 입금확인만 운영 결제 경로로 사용한다.
  - 후속: 기존에 구현된 미사용 Toss 실행 경로와 설정은 B-067에서 제거한다. 과거 결제 데이터 호환에 필요한 enum과 migration은 삭제하지 않는다.

## 2026-07-14

- B-064 관리자 상품 검수 UI와 원본 추적
  - 완료 내용: 상품에 관리자 전용 `sourceUrl`을 추가하고 수집 manifest와 import API 요청으로 전달한다. 판매 준비 상태는 별도 컬럼 없이 판매가, 대표 이미지, 활성 옵션, 활성 상품 고시, 인증 검수를 기준으로 계산하며 관리자 목록·상세에 안정적인 blocker 코드와 체크리스트로 표시한다.
  - 운영 화면: 관리자 목록에 `READY`/`BLOCKED` 서버 필터, 공급가·판매가·원가 대비 인상률·옵션 수·부족 항목·원본 링크를 추가했다. 상세에서는 원본 URL과 대표 이미지를 수정하고, 준비 완료 상품만 기존 상태 API로 개별 `ACTIVE` 전환할 수 있다. 일괄 공개는 범위에서 제외했다.
  - 검증: 카탈로그 통합 테스트와 전체 API 테스트, PostgreSQL migration smoke, Web lint/build, Playwright 개별 검수·원본 링크·ACTIVE 전환 및 전체 Desktop/Mobile suite `64 passed / 24 skipped / 0 failed`, `git diff --check`를 통과했다. Desktop `1440x1000`, Mobile `390x844` 목록·상세 캡처에서 horizontal overflow가 없음을 확인했다.
- B-066 Playwright 판매 가능 fixture와 snapshot 복구
  - 완료 내용: `local/dev` 시작 시 정확한 시드 공급처와 상품 10개를 이름으로 찾아 기존 ID를 유지하면서 상태, 인증 검수, 가격, 대표 이미지 URL과 누락된 옵션·이미지 metadata·상세·고시를 복구한다. 이미지 metadata가 남아 있어도 실제 파일이 없으면 다시 생성하며 비시드 상품은 수정하지 않는다.
  - fixture: 대표 상품을 `K2 안전모 K2-THINK 1`과 `기본` 옵션으로 고정하고 주문 시드와 Playwright가 같은 상품을 사용한다. 시드 상품 10개는 로컬 재시작 시 정의 상태(`ACTIVE` 8, `SOLD_OUT` 1, `HIDDEN` 1)로 돌아간다.
  - 검증: 시드 복구 테스트와 전체 API 테스트, Web lint/build, Desktop/Mobile Playwright 전체 suite `63 passed / 23 skipped / 0 failed`, actual/diff 육안 검토 후 snapshot 6개 갱신, `git diff --check`.
- B-063 관리자 상품 목록 서버 페이징과 필터
  - 완료 내용: 관리자 상품 목록 API를 최신 등록순 서버 페이지 조회로 전환하고 상품명·요약·공급처 검색, 상태, 카테고리, 공급처 필터를 추가했다. Web은 URL query로 필터와 페이지를 유지하며 전체 건수와 이전·다음·페이지 번호를 표시한다.
  - 정합성: 관리자 대시보드는 페이지 응답의 전체 건수를 사용하고, 관리자 상품 상세는 전체 목록을 다시 읽지 않도록 상세 API의 관리자 전용 공급처 정보를 사용한다. 공개 상품 상세에는 공급처와 원가·검수 정보가 노출되지 않는다.
  - 검증: 카탈로그 통합 테스트, 전체 API 테스트, Web lint/build, Playwright 관리자 필터·페이지·모바일 screenshot smoke, `git diff --check`.
- B-056 판매 필수정보 없는 상품의 ACTIVE 전환 차단
  - 완료 내용: 상품 판매가는 0원보다 커야 하고 대표 이미지, 판매 가능한 옵션, 활성 상품 고시가 있어야 하며 인증 검수 상태가 `NOT_REQUIRED` 또는 `VERIFIED`일 때만 `ACTIVE` 전환을 허용한다. 신규 상품은 `HIDDEN` 등록을 기본으로 하고, 활성 상품에서 필수정보를 제거하는 가격·이미지·옵션·인증 변경도 같은 검증으로 차단한다.
  - 데이터 처리: `products.compliance_status`를 추가하고 기존 상품은 `PENDING`, 기존 `ACTIVE` 상품은 검수 증적이 없으므로 `HIDDEN`으로 전환한다.
  - 검증: `CatalogApiIntegrationTest`, Web lint/build, 전체 API 테스트, PostgreSQL migration smoke, `git diff --check`.

## 2026-07-13

- B-040 GitHub Actions Docker build 최적화
  - 완료 내용: API/Web ARM64 image build에 BuildKit GitHub Actions cache를 적용하고 문서-only push의 deploy skip을 유지했다.
  - 검증: cache warm-up 후 `build-and-push`가 기준 `7m39s`에서 `5m43s`로 약 `1m56s` 감소했다.
- B-042 즉시 보안/운영 핫픽스
  - 완료 내용: OAuth redirect 검증, Toss 환불 idempotency, SMS 안전 기본값, deploy concurrency, Docker image prune, container memory 제한을 적용했다.
- B-043 체크아웃 중복 제출 및 주문 상태 경합 방지
  - 완료 내용: 장바구니 잠금과 주문/결제 그룹 낙관적 잠금으로 중복 checkout과 동시 상태 변경 충돌을 막았다.
  - 검증: 중복 checkout과 고객 취소·관리자 액션 경합 회귀 테스트를 추가했다.
- B-037 배포 환경 부하 smoke
  - 완료 내용: 공개 페이지/API를 5 VU, 20 VU로 검증하고 t4g.micro의 응답 시간과 메모리/swap 기준을 기록했다.
- B-038 실오픈 전 성능/보안 baseline 점검
  - 완료 내용: Lighthouse mobile과 OWASP ZAP passive baseline을 기록하고 후속 웹 보안 헤더 작업을 B-058로 완료했다.

## 2026-07-12

- B-060 테스트 환경 EC2 운영시간 스케줄링
  - 완료 내용: EventBridge Scheduler로 `coreable-saf-test` EC2를 매일 `09:00 KST`에 시작하고 `01:00 KST`에 정지하도록 구성했다. Scheduler IAM Role은 해당 인스턴스의 시작·정지만 허용한다.
  - 운영 기준: 야간 정지 전에 완료되도록 DB·업로드 백업 cron을 `03:10`에서 `00:10 KST`로 옮겼다. 자동 정지는 오픈 전 전용이며 실제 주문을 받기 전에 비활성화한다.

## 2026-07-05

- B-058 웹 보안 헤더 hardening
  - 커밋: `test: harden web security headers`
  - 완료 내용: Next.js `next.config.ts`에 웹 보안 헤더를 추가하고 `poweredByHeader`를 껐다. 적용 헤더는 HSTS, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`, CSP다. CSP는 Next.js 인라인 스크립트 때문에 `script-src 'unsafe-inline'`, CSS/폰트는 기존 Pretendard CDN 때문에 `https://cdn.jsdelivr.net`만 최소 허용했다.
  - 검증: 로컬 production `PORT=3001 npm run start` 기준 `curl -I` 헤더 확인, `E2E_WEB_BASE_URL=http://localhost:3001 E2E_API_BASE_URL=http://localhost:8080 npx playwright test --workers=2` 결과 `58 passed / 22 skipped`. 배포 후 `curl -I https://coreable-saf.com`, `E2E_WEB_BASE_URL=https://coreable-saf.com E2E_API_BASE_URL=https://coreable-saf.com npx playwright test deploy-smoke --workers=2` 결과 `8 passed`, ZAP baseline 재실행 결과 `FAIL 0`, `WARN 15 -> 14`.
  - 비고: ZAP의 HTML anti-clickjacking, `X-Powered-By` 노출은 PASS로 바뀌었다. 남은 경고 중 `_next/static` 일부는 Cloudflare에 이전 무헤더 정적 chunk가 HIT로 남은 영향이고, `/uploads/products`는 API 응답이므로 이번 Next hardening 범위 밖이다.
- B-039 AWS EC2 Docker CI/CD 배포
  - 커밋: `test: repair e2e deployment smoke`
  - 완료 내용: `coreable-saf.com` 배포를 GitHub Actions, GHCR, EC2 Docker Compose로 반복 가능하게 구성했다. API/Web Dockerfile, production compose, nginx reverse proxy, EC2 bootstrap, GitHub Actions verify/build/deploy, Cloudflare DNS와 Full(strict) HTTPS 연결을 완료했다.
  - 검증: `15ab8e9` 배포 run 성공, API/Web/Postgres/nginx Up, public/loopback health 정상. E2E drift 수리 후 `E2E_WEB_BASE_URL=https://coreable-saf.com npx playwright test deploy-smoke` 결과 `6 passed`.
- B-016 테스트 배포 및 운영 readiness 점검
  - 커밋: `test: repair e2e deployment smoke`
  - 완료 내용: 배포 서버 env key 목록, 업로드 host volume과 `APP_STORAGE_*` compose 주입, Flyway migration 최신 적용, backup/snapshot 상태, HTTPS, 공개 정책/회사정보/고객센터 경로를 확인했다. 배포 URL 전용 Playwright smoke를 snapshot/auth/seed 의존 없이 분리했다.
  - 검증: 로컬 `npx playwright test --workers=2` 결과 `50 passed / 22 skipped`, 배포 URL `deploy-smoke` 결과 `6 passed`, 배포 URL readiness/visual spec은 snapshot/auth/seed 의존 테스트가 `28 skipped`, 공개 readiness `2 passed`로 실패 없이 종료.

## 2026-07-04

- B-054 수집 상품 필터링 + 실제 데이터 선별 Import
  - 커밋: 미커밋
  - 완료 내용: `scripts/review-domeggook-products.mjs`를 추가해 `tmp/domeggook-products/*/product.json` 761개를 `IMPORT`/`REVIEW`/`EXCLUDE`로 자동 분류했다. 가격/옵션/이미지/카테고리/최소구매수량/배송비/금지·검수 키워드를 기준으로 보수 판정하고, 원본 상세 URL에서 공급처 기본 배송비를 파싱해 `effectiveSourcePrice = 수집 원가 + 배송비`, `calculatedBasePrice = 25% 증액 후 100원 단위 반올림`으로 계산한다. 결과는 `tmp/domeggook-product-review.json`, `tmp/domeggook-product-review.csv`, `tmp/domeggook-import-manifest.filtered.json`에 저장된다.
  - 결정: 자동 공개는 하지 않는다. `IMPORT` 후보도 모두 `HIDDEN` 상태로만 적재하고, 인증/KC/상품고시/이미지/가격 검수 후 관리자에서 수동으로 `ACTIVE` 전환한다. 배송비 조건부, 최소구매수량 2개 이상, 카테고리 확신 낮음, 브랜드/부속품/도메인 이탈 의심 상품은 자동 import하지 않고 `REVIEW`로 남긴다.
  - 검증: `node --check scripts/review-domeggook-products.mjs`, `node scripts/review-domeggook-products.mjs --shipping-concurrency 4` 결과 `IMPORT 127 / REVIEW 574 / EXCLUDE 60`, `node scripts/import-domeggook-products.mjs --manifest tmp/domeggook-import-manifest.filtered.json`, 첫 10개 제한 manifest dry-run, `node scripts/import-domeggook-products.mjs --manifest tmp/domeggook-import-manifest.filtered-first10.json --cookie-file tmp/admin-cookie.txt --apply` 결과 9개 `HIDDEN` 적재 및 관리자 상세에서 대표 이미지, 상세 이미지 블록, 옵션 source metadata 확인
- B-053 도매꾹 전체 카테고리 커버리지 + 기존 수집본 옵션 Backfill
  - 커밋: 미커밋
  - 완료 내용: 기존 수집 산출물의 `sourceUrl`을 다시 조회해 이미지 재다운로드 없이 `optSet`, `optData`, `optSoldOut` 기반 옵션 배열을 backfill하는 모드를 추가했다. 81개 leaf category 기준 coverage scan을 모바일 검색 API로 구현하고, import manifest와 관리자 API 적재를 옵션-aware 가격 계산으로 변경했다. `product_options`에는 원본 옵션코드, 원본 추가금, 원본 재고, 정렬값을 보존하되 public product detail에는 노출하지 않는다.
  - 결정: 원본 재고 수량은 운영 참고값으로만 보존하고 checkout 재고 차감에는 쓰지 않는다. 대량 신규 수집은 도구 검증 후 별도 실행하며, 이번 검증은 5개 backfill과 3개 카테고리 샘플로 제한했다.
  - 검증: `node scripts/collect-domeggook-product.mjs --help`, `node scripts/collect-domeggook-product.mjs --backfill-options --limit 5`, 안전화 샘플 `65522270` 옵션 12개 저장 확인, `node scripts/collect-domeggook-product.mjs --coverage-scan --target-per-category 1 --max-categories 3`, `node scripts/import-domeggook-products.mjs --init-manifest`, `node scripts/import-domeggook-products.mjs --manifest tmp/domeggook-import-manifest.json`, `cd apps/api && ./gradlew test --tests '*Catalog*'`, `cd apps/api && ./gradlew test`, `cd apps/web && npm run lint`, `cd apps/web && npm run build`
- B-051 UX/UI 폴리싱 (리뷰 잔여 항목)
  - 커밋: `feat: polish mobile commerce ux`
  - 완료 내용: 상품 상세 구매 영역을 가격/구매조건/옵션/수량/장바구니/바로구매 순서의 구매 패널로 정리하고 `바로구매`를 primary CTA로 두었다. 모바일 홈/상품목록/관련상품 카드는 390px에서 읽히도록 1열 리스트 밀도로 보정하고, header/search/footer/forms/summary list가 좁은 화면에서 overflow를 만들지 않게 했다. 고객 핵심 form에는 `useFormStatus` 기반 `SubmitButton`을 적용해 제출 중 비활성/문구 피드백을 제공한다. 빈 상태와 API 오류는 기존 `.notice` 안에서 `empty`/`danger`로 구분했다.
  - 결정: 실제 상품 데이터 재수집, SKU/MOQ 필드 추가, 실제 사업자/고객센터 값 보강은 각각 B-085/B-030/후속 API 이슈로 남긴다. 이번 범위는 코드로 해결 가능한 모바일/상태/구매영역 폴리싱만 닫는다.
  - 검증: `cd apps/web && npm run lint`, `cd apps/web && npm run build`, `cd apps/web && npx playwright test --workers=2`, `git diff --check`
- B-050 추천인 코드 수집 (첫 로그인 온보딩)
  - 커밋: `feat: add referral onboarding`
  - 완료 내용: `users`에 `referral_code`, `referred_by_user_id`, `referred_at`을 추가하고 추천 코드를 lazy 생성한다. 신규 소셜 로그인 계정만 `/welcome` 온보딩으로 보내 추천인 코드를 선택 등록하거나 건너뛸 수 있게 했다. 고객 계정 화면에는 내 추천 코드와 추천인 등록 여부만 표시하고, 관리자에는 읽기 전용 추천 관계 목록을 추가했다.
  - 결정: 1차 범위는 추천 관계 기록/추적만이며 적립금, 포인트, 추천 보상 정산은 후속 이슈로 미룬다. 고객 화면에는 추천인의 이름이나 이메일을 노출하지 않는다.
  - 검증: `cd apps/api && ./gradlew test --tests '*AccountReferral*' --tests '*OAuthLogin*'`, `cd apps/web && npm run lint`, `cd apps/web && npm run build`, `cd apps/web && npx playwright test tests/e2e/referral-onboarding.spec.ts`, `cd apps/api && ./gradlew test`, `git diff --check`
- B-049 상품 상세 비로그인 구매 진입 개선
  - 커밋: `feat: improve guest product purchase entry`
  - 완료 내용: 상품 상세에서 비로그인 사용자도 옵션, 수량, 장바구니, 바로구매 버튼을 볼 수 있게 했다. 장바구니 서버 액션 초입에서 `getCurrentUser()`로 세션을 먼저 확인하고, 비로그인 제출은 `/login?redirectTo=/products/{productId}`로 이동시킨다. 게스트 장바구니는 만들지 않는다.
  - 결정: 로그인 후 상품 상세 복귀는 유지하지만, 선택했던 옵션/수량 복원은 이번 범위에서 구현하지 않는다.
  - 검증: `cd apps/web && npm run lint`, `cd apps/web && npm run build`, `cd apps/web && npx playwright test tests/e2e/product-detail-purchase-entry.spec.ts`, `cd apps/web && npx playwright test tests/e2e/visual-regression.spec.ts --project=desktop -g 'desktop product detail'`, `cd apps/api && ./gradlew test`, `git diff --check`
  - 비고: `pnpm lint`는 이 프로젝트가 `package-lock.json` 기반이라 pnpm이 `node_modules`를 재구성하려다 build script 승인 단계에서 실패했다. npm 기준으로 복구 후 검증했다.
- B-048 로컬 개발용 시드 계정 간편 로그인 도구
  - 커밋: `feat: add local seed dev login`
  - 완료 내용: local/dev 프로필과 `app.dev-login.enabled`를 모두 만족할 때만 `/api/dev/login`을 노출한다. 기존 시드 고객/관리자(`local-b003-customer`, `local-b003-admin`)를 찾아 `JwtAccessTokenService`로 JWT를 발급하고, OAuth 로그인과 같은 `ACCESS_TOKEN` HttpOnly cookie 속성을 재사용한다. Playwright E2E helper는 로컬에서 쿠키 env가 없으면 이 엔드포인트로 seed 쿠키를 받아온다.
  - 결정: 이 엔드포인트는 로컬 개발/QA 편의 전용이다. 사용자 생성, 운영 계정 관리, 관리자 권한 정책에는 관여하지 않는다. prod 프로필에서는 `app.dev-login.enabled=true`가 주입돼도 controller bean이 로드되지 않아 404를 반환한다.
  - 검증: `cd apps/api && ./gradlew test --tests '*DevLogin*'`, local API `curl -i http://localhost:8080/api/dev/login?role=CUSTOMER`, local API `curl -i http://localhost:8080/api/dev/login?role=ADMIN`, `cd apps/api && ./gradlew test`, `cd apps/web && npm run lint`, `cd apps/web && npm run build`, `git diff --check`
- B-047 Playwright E2E 페이지 커버리지 확장
  - 커밋: `test: expand playwright page coverage`
  - 완료 내용: E2E 공통 헬퍼를 분리하고 `/login`, `/auth/callback/success` 리다이렉트, 계좌입금 `/checkout/[checkoutNumber]`, 고객 `/orders/[orderId]` 상세/클레임 폼/클레임 상태, `/policies/[slug]`, 상품 검색 빈 상태, 404, 비로그인 관리자 접근 상태를 Playwright로 커버했다. 데스크톱 홈, 상품 상세, 체크아웃 계좌입금, 관리자 주문 상세 스크린샷 baseline을 추가했다.
  - 결정: 클레임 증빙 파일은 실제 업로드 제출까지 E2E에서 수행하지 않고, 파일 input 노출까지만 확인한다. 업로드 저장/검증은 B-015/B-046 API 회귀 테스트와 수동 QA로 분리해 스토리지 상태에 따른 flaky를 피한다.
  - 검증: `cd apps/web && npm run lint`, `cd apps/web && npm run build`, `cd apps/api && ./gradlew test`, `cd apps/web && npm run test:e2e`, 데스크톱 스크린샷 2회 안정성 확인, `git diff --check`

## 2026-07-03

- B-003 관리자 주문 처리 액션을 실제 운영 흐름에 연결하기
  - 커밋: `feat: finalize admin order action smoke`
  - 완료 내용: local/dev 시드에 관리자 주문 검증용 주문 6종(`PAYMENT_PENDING`, `SUPPLIER_ORDER_PENDING`, `SUPPLIER_ORDERED`, `SHIPPED`, `DELIVERED`, `OUT_OF_STOCK`)과 시드 고객/관리자 계정을 추가했다. 관리자 주문 서버 액션은 백엔드 `ApiError` 메시지를 보존해 상태 가드/validation/권한 실패 사유가 배너에 표시되도록 했다. Playwright smoke는 로컬 시드 주문이 없으면 실패하고, 성공 후 상세 갱신과 실패 시 서버 오류 이유 노출을 검증한다.
  - 검증: `cd apps/api && ./gradlew test --tests 'com.dropshipshop.api.dev.LocalOrderSeedDataTest'`, `cd apps/web && npm run lint`, `cd apps/web && npm run build`, `cd apps/api && ./gradlew test`, `E2E_ADMIN_COOKIE=... npx playwright test --project=desktop -g 'admin order action'`, `git diff --check`
- B-011 알림/메일/문자 발송
  - 커밋: `feat: add sms notification dispatch`
  - 완료 내용: 거래 알림을 SMS 우선으로 전환하고, `NotificationLog`가 `PENDING`에서 실제 dispatch 결과에 따라 `SENT`/`FAILED`/`SKIPPED`로 바뀌도록 했다. 기존 인증번호 SMS는 유지하면서 SENS HTTP 호출을 공용 클라이언트로 분리했다. 체크아웃 생성 시 입금대기 안내를 만들고, 주문 관련 알림은 주문 수령인 전화번호로 보낸다. 관리자 알림 조회는 status filter를 지원하며, 실패 알림 retry API와 수동 공급처 지연 안내 액션을 추가했다.
  - 검증: `cd apps/api && ./gradlew test --tests 'com.dropshipshop.api.checkout.CheckoutApiIntegrationTest' --tests 'com.dropshipshop.api.order.AdminOrderApiIntegrationTest' --tests 'com.dropshipshop.api.notification.AdminNotificationApiIntegrationTest'`, `cd apps/api && ./gradlew test`, `cd apps/web && npm run lint`, `cd apps/web && npm run build`, `git diff --check`
- B-015 고객 클레임 조회/증빙/교환 흐름 고도화
  - 커밋: `feat: add claim evidence flow`
  - 완료 내용: 고객 주문별 클레임 목록/상세 API를 추가하고, 상품 하자·오배송·상품 정보와 다름·배송 문제 클레임은 사진 증빙을 필수로 받도록 했다. 증빙 파일은 기존 업로드 검증을 재사용해 이미지 매직 바이트를 확인하고 `claim_evidences` 테이블에 URL/파일명/크기/콘텐츠 타입을 저장한다. 고객 주문 상세는 클레임 목록과 증빙 사진을 표시하고, 관리자 주문 상세도 증빙을 확인할 수 있게 했다.
  - 검증: `cd apps/api && ./gradlew test --tests '*CustomerCancellationApiIntegrationTest'`, `cd apps/api && ./gradlew test`, `cd apps/web && npm run lint`, `cd apps/web && npm run build`, `git diff --check`
- B-014 회원 탈퇴 요청 흐름
  - 커밋: `feat: add account deletion flow`
  - 완료 내용: `users.deleted_at`, `users.anonymized_at` 컬럼을 추가하고, `POST /api/me/deletion-request` 고객 API를 구현했다. 탈퇴 시 개인정보와 소셜 provider user id를 비식별화하고 `ACCESS_TOKEN` 쿠키를 삭제한다. 진행 중 주문/환불/클레임이 있으면 사유와 주문번호를 포함해 400으로 거부한다. 계정 화면에는 되돌릴 수 없는 탈퇴 안내와 확인 체크박스를 추가했다.
  - 검증: `cd apps/api && ./gradlew test --tests '*AccountDeletionApiIntegrationTest' --tests '*OAuthLoginApiIntegrationTest'`, `cd apps/api && ./gradlew test`, `cd apps/web && npm run lint`, `cd apps/web && npm run build`, `git diff --check`
- B-046 sanitizer/업로드 검증 강화
  - 커밋: `feat: harden catalog content validation`, `test: cover webp image upload validation`
  - 완료 내용: 상품 상세 HTML sanitizer를 regex blacklist에서 jsoup safelist로 교체하고, 허용 태그/속성/protocol만 보존하게 했다. 이미지 업로드는 확장자 검증에 더해 `jpg/jpeg`, `png`, `webp` 매직 바이트를 확인하고, `/uploads/products/**` 응답에 `X-Content-Type-Options: nosniff`를 앱 레벨에서 적용했다.
  - 검증: 따옴표 없는 `onerror`, `svg onload`, `javascript:`/`data:` URL, `iframe`, `script` 제거 회귀 테스트 추가. HTML로 위장한 `.png` 업로드 400 거부, 정상 PNG/JPEG/WebP 업로드 성공, 업로드 이미지 nosniff 헤더 회귀 테스트 추가. `cd apps/api && ./gradlew test --tests '*CatalogApiIntegrationTest'`, `cd apps/api && ./gradlew test`, `git diff --check`
  - 비고: B-042~B-046 외부 리뷰 후속 보안/운영 이슈 큐를 모두 완료했다.
- B-045 백업/복구 최소 운영
  - 커밋: `feat: add backup and restore operations`
  - 완료 내용: EC2 local PostgreSQL과 업로드 이미지 백업을 `s3://coreable-backups-prod`로 업로드하는 `/opt/coreable/backup.sh`와 설치 스크립트를 추가했다. `coreable-backup-writer` IAM user는 백업 버킷의 `db/*`, `uploads/*` 읽기/쓰기와 제한된 list 권한만 갖도록 구성했다. EC2 root volume은 `DeleteOnTermination=false`로 바꾸고, DLM weekly snapshot retain 4 정책을 생성했다.
  - 검증: 수동 백업 실행, S3 DB dump와 uploads 91개 확인, EC2 credential hygiene 확인, 임시 PostgreSQL 컨테이너에 최신 dump `pg_restore --no-owner` 복구 리허설 성공, `git diff --check`
- B-044 배송 후 반품/환불 플로우 완성
  - 커밋: `feat: complete delivered return refund flow`
  - 완료 내용: 배송완료 주문의 RETURN 클레임을 승인 후 `RETURN_WAITING`, 반품 수령 후 `RETURN_RECEIVED`, 환불 시작 후 `REFUND_PROCESSING`, 수동 계좌환불 완료 후 `COMPLETED`까지 연결했다. `claims.refund_id`로 환불과 클레임을 연결하고, 관리자 반품 수령/환불 시작/거부 액션과 고객 주문 상세의 반품 진행 상태 표시를 추가했다.
  - 검증: `cd apps/api && ./gradlew test --tests '*CustomerCancellationApiIntegrationTest'`, `cd apps/api && ./gradlew test`, `cd apps/web && npm run lint`, `cd apps/web && npm run build`, `git diff --check`
- B-041 계좌입금 주문/입금확인 플로우 전환
  - 커밋: 미커밋
  - 완료 내용: 고객 체크아웃 주 경로를 Toss 결제창에서 계좌입금 안내로 전환했다. 체크아웃 응답에 입금 계좌, 입금자명, 금액, 기한, 현금영수증 안내를 포함하고, 관리자 주문 화면에 입금대기 필터, 입금확인, 미입금취소, 입금 불일치 메모 액션을 연결했다. 입금확인 시 `BANK_TRANSFER` payment를 만들고 주문을 `SUPPLIER_ORDER_PENDING`으로 넘기며, 수동 환불 완료 액션은 `Refund`, `Payment`, `PaymentGroup`, `OrderStatusHistory`를 함께 갱신한다.
  - 검증: `cd apps/api && ./gradlew test --tests '*Checkout*' --tests '*AdminOrder*' --tests '*Refund*'`, `cd apps/api && ./gradlew test`, `cd apps/web && npm run lint`, `cd apps/web && npm run build`

## 2026-07-02

- B-036 Testcontainers PostgreSQL Smoke 도입
  - 커밋: 미커밋
  - 완료 내용: 기존 H2 통합 테스트는 유지하면서 PostgreSQL 17 Testcontainers smoke를 추가했다. Flyway migration, JPA `ddl-auto=validate`, readiness endpoint, public catalog `sourcePrice` 미노출, active pricing policy seed를 확인한다.
  - 검증: `cd apps/api && ./gradlew test --tests '*Postgres*Smoke*'`, `cd apps/api && ./gradlew test`, `git diff --check`
- B-034 Playwright 기반 배포 전 UI Smoke 도입
  - 커밋: 미커밋
  - 완료 내용: `apps/web`에 Playwright smoke를 도입하고 desktop/mobile Chromium 기준 공개 고객 화면, 로그인 고객 화면, 관리자 화면, 정책/고지 화면 접근과 horizontal overflow를 확인하도록 했다. 인증 쿠키가 없으면 auth smoke는 skip된다.
  - 검증: `cd apps/web && npm run lint`, `cd apps/web && npm run build`, `cd apps/web && npm run test:e2e`, `git diff --check`

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
  - 완료 내용: 프론트 `/api/**` 호출과 백엔드 controller/DTO, `docs/api-spec.md`를 대조해 API path/method, 권한, DTO, 오류 처리 계약을 점검했다. 후속 정리 항목은 B-024로 분리했다.
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
