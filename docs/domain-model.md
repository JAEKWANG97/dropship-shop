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
- sourceAutoSoldOut: durable provenance for a source-sync `ACTIVE -> SOLD_OUT` transition; V40 default/backfill false
- basePrice
- minimumOrderQuantity: minimum purchasable quantity, 1-99, defaults to 1
- orderQuantityStep: allowed quantity increment, 1-99, defaults to 1
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
- Customer quantity must be at least `minimumOrderQuantity` and be divisible by `orderQuantityStep`.
- Default sale price is calculated from the active pricing policy, currently supplier cost plus 25% and rounded to the nearest 100 KRW.
- Scheduled sync updates current product price, MOQ, order step and options; existing order snapshots never change. The `sourceItemNo` used to fetch must still exactly match the fresh locked Product for both success apply and failure recording. Manual `HIDDEN`, `STOPPED`, and `SOLD_OUT` states are not overridden. Sync sets `sourceAutoSoldOut=true` only when confirmed unavailability actually applies `ACTIVE -> SOLD_OUT`; target/recovery includes only this marker-backed state. Recovery requires source MOQ at most 10, a positive capped current price, compliance other than `REJECTED`, an active option, canonical thumbnail, and active notice, and clears the marker on success. Every successful admin status command also clears it, even when the requested status is already `SOLD_OUT`.
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
- Implemented B-101 adds `DETAIL` to ProductImage type and nullable `storageObjectKey`. New supplier uploads always use a server-generated, globally single-use key; legacy/external URL rows keep it null.
- Portal ProductDetailBlock `IMAGE` stores an owned `productImageId` whose type is `DETAIL` instead of accepting an arbitrary URL or key. This keeps thumbnail, gallery, and up to 50 detail-image binaries under one ownership and cleanup path.

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
- productImageId: Implemented B-101 portal IMAGE block FK to an owned ProductImage of type DETAIL
- htmlContent
- sortOrder
- altText
- createdAt
- updatedAt

Implemented B-101 keeps legacy/admin URL blocks readable. New supplier IMAGE blocks require `productImageId`, derive `imageUrl` from that row, and cannot reference another Product or supplier. A referenced DETAIL image cannot be deleted until the block is removed/replaced in the same Product transaction.

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
- 수량은 상품의 현재 최소주문수량 이상, 주문단위의 배수, 최대 99개로 제한한다.
- 같은 옵션을 다시 담을 때는 요청 수량이 아니라 합산된 최종 수량을 검증한다.
- 상품 또는 옵션이 장바구니에 담긴 뒤 품절/숨김/판매중지되어도 항목은 남긴다.
- 상품 MOQ가 바뀌어 기존 수량이 무효가 되어도 자동 보정하지 않고 Checkout을 차단한다.
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
- actualDepositorName
- actualDepositAmount
- depositReceivedAt
- depositTransactionReference
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
- B-068 deposit-mismatch memo rows remain readable as history. Implemented B-102 uses the payment-group exception/refund model below for a newly identified amount-mismatched receipt instead of keeping it memo-only in `PAYMENT_PENDING`.

## Payment

결제 기록. 현재 MVP 주 경로는 계좌입금이다. 과거 PG 데이터는 조회 호환을 위해 기존 enum과 컬럼을 유지한다.

Implemented fields:

- id
- paymentGroupId
- provider: BANK_TRANSFER (현재 생성값), TOSS_PAYMENTS (과거 기록 호환 전용)
- providerPaymentKey
- method: BANK_TRANSFER (현재 생성값), CARD / EASY_PAY / TRANSFER (과거 기록 호환 전용)
- status: 현재 계좌입금 흐름은 `APPROVED`, B-102 `PAYMENT_EXCEPTION`과 수동 환불 관련 상태를 생성한다. 나머지 PG 상태값은 과거 기록 호환 전용이다.
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
- Implemented B-102 uses `status=PAYMENT_EXCEPTION`, `exceptionReason=AMOUNT_MISMATCH`, `requestedAmount=PaymentGroup.totalAmount`, and null approved amount/time for a received but non-approvable mismatched deposit. The actual received amount remains on the linked PaymentGroup receipt evidence and is the Refund amount.

## PaymentEvent

계좌입금 관리자 처리와 환불 이력. 과거 PG 이벤트 값은 조회 호환을 위해 enum에 남긴다.

Implemented fields:

- id
- paymentId
- paymentGroupId
- orderId: nullable; an order-scoped payment/refund command retains the target Order id, while a `PAYMENT_GROUP` amount-mismatch command keeps it null
- providerPaymentKey
- eventType: CONFIRM_REQUESTED / CONFIRM_APPROVED / CONFIRM_REJECTED / PAYMENT_EXCEPTION / PAYMENT_EXCEPTION_CANCEL_REQUESTED / PAYMENT_EXCEPTION_CANCEL_COMPLETED / PAYMENT_EXCEPTION_CANCEL_FAILED / TOSS_WEBHOOK_RECEIVED / PAYMENT_REVIEW_REQUIRED / BANK_TRANSFER_DEPOSIT_CONFIRMED / BANK_TRANSFER_UNPAID_CANCELLED / BANK_TRANSFER_DEPOSIT_MISMATCH_RECORDED / BANK_TRANSFER_LATE_DEPOSIT_RECORDED / REFUND_REQUESTED / REFUND_COMPLETED / MANUAL_REFUND_COMPLETED / REFUND_FAILED
- idempotencyKey
- commandType: nullable; populated for B-102 bank-transfer command replay rows
- requestHash: nullable; populated with `commandType`
- resultSnapshot: nullable ADMIN-safe immutable result JSON; populated with `commandType`
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
- The implemented refund scopes are the delivery-group order and the B-102 `PAYMENT_GROUP` amount-mismatch exception.
- Implemented B-102 adds exactly one `PAYMENT_GROUP` Refund for `PAYMENT_AMOUNT_MISMATCH`. Its `refundAmount` equals the positive actual received amount rather than PaymentGroup total or any Order amount, and all included Order ids are resolved through the PaymentGroup.
- A qualifying unpaid-cancelled exact receipt keeps `DELIVERY_GROUP_ORDER` scope: it creates one `LATE_DEPOSIT_EXCEPTION` Refund for every immutable Order amount, and those amounts sum to the exact received PaymentGroup total. Order-by-Order completion may leave the Payment/PaymentGroup `PARTIALLY_REFUNDED` until all are complete.
- Refund creation and completion require `Refund.paymentGroup`, linked `Payment.paymentGroup`, and any linked `Order.paymentGroup` to be the same locked aggregate. V41 composite foreign keys enforce this at the database boundary; a service mismatch returns `409` before money or state mutation.
- Customer/admin Order refund summaries must resolve a `PAYMENT_GROUP` Refund through `paymentGroupId` for every included Order instead of assuming `Refund.orderId` is always present. Existing delivery-group Refund projections remain unchanged.
- Actual manual bank-transfer refund completion is required before an order can move to `REFUNDED`.
- If the payment group still has active orders after one delivery-group order refund, the payment group and payment become `PARTIALLY_REFUNDED`.
- A payment-group amount-mismatch refund never becomes partial: completion reduces the exception group's refundable amount from the actual received amount to zero and atomically moves its Payment, PaymentGroup, Refund, and every included Order to `REFUNDED`.

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
- Admin can list notification logs with `GET /api/admin/notifications?status=...` and currently retry failed or skipped legacy logs with `POST /api/admin/notifications/{notificationId}/retry`. Implemented B-100 rejects every invite-linked generic retry. Implemented B-103 permits supplier operational retry only for FAILED, non-null-recipient rows before creation+7 days after current portal/manager/time-valid-contract/verified-email/recipient revalidation; supplier SKIPPED/SENT, recipient-null, expired-window or lifecycle/contract-mismatch rows remain terminal even if the relationship later recovers.

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
- reason: required PII-free operational text before relationship cleanup, nullable afterward
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
- productId: live Product FK; Implemented B-101에서 nullable
- productOptionId: live ProductOption FK; Implemented B-101에서 nullable
- subjectProductId: Implemented B-101 immutable audit subject id
- subjectProductOptionId: Implemented B-101 nullable immutable audit subject id
- adminUserId
- changeType: PRICE / PRODUCT_STATUS / COMPLIANCE_STATUS / OPTION_STATUS / SUPPLIER / PRODUCT_BASE / ORDER_QUANTITY / OPTION_BASE / IMAGES / DETAIL_BLOCKS / NOTICE / PRODUCT_DELETED / OPTION_DELETED
- beforeValue
- afterValue
- reason
- createdAt

Implemented B-101 expands this existing history without dropping old rows. It adds immutable `subjectProductId`, nullable immutable `subjectProductOptionId`, nullable `actorUserId`, `actorType=ADMIN|SUPPLIER|SYSTEM`, `actorSupplierId`, `actorSystemCode`, and aggregate `beforeVersion`/`afterVersion`. Subject ids are backfilled from the current associations, history is queried by subject id, and live Product/ProductOption associations are nullable with `ON DELETE SET NULL`. Rows whose legacy `adminUserId` is the known zero-UUID source-sync sentinel backfill `actorType=SYSTEM`, `actorUserId=null`, `actorSystemCode=DOMEGGOOK_CATALOG_SYNC`; real user ids backfill ADMIN. New supplier rows use SUPPLIER and source jobs use SYSTEM, so the sentinel is never inserted into a new User FK. Existing admin history responses remain compatible.

B-101 history writers canonicalize before/after snapshots from an explicit allowlist of product, option, image, detail, notice, pricing, and review business fields. They never serialize a raw request, actor contact data, customer/order data, or arbitrary admin notes. Durable review/internal reasons and supplier-facing messages are bounded single-line PII-free text.

Hard delete appends `PRODUCT_DELETED` or `OPTION_DELETED` before removing the live row. Product deletion stores the current `beforeVersion`, null `afterVersion`, an allowlisted before snapshot, null after snapshot, and server reason `DRAFT_ABANDONED`; Option deletion increments the surviving Product aggregate, stores `beforeVersion=v`, `afterVersion=v+1`, and server reason `DRAFT_OPTION_REMOVED`. DELETE accepts no free-text reason. The immutable subject ids keep both histories queryable after the live FK is cleared; a random subject id with no live product or history still returns `404` from the admin history route.

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

B-100 implements the managed type `SUPPLIER_APPLICATION_PRIVACY` and exact-current-ACTIVE-version validation on public supplier application submit. Production still requires an active reviewed notice row; this notice remains separate from the customer privacy policy.

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

## Current-Compatible MVP State Sets And Portal Extensions

### Order.status

- `PAYMENT_PENDING`: 현재 MVP에서는 입금대기 주문. 공급처 발주 대상이 아니다.
- `EXPIRED`: 입금 기한이 지나 종료된 주문.
- `PAYMENT_EXCEPTION`: 현재 Order enum의 legacy 호환 상태다. Implemented B-102 portal late-deposit command는 이를 최종 Order 상태로 커밋하지 않고 exception 이력에만 남기며, Payment/PaymentGroup 증적과 자동 Refund를 기록한 같은 transaction에서 Order를 `REFUND_REQUESTED`로 끝낸다.
- `SUPPLIER_ORDER_PENDING`: 결제 검증 완료 후 공급처 발주 전 주문.
- `TRACKING_REGISTERED`: Planned B-104 portal 주문에서 송장은 등록됐지만 실제 인계·배송완료는 확인되지 않은 상태.
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
- `PAYMENT_EXCEPTION`: Implemented B-102에서 실제 계좌입금은 확인됐지만 정상 주문으로 수용할 수 없어 환불로 보내는 증적 상태.

