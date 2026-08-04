# Domain Model

## Core Entities

```text
User
SocialAccount
UserAddress
PhoneVerificationCode
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
CustomerInquiry
```

## User

고객 또는 관리자를 나타낸다.

Implemented fields:

- id
- provider
- providerUserId
- email
- displayName
- phoneNumber
- phoneVerifiedAt
- referralCode
- referredByUserId
- referredAt
- role: CUSTOMER / ADMIN
- status: ACTIVE / DELETED
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

B-017 implementation notes:

- 고객 회원 유형은 구분하지 않는다.
- 로그인 후 필수 정보 완료 상태는 이름, 연락 가능한 이메일, 형식이 유효한 배송 연락처로 판단한다.
- 소셜 로그인 placeholder 이메일은 고객 연락 주소로 보지 않는다.
- 휴대폰 번호는 프로필에서 직접 저장한다. 기존 SMS OTP 성공 기록은 `phoneVerifiedAt`에 보존하지만 주문 조건으로 사용하지 않는다.

B-014 implementation notes:

- 회원 탈퇴는 `status=DELETED`, `deletedAt`, `anonymizedAt` 기록과 개인식별정보 비식별화로 처리한다.
- `email`은 `deleted-{userId}@deleted.local`, `displayName`은 `탈퇴회원`, `phoneNumber`/`phoneVerifiedAt`은 null로 바꾼다.
- 현재 구현은 별도 `SocialAccount` 테이블 없이 `User.provider/providerUserId`를 소셜 연결로 사용한다. 탈퇴 시 `providerUserId`를 `deleted-{userId}`로 바꿔 같은 소셜 계정 재가입이 새 계정을 만들게 한다.
- 진행 중 주문, 환불, 클레임이 있으면 탈퇴를 거부한다. 법정 보존 대상 거래 기록은 비식별화된 유저 참조로 보존한다.

B-050 implementation notes:

- `referralCode`는 모든 회원에게 lazy 생성되는 고유 추천 코드다.
- `referredByUserId`와 `referredAt`은 첫 로그인 온보딩에서 선택 등록한 추천 관계를 한 번만 기록한다.
- 고객 화면/API는 내 추천 코드와 추천인 등록 여부만 노출하고, 추천인의 이름이나 이메일은 노출하지 않는다.
- 추천 포인트, 보상, 정산은 아직 모델링하지 않는다.

## SocialAccount

카카오, 구글, 네이버 소셜 로그인 식별 정보를 나타낸다. 고객 로그인 화면은 카카오만 노출하고 Google/Naver 연결은 기존 계정 호환용으로 유지한다.

Planned fields:

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

MVP implementation note:

- B-014 기준 현재 구현은 `SocialAccount` 테이블을 만들지 않고 `users.provider/provider_user_id`를 사용한다. 복수 provider 연결, 계정 병합, 소셜 연결 해제가 필요해질 때 별도 테이블로 분리한다.

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

## PhoneVerificationCode

휴대폰 번호 소유 확인을 위한 SMS OTP 시도 기록.

Implemented fields:

- id
- userId
- phoneNumber
- codeHash
- expiresAt
- verifiedAt
- attemptCount
- createdAt

Rules:

- 인증번호는 평문 저장하지 않는다.
- 5분 만료, 재발송 제한, 시도 횟수 제한을 적용한다.
- NICE/PASS/CI/DI 본인확인은 MVP 범위가 아니다.

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
- sourcePrice: supplier/domeggook cost, admin-only
- sourceItemNo: optional unique supplier product number, admin-only
- sourceUrl: optional supplier source page, admin-only
- sourceAvailable: optional supplier sale availability from the last successful sync, admin-only
- sourceSyncedAt: optional last sync attempt time, admin-only
- sourceSyncError: optional last sync failure, admin-only
- basePrice
- categoryCode: fixed product taxonomy code such as `PPE_SAFETY_HELMET`
- status: ACTIVE / SOLD_OUT / HIDDEN / STOPPED
- complianceStatus: PENDING / NOT_REQUIRED / VERIFIED / REJECTED
- `PENDING`은 운영 참고 상태이며 판매를 차단하지 않는다. `REJECTED`만 판매 준비 실패로 처리한다.
- thumbnailImageUrl: optional denormalized cache of the thumbnail `ProductImage`
- createdAt
- updatedAt

