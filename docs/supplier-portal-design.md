# Supplier Portal Design

Status: `B-100` Implemented, `B-101`~`B-105` Planned; design owned by `B-099`

이 문서는 외부 공급처가 Coreable 안에서 상품과 출고 업무를 직접 처리하는 포털의 구현 기준을 정리한다. 정책의 원문 기준은 `docs/policies/*`와 `docs/decision-log.md`이며, 이 문서는 도메인·API·마이그레이션·구현 순서를 한곳에서 연결하는 설계 인덱스다.

## Goal

- Coreable은 계속 고객에게 판매하고 결제·환불·CS 책임을 지는 단일 판매자다.
- 승인된 공급처는 자기 상품, 재고, 입금확인 완료 주문, 송장, Coreable이 요청한 클레임 사실만 처리한다.
- 공급처가 쉽게 사용할 수 있도록 주문 수락 단계와 정산 화면을 만들지 않는다.
- 기존 Domeggook 자동 발주와 Coreable 수동 발주 흐름은 유지한다.

## Non-Goals

- 판매자 마켓플레이스, 공급처 정산, 수수료 계산, 세금계산서 처리
- 공급처가 고객 결제, 환불, 클레임 승인, 주문 상태를 직접 결정하는 기능
- `B-100`~`B-105`에서 CSV 상품 일괄 등록
- 택배사 실시간 상태 API
- 한 공급처에 여러 담당자 또는 세분화된 공급처 권한
- 고객에게 공급처명, 공급가, 재고 모드나 `무제한` 문구를 노출하는 기능

## Product Boundary

```text
Customer
  -> Coreable catalog / checkout / order / claim

Coreable admin
  -> supplier application approval
  -> flagged product review
  -> deposit confirmation / refund / CS / claim decision
  -> shipment manual correction

Approved supplier manager
  -> own products / options / inventory
  -> paid fulfillment requests
  -> shortage report / tracking registration
  -> Coreable-requested claim facts
```

공급처 포털은 기존 Next.js web과 Spring Boot modular monolith 안에 둔다. 별도 서비스, 별도 결제 시스템, 별도 공급처 주문 원장을 만들지 않고 현재 `Supplier`, `Product`, `Order`, `Fulfillment`, `Shipment`, `Claim`을 확장한다.

## Application, Invitation, And Authentication

### Application

- 비로그인 사용자는 필수 공급처명·담당자명·연락 이메일과 선택 전화번호·문의 메모로 신청한다.
- 신청 전 active `SUPPLIER_APPLICATION_PRIVACY` 문서를 읽고 개인정보 수집·이용에 동의해야 한다. 서버는 exact active version을 검증하고 canonical 동의시각을 증적으로 저장한다.
- 연락 이메일을 정규화한 값 기준으로 non-expired `SUBMITTED` 또는 `APPROVED` 신청은 합쳐서 하나만 허용하고 같은 idempotency key 재요청은 기존 신청을 반환한다. 새 submit도 matching SUBMITTED를 잠가 90일 deadline이 지났으면 EXPIRED cleanup한 뒤 duplicate를 판단한다.
- 공개 신청 API에는 기본 rate limit을 적용한다.
- Coreable 관리자가 `SUBMITTED -> APPROVED|REJECTED`로만 승인 또는 거절하며, 처리 관리자·시각·사유를 보존한다. Review는 application lock 뒤 생성+90일 deadline을 다시 확인하고 이미 지났으면 scheduler와 같은 `EXPIRED` cleanup을 먼저 적용한 뒤 거절하므로 scheduler 지연으로 만료 신청을 승인할 수 없다.
- 승인·거절 요청은 `Idempotency-Key`와 canonical keyed-HMAC를 저장한다. application row를 잠근 뒤 최초 action/mode/선택 Supplier/reason/result를 함께 기록하며, 같은 key/hash의 retry만 최초 결과를 반환하고 key·payload가 다르거나 반대 action이면 `409`로 아무것도 바꾸지 않는다. 따라서 같은 신청으로 Supplier나 초대를 중복 생성하지 않는다.
- `SUBMITTED`는 생성+90일, `REJECTED`는 검토+90일에 연락 PII와 재식별 가능한 key/HMAC를 비식별화한다. `APPROVED` 연락 정보는 Supplier 운영 기록으로 이어지고, B-098/privacy notice가 정한 관계 종료 기한에 Supplier와 application 중복 PII를 함께 정리한다. 90일은 법정 보존기간 주장이 아닌 Coreable 운영 선택값이며 production 활성화 전 개인정보처리방침 검토에서 더 짧게 조정할 수 있다.

### Supplier portal status and invitation

- 기존 `Supplier.status=ACTIVE/INACTIVE`는 상품·거래 가능 여부로 유지하고 `portalStatus=DISABLED/PENDING_ACTIVATION/ACTIVE/SUSPENDED`를 별도로 둔다.
- 승인은 `CREATE_NEW|LINK_EXISTING`을 명시해 supplier를 중복 없이 생성·연결하고 `portalStatus=PENDING_ACTIVATION`인 1회용 이메일 초대를 만든다. 새 Supplier는 `Supplier.status=INACTIVE`, `portalContractStatus=UNVERIFIED`로 시작한다. 기존 Supplier 연결은 manager, invite, application link, portal lifecycle 이력이 한 번도 없는 legacy DISABLED 대상을 id로 명시하며 거래 상태는 유지하되 신청 연락 이메일을 Supplier 연락 이메일로 동기화하고 검증시각을 비운 뒤 같은 주소로 초대한다. 영구 종료된 portal supplier는 재연결하지 않고 CREATE_NEW도 현재 Supplier 연락처 충돌을 거절한다.
- 초대 원문은 DB에 저장하지 않고 digest만 저장한다.
- 초대는 supplier, 수신 이메일, unique token digest, issuance idempotency key/request hash, 만료·소비·폐기시각, 소비 사용자, 생성 관리자를 저장하고 supplier마다 유효한 미사용 초대 하나만 허용한다.
- 초대 `NotificationLog`의 subject/body/payload에는 token이나 raw link를 저장하지 않는다. fragment raw link는 after-commit 발송 메모리에서만 일시적으로 만들고 발송 뒤 폐기해 로그·재시도 저장소에서 복원할 수 없게 한다.
- 초대에는 token과 포털 연결 안내 외 운영 내용을 넣지 않는다. 아직 검증되지 않은 연락 이메일로 보내는 유일한 1회성 연락처 검증 메일이다.
- 초대에는 만료시각이 있으며 구현 기본값은 7일이다.
- 재발급은 idempotency key와 allowlisted `DELIVERY_FAILED|INVITE_EXPIRED|RECIPIENT_CHANGED|ADMIN_REISSUE` reason code가 필수이며 free text는 받지 않는다. 새 command는 `PENDING_ACTIVATION`, manager 없음, current contact email 존재, 미검증 상태에서만 허용하고 ACTIVE/SUSPENDED/DISABLED나 manager-bound 상태를 거절한다. 같은 key/payload는 기존 결과를 반환하고, 새 key의 명시적 재발급만 기존 미사용 초대를 폐기해 한 replacement를 만든다. raw link를 복원하는 일반 재시도는 하지 않으며 발송 실패·유실 복구도 반드시 새 key 재발급으로 처리한다.
- 초대 링크는 URL fragment로 토큰을 전달하고 web이 즉시 교환해 access log와 Referer 노출을 줄인다.
- token 교환은 초대를 소비하지 않고 5분짜리 HttpOnly/Secure/SameSite=Lax invite binding cookie와 OAuth state를 만든다. 해당 binding이 있는 Kakao authorize/callback만 공급처 연결을 완료할 수 있다.
- callback은 digest·만료·폐기·OAuth state·supplier manager 공석을 다시 확인하고, manager 연결·연락 이메일 검증·`portalStatus=ACTIVE`·초대 `CONSUMED`를 한 트랜잭션에서 처리한다.
- 이미 소비·만료·폐기된 초대, 다른 supplier에 연결된 사용자, manager가 이미 있는 supplier는 연결하지 않는다. 실패한 callback은 초대를 소비하거나 기존 계정 role을 바꾸지 않는다.
- 초대가 소비·폐기·만료된 시각+30일에는 recipient email, issuance key/HMAC와 연결 NotificationLog recipient를 null 처리한다. `consumedByUserId`는 계약관계 중 운영 audit으로만 유지하고 B-098 관계 종료 보관기한 뒤 null 처리하며, digest·terminal 시각·비PII action 결과는 남긴다.
- 활성화 화면은 supplier/account 존재 여부를 숨긴 allowlist 오류와 `초대 링크를 다시 확인` 또는 `Coreable에 새 초대를 요청` 행동만 보여준다. OAuth 일시 실패가 아닌 만료·폐기·사용완료 오류에는 무의미한 재시도를 제공하지 않는다.

