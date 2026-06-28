# Domain Model

## Core Entities

```text
User
SocialAccount
UserAddress
Product
ProductImage
ProductOption
ProductDetailBlock
ProductNotice
Supplier
Cart
CartItem
Order
OrderItem
DeliveryGroup
PaymentGroup
Payment
PaymentEvent
Fulfillment
Shipment
Refund
Claim
NotificationLog
OrderStatusHistory
AdminActionHistory
ProductChangeHistory
BusinessProfile
PolicyDocument
UserPolicyAgreement
OrderPolicyAgreement
MarketingConsent
PrivacyProcessingItem
LegalRetentionRecord
```

## User

고객 또는 관리자를 나타낸다.

Implemented fields:

- id
- email
- name
- phone
- role: CUSTOMER / ADMIN
- status: ACTIVE / SUSPENDED / DELETED
- deletedAt
- anonymizedAt
- createdAt
- updatedAt

DS-32 implementation notes:

- `user_addresses` stores the customer's reusable address book.
- Order rows keep shipping address snapshots and do not reference `user_addresses`.
- The first saved address becomes default automatically.
- Only one default address is kept per customer through service logic.
- If the current default address is deleted and other addresses remain, the most recently created address becomes default.

## SocialAccount

카카오, 구글, 네이버 소셜 로그인 식별 정보를 나타낸다.

Implemented fields:

- id
- userId
- provider: KAKAO / GOOGLE / NAVER
- providerUserId
- email
- displayName
- linkedAt
- unlinkedAt
- createdAt
- updatedAt

## UserAddress

고객이 저장한 배송지. 주문에는 이 값을 그대로 참조하지 않고 주문 시점 주소 스냅샷을 저장한다.

Implemented fields:

- id
- userId
- recipientName
- recipientPhone
- postalCode
- address1
- address2
- defaultAddress
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
- thumbnailImageUrl: optional denormalized cache of the thumbnail `ProductImage`
- createdAt
- updatedAt

## ProductImage

상품 대표 이미지와 갤러리 이미지를 나타낸다. 대표 이미지는 `ProductImage.type = THUMBNAIL`이 기준이고, `Product.thumbnailImageUrl`은 목록 조회 성능을 위한 선택적 캐시로만 사용한다.

Suggested fields:

- id
- productId
- type: THUMBNAIL / GALLERY
- imageUrl
- sortOrder
- altText
- createdAt
- updatedAt

Modeling notes:

- One product can have one `THUMBNAIL` image.
- One product can have up to ten `GALLERY` images.
- If `Product.thumbnailImageUrl` is kept, it must be updated from the canonical thumbnail image.

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

## ProductNotice

상품 정보 제공 고시와 상품별 배송, AS, 반품, 교환 안내 버전. 주문 상품 스냅샷에서 `productNoticeSnapshotId` 또는 동등한 버전 참조로 연결된다.

Suggested fields:

- id
- productId
- version
- status: DRAFT / ACTIVE / ARCHIVED
- productInfoNotice
- shippingInfo
- asInfo
- returnExchangeInfo
- effectiveFrom
- createdAt
- updatedAt

## Cart

고객의 장바구니.

Implemented fields:

- id
- userId
- createdAt
- updatedAt

Rules:

- 비회원 장바구니는 MVP에서 제외한다.
- 고객 1명은 현재 장바구니 1개를 가진다.
- 장바구니 가격은 현재 상품/옵션 가격을 보여주기 위한 값이다.
- 결제 가격 확정은 주문 생성 시점의 스냅샷으로 처리한다.

## CartItem

장바구니의 상품 옵션 항목.

Implemented fields:

- id
- cartId
- productId
- productOptionId
- quantity
- createdAt
- updatedAt

Rules:

- 같은 상품 옵션을 다시 담으면 새 항목을 만들지 않고 기존 수량을 증가시킨다.
- 수량은 1개 이상 99개 이하로 제한한다.
- 상품 또는 옵션이 장바구니에 담긴 뒤 품절/숨김/판매중지되어도 항목은 남긴다.
- 주문서 진입 또는 주문 생성 전에 상품/옵션 판매 가능 상태를 다시 검증한다.

## Order

고객 주문의 중심 엔티티.

Implemented fields:

- id
- orderNumber
- userId
- supplierId
- paymentGroupId
- status: PAYMENT_PENDING / EXPIRED / PAYMENT_EXCEPTION / SUPPLIER_ORDER_PENDING / SUPPLIER_ORDERED / OUT_OF_STOCK / SHIPPED / DELIVERED / CANCELLED / REFUND_REQUESTED / REFUNDED
- recipientName
- recipientPhone
- postalCode
- address1
- address2
- subtotalAmount
- shippingFee
- discountAmount
- totalAmount
- expiresAt
- supplierOrderStartedAt
- addressLockedAt
- addressLockedByAdminId
- createdAt
- updatedAt

Implemented statuses:

- PAYMENT_PENDING
- EXPIRED
- PAYMENT_EXCEPTION
- SUPPLIER_ORDER_PENDING
- SUPPLIER_ORDERED
- OUT_OF_STOCK
- SHIPPED
- DELIVERED
- CANCELLED
- REFUND_REQUESTED
- REFUNDED

Rules:

- DS-8 creates one `PAYMENT_PENDING` order per supplier-backed delivery group.
- DS-11 exposes `SUPPLIER_ORDER_PENDING` orders through the admin supplier order queue.
- DS-11 admin order detail reads supplier, product option, shipping address snapshot, and payment summary from existing order/payment/catalog tables.
- MVP does not create a separate `delivery_groups` table; `supplierId` is the order grouping boundary.
- Shipping fee is 0 because shipping cost is included in product price.
- Orders expire with their payment group 30 minutes after checkout creation.

## OrderItem

주문에 포함된 상품 옵션. 주문 시점의 이름과 가격을 스냅샷으로 보관한다.

Implemented fields:

- id
- orderId
- productId
- productOptionId
- productName
- optionName
- productSummary
- productDetailVersion
- productNoticeVersion
- unitPrice
- quantity
- lineAmount
- supplierId
- createdAt
- updatedAt

Rules:

- Product name, summary, option name, unit price, detail version, and notice version are snapshotted at checkout creation.
- Product or option edits after checkout creation do not change existing order item snapshots.

## DeliveryGroup

고객에게 노출하는 배송 묶음. MVP에서는 같은 공급처 상품을 하나의 배송 그룹으로 묶는다.

Deferred fields:

- id
- supplierId
- displayName
- shippingFee
- createdAt
- updatedAt

## PaymentGroup

고객의 한 번 결제를 나타내는 결제 그룹. 하나의 `PaymentGroup`은 여러 배송 그룹 주문을 포함할 수 있다.

Implemented fields:

- id
- checkoutNumber
- userId
- status: PAYMENT_PENDING / APPROVED / PARTIALLY_REFUNDED / REFUNDED / PAYMENT_EXCEPTION / EXPIRED / CANCELLED / CANCEL_FAILED
- totalAmount
- approvedAmount
- refundableAmount
- expiresAt
- approvedAt
- policyConfirmedAt
- createdAt
- updatedAt

Rules:

- DS-8 creates `PaymentGroup` before PG payment.
- Initial status is `PAYMENT_PENDING`.
- `checkoutNumber` is the customer/payment-flow identifier.
- Policy confirmation is stored separately and also reflected by `policyConfirmedAt`.
- Actual PG payment attempts are stored by DS-9.

## Payment

PG 결제 기록.

Implemented fields:

- id
- paymentGroupId
- provider
- providerPaymentKey
- method: CARD / EASY_PAY / TRANSFER
- status: READY / APPROVED / FAILED / CANCEL_REQUIRED / CANCEL_REQUESTED / CANCELLED / CANCEL_FAILED / REFUND_REQUESTED / PARTIALLY_REFUNDED / REFUNDED / REFUND_FAILED / REVIEW_REQUIRED
- requestedAmount
- approvedAmount
- approvedAt
- exceptionReason: AMOUNT_MISMATCH / APPROVED_AFTER_EXPIRED / SELLABILITY_CHECK_FAILED / DUPLICATE_OR_CONFLICTING_CONFIRMATION / PG_CONFIRMATION_ERROR
- idempotencyKey
- failureCode
- failureMessage
- providerCancelTransactionKey
- cancelRequestedAt
- cancelledAt
- rawProviderStatus
- lastSyncedAt
- createdAt
- updatedAt

Rules:

- DS-9 creates one `Payment` on Toss confirmation success or payment exception.
- Same `providerPaymentKey` for the same checkout is idempotent.
- Same `providerPaymentKey` for another checkout is rejected.
- `APPROVED` moves the payment group to `APPROVED` and orders to `SUPPLIER_ORDER_PENDING`.
- `CANCEL_REQUIRED` is used for payment exception paths that need PG cancel/admin follow-up.
- Payment exception cancel requests use a stable idempotency key.
- Successful payment exception cancel moves `Payment`, `PaymentGroup`, and linked orders to `CANCELLED`.
- Failed payment exception cancel moves `Payment` and `PaymentGroup` to `CANCEL_FAILED` and leaves the case in the admin payment exception queue.

