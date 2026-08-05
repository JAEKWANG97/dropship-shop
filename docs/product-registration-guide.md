# Product Registration Guide

초기 판매 상품을 빠르게 등록하기 위한 운영 기준이다. 실제 상품 데이터는 코드에 하드코딩하지 않고 관리자 화면에서 등록한다.

## Image Rules

- 대표 이미지: 1:1 정사각형, 1200x1200px, webp 권장
- 상세 이미지 블록: 16:9, 1600x900px 또는 1920x1080px, webp 권장
- 허용 파일: jpg, jpeg, png, webp
- 최대 용량: 10MB
- 파일명 확장자와 실제 이미지 파일 시그니처가 모두 맞아야 업로드된다.
- 파일명 예시:
  - `ppe-safety-helmet-k2-think-thumb.webp`
  - `ppe-safety-helmet-k2-think-detail-01.webp`

## Product Checklist

- 상품명
- 카테고리
- 공급처
- 공급가
- 판매가
- 최소주문수량과 주문단위
- 옵션명과 추가금액
- 판매 상태
- 대표 이미지와 대체 텍스트
- 상세 이미지 또는 HTML 상세 블록
- 상품 고시
- 안전인증, KC 또는 인증 대상 여부
- 인증번호 또는 인증서 보관 위치
- 인증 검수 상태: 검수 전 / 인증 비대상 / 인증 확인 완료 / 판매 불가
- 제조사 또는 수입자
- 원산지
- 재질과 규격
- 사용상 주의사항
- 배송 안내
- AS 안내
- 반품/교환 안내

## Operating Order

1. 처음에는 10~20개만 등록한다.
2. 대표 이미지는 정사각형으로 맞춘 뒤 업로드한다.
3. 상세 이미지는 16:9로 맞춘 뒤 상세 이미지 블록으로 추가한다.
4. 공급처가 제공한 인증서나 인증번호가 있으면 관리자 인증 상태에 기록한다.
5. 상품 고시에 제조사/수입자, 원산지, 재질, 규격, 인증번호, AS/반품 기준을 입력한다.
6. 공급가는 운영자 전용으로 입력하고, 판매가는 가격 정책 기준 계산가를 적용한 뒤 필요하면 수동 조정한다. 최소주문수량과 주문단위는 공급처 조건대로 1~99 범위에서 입력한다.
7. 인증 비대상은 `NOT_REQUIRED`, 인증 확인 완료 상품은 `VERIFIED`, 미확인은 `PENDING`으로 기록한다.
8. 판매가, 대표 이미지, 판매 가능한 옵션, 상품 고시를 확인한 뒤 `ACTIVE`로 전환한다. `PENDING`은 공개를 차단하지 않는다.
9. `/products`, `/products/{productId}`, `/cart`에서 이미지와 가격 표시를 확인한다.
10. 이상 없으면 다음 상품 묶음을 등록한다.

## Sale Catalog Audit

추천 상품 후보는 관리자 상품을 자동 감사해 실제 정보가 있는 상품만 사용한다.

```bash
node scripts/audit-sale-catalog.mjs --cookie-file tmp/admin-cookie.txt
```

- 결과: `tmp/sale-catalog-audit.json`, `tmp/sale-catalog-audit.csv`
- 자동 차단: 판매 준비 조건 미충족, 공급처 상품번호 누락·중복, 모델명·제조사·원산지·배송·반품 항목 누락
- 상품명은 길이, 키워드 수, 다른 상품과의 이름 중복을 이유로 차단하거나 정제하지 않는다. 중복 상품은 공급처 상품번호로 판정한다.
- `상세정보 별도표기`, `해당없음`, `1 / 1`, `0x0x0 / 0g` 등 상품 페이지에 표시된 값은 공급처 표시값으로 취급하고 판단하거나 치환하지 않는다.
- 경고만 기록: 인증 `PENDING`. 기존 정책대로 판매 자체를 차단하지 않으며 상품 상세에는 `상품 정보 확인 필요`로 표시한다.
- 감사 도구는 상품 정보를 추측하거나 자동 생성하지 않는다.

## Domeggook Collection

도매꾹 상품은 자동 수집·선별하며 상품별 수동 `REVIEW` 큐를 만들지 않는다.

상품 페이지에서 확인되는 상품 정보를 원문 그대로 수집·등록한다.