### Fulfillment.status

- `PENDING`
- `ORDERED`
- `OUT_OF_STOCK`
- `CANCELLED`

### Shipment.status

- `READY`
- `SHIPPED`
- `DELIVERED`
- `TRACKING_REGISTERED`: Planned B-104 portal Shipment의 송장 등록 상태.
- `VOIDED`: Planned B-104에서 중복·오류 송장을 삭제하지 않고 무효화한 상태.

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

- 현행 COREABLE-managed 상품과 옵션은 `UNTRACKED`로 유지해 실제 재고 수량을 두지 않는다. Implemented B-102 portal 옵션은 `TRACKED`의 on-hand/reserved ledger 또는 명시적 `UNTRACKED` mode를 사용한다.
- 상품 전체 상태와 상품 옵션 상태를 분리한다.
- 현행 COREABLE baseline의 구매 가능 조건은 상품 상태가 `ACTIVE`이고 옵션 상태도 `ACTIVE`인 경우다. Implemented portal 상품은 여기에 Supplier ACTIVE, time-valid VERIFIED 계약, supplier availability와 TRACKED available quantity guard를 모두 추가한다.
- 상품이 `ACTIVE`여도 특정 옵션이 `SOLD_OUT`이면 해당 옵션은 구매할 수 없다. Portal guard 중 하나라도 실패해도 고객 projection은 같은 구매불가/품절 경계로 닫힌다.
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

## Supplier Portal Extension

Status: `B-100` onboarding, lifecycle, application/invite retention and browser security, `B-101` catalog/review and V40 additive schema, `B-102` inventory/reservation/payment exception behavior and V41 additive schema, and `B-103` fulfillment/minimum-PII/operational-email behavior and V42 additive schema are Implemented. `B-098` contract evidence/expiry automation and relationship cleanup plus `B-104` through `B-105` remain Planned.

`B-100`은 기존 legacy 주문·배송 의미를 유지한 expand-contract 변경이다. 이후 slice도 같은 호환 경계를 따른다.

### Supplier Manager Authority (Implemented B-100)

`User.role`의 저장 값은 계속 `CUSTOMER` / `ADMIN`만 사용한다. 공급처 권한은 별도 저장 role이나 다인원 membership이 아니라 현재 활성 연결에서 파생한다.

Implemented `Supplier` fields (V39):

- managerUserId: nullable unique FK to `User`
- portalEnrolledAt: nullable immutable first-enrollment marker; legacy backfill remains null
- portalStatus: DISABLED / PENDING_ACTIVATION / ACTIVE / SUSPENDED
- contactEmailVerifiedAt
- portalContractStatus: UNVERIFIED / VERIFIED / EXPIRED / REVOKED
- portalContractVersion: nullable
- portalContractEffectiveAt: nullable
- portalContractExpiresAt: nullable
- portalContractVerifiedAt: nullable
- portalContractVerifiedByAdminId: nullable
- contactRetentionExpiresAt: nullable, required after permanent relationship closure once B-098 duration is configured
- contactAnonymizedAt: nullable

Rules:

- 기존 공급처는 `portalStatus=DISABLED`, `portalEnrolledAt=null`로 backfill하고 기존 `Supplier.status`의 `ACTIVE` / `INACTIVE` 거래·catalog 의미를 바꾸지 않는다. 승인으로 한 번 포털에 들어간 Supplier는 영구 DISABLED 뒤에도 marker를 보존해 legacy PATCH나 계약 없는 sales 재개 경로로 돌아가지 않는다.
- 한 공급처는 담당자 한 명만, 한 사용자는 공급처 한 곳만 관리한다. `managerUserId`의 nullable unique 제약으로 양방향 1:1을 보장한다.
- `ROLE_SUPPLIER`는 활성 사용자, `portalStatus=ACTIVE`와 manager 연결이 모두 유효할 때 요청 시점에 파생한다. Terminal 또는 already-overdue VERIFIED contract는 즉시 권한을 막고 최초 UNVERIFIED onboarding은 비PII catalog 작업만 허용한다. `Supplier.status`는 신규 판매·checkout만 막는 독립 gate이므로 `INACTIVE`여도 time-valid contract가 있을 때만 이미 입금확인된 주문을 계속 처리할 수 있다.
- 기존 CUSTOMER 또는 ADMIN 계정이 담당자가 되어도 저장 role과 기존 권한을 잃지 않는다.
- 포털 정지는 `portalStatus=SUSPENDED`만 적용하고, 담당자 연결 해제는 manager를 비우고 `portalStatus=PENDING_ACTIVATION`으로 전환한다. 기존 결제완료 portal 주문은 channel과 증적을 유지한 Coreable 인계 큐로 보내며 portal 재활성화가 이를 자동 재배정하지 않는다.
- 정지·연결 해제·연락 email 변경 명령은 `salesAction=KEEP|PAUSE`를 필수로 받아 판매 중지를 명시적으로 선택한 경우에만 `Supplier.status=INACTIVE`로 바꾼다. UI 기본값은 PAUSE지만 서버가 숨겨서 바꾸지 않는다. Implemented B-103은 `KEEP` 중 새 입금확인 주문을 portal이 다시 활성화될 때까지 `COREABLE_MANUAL` Fulfillment로 생성하고, portal 재활성화는 판매 상태를 자동 복구하지 않는다.
- 활성 담당자의 고객 셀프서비스 탈퇴는 먼저 관리자에게 공급처 연결을 해제받기 전까지 거절한다.
- 공급처 연락 email이 바뀌면 manager 연결과 `contactEmailVerifiedAt`을 지우고 `portalStatus=PENDING_ACTIVATION`으로 바꾼 뒤 미사용 초대를 폐기하고 재초대한다. 같은 명령의 필수 sales action으로 판매 유지 여부를 명시한다.
- 판매상태 변경은 portal 재활성화와 분리된 관리자 명령으로만 `Supplier.status=ACTIVE|INACTIVE`를 명시한다. reason과 idempotency key가 필수이며 판매 재개가 인계된 주문이나 portal 상태를 자동 복구하지 않는다.
- `SUSPENDED -> ACTIVE` portal 재활성화는 retained active manager, verified contact email과 time-valid VERIFIED contract를 요구한다. Contract 재검증만으로 portal/sales를 바꾸지 않는다.
- 신규 CREATE_NEW Supplier는 `status=INACTIVE`, `portalContractStatus=UNVERIFIED`로 시작한다. B-100은 denormalized contract columns/default와 fail-closed sales guard를 소유하고, B-098은 history/command/evidence/expiry index와 scheduler를 소유한다. B-098이 supplier-unique version의 current evidence를 `VERIFIED`로 기록한 뒤에만 sales-status ACTIVE를 허용한다. Current는 `effectiveAt <= now`이고 expiresAt이 없거나 `now < expiresAt`인 경우다. Sales activation과 checkout은 Supplier를 잠그고 overdue VERIFIED를 lazy EXPIRED로 바꾼 뒤 아래 terminal routine을 실행해 scheduler 지연 판매와 PII 접근을 막는다. Global flag가 열린 뒤에도 portal 상품 public query/checkout은 supplier ACTIVE와 time-valid VERIFIED를 모두 재검증한다.
- Supplier 운영 연락 PII는 관계 이행 중에만 유지한다. Production 전 B-098/privacy notice가 post-relationship duration을 확정해야 하며, 영구 portal DISABLED + trade INACTIVE + open fulfillment/Claim/Refund 없음이 모두 성립하면 `contactRetentionExpiresAt`을 계산한다. 기한 뒤 scheduler는 Supplier를 잠그고 같은 lifecycle/open-work 조건을 다시 확인한다. 새 open work가 있으면 deadline을 clear/defer하고, 모두 여전히 참일 때만 Supplier contact name/phone/email/memo, approved application 중복 PII와 replay material을 함께 지우고 시각을 기록한다. 주문·계약 법정 원장은 별도 보존한다.

Implemented `SupplierPortalActionHistory` fields (V39):

- id
- supplierId
- actorAdminId
- action: INVITE_REISSUED / INVITE_REVOKED / PORTAL_SUSPENDED / PORTAL_REACTIVATED / PORTAL_DISABLED / MANAGER_DISCONNECTED / CONTACT_EMAIL_CHANGED / SALES_STATUS_CHANGED
- beforePortalStatus / afterPortalStatus
- beforeSalesStatus / afterSalesStatus
- salesAction: nullable KEEP / PAUSE
- reason
- requestHash: nullable after relationship cleanup; server-keyed HMAC when the command contains contact email
- idempotencyKey: nullable after relationship cleanup
- resultSnapshot: nullable after relationship cleanup; immutable ADMIN-safe canonical result before cleanup
- createdAt

Portal-status, manager-disconnect, contact-email, and sales-status commands lock the Supplier and append this history in the same transaction. Partial unique `(supplierId, idempotencyKey)` applies while key is non-null; an identical retry returns the first result and a different payload with the same key is rejected. Contact/reissue/disable paths follow `Supplier -> Invite(id) -> User/manager -> Fulfillment`; callback follows the same prefix, preventing Invite/Supplier inversion. Deposit confirmation and late-deposit commands use PaymentGroup -> affected Suppliers -> Products -> every affected ProductOption including UNTRACKED -> Orders/Fulfillments, each group ordered by id. Lifecycle-only commands never acquire PaymentGroup/Product/Option locks. Catalog/inventory saleability writers use Supplier when needed, then Product -> Option, and never lock Supplier after Product. At B-098 relationship cleanup the contact-bearing reason/request HMAC/key/result may be nulled as a privacy exception to append-only business events; action, state transition, actor admin and time remain. Payment processing rechecks locked trade/catalog/availability state before routing or exception.

Planned B-098 `SupplierPortalContractHistory` fields:

- id
- supplierId
- status: VERIFIED / EXPIRED / REVOKED
- contractVersion: new version for VERIFIED; target current version for EXPIRED/REVOKED
- expectedCurrentContractVersion: command-only concurrency guard, null only for first verification
- effectiveAt
- expiresAt: nullable future time for VERIFIED
- evidenceReference: ADMIN-only non-secret registry reference
- actedByAdminId: nullable for expiry scheduler
- reason
- requestHash: nullable for scheduler
- idempotencyKey: nullable for scheduler
- resultSnapshot: ADMIN-safe immutable result
- createdAt

Contract status is changed only through this append-only B-098 command. Every admin command carries `expectedCurrentContractVersion` and locks Supplier. VERIFIED requires the expected value (including null initially) to match current, a supplier-unique new version/evidence, `effectiveAt <= now`, and null/future `expiresAt`. EXPIRED/REVOKED require current status VERIFIED, record the non-null target current version, and permit only one terminal event for that version. The expiry scheduler compares candidate status/version/expiry after locking and no-ops if terminal processing or re-verification already won. Terminal status and every lazy expiry share one routine: set sales INACTIVE; change ACTIVE portal to SUSPENDED while retaining manager; revoke any open invite; and move all still-SUPPLIER-owned open portal Fulfillments to COREABLE with `CONTRACT_EXPIRED|CONTRACT_REVOKED` evidence. A pending activation stays unauthorized but loses its invite. Paid-work list/detail/mutation and effective Claim-grant access recheck time-valid VERIFIED directly, so an overdue row cannot expose PII before this routine commits. Same supplier/key/hash replays its result and changed payload conflicts. Re-verification uses a new version/key but does not reactivate portal/sales or restore ownership. Supplier/customer projections never expose evidence.

### SupplierApplication (Implemented B-100)

외부 업체가 로그인 없이 제출하는 공급처 신청이다.

Implemented fields (V39):