## PaymentEvent

PG 승인, 취소, 환불, webhook, 서버 확인 요청 이력. 멱등 처리와 PG 대사를 위해 원본 이벤트 단위로 기록한다.

Implemented fields:

- id
- paymentId
- paymentGroupId
- orderId
- providerPaymentKey
- eventType: CONFIRM_REQUESTED / CONFIRM_APPROVED / CONFIRM_REJECTED / PAYMENT_EXCEPTION / PAYMENT_EXCEPTION_CANCEL_REQUESTED / PAYMENT_EXCEPTION_CANCEL_COMPLETED / PAYMENT_EXCEPTION_CANCEL_FAILED / TOSS_WEBHOOK_RECEIVED / PAYMENT_REVIEW_REQUIRED / REFUND_REQUESTED / REFUND_COMPLETED / REFUND_FAILED
- idempotencyKey
- rawPayload
- resultMessage
- receivedAt
- processedAt
- createdAt

Rules:

- Toss webhook events store the webhook raw payload and an idempotency key.
- Duplicate webhook deliveries with the same idempotency key are ignored.
- Server confirm and webhook status conflicts create a `PAYMENT_REVIEW_REQUIRED` event and move the payment to `REVIEW_REQUIRED`.

## Fulfillment

공급처 발주 처리 상태.

Suggested fields:

- id
- orderId
- supplierId
- status: PENDING / ORDERED / OUT_OF_STOCK / CANCELLED
- supplierOrderStartedAt
- supplierOrderNumber
- orderedAddressSnapshot
- orderedByAdminId
- expectedShipDate
- supplierResponseMemo
- orderedAt
- outOfStockReason
- createdAt
- updatedAt

Planned fields:

- supplierResponseDueAt
- delayNoticeRequiredAt
- delayNotifiedAt
- memo

Rules:

- DS-12 creates a fulfillment row when supplier work starts or an out-of-stock action is recorded.
- Supplier work start keeps order status `SUPPLIER_ORDER_PENDING` and records address lock fields on `CustomerOrder`.
- Supplier order completion is allowed only after supplier work start and moves the order to `SUPPLIER_ORDERED`.
- Supplier out-of-stock is allowed only before shipment and moves the order to `OUT_OF_STOCK`.
- Admin order actions are recorded in `admin_order_action_histories`.

## Shipment

배송 정보.

Suggested fields:

- id
- orderId
- carrier
- trackingNumber
- status: READY / SHIPPED / DELIVERED
- manualCorrectionReason
- shippedAt
- deliveredAt
- trackingSyncedAt
- trackingSyncFailureReason
- manualOverride
- manualCorrectedByAdminId
- manualCorrectedAt
- createdAt
- updatedAt

Planned fields:

- trackingStatus

Rules:

- DS-13 creates one shipment per order when admin enters carrier and tracking number.
- Shipment creation is allowed only from `SUPPLIER_ORDERED` and moves the order to `SHIPPED`.
- Duplicate shipment creation for the same order is rejected.
- DS-35 syncs tracking results by shipment id or carrier/tracking number.
- Delivered tracking moves shipment and order to `DELIVERED`; non-delivered tracking does not move state backward.
- Tracking sync failure records `trackingSyncFailureReason` and keeps current order/shipment state.
- DS-36 supports admin manual correction to `DELIVERED` with reason, admin id, correction time, admin action history, and order status history.
- Customer order detail exposes shipment display status, carrier, and tracking number.

## Refund

취소 또는 환불 기록.

Suggested fields:

- id
- paymentGroupId
- orderId
- paymentId
- reason: CUSTOMER_CANCEL / SUPPLIER_OUT_OF_STOCK / DELIVERY_GROUP_OUT_OF_STOCK / ADMIN_CANCEL / PAYMENT_AMOUNT_MISMATCH / RETURN_REQUESTED / EXCHANGE_REQUESTED
- status: REQUESTED / APPROVED / PG_CANCEL_REQUESTED / PROCESSING / COMPLETED / FAILED / RETRY_REQUIRED / REJECTED / MANUAL_REVIEW_REQUIRED
- refundAmount
- refundScope: PAYMENT_GROUP / DELIVERY_GROUP_ORDER
- providerPaymentKey
- providerCancelTransactionKey
- idempotencyKey
- failureCode
- failureMessage
- rawProviderStatus
- reviewedByAdminId
- adminReviewReason
- reviewedAt
- requestedAt
- completedAt
- failedAt
- createdAt
- updatedAt