### Kakao session

- 초대 수락 로그인은 Kakao만 허용한다.
- 이메일 링크를 열 수 있었음을 연락 이메일 검증 근거로 사용하며 Kakao 이메일 일치를 요구하지 않는다.
- 한 공급처에는 활성 담당자 한 명만 연결한다. 첫 버전은 tenant 선택 UI를 만들지 않기 위해 한 담당자 계정도 공급처 한 곳에만 연결한다. `suppliers.manager_user_id`의 nullable unique 제약으로 이를 보장한다.
- 기존 `CUSTOMER` 또는 `ADMIN` 계정의 저장 role을 교체하지 않는다. 활성 공급처 담당자 연결에서 `ROLE_SUPPLIER` 권한을 파생해 한 Kakao 계정이 기존 권한을 잃지 않게 한다.
- 담당자 연결이 해제되거나 portal이 정지되면 공급처 권한은 즉시 사라진다.
- `portalStatus=SUSPENDED`는 기존 manager 연결과 감사 이력을 보존하되 모든 supplier API 권한을 즉시 막는다. 재개는 Coreable 관리자만 할 수 있다.
- 파생 권한은 active user, `portalStatus=ACTIVE`, manager 연결을 요구하고 terminal 또는 overdue VERIFIED contract면 즉시 제거한다. 최초 UNVERIFIED onboarding은 비PII catalog 작업만 허용한다. `Supplier.status`는 신규 catalog 판매·checkout gate이며, 거래 상태가 `INACTIVE`여도 time-valid contract가 있는 담당자만 이미 입금확인된 주문을 계속 출고할 수 있다.
- 포털 정지는 `portalStatus=SUSPENDED`만 바꾸고, 담당자 연결 해제·교체는 manager를 비운 뒤 `portalStatus=PENDING_ACTIVATION`으로 바꾼다. 두 경우 기존 결제완료 portal 주문은 원래 channel과 증적을 보존하고 `Fulfillment.operationalOwner=COREABLE`, 인계시각·사유를 기록해 관리자 인계 큐에서 처리한다. 포털 재개 후에도 자동으로 공급처에 재배정하지 않는다.
- 정지·연결 해제 화면은 신규 판매 중지도 별도 선택하게 하고 안전한 UI 기본값은 `판매 중지`다. 요청에는 `salesAction=KEEP|PAUSE`를 명시하며 서버가 `Supplier.status`를 숨겨서 바꾸지 않는다. `KEEP`이면 portal 접근이 돌아올 때까지 신규 입금확인 주문을 `COREABLE_MANUAL`로 생성해 Coreable이 처리하고, `PAUSE`이면 신규 checkout을 막는다. 포털 재개도 판매 상태나 인계된 주문을 자동 복구하지 않는다.
- 연락 이메일 변경도 `salesAction=KEEP|PAUSE`를 필수로 받는다. 검증시각과 manager 연결을 지우고 미사용 초대를 폐기한 뒤 `PENDING_ACTIVATION` 상태에서 새 이메일 재초대를 요구하며, `KEEP` 중 신규 입금확인 주문은 같은 Coreable fallback을 사용한다.
- 영구 포털 종료는 `portalStatus=DISABLED`를 사용한다. 판매 종료는 별도 명시적 `Supplier.status=INACTIVE` 선택이며, 재개·교체·종료는 Coreable 관리자만 수행한다.
- 판매 재개/중지는 portal 재개와 별도인 idempotent `sales-status` 관리자 명령으로 ACTIVE/INACTIVE를 명시한다. portal/contact/manager/sales lifecycle 명령은 actor, 전후 portal·판매 상태, salesAction, PII-free reason, request HMAC와 결과/시각을 이력에 남긴다. Reason은 연락처·고객식별자 입력을 거절하고 관계 종료 cleanup에서 reason/key/hash/result를 null 처리하되 비PII action/state/time은 남긴다.
- `SUSPENDED -> ACTIVE` portal 재개는 retained active manager, verified contact email과 time-valid VERIFIED contract를 요구하며, contract 재검증만으로 portal/sales/handed-over owner를 복구하지 않는다.
- 영구 `portalStatus=DISABLED`, 거래 `INACTIVE`, open Fulfillment/Claim/Refund 없음이 모두 성립할 때만 관계 종료 연락 PII deadline을 설정한다. Scheduler는 그 시각에 Supplier를 잠그고 조건을 다시 확인하며, 새 open work가 있으면 deadline을 clear/defer하고 계속 적격일 때만 Supplier와 approved application의 중복 연락 PII/replay material을 함께 정리한다.
- B-100은 Supplier의 denormalized contract status/version/effective/expiry columns, `UNVERIFIED` default와 fail-closed sales guard를 소유한다. B-098은 idempotent contract-status 명령, supplier-unique verified version, version당 terminal event 하나, 비밀이 아닌 evidence reference, history, expiry index/scheduler를 소유한다. 모든 명령은 expected current version을 비교하고, scheduler도 Supplier lock 뒤 status/version/expiry를 재검사해 오래된 expiry가 재검증 version을 덮지 못하게 한다. `VERIFIED`는 `effectiveAt <= now`이고 expiry가 없거나 `now < expiresAt`일 때만 current다. EXPIRED/REVOKED와 sales/checkout/deposit의 lazy expiry는 sales INACTIVE, ACTIVE portal SUSPENDED, open invite 폐기와 모든 열린 supplier-owned portal Fulfillment의 Coreable 인계를 한 routine으로 처리한다. Re-verification은 portal/sales/ownership을 자동 복구하지 않는다.
- portal/contact/manager/sales lifecycle 명령과 정상·늦은 입금 처리는 같은 Supplier에 대해 직렬화한다. 공유 전역 잠금 순서는 `PaymentGroup -> 영향받는 Supplier(id) -> Product(id) -> 모든 ProductOption(id, UNTRACKED 포함) -> Order/Fulfillment(id)`이다. 입금 처리는 portal-origin item의 contract를 Supplier lock 아래 lazy expiry하고 거래·portal·manager·상품·옵션·compliance·availability를 다시 검사한다. lifecycle-only 명령은 Supplier 뒤 Fulfillment를 잠그며 Product/Option을 잡지 않고, catalog/inventory writer는 필요한 Supplier 뒤 Product -> Option을 따르며 Product 뒤 Supplier를 역순으로 잡지 않는다.
- 모든 `/api/supplier/**` 쿼리는 현재 사용자에서 활성 공급처를 먼저 결정하고 `resource id + supplier id`로 조회한다. 다른 공급처 리소스는 존재 여부를 노출하지 않도록 `404`로 처리한다.

## Product Registration And Review