- 수집·등록: 상품명, 공급가, 옵션, 재고, 최소수량, 대표·상세 이미지, 상세설명, 원산지, 모델명, 제조사, 부피·무게, 인증정보, 상품정보제공고시, 배송 방법·예정일·배송비
- 수집본 보존: 거래조건, 공급사 사업자 정보, 공급처 반품·교환 정보와 원문 배송·약관 데이터
- 제외: 도매꾹 화면 구성·브랜드 문구, 후기·문의, 개인정보, 로그인 계정별 정보, 광고·추적 데이터
- 공급처가 이미지 사용을 허용한 상품만 이미지와 상세설명을 사용한다.
- 공급처 원문과 별도로 고객 계약에 적용되는 코어러블 A/S·반품·교환 정책을 표시한다.
- 공급가는 원문 그대로 `sourcePrice`에 저장하고 고객 판매가 `basePrice`는 코어러블 가격 정책으로 계산한다.
- 공급처 카테고리는 원문으로 보존하되 고객 상품 카테고리는 코어러블 카테고리로 매핑한다.

```bash
node scripts/collect-domeggook-product.mjs https://mobile.domeggook.com/8667274
node scripts/collect-domeggook-product.mjs --file tmp/domeggook-urls.txt
node scripts/collect-domeggook-product.mjs --backfill-options --limit 5
node scripts/collect-domeggook-product.mjs --backfill-seller-score --limit 5
node scripts/collect-domeggook-product.mjs --coverage-scan --target-per-category 1 --max-categories 3
node scripts/collect-domeggook-product.mjs --open-api-coverage --category PPE_SAFETY_HELMET --target-per-category 2
node scripts/collect-domeggook-product.mjs --open-api-coverage --target-per-category 30
node --env-file=.env scripts/collect-domeggook-product.mjs --open-api-coverage --expanded-keywords --target-per-category 60
node --env-file=.env scripts/collect-domeggook-product.mjs --source-category-discovery --target-per-category 60
node --env-file=.env scripts/collect-domeggook-product.mjs --open-api-refresh
node scripts/audit-domeggook-kosha-certifications.mjs --run-ocr
node --env-file=.env scripts/audit-domeggook-kosha-certifications.mjs --run-ocr
node --env-file=.env scripts/audit-domeggook-kosha-certifications.mjs
```