Modeling notes:

- MVP stores one fixed `categoryCode` per product.
- `sourcePrice` is internal cost. `sourceItemNo` is the supplier-product identity and `sourceUrl` is its operator traceability link. None are exposed by public product APIs.
- Domeggook product creation derives `sourceItemNo` from `sourceUrl`; duplicate non-null values are rejected.
- `sourceUrl` accepts only `http` or `https` and is limited to 2,000 characters. Scheduled source sync uses `sourceItemNo` with the approved Open API rather than fetching this page URL.
- `basePrice` is the customer sale price. Supplier shipping fees are operating costs and are not added to its calculation.
- Default sale price is calculated from the active pricing policy, currently supplier cost plus 25% and rounded to the nearest 100 KRW.
- Scheduled sync updates current product and option prices only; existing order price snapshots never change. Manual `HIDDEN` and `STOPPED` states are not overridden.
- Category administration, multi-category assignment, and tag search are out of MVP scope.
- `ACTIVE` requires a positive sale price, canonical thumbnail, active option, active notice, and compliance status other than `REJECTED`.
- `saleReady` and `saleBlockers` are derived admin views over those conditions rather than persisted product state. Detail-content presence is shown as a recommendation and does not block activation.

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
- DS-42 adds admin binary upload that stores the file and returns URL/object-key metadata.

## ProductOption

색상, 사이즈, 구성 같은 구매 단위.

Suggested fields:

- id
- productId
- name
- additionalPrice
- sourceOptionCode
- sourceAdditionalPrice
- sourceStockQuantity
- sortOrder
- status: ACTIVE / SOLD_OUT / STOPPED
- createdAt
- updatedAt

Rules:

- `additionalPrice` is the customer-facing sale delta from the product `basePrice`.
- Source option metadata is retained for supplier/import traceability and admin review.
- Source metadata must not be included in public product detail responses.
- Source stock quantity is not a checkout inventory source in MVP.

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

상품 정보 제공 고시와 상품별 배송, AS, 반품, 교환 안내 버전. 주문 상품 스냅샷에서 `productNoticeSnapshotId` 또는 동등한 버전 참조로 연결된다. 고객 화면은 공급처 상품정보제공고시를 구조화한 `noticeRows`와 코어블 정책만 사용한다.

Suggested fields:

- id
- productId
- version
- status: DRAFT / ACTIVE / ARCHIVED
- productInfoNotice
- noticeRows: `{ label, value }[]`
- shippingInfo
- asInfo
- returnExchangeInfo
- effectiveFrom
- createdAt
- updatedAt

Rules:

- Supplier identity and trade terms may be retained in collection snapshots but are not customer-facing product notice rows.

Implemented DS-40 scope:

- Public `GET /api/business-profile` exposes the customer-facing business disclosure baseline.
- The initial implementation is a static backend response; admin update remains planned.

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
- Current bank-transfer checkout uses a 24-hour deposit deadline.

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
- bankTransferBankName
- bankTransferAccountNumber
- bankTransferAccountHolder
- bankTransferDepositorName
- bankTransferCashReceiptNotice
- depositConfirmedByAdminId
- depositConfirmedAt
- depositConfirmationReason
- depositMismatchMemo
- depositMismatchRecordedByAdminId
- depositMismatchRecordedAt
- unpaidCancelledByAdminId
- unpaidCancelledAt
- unpaidCancelReason
- createdAt
- updatedAt

Rules:

- DS-8 creates `PaymentGroup` before payment/deposit completion.
- Initial status is `PAYMENT_PENDING`; in B-041 this means direct bank-transfer deposit waiting.
- `checkoutNumber` is the customer/payment-flow identifier.
- Policy confirmation is stored separately and also reflected by `policyConfirmedAt`.
- Bank-transfer deposit metadata is configured at checkout creation and used on the customer checkout detail.
- Admin deposit confirmation sets approved amount/time and records admin id/reason.
- Admin unpaid cancellation records admin id/time/reason and moves the group to `CANCELLED`.
- Admin deposit mismatch records a memo while keeping the group `PAYMENT_PENDING`.

## Payment