- 첫 버전은 개별 상품 등록만 제공한다.
- 무옵션 상품도 내부적으로 `기본` 옵션 하나를 생성해 기존 주문 항목의 필수 option 참조를 유지한다.
- 공급처는 상품명, 요약, 공급가, 옵션 공급가, 공급처 옵션코드, MOQ/주문단위, 이미지, 상세와 상품정보제공고시를 입력한다. 재고 모드와 수량 입력은 B-102 endpoint가 같은 상품 편집 화면에 추가한다.
- 공급처 요청은 `supplierId`, 고객 판매가, 판매 상태, 검토 상태를 받지 않는다.
- 고객 판매가는 현재 Coreable active 가격 정책으로 서버가 결정적으로 계산한다. `basePrice=price(sourcePrice)`, `optionCustomerTotal=price(sourcePrice+sourceAdditionalPrice)`, `additionalPrice=optionCustomerTotal-basePrice`이며, `price`는 같은 markup, resale-minimum floor, rounding rule을 적용한다. 공급처는 고객 판매가를 직접 정하지 않는다.
- 승인된 공급가 변경은 모든 상품·옵션 고객가와 적용 pricing policy id/monotonic version, rates·rounding unit·resale minimum의 immutable calculator snapshot, before/after 가격 이력을 한 트랜잭션에 저장한다.
- 일반 상품은 구조 검증과 판매 준비 조건을 통과하면 자동 승인·공개한다.
- 인증, 카테고리, 법정 필수정보 또는 안전 규칙이 사람 판단을 요구하면 숨김 상태로 Coreable 검토 큐에 보낸다.
- 자동 공개 분류는 허용 규칙이 명확히 통과된 경우에만 성공한다. 분류 결과가 없거나 필수 근거가 누락되거나 규칙 실행이 실패하면 `REVIEW_REQUIRED`로 닫힌다.
- 검토 결과는 `DRAFT`, `AUTO_APPROVED`, `REVIEW_REQUIRED`, `SUPPLEMENT_REQUESTED`, `APPROVED`, `REJECTED`로 분리한다. `DRAFT`는 이미지 등 여러 요청이 필요한 편집 중 서버 상태일 뿐 공급처가 별도 승인 요청 단계를 거치게 하지 않는다.
- 최초 submit 시각은 한 번만 기록한다. 승인·검토중 상품 수정으로 다시 `DRAFT`가 되어도 이 시각은 지우지 않아 새 초안과 구분한다.
- 공급처 화면에는 `상품 등록` 동작 하나만 둔다. 이 동작이 최종 구조 검증과 분류를 실행하며, 조건을 통과한 일반 상품은 반드시 `AUTO_APPROVED`로 공개하고 나머지는 `REVIEW_REQUIRED`로 접수한다.
- 공급처 상품 조회·등록 응답은 내부 검토 메모나 규칙 trace 대신 allowlist인 `supplierDisplayStatus`, `reviewReasonCode`, `reviewMessage`, `nextAction`만 반환한다. reason code는 `CERTIFICATION_REVIEW`, `CATEGORY_REVIEW`, `REQUIRED_INFO_MISSING`, `SAFETY_REVIEW`, `SUPPLEMENT_REQUIRED`, `REJECTED_POLICY`, next action은 `WAIT`, `EDIT_AND_RESUBMIT`, `CONTACT_COREABLE`, `NONE`만 허용한다.
- `reviewMessage`는 공급처 전달용으로 별도 입력·검증한 500자 이하 single-line PII-free 문구다. Email, phone, address, customer identifier와 link를 거절한다. 보완 요청은 같은 상품 편집 화면과 `상품 등록` 동작으로 다시 제출하며 관리자 내부 메모·담당자·분류 trace는 공급처에 노출하지 않는다.
- 기존 `ProductComplianceStatus`의 의미는 바꾸지 않는다. 공급처 포털 검토 상태는 별도 필드로 두어 기존 `PENDING` 상품의 판매 호환성을 보존한다.
- 기존 상품은 `managementChannel=COREABLE`, portal 생성 상품은 `SUPPLIER_PORTAL`로 고정하고 OrderItem에 snapshot해 금전·호환 분기를 mutable 상태로 추론하지 않는다.
- Coreable은 언제든 상품을 숨김·판매중지할 수 있고 공급처는 이를 덮어쓸 수 없다.
- Product aggregate에는 optimistic `version`을 둔다. supplier mutation과 admin review는 화면에서 읽은 expectedVersion을 요구하며 stale 요청은 `409`로 아무것도 쓰지 않는다.
- 상품 실제 삭제는 자기 `SUPPLIER_PORTAL` 상품이 최초 submit 전 `DRAFT`이고 상품·모든 옵션의 OrderItem/CartItem 참조가 없을 때만 허용한다. 옵션도 같은 상품 단계에서 자기 참조가 없고 최소 한 옵션을 남길 때만 실제 삭제한다. 제출·검토·공개·사용 뒤에는 soft/hard delete 없이 Coreable의 숨김·판매중지 상태로 보존한다.
- DELETE는 `If-Match` Product version을 요구하고 Product -> 모든 Option을 id 순서로 잠근 뒤 guard를 다시 확인한다. Cart 추가와 checkout도 같은 잠금 뒤 참조를 만들어, 참조가 먼저면 delete `409`, delete가 먼저면 구매 경로 `404`/판매불가로 결정되게 한다.
- 삭제 전에 `PRODUCT_DELETED`/`OPTION_DELETED` 이력을 immutable subject id와 allowlisted before snapshot으로 남긴다. Live FK는 `ON DELETE SET NULL`이며 서버 고정 reason code를 사용한다. Server-owned image key는 metadata와 durable cleanup job을 함께 commit한 뒤 idempotent하게 삭제·재시도하고, 외부/legacy URL은 건드리지 않는다.
- 최초 DRAFT submit은 AUTO_APPROVED 또는 REVIEW_REQUIRED다. REVIEW_REQUIRED admin은 APPROVED/SUPPLEMENT_REQUESTED/REJECTED만 선택한다. SUPPLEMENT_REQUESTED 편집은 숨김을 유지하고 재제출은 항상 REVIEW_REQUIRED로 돌아가며, REJECTED는 Coreable 문의만 제공한다. 승인·검토중 상품의 review-relevant 수정은 즉시 HIDDEN/DRAFT로 만들고 다시 분류한다.
- 보완/거절의 supplier-safe code/message는 내부 reason과 분리하되 message와 internal reason 모두 500자 이하 single-line PII-free validator를 통과한다. 상품·옵션·이미지·상세·고시·가격·재고·검토 변경은 actor user/type/supplier, before/after version, 사유와 시각을 남긴다. History snapshot은 allowlisted 상품 business field만 canonicalize하고 raw request, actor contact, customer/order data와 arbitrary admin note를 복제하지 않는다. 기존 admin/source writer도 같은 version을 증가시키되 기존 admin request는 호환 릴리스 동안 optional precondition으로 이관한다.

## Inventory And Reservation

### Option inventory

```text
InventoryMode = TRACKED | UNTRACKED

TRACKED:
  onHandQuantity >= 0
  reservedQuantity >= 0
  reservedQuantity <= onHandQuantity
  availableQuantity = onHandQuantity - reservedQuantity

UNTRACKED:
  onHandQuantity = null
  reservedQuantity = 0
```

- B-102 이전의 기존 COREABLE 상품 옵션은 migration에서 `UNTRACKED`로 backfill한다. B-101에서 B-102 사이에 생성된 `SUPPLIER_PORTAL` 옵션은 `TRACKED/onHandQuantity=0`으로 별도 backfill해 재고 입력 전까지 품절로 둔다.
- 새 공급처 포털 옵션은 `TRACKED`가 기본이다.
- `sourceStockQuantity`는 외부 원천 참고값으로 유지하며 주문 재고로 사용하지 않는다.
- 고객은 재고 모드와 `무제한` 문구를 보지 않는다. 구매 가능/품절만 본다.
- `TRACKED` 옵션의 available이 0이면 구매할 수 없지만 운영 상태를 자동으로 `SOLD_OUT`으로 바꾸지는 않는다. 재입고하면 자동으로 다시 구매 가능해진다.
- 재고 수정 API는 절대값과 idempotency key를 받는다. Immutable `subjectOptionId`와 nullable live Option FK를 사용하고, unique `(subjectOptionId, idempotencyKey)`와 product/option path까지 묶은 request hash로 동일 retry에는 허용된 draft Option 삭제 뒤에도 최초 canonical inventory projection을 반환하며 다른 path/payload의 key 재사용은 거절한다.
- 성공·충돌 응답은 서버가 소유하는 reserved/available을 포함한 같은 canonical projection을 반환한다. 성공 시 actor, before/after availability·mode·on-hand와 reserved snapshot, key/hash, 시각을 append-only history에 함께 저장하며 checkout reservation 이력은 OrderItem 증적을 canonical로 유지한다.
- 공급처는 `onHandQuantity`를 현재 예약량 아래로 낮출 수 없다.
- 해당 option을 참조하는 open `PAYMENT_PENDING` OrderItem이 있으면 `TRACKED <-> UNTRACKED` 양방향 전환을 모두 막는다. 참조가 끝난 뒤 `UNTRACKED -> TRACKED`로 바꿀 때는 on-hand가 필요하다.
- 결제 후 취소·반품 시 재고를 자동 복구하지 않는다. 실물 반입을 확인한 공급처가 재고를 명시적으로 갱신한다.

### Reservation lifecycle

