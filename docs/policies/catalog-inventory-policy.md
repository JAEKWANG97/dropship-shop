# Catalog And Inventory Policy

Status: Confirmed

## Purpose

상품, 옵션, 공급처, 판매 상태, 품절 처리 기준을 정한다.

## Policy Areas

- 상품 등록 기준
- 상품 옵션 구성
- 상품 이미지 정책
- 상품 상세 작성 방식
- 공급처 정보 관리
- 실제 재고 수량 관리 여부
- 상품/옵션 판매 상태
- 주문 후 공급처 품절 처리

## Initial Direction

- 실제 재고 수량은 관리하지 않는다.
- 상품과 옵션은 판매 상태만 가진다.
- 옵션 단위 품절을 허용한다.
- 상품 상태는 `ACTIVE`, `SOLD_OUT`, `HIDDEN`, `STOPPED`로 시작한다.
- 옵션 상태는 `ACTIVE`, `SOLD_OUT`, `STOPPED`로 시작한다.
- 공급처 품절이 확인되면 관리자가 상품 또는 옵션을 품절 처리한다.

## Confirmed Policy

- 현재 구현의 Coreable 관리/Domeggook 상품은 실제 재고 수량을 보장하지 않고 판매 상태와 외부 source metadata를 유지한다.
- 상품 전체 상태와 상품 옵션 상태를 분리한다.
- 상품이 `ACTIVE`이고 옵션도 `ACTIVE`일 때만 고객이 구매할 수 있다.
- 상품별 최소주문수량과 주문단위는 1~99로 관리하며 기본값은 각각 1이다.
- 유효한 주문수량은 최소주문수량 이상이며 수량이 주문단위로 나누어떨어져야 한다.
- 상품은 판매가가 0원보다 크고, 대표 이미지, `ACTIVE` 옵션, 활성 상품 고시가 있으며 인증 검수 상태가 `REJECTED`가 아닐 때 `ACTIVE`가 될 수 있다.
- 인증 검수 상태는 `PENDING`, `NOT_REQUIRED`, `VERIFIED`, `REJECTED`로 관리하되 `PENDING`은 자동 공개를 차단하지 않는다.
- 신규 상품은 판매 필수정보를 확인한 뒤 `ACTIVE`로 전환한다.
- 상품이 `HIDDEN`이면 고객 상품 목록과 상세에서 노출하지 않는다.
- 상품이 `STOPPED`이면 더 이상 판매하지 않는 상품으로 취급한다.
- 옵션이 `SOLD_OUT`이면 해당 옵션만 선택할 수 없다.
- 옵션이 `STOPPED`이면 해당 옵션은 더 이상 판매하지 않는다.
- 상품 상세 콘텐츠는 `IMAGE`와 `HTML` 블록으로 구성한다.
- 상품 상세 블록은 노출 순서를 가진다.
- `IMAGE` 블록은 공급처가 제공한 상세 이미지 또는 운영자가 제작한 상세 이미지를 업로드해 사용한다.
- `HTML` 블록은 관리자만 입력할 수 있으며, 서버 저장 시점에 safelist 기반으로 sanitize해야 한다.
- 배송, 교환, 환불, 주문 후 품절 가능성 같은 운영 정책 고지는 상품 상세 이미지/HTML에만 의존하지 않고 별도 텍스트 정책 영역에 노출한다.
- 대표 이미지는 상품당 1장으로 시작한다.
- 갤러리 이미지는 상품당 최대 10장으로 시작한다.
- 상세 블록 이미지는 상품당 최대 50장으로 시작한다.
- 이미지 1장당 최대 파일 크기는 10MB로 제한한다.
- 지원 이미지 확장자는 `jpg`, `jpeg`, `png`, `webp`로 제한한다.
- 이미지 업로드는 파일명 확장자와 실제 이미지 파일 시그니처를 함께 검증한다.
- 상품 가격 변경은 변경 이후 생성되는 새 주문부터 적용한다.
- 이미 결제 완료된 주문은 결제 당시 상품명, 옵션명, 단가, 수량, 결제 금액을 유지한다.
- 이미 결제 완료된 주문은 주문 시점의 상품 요약, 상품 상세 버전, 상품 정보 제공 고시 버전 참조도 유지한다.
- 상품 상세 이미지/HTML 또는 상품 정보 제공 고시가 변경되어도 기존 주문 상품 스냅샷은 변경하지 않는다.
- 공급처 가격이 결제 이후 변경되더라도 고객에게 추가 청구하지 않는다.