Implemented DS-38 scope:

- Refunds are created as `REQUESTED`.
- Admin approval moves a refund to `APPROVED`; PG cancel/refund request is allowed only after approval.
- First PG cancel failure moves to `RETRY_REQUIRED`; retry failure moves to `MANUAL_REVIEW_REQUIRED`.
- Manual review can move the refund back to `APPROVED` or to `REJECTED`.

Planned fields:

- requestedByUserId
- approvedByAdminId
- retryCount
- pgCancelRequestedAt
- pgCancelApprovedAt
- customerNotifiedAt

Rules:

- DS-15 creates refund records for approved cancellation and supplier out-of-stock.
- MVP refund scope is the delivery-group order.
- PG cancel/refund success is required before an order can move to `REFUNDED`.
- If the payment group still has active orders after one delivery-group order refund, the payment group and payment become `PARTIALLY_REFUNDED`.
- PG cancel/refund failure keeps the order in `REFUND_REQUESTED` and marks the refund `RETRY_REQUIRED`.

## Claim

취소, 반품, 교환 클레임 접수와 관리자 처리 상태.

Implemented fields:

- id
- orderId
- userId
- claimType: CANCEL / RETURN / EXCHANGE
- reason: SIMPLE_CHANGE_OF_MIND / DEFECT / WRONG_DELIVERY / DIFFERENT_FROM_PRODUCT_INFO / DELIVERY_ISSUE
- status: REQUESTED / UNDER_REVIEW / EVIDENCE_REQUESTED / APPROVED / REJECTED / RETURN_WAITING / RETURN_RECEIVED / REFUND_PROCESSING / EXCHANGE_SHIPPING / COMPLETED / WITHDRAWN
- requestedAction: REFUND / EXCHANGE
- customerMemo
- reviewedByAdminId
- adminReviewReason
- reviewedAt
- createdAt
- updatedAt

Planned fields:

- paymentGroupId
- shippingCostBearer: CUSTOMER / SELLER / UNDECIDED
- returnShippingFeeAmount
- exchangeShippingFeeAmount
- evidenceUrls
- adminMemo
- rejectionReason

Rules:

- DS-14 implements `CANCEL` claim creation and admin review.
- DS-37 implements `RETURN` and `EXCHANGE` claim creation after delivery and admin approve/reject review.
- Customer self-service cancellation is allowed only for `SUPPLIER_ORDER_PENDING` orders whose supplier work and address lock fields are empty.
- Eligible self-service cancellation creates an approved `CANCEL` claim and moves the order to `REFUND_REQUESTED`.
- After supplier work starts or after `SUPPLIER_ORDERED`, the customer can submit a `CANCEL` claim for admin review before shipment.
- After delivery, the customer can submit a `RETURN` claim with requested action `REFUND` or an `EXCHANGE` claim with requested action `EXCHANGE`.
- Simple change-of-mind return/exchange claims are accepted only within 7 days from `deliveredAt`.
- Seller-fault return/exchange claims use a 90-day delivered-at baseline in DS-37; discovery date and evidence URLs remain planned fields.
- Admin approval moves the order to `REFUND_REQUESTED`; admin rejection keeps the order status unchanged.
- requestedAt
- deliveredAtAtRequest
- discoveryDate
- approvedByAdminId
- approvedAt
- returnCarrier
- returnTrackingNumber
- returnReceivedAt
- inspectedAt
- refundId
- createdAt
- updatedAt

## NotificationLog

주문, 결제, 배송, 환불, 클레임 처리 알림 발송 이력. 거래 알림과 마케팅 알림을 구분해서 기록한다.

Implemented fields:

- id
- userId
- orderId
- paymentGroupId
- claimId
- refundId
- type: PAYMENT_COMPLETED / PAYMENT_EXCEPTION / OUT_OF_STOCK / SHIPMENT_STARTED / DELIVERY_COMPLETED / DELAY_NOTICE / CLAIM_STATUS_CHANGED / REFUND_COMPLETED / MARKETING
- channel: EMAIL / ORDER_DETAIL / SMS / KAKAO_ALIMTALK / PUSH
- transactional
- status: PENDING / SENT / FAILED / SKIPPED
- recipient
- templateKey
- payloadSnapshot
- failureReason
- sentAt
- createdAt
- updatedAt