- 결과는 `tmp/domeggook-products/{상품번호}/`에 저장된다.
- 공공데이터포털 키는 `.env`의 `DATA_GO_KR_SERVICE_KEY_DECODED`, `DATA_GO_KR_SERVICE_KEY_ENCODED`에 저장한다. 감사 도구는 디코딩 키를 먼저 사용하고 인증 실패 시 인코딩 키로 한 번 재시도한다.
- 공식 Open API 수집은 로컬 `.env`의 `DOMEGGOOK_OPEN_API_KEY`를 사용하며 key를 산출물이나 git에 남기지 않는다.
- `--open-api-coverage`는 카테고리별 도매꾹랭킹순(`rd`) 60개를 한 번 조회하고 상품번호 중복을 제거한다. 목표 30개 미달도 확보된 상품만 사용하고 PASS 처리한다.
- `--expanded-keywords`는 승인된 26개 판매 카테고리에 구체 검색어를 순서대로 보충 적용한다. 기존 수집본을 먼저 세어 카테고리당 총 60개까지만 채우고 미달은 확보 수량으로 PASS 처리한다.
- `--source-category-discovery`는 `docs/domeggook-reference-items.txt`의 참조 상품에서 공급처 원본 카테고리를 찾고, 각 원본 카테고리의 도매꾹랭킹순 후보를 지정 상한까지 수집한다. 장갑에 한정하지 않으며 새 참조 링크를 파일에 추가하면 같은 방식으로 상품군을 확장한다.
- 참조 상품은 자동 등록 예외가 아니다. 고정 키워드와 코어러블 카테고리에 매핑되지 않아도 `REVIEW_CANDIDATE`로 수집해 `tmp/domeggook-source-discovery/` 보고서에 남기고, 배송·MOQ·이미지·판매 상태 검증은 기존 기준을 그대로 적용한다.
- 원본 카테고리 탐색으로 수집된 미분류 상품은 운영자가 코어러블 판매 카테고리를 확정하기 전까지 import하지 않는다. 공급처 카테고리만으로 고객 카테고리를 자동 생성하거나 확정하지 않는다.
- 목록 조회 조건은 도매꾹랭킹순(`rd`)과 도매매 최대 주문단위 `mxq=10`을 사용한다. 기본 모드는 보충 검색을 하지 않으며, `--expanded-keywords`에서 승인된 구체 검색어만 추가 조회한다. 어느 모드도 조건 완화는 하지 않는다.
- 상세 조회에서 `channel.supply=true`, 개당 공급가 `price.supply`, 도매매 주문단위 `qty.supplyUnit`을 확인한다. `supplyUnit`을 고객 최소수량과 주문단위로 저장하며 1~10만 판매 후보로 허용한다.
- `qty.domeMoq`는 도매꾹 채널 최소수량이므로 도매매 고객 제약으로 사용하지 않는다. `qty.supplyLoq`는 공급처 최대수량 원문으로 구분해 보존한다.
- 상세 조회에서 판매 상태, 배송비, 이미지, 옵션, 카테고리와 인증 정보를 검증한다.
- 상품정보, 상품정보제공고시, 거래조건, 공급사와 반품 정보는 Open API 원문을 수집본에 보존한다. 고객 공개 상품에는 상품정보제공고시 행만 등록한다.
- 분당 호출 제한을 피하기 위해 호출 간격을 최소 1초로 강제하고 일일 자체 한도는 5,000회로 제한한다.
- 호출 횟수는 `tmp/domeggook-api-usage-YYYY-MM-DD.json`에 기록한다. `429` 응답이면 재시도하지 않고 실행을 중단한다.
- 기존 수집본은 `--backfill-seller-score`로 이미지를 다시 받지 않고 최근 180일 판매자 후기 수와 만족도만 보강한다. 완료 상품은 재실행 시 건너뛴다.
- 판매자 후기 수와 만족도는 판매자 단위 참고 metadata로만 저장하며 자동 수집·공개 제외 기준으로 사용하지 않는다.
- 진행 보고서는 상품마다 갱신한다. 중단 후 같은 명령을 다시 실행하면 완료 상품번호와 완료 카테고리를 건너뛰고 미완료 카테고리부터 이어간다. 필터 변경 후 전체 재수집이 필요할 때만 `--fresh`를 사용한다.
- Open API coverage 결과는 `tmp/domeggook-open-api-coverage/`에 저장된다.
- `이미지사용` 값이 `허용`인 상품만 대표 이미지와 상세 이미지를 다운로드한다.
- 이미지 파일 크기와 해상도는 자동 수집 제외 기준으로 사용하지 않는다. 이미지가 없거나 다운로드되지 않은 상품만 제외한다.
- 최소구매수량 1~10 상품을 수집한다. 10 초과 상품은 `MIN_ORDER_QUANTITY_GT_10` 사유로 제외한다.
- 고객이 별도 부품을 조립하거나 추가 구매하지 않아도 사용할 수 있는 완제품만 수집한다. 교체용·리필·호환품·부속품·내피·턱끈·패드 같은 단품 부속은 자동 제외한다.
- 무료배송 또는 금액이 확정된 고정 선결제 배송 상품만 수집한다.
- 수량별 비례·차등 배송비, 착불, 선불·착불 선택 상품은 고객 무료배송 가격을 확정할 수 없으므로 자동 제외한다.
- 공식 Open API의 공급처 카테고리는 참고값으로 저장한다. 공급처 오분류가 있으므로 이 값만으로 상품을 제외하거나 우리 카테고리를 확정하지 않는다.
- 수집 목표 카테고리의 명시적 키워드가 상품명·옵션에 있고 자동 분류가 다른 카테고리와 충돌하지 않으면 수집 목표를 확정 카테고리로 사용한다.
- 전체식·상체식·그네식·하네스는 안전대, 주상용·허리·둔부·벨트형은 안전벨트로 분류하며 먼저 명시된 형태를 우선한다.
- 바리케이드 명시는 안전휀스보다 우선한다. 산소·O2, 개구부 덮개, 구급함·키트·파우치처럼 더 구체적인 용도는 일반 가스측정기·추락방지망·응급처치용품보다 우선한다.
- 주차금지, 층간소음, 생활소음, 인쇄형이라는 표현만으로 안전용품을 검수 대상으로 만들지 않는다.
- 주문인쇄·맞춤·로고·문구선택·시안 옵션은 `STOPPED`로 적재한다. 기성 옵션이 없으면 상품을 자동 제외한다.
- 기존 수집본은 `--backfill-options`로 이미지를 다시 받지 않고 옵션 정보만 보강한다.
- 전체 카테고리 후보는 `--coverage-scan`으로 카테고리별 검색 후보와 부족 카테고리 리포트를 만든다.
- 수집 선별에서 확정할 수 없는 가격, 카테고리, 배송, 옵션 조건은 `REVIEW`로 보내지 않고 자동 제외한다.
- 보호구 인증 감사 결과는 `tmp/domeggook-kosha-cert-audit.json`과 CSV로 저장한다.
- `review-domeggook-products.mjs`는 감사 결과를 자동으로 읽고 인증 미확인 상품은 compliance `PENDING`으로 기록한다.
- 원본의 인증번호는 공공데이터포털 보호구 인증현황 API에서 정확히 일치하는 번호를 조회하고, 공식 등록 모델과 판매 모델까지 일치해야 인증 확인 완료로 본다.
- 인증 증적이 없거나 공식 조회가 실행되지 않은 상품도 다른 판매 준비 조건을 충족하면 공개할 수 있다.
- `KCS 인증제품이 아님`과 `위험 작업 현장 사용 금지`를 명시한 경작업모는 산업용 안전모 수집 대상에서 제외한다.