- id
- supplierName: nullable after retention cleanup
- contactName: nullable after retention cleanup
- contactEmail: nullable after retention cleanup
- normalizedContactEmail: nullable after retention cleanup
- contactPhone: optional, nullable after retention cleanup
- memo: optional, nullable after retention cleanup
- idempotencyKey: nullable after retention cleanup
- requestHash: nullable server-keyed HMAC of canonical PII payload
- consentPolicyVersion
- consentedAt
- status: SUBMITTED / APPROVED / REJECTED / EXPIRED
- reviewedByAdminId
- reviewReasonCode: allowlisted
- reviewReason: nullable temporary PII-free internal text
- reviewedAt
- approvedSupplierId
- reviewAction: nullable APPROVE / REJECT
- approvalMode: nullable CREATE_NEW / LINK_EXISTING
- requestedExistingSupplierId: nullable
- reviewIdempotencyKey: nullable after retention cleanup
- reviewRequestHash: nullable server-keyed HMAC of the canonical review command
- reviewResultSnapshot: nullable immutable ADMIN-safe result
- retentionExpiresAt
- anonymizedAt
- createdAt
- updatedAt

Rules:

- 공개 신청은 active `SUPPLIER_APPLICATION_PRIVACY` 고지를 먼저 읽고 명시적 동의와 정확한 서버 정책 버전·시각을 함께 저장한다. 버전 불일치는 거절하며 서버가 canonical consent time을 기록한다.
- 관리자가 승인 또는 거절하며 처리 사유를 필수로 기록한다.
- 같은 정규화 연락 email의 non-expired SUBMITTED 또는 APPROVED 신청은 합쳐서 하나만 허용한다. 같은 idempotency key와 request hash의 재요청은 기존 신청을 반환하고, key를 다른 payload에 재사용하면 존재 여부를 노출하지 않는 generic conflict를 반환한다. 새 submit은 normalized-email 경계에서 matching SUBMITTED를 잠그고 deadline이 지났으면 EXPIRED cleanup을 먼저 적용한 뒤 duplicate를 판단해 scheduler lag를 흡수한다. REJECTED/EXPIRED의 원문 email 익명화 뒤 새 신청은 허용한다.
- 승인·거절은 application row를 잠그고 action, key, canonical keyed-HMAC, approval mode/target/reason과 immutable result를 상태 전이와 함께 저장한다. 같은 action/key/hash만 최초 결과를 반환하며 key 또는 payload가 다르거나 반대 action이면 conflict여서 공급처나 초대를 중복 생성하지 않는다.
- 이름이나 email만으로 기존 Supplier/User와 자동 병합하지 않는다.
- 사람의 상태 전이는 `SUBMITTED -> APPROVED|REJECTED`만 허용하고 terminal 상태 사이 전이는 금지한다. 생성 시 `retentionExpiresAt=createdAt+90일`이며 미검토 상태가 그 시각에 도달하면 EXPIRED로 바꾸고 cleanup한다. Review도 row lock 뒤 deadline을 재검사해 `now >= retentionExpiresAt`이면 scheduler와 같은 EXPIRED cleanup을 먼저 commit하고 `APPLICATION_EXPIRED`로 거절하므로 scheduler 지연이 만료 신청을 승인시키지 않는다. terminal replay는 동일 action/key/hash에만 최초 result snapshot을 반환한다.
- 승인은 `CREATE_NEW|LINK_EXISTING`을 명시한다. 신규 Supplier 생성값은 판매대기 `Supplier.status=INACTIVE`, `portalContractStatus=UNVERIFIED`, 초대 대기 `portalStatus=PENDING_ACTIVATION`이다. 기존 Supplier 연결은 manager, invite, application link, portal lifecycle history가 한 번도 없는 legacy `portalStatus=DISABLED` 대상을 관리자가 id로 명시하며 현재 거래 상태를 자동 변경하지 않는다. 영구 종료된 이전 portal supplier는 새 public application으로 재연결할 수 없다. 대신 approved application의 normalized contact email을 Supplier 연락 email로 원자적으로 동기화하고 검증시각을 비운 뒤 그 동일 주소로 초대해, 다른 주소를 검증한 것으로 잘못 기록하지 않는다. 어느 approval mode도 portal 상품 계약 검증을 대신하지 않는다.
- REJECTED는 `retentionExpiresAt=reviewedAt+90일`로 다시 정하고 EXPIRED는 최초 `createdAt+90일` deadline을 사용한다. 두 상태의 cleanup은 application의 공급처명·담당자명·email·정규화 email·전화·memo·temporary review reason, submit/review idempotency key·request HMAC·review result snapshot을 null 처리하고 consent version/time, terminal status, review action/mode, allowlisted reason code와 reviewer/time만 보존한다. APPROVED contact data는 Supplier 운영 기록이 되며 B-098 post-relationship retention deadline에 Supplier와 application 중복 PII 및 review replay material을 함께 정리한다. request hash는 cleanup 전에도 plain hash가 아닌 server-keyed HMAC다. cleanup 뒤 같은 public key replay는 새 신청으로 취급하며 CREATE_NEW approval은 current Supplier 연락처 충돌을 별도로 거절한다.
- Review reason code는 `APPLICATION_APPROVED`, `INCOMPLETE_INFORMATION`, `OUT_OF_SCOPE`, `POLICY_NOT_MET`, `DUPLICATE_OR_EXISTING_RELATIONSHIP`만 허용한다. 승인에는 첫 code, 거절에는 나머지만 허용하고 internal reason은 PII 입력을 거절한다.
- Production `APP_SUPPLIER_PORTAL_ENABLED=false`에서는 ADMIN/application scope와 기존 review key/hash/result replay 조회 뒤 새 신청 승인처럼 초대를 생성하는 command가 application/Supplier/invite를 바꾸기 전에 `SUPPLIER_PORTAL_NOT_RELEASED`로 거절된다. 이미 완료된 동일 command는 token-free 저장 결과만 반환하고 재발송하지 않으며 신청 거절은 계속 허용한다.

### SupplierInvite (Implemented B-100)

관리자 승인을 받은 공급처의 담당자 한 명을 Kakao 계정에 연결하기 위한 1회용 email 초대다.

Implemented fields (V39):

- id
- supplierId
- recipientEmail: nullable after terminal retention cleanup
- tokenDigest
- issuanceIdempotencyKey: nullable after terminal retention cleanup
- issuanceRequestHash: nullable server-keyed HMAC when recipient email is included
- expiresAt
- consumedAt
- consumedByUserId
- revokedAt
- revokedByAdminId
- revocationReasonCode: nullable allowlist `DELIVERY_FAILED` / `INVITE_EXPIRED` / `RECIPIENT_CHANGED` / `ADMIN_REISSUE`
- recipientRetentionExpiresAt
- recipientAnonymizedAt
- createdByAdminId
- createdAt

Rules:

- 최소 256-bit 무작위 token 원문은 연락 이메일 검증용 email에 한 번만 사용하고 DB와 application/access log에는 저장하지 않는다. DB에는 unique digest만 저장하며 메일에는 token/link와 일반 연결 안내 외 운영 내용을 넣지 않는다.
- 초대 기본 유효기간은 7일이다. 재발급은 기존 미사용 초대를 먼저 폐기하며 공급처마다 사용 가능한 초대는 하나뿐이다.
- 승인 발급은 application 기반 deterministic key를 사용한다. 관리자 재발급 command는 요청 `Idempotency-Key`와 allowlisted `revocationReasonCode`를 저장하되, invite issuance key는 `reissue:` server-HMAC namespace로 파생해 `application:` 최초 발급 key와 충돌하지 않게 한다. 재발급은 free-text reason을 받지 않는다. Scoped key/hash replay 조회 뒤 새 재발급은 Supplier lock에서 `portalStatus=PENDING_ACTIVATION`, manager 없음, current contact email 존재, 미검증 상태만 허용한다. ACTIVE/SUSPENDED/DISABLED 또는 manager-bound 상태는 거절하고, contact change는 먼저 pending/unverified 상태를 원자적으로 만든다. `(supplierId, issuanceIdempotencyKey)`는 unique이며 동일 payload retry는 기존 invite metadata를 반환하고 key를 다른 payload에 재사용하면 거절한다. 새 key의 명시적 재발급만 이전 open invite를 폐기한다.
- Global supplier-portal flag가 false이면 scoped key/hash/result replay 조회 뒤 새 승인 발급, 재발급과 연락 이메일 변경 뒤 후속 발급은 invite/lifecycle mutation 전에 fail closed한다. 동일 완료-command replay는 token-free result만 반환하고 발송하지 않는다. After-commit dispatch도 발송 직전에 flag를 다시 확인해 `SKIPPED/PORTAL_NOT_RELEASED`로 끝내며, 다시 연 뒤에는 새 key로 재발급해야 한다. Portal 정지/종료와 retention cleanup은 이 gate 뒤에도 가능하다.
- email 링크는 URL fragment로 token을 전달한다. Web은 token을 즉시 교환하고 fragment를 제거한 뒤 짧은 수명의 HttpOnly invite-context cookie만 사용한다.
- 초대 email은 연락 email을 검증하기 위한 유일한 pre-verification 발송 예외다. 그 밖의 운영 email은 `contactEmailVerifiedAt`이 있어야 한다.
- supplier invite context에서는 `KAKAO`만 허용하고 기존 Google/Naver callback으로 초대를 소비할 수 없다.
- OAuth `state`와 invite context를 함께 검증한 뒤 invite row를 write lock으로 읽는다.
- callback은 digest를 비잠금 조회해 Supplier id만 해석한 뒤 공통 `Supplier -> SupplierInvite(id) -> User/manager` 순서로 잠그고 digest·binding/state·만료/폐기/소비·recipient·manager 공석을 재검증한다. Contact change, portal disable과 reissue도 Supplier를 Invite보다 먼저 잠근다. 성공 시 active Kakao User 조회 또는 생성, manager 연결, `contactEmailVerifiedAt` 기록, `portalStatus=ACTIVE`, invite 소비를 한 트랜잭션에서 처리한다.
- Kakao email과 `recipientEmail`의 일치는 요구하지 않는다. 초대 token 소유와 Kakao provider user id가 각각 연락 email 검증과 로그인 식별 근거다.
- 만료·폐기·이미 소비된 초대와 이미 다른 공급처에 연결된 사용자는 거절한다. 동시 callback 중 하나만 성공할 수 있다.
- Invitation notification audit에는 invite id, recipient, template, expiry와 delivery result만 저장하고 raw token이나 token-bearing link는 넣지 않는다. Raw token은 after-commit send context에만 존재하며 발송 유실/실패는 generic resend가 아니라 새 key의 revoke/reissue로 복구한다.
- 소비·폐기·만료 중 가장 먼저 성립한 terminal 시각 +30일에 recipientEmail, issuance idempotency key/HMAC와 연결 NotificationLog recipient를 null 처리하고 `recipientAnonymizedAt`을 남긴다. `consumedByUserId`는 B-098 관계 종료 보관기한 뒤 null 처리하며 digest와 terminal/action 비PII audit은 보존한다.

### Supplier Product Review (Implemented B-101)

Implemented `Product` fields:

- managementChannel: COREABLE / SUPPLIER_PORTAL
- version: optimistic aggregate version
- firstSubmittedAt: nullable immutable first submit time; null means the portal draft has never entered review/publication
- pricingPolicyIdApplied
- pricingPolicyVersionApplied
- reviewStatus: DRAFT / AUTO_APPROVED / REVIEW_REQUIRED / SUPPLEMENT_REQUESTED / APPROVED / REJECTED
- reviewReasonCode: nullable allowlisted supplier-facing code
- supplierReviewMessage: nullable supplier-facing single-line PII-free message up to 500 characters, separate from internal admin notes