결제 기록. 현재 MVP 주 경로는 계좌입금이다. 과거 PG 데이터는 조회 호환을 위해 기존 enum과 컬럼을 유지한다.

Implemented fields:

- id
- paymentGroupId
- provider: BANK_TRANSFER (현재 생성값), TOSS_PAYMENTS (과거 기록 호환 전용)
- providerPaymentKey
- method: BANK_TRANSFER (현재 생성값), CARD / EASY_PAY / TRANSFER (과거 기록 호환 전용)
- status: 현재 계좌입금 흐름은 `APPROVED`와 수동 환불 관련 상태만 생성한다. 나머지 PG 상태값은 과거 기록 호환 전용이다.
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

- B-041 creates one `Payment` on admin bank-transfer deposit confirmation.
- Bank-transfer payments use `providerPaymentKey = BANK-{checkoutNumber}` to keep the existing unique provider key invariant.
- `APPROVED` moves the payment group to `APPROVED` and orders to `SUPPLIER_ORDER_PENDING`.

## PaymentEvent

계좌입금 관리자 처리와 환불 이력. 과거 PG 이벤트 값은 조회 호환을 위해 enum에 남긴다.

Implemented fields:

- id
- paymentId
- paymentGroupId
- orderId
- providerPaymentKey
- eventType: CONFIRM_REQUESTED / CONFIRM_APPROVED / CONFIRM_REJECTED / PAYMENT_EXCEPTION / PAYMENT_EXCEPTION_CANCEL_REQUESTED / PAYMENT_EXCEPTION_CANCEL_COMPLETED / PAYMENT_EXCEPTION_CANCEL_FAILED / TOSS_WEBHOOK_RECEIVED / PAYMENT_REVIEW_REQUIRED / BANK_TRANSFER_DEPOSIT_CONFIRMED / BANK_TRANSFER_UNPAID_CANCELLED / BANK_TRANSFER_DEPOSIT_MISMATCH_RECORDED / REFUND_REQUESTED / REFUND_COMPLETED / MANUAL_REFUND_COMPLETED / REFUND_FAILED
- idempotencyKey
- rawPayload
- resultMessage
- receivedAt
- processedAt
- createdAt

Rules:


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
- status: REQUESTED / COMPLETED / REJECTED (현재 수동 계좌환불 흐름), PG_CANCEL_REQUESTED / APPROVED / PROCESSING / FAILED / RETRY_REQUIRED / MANUAL_REVIEW_REQUIRED (과거 기록 호환 전용)
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
- manualRefundedByAdminId
- manualRefundedAt
- manualRefundReason
- manualRefundBankName
- manualRefundAccountNumber
- manualRefundAccountHolder
- requestedAt
- completedAt
- failedAt
- createdAt
- updatedAt

Implemented DS-38 scope:

- Refunds are created as `REQUESTED`.
- Admin approval moves a refund to `APPROVED`; an admin then records actual manual bank-transfer completion for `BANK_TRANSFER` payments.

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
- Actual manual bank-transfer refund completion is required before an order can move to `REFUNDED`.
- If the payment group still has active orders after one delivery-group order refund, the payment group and payment become `PARTIALLY_REFUNDED`.

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
- returnReceivedByAdminId
- returnReceivedAt
- returnReceivedMemo
- refundId
- completedAt
- createdAt
- updatedAt

Related implemented model:

- `ClaimEvidence`
  - id
  - claimId
  - fileUrl
  - objectKey
  - originalFilename
  - contentType
  - sizeBytes
  - uploadedAt

Planned fields:

- paymentGroupId
- shippingCostBearer: CUSTOMER / SELLER / UNDECIDED
- returnShippingFeeAmount
- exchangeShippingFeeAmount
- adminMemo
- rejectionReason
- requestedAt
- deliveredAtAtRequest
- discoveryDate
- approvedByAdminId
- approvedAt
- returnCarrier
- returnTrackingNumber
- inspectedAt

Rules:

- DS-14 implements `CANCEL` claim creation and admin review.
- DS-37 implements `RETURN` and `EXCHANGE` claim creation after delivery and admin approve/reject review.
- B-044 implements delivered return receive, return refund start, manual refund completion linkage, and approved return rejection after inspection.
- Customer self-service cancellation is allowed only for `SUPPLIER_ORDER_PENDING` orders whose supplier work and address lock fields are empty.
- Eligible self-service cancellation creates an approved `CANCEL` claim and moves the order to `REFUND_REQUESTED`.
- After supplier work starts or after `SUPPLIER_ORDERED`, the customer can submit a `CANCEL` claim for admin review before shipment.
- After delivery, the customer can submit a `RETURN` claim with requested action `REFUND` or an `EXCHANGE` claim with requested action `EXCHANGE`.
- Simple change-of-mind return/exchange claims are accepted only within 7 days from `deliveredAt`.
- Seller-fault return/exchange claims use a 90-day delivered-at baseline in the current implementation. Discovery date input remains planned.
- Seller-fault claim reasons require at least one image evidence file at customer claim creation. Evidence metadata is stored in `ClaimEvidence`.
- Admin approval of a cancellation claim moves the order to `REFUND_REQUESTED`.
- Admin approval of a return claim moves the claim to `RETURN_WAITING` and keeps the order `DELIVERED`.
- Admin return received action moves the claim to `RETURN_RECEIVED`.
- Admin return refund start requires a `RETURN_RECEIVED` return claim, creates a `RETURN_REQUESTED` refund, moves the order to `REFUND_REQUESTED`, links the refund to the claim, and moves the claim to `REFUND_PROCESSING`.
- Manual bank-transfer refund completion moves the order to `REFUNDED`, refund to `COMPLETED`, and linked return claim to `COMPLETED`.
- Admin rejection keeps the order status unchanged; approved return rejection after inspection keeps the order `DELIVERED`.

## NotificationLog

주문, 결제, 배송, 환불, 클레임 처리 알림 발송 이력. 거래 알림과 마케팅 알림을 구분해서 기록한다.

Implemented fields:

- id
- userId
- orderId
- paymentGroupId
- claimId
- refundId
- customerInquiryId
- type: PAYMENT_PENDING / PAYMENT_COMPLETED / PAYMENT_EXCEPTION / OUT_OF_STOCK / SHIPMENT_STARTED / DELIVERY_COMPLETED / DELAY_NOTICE / CLAIM_STATUS_CHANGED / REFUND_COMPLETED / CUSTOMER_INQUIRY_ANSWERED / MARKETING
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

Implemented B-011 scope:

- `notification_logs` persists transactional SMS and customer inquiry email attempts.
- Logs start as `PENDING` and move to `SENT`, `FAILED`, or `SKIPPED` after dispatch.
- `sms.sens.enabled=false` records transactional SMS as `SKIPPED` instead of pretending to send.
- Implemented triggers include payment pending bank-transfer 안내, payment completed, out-of-stock, shipment started, delivery completed, manual delay notice, claim status changed, and refund completed.
- Order-related notifications use the order recipient phone number, not the account phone number.
- Admin can list notification logs with `GET /api/admin/notifications?status=...` and retry failed or skipped logs with `POST /api/admin/notifications/{notificationId}/retry`.

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
- DS-44 exposes admin order status history reads at `GET /api/admin/orders/{orderId}/status-history`.

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

Implemented DS-44 scope:

- Admin order action history is read from `admin_order_action_histories`.
- `GET /api/admin/actions` returns order id, admin user id, action type, before/after status, reason, and created time. `orderId` query filtering is supported for the selected admin order detail.

## ProductChangeHistory

상품 주요 변경 이력. MVP에서는 운영 영향이 큰 변경부터 기록한다.

Suggested fields:

- id
- productId
- productOptionId
- adminUserId
- changeType: PRICE / PRODUCT_STATUS / COMPLIANCE_STATUS / OPTION_STATUS / SUPPLIER / PRODUCT_BASE / OPTION_BASE / IMAGES / DETAIL_BLOCKS / NOTICE
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

Implemented DS-41 scope:

- `policy_documents` persists managed policy versions.
- Admin can create and update drafts, then activate a draft.
- Activating a policy archives the previous active policy of the same type.
- Public APIs expose the current active policy and specific versions.
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

Implemented DS-40 scope:

- Public `GET /api/privacy-processing-items` exposes the MVP privacy processing table.
- The initial implementation is a static backend response; admin replacement remains planned.
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
- Terms and order policy versions are `2026-08-02`; the privacy policy version is `2026-08-04`. Changing a required version requires a new customer agreement.

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

