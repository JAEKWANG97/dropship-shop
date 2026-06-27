# Domain Model

## Core Entities

```text
User
SocialAccount
Product
ProductImage
ProductOption
ProductDetailBlock
Supplier
Cart
CartItem
Order
OrderItem
Payment
Fulfillment
Shipment
Refund
```

## User

고객 또는 관리자를 나타낸다.

Suggested fields:

- id
- email
- name
- phone
- role: CUSTOMER / ADMIN
- status: ACTIVE / SUSPENDED / DELETED
- createdAt
- updatedAt

## SocialAccount

카카오, 구글, 네이버 소셜 로그인 식별 정보를 나타낸다.

Suggested fields:

- id
- userId
- provider: KAKAO / GOOGLE / NAVER
- providerUserId
- email
- displayName
- createdAt
- updatedAt

## Supplier

상품을 실제로 출고하는 공급처.

Suggested fields:

- id
- name
- contactName
- phone
- email
- memo
- status: ACTIVE / INACTIVE
- createdAt
- updatedAt

## Product

판매 상품. 실제 재고 수량을 보장하지 않는다.

Suggested fields:

- id
- supplierId
- name
- summary
- description
- basePrice
- status: ACTIVE / SOLD_OUT / HIDDEN / STOPPED
- thumbnailImageUrl
- createdAt
- updatedAt

## ProductImage

상품 대표 이미지와 갤러리 이미지를 나타낸다.

Suggested fields:

- id
- productId
- type: THUMBNAIL / GALLERY
- imageUrl
- sortOrder
- altText
- createdAt
- updatedAt

## ProductOption

색상, 사이즈, 구성 같은 구매 단위.

Suggested fields:

- id
- productId
- name
- additionalPrice
- status: ACTIVE / SOLD_OUT / STOPPED
- createdAt
- updatedAt

## ProductDetailBlock

상품 상세 콘텐츠를 구성하는 블록. 공급처 상세 이미지와 관리자 HTML 설명을 순서대로 노출하기 위해 사용한다.

Suggested fields:

- id
- productId
- type: IMAGE / HTML
- imageUrl
- htmlContent
- sortOrder
- altText
- createdAt
- updatedAt

## Cart

고객의 장바구니.

Suggested fields:

- id
- userId
- createdAt
- updatedAt

## CartItem

장바구니의 상품 옵션 항목.

Suggested fields:

- id
- cartId
- productId
- productOptionId
- quantity
- createdAt
- updatedAt

## Order

고객 주문의 중심 엔티티.

Suggested fields:

- id
- orderNumber
- userId
- status
- recipientName
- recipientPhone
- postalCode
- address1
- address2
- subtotalAmount
- shippingFee
- discountAmount
- totalAmount
- createdAt
- updatedAt

Suggested statuses:

- PAYMENT_PENDING
- SUPPLIER_ORDER_PENDING
- SUPPLIER_ORDERED
- OUT_OF_STOCK
- PREPARING_SHIPMENT
- SHIPPED
- DELIVERED
- CANCEL_REQUESTED
- CANCELLED
- REFUND_REQUESTED
- REFUNDED

## OrderItem

주문에 포함된 상품 옵션. 주문 시점의 이름과 가격을 스냅샷으로 보관한다.

Suggested fields:

- id
- orderId
- productId
- productOptionId
- productName
- optionName
- unitPrice
- quantity
- lineAmount
- supplierId
- status
- createdAt
- updatedAt

## Payment

PG 결제 기록.

Suggested fields:

- id
- orderId
- provider
- providerPaymentKey
- method
- status: READY / APPROVED / FAILED / CANCELLED / REFUNDED
- requestedAmount
- approvedAmount
- approvedAt
- createdAt
- updatedAt

## Fulfillment

공급처 발주 처리 상태.

Suggested fields:

- id
- orderId
- supplierId
- status: PENDING / ORDERED / OUT_OF_STOCK / CANCELLED
- orderedAt
- memo
- createdAt
- updatedAt

## Shipment

배송 정보.

Suggested fields:

- id
- orderId
- carrier
- trackingNumber
- status: READY / SHIPPED / DELIVERED
- shippedAt
- deliveredAt
- createdAt
- updatedAt

## Refund

취소 또는 환불 기록.

Suggested fields:

- id
- orderId
- paymentId
- reason
- status: REQUESTED / APPROVED / REJECTED / COMPLETED
- refundAmount
- requestedAt
- completedAt
- createdAt
- updatedAt

## Modeling Notes

- 상품과 옵션에는 실제 재고 수량을 두지 않는다.
- 상품 전체 상태와 상품 옵션 상태를 분리한다.
- 고객이 구매할 수 있는 조건은 상품 상태가 `ACTIVE`이고 옵션 상태도 `ACTIVE`인 경우다.
- 상품이 `ACTIVE`여도 특정 옵션이 `SOLD_OUT`이면 해당 옵션은 구매할 수 없다.
- 상품 상세 콘텐츠는 `IMAGE`와 `HTML` 블록으로 구성하고 `sortOrder`에 따라 노출한다.
- `HTML` 블록은 XSS 방지를 위해 sanitize해야 한다.
- 배송, 교환, 환불, 품절 가능성 같은 운영 정책 고지는 상품 상세 콘텐츠와 별도로 관리한다.
- 주문 상품에는 상품명, 옵션명, 가격을 스냅샷으로 저장한다.
- 상품 가격이 변경되어도 기존 주문 상품의 스냅샷 가격은 변경하지 않는다.
- 주문은 결제 요청 전에 `PAYMENT_PENDING` 상태로 생성한다.
- `PAYMENT_PENDING` 주문은 결제 검증 전이므로 공급처 발주 대상이 아니다.
- 결제 상태와 주문 상태를 같은 필드로 합치지 않는다.
- 공급처 발주 상태는 주문 상태와 분리하되, 고객에게 보여줄 주문 상태와 동기화 규칙을 둔다.
- 주문 상태 변경 이력은 별도 테이블로 추가하는 것이 좋다.