Rules:

- B-101에서 공급처는 자기 상품의 이름, 요약, 공급가, 옵션 공급가, 공급처 옵션코드, MOQ/주문단위, 이미지, 상세와 상품정보제공고시만 입력한다. 재고 모드와 수량은 B-102가 같은 편집 화면에 추가한다.
- 공급처 요청은 supplierId, 고객 판매가인 `basePrice`, 상품 판매 상태, compliance 상태와 review 상태를 받지 않는다.
- Coreable 서버가 active pricing policy로 고객 판매가를 계산한다.
- 가격 계산은 `basePrice=price(sourcePrice)`, `optionCustomerTotal=price(sourcePrice+sourceAdditionalPrice)`, `additionalPrice=optionCustomerTotal-basePrice`다. `price`는 동일 markup, resale-minimum floor와 rounding rule을 적용한다. B-101 이후 모든 writer는 `sourcePrice`와 `sourceAdditionalPrice`를 각각 `0..100,000,000` 정수 KRW, customer unit price를 `1..1,000,000,000`으로 제한하고 exact 합산·수량 곱을 사용한다. 제약 추가 전 legacy 범위 밖 row와 `basePrice+additionalPrice` 상한 초과를 스캔하고 발견하면 명시적 정정 승인 없이 migration을 진행하지 않는다. 승인된 비용 변경과 Portal 상품의 legacy admin 수정은 요청 고객가를 신뢰하지 않고 모든 고객 가격, applied policy id/version과 full calculator snapshot history를 원자적으로 갱신한다. B-101은 existing PricingPolicy에 monotonic version을 추가하고 in-place 정책 update마다 version을 증가시킨다.
- 무옵션 상품도 기존 `OrderItem.productOptionId` 필수 참조를 유지하기 위해 내부 `기본` 옵션 하나를 가진다.
- `DRAFT`는 여러 asset 요청을 잇는 내부 편집 상태다. 공급처 화면의 단일 `상품 등록` 동작이 submit과 분류를 함께 수행해 별도 승인 요청 단계를 만들지 않는다.
- 최초 submit은 `firstSubmittedAt`을 한 번만 기록한다. 승인·검토중 상품의 수정으로 다시 `DRAFT`가 되어도 이 값은 지우지 않는다.
- 구조와 판매 준비 조건을 통과한 일반 상품은 반드시 `AUTO_APPROVED`로 공개한다.
- 인증, category 또는 법정 필수정보 규칙이 사람 판단을 요구하면 `HIDDEN`과 `REVIEW_REQUIRED`로 Coreable 검토 큐에 보낸다.
- 공급처 projection은 `supplierDisplayStatus`, allowlisted `reviewReasonCode`, `supplierReviewMessage`을 매핑한 `reviewMessage`, derived `nextAction`만 검토 피드백으로 반환한다. REVIEW_REQUIRED/SUPPLEMENT_REQUESTED/REJECTED는 reason code가 필요하고, 보완·거절은 supplier-safe message도 필요하다. 내부 admin note, reviewer identity와 classifier trace는 제외하고 보완은 같은 등록 동작으로 재제출한다.
- 기존 `ProductComplianceStatus`와 legacy `PENDING` 공개 의미는 바꾸지 않는다. `CERTIFICATION_REVIEW`에 대한 Coreable `APPROVED`는 portal `reviewStatus`만 통과시키고 `complianceStatus`를 자동 변경하지 않으며, `PENDING`은 판매를 허용하고 `REJECTED`만 판매 준비를 차단한다.
- 상품·옵션·이미지·상세·고시·가격·검토 변경 이력은 actor type(`ADMIN` / `SUPPLIER` / `SYSTEM`), nullable actor user, supplier tenant 또는 system code, allowlisted business-field before/after, PII-free 사유와 시각을 기록한다. Raw request, actor contact, customer/order data와 arbitrary admin note를 snapshot에 복제하지 않는다. Domeggook/source sync는 SYSTEM이며 zero-UUID를 User FK나 ADMIN으로 위장하지 않는다.
- supplierReviewMessage와 internalReason은 각각 500자 이하 single-line이며 email, phone, address, customer identifier와 link를 거절한다. 공급처 actor user 연결은 B-098 관계 종료 보관기한 뒤 null 처리하되 actor type, supplier, version과 비PII action evidence는 유지한다.
- 기존 상품은 `managementChannel=COREABLE`로 backfill하고 portal 생성 상품만 `SUPPLIER_PORTAL`로 고정한다. 이 값은 공급처 payload로 변경할 수 없으며 B-102가 checkout snapshot과 호환 경로 판단에 사용한다.
- supplier product query/mutation은 supplier ownership뿐 아니라 `managementChannel=SUPPLIER_PORTAL`을 요구한다. LINK_EXISTING은 기존 COREABLE/Domeggook 상품을 공급처 편집 대상으로 자동 이전하지 않는다.
- supplier/admin detail과 mutation response는 `version`을 반환한다. review-relevant supplier mutation과 admin review action은 `expectedVersion`을 요구하고 성공 시 증가시키며 stale 요청은 아무 변경 없이 `409`다. Admin/review/cart/checkout/source writer는 scalar supplier/ownership discovery 뒤 `Supplier -> fresh Product -> ProductOption(id)` 순서로 잠그고, 대기 뒤 fresh owner가 discovery/request tenant와 다르면 conflict 또는 tenant-safe `404`로 끝낸다.
- 최초 DRAFT submit은 AUTO_APPROVED 또는 REVIEW_REQUIRED로 분류한다. REVIEW_REQUIRED의 admin 전이는 APPROVED/SUPPLEMENT_REQUESTED/REJECTED만 허용한다. SUPPLEMENT_REQUESTED는 공급처 편집 중에도 숨김을 유지하고 재제출 시 반드시 REVIEW_REQUIRED로 돌아간다. AUTO_APPROVED/APPROVED/REVIEW_REQUIRED의 review-relevant 수정은 즉시 HIDDEN/DRAFT로 바꾼 뒤 새 submit을 요구한다. REJECTED는 직접 재제출하지 않고 Coreable 문의로 끝낸다.
- 보완/거절은 supplier-safe reason code/message와 내부 reason을 분리하고 둘 다 위 PII-free validator를 통과시킨다. 모든 결정은 처리한 정확한 version과 actor를 변경 이력에 남긴다.
- B-103까지 배포된 상태에서도 production supplier portal feature gate를 닫는다. Implemented B-102 inventory/checkout guard와 B-103 fulfillment/privacy 기반은 필요조건일 뿐이며 Planned B-104~B-105, 개인정보 고지·실 email·계약 gate가 모두 준비되기 전에는 portal 상품 고객 구매와 외부 route를 열지 않는다.

B-101은 private 인증문서 파일을 수집하지 않고 structured category/notice, 기존 admin-managed compliance 상태와 validated public ProductImage만 검토한다. 별도 private 문서가 필요하면 retention/access 정책을 확정하는 후속 범위로 둔다.

Supplier draft deletion rules:

- Product hard delete는 현재 tenant가 소유한 `managementChannel=SUPPLIER_PORTAL`, `reviewStatus=DRAFT`, `firstSubmittedAt=null`인 상품에만 허용한다. 상품 또는 그 모든 Option을 참조하는 CartItem·OrderItem이 하나라도 있으면 거절한다.
- Option hard delete도 위 Product 단계에서만 허용하며 대상 Option의 CartItem·OrderItem 참조가 없고 최소 한 Option을 남겨야 한다. 제출·검토·공개 뒤에는 Product/Option을 삭제하지 않고 Coreable의 `HIDDEN`/`STOPPED` 상태로 보존한다. 일반 soft-delete tombstone은 추가하지 않는다.
- DELETE는 expected Product version을 요구한다. Service는 scalar ownership discovery 뒤 `Supplier -> fresh Product -> 모든 Option(id)` 순서로 잠그고 tenant/version/참조를 다시 확인한 뒤 deletion history를 append한다. Product hard delete는 DetailBlock -> ProductImage -> ProductOption/ProductNotice -> Product 순서로 metadata를 명시적으로 지운다. CartItem·OrderItem FK는 required/restrict로 유지한다.
- Cart 추가와 checkout의 CartItem·OrderItem 생성도 같은 잠금 계약 뒤 fresh ownership, resource/saleability와 참조 guard를 다시 확인한다. Stale ownership은 conflict 또는 tenant-safe `404`로 끝나며 dangling row나 raw FK 오류를 반환하지 않는다.
- Portal upload가 만든 ProductImage metadata는 single-use unique server-owned `storageObjectKey`를 보존한다. Admin thumbnail/gallery upload도 upload endpoint가 발급한 같은 Product의 URL/key pair만 metadata에 연결할 수 있고, cleanup job이 생긴 tombstone key는 pending/terminal 여부와 무관하게 재첨부할 수 없으며 reorder/replace에서 계속 유지된 key만 보존한다. Metadata 삭제와 제거된 immutable key의 unique cleanup job enqueue를 같은 transaction에 저장하고 반복 enqueue는 멱등 처리한다. Worker는 binary 삭제 직전 live metadata 참조를 검사해 있으면 삭제 없이 `COMPLETED/LIVE_REFERENCE`로 끝내고, 이후 그 metadata가 실제 제거되면 같은 job을 `PENDING`으로 다시 연다. Not-found는 성공으로 처리하고 실패는 재시도하며 삭제 metadata를 복구하지 않는다. Legacy/external URL은 server-owned key가 없으면 binary 삭제 대상이 아니다.

### Option Inventory And Reservation (Implemented B-102)

Implemented `ProductOption` fields (V41):

- supplierAvailability: AVAILABLE / UNAVAILABLE
- inventoryMode: TRACKED / UNTRACKED
- onHandQuantity
- reservedQuantity
- inventoryVersion: option-local monotonic inventory/reservation version

Implemented `OrderItem` fields (V41):

- managementChannelSnapshot: COREABLE / SUPPLIER_PORTAL
- inventoryModeSnapshot: TRACKED / UNTRACKED
- reservationStatus: NOT_APPLICABLE / HELD / CONSUMED / RELEASED
- reservedAt
- consumedAt
- releasedAt
- reacquiredAt: nullable late-deposit audit timestamp

Rules:

- 기존 COREABLE 옵션은 `supplierAvailability=AVAILABLE`, `UNTRACKED`, `onHandQuantity=null`, `reservedQuantity=0`으로 backfill한다. B-101에서 B-102 전에 생성된 SUPPLIER_PORTAL 옵션은 `TRACKED/onHandQuantity=0/reservedQuantity=0`으로 이관한다. B-102 이후 새 공급처 포털 옵션은 `TRACKED`가 기본이지만 공급처가 명시적으로 `UNTRACKED`를 선택할 수 있다.
- B-102 전 production portal 판매 gate가 닫혀 있었으므로 기존 OrderItem이 `SUPPLIER_PORTAL` Product를 참조하면 migration을 중단하고 수동 reconciliation을 요구한다. 그런 row를 COREABLE/UNTRACKED로 조용히 오분류하지 않는다.
- `supplierAvailability`는 공급처의 신규 주문 받기/중지 값이며 Coreable 소유 Product/Option sale status와 분리한다. UNAVAILABLE은 checkout을 막고 AVAILABLE은 Coreable 중지·숨김·안전 상태를 덮어쓰지 못한다.
- 공급처 UI의 TRACKED label은 `수량 관리 (권장)`이고 0 이상의 on-hand를 필수로 받는다. UNTRACKED label은 `재고 수량 관리 안 함`이며 on-hand 대신 `주문 받기`/`주문 중지`를 사용한다. 고객 projection은 두 모드 모두 구매 가능/품절만 반환한다.
- Portal checkout은 이미 구현된 `OrderItem.sourceUnitPrice`에 당시 option 공급가를 보존해 비용 원장을 이중화하지 않는다. 기존 고객 `unitPrice`/`lineAmount` 스냅샷을 바꾸지 않으며 이 내부 공급가는 ADMIN 감사에만 사용하고 공급처 주문 DTO나 정산 UI에는 노출하지 않는다. 공급처 정산은 B-099 범위가 아니다.
- `TRACKED`는 `0 <= reservedQuantity <= onHandQuantity`를 유지하고 `availableQuantity=onHandQuantity-reservedQuantity`로 계산한다. available은 저장하지 않는다.
- 공급처 절대값 수정은 마지막 canonical `expectedInventoryVersion`을 요구한다. 공급처 수정과 checkout reserve/release/consume/reacquire가 모두 `inventoryVersion`을 증가시키며 stale 수정은 현재 canonical projection을 담은 `409 INVENTORY_CONFLICT`로 끝난다. 이 버전은 Product aggregate version/review와 분리되어 재고 수정만으로 재검토를 만들지 않는다.
- `UNTRACKED`는 on-hand를 저장하지 않고 예약 상태를 만들지 않는다. 구매 가능 여부는 supplierAvailability와 Coreable sale guards로 결정한다. 기존 `sourceStockQuantity`는 외부 참고값일 뿐 portal 재고로 사용하지 않는다.
- checkout은 영향받는 모든 Supplier, Product, 모든 ProductOption(UNTRACKED 포함)을 각 id 순서로 잠근다. Supplier 거래 상태, Product/Option/compliance와 availability를 다시 검사하고 portal-origin 항목에만 time-valid contract를 추가 검사한 뒤 TRACKED 수량에 한해 한 트랜잭션에서 24시간 예약을 만든다. Catalog/inventory writer도 Product -> Option 순서를 사용하며 Product를 잡은 뒤 Supplier를 역순으로 잡지 않는다. 하나라도 실패하면 전체 checkout을 롤백한다.
- 기한 내 입금확인은 HELD 예약을 원자적으로 소비해 on-hand와 reserved를 함께 줄인다. 미입금 취소 또는 만료는 HELD 예약만 한 번 해제한다.
- 입금확인은 현재 Supplier/product/option/compliance/supplierAvailability를 다시 검증하고 portal-origin Supplier의 overdue contract를 lock 아래 공통 terminal routine으로 EXPIRED 처리한 뒤 time-valid VERIFIED를 요구한다. PaymentGroup에 portal snapshot 항목이 있고 실제 입금은 확인됐지만 판매불가면 receipt를 버리는 validation error 대신 whole-group `SALE_UNAVAILABLE_AT_DEPOSIT` 예외·환불 경로를 사용한다.
- 만료 뒤 발견한 입금은 실제 입금시각이 원래 기한 안이고 동일한 판매가능 guard를 통과하며 모든 TRACKED 재고를 다시 확보할 수 있을 때만 승인한다. 성공하면 기존 `releasedAt`을 보존하고 `reacquiredAt`과 `consumedAt`을 기록해 RELEASED 이력을 지우지 않은 채 현재 reservation status를 CONSUMED로 만든다. 판매불가는 재확보를 rollback하고 `SALE_UNAVAILABLE_AT_DEPOSIT`, 재고 실패 또는 기한 후 입금은 `LATE_DEPOSIT_EXCEPTION`으로 보낸다.
- 재고 절대값 update는 idempotent하며 on-hand를 현재 reserved 아래로 낮출 수 없다. 예약이 있으면 `UNTRACKED`로 바꿀 수 없다.
- `TRACKED <-> UNTRACKED` 전환은 해당 option을 참조하는 open PAYMENT_PENDING OrderItem이 하나라도 있으면 거절한다. 만료 뒤 mode가 바뀐 주문의 late deposit은 immutable inventoryModeSnapshot과 current mode 불일치를 `SALE_UNAVAILABLE_AT_DEPOSIT`으로 처리해 새 ledger를 예약 없이 소비하지 않는다.

Implemented `SupplierInventoryChangeHistory` fields (V41):

- id
- productOptionId: nullable live FK with `ON DELETE SET NULL`
- subjectProductOptionId: immutable option id used for lookup and idempotency uniqueness
- supplierId
- actorUserId: nullable after supplier-relationship actor retention cleanup
- beforeSupplierAvailability / afterSupplierAvailability
- beforeInventoryMode / afterInventoryMode
- beforeOnHandQuantity / afterOnHandQuantity
- beforeReservedQuantity / afterReservedQuantity
- beforeInventoryVersion / afterInventoryVersion
- requestHash
- idempotencyKey
- createdAt

Supplier inventory PUT은 `(subjectProductOptionId,idempotencyKey)` unique 경계로 이 immutable row와 option update를 함께 commit한다. 현재 supplier principal을 확인한 뒤 live Option보다 history replay를 먼저 찾고 product/option path id와 body를 hash하므로, 허용된 draft Option 삭제 뒤 같은 retry도 최초 canonical projection을 반환하며 바뀐 path/payload는 거절한다. Live option FK가 지워져도 subject id와 inventory audit은 남고 다른 tenant는 `404`다. Checkout HELD/CONSUMED/RELEASED 변화는 OrderItem reservation evidence가 기준이며 manual change history에 중복 기록하지 않는다.

### Bank-Transfer Deposit Payment Exception (Implemented B-102)

Implemented status semantics:

- Portal late-deposit command의 최종 `Order.status`는 `REFUND_REQUESTED`다. 기존 `PAYMENT_EXCEPTION` Order enum은 legacy 읽기 호환을 위해 남기지만 이 command의 별도 커밋 상태로 사용하지 않고 exception status history만 기록한다.
- `PaymentGroup.status=PAYMENT_EXCEPTION`: 실입금은 존재하지만 주문 승인과 공급처 출고 요청은 생성되지 않은 결제 그룹이다.
- `Payment.status=PAYMENT_EXCEPTION`: 실제 수령한 `BANK_TRANSFER` Payment를 정상 승인 Payment와 구분하는 B-102 값이다.
- `Payment.exceptionReason=AMOUNT_MISMATCH`: positive actual receipt가 immutable PaymentGroup total과 다른 최우선 금액 불일치 예외다.
- `Refund(reason=PAYMENT_AMOUNT_MISMATCH, refundScope=PAYMENT_GROUP)`: `orderId=null`, `paymentId` 연결, `refundAmount=actualDepositAmount`인 결제그룹당 단일 Refund다.
- `Refund.reason=SALE_UNAVAILABLE_AT_DEPOSIT`: portal-origin 항목을 포함한 PaymentGroup에서 실제 입금 확인 시 판매/안전 guard가 더 이상 충족되지 않아 전액을 정상 승인하지 않은 예외다.
- `Refund.reason=LATE_DEPOSIT_EXCEPTION`: exact receipt의 늦은 timestamp·재고 재확보 실패뿐 아니라 qualifying 미입금 `CANCELLED` 뒤 발견되어 terminal refund로 끝나는 경우에도 사용하는 Order별 예외다.

Rules:

- 만료 뒤 발견한 입금의 실제 입금시각이 원래 deadline 이내이고 portal-origin contract lazy-expiry를 포함한 현재 판매/안전 guard와 모든 TRACKED 수량의 원자적 재확보가 모두 성공한 경우에만 정상 입금확정으로 진행한다.
- Normal 또는 late command에서 `actualDepositAmount != totalAmount`이면 portal/legacy 여부와 무관하게 다른 guard보다 먼저 `AMOUNT_MISMATCH`로 분기한다. PaymentGroup의 expected total은 바꾸지 않고 approved amount/time은 null로 두며, refundable amount를 실제 수령액으로 설정한다.
- 이 분기는 `PAYMENT_PENDING`, `EXPIRED`와 미입금 취소만 완료된 `CANCELLED` 결제그룹을 받는다. `CANCELLED` 그룹은 수령 Payment/Refund/Fulfillment가 없어야 하고 모든 포함 Order가 미입금 취소 상태여야 하며, 명령은 그 Order들을 원자적으로 `REFUND_REQUESTED`로 전환한다.
- 금액 불일치 transaction은 전체 입금 증적과 Payment/PaymentGroup `PAYMENT_EXCEPTION`, 모든 Order의 exception 이력과 최종 `REFUND_REQUESTED`, 결제그룹 Refund 한 건을 함께 commit한다. 남은 HELD는 한 번만 RELEASED하고 만료된 RELEASED는 유지하며 재고 재확보·소비를 하지 않는다.
- 금액 불일치 Refund는 `REQUESTED -> APPROVED -> COMPLETED`의 Coreable 수동 계좌환불 흐름을 사용하되 거절이나 정상 주문 재개로 전환하지 않는다. 완료 command도 별도 key/hash/immutable result를 사용하고 모든 포함 Order를 원자적으로 `REFUNDED`로 끝낸다.
- 금액이 정확한 qualifying 미입금 `CANCELLED` 그룹도 portal/legacy 공통으로 정상 주문을 재개하지 않는다. 같은 transaction에서 immutable total은 유지하고 approved amount/time은 null로 두며 미입금 취소가 0으로 만든 `refundableAmount`를 `totalAmount=actualAmount`로 복구한다. 입금시각·saleability와 무관하게 재고를 재확보·소비하지 않고 immutable Order 금액의 `LATE_DEPOSIT_EXCEPTION` Refund를 Order마다 하나씩 만들어 전체 수령액을 반환한 뒤 새 checkout만 허용한다.
- B-102 received-payment exception Refund인 `PAYMENT_AMOUNT_MISMATCH`, `LATE_DEPOSIT_EXCEPTION`, `SALE_UNAVAILABLE_AT_DEPOSIT`은 실제 받은 돈의 반환이므로 금액 변경·거절·정상 주문 재개를 허용하지 않는다. Order-scoped manual completion도 Refund id를 포함한 별도 key/hash/result를 상태 검사 전에 replay하고 한 Refund/Order 완료와 Payment/PaymentGroup 부분·전체 환불 집계를 원자적으로 commit한다.
- Implemented `PaymentGroup.applyRefund` accepts `PAYMENT_EXCEPTION` only through an approved B-102 received-payment exception Refund. It subtracts the immutable Refund amount from the restored positive refundable balance, rejects nonpositive/overflow amounts, and moves the group to `PARTIALLY_REFUNDED` or `REFUNDED`; unrelated payment exceptions remain non-refundable through this method.
- 재고 재확보가 실패하거나 실제 입금시각이 deadline 이후이면 PaymentGroup과 실제 수령한 Payment를 `PAYMENT_EXCEPTION`으로 저장하고 모든 배송 그룹 Order에 예외 상태 이력을 남긴 뒤 같은 transaction의 최종 상태를 `REFUND_REQUESTED`로 커밋한다.
- PaymentGroup, affected Suppliers, Products, 모든 affected Options, Orders/Fulfillments를 공통 순서로 잠근 한 트랜잭션에서 기존 입금 증적 필드, 고유 `providerPaymentKey`, idempotency key와 PaymentEvent를 사용해 실제 입금 증적을 exactly once 기록한다. 잠긴 거래·catalog·availability와 immutable snapshot을 라우팅 직전에 다시 확인한다.
- Implemented bank-transfer payment-command PaymentEvent는 normal confirmation, amount-mismatch, late-deposit의 성공/exception과 모든 B-102 received-payment exception의 `MANUAL_REFUND_COMPLETED`에 `commandType`, `requestHash`와 ADMIN-safe immutable `resultSnapshot`을 보존한다. 완료 request hash는 Refund id, admin actor, 정확한 이체액과 이체 증적을 server-keyed HMAC으로 묶고 계좌 데이터를 event에 복제하지 않는다. 결과는 account/transfer evidence를 제외한 target Refund/Order와 Payment/PaymentGroup 집계 또는 group-scope 모든 Order 상태를 보존한다. `(paymentGroupId,idempotencyKey)` command unique 경계는 현재 PaymentGroup 상태 guard보다 먼저 조회한다. 동일 hash는 최초 결과를 반환하고 다른 Refund/command/amount/depositor/time/reference/reason의 key 재사용은 `409`다.
- 예외 트랜잭션은 `SUPPLIER_ORDER_PENDING`, Fulfillment, `requestedAt`, 배송지 잠금 또는 공급처 알림을 만들지 않는다. 공급처 주문 목록과 상세에는 이 주문을 절대 노출하지 않는다.
- Reason은 금액 불일치를 가장 먼저 평가한다. 금액이 정확하면 qualifying 미입금 `CANCELLED`를 두 번째로 평가해 바로 `LATE_DEPOSIT_EXCEPTION`으로 끝낸다. 나머지 pending/expired portal 경로에서만 판매/계약/compliance/availability 또는 immutable/current mode 실패를 `SALE_UNAVAILABLE_AT_DEPOSIT`으로 정하고, 그 guard까지 모두 통과한 경우에만 기한 초과/재고 실패를 `LATE_DEPOSIT_EXCEPTION`으로 사용한다. 뒤의 두 reason은 배송 그룹별 unique Refund를 하나씩 만들지만 금액 불일치는 실제 수령액의 결제그룹 Refund 한 건만 만든다. 어느 terminal 예외에도 정상 주문 재개 전이는 없다.
- 일반 confirm-deposit도 exact receipt를 확인한 PaymentGroup에 portal snapshot 항목이 하나라도 있는데 현재 판매/안전 guard가 실패하면 whole-PaymentGroup exception outcome과 Order별 `SALE_UNAVAILABLE_AT_DEPOSIT` Refund를 exactly once 생성한다. portal 항목이 전혀 없는 legacy PaymentGroup의 기존 validation error 동작은 유지한다.
- 일반 confirm-deposit은 scheduler가 아직 상태를 EXPIRED로 바꾸지 않았어도 locked PaymentGroup의 원래 deadline과 실제 `depositedAt`을 비교한다. 기한 후 timestamp면 `LATE_DEPOSIT_EXCEPTION`으로 보내고 남은 HELD 예약을 exactly once release하며 정상 소비/fulfillment를 금지한다.
- 이 normal pre-expiry 예외 transaction은 모든 portal TRACKED HELD 예약을 exactly once RELEASED로 바꾸고 reserved를 감소시키며 `releasedAt`을 기록한다. Late path의 tentative 재확보가 실패하면 rollback되어 기존 RELEASED 상태를 유지한다.
- 입금 증적, exception reason과 환불 다음 작업은 ADMIN 응답에만 포함한다. 고객 checkout 결과와 주문 내역은 raw 상태 대신 `입금 확인 및 환불 처리 중`과 적용되는 환불 예정액만 보고, 입금자·거래 식별값·관리자 사유·계좌/이체 증적은 받지 않는다. 공급처는 예외·환불 존재 여부도 알 수 없다.

