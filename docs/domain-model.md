# Domain Model

## Core Entities

```text
User
Product
ProductOption
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
- passwordHash
- name
- phone
- role: CUSTOMER / ADMIN
- status: ACTIVE / SUSPENDED / DELETED
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
- PAID
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
- 주문 상품에는 상품명, 옵션명, 가격을 스냅샷으로 저장한다.
- 결제 상태와 주문 상태를 같은 필드로 합치지 않는다.
- 공급처 발주 상태는 주문 상태와 분리하되, 고객에게 보여줄 주문 상태와 동기화 규칙을 둔다.
- 주문 상태 변경 이력은 별도 테이블로 추가하는 것이 좋다.