```text
OrderItemReservationStatus = NOT_APPLICABLE | HELD | CONSUMED | RELEASED

checkout creation:
  TRACKED -> reserved += quantity, HELD
  UNTRACKED -> NOT_APPLICABLE

deposit confirmed before expiry:
  onHand -= quantity, reserved -= quantity, HELD -> CONSUMED

unpaid cancel or 24-hour expiry:
  reserved -= quantity, HELD -> RELEASED
```

- 공급처 화면의 기본 선택은 `수량 관리 (권장)`=`TRACKED`다. `onHandQuantity`는 0 이상의 필수 정수이며, 주문서 생성 동안 수량이 예약되어 판매 가능 수량이 줄어든다는 도움말을 표시한다.
- 대안은 `재고 수량 관리 안 함`=`UNTRACKED`다. 이 모드에서는 수량을 입력받지 않고 공급처의 별도 `주문 받기`/`주문 중지` 값으로 신규 checkout을 통제한다는 도움말을 표시한다.
- supplierAvailability는 `AVAILABLE|UNAVAILABLE`이며 Coreable 소유 판매/숨김/안전 상태와 분리한다. 공급처는 주문을 중지할 수 있지만 AVAILABLE로 Coreable 중지를 덮어쓸 수 없다.
- 두 모드 모두 고객 화면에는 `무제한`이나 내부 수량을 표시하지 않고 구매 가능 또는 품절만 보여준다.

- checkout은 영향받는 Supplier, Product, 모든 ProductOption을 각 id 순서로 잠그며 UNTRACKED option도 생략하지 않는다. 잠금 아래 saleability를 재검증하고 TRACKED 수량만 모든 배송 그룹에 한 트랜잭션으로 예약한다. 하나라도 부족하거나 concurrent 판매중지/상품변경이 있으면 주문 전체를 롤백한다.
- 만료 scheduler와 입금확인은 PaymentGroup을 먼저 잠그고 상태를 다시 검사한다.
- 중복 만료와 중복 입금확인은 reservation 상태 guard로 idempotent하게 처리한다.
- 식별된 양수 실입금액이 immutable PaymentGroup total과 다르면 portal/legacy 여부, 판매가능성, 기한, 재고보다 먼저 `PAYMENT_AMOUNT_MISMATCH`로 분기한다. 실제 입금자·금액·시각·거래 식별값·관리자 사유와 Payment/PaymentGroup `PAYMENT_EXCEPTION`을 exactly once 저장하고, 남은 HELD를 재확보·소비 없이 한 번만 RELEASED한다.
- 이 분기는 `PAYMENT_PENDING`, `EXPIRED`와 미입금 취소만 완료된 `CANCELLED` 결제그룹을 받는다. `CANCELLED` 그룹은 수령 Payment/Refund/Fulfillment가 없고 모든 포함 Order가 미입금 취소 결과인 경우에만 허용해, 취소 뒤 발견된 실입금도 주문 재개 없이 반환한다.
- 금액 불일치는 배송 그룹별로 금액을 나누지 않는다. 모든 포함 Order를 `REFUND_REQUESTED`로 보내고 `Refund(status=REQUESTED, refundScope=PAYMENT_GROUP, orderId=null, reason=PAYMENT_AMOUNT_MISMATCH, refundAmount=actualDepositAmount)`를 결제그룹당 한 건만 만든다. Fulfillment, 주소 잠금, 공급처 PII·알림·조회 결과와 정상 주문 재개 액션은 만들지 않는다.
- Coreable이 이 그룹 Refund를 승인하고 실제 계좌이체 증적을 별도 idempotent 완료 명령으로 기록하면 Refund, Payment, PaymentGroup과 모든 포함 Order를 `REFUNDED`로 끝낸다. 실입금액 전체를 반환하므로 `PARTIALLY_REFUNDED`를 사용하지 않으며 고객이 계속 구매하려면 새 checkout을 만든다.
- 정확한 금액이어도 qualifying 미입금 `CANCELLED` 뒤 발견된 실입금은 portal/legacy 공통 terminal 예외다. 입금시각·현재 재고와 무관하게 주문을 되살리지 않고 immutable 배송 그룹 금액의 `LATE_DEPOSIT_EXCEPTION` Refund를 Order마다 하나씩 만들어 전액 반환하며, 공급처 노출 없이 고객이 새 checkout을 만들게 한다.
- 재고 예약은 판매 보장이 아니다. 입금확인은 Supplier 거래 상태와 상품·옵션·compliance·supplier availability를 다시 확인한다. portal snapshot 항목이 포함된 PaymentGroup의 실제 입금이 확인됐지만 판매불가면 단순 validation error로 receipt를 버리지 않고 group 전체를 `SALE_UNAVAILABLE_AT_DEPOSIT` 예외·환불 처리하며 공급처에는 노출하지 않는다. legacy-only group은 기존 validation 동작을 유지한다.
- 입금 기한이 지난 뒤 관리자가 입금을 발견하면 실제 입금시각을 기준으로 처리한다.
  - 실제 입금시각이 기한 이내면 동일 판매가능 guard와 모든 TRACKED 옵션 재확보를 원자적으로 수행한다. 성공하면 입금 승인, 판매불가면 재확보를 rollback하고 `SALE_UNAVAILABLE_AT_DEPOSIT`, 재고 실패면 `LATE_DEPOSIT_EXCEPTION` 증적과 환불 대상으로 보낸다.
  - 실제 입금시각이 기한 이후면 재고를 잡지 않고 같은 예외 증적과 환불 대상으로 보낸다.
- 예외에서도 실제 수령한 계좌입금 Payment와 관리자 증적은 exactly once 저장하되 주문을 승인하거나 공급처에 노출하지 않는다. Order는 같은 transaction의 자동 Refund 생성과 함께 최종 `REFUND_REQUESTED`로 커밋하며 정상 주문으로 재개하는 액션은 없다.
- 같은 예외 명령은 배송 그룹 Order마다 unique `order_id`를 idempotency 경계로 원인에 맞는 `Refund(status=REQUESTED, reason=LATE_DEPOSIT_EXCEPTION|SALE_UNAVAILABLE_AT_DEPOSIT)`를 정확히 하나씩 자동 생성하고 Order를 `REFUND_REQUESTED`로 보낸다. Coreable은 기존 수동 계좌환불 증적 흐름으로 실제 환불만 완료한다.
- 고객 checkout 결과와 주문 내역에는 raw 예외나 입금 증적 대신 `입금 확인 및 환불 처리 중`을 표시한다. 실제 계좌환불 증적 완료 후 Order는 `REFUNDED`가 된다. PaymentGroup은 배송 그룹 환불액을 차감해 `PARTIALLY_REFUNDED` 또는 `REFUNDED`로 끝나며, 공급처에는 예외와 환불 존재를 모두 노출하지 않는다.

## Fulfillment Request

- 입금확인이 성공하면 immutable OrderItem management-channel snapshot으로 배송 그룹별 routing을 결정한다.
- 모든 항목이 portal-origin이고 해당 Supplier의 거래 상태, time-valid VERIFIED contract, `portalStatus=ACTIVE`, manager 연결이 모두 유효할 때만 `fulfillmentChannel=SUPPLIER_PORTAL`, `operationalOwner=SUPPLIER`로 만들고 `requestedAt`·PII cutoff·주소 잠금을 저장해 공급처 queue/email에 즉시 노출한다.
- 모든 항목이 portal-origin이지만 `salesAction=KEEP`으로 판매만 유지된 채 portal/manager가 비활성인 동안에는 `COREABLE_MANUAL`, `operationalOwner=COREABLE`로 만들고 공급처 queue와 email에는 노출하지 않는다.
- mixed 또는 legacy 항목이 있는 배송 그룹은 기존 snapshot 기반 `COREABLE_MANUAL`/`DOMEGGOOK_API` routing을 유지하며 새 supplier portal queue로 보내지 않는다.
- 공급처 수락/거절 단계는 만들지 않는다.
- 입금확인 트랜잭션에서 재고 소비, 주문 전환, Fulfillment 요청 생성, 배송지 잠금을 함께 처리한다.
- 공급처에 노출된 뒤에는 고객 셀프서비스 취소와 주소 변경을 허용하지 않는다. 취소는 Coreable 클레임 검토로 진행한다.
- 기존 `COREABLE_MANUAL`과 `DOMEGGOOK_API` 흐름, 관리자 발주 시작/완료 API는 호환을 위해 유지한다.
- supplier paid-work list/detail와 shipment/shortage mutation은 time-valid VERIFIED contract, `SUPPLIER_PORTAL + operationalOwner=SUPPLIER` 및 허용 상태를 요구한다. Detail은 original supplier의 ACTIVE portal/current manager도 요구한다. cutoff/terminal 경계로 COREABLE에 인계된 work는 원래 supplier에게 MASKED detail만 남기고, active allowed-status Claim grant와 time-valid contract가 함께 있을 때만 read-only FULL을 한시 재개한다. Contract expiry/revoke는 lifecycle authorization 실패 `403`, admin takeover/shortage 등 다른 비공개 인계는 grant와 무관한 `404`로 닫고 별도 safe queue를 사용한다. 어느 예외도 출고 mutation을 허용하지 않는다.
- 관리자 `portal-takeover`는 idempotency key/request hash로 동일 replay에 최초 결과를 반환하고 다른 payload 재사용은 거절한다. 200자 이하이며 연락처·주소·고객식별자 입력을 거절하는 PII-free reason, actor, owner before/after, 인계시각을 append-only command history에 남기며 재개 뒤에도 자동으로 SUPPLIER owner로 되돌리지 않는다.

