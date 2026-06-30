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
- 배송 안내
- AS 안내
- 반품/교환 안내

## Operating Order

1. 처음에는 10~20개만 등록한다.
2. 대표 이미지는 정사각형으로 맞춘 뒤 업로드한다.
3. 상세 이미지는 16:9로 맞춘 뒤 상세 이미지 블록으로 추가한다.
4. `/products`, `/products/{productId}`, `/cart`에서 이미지와 가격 표시를 확인한다.
5. 이상 없으면 다음 상품 묶음을 등록한다.