Implemented DS-39 scope:

- `notification_logs` persists transactional email notification records.
- MVP email baseline records `SENT` logs without adding an external SMTP/provider dependency.
- Implemented triggers include payment completed, payment exception, out-of-stock, shipment started, delivery completed, claim status changed, and refund completed.
- Admin can list notification logs with `GET /api/admin/notifications`.

## OrderStatusHistory

주문 상태 변경 이력. 주문 상태는 임의 되돌리기 없이 허용된 액션을 통해서만 변경한다.

Suggested fields:

- id
- orderId
- actorUserId
- actionType
- fromStatus
- toStatus
- guardResult
- sideEffectSummary
- reason
- createdAt

Implemented DS-36 scope:

- Status history is persisted in `order_status_histories`.
- Admin fulfillment/shipment actions and shipment tracking/manual correction delivery completion record order status history.
- System tracking sync uses a null actor; admin correction stores the admin user id.

## AdminActionHistory

관리자 주요 작업 이력. 상태 변경 외에도 환불, 품절, 배송 수동 보정, 정정 처리 같은 운영 액션을 기록한다.

Suggested fields:

- id
- adminUserId
- targetType: ORDER / PAYMENT / REFUND / CLAIM / SHIPMENT / PRODUCT / PRODUCT_OPTION
- targetId
- actionType
- reason
- beforeValue
- afterValue
- createdAt

## ProductChangeHistory

상품 주요 변경 이력. MVP에서는 운영 영향이 큰 변경부터 기록한다.

Suggested fields:

- id
- productId
- productOptionId
- adminUserId
- changeType: PRICE / PRODUCT_STATUS / OPTION_STATUS / SUPPLIER
- beforeValue
- afterValue
- reason
- createdAt

## PolicyDocument

고객에게 고지하는 정책 문서. 이용약관, 개인정보처리방침, 배송 정책, 취소/환불 정책을 버전 단위로 관리한다.

Suggested fields:

- id
- type: TERMS_OF_SERVICE / PRIVACY_POLICY / SHIPPING_POLICY / CANCELLATION_REFUND_POLICY
- version
- title
- content
- effectiveFrom
- status: DRAFT / ACTIVE / ARCHIVED
- createdAt
- updatedAt

## BusinessProfile

고객에게 표시하는 사업자, 통신판매, 고객센터, 개인정보 보호책임자 정보.

Suggested fields:

- id
- companyName
- representativeName
- businessRegistrationNumber
- mailOrderSalesRegistrationNumber
- mailOrderSalesRegistrationAuthority
- businessAddress
- customerCenterPhone
- customerCenterEmail
- customerCenterHours
- privacyOfficerName
- privacyOfficerEmail
- privacyOfficerPhone
- hostingProvider
- status: DRAFT / ACTIVE / ARCHIVED
- effectiveFrom
- createdAt
- updatedAt

## PrivacyProcessingItem

개인정보처리방침의 처리표 항목.

Suggested fields:

- id
- category: SOCIAL_LOGIN / ACCOUNT / ORDER_CONTACT / SHIPPING_ADDRESS / PAYMENT / CLAIM / MARKETING / LOG
- collectedItems
- purpose
- retentionPeriod
- processorName
- processorPurpose
- thirdPartyRecipient
- thirdPartyPurpose
- thirdPartyItems
- thirdPartyRetentionPeriod
- status: ACTIVE / ARCHIVED
- createdAt
- updatedAt

## MarketingConsent

선택 마케팅 수신 동의. 주문, 배송, 결제, 환불, 클레임 거래 알림과 분리한다.

Suggested fields:

- id
- userId
- channel: EMAIL / SMS / KAKAO_ALIMTALK / PUSH
- agreed
- policyVersion
- agreedAt
- withdrawnAt
- createdAt
- updatedAt

## LegalRetentionRecord

회원 탈퇴 후에도 법정 보존 또는 분쟁 대응을 위해 분리 보관하는 기록의 색인.

Suggested fields:

- id
- sourceType: ORDER / PAYMENT / SHIPMENT / REFUND / CLAIM / POLICY_AGREEMENT
- sourceId
- formerUserId
- retentionReason
- retentionUntil
- accessScope: LEGAL_ADMIN_ONLY
- anonymized
- createdAt
- updatedAt

## UserPolicyAgreement

회원가입 또는 첫 소셜 로그인 시점의 약관/개인정보 동의 기록.