## Supplier Order Data And PII

- 주문 목록에는 주문번호, 처리 상태, 상품 요약, 수량, 요청시각만 보여주고 고객 PII는 넣지 않는다.
- 주문 상세에는 자기 공급처 배송에 필요한 최소 정보만 보여준다.

| 허용 | 금지 |
| --- | --- |
| 주문번호 | 고객 이메일, 회원 id, 표시 이름 |
| 수령인 이름·전화 | 결제·입금·은행·금액 정보 |
| 우편번호·주소1·주소2 | 환불계좌, 환불 실행 정보 |
| 배송 메모 | 다른 공급처와 그 상품 |
| 자기 상품명·옵션명·수량 | 관리자 메모와 내부 감사정보 |

- 전체 PII는 time-valid VERIFIED contract가 있는 담당자의 입금확인된 자기 포털 주문에만 제공한다. Fulfillment의 stored monotonic `piiAccessCutoffAt`은 생성 때 `requestedAt+60일`로 저장하고 송장을 등록할 때마다 같은 트랜잭션에서 `min(현재 저장값, registeredAt+30일)`로만 짧아진다. Shipment void/replacement는 cutoff를 다시 계산하거나 늘리지 않아 이미 마스킹된 PII가 grant 없이 부활하지 않게 한다.
- 주문이 `OUT_OF_STOCK`, `CANCELLED`, `REFUND_REQUESTED` 또는 `REFUNDED`가 되면 non-voided 송장 유무와 관계없이 기본 종료시각을 기다리지 않고 즉시 마스킹하고 COREABLE owner로 인계한다. Original supplier의 active manager에게는 `TERMINAL_MASKED`만 허용하며 유효 Claim grant와 time-valid contract가 함께 있을 때만 FULL을 한시 다시 연다.
- 기본 종료시각부터(`now >= cutoff`) 한 글자 이름은 `*`, 두 글자 이상 이름은 첫 Unicode code point와 고정 `**`로 반환한다. 전화번호는 숫자로 정규화한 뒤 4자리 이하면 전부 `*`, 5자리 이상이면 앞자리를 모두 `*`로 바꾸고 마지막 4자리만 남긴다. 우편번호·주소1·주소2·배송 메모는 `null`이며 응답에는 `piiAccessLevel=MASKED`와 종료시각을 포함한다.
- Coreable이 승인한 진행 중 클레임은 각 grant/extension 요청시점부터 최대 30일인 명시적 `supplierPiiAccessUntil`까지만 전체 PII 접근을 다시 허용한다.
- 클레임 상태가 `APPROVED`, `RETURN_WAITING`, `RETURN_RECEIVED`, `REFUND_PROCESSING`, `EXCHANGE_SHIPPING`이고 Supplier contract가 time-valid VERIFIED일 때만 최신 non-revoked grant가 유효하다. 그 밖의 Claim 상태나 contract expiry/revoke는 revoke row가 없어도 즉시 접근을 닫는다. grant/extension/revoke는 승인 관리자, PII 입력을 거절하는 200자 이하 운영 사유, 시각을 append-only로 남긴다.
- monotonic cutoff에 도달한 open supplier work는 B-103 scheduler가 `PII_CUTOFF_REACHED` 증적으로 COREABLE에 exactly once 인계한다. 이때 active manager의 조회는 `EXPIRED_MASKED`로 남고 terminal 인계는 `TERMINAL_MASKED`다. 관리자는 개별 주문을 더 일찍 idempotent takeover할 수 있으며, Coreable은 B-104 admin portal-shipment로 이어 처리한다. Claim grant는 time-valid contract가 있을 때만 인계 뒤 read-only FULL을 열고 출고 권한을 되돌리지 않는다.
- Contract EXPIRED/REVOKED는 `CONTRACT_EXPIRED|CONTRACT_REVOKED` 증적으로 모든 열린 supplier-owned portal work를 COREABLE에 인계하고 과거 Claim grant와 무관하게 공급처 detail을 `404`로 닫는다. 재검증·portal 재개도 인계된 owner를 자동 복구하지 않는다.
- 공급처 주문 상세 응답에는 `Cache-Control: no-store`를 적용한다.
- 공급처 주문 상세 조회마다 actor, 주문, 접근 근거와 시각만 기록한다. 로그에는 실제 PII 값이나 응답 본문을 복제하지 않는다.
- PII 접근 로그는 관리자만 조회하고 1년 뒤 삭제한다.

## Tracking And Multiple Shipments

- 기본 화면은 택배사와 송장번호만 받으며 `전체 수량 출고`가 선택된 단일 송장을 만든다. `분할 출고`를 선택한 경우에만 주문 항목별 수량 입력을 펼친다.
- 공급처는 서버가 제공하는 지원 택배사 목록에서 선택한다. 지원하지 않는 택배사는 임의 URL로 우회하지 않고 Coreable 문의 대상으로 둔다.
- 공급처는 택배사 코드와 송장번호만 등록한다. 실제 집하·배송완료 상태를 직접 입력하지 않는다.
- 서버가 carrier code와 tracking number로 공식 택배사 조회 URL을 생성한다. 임의 URL은 저장하지 않는다.
- 포털 송장 등록은 실제 출고를 의미하지 않으며 `TRACKING_REGISTERED`로 표시한다.
- 한 주문에 여러 Shipment를 허용하고 `shipment_items`로 주문 항목별 수량을 할당한다.
- 첫 송장에서 allocation을 생략하면 아직 미할당된 전 수량을 기본 배정한다. 추가 송장은 명시적 allocation이 필요하다.
- 각 주문 항목의 전체 송장 할당 합계는 주문수량을 넘을 수 없다. 동시 등록은 주문과 항목을 잠가 over-allocation을 막는다.
- 첫 송장 등록 시 주문은 planned `TRACKING_REGISTERED` 상태로 이동한다. 공급처 응답은 raw Order 상태 대신 `FULFILLMENT_REQUESTED`, `TRACKING_REGISTERED`, `DELIVERED`, `SHORTAGE_REPORTED`, `CLOSED`의 전용 표시 상태를 사용해 수락 대기처럼 보이지 않게 한다.
- 공급처는 배송완료 전 자기 송장의 택배사·송장번호만 version guard와 사유로 정정할 수 있다. allocation 오류는 수정하지 않고 Coreable이 Shipment를 `VOIDED`한 뒤 새 송장으로 다시 등록해 과거 할당 증거를 보존한다.
- Coreable은 배송완료 전 중복·오등록 송장을 `VOIDED` 처리해 allocation을 다시 사용할 수 있게 하고, `registeredAt <= deliveredAt <= evidenceObservedAt <= now`인 공식 조회 근거와 사유로 각 유효 Shipment를 배송완료 처리한다. 마지막 non-voided 송장이 없어지면 Order는 `SUPPLIER_ORDER_PENDING`/공급처 표시 `FULFILLMENT_REQUESTED`로, 하나라도 남으면 `TRACKING_REGISTERED`로 재계산한다. 모든 주문수량이 유효 Shipment에 할당되고 그 Shipment가 모두 배송완료된 경우에만 `DELIVERED`다.
- portal 주문의 반품·교환 claim 기간 계산에 쓰는 배송완료 기준시각은 `max(non-voided Shipment.deliveredAt)`이다. voided row의 시각은 증적으로만 남고 기간 기준에는 포함하지 않는다.
- Coreable이 잘못 누른 portal 수동 배송완료는 후속 Claim/Refund가 생성되기 전에만 사유와 idempotency/version guard로 `REOPEN_TRACKING`하거나 `registeredAt <= correctedDeliveredAt <= evidenceObservedAt <= now`인 시각으로 정정할 수 있다. 원래 배송완료 증적은 이력에 남기고, 이미 후속 처리가 있으면 `409`로 막아 incident/claim 절차로 보낸다. 고객에게 보이는 후퇴 정정은 알림을 남긴다.
- 실시간 택배사 상태 API는 공급처 포털 MVP에서 제외한다. 고객은 공식 조회 링크를 열고, Coreable은 기존 수동 배송완료 보정을 사용할 수 있다.
- 고객 주문 응답은 각 유효 Shipment의 택배사명·송장번호·서버 생성 공식 링크와 `TRACKING_REGISTERED` 표시를 제공한다. 고객 문구는 `송장 등록 · 배송조회 가능`이며 실제 집하·배송중을 뜻하지 않는다.
- Coreable 인계된 SUPPLIER_PORTAL 주문은 같은 plural/allocation service의 admin portal-shipment 경로로 출고한다. 새 COREABLE_MANUAL fallback은 기존 admin 경로를 쓴다.
- 기존 Domeggook tracking sync와 단일 Shipment 데이터는 유지하되 portal channel은 legacy 발주 시작/완료·단일 shipment·tracking-sync·manual-correction을 거절한다. unique 제거 전에 singular repository caller를 plural aggregate로 바꾼다. row가 있으면 가장 이른 non-voided row와 truncation flag를 반환하고, row가 없으면 customer는 기존 non-null READY placeholder를, admin은 기존 null을 유지하며 canonical plural은 빈 배열이다. 새 portal row는 carrier registry code와 기존 non-null carrier name을 dual-write한다.

