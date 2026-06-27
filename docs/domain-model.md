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
DeliveryGroup
PaymentGroup
Payment
PaymentEvent
Fulfillment
Shipment
Refund
Claim
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

Suggested fields:

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

## SocialAccount

카카오, 구글, 네이버 소셜 로그인 식별 정보를 나타낸다.

Suggested fields:

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
- expiresAt
- deliveryGroupId
- paymentGroupId
- supplierOrderStartedAt
- addressLockedAt
- addressLockedByAdminId
- createdAt
- updatedAt

Suggested statuses:

- PAYMENT_PENDING
- EXPIRED
- PAYMENT_EXCEPTION
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

## DeliveryGroup

고객에게 노출하는 배송 묶음. MVP에서는 같은 공급처 상품을 하나의 배송 그룹으로 묶는다.

Suggested fields:

- id
- supplierId
- displayName
- shippingFee
- createdAt
- updatedAt

## PaymentGroup

고객의 한 번 결제를 나타내는 결제 그룹. 하나의 `PaymentGroup`은 여러 배송 그룹 주문을 포함할 수 있다.

Suggested fields:

- id
- checkoutNumber
- userId
- status: PAYMENT_PENDING / APPROVED / PARTIALLY_REFUNDED / REFUNDED / PAYMENT_EXCEPTION / EXPIRED
- totalAmount
- approvedAmount
- refundableAmount
- expiresAt
- approvedAt
- createdAt
- updatedAt

## Payment

PG 결제 기록.

Suggested fields:

- id
- paymentGroupId
- provider
- providerPaymentKey
- method: CARD / EASY_PAY / TRANSFER
- status: READY / APPROVED / FAILED / CANCEL_REQUIRED / CANCEL_REQUESTED / CANCELLED / CANCEL_FAILED / REFUND_REQUESTED / REFUNDED / REFUND_FAILED / REVIEW_REQUIRED
- requestedAmount
- approvedAmount
- approvedAt
- exceptionReason: AMOUNT_MISMATCH / APPROVED_AFTER_EXPIRED / SELLABILITY_CHECK_FAILED / DUPLICATE_OR_CONFLICTING_CONFIRMATION / PG_CONFIRMATION_ERROR
- idempotencyKey
- failureCode
- failureMessage
- rawProviderStatus
- lastSyncedAt
- createdAt
- updatedAt

## PaymentEvent

PG 승인, 취소, 환불, webhook, 서버 확인 요청 이력. 멱등 처리와 PG 대사를 위해 원본 이벤트 단위로 기록한다.

Suggested fields:

- id
- paymentId
- paymentGroupId
- orderId
- provider
- providerPaymentKey
- providerEventId
- eventType: CONFIRM_REQUESTED / CONFIRM_SUCCEEDED / CONFIRM_FAILED / CANCEL_REQUESTED / CANCEL_SUCCEEDED / CANCEL_FAILED / REFUND_REQUESTED / REFUND_SUCCEEDED / REFUND_FAILED / WEBHOOK_RECEIVED
- idempotencyKey
- rawStatus
- rawPayload
- result
- receivedAt
- processedAt
- createdAt

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
- supplierResponseDueAt
- delayNoticeRequiredAt
- delayNotifiedAt
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
- trackingStatus
- trackingLastSyncedAt
- trackingSyncFailureReason
- manualOverride
- manualCorrectionReason
- manualCorrectedByAdminId
- manualCorrectedAt
- shippedAt
- deliveredAt
- createdAt
- updatedAt

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
- providerCancelKey
- refundTransactionId
- idempotencyKey
- requestedByUserId
- approvedByAdminId
- failureCode
- failureMessage
- retryCount
- rawProviderStatus
- requestedAt
- pgCancelRequestedAt
- pgCancelApprovedAt
- customerNotifiedAt
- completedAt
- createdAt
- updatedAt

## Claim

취소, 반품, 교환 클레임 접수와 관리자 처리 상태.

Suggested fields:

- id
- orderId
- paymentGroupId
- userId
- claimType: CANCEL / RETURN / EXCHANGE
- reason: SIMPLE_CHANGE_OF_MIND / DEFECT / WRONG_DELIVERY / DIFFERENT_FROM_PRODUCT_INFO / DELIVERY_ISSUE
- status: REQUESTED / UNDER_REVIEW / EVIDENCE_REQUESTED / APPROVED / REJECTED / RETURN_WAITING / RETURN_RECEIVED / REFUND_PROCESSING / EXCHANGE_SHIPPING / COMPLETED / WITHDRAWN
- requestedAction: REFUND / EXCHANGE
- shippingCostBearer: CUSTOMER / SELLER / UNDECIDED
- returnShippingFeeAmount
- exchangeShippingFeeAmount
- evidenceUrls
- customerMemo
- adminMemo
- rejectionReason
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

## OrderStatusHistory

주문 상태 변경 이력. 주문 상태는 임의 되돌리기 없이 허용된 액션을 통해서만 변경한다.

Suggested fields:

- id
- orderId
- actorUserId
- actionType
- fromStatus
- toStatus
- reason
- createdAt

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
- policyDocumentId
- policyType
- policyVersion
- agreedAt
- createdAt

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
- 자동 배송조회 실패에 대비해 배송 상태 수동 보정과 상태 변경 이력이 필요하다.
- MVP 배송은 주문 1개당 배송 1개로 시작하고 부분 출고/분할 배송은 제외한다.
- 자동 배송조회는 관리자 수동 보정 상태를 임의로 덮어쓰거나 뒤로 되돌리지 않는다.
- MVP에서는 고객에게 별도 배송비를 청구하지 않으며 `shippingFee`는 `0`으로 시작한다.
- MVP에서 한 주문은 하나의 배송 그룹만 포함한다.
- 배송 그룹은 공급처 기준으로 나누지만 고객 화면에는 공급처 대신 배송 그룹으로 표시한다.
- 고객 화면에는 내부 주문 상태를 그대로 노출하지 않고 고객용 표시 상태로 매핑한다.
- 공급처 발주 상태는 주문 상태와 분리하되, 고객에게 보여줄 주문 상태와 동기화 규칙을 둔다.
- 주문 상태 변경 이력은 MVP부터 별도 테이블에 기록한다.
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