B-080 implementation note:

- The checkout response exposes the shared shipping-address snapshot and the server-owned policy evidence.
- The client returns only the displayed versions; the server validates them and persists its canonical notice text.
- Checkout policy confirmation locks direct customer address changes for the payment group and resulting orders.

## Modeling Notes

## Final MVP State Sets

### Order.status

- `PAYMENT_PENDING`: 현재 MVP에서는 입금대기 주문. 공급처 발주 대상이 아니다.
- `EXPIRED`: 입금 기한이 지나 종료된 주문.
- `PAYMENT_EXCEPTION`: 과거 PG 데이터 호환을 위해 남긴 legacy 상태. 신규 주문에는 사용하지 않는다.
- `SUPPLIER_ORDER_PENDING`: 결제 검증 완료 후 공급처 발주 전 주문.
- `SUPPLIER_ORDERED`: 공급처 발주 완료 후 송장 입력 전 주문.
- `OUT_OF_STOCK`: 공급처 품절로 고객 안내와 환불 처리가 필요한 주문.
- `SHIPPED`: 택배사와 송장번호가 입력되어 배송 중인 주문.
- `DELIVERED`: 배송 완료가 확인된 주문.
- `CANCELLED`: 미입금 주문 종료 또는 승인된 주문의 취소 처리 완료.
- `REFUND_REQUESTED`: 결제 승인 완료 주문의 환불 처리 중 상태.
- `REFUNDED`: 실제 수동 계좌환불 완료가 확인된 환불 완료 주문.

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

`PG_CANCEL_REQUESTED`, `RETRY_REQUIRED`, `MANUAL_REVIEW_REQUIRED`를 포함한 PG 환불 상태는 과거 데이터 조회 호환을 위해 보존한다. 새 계좌입금 주문과 수동 환불에서는 생성하지 않는다.