## Shortage, Claims, And Refunds

- 공급처는 VOIDED 포함 Shipment가 한 번도 등록되지 않은 출고 요청에만 품절을 보고할 수 있다.
- shortage POST는 idempotency key, canonical request hash와 immutable supplier-safe submit result를 먼저 조회한 뒤 현재 `operationalOwner` guard를 검사한다. 따라서 첫 보고로 owner가 COREABLE로 인계되거나 review가 끝난 뒤의 동일 retry도 최초 결과를 반환하며, 같은 key의 다른 payload나 같은 order의 새 key는 `409`다.
- 최초 보고는 배송 그룹 전체 `ShortageReport(status=REPORTED)`를 만들고 Fulfillment를 즉시 `operationalOwner=COREABLE`로 인계해 시각·사유를 남기는 데까지만 수행한다. 이 단계에서 Order, Claim, Refund 상태와 고객 알림·환불 queue는 바꾸지 않는다.
- Coreable 관리자 승인·거절도 idempotency key/request hash와 allowlisted review reason code를 요구하고 free text를 받지 않는다. `SHORTAGE_CONFIRMED`만 승인에, `INSUFFICIENT_EVIDENCE|FULFILLMENT_CAN_CONTINUE`만 거절에 쓴다. 승인은 같은 트랜잭션에서 기존 관리자 out-of-stock/refund service를 호출하고 성공한 경우에만 report를 `APPROVED`로 끝내 `OUT_OF_STOCK`, 고객 알림과 환불 처리를 실행한다. 동일 기존 service의 권한·상태·idempotency 규칙을 재사용하고 별도 환불 로직을 복제하지 않는다.
- Coreable 관리자가 report를 거절하면 report를 `REJECTED`로 끝내되 `operationalOwner=COREABLE`을 유지한다. 공급처 list/detail에는 내부 사유 대신 `nextAction=CONTACT_COREABLE`만 보여주며 출고 mutation을 다시 열지 않는다.
- owner 인계 뒤에도 원래 공급처는 별도 shortage report list/detail에서 자기 report의 supplier-safe 상태를 읽을 수 있지만 Order shipment/shortage mutation 권한은 되찾지 않는다.
- 품절 승인도 배송 그룹 주문 전체에만 적용하며 상품·옵션·수량 일부만 환불하지 않는다.
- 공급처는 환불을 승인·거절·완료할 수 없다.
- 고객 클레임의 승인·거절·환불·CS는 Coreable만 수행한다.
- Coreable이 공급처 확인을 요청한 경우 공급처는 다음 사실만 append-only로 기록한다.
  - `SHIPMENT_STOP_RESULT`
  - `RETURN_INSTRUCTIONS`
  - `RETURN_RECEIVED`
  - `INSPECTION_RESULT`
- 공급처 claim-task list/detail은 `orderNumber`, 자기 상품·옵션 요약, 자기 supplier order 상세 direct link만 safe correlation으로 제공하고 고객 PII, Claim 본문, 결제·환불·관리자 context는 제공하지 않는다. detail은 자기 task의 safe fact id/type/payload/correction reference/time만 함께 보여줘 정정 대상을 선택하게 한다. Fact 작성·정정은 idempotency key/request hash와 immutable supplier-safe result를 저장하고 이전 row를 바꾸지 않는다.
- Coreable task 생성은 claim별 idempotency key/request hash와 immutable ADMIN-safe creation result로 network retry가 두 OPEN task를 만들지 않고 terminal/close 뒤에도 최초 응답을 반환하게 한다. 새 확인 round는 새 key를 쓴다. Admin list/detail은 Claim/order 연결, 관리자와 내부 context 및 동일 task의 전체 fact history를 읽을 수 있으며, 읽은 fact는 참고 증거일 뿐 별도 Claim/Refund 관리자 action 없이는 상태를 바꾸지 않는다.
- 공급처 사실 입력은 Claim, Order, Refund 상태를 직접 변경하지 않는다.

## Email And Audit

- 공급처 운영 알림은 검증된 연락 이메일로만 보낸다. SMS, 카카오 알림톡, 앱 푸시는 만들지 않는다.
- 최초 초대 메일만 아직 검증되지 않은 신청 이메일로 보낼 수 있다. 초대 링크 교환 완료를 이메일 소유 검증으로 기록한 뒤에만 다른 운영 알림을 보낸다.
- 알림 유형은 초대, 신규 출고 요청, 상품 검토 결과, Coreable 승인 클레임 작업 요청으로 제한한다.
- 제목·본문·payload snapshot에는 고객 이름, 전화, 주소, 배송 메모, 결제·환불 정보를 넣지 않는다. 주문번호/상품 식별자와 포털 링크만 사용한다.
- 운영 email의 최초 dispatch와 모든 retry는 현재 Supplier를 다시 읽어 time-valid VERIFIED contract, 검증된 연락 이메일, active portal, 현재 manager 연결과 저장 recipient가 모두 일치하는지 확인한다. 하나라도 불일치하거나 lifecycle/contract에서 권한이 철회됐으면 보내지 않고 `SKIPPED`로 끝낸다.
- 실제 email 발송은 발송 결과와 재시도 이력을 남긴다. 현재 SES 실발송이 비활성인 환경에서는 production 공급처 활성화를 허용하지 않는다.
- 운영 email은 생성 뒤 7일까지만 retry한다. Supplier-linked writer는 provider 예외 원문 대신 allowlisted/redacted failure code만 저장한다. `SENT`/`SKIPPED` 또는 retry 종료 `FAILED`의 recipient와 legacy/free-text failure reason은 terminal+30일에 null 처리하고 non-PII code만 보존할 수 있다. B-100은 기존 NOT NULL `notification_logs.recipient`를 nullable로 expand하고 legacy reader/writer 호환을 먼저 배포한다.
- 기존 admin notification retry는 invite-linked row를 항상 거절한다. Supplier 운영 row는 `FAILED`, recipient 존재, 생성+7일 전이면서 현재 portal/manager/time-valid contract/verified email이 다시 일치할 때만 재시도하고, `SKIPPED`/`SENT`/recipient-null/기한 종료 row는 lifecycle/contract가 회복돼도 다시 열지 않는다.
- 공급처의 상품, 재고, 주문, 송장, 클레임 사실 변경은 actor와 supplier tenant를 감사 로그에 남긴다. invite 소비자와 catalog/inventory/lifecycle actor 연결은 B-098 관계 종료 보관기한 뒤 null 처리하고 비PII 행위 증적을 남긴다. Shipment/shortage/claim actor 연결은 parent Order/Claim의 법정 보존기간까지만 유지한 뒤 null 처리하거나 parent와 함께 파기한다. PII access log는 별도 1년 삭제 기준을 따른다.