### Portal Fulfillment And Order Snapshot (B-100/B-103 Implemented; B-104 Planned)

Mixed `Order` fields/status:

- deliveryMemo: Implemented B-103 nullable shipping-address snapshot field; max 300, trimmed, blank becomes null
- B-104 status addition for portal orders: TRACKING_REGISTERED

V39 implements the additive `Fulfillment` fields below. B-103 implements portal fulfillment creation and initial PII cutoff behavior; B-104 owns Shipment use and cutoff shortening:

- channel: COREABLE_MANUAL / DOMEGGOOK_API / SUPPLIER_PORTAL
- requestedAt
- operationalOwner: COREABLE / SUPPLIER
- piiAccessCutoffAt: initialized for portal fulfillment and monotonically non-increasing
- handedOverAt: nullable
- handedOverReason: nullable PII-free operational text, max 200 characters when supplied by ADMIN
- handedOverByAdminId: nullable

Rules:

- 기존 주문의 address snapshot에 영향을 주지 않고 새 checkout request와 `ShippingAddressSnapshot`에 `deliveryMemo`를 추가한다.
- Implemented B-102는 관리자 입금확인 성공 시 예약 소비와 `SUPPLIER_ORDER_PENDING` 전환까지만 담당한다. 같은 입금 트랜잭션에서 Implemented B-103은 한 delivery-group Order의 모든 item이 `managementChannelSnapshot=SUPPLIER_PORTAL`이고 portal 권한이 활성인 경우 `Fulfillment(channel=SUPPLIER_PORTAL, requestedAt)`와 배송지 잠금을 만들며, 하나라도 COREABLE item이면 기존 source 조건에 따른 COREABLE_MANUAL/DOMEGGOOK 호환 경로를 유지하고 all-portal `KEEP` 비활성 접근이면 `COREABLE_MANUAL`로 라우팅한다. Portal item의 TRACKED 예약/입금 guard는 Coreable routing에서도 유지한다.
- 정상 portal 출고 요청은 `operationalOwner=SUPPLIER`다. portal 정지·연결 해제 시 열린 portal Fulfillment를 `COREABLE`로 바꾸고 인계시각·사유·관리자를 기록한다. Supplier list/mutation은 SUPPLIER owner만 허용하며 재활성화가 owner를 자동 복구하지 않는다. Detail의 cutoff/terminal MASKED 예외와 active Claim FULL 예외만 아래 privacy 규칙을 따른다.
- portal 주문은 공급처 수락 단계와 기존 관리자 발주 시작/완료 단계를 거치지 않는다. 기존 COREABLE_MANUAL/DOMEGGOOK_API 흐름은 유지한다.
- supplier DTO는 raw Order status를 반환하지 않는다. `SUPPLIER_ORDER_PENDING`은 `FULFILLMENT_REQUESTED`로 매핑하며 `TRACKING_REGISTERED`, `DELIVERED`, `SHORTAGE_REPORTED`, `CLOSED`만 공급처용 표시 상태로 사용한다.
- portal 주문은 입금확인과 동시에 주소가 잠기므로 고객 self-cancel을 허용하지 않는다. 기존 self-cancel 규칙은 `COREABLE_MANUAL`/`DOMEGGOOK_API`와 `addressLockedAt is null`인 주문에만 적용한다.
- 공급처 주문 배정에는 별도 assignment table을 만들지 않고 기존 `Order.supplierId`, fulfillment channel과 operational owner를 사용한다.
- B-100이 channel/owner/handover columns와 lifecycle takeover write를 먼저 추가하고, B-103이 portal Fulfillment 생성과 KEEP fallback을 활성화한다. 따라서 각 slice는 독립 migration/commit으로 완료할 수 있다.
- B-103 scheduler와 상세 read는 `now >= piiAccessCutoffAt`인데 owner가 SUPPLIER인 open portal work를 reason `PII_CUTOFF_REACHED`로 exactly once COREABLE에 인계한다. Planned B-104/B-105 supplier mutation도 Fulfillment lock 아래 같은 cutoff를 lazy-enforce해 기한이 지났으면 인계 후 거절해야 한다. ADMIN도 구현된 개별 portal-takeover 명령의 idempotency key와 `COREABLE_FULFILLMENT_TAKEOVER|SUPPLIER_SUPPORT_REQUIRED|OPERATIONAL_RISK` reason code로 더 일찍 인계할 수 있다. 어느 경로도 자동 반환하지 않는다.
- supplier paid-work list/detail와 shipment/shortage mutation은 time-valid VERIFIED contract, `channel=SUPPLIER_PORTAL`, `operationalOwner=SUPPLIER`, tenant 및 허용 Order 상태를 함께 검증한다. Detail은 original supplier의 ACTIVE portal/current manager도 요구한다. COREABLE-owner work는 cutoff/terminal 경계에서만 MASKED read를 허용하고 active allowed-status Claim grant와 time-valid contract가 함께 있을 때만 read-only FULL을 허용한다. Contract expiry/revoke는 lifecycle authorization 실패 `403`, admin takeover/shortage 등 다른 비공개 인계는 grant와 무관한 `404`로 닫고 별도 safe queue를 쓴다. 어느 read 예외도 mutation을 열지 않는다. `OUT_OF_STOCK`, `CANCELLED`, `REFUND_REQUESTED`, `REFUNDED` 또는 contract terminal transition으로 supplier work가 끝나면 open portal Fulfillment를 COREABLE로 인계한다.

Implemented B-100 `FulfillmentHandoverHistory` schema fields:

- id
- fulfillmentId
- actorType: ADMIN / SYSTEM
- actorAdminId: nullable
- reasonCode: ADMIN_TAKEOVER / PII_CUTOFF_REACHED / PORTAL_SUSPENDED / PORTAL_DISABLED / MANAGER_DISCONNECTED / CONTACT_EMAIL_CHANGED / CONTRACT_EXPIRED / CONTRACT_REVOKED / SUPPLIER_SHORTAGE_REPORTED / TERMINAL_STATE
- reason: nullable for system/lifecycle reasons, required PII-free ADMIN-only text of at most 200 characters for ADMIN_TAKEOVER
- requestHash: nullable for non-request system transitions
- idempotencyKey: nullable for non-request system transitions
- resultSnapshot: ADMIN-safe immutable command result, nullable for non-request system transitions
- createdAt

B-100 lifecycle transitions change owner and append this row under the locked Fulfillment. V39 provides the partial unique `(fulfillmentId,idempotencyKey)` boundary. B-103 implements the ADMIN portal-takeover command, cutoff scheduler/read-lazy takeover and terminal-state takeover on the same history boundary.

### Multiple Shipments And Allocation (Planned B-104)

Planned `Shipment` changes:

- one Order to many Shipments
- version: optimistic lock
- idempotencyKey: required for portal creation, unique with Order
- creationRequestHash
- creationResultSnapshot: immutable safe creation response
- carrierCode
- status additions: TRACKING_REGISTERED / VOIDED
- registeredAt
- registeredByUserId: nullable after the parent Order legal-retention boundary
- shippedAt becomes nullable for portal tracking registration

Planned `ShipmentItem` fields:

- id
- shipmentId
- orderItemId
- quantity
- createdAt

Rules:

- 공급처는 자기 portal 주문의 carrier code와 tracking number만 등록하며 실제 집하·배송완료 상태를 입력하지 않는다.
- 등록 시 서버가 지원 carrier registry에서 공식 조회 URL을 생성한다. 공급처가 임의 URL을 저장하지 않는다.
- registry는 carrierCode를 기존 non-null carrier 정식명에 매핑한다. 새 portal Shipment는 두 필드를 함께 쓰고, 결정적으로 매핑 가능한 legacy row만 carrierCode를 backfill한다.
- 첫 송장에서 allocation을 생략하면 미할당 전 수량을 기본 배정한다. 추가 송장은 양수의 명시적 allocation이 필요하다.
- B-104의 portal Shipment 생성·정정·void·배송완료/재개와 admin portal-shipment는 Order -> Fulfillment -> all Shipment rows -> OrderItems의 공통 write-lock 순서를 사용하고 조건을 재검사한 뒤 aggregate를 재계산한다. B-105가 report table을 추가한 뒤 이 service와 supplier shortage submit/admin review를 Order -> Fulfillment -> report -> all Shipment rows -> OrderItems로 확장한다. 모든 Claim/Refund writer는 parent Order를 해당 Claim/Refund row보다 먼저 잠근다. Payment-origin Refund는 더 넓은 `PaymentGroup -> Supplier -> Product -> Option -> Order -> Refund` 순서를 유지하며 Order부터 시작하지 않는다. Delivery correction은 같은 Order lock 아래 후속 Claim/Refund 부재를 다시 확인한다.
- 한 OrderItem의 모든 ShipmentItem 수량 합은 주문수량을 넘을 수 없다. 등록은 위 order/fulfillment/item lock으로 동시 over-allocation을 막는다.
- 첫 portal 송장 등록은 Shipment와 Order를 `TRACKING_REGISTERED`로 표시할 뿐 `SHIPPED` 증거로 사용하지 않는다.
- 공급처는 아직 배송완료되지 않은 자기 Shipment의 carrier/tracking만 idempotency key, version guard와 필수 사유로 정정할 수 있다. allocation 오류는 Coreable void 뒤 새 Shipment 등록으로 고치며 ShipmentItem은 수정·삭제하지 않는다.
- Coreable은 배송완료 전 중복·오등록 Shipment를 `VOIDED`로 바꾸고 그 allocation을 다시 사용할 수 있게 하거나, 공식 조회 결과를 확인한 `evidenceObservedAt`과 사유로 개별 Shipment를 배송완료 처리할 수 있다. 배송완료는 `registeredAt <= deliveredAt <= evidenceObservedAt <= now`만 허용한다.
- void 뒤 non-voided Shipment가 없으면 Order는 `SUPPLIER_ORDER_PENDING`, 하나 이상이면 `TRACKING_REGISTERED`로 재계산한다. 모든 유효 Shipment가 배송완료 조건을 충족한 경우만 `DELIVERED`다.
- 잘못된 portal 관리자 배송완료는 그 뒤 Claim/Refund가 생기기 전까지만 `REOPEN_TRACKING` 또는 deliveredAt 정정이 가능하다. 시각 정정은 별도 `evidenceObservedAt`과 `registeredAt <= correctedDeliveredAt <= evidenceObservedAt <= now`를 만족해야 한다. stale version이나 후속 의존 데이터가 있으면 `409`이며, 원래 배송완료 증적과 고객 알림 이력을 보존한다.
- 모든 수량이 void되지 않은 Shipment에 할당되고 그 Shipment가 모두 Coreable 배송완료 증적을 가진 경우에만 Order를 `DELIVERED`로 재계산한다.
- Coreable 인계된 `SUPPLIER_PORTAL + owner=COREABLE` 주문은 supplier creation과 같은 plural/allocation service를 쓰는 admin portal-shipment 명령으로 출고한다. B-105부터 REPORTED shortage가 열려 있으면 이 명령을 거절하고 REJECTED 뒤에만 수동 출고를 계속할 수 있다. `COREABLE_MANUAL` fallback은 기존 admin shipment 경로를 사용한다.
- 복수 Shipment 주문의 Claim 가능기간과 법정 배송완료 기준은 legacy singular projection이 아니라 void되지 않은 Shipment의 `max(deliveredAt)`을 사용한다. B-104는 `TRACKING_REGISTERED`를 모든 Claim/refund transition guard와 customer/admin status projection에 추가한다. 고객 direct cancel은 계속 막고, Coreable 승인 취소는 유효 tracking을 먼저 void/stop하거나 실제 출고면 return flow로 전환한 뒤에만 환불 상태로 이동한다.
- 기존 단일 Shipment, `READY` / `SHIPPED` / `DELIVERED`, Domeggook tracking sync와 endpoint별 응답 shape는 호환 기간 동안 보존하되 legacy supplier-work/단일 shipment/tracking-sync/manual-correction 명령은 `SUPPLIER_PORTAL` channel을 거절한다. repository의 singular Order lookup은 unique 제거 전에 plural aggregate로 교체한다. non-voided row가 있으면 legacy singular projection은 `(registeredAt,id)`가 가장 이른 row를 고르고 복수이면 truncation flag를 표시한다. row가 없으면 customer detail은 현재 non-null `{status: READY, carrier: null, trackingNumber: null}` placeholder를 유지하고 admin detail은 현재처럼 null을 유지한다; 새 `shipments[]`는 양쪽 모두 빈 배열이며 canonical이다.

Planned `ShipmentChangeHistory` fields:

- id
- shipmentId
- actorUserId: nullable after the parent Order legal-retention boundary
- actorType: ADMIN / SUPPLIER
- action: SUPPLIER_CORRECTED / ADMIN_CORRECTED / ADMIN_VOIDED / ADMIN_DELIVERY_COMPLETED / ADMIN_DELIVERY_REOPENED / ADMIN_DELIVERED_AT_CORRECTED
- beforeSnapshot
- afterSnapshot
- reason: required ADMIN-only PII-free operational text, max 200 characters
- evidenceObservedAt: nullable, required for ADMIN_DELIVERY_COMPLETED and ADMIN_DELIVERED_AT_CORRECTED
- requestHash
- idempotencyKey
- resultSnapshot: immutable actor-safe canonical response
- createdAt

Portal creation stores the original request hash and immutable safe result snapshot before later corrections can change carrier/tracking fields. After tenant/resource authentication, `(orderId,idempotencyKey)` is checked before mutable owner/state/cutoff guards. Its canonical hash includes `SUPPLIER_CREATE|ADMIN_CREATE`, actor type, and canonical body because supplier/admin creation share one key space; only the same actor/action/payload replays the stored result after takeover or later state change, while another actor/route or changed payload conflicts. Correction, void, delivery-complete and delivery-correction actions likewise check action-history key/hash before current state/version, and their shared `(shipmentId,idempotencyKey)` hash includes exact action, actor type, and canonical body. New actions append history and never delete Shipment or allocation evidence; a different actor/action can never replay another result, and stale `version` is rejected only after replay lookup.

### Supplier PII Access (B-103 Implemented; B-104 Cutoff Shortening Planned)

Normal access cutoff:

```text
portal fulfillment creation:
  piiAccessCutoffAt = requestedAt + 60 days

each tracking registration:
  piiAccessCutoffAt = min(current piiAccessCutoffAt, registeredAt + 30 days)

void/replacement:
  never increases piiAccessCutoffAt
```

Rules:

- FULL 접근은 입금확인으로 portal fulfillment가 생성된 시각부터 stored monotonic cutoff 미만까지 허용한다. cutoff 시각부터 MASKED이며 Shipment void만으로 이전 FULL window가 부활하지 않는다.
- 주문이 `OUT_OF_STOCK`, `CANCELLED`, `REFUND_REQUESTED` 또는 `REFUNDED`가 되면 non-voided Shipment 유무와 관계없이 60일 fallback을 기다리지 않고 즉시 `TERMINAL_MASKED`다.
- Coreable이 append-only grant history에 기록한 최신 유효 `accessUntil`이 현재보다 뒤이고 Claim이 허용된 진행 상태이며 Supplier contract가 time-valid VERIFIED일 때만 normal cutoff 이후에도 해당 deadline 미만까지 FULL 접근을 한시 재개한다.
- MASKED 응답은 한 글자 이름을 `*`, 두 글자 이상 이름을 첫 Unicode code point와 고정 `**`로 반환한다.
- MASKED 전화번호는 숫자 정규화 후 마지막 네 자리만 남기고 앞 숫자를 `*`로 바꾼다. 네 자리 이하면 전부 `*`다.
- MASKED 응답의 postalCode, address1, address2, deliveryMemo는 null이다.
- Supplier order detail은 `piiAccessLevel`, 적용 근거와 cutoff를 포함하며 cache/store 대상이 아니다.

Implemented B-103 `SupplierPiiAccessGrant` fields (V42):

- id
- claimId
- supplierId
- sequence: monotonic per Claim
- action: GRANTED / EXTENDED / REVOKED
- accessUntil: required for GRANTED/EXTENDED, null for REVOKED
- previousGrantId: nullable self-reference
- actedByAdminId
- reason
- requestHash
- idempotencyKey
- resultSnapshot: immutable ADMIN-safe result
- createdAt

Grant rules:

- Claim에 현재값을 덮어쓰지 않는다. 생성·연장·철회는 새 immutable history row를 append하며 기존 row를 update/delete하지 않는다. ADMIN과 Order/Claim scope 뒤 Command는 `(claimId,idempotencyKey)`/hash replay를 먼저 확인하고, 새 command만 Order -> Claim -> latest grant의 공통 순서로 잠근 뒤 `expectedLatestGrantId`와 allowed Claim status를 검증해 per-Claim sequence를 하나 증가시킨다.
- 생성/연장의 `accessUntil`은 각각 요청시각보다 미래이면서 `now+30일` 이하여야 한다. 연장은 이전 deadline에 일수를 누적하지 않고 새로 bounded deadline을 append한다.
- 가장 높은 sequence의 action이 `GRANTED` 또는 `EXTENDED`이고 `accessUntil`이 현재보다 뒤이며 Claim status가 `APPROVED`, `RETURN_WAITING`, `RETURN_RECEIVED`, `REFUND_PROCESSING`, `EXCHANGE_SHIPPING` 중 하나이고 Supplier contract가 time-valid VERIFIED일 때만 active다. EXTENDED는 latest가 active GRANTED/EXTENDED일 때만 허용하고 REVOKED 뒤에는 명시적 새 GRANTED만 다시 열 수 있다. 다른/terminal Claim 상태나 contract expiry/revoke는 별도 revoke row 없이 즉시 접근을 닫는다.
- 동일 key/hash replay는 stored result를 반환하고 다른 payload 또는 stale expected latest id는 conflict다. Sequence를 사용하므로 같은 timestamp의 revoke/extend 순서가 모호하지 않다.
- grant의 supplier는 Claim Order의 supplier에서 서버가 결정한다. ADMIN만 action을 만들며 reason과 idempotency key가 필수다. Grant/extension reason은 `RETURN_COORDINATION_REQUIRED|EXCHANGE_COORDINATION_REQUIRED|REFUND_COORDINATION_REQUIRED`, revoke reason은 `CLAIM_ACCESS_NO_LONGER_REQUIRED`만 허용해 Claim/customer 자유문을 복제하지 않는다.
- normal cutoff 뒤 FULL 응답은 실제 권한 근거인 최신 grant row를 서버에서 검증한다.

Implemented B-103 `SupplierPiiAccessLog` fields (V42):

- id
- actorUserId
- orderId
- accessReason: NORMAL_FULL / CLAIM_FULL / TERMINAL_MASKED / EXPIRED_MASKED
- accessedAt

Rules:

- supplier order detail을 반환할 때마다 actor, Order, 접근 근거와 시각만 append-only로 기록한다. supplier와 grant는 Order 및 현재 권한 검증에서 join하며 로그에 중복하지 않는다.
- access log와 grant audit에는 수령인, 전화, 주소, 배송 memo의 실제 값을 복제하지 않는다.
- access log는 관리자만 조회하고 1년 뒤 삭제한다.
- grant history 생성·연장·철회는 ADMIN만 수행하고 supplier 입력으로 Claim/Order/Refund 상태나 PII 기한을 바꿀 수 없다.

### SupplierShortageReport (Planned B-105)

공급처가 VOIDED 포함 Shipment를 한 번도 등록하지 않은 자기 delivery-group 주문 전체의 품절을 한 번 신고한 감사 레코드다.

Planned fields:

- id
- orderId: unique
- supplierId
- actorUserId: nullable after the parent Order legal-retention boundary
- reasonCode: OUT_OF_STOCK / OPTION_UNAVAILABLE / QUANTITY_UNAVAILABLE
- status: REPORTED / APPROVED / REJECTED
- requestHash
- idempotencyKey
- submitResultSnapshot: immutable supplier-safe canonical submit response
- reviewedByAdminId: nullable
- reviewedAt: nullable
- reviewReasonCode: nullable allowlisted supplier-safe code
- reviewRequestHash: nullable
- reviewIdempotencyKey: nullable
- reviewResultSnapshot: nullable ADMIN-safe immutable result
- createdAt

Rules:

- 새 신고는 tenant, `SUPPLIER_PORTAL/operationalOwner=SUPPLIER`, paid/action state와 Shipment 전무 조건을 검증하고 한 Order당 한 row만 만든다. endpoint는 owner/state guard보다 먼저 `(supplierId,idempotencyKey)`와 request hash를 조회해 동일 retry가 인계 뒤에도 최초 safe 결과를 반환하도록 하며, 다른 payload key reuse는 conflict다. Report row가 최초 key 하나만 durable하게 bind하므로 같은 order의 새 key는 reason code와 무관하게 `SHORTAGE_ALREADY_REPORTED` conflict다.
- 자유 memo, 상품 일부 수량, 고객 이름·전화·주소·claim/payment/refund 정보는 받거나 저장하지 않는다.
- 생성은 `status=REPORTED`를 저장하고 Fulfillment만 reason `SUPPLIER_SHORTAGE_REPORTED`로 COREABLE에 인계한다. Order, Claim, Payment, Refund는 바꾸지 않는다.
- Submit과 ADMIN review는 Shipment/admin portal-shipment와 같은 Order -> Fulfillment -> report/Shipment -> OrderItems lock 순서를 사용한다. Submit과 새 승인 명령은 Shipment가 한 번도 없음을 다시 확인하며 open REPORTED report는 admin portal-shipment를 막는다. ADMIN review는 authorization/report scope 뒤 review key/hash/result를 expected status, REPORTED와 Shipment guard보다 먼저 조회해 동일 terminal replay를 반환하고 changed payload를 거절한다. 새 command만 REPORTED와 expected status, allowlisted review reason code를 검증하며 free text를 받지 않는다. `SHORTAGE_CONFIRMED`는 승인 전용이고 `INSUFFICIENT_EVIDENCE|FULFILLMENT_CAN_CONTINUE`는 거절 전용이다. 승인 시 기존 Coreable out-of-stock/refund service와 report APPROVED를 한 transaction에서 commit한다. 거절은 report를 REJECTED로 바꾸고 Refund를 만들지 않으며 Coreable owner를 유지한다. 어느 경로도 자동으로 supplier owner를 복구하지 않는다.
- 공급처 list/detail은 report id, order number, reason/status/time, allowlisted review reason과 `WAIT|NONE|CONTACT_COREABLE` next action만 반환한다.

### SupplierClaimTask (Planned B-105)

Coreable이 공급처에 요청한 제한된 운영 사실의 입력 권한이다. Claim 자체를 공급처에 공개하지 않는다.

Planned fields:

- id
- claimId
- supplierId
- requestedType: SHIPMENT_STOP_RESULT / RETURN_INSTRUCTIONS / RETURN_RECEIVED / INSPECTION_RESULT
- status: OPEN / ANSWERED / CLOSED
- instructionCode
- instructions: allowlisted non-PII template text
- requestedByAdminId
- creationRequestHash
- creationIdempotencyKey
- creationResultSnapshot: immutable ADMIN-safe canonical creation response
- requestedAt
- dueAt
- answeredAt
- closedByAdminId
- closedAt
- closeReasonCode
- closeRequestHash: nullable
- closeIdempotencyKey: nullable
- closeResultSnapshot: nullable ADMIN-safe immutable result

Rules:

- ADMIN만 Claim Order의 supplier를 대상으로 task를 생성·종료한다. task list에는 task id, order number, 자기 상품/옵션명·수량, requested type, safe instructions, due/status/timestamps만 포함한다. detail은 같은 safe correlation fields와 같은 task의 safe fact id/type/payload/correction reference/time을 추가해 정정 대상을 선택하게 하되 Claim/고객 본문, PII와 actor identity는 포함하지 않는다.
- `(claimId,creationIdempotencyKey)`는 unique다. ADMIN authorization과 Order/Claim scope 뒤 key/hash/result를 Claim status보다 먼저 조회해 동일 request hash retry는 최초 task를 반환하고 다른 payload 재사용은 거절하며, 의도한 추가 round는 새 key로 만든다.
- ADMIN list/detail projection은 Claim/order linkage, 요청·종료 관리자와 내부 task context, 같은 task의 전체 append-only fact history를 반환해 Coreable 판단에 사용한다. fact 자체는 상태 전이 권한이 아니므로 별도 기존 Claim action을 실행해야 한다.
- OPEN task는 현재 supplier manager의 첫 답변을 허용한다. 첫 유효 fact가 기록되면 answeredAt을 한 번 기록하고 ANSWERED로 전환한다. ANSWERED task는 기존 같은-task fact를 `correctsFactId`로 지정한 정정만 허용하며 ADMIN close 뒤에는 모든 입력을 거절한다.
- 새 task 생성과 fact 입력은 Order -> Claim -> Task -> Fact 순서로 잠그고 `REQUESTED`, `UNDER_REVIEW`, `EVIDENCE_REQUESTED`, `APPROVED`, `RETURN_WAITING`, `RETURN_RECEIVED`, `REFUND_PROCESSING`, `EXCHANGE_SHIPPING` 중 하나인지 확인한다. `REJECTED`, `COMPLETED`, `WITHDRAWN` 전이도 Order -> Claim prefix를 먼저 잠근 뒤 열린 task를 `CLAIM_TERMINAL`로 원자적으로 종료한다. `now >= dueAt`이면 새 입력을 거절하고 scheduler가 `DUE_AT_EXPIRED`로 idempotent 종료한다.
- ADMIN close는 `RESPONSE_ACCEPTED`, `SUPERSEDED`, `NO_LONGER_NEEDED`만 요청할 수 있다. `DUE_AT_EXPIRED`와 `CLAIM_TERMINAL`은 각각 실제 deadline/terminal guard가 성립할 때 서버만 기록한다. ADMIN/Order/Claim/task scope 뒤 `(taskId,closeIdempotencyKey)` hash/result 조회가 task/Claim/deadline guard보다 먼저 실행되어 같은 replay는 stored result를 반환하고 다른 payload는 conflict다.
- instructions는 서버의 allowlisted template에서 생성하고 고객 PII, 결제, 환불계좌, Claim 자유본문 또는 관리자 memo를 복사하지 않는다.

### SupplierClaimFact (Planned B-105)

공급처가 Coreable 요청에 답하는 append-only 사실 기록이다.

Planned fields:

- id
- taskId
- claimId
- supplierId
- actorUserId: nullable after the parent Claim legal-retention boundary
- type: SHIPMENT_STOP_RESULT / RETURN_INSTRUCTIONS / RETURN_RECEIVED / INSPECTION_RESULT
- payload: type별 schema로 검증된 structured data
- correctsFactId: nullable self-reference
- requestHash
- idempotencyKey
- resultSnapshot: immutable supplier-safe canonical fact response
- createdAt

Rules:

- Coreable이 만든 OPEN/ANSWERED task가 없으면 입력할 수 없다. task supplier, 현재 manager tenant와 Claim Order supplier가 모두 일치해야 한다.
- fact type은 task requestedType과 같아야 하며 payload는 type별 allowlist/enum/timestamp schema만 허용한다. 자유 text와 고객 PII는 거절한다.
- 수정은 기존 row를 바꾸지 않고 같은 task의 이전 fact를 `correctsFactId`로 참조하는 새 row로 append한다.
- `(taskId, idempotencyKey)`는 unique다. Current manager tenant와 Order/Claim/task scope를 인증한 뒤 key/hash/result를 task/Claim/deadline/correction guard보다 먼저 조회해 동일 retry는 최초 결과를 반환하고 다른 payload의 key 재사용은 거절한다.
- 공급처 사실 입력은 Claim, Order, Refund 상태를 직접 변경하지 않는다. 승인·거절·환불·CS는 Coreable만 수행한다.

### Supplier Tenant, Email, And Browser Security

Status: Dynamic `ROLE_SUPPLIER`, invite/session feature gating, allowed Origin/Referer checks, B-100 invite email/retention boundaries, B-101 catalog tenant queries, B-102 inventory tenant queries, and B-103 fulfillment/PII/operational-email behavior are Implemented. B-104 Shipment and B-105 shortage/claim-task behavior remain Planned.

- Supplier-side actor FK는 영구 식별자가 아니다. Invite 소비자와 catalog/inventory/lifecycle actor는 B-098 관계 종료 보관기한 뒤 null 처리하고, Shipment/shortage/claim actor는 parent Order/Claim 법정 보존기한까지 보존한 뒤 null 처리하거나 parent와 함께 파기한다. Actor type, supplier/business object, action, state/version과 timestamp 같은 비PII 증적은 해당 원장의 보존 규칙에 따라 남길 수 있다. `SupplierPiiAccessLog`는 이 일반 규칙 대신 1년 뒤 row 자체를 삭제한다.

- 모든 `/api/supplier/**` repository query는 현재 principal에서 결정한 supplier id를 resource id와 같은 DB predicate에 포함한다. 조회 후 Java에서만 비교하지 않는다.
- 다른 supplier의 resource는 존재 여부를 숨기기 위해 `404`를 반환한다. 요청 payload의 supplierId는 신뢰하지 않는다.
- Cookie를 사용하는 supplier/public-invite unsafe HTTP method는 allowlist와 정확히 일치하는 `Origin`을 요구한다. Origin이 없을 때만 same-origin `Referer`를 fallback으로 검사하며 둘 다 없거나 불일치하면 `403`이다.
- Access/invite-context cookie는 `HttpOnly`, `SameSite=Lax`를 사용하고 production HTTPS에서는 `Secure`를 강제한다.
- 초대는 token/link와 일반 연결 안내만 담는 유일한 pre-verification 연락처 검증 email이다. 그 밖의 supplier 운영 email은 현재 검증된 Supplier 연락 email에만 보내며 고객 이름·전화·주소·배송 memo·결제·환불 정보를 subject, body와 payload snapshot에 넣지 않는다. 최초 dispatch와 retry는 Supplier의 active portal/manager, time-valid VERIFIED contract, verified email과 stored recipient를 다시 읽고 하나라도 달라졌으면 `SKIPPED`로 끝내 old contact에 보내지 않는다. Supplier-linked notification writer는 raw `exception.getMessage()`를 저장하지 않고 allowlisted/redacted failure code만 기록한다. B-100은 기존 NOT NULL NotificationLog recipient를 nullable로 expand하고 compatible writer/reader를 먼저 배포한다. 운영 email retry는 생성 뒤 7일까지만 허용하고 SENT/SKIPPED 또는 retry 종료 FAILED recipient와 legacy/free-form failure reason은 30일 뒤 null 처리하며 allowlisted non-PII code만 보존할 수 있다. 초대 실패는 raw token을 저장하지 않으므로 generic retry하지 않고 새 key의 revoke/reissue만 허용한다. B-103은 출고 요청과 관리자 상품 승인·보완·거절 결과 producer를 연결했다. `SUPPLIER_CLAIM_WORK_REQUESTED` type/template은 B-103에 있지만 실제 producer는 Planned B-105의 Coreable claim-task 생성이 맡는다.
- Supplier PII detail response는 `Cache-Control: no-store`를 사용한다.