## Supplier Portal Catalog And Inventory — `B-101`/`B-102` Implemented

Status: `B-101` catalog/review and `B-102` inventory/reservation are Implemented. Existing Coreable/Domeggook catalog behavior remains compatible and the production portal gate remains closed.

- 공급처 포털 옵션은 `TRACKED`와 `UNTRACKED` 재고 모드를 가진다. 기존 COREABLE 옵션은 `UNTRACKED`, B-101에서 B-102 전에 생성된 portal 옵션은 `TRACKED/onHand=0`, 이후 신규 포털 옵션은 `TRACKED`가 기본이다.
- `TRACKED` 옵션은 on-hand와 reserved를 저장하고 available을 `onHand - reserved`로 계산한다. `UNTRACKED` 옵션은 on-hand를 저장하지 않으며 reserved는 0이다.
- 상품과 옵션이 모두 `ACTIVE`이고, `TRACKED` 옵션이면 available이 주문수량 이상일 때만 고객이 구매할 수 있다.
- 공급처 화면은 `상품 등록` 동작 하나로 최종 검증·분류를 끝내며 별도 승인 요청 단계를 요구하지 않는다. 일반 상품은 구조 검증과 판매 준비 조건을 통과하면 반드시 자동 공개한다. 인증·카테고리·법정 필수정보 규칙이 사람 판단을 요구하면 `HIDDEN`으로 두고 Coreable 검토를 거친다.
- 모든 상품의 자동 공개와 계속 판매는 해당 `Supplier.status=ACTIVE`일 때만 허용한다. Portal 관리 상품은 B-098 evidence가 `VERIFIED`, `effectiveAt <= now`, expiry 없음 또는 `now < expiresAt`인 경우만 허용하고 checkout/입금 시 overdue evidence를 Supplier lock 아래 공통 terminal routine으로 EXPIRED 처리한다. 이 routine은 sales INACTIVE와 함께 ACTIVE portal suspension, invite 폐기와 open-work Coreable 인계를 수행한다.
- 자동 공개 분류는 허용 규칙이 명확히 통과된 경우에만 성공한다. 분류 결과가 없거나 필수 근거가 누락되거나 규칙 실행이 실패하면 `REVIEW_REQUIRED`로 처리한다.
- 허용 규칙은 기존 B-093 category policy를 재사용한다. 구조·필수 고시·thumbnail·active option을 갖춘 `A` category와 `PPE_WORK_GLOVES`만 자동 승인 후보로 두고, 기존 KOSHA 대상 category는 compliance가 `VERIFIED` 또는 `NOT_REQUIRED`가 아니면 `CERTIFICATION_REVIEW`, B-093 `R`/`M` category는 `CATEGORY_REVIEW`, 필수정보 누락은 `REQUIRED_INFO_MISSING`, 미분류·규칙 누락·실행 실패는 `SAFETY_REVIEW`로 fail closed한다.
- 새 category는 정책과 classifier allowlist가 함께 갱신되기 전까지 자동 승인하지 않는다. B-101은 structured notice/category/compliance와 public ProductImage만 사용하고 private 인증문서 업로드·보관 모델을 만들지 않는다.
- 공급처 포털 검토 상태는 `DRAFT`, `AUTO_APPROVED`, `REVIEW_REQUIRED`, `SUPPLEMENT_REQUESTED`, `APPROVED`, `REJECTED`로 별도 관리하며 기존 인증 검수 상태의 의미를 바꾸지 않는다. `CERTIFICATION_REVIEW`에 대한 Coreable `APPROVED`는 포털 사람 검토만 통과시키고 `complianceStatus`를 자동 변경하지 않는다. 기존 정책대로 `PENDING`은 판매를 차단하지 않고 `REJECTED`만 판매 준비를 차단한다.
- 포털 상품의 최초 submit 시각은 한 번만 기록한다. 현재 `DRAFT`여도 이 시각이 있으면 이미 검토·공개 흐름에 들어간 상품이므로 새 초안처럼 삭제할 수 없다.
- Product aggregate version을 두고 supplier mutation과 admin review는 expected version으로 stale write를 거절한다. 기존 admin/source writer도 같은 version을 증가시키며 기존 admin body는 호환 릴리스 동안 additive optional precondition으로 이관한다. Admin/review/cart/checkout/source writer는 scalar supplier/ownership만 먼저 조회하고 `Supplier -> fresh Product -> 모든 Option(id)` 순서로 잠근다. lock 대기 뒤 fresh Product의 supplier가 discovery 또는 요청 tenant와 다르면 경로에 맞는 conflict 또는 tenant-safe `404`로 거절한다.
- Domeggook sync는 fetch에 사용한 `sourceItemNo`와 위 잠금 뒤 fresh Product의 현재 `sourceItemNo`가 정확히 같을 때만 성공 snapshot 또는 실패를 기록한다. V40의 durable `sourceAutoSoldOut`은 기본값과 기존 row backfill이 `false`이며, sync가 confirmed unavailable로 실제 `ACTIVE -> SOLD_OUT`을 적용할 때만 `true`가 된다. Sync target과 자동 복구는 이 marker가 `true`인 `SOLD_OUT`만 포함하고, 공급처 MOQ가 10 이하이며 현재 판매가가 양수·상한 이하이고 compliance가 `REJECTED`가 아니며 `ACTIVE` 옵션·대표 이미지·활성 고시가 모두 있을 때만 `ACTIVE`로 복구한 뒤 marker를 `false`로 지운다. Admin status 명령은 같은 `SOLD_OUT` 재지정을 포함해 marker를 `false`로 지워 수동 품절을 보호한다.
- 최초 DRAFT submit은 AUTO_APPROVED 또는 REVIEW_REQUIRED로만 끝난다. REVIEW_REQUIRED admin은 APPROVED/SUPPLEMENT_REQUESTED/REJECTED만 선택하고, 보완 편집·재제출은 숨김을 유지한 채 반드시 REVIEW_REQUIRED로 돌아가며 REJECTED는 직접 재제출하지 않는다.
- 안전·인증·카테고리·고시 등 검토 결과에 영향을 주는 승인·검토중 상품 수정은 즉시 `HIDDEN/DRAFT`로 만들고 분류를 다시 실행한다. supplier-safe 보완/거절 문구와 내부 관리자 reason은 분리한다.
- 공급처는 자기 상품의 공급가, 옵션 공급가, 공급처 옵션코드, MOQ/주문단위, 이미지, 상세, 고시, 재고를 관리할 수 있다. supplier id, 고객 판매가, 판매 상태와 검토 상태는 정할 수 없다.
- 공급처 소유 `SUPPLIER_PORTAL` 상품은 `DRAFT`이고 최초 submit 전이며 상품 또는 그 옵션을 참조하는 CartItem·OrderItem이 하나도 없을 때만 실제 삭제한다. 옵션도 같은 단계에서 자기 CartItem·OrderItem 참조가 없고 최소 한 옵션을 남길 때만 실제 삭제한다.
- 한 번이라도 submit·검토·공개됐거나 장바구니·주문에 사용된 상품과 옵션에는 일반 soft-delete나 hard-delete를 적용하지 않는다. Coreable이 기존 `HIDDEN`·`STOPPED` 또는 옵션 `STOPPED` 상태로 보존하며 공급처는 이를 덮어쓸 수 없다.
- 실제 삭제는 expected Product version을 요구하고 scalar ownership discovery 뒤 `Supplier -> fresh Product -> 모든 Option(id)` 잠금 상태에서 tenant/version/참조를 다시 검사한다. Cart 추가와 checkout의 CartItem·OrderItem 생성도 같은 잠금 계약과 fresh ownership/saleability 재검증을 사용해 stale owner에 참조를 만들거나 raw FK 오류를 노출하지 않는다.
- 삭제 가능한 미제출 `DRAFT/HIDDEN`은 정상 장바구니·checkout에서 판매불가다. B-101은 불가능한 판매가능·삭제가능 상태를 인위적으로 만들지 않고 공통 잠금 순서를 단위 검증하며, 기존 CartItem/OrderItem 참조는 통합 테스트로 삭제 거절을 확인한다.
- 삭제 이력은 live FK와 별도의 immutable subject product/option id를 보존한다. 삭제 transaction은 allowlisted before snapshot과 actor/version을 먼저 기록하고, live FK는 `ON DELETE SET NULL`로 비운다. 자유입력 사유 대신 상품은 `DRAFT_ABANDONED`, 옵션은 `DRAFT_OPTION_REMOVED` 서버 reason을 사용한다. 삭제된 상품은 일반 조회에서 `404`지만 관리자는 subject id로 이력을 조회할 수 있다.
- 포털 업로드 이미지는 서버가 만든 single-use unique storage key를 metadata에 보존한다. Cleanup job이 한 번이라도 생긴 key는 tombstone으로 보고 `PENDING` 또는 terminal 여부와 무관하게 admin metadata 재첨부를 거절한다. Metadata 삭제와 durable cleanup job enqueue를 함께 commit하고 동일 key enqueue는 같은 job으로 멱등 처리한다. Worker는 binary 삭제 직전 live `ProductImage.storageObjectKey` 참조를 다시 확인하고 참조가 있으면 삭제하지 않은 채 `COMPLETED/LIVE_REFERENCE`로 끝낸다. 이후 실제 metadata 제거가 같은 key를 enqueue하면 그 job을 `PENDING`으로 다시 열어 삭제를 재시도하며 DB row를 되살리지 않는다. 외부/legacy URL은 Coreable 소유 storage key가 아니면 삭제하지 않는다.
- 공급처 검토 응답은 allowlist 표시 상태, 사유 코드, supplier-safe message와 next action만 노출한다. 내부 관리자 메모·담당자·분류 trace는 숨기고 보완은 같은 상품 편집/등록 동작으로 재제출한다.
- 무옵션 상품도 주문 호환을 위해 내부 `기본` 옵션 한 개를 가진다.
- 해당 상품의 승인된 공급처 담당자는 IMAGE/HTML 상세를 입력할 수 있으며 기존 관리자와 같은 파일 검증·sanitize 규칙을 적용한다.
- 새 supplier IMAGE 상세 블록은 같은 상품에 서버가 업로드한 `DETAIL` ProductImage만 참조한다. 임의 URL/storage key와 다른 상품 이미지는 거절하고, 참조 중인 DETAIL 이미지는 블록과 함께 제거하기 전 단독 삭제하지 않는다.
- `TRACKED` 재고는 checkout 생성 시 24시간 결제기한까지 예약하고, 입금확인 시 소비하며, 미입금 취소 또는 만료 시 해제한다.
- 공급처 재고 수정은 절대값 기반 idempotent update로 처리하고, Product 검토 버전과 분리된 마지막 `inventoryVersion`을 요구하며, on-hand를 현재 reserved 아래로 낮출 수 없다. 공급처 수정과 예약 lifecycle은 이 버전을 증가시키지만 상품 검토 상태는 바꾸지 않는다.
- 해당 option을 참조하는 open `PAYMENT_PENDING` OrderItem이 하나라도 있으면 `TRACKED <-> UNTRACKED` 양방향 전환을 모두 거절한다. 참조가 모두 끝난 뒤 `UNTRACKED -> TRACKED`로 전환할 때는 on-hand를 함께 입력한다.
- 결제 후 취소·반품·환불은 실제 실물 반환을 보장하지 않으므로 on-hand를 자동 복구하지 않는다.
- 고객에게 재고 모드나 `무제한` 표현을 노출하지 않고 구매 가능 또는 품절만 표시한다.
- 공급처 UI는 `수량 관리 (권장)`을 TRACKED 기본값으로 두고 0 이상의 on-hand와 예약 도움말을 제공한다. `재고 수량 관리 안 함`을 선택하면 on-hand 입력을 제거하고 별도 `주문 받기`/`주문 중지` availability를 안내한다. 공급처 AVAILABLE은 Coreable 판매중지·숨김·안전 상태를 덮어쓰지 못한다.
- B-101은 기존 상품을 `managementChannel=COREABLE`, portal 생성 상품을 `SUPPLIER_PORTAL`로 고정한다. B-102는 이를 checkout OrderItem에 snapshot하고, B-101에서 미리 생성된 portal option을 `TRACKED/onHand=0`, 기존 COREABLE option을 `UNTRACKED`로 이관한다.
- B-100~B-105가 구현된 현재도 production supplier portal feature gate는 닫아 portal 상품을 고객에게 구매 가능하게 만들지 않는다. 이 구현은 필요조건일 뿐이며 privacy/live-email/contract gate가 모두 준비된 뒤에만 activation gate를 연다.