## Data Changes Status

V39 implements the `B-100` access, invitation, lifecycle, fulfillment-handover base and notification linkage schema. `B-098` contract history/command/scheduler and `B-101`~`B-105` changes remain Planned.

| Area | Change |
| --- | --- |
| Access | managed `SUPPLIER_APPLICATION_PRIVACY`; application status/consent/submit+review idempotency/result/retention fields; `supplier_invites` digest/issuance-idempotency/status/binding/actor-retention fields; `suppliers.manager_user_id`, `portal_status`, contact verification/retention and B-100-owned denormalized contract fields; B-098-owned contract history; append-only lifecycle action history |
| Product | `products.management_channel`, optimistic `version`, immutable nullable `first_submitted_at`, `review_status`, allowlisted `review_reason_code`, supplier-safe `supplier_review_message`; product change history immutable subject id/nullable live FK/actor/version expand-contract; unique server-owned image key와 durable cleanup job |
| Inventory | `product_options.supplier_availability`, `inventory_mode`, `on_hand_quantity`, `reserved_quantity`; order item management-channel/inventory reservation snapshot/status/timestamps; inventory history immutable subject option id와 nullable live FK |
| Fulfillment | B-100-owned `fulfillments.channel`, `operational_owner`, 인계시각·사유·관리자; B-103-owned portal `requested_at`와 monotonic `pii_access_cutoff_at` creation/use |
| Shipment | Shipment 1:N, creation idempotency key와 optimistic version, `carrier_code`, `registered_at`, `registered_by_user_id`, nullable `shipped_at`, `VOIDED`, immutable `shipment_items`, idempotent append-only correction history |
| Order/privacy | `orders.delivery_memo` checkout snapshot, 최소 `supplier_pii_access_logs`, append-only claim PII grant history |
| Payment/refund | planned Payment `PAYMENT_EXCEPTION`; amount mismatch용 actual-amount `PAYMENT_GROUP` Refund 1건과 `PAYMENT_AMOUNT_MISMATCH`; exact-amount late/saleability 및 qualifying unpaid-cancelled용 `LATE_DEPOSIT_EXCEPTION` / `SALE_UNAVAILABLE_AT_DEPOSIT` Order별 Refund |
| Claim | `supplier_shortage_reports`, Coreable-owned `supplier_claim_tasks`, idempotent append-only `supplier_claim_facts`와 supplier-safe fact history projection |
| Notification | 기존 `notification_logs.recipient` nullable expand; token-free invitation log와 ephemeral raw link; supplier recipient linkage, dispatch-time lifecycle revalidation, 7일 retry와 terminal+30일 recipient cleanup, PII-free payload |

`B-100` uses V39. Later implementation branches must recheck the latest migration number; B-099 itself created no migration or runtime code.

## API Surface

### Public and admin onboarding (Implemented B-100; contract command Planned B-098)

All routes below are Implemented by B-100 except `/portal-contract-status`, which remains Planned in B-098.

```text
GET  /api/policies/SUPPLIER_APPLICATION_PRIVACY/current
POST /api/supplier-applications
GET  /api/admin/supplier-applications
GET  /api/admin/supplier-applications/{applicationId}
POST /api/admin/supplier-applications/{applicationId}/approve
POST /api/admin/supplier-applications/{applicationId}/reject
POST /api/admin/suppliers/{supplierId}/invite/reissue
PATCH /api/admin/suppliers/{supplierId}/portal-status
PATCH /api/admin/suppliers/{supplierId}/sales-status
POST /api/admin/suppliers/{supplierId}/portal-contract-status
POST /api/admin/suppliers/{supplierId}/manager-disconnect
PATCH /api/admin/suppliers/{supplierId}/contact-email
POST /api/supplier-invites/session
GET  /api/supplier/auth/kakao/authorize
GET  /api/supplier/auth/kakao/callback
GET  /api/supplier/me
```

### Supplier catalog

```text
GET/POST              /api/supplier/products
GET/PATCH/DELETE      /api/supplier/products/{productId}
POST                  /api/supplier/products/{productId}/submit
POST                  /api/supplier/products/{productId}/options
PATCH/DELETE          /api/supplier/products/{productId}/options/{optionId}
POST                  /api/supplier/products/{productId}/images
DELETE                /api/supplier/products/{productId}/images/{imageId}
PUT                   /api/supplier/products/{productId}/images/order
PUT                   /api/supplier/products/{productId}/detail-blocks
PUT                   /api/supplier/products/{productId}/notice
PUT                   /api/supplier/products/{productId}/options/{optionId}/inventory
POST                  /api/admin/orders/{orderId}/deposit-mismatch
POST                  /api/admin/orders/{orderId}/late-deposit
```

### Coreable product review

```text
GET  /api/admin/product-reviews
GET  /api/admin/product-reviews/{productId}
POST /api/admin/product-reviews/{productId}/approve
POST /api/admin/product-reviews/{productId}/supplement
POST /api/admin/product-reviews/{productId}/reject
```

### Supplier fulfillment

```text
GET  /api/supplier/orders
GET  /api/supplier/orders/{orderNumber}
GET  /api/admin/supplier-pii-access-logs
POST /api/admin/orders/{orderId}/portal-takeover
POST /api/admin/claims/{claimId}/supplier-pii-access-grants
POST /api/admin/claims/{claimId}/supplier-pii-access-grants/revoke
GET  /api/supplier/shortage-reports
GET  /api/supplier/shortage-reports/{reportId}
POST /api/supplier/orders/{orderNumber}/shortage-reports
GET  /api/admin/supplier-shortage-reports
GET  /api/admin/supplier-shortage-reports/{reportId}
POST /api/admin/supplier-shortage-reports/{reportId}/approve
POST /api/admin/supplier-shortage-reports/{reportId}/reject
GET  /api/supplier/carriers
GET  /api/supplier/orders/{orderNumber}/shipments
POST /api/supplier/orders/{orderNumber}/shipments
PATCH /api/supplier/orders/{orderNumber}/shipments/{shipmentId}
POST /api/admin/orders/{orderId}/portal-shipments
GET  /api/supplier/claim-tasks
GET  /api/supplier/claim-tasks/{taskId}
POST /api/supplier/claim-tasks/{taskId}/facts
GET  /api/admin/supplier-claim-tasks
GET  /api/admin/supplier-claim-tasks/{taskId}
POST /api/admin/claims/{claimId}/supplier-tasks
POST /api/admin/supplier-claim-tasks/{taskId}/close
PATCH /api/admin/shipments/{shipmentId}/tracking-correction
POST  /api/admin/shipments/{shipmentId}/void
POST  /api/admin/shipments/{shipmentId}/delivery-complete
POST  /api/admin/shipments/{shipmentId}/delivery-correction
GET   /api/orders/{orderId}/shipments
```

Supplier shortage와 claim-task list/detail은 현재 tenant의 supplier-safe projection만 반환한다. Claim-task correlation은 주문번호·자기 item/option 요약·직접 order link로 제한하며 고객 PII는 포함하지 않는다. Admin shortage와 claim-task list/detail은 Coreable 검토 context를 별도 admin projection으로 읽는다.

## Compatibility Plan

- 기존 COREABLE option은 `UNTRACKED`, B-101에서 미리 생긴 portal option은 `TRACKED/onHand=0`, 기존 order item은 `managementChannelSnapshot=COREABLE`, `NOT_APPLICABLE`로 backfill한다.
- 기존 주문은 현재 수동/Domeggook 발주 채널로 유지한다.
- 기존 Shipment 상태와 `shippedAt`을 보존하고 allocation을 주문 전체 수량으로 backfill한다. 결정적 carrier mapping만 code를 채우며 새 portal row는 code/name을 dual-write한다.
- singular repository caller를 먼저 plural aggregate로 바꾼 뒤 unique를 제거한다. 기존 단일 `shipment`는 최소 한 릴리스 동안 row가 있으면 가장 이른 non-voided row와 truncation flag, row가 없으면 customer READY placeholder/admin null인 현재 endpoint shape를 유지하며 `shipments[]`를 canonical로 추가한다.
- 기존 `READY`, `SHIPPED`, `DELIVERED`, `SUPPLIER_ORDERED` enum을 삭제하지 않는다.
- 기존 `ProductComplianceStatus.PENDING` 공개 동작을 바꾸지 않는다.
- 기존 `CUSTOMER`/`ADMIN` 저장 role을 바꾸지 않고 supplier authority를 추가로 파생한다.
- `B-101`은 분류와 검토를 구현하되 production feature flag를 계속 닫아 portal 상품을 고객 구매 가능하게 만들지 않는다. `B-102` inventory migration과 checkout guard는 release의 필요조건이지만 충분조건은 아니며, 전체 supplier portal activation은 B-100~B-105와 privacy/email/contract gate가 모두 준비된 뒤에만 가능하다.

