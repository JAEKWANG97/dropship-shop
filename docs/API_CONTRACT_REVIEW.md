# API Contract Review

## 2026-06-30 B-023

프론트엔드 `/api/**` 호출, 백엔드 controller/DTO, `docs/api-spec.md`를 대조했다.

## Verdict

- P0 없음: 프론트가 호출하는 API path/method는 모두 백엔드에 존재한다.
- 쿠키가 필요한 호출은 `apiGetWithCookie` 또는 `apiSendWithCookie`를 사용하고 있다.
- 상품 카테고리 enum은 프론트 `PRODUCT_CATEGORIES`와 백엔드 `ProductCategory`가 81개 모두 일치한다.
- 관리자 상품 이미지 업로드는 multipart 예외로 직접 `fetch`를 사용해 JSON helper를 피하고 있다.

## Findings

> B-024에서 아래 후속 정리를 반영했다. 이 문서는 B-023 검토 당시 발견 내역을 보관한다.

### P1: 관리자 계정의 고객 주문 화면 권한 처리가 장바구니/체크아웃과 다르다

- 화면/파일: `apps/web/src/app/orders/page.tsx`, `apps/web/src/app/orders/[orderId]/page.tsx`
- 프론트 호출: `GET /api/orders`, `GET /api/orders/{orderId}`
- 백엔드 API: `CustomerOrderController`, `CustomerClaimController`는 `CUSTOMER` 권한 API
- 문제: 관리자 로그인 상태에서 `/orders`는 `403`을 빈 주문 목록처럼 보여준다. `/orders/{orderId}`는 같은 유형의 `403`을 API 장애처럼 보여줄 수 있다.
- 영향: 운영자가 고객 주문 화면을 열었을 때 권한 제한과 실제 주문 없음/API 장애를 구분하기 어렵다.
- 최소 수정 방향: `/cart`, `/checkout`처럼 `getAdminUser()`를 함께 확인하고 관리자 계정이면 “관리자 계정은 고객 주문 기능을 사용할 수 없습니다” 안내와 `상품 보기`, `관리자 홈` CTA를 표시한다.

### P1: `docs/api-spec.md`의 고객 취소/클레임 계약이 실제 구현과 어긋난다

- 화면/파일: `docs/api-spec.md`
- 프론트 호출: `POST /api/orders/{orderId}/cancel`, `POST /api/orders/{orderId}/claims`
- 백엔드 API: `CustomerClaimController`의 `CUSTOMER` 권한, 둘 다 구현됨
- 문제: `POST /api/orders/{orderId}/cancel`이 한 섹션에서는 `Planned`, 다른 섹션에서는 `Implemented`로 중복 기록되어 있고, auth가 `Authenticated user`로 적혀 있다.
- 영향: 후속 구현자가 관리자나 일반 인증 사용자도 호출 가능한 API로 오해할 수 있다.
- 최소 수정 방향: checkout/order 섹션과 refund/claim 섹션의 상태를 `Implemented`, auth를 `CUSTOMER`로 통일한다. 중복 행은 하나만 남기거나 참조 문구로 정리한다.

### P2: 약관 동의 POST 응답 타입 선언이 실제 응답과 다르다

- 화면/파일: `apps/web/src/app/checkout/actions.ts`
- 프론트 호출: `POST /api/me/agreements`
- 백엔드 API: `AccountAgreementDtos.AgreeResponse`
- 문제: 프론트가 `apiSendWithCookie<AgreementState>`로 호출하지만 백엔드는 `agreementId`, `termsVersion`, `privacyVersion`, `agreedAt` 형태를 반환한다.
- 영향: 현재 응답을 사용하지 않아 런타임 문제는 없지만 타입 계약이 잘못 남아 있다.
- 최소 수정 방향: 응답을 쓰지 않는다면 generic을 제거하거나 `AgreeResponse` 타입을 별도로 선언한다.

### P2: 옵션 생성 사유는 프론트에서 보내지만 변경 이력에는 남지 않는다

- 화면/파일: `apps/web/src/app/admin/products/[productId]/actions.ts`, `CatalogService.createOption`
- 프론트 호출: `POST /api/admin/products/{productId}/options`
- 백엔드 API: `ProductOptionRequest.reason`은 optional이고 `createOption`은 admin id를 받지 않는다.
- 문제: 옵션 추가 form은 사유를 입력받지만 백엔드는 옵션 생성 이력을 기록하지 않는다.
- 영향: 운영자가 “옵션 추가 사유도 이력에 남는다”고 기대할 수 있다.
- 최소 수정 방향: MVP에서는 사유 입력을 제거하거나, 후속으로 create option API/service가 admin id와 변경 이력을 기록하도록 정한다.

### P2: 이미지 업로드 구현 상태 문서 문구가 일부 오래됐다

- 화면/파일: `docs/api-spec.md`
- 프론트 호출: `POST /api/admin/products/{productId}/images/upload`
- 백엔드 API: `AdminCatalogController.uploadImage`
- 문제: endpoint 표는 `Implemented`인데 DS-6 notes에는 “Image binary upload can remain planned”가 남아 있다.
- 영향: 실제 구현 상태를 빠르게 확인할 때 혼란이 생긴다.
- 최소 수정 방향: DS-6 notes에서 해당 문구를 현재 구현 상태에 맞게 수정한다.

## Verification

- `cd apps/web && npm run lint`
- `cd apps/web && npm run build`
- `cd apps/api && ./gradlew test`
- `git diff --check`
- Chrome 샘플 확인:
  - `/products`, `/products/{id}` 정상
  - `/account` 정상
  - `/cart`, `/checkout` 관리자 계정 권한 안내 정상
  - `/orders` 관리자 계정에서 빈 주문 목록으로 표시됨
  - `/admin/products`, `/admin/products/{id}`, `/admin/orders` 정상