## System Impact

- `stockQuantity`를 현재 구현의 핵심 필드로 두지 않는다.
- 결제 후 품절 가능성을 주문/환불 정책에서 반드시 처리해야 한다.
- 구매 가능 조건은 상품·옵션이 모두 `ACTIVE`이고 수량이 상품의 최소주문수량·주문단위 규칙을 만족하는 것이다.
- `ACTIVE` 전환과 활성 상품의 가격·대표 이미지·옵션·명시적 인증 거절 상태 변경은 같은 판매 준비 조건을 검증해야 한다.
- 상품 전체 품절과 옵션 단위 품절을 구분해 관리자 화면에서 처리해야 한다.
- 상품 상세 콘텐츠 모델은 `ProductDetailBlock` 또는 동등한 구조를 가진다.
- HTML 상세는 XSS 위험이 있으므로 허용 태그, 속성, URL protocol을 제한해야 한다.
- 운영 정책 고지는 상품 설명 콘텐츠와 분리해 재사용 가능한 정책 영역으로 관리해야 한다. Product detail responses include reusable policy links by DS-16.
- 이미지 업로드는 파일 개수, 파일 크기, 확장자, 실제 파일 시그니처를 검증해야 한다.
- 업로드 이미지 바이너리는 DB 밖의 storage에 저장하고 DB에는 URL 또는 storage key만 저장한다. 현재 production-style 배포는 EBS-backed local upload volume과 S3 backup을 사용하며, 다중 서버나 복구 요구가 커지면 S3-compatible serving으로 전환한다.
- 상품명, 요약, 가격, 옵션, 판매 상태, 구조화된 상품정보제공고시 행, 상세 HTML/이미지 블록 메타데이터는 PostgreSQL에 저장한다.
- 공급처 거래조건과 공급사 정보는 수집 원본에 보존할 수 있지만 고객 상품 상세에는 노출하지 않는다.
- 이미지 바이너리는 PostgreSQL에 저장하지 않는다. 로컬 개발과 현재 단일 서버 운영은 파일 시스템 저장소를 사용하고 S3에 백업한다.
- `product_images.image_url`과 `products.thumbnail_image_url`은 이미지 바이너리 자체가 아니라 고객 화면에서 접근 가능한 URL 또는 object storage key에서 파생된 URL만 저장한다.
- 프론트엔드는 별도 mock catalog를 장기 보관하지 않고 백엔드 API 응답을 기준으로 렌더링한다.
- 로컬 화면 검증용 샘플 상품은 백엔드 `local` profile seed 또는 관리자 등록 스크립트로 실제 DB에 생성한다.
- 주문 상품에는 가격과 상세 변경의 영향을 받지 않도록 주문 시점의 상품명, 옵션명, 단가, 수량, 금액, 상품 요약, 상품 상세 버전, 상품 정보 제공 고시 버전 참조를 스냅샷으로 저장한다.