## Security And Release Gates

- production은 `APP_SUPPLIER_PORTAL_ENABLED=false`를 기본으로 두고, false일 때 외부 신청·초대 수락·supplier route와 portal 상품 고객 구매를 열지 않는다. ADMIN/resource scope와 저장된 idempotency replay 뒤 새 신청 승인·invite 재발급·연락 이메일 후속 초대 발급은 mutation 전에 `SUPPLIER_PORTAL_NOT_RELEASED`로 거절한다. 동일 완료 command는 token-free 결과만 반환하고 재발송하지 않는다. Dispatch도 발송 직전 flag를 재검사해 stale job을 `SKIPPED/PORTAL_NOT_RELEASED`로 끝내고, 다시 연 뒤에는 새 key 재발급으로 복구한다. 신청 거절, portal 정지/종료, retention cleanup, 관리자 문서 검토와 기존 Coreable 주문 운영은 계속 가능하다. Planned B-098 contract evidence도 구현 뒤에는 이 release gate 밖에서 관리한다. B-102 완료만으로 이 flag를 열지 않는다.
- B-100은 cookie 인증 supplier `POST`/`PUT`/`PATCH`/`DELETE` 요청의 `Origin`을 설정된 web origin allowlist와 비교한다. `Origin`이 없는 요청은 같은 origin의 `Referer`가 있을 때만 허용하고 둘 다 없거나 불일치하면 `403`으로 거절한다.
- 인증 cookie는 production에서 `HttpOnly`, `Secure`, `SameSite=Lax`를 강제하며 Origin/Referer 성공·실패 경계를 통합 테스트한다.
- 모든 supplier query가 supplier predicate를 포함하는지 통합 테스트로 검증한다.
- 초대 token 원문, 신청자/공급처 연락 PII, 고객 PII, PII-bearing idempotency key/HMAC, 이메일 본문, 공급가와 내부 메모를 request/application log에 남기지 않는다.
- 실제 공급처 주문을 열기 전에 공급처·택배사 제3자 제공 고지와 개인정보처리방침 버전을 갱신한다.
- 실제 초대/운영 이메일 도착을 검증하기 전 production supplier activation을 막는다.
- B-098에서 외부 공급처 계약, 개인정보 취급 의무와 거래조건을 확정하고 해당 Supplier의 time-valid VERIFIED evidence를 기록하기 전에는 실제 외부 공급처의 판매를 production에서 활성화하지 않는다.
- B-097의 실제 연락과 B-098의 계약·상품 반영은 사용자 승인 경계를 유지하며 포털 구현이 이를 자동 실행하지 않는다.

## Implementation Slices

1. `B-100` — Implemented: 신청, 관리자 승인, 이메일 초대, Kakao 연결, supplier tenant guard, denormalized contract fail-closed columns, fulfillment channel/owner/handover additive schema와 lifecycle audit
2. `B-101` — Planned: 개별 상품·옵션·이미지·고시 등록, 미제출 DRAFT 삭제와 감사/asset cleanup, 자동/수동 검토, Coreable 가격 계산
3. `B-102` — Planned: TRACKED/UNTRACKED 재고, 24시간 예약·만료, 금액 불일치 결제그룹 전액 환불, 늦은 입금 재확보
4. `B-103` — Planned: 공급처 출고 요청 생성·목록/상세, KEEP `COREABLE_MANUAL` fallback, 배송 메모 snapshot, `requestedAt + 60일` PII fallback, 접근 로그, 이메일 알림
5. `B-104` — Planned: report table에 의존하지 않는 복수 Shipment 공통 lock/service, 수량 할당, 공식 택배사 링크, 송장마다 monotonic PII cutoff 단축, 고객/admin 호환
6. `B-105` — Planned: REPORTED 품절 인계와 Coreable 승인/거절, 기존 Shipment service에 report lock/open guard 확장, supplier claim facts, 환불 경계

## Cross-Slice Verification Contract

| Risk | Required proof before owning slice is review-ready |
| --- | --- |
| Onboarding | active policy version, SUBMITTED/APPROVED 중복 신청·승인/거절 key-hash-result replay와 mode, LINK_EXISTING email 동기화, INACTIVE+UNVERIFIED 신규값, time-valid contract gate, flag-off 발급/dispatch fail-closed, token-free NotificationLog/ephemeral link/new-key 재발급, 초대 만료/폐기/재사용/동시 callback과 안전 오류 UX, lifecycle/sales-status replay, Kakao 외 provider 거절, 기존 CUSTOMER/ADMIN 권한 보존, manager 탈퇴 차단 |
| Tenant/security | supplier A의 product/order/shipment/claim task로 supplier B가 접근할 때 `404`, payload supplier id 무시, Origin/Referer 허용·거절, feature flag off |
| Catalog | 한 번의 등록 동작, Product version stale review 거절, 일반 자동 공개와 fail-closed 검토/보완 재제출 전이, legacy admin/source writer version 증가, supplier 비활성 공개 차단, full release gate, 공급가 변경의 결정적 formula·policy version·calculator snapshot, 금지 필드·public DTO 누출, HTML/image 안전, 최초 submit 전 DRAFT만 삭제, 제출복귀 DRAFT/CartItem/OrderItem/마지막 option 거절, cart·checkout 경합, 삭제 후 404와 subject-id 감사 보존, DETAIL image ownership과 cleanup retry |
| Inventory/payment | canonical inventory projection과 immutable subject-option id/nullable live FK의 idempotent history, 동시 checkout oversell 방지, B-101 portal option backfill, immutable portal-origin snapshot, 혼합 PaymentGroup 원자성, lifecycle/deposit 공유 lock order와 상태 재확인, cancel/expiry 중복 해제 방지, 입금확인 소비, 부족·초과 입금의 actual-amount 단일 PaymentGroup Refund와 완료 replay, normal/late 판매불가·늦은 입금 재확보 성공/실패·미입금취소 뒤 exact receipt의 no-resume Order별 환불 및 supplier 비노출 |
| PII/email | 신청·초대·운영메일 cleanup, NotificationLog recipient nullable migration, supplier actor FK의 관계/거래 보관기한 cleanup, 목록 forbidden-field 직렬화, stored monotonic cutoff 직전/정각/직후와 void/replacement 비연장, REFUND_REQUESTED 포함 terminal 즉시 mask, cutoff scheduler/admin idempotent takeover 이력, 30일 claim grant/연장/철회/상태변경 만료와 COREABLE-owner read-only 예외, `no-store`, 접근 로그 무PII, email dispatch/retry recipient·lifecycle 재검증과 `SKIPPED` |
| Shipment | stable order-item id와 remaining 수량, supplier shipment version 응답, 단일 기본 전체 할당, 분할 opt-in, 양수/소속/누적 수량 guard, action+actor+body hash의 supplier/admin 공유-key idempotency, carrier dual-write, carrier/tracking 정정, allocation 오류 void+재등록, admin takeover creation/void/배송완료/근거시각 정정과 aggregate 재계산, 고객 공식 URL, legacy route guard/singular repository·projection 호환 |
| Shortage/claim | owner guard 전 duplicate 조회, REPORTED 생성+COREABLE 인계에서 Order/Refund 불변, admin 승인 시 기존 품절/refund service 실행, 거절 후 COREABLE owner/CONTACT_COREABLE, VOIDED 포함 송장 후 거절, max(non-voided deliveredAt) claim 기준, safe correlation의 supplier/admin task projection과 idempotent append-only fact 정정 |

DB 변경 slice는 PostgreSQL migration smoke를 포함한다. 각 slice는 변경 범위 테스트와 `git diff --check`를 통과하고, PR CI에서 전체 API test와 Web lint/build를 한 번 실행한다.

각 slice는 하나의 backlog/commit 단위이며 대상 테스트, 문서 동기화, `git diff --check`를 통과한 뒤 `review_ready`로 보고한다.