Suggested fields:

- id
- userId
- termsVersion
- privacyVersion
- agreedAt
- createdAt

DS-31 implementation note:

- The first implementation stores required terms and privacy versions directly.
- `PolicyDocument` linkage can be added later when managed policy version APIs are implemented.
- Duplicate agreement for the same user, terms version, and privacy version is idempotent.
- Checkout creation requires the current required terms/privacy agreement.

## OrderPolicyAgreement

주문서에서 결제 그룹(PaymentGroup)마다 확인한 정책 고지 기록. 하나의 확인 기록은 결제 그룹에 포함된 배송 그룹 주문들에 적용된다.

Suggested fields:

- id
- paymentGroupId
- userId
- appliedOrderIds
- termsVersion
- privacyVersion
- shippingPolicyVersion
- cancellationRefundPolicyVersion
- confirmedNoticeText
- confirmedAt
- createdAt

## Modeling Notes

## Final MVP State Sets

### Order.status

- `PAYMENT_PENDING`: 결제 검증 전 주문. 공급처 발주 대상이 아니다.
- `EXPIRED`: 결제 검증 없이 30분이 지나 만료된 주문.
- `PAYMENT_EXCEPTION`: PG 승인은 있었지만 주문 확정 검증에 실패한 주문.
- `SUPPLIER_ORDER_PENDING`: 결제 검증 완료 후 공급처 발주 전 주문.
- `SUPPLIER_ORDERED`: 공급처 발주 완료 후 송장 입력 전 주문.
- `OUT_OF_STOCK`: 공급처 품절로 고객 안내와 환불 처리가 필요한 주문.
- `SHIPPED`: 택배사와 송장번호가 입력되어 배송 중인 주문.
- `DELIVERED`: 배송 완료가 확인된 주문.
- `CANCELLED`: PG 승인 전 주문 종료 또는 결제 예외 취소 완료.
- `REFUND_REQUESTED`: 결제 승인 완료 주문의 환불 처리 중 상태.
- `REFUNDED`: PG 취소/환불 성공이 확인된 환불 완료 주문.

### PaymentGroup.status

- `PAYMENT_PENDING`
- `APPROVED`
- `PARTIALLY_REFUNDED`
- `REFUNDED`
- `PAYMENT_EXCEPTION`
- `EXPIRED`
- `CANCELLED`
- `CANCEL_FAILED`

### Payment.status

- `READY`
- `APPROVED`
- `FAILED`
- `CANCEL_REQUIRED`
- `CANCEL_REQUESTED`
- `CANCELLED`
- `CANCEL_FAILED`
- `REFUND_REQUESTED`
- `PARTIALLY_REFUNDED`
- `REFUNDED`
- `REFUND_FAILED`
- `REVIEW_REQUIRED`

### Fulfillment.status

- `PENDING`
- `ORDERED`
- `OUT_OF_STOCK`
- `CANCELLED`

### Shipment.status

- `READY`
- `SHIPPED`
- `DELIVERED`

### Refund.status

- `REQUESTED`
- `APPROVED`
- `PG_CANCEL_REQUESTED`
- `PROCESSING`
- `COMPLETED`
- `FAILED`
- `RETRY_REQUIRED`
- `REJECTED`
- `MANUAL_REVIEW_REQUIRED`