### B-101 / B-102 Implemented Impact

- 기존 `sourceStockQuantity`는 외부 참고값으로 유지하고 portal inventory의 on-hand로 자동 전환하지 않는다.
- `ProductOption`은 portal inventory용 `inventoryMode`, `onHandQuantity`, `reservedQuantity`를 가지며 available은 저장하지 않는다.
- 주문 생성 시 상품/옵션 판매 상태, 주문수량 규칙과 tracked available을 한 트랜잭션에서 검증한다.
- 주문 항목은 inventory mode snapshot과 `NOT_APPLICABLE`, `HELD`, `CONSUMED`, `RELEASED` 예약 상태 및 시각을 보존한다.
- 공급처 포털 상품 변경 요청은 항상 현재 principal의 supplier 소유권을 쿼리에 포함해야 한다.
- 공개 상품 쿼리와 checkout 재검증은 모든 상품의 supplier 활성 상태를 함께 확인해야 한다. B-101 배포 전 기존 `INACTIVE` supplier의 공개 상품 영향을 조회하고 운영자가 상태를 확인한다.
- 공급처 상품 변경 이력은 기존 관리자 이력을 actor user/type 기반으로 일반화하되 기존 관리자 이력 조회 호환성을 유지해야 한다.

## Open Questions

None