## Domeggook Import

도매꾹 수집 상품은 자동 리뷰로 걸러낸 뒤 관리자 API로 먼저 `HIDDEN` 상태로 완성한다. 상품 고시와 인증 상태까지 저장한 후 판매 준비 조건을 충족한 상품을 마지막 단계에서 자동으로 `ACTIVE` 전환한다. 인증 `PENDING`은 공개 차단 조건이 아니다.

```bash
node scripts/import-domeggook-products.mjs --init-manifest
node scripts/review-domeggook-products.mjs
node scripts/review-domeggook-products.mjs --api http://localhost:8080 --cookie-file tmp/admin-cookie.txt
node scripts/import-domeggook-products.mjs --manifest tmp/domeggook-import-manifest.json
node scripts/import-domeggook-products.mjs --manifest tmp/domeggook-import-manifest.filtered.json
node scripts/import-domeggook-products.mjs --manifest tmp/domeggook-import-manifest.json --cookie-file tmp/admin-cookie.txt --apply
node scripts/import-domeggook-products.mjs --manifest tmp/domeggook-import-manifest.filtered.json --cookie-file tmp/admin-cookie.txt --apply
```

- `tmp/domeggook-import-manifest.json`에서 `import`, `categoryCode`, `summary`, `sourcePrice`, `basePrice`, `minimumOrderQuantity`, `orderQuantityStep`, `options`를 먼저 확인한다.
- 생성된 manifest는 기본적으로 `import: false`, `status: "HIDDEN"`이다.
- 자동 선별 결과는 `IMPORT`, `EXCLUDE`로만 나뉜다.
- `IMPORT` 대상만 `tmp/domeggook-import-manifest.filtered.json`에서 `import: true`가 된다.
- `EXCLUDE` 상품은 가격 없음, 이미지 사용 미허용, 활성 옵션 없음, 상세 이미지 없음, 카테고리 불명확, 원산지·제조사 누락, 명백한 비안전용품, 고객 노출 금지 키워드, 최소구매수량 10개 초과, 조건부·수량별·착불 배송 상품이다.
- `basePrice`는 기본 가격 정책 기준으로 `sourcePrice`를 25% 증액하고 100원 단위로 반올림한 값이다.
- `tmp/domeggook-import-manifest.filtered.json`의 `sourcePrice`는 공급처 상품가만 사용한다. 공급처 배송비는 판매가 계산에 더하지 않는다.
- 옵션이 있는 상품은 옵션별 원본 공급가(`sourcePrice + sourceAdditionalPrice`)에 가격 정책을 적용하고, 가장 낮은 옵션 판매가를 상품 `basePrice`로 둔다. 각 옵션의 `additionalPrice`는 `옵션 판매가 - basePrice`로 계산한다.
- 공급처 `price.resale.minimum`이 있으면 100원 단위로 올림한 금액과 계산 판매가 중 큰 값을 사용한다.
- `sourceOptionCode`, `sourceAdditionalPrice`, `sourceStockQuantity`, `sortOrder`는 관리자 검수용 메타데이터이며 고객 화면에는 노출하지 않는다.
- 숫자 가격이 없는 상품은 자동 제외한다.
- filtered manifest는 공급처 상품 페이지의 상품정보제공고시 행을 새로 조합하거나 치환하지 않고 그대로 등록한다. 거래조건과 공급사 정보는 수집본에만 보존하고, 고객에게는 코어블SAF 배송·반품 정책을 별도로 표시한다.
- 공식 등록 모델 일치는 `VERIFIED`, 명시적 인증 비대상 근거나 관리되는 단순 비인증 품목은 `NOT_REQUIRED`, 나머지는 `PENDING`으로 기록한다.
- importer는 생성 요청에 항상 `HIDDEN`을 사용한다. 옵션·대표 이미지·상세 이미지·상품 고시 저장이 모두 성공한 뒤 manifest 목표 상태가 `ACTIVE`인 상품만 상태 API로 공개한다.
- 같은 `sourceItemNo` 상품이 이미 있으면 새 상품을 만들지 않고 원문 상품명·가격·옵션·고시를 갱신한다. 공급처에서 사라진 옵션은 `STOPPED`로 바꾼다. 상세 이미지 블록은 backend 허용 한도인 앞 50개까지 저장한다.
- 인증 상태가 명시적으로 `REJECTED`인 상품만 자동 공개하지 않는다.
- import 결과는 `tmp/domeggook-import-result.json`에 저장된다.
- 대량 적재 전에는 filtered manifest를 10개 정도로 제한한 임시 manifest를 만들어 먼저 `--apply` 한다.