- 상품과 옵션에는 실제 재고 수량을 두지 않는다.
- 상품 전체 상태와 상품 옵션 상태를 분리한다.
- 고객이 구매할 수 있는 조건은 상품 상태가 `ACTIVE`이고 옵션 상태도 `ACTIVE`인 경우다.
- 상품이 `ACTIVE`여도 특정 옵션이 `SOLD_OUT`이면 해당 옵션은 구매할 수 없다.
- 상품 상세 콘텐츠는 `IMAGE`와 `HTML` 블록으로 구성하고 `sortOrder`에 따라 노출한다.
- `HTML` 블록은 XSS 방지를 위해 sanitize해야 한다.
- 배송, 교환, 환불, 품절 가능성 같은 운영 정책 고지는 상품 상세 콘텐츠와 별도로 관리한다.
- 주문 상품에는 상품명, 옵션명, 가격을 스냅샷으로 저장한다.
- 주문 상품에는 상품 요약, 상품 상세 버전, 상품 정보 제공 고시 버전 참조도 스냅샷으로 저장한다.
- 상품 가격이 변경되어도 기존 주문 상품의 스냅샷 가격은 변경하지 않는다.
- 상품 상세 HTML/이미지 내용이 변경되어도 결제 완료 주문의 주문 상품 스냅샷은 변경하지 않는다.
- 주문은 결제 요청 전에 `PAYMENT_PENDING` 상태로 생성한다.
- `PAYMENT_PENDING` 주문은 결제 검증 전이므로 공급처 발주 대상이 아니다.
- `PAYMENT_PENDING` 주문은 생성 후 30분이 지나면 `EXPIRED`로 만료 처리한다.
- 결제 상태와 주문 상태를 같은 필드로 합치지 않는다.
- PG 결제가 승인됐지만 주문을 확정할 수 없으면 주문은 `PAYMENT_EXCEPTION`으로 전환하고 공급처 발주를 차단한다.
- 결제 예외는 즉시 PG 전액 취소를 시도하고, 실패하면 관리자 긴급 확인 큐로 전환한다.
- 결제 이벤트와 환불 이벤트는 멱등 처리와 PG 대사를 위해 별도 이력으로 기록한다.
- MVP 결제수단은 카드, 간편결제, 계좌이체로 제한한다.
- MVP에서는 배송 그룹 주문 단위 부분 취소/부분 환불을 지원한다.
- 상품, 옵션, 수량 단위 부분 취소/부분 환불은 MVP에서 지원하지 않는다.
- 하나의 결제 그룹(PaymentGroup)은 여러 배송 그룹 주문을 포함할 수 있다.
- 하나의 배송 그룹 주문은 하나의 결제 그룹(PaymentGroup)에 속한다.
- 하나의 결제 그룹(PaymentGroup)에 일부 주문만 환불되면 결제 그룹은 `PARTIALLY_REFUNDED`가 될 수 있다.
- 결제 승인 완료 주문은 PG 취소/환불 성공 후에만 `REFUNDED`가 될 수 있다.
- PG 취소/환불 실패는 `FAILED`, `RETRY_REQUIRED`, `MANUAL_REVIEW_REQUIRED` 같은 상태로 남기고 완료 상태로 처리하지 않는다.
- 고객 직접 취소는 `SUPPLIER_ORDER_PENDING` 상태이면서 공급처 발주 작업이 시작되지 않은 주문에만 허용한다.
- 공급처 발주 작업 시작 후 배송 전 취소는 `Claim`으로 접수하고 관리자 수동 심사로 처리한다.
- 배송 후 반품/교환은 `Claim`으로 접수하고 관리자 수동 심사로 처리한다.
- 단순 변심 반품/교환 클레임은 배송 완료일로부터 7일 이내 접수된 건만 심사한다.
- 상품 하자, 오배송, 상품 정보와 다름, 배송 문제 클레임은 배송 완료일로부터 3개월 이내이면서 고객이 그 사실을 안 날 또는 알 수 있었던 날부터 30일 이내 접수된 건만 심사한다.
- 상품 하자, 오배송, 상품 정보와 다름, 배송 문제 클레임은 사진 증빙을 필수로 저장한다.
- 클레임 상태와 환불 상태는 분리하고, 클레임 승인 후 PG 환불은 `Refund`에서 처리한다.
- 고객 직접 배송지 변경은 `SUPPLIER_ORDER_PENDING` 상태라도 `addressLockedAt`이 기록되면 거절한다.
- 공급처 발주 작업 시작은 새 주문 상태를 추가하지 않고 `supplierOrderStartedAt`과 `addressLockedAt`으로 기록한다.
- 공급처 발주 증빙으로 공급처 주문번호, 발주 주소 스냅샷, 발주 관리자, 예상 출고일, 공급처 응답 메모를 기록한다.
- 공급처 발주 후 2영업일 이상 출고 예정이 불명확하면 고객 지연 안내 대상으로 관리한다.
- 배송 후 반품/교환은 클레임 접수와 관리자 수동 심사로 시작한다.
- 택배사와 송장번호 입력 후 배송 상태는 자동 조회/동기화한다.
- `PREPARING_SHIPMENT`은 MVP 주문 상태에서 제거하고 공급처 발주 완료 후 송장 입력 전 구간은 `SUPPLIER_ORDERED`로 표현한다.
- 자동 배송조회 실패에 대비해 배송 상태 수동 보정과 상태 변경 이력이 필요하다.
- MVP 배송은 주문 1개당 배송 1개로 시작하고 부분 출고/분할 배송은 제외한다.
- 자동 배송조회는 관리자 수동 보정 상태를 임의로 덮어쓰거나 뒤로 되돌리지 않는다.
- MVP에서는 고객에게 별도 배송비를 청구하지 않으며 `shippingFee`는 `0`으로 시작한다.
- MVP에서 한 주문은 하나의 배송 그룹만 포함한다.
- 배송 그룹은 공급처 기준으로 나누지만 고객 화면에는 공급처 대신 배송 그룹으로 표시한다.
- 고객 화면에는 내부 주문 상태를 그대로 노출하지 않고 고객용 표시 상태로 매핑한다.
- 고객 주문 내역에는 결제 성공 후 확정된 주문과 고객에게 처리 상태를 보여줘야 하는 결제 예외 주문만 노출한다.
- `PAYMENT_PENDING`, `EXPIRED`, 결제 실패 주문은 일반 고객 주문 내역이 아니라 체크아웃/결제 재시도 화면에서만 다룬다.
- 공급처 발주 상태는 주문 상태와 분리하되, 고객에게 보여줄 주문 상태와 동기화 규칙을 둔다.
- 주문 상태 변경 이력은 MVP부터 별도 테이블에 기록한다.
- 주문 상태 변경 이력은 action, guard result, side effect summary를 남긴다.
- 결제 완료, 결제 예외, 품절, 배송 시작, 배송 완료, 지연 안내, 클레임 상태 변경, 환불 완료는 `NotificationLog`에 기록한다.
- 관리자 주문 액션은 현재 상태에서 허용된 다음 액션으로만 실행한다.
- 자동 상태 되돌리기 버튼은 MVP에서 제공하지 않고, 잘못된 상태 변경은 관리자 정정 액션과 이력으로 처리한다.
- 취소, 환불, 품절, 배송 수동 보정, 관리자 정정 액션은 사유를 필수로 기록한다.
- 상품 변경 이력은 가격, 상품/옵션 판매 상태, 공급처 변경부터 MVP에서 기록한다.
- 상품 상세 HTML diff, 이미지 변경 diff, 상품명/요약문 상세 diff는 MVP 이후로 미룬다.
- 이용약관, 개인정보처리방침, 배송 정책, 취소/환불 정책은 버전과 시행일을 가진다.
- 첫 가입 또는 첫 소셜 로그인 완료 시 이용약관과 개인정보처리방침 동의 이력을 저장한다.
- 소셜 로그인 필수 저장 항목은 제공자, 제공자 user id, 이메일, 표시 이름으로 시작한다.
- 전화번호는 소셜 로그인 필수 항목이 아니라 주문, 배송, 클레임에 필요한 시점에 수집한다.
- 회원 탈퇴 시 고객 프로필과 소셜 계정 연결은 삭제 또는 비식별화한다.
- 법정 보존 대상 주문, 결제, 배송, 클레임, 정책 동의 기록은 탈퇴 후에도 분리 보관한다.
- 법정 보존 기록은 보존 사유와 보존 만료일을 저장한다.
- 전자상거래 표시/광고 기록은 6개월, 계약/청약철회 기록은 5년, 대금결제/재화 공급 기록은 5년, 소비자 불만/분쟁 처리 기록은 3년 보존 기준으로 시작한다.
- 탈퇴 후 같은 소셜 계정으로 재가입하면 새 고객 계정으로 생성하고 기존 주문 내역은 고객 화면에 자동 복구하지 않는다.
- 푸터와 고객센터/회사 정보 페이지에는 사업자 정보, 통신판매업 신고 정보, 고객센터, 개인정보 보호책임자 정보를 표시한다.
- 상품 상세에는 품목별 상품 정보 제공 고시, 배송, AS, 반품, 교환 정보를 표시한다.
- 개인정보 처리표는 수집 항목, 처리 목적, 보유 기간, 처리 위탁처, 제3자 제공 여부를 관리한다.
- 거래 알림은 주문, 배송, 결제, 환불, 클레임 처리에 필요한 알림으로 보고 선택 마케팅 수신 동의와 분리한다.
- 마케팅 알림은 채널별 선택 동의와 철회 이력을 저장한다.
- 결제 그룹(PaymentGroup) 시점에는 주문서 통합 확인 체크의 정책 버전과 확인 시각을 저장한다.
- 주문서 통합 확인 체크는 결제 그룹(PaymentGroup) 단위로 저장한다.
- 주문서 통합 확인 체크는 주문 상품, 결제 금액, 배송지, 배송/취소/환불 정책, 품절 가능성, 품절 시 해당 배송 그룹 주문 금액 환불 안내를 포함한다.