- 상품과 옵션에는 실제 재고 수량을 두지 않는다.
- 상품 전체 상태와 상품 옵션 상태를 분리한다.
- 고객이 구매할 수 있는 조건은 상품 상태가 `ACTIVE`이고 옵션 상태도 `ACTIVE`인 경우다.
- 상품이 `ACTIVE`여도 특정 옵션이 `SOLD_OUT`이면 해당 옵션은 구매할 수 없다.
- 상품 상세 콘텐츠는 `IMAGE`와 `HTML` 블록으로 구성하고 `sortOrder`에 따라 노출한다.
- `HTML` 블록은 XSS 방지를 위해 서버 저장 시점에 safelist 기반으로 sanitize해야 한다.
- 배송, 교환, 환불, 품절 가능성 같은 운영 정책 고지는 상품 상세 콘텐츠와 별도로 관리한다.
- 주문 상품에는 상품명, 옵션명, 가격을 스냅샷으로 저장한다.
- 도매꾹 자동 발주 대상 주문 상품에는 공급처 상품번호, 옵션코드, 공급가도 주문 시점 snapshot으로 저장한다.
- 주문 상품에는 상품 요약, 상품 상세 버전, 상품 정보 제공 고시 버전 참조도 스냅샷으로 저장한다.
- 상품 가격이 변경되어도 기존 주문 상품의 스냅샷 가격은 변경하지 않는다.
- 상품 상세 HTML/이미지 내용이 변경되어도 결제 완료 주문의 주문 상품 스냅샷은 변경하지 않는다.
- 주문은 입금 안내 전에 `PAYMENT_PENDING` 상태로 생성한다.
- `PAYMENT_PENDING` 주문은 관리자 입금확인 전이므로 공급처 발주 대상이 아니다.
- `PAYMENT_PENDING` 주문의 기본 입금 기한은 24시간이며, 미입금 취소는 관리자 수동 액션으로 처리한다.
- 결제 상태와 주문 상태를 같은 필드로 합치지 않는다.
- 결제 이벤트와 환불 이벤트는 멱등 처리와 운영 대사를 위해 별도 이력으로 기록한다.
- MVP 결제 주 경로는 계좌입금과 관리자 입금확인이다.
- MVP에서는 배송 그룹 주문 단위 부분 취소/부분 환불을 지원한다.
- 상품, 옵션, 수량 단위 부분 취소/부분 환불은 MVP에서 지원하지 않는다.
- 하나의 결제 그룹(PaymentGroup)은 여러 배송 그룹 주문을 포함할 수 있다.
- 하나의 배송 그룹 주문은 하나의 결제 그룹(PaymentGroup)에 속한다.
- 하나의 결제 그룹(PaymentGroup)에 일부 주문만 환불되면 결제 그룹은 `PARTIALLY_REFUNDED`가 될 수 있다.
- 결제 승인 완료 주문은 실제 수동 계좌환불 완료 후에만 `REFUNDED`가 될 수 있다.
- PG 취소/환불 상태값은 과거 데이터 조회 호환 전용이며 새 주문 흐름에서는 생성하지 않는다.
- 고객 직접 취소는 `SUPPLIER_ORDER_PENDING` 상태이면서 공급처 발주 작업이 시작되지 않은 주문에만 허용한다.
- 공급처 발주 작업 시작 후 배송 전 취소는 `Claim`으로 접수하고 관리자 수동 심사로 처리한다.
- 배송 후 반품/교환은 `Claim`으로 접수하고 관리자 수동 심사로 처리한다.
- 단순 변심 반품/교환 클레임은 배송 완료일로부터 7일 이내 접수된 건만 심사한다.
- 상품 하자, 오배송, 상품 정보와 다름, 배송 문제 클레임은 배송 완료일로부터 3개월 이내이면서 고객이 그 사실을 안 날 또는 알 수 있었던 날부터 30일 이내 접수된 건만 심사한다.
- 상품 하자, 오배송, 상품 정보와 다름, 배송 문제 클레임은 사진 증빙을 필수로 저장한다.
- 클레임 상태와 환불 상태는 분리하고, 클레임 승인 후 수동 계좌환불은 `Refund`에서 처리한다.
- 고객 직접 배송지 변경은 `SUPPLIER_ORDER_PENDING` 상태라도 `addressLockedAt`이 기록되면 거절한다.
- 공급처 발주 작업 시작은 새 주문 상태를 추가하지 않고 `supplierOrderStartedAt`과 `addressLockedAt`으로 기록한다.
- 공급처 발주 증빙으로 공급처 주문번호, 발주 주소 스냅샷, 발주 관리자, 예상 출고일, 공급처 응답 메모를 기록한다.
- 도매꾹 자동 발주는 고객 입금확인 후에만 시작하고, 주문 직전 공급처 가격·옵션·배송비와 e-money 잔액을 확인한다.
- 외부 주문 응답이 불명확하면 자동 재시도하지 않고 `RECONCILIATION_REQUIRED`로 두어 구매 주문 목록과 대사한다.
- 고객 계좌환불과 도매꾹 주문취소/e-money 반환은 서로 다른 상태와 증적으로 관리한다.
- 공급처 발주 후 2영업일 이상 출고 예정이 불명확하면 고객 지연 안내 대상으로 관리한다.
- 배송 후 반품/교환은 클레임 접수와 관리자 수동 심사로 시작한다.
- 도매꾹 자동 발주 주문은 구매 주문 조회에서 택배사, 송장번호와 배송 상태를 동기화하고, 그 외 주문은 관리자가 직접 입력한다.
- `PREPARING_SHIPMENT`은 MVP 주문 상태에서 제거하고 공급처 발주 완료 후 송장 입력 전 구간은 `SUPPLIER_ORDERED`로 표현한다.
- 공급처 주문 조회 실패에 대비해 배송 상태 수동 보정과 상태 변경 이력이 필요하다.
- MVP 배송은 주문 1개당 배송 1개로 시작하고 부분 출고/분할 배송은 제외한다.
- 공급처 주문 동기화는 관리자 수동 보정 상태를 임의로 덮어쓰거나 뒤로 되돌리지 않는다.
- MVP에서는 고객에게 별도 배송비를 청구하지 않으며 `shippingFee`는 `0`으로 시작한다.
- MVP에서 한 주문은 하나의 배송 그룹만 포함한다.
- 배송 그룹은 공급처 기준으로 나누지만 고객 화면에는 공급처 대신 배송 그룹으로 표시한다.
- 고객 화면에는 내부 주문 상태를 그대로 노출하지 않고 고객용 표시 상태로 매핑한다.
- 고객 주문 내역에는 관리자 입금확인 후 확정된 주문만 노출한다.
- `PAYMENT_PENDING`, `EXPIRED`, 결제 실패, 미입금 취소 주문은 일반 고객 주문 내역이 아니라 체크아웃 화면 또는 고객 문의에서 다룬다.
- 공급처 발주 상태는 주문 상태와 분리하되, 고객에게 보여줄 주문 상태와 동기화 규칙을 둔다.
- 주문 상태 변경 이력은 MVP부터 별도 테이블에 기록한다.
- 주문 상태 변경 이력은 action, guard result, side effect summary를 남긴다.
- 입금대기, 결제 완료, 품절, 배송 시작, 배송 완료, 지연 안내, 클레임 상태 변경, 환불 완료는 `NotificationLog`에 기록한다.
- 관리자 주문 액션은 현재 상태에서 허용된 다음 액션으로만 실행한다.
- 자동 상태 되돌리기 버튼은 MVP에서 제공하지 않고, 잘못된 상태 변경은 관리자 정정 액션과 이력으로 처리한다.
- 취소, 환불, 품절, 배송 수동 보정, 관리자 정정 액션은 사유를 필수로 기록한다.
- 상품 변경 이력은 가격, 상품/옵션 판매 상태, 공급처, 상품/옵션 기본 정보, 이미지, 상세 블록, 고시 변경을 MVP에서 기록한다.
- 상품 상세 HTML diff, 이미지 변경 diff, 상품명/요약문 상세 diff처럼 필드별 상세 diff는 MVP 이후로 미룬다.
- 이용약관, 개인정보처리방침, 배송 정책, 취소/환불 정책은 버전과 시행일을 가진다.
- 첫 가입 또는 첫 소셜 로그인 완료 시 이용약관과 개인정보처리방침 동의 이력을 저장한다.
- 소셜 로그인 필수 저장 항목은 제공자, 제공자 user id, 표시 이름으로 시작한다.
- 소셜 제공자가 이메일을 내려주는 경우 이메일을 저장할 수 있지만, 소셜 이메일은 필수 항목으로 두지 않는다.
- 이메일 또는 전화번호 같은 고객 연락처는 소셜 로그인 필수 항목이 아니라 주문, 배송, 클레임에 필요한 시점에 서비스 화면에서 수집한다.
- 회원 탈퇴 시 고객 프로필과 소셜 계정 연결은 삭제 또는 비식별화한다.
- 법정 보존 대상 주문, 결제, 배송, 클레임, 정책 동의 기록은 탈퇴 후에도 보존한다.
- MVP에서는 별도 `LegalRetentionRecord` 색인 테이블을 만들지 않고 비식별화된 유저 참조로 거래 기록을 보존한다. 보존 사유와 보존 만료일 색인은 후속 법정 보존 자동화 범위에서 추가한다.
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
- 고객 문의는 `CustomerInquiry`로 저장하고 관리자 화면에서 상태, 메모, 최신 답변을 관리한다.
- 답변 이메일 실패는 문의 답변 저장을 되돌리지 않으며 고객 조회 링크에서 저장된 답변을 확인할 수 있다.

## CustomerInquiry

사이트 고객 문의 접수 기록이다.

Implemented fields:

- id
- customerName
- email
- phone
- subject
- message
- status: RECEIVED / IN_PROGRESS / ANSWERED / CLOSED
- consentPolicyVersion
- consentedAt
- retentionExpiresAt
- adminMemo
- answer
- handledByAdminId
- answeredAt
- closedAt
- createdAt
- updatedAt

Rules:

- 비로그인 고객도 문의를 접수할 수 있다.
- 같은 이메일은 10분 동안 최대 3건까지 접수할 수 있다.
- 고객 조회는 문의 id와 HMAC 조회 토큰을 함께 검증하며 연락처와 관리자 메모를 반환하지 않는다.
- 관리자만 문의 목록, 상세, 상태, 메모, 답변을 관리할 수 있다.
- 문의와 동의 증적은 접수일로부터 3년 후 자동 삭제하며, 연결된 알림 로그는 수신 이메일과 답변 snapshot을 익명화한 뒤 보존한다.
