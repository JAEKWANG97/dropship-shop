# Product Registration Guide

초기 판매 상품을 빠르게 등록하기 위한 운영 기준이다. 실제 상품 데이터는 코드에 하드코딩하지 않고 관리자 화면에서 등록한다.

## Image Rules

- 대표 이미지: 1:1 정사각형, 1200x1200px, webp 권장
- 상세 이미지 블록: 16:9, 1600x900px 또는 1920x1080px, webp 권장
- 허용 파일: jpg, jpeg, png, webp
- 최대 용량: 5MB
- 파일명 예시:
  - `ppe-safety-helmet-k2-think-thumb.webp`
  - `ppe-safety-helmet-k2-think-detail-01.webp`

## Product Checklist

- 상품명
- 카테고리
- 공급처
- 기본가
- 옵션명과 추가금액
- 판매 상태
- 대표 이미지와 대체 텍스트
- 상세 이미지 또는 HTML 상세 블록
- 상품 고시
- 안전인증, KC 또는 인증 대상 여부
- 인증번호 또는 인증서 보관 위치
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
4. 안전모, 안전화, 안전대, 마스크, 보안경 같은 보호구는 인증서, 인증번호, KC 또는 안전인증 대상 여부를 먼저 확인한다.
5. 상품 고시에 제조사/수입자, 원산지, 재질, 규격, 인증번호, AS/반품 기준을 입력한다.
6. `/products`, `/products/{productId}`, `/cart`에서 이미지와 가격 표시를 확인한다.
7. 이상 없으면 다음 상품 묶음을 등록한다.

## Domeggook Collection

도매꾹 상품은 자동 등록하지 않고 수동 검수용으로만 수집한다.

```bash
node scripts/collect-domeggook-product.mjs https://mobile.domeggook.com/8667274
node scripts/collect-domeggook-product.mjs --file tmp/domeggook-urls.txt
```

- 결과는 `tmp/domeggook-products/{상품번호}/`에 저장된다.
- `이미지사용` 값이 `허용`인 상품만 대표 이미지와 상세 이미지를 다운로드한다.
- 수집 후 상품명, 가격, 카테고리, 인증/KC, 상품고시, 이미지 품질을 확인한다.
- 이미지는 필요한 크기로 수동 보정한 뒤 관리자 화면에서 업로드한다.

## Domeggook Import

도매꾹 수집 상품은 관리자 API를 통해 `HIDDEN` 상태로만 먼저 적재한다.

```bash
node scripts/import-domeggook-products.mjs --init-manifest
node scripts/import-domeggook-products.mjs --manifest tmp/domeggook-import-manifest.json
node scripts/import-domeggook-products.mjs --manifest tmp/domeggook-import-manifest.json --cookie-file tmp/admin-cookie.txt --apply
```

- `tmp/domeggook-import-manifest.json`에서 `import`, `categoryCode`, `summary`, `basePrice`를 먼저 확인한다.
- 생성된 manifest는 기본적으로 `import: false`, `status: "HIDDEN"`이다.
- `ACTIVE` 전환은 관리자 화면에서 상품 고시, 인증/KC, 가격, 이미지 품질을 확인한 뒤 진행한다.
- import 결과는 `tmp/domeggook-import-result.json`에 저장된다.
