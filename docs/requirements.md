# Requirements

## Functional Requirements

### Account

- 고객 로그인 화면에는 카카오 소셜 로그인만 노출해야 한다.
- 기존 Google/Naver OAuth 계정 호환을 위한 백엔드 경로는 유지할 수 있다.
- 고객은 로그인/로그아웃할 수 있어야 한다.
- 고객 이메일/비밀번호 로그인은 MVP에서 제공하지 않는다.
- 고객은 첫 가입 또는 첫 소셜 로그인 완료 시 이용약관과 개인정보처리방침에 동의해야 한다.
- 소셜 로그인에서 필수 저장하는 값은 제공자, 제공자 user id, 표시 이름으로 시작해야 한다.
- 카카오 로그인은 닉네임과 이메일 동의를 요청하고, 유효하고 인증된 이메일이 제공되면 저장해야 한다.
- 카카오가 이메일을 제공하지 않는 경우 고객 연락처는 주문, 배송, 클레임에 필요한 시점에 별도로 수집되어야 한다.
- 전화번호는 소셜 로그인 제공자에서 필수 수집하지 않고, 로그인 후 배송 연락처로 직접 입력받아 형식을 검증해야 한다.
- 고객은 배송지 정보를 관리할 수 있어야 한다.
- 고객은 회원 탈퇴를 요청할 수 있어야 한다.
- 회원 탈퇴 시 고객 프로필과 소셜 계정 연결은 삭제 또는 비식별화되어야 한다.
- 법정 보존 대상 주문, 결제, 배송, 클레임 기록은 탈퇴 후에도 분리 보관되어야 한다.
- 법정 보존 기록에는 보존 사유와 보존 만료일이 저장되어야 한다.
- 전자상거래 표시/광고 기록은 6개월, 계약/청약철회 기록은 5년, 대금결제/재화 공급 기록은 5년, 소비자 불만/분쟁 처리 기록은 3년 보존 기준으로 시작해야 한다.
- 탈퇴 후 같은 소셜 계정으로 재가입하면 새 고객 계정으로 생성되고 기존 주문 내역은 고객 화면에 자동 복구되지 않아야 한다.
- 관리자는 카카오 소셜 로그인 후 DB에 등록된 관리자 권한으로 접근할 수 있어야 한다.

### Supplier Portal Onboarding — Implemented (`B-100`), Production Gated

`B-100`의 신청·승인/거절·초대·Kakao 연결·동적 권한·lifecycle·신청/초대 retention·Origin/feature gate와 기본 Web 화면, `B-101`의 개별 상품 등록·검토 흐름, `B-102`의 옵션 재고·24시간 예약·입금 예외, `B-103`의 출고 요청·최소 PII·운영 이메일 기반은 구현됐다. `B-098` 계약 증적 명령·scheduler·관계 종료 cleanup과 `B-104`~`B-105`는 Planned이며, active 공급처 신청 개인정보 고지와 실제 이메일 delivery 및 전체 release gate가 준비될 때까지 production flag는 기본 `off`를 유지한다.

- 비로그인 사용자는 필수 공급처명·담당자명·연락 이메일과 선택 전화번호·문의 메모로 공급처 신청을 제출할 수 있어야 한다.
- 신청 화면은 active `SUPPLIER_APPLICATION_PRIVACY`의 수집 목적, 항목, 보유 기간, 동의 거부 시 신청 불가를 고지해야 한다. 서버는 exact active version을 검증하고 canonical 동의시각을 저장해야 한다.
- 공개 신청은 rate limit을 적용하고 Coreable 관리자가 승인 또는 거절해야 하며 처리 관리자, 시각과 사유를 남겨야 한다. 승인·거절은 필수 `Idempotency-Key`, canonical keyed-HMAC와 immutable result를 저장해 같은 key/hash만 replay하고 mode·대상 Supplier·reason이 달라지거나 반대 action이면 거절해야 한다.
- 정규화 연락 이메일별 non-expired `SUBMITTED` 또는 `APPROVED` 신청은 합쳐서 하나만 허용해야 한다. 새 submit도 같은 이메일의 overdue SUBMITTED를 lock 아래 EXPIRED·cleanup한 뒤 duplicate를 판단해야 한다. 미검토 SUBMITTED는 생성 90일 뒤 EXPIRED·비식별화하고, 거절 신청 연락 PII는 거절 90일 뒤 비식별화해야 한다. APPROVED 연락은 Supplier 운영 기록으로 관리하고 B-098의 관계 종료 retention deadline에 application 중복 PII와 함께 정리해야 한다.
- 사람은 신청을 `SUBMITTED -> APPROVED|REJECTED`로만 전이하고 terminal 반대 action을 거절해야 한다. Review는 row lock 뒤 deadline을 재검사해 기한이 지났으면 scheduler와 같은 EXPIRED cleanup을 먼저 적용하고 승인·거절을 거부해야 한다. 승인은 신규 생성 또는 manager/invite/application/portal 이력이 없는 legacy DISABLED 기존 Supplier 연결을 명시해야 하며 이름/email 자동 매칭과 영구종료 supplier 재연결을 금지해야 한다.
- 승인 시 공급처를 중복 생성하지 않고 신청 연락 이메일로 만료되는 1회용 초대를 보내야 한다. 기존 Supplier 연결은 그 연락 이메일로 운영 연락처를 동기화하고 검증시각을 비운 뒤 같은 주소로 초대해야 한다. token과 연결 안내만 담은 최초 초대는 연락 이메일을 검증하기 위한 유일한 미검증 이메일 발송이어야 한다.
- 관리자 승인만으로 포털 접근을 열지 않고, 초대 교환과 Kakao 담당자 연결이 완료된 뒤 별도 portal 상태를 활성화해야 한다.
- 초대 원문은 저장하거나 로그에 남기지 않고 unique digest, issuance idempotency key/request hash와 수신자·만료·소비·폐기 이력만 저장해야 한다. 공급처별 유효한 미사용 초대는 하나이고, PENDING_ACTIVATION·manager 없음·현재 연락처 미검증 상태에서만 새 key와 allowlisted reason code의 재발급이 이전 초대를 폐기해야 한다. ACTIVE/SUSPENDED/DISABLED 또는 manager-bound 상태는 거절하고 동일 retry는 첫 결과를 반환해야 한다. 재발급은 free-text reason을 받지 않아야 한다.
- 초대 수락 로그인은 Kakao만 허용하고 이메일/비밀번호, Google, Naver 공급처 로그인은 제공하지 않아야 한다.
- 초대 binding과 OAuth state를 결합하고 callback에서 초대 잠금, 담당자 연결, 이메일 검증, portal 활성화와 초대 소비를 한 트랜잭션으로 처리해야 한다.
- 한 공급처에는 활성 담당자 한 명만 연결되어야 하며, 기존 고객 또는 관리자 계정의 저장 role을 교체하지 않고 공급처 권한을 추가로 파생해야 한다.
- active user, 활성 portal 상태와 현재 manager 연결로 공급처 권한을 파생하되 terminal 또는 overdue VERIFIED contract는 즉시 권한을 막아야 한다. 최초 UNVERIFIED onboarding은 비PII catalog 작업만 허용할 수 있다. `Supplier.status`는 별도의 신규 판매·checkout gate이며, 거래 상태가 비활성이어도 time-valid contract가 있는 담당자만 기존 입금확인 주문을 출고할 수 있어야 한다.
- 포털 정지 또는 담당자 연결 해제는 `/api/supplier/**` 권한을 즉시 제거하고 기존 결제완료 portal 주문을 Coreable 인계 큐에 고정해야 한다. 신규 판매 중지는 안전한 UI 기본값으로 제공하되 요청의 `salesAction=KEEP|PAUSE`로 명시해야 한다. `KEEP`이면 portal 접근이 돌아올 때까지 신규 입금확인 주문을 `COREABLE_MANUAL`로 보내고, `PAUSE`이면 신규 checkout을 막아야 하며 portal 재개가 거래 상태나 인계 주문을 자동 복구해서는 안 된다.
- 연락 이메일 변경은 `salesAction=KEEP|PAUSE`를 필수로 받고 기존 manager 연결과 검증시각을 지우고 초대를 폐기한 뒤 재초대를 요구해야 한다. `KEEP`인 동안 신규 입금확인 주문은 같은 Coreable fallback을 사용해야 한다.
- 포털 상태와 별도인 판매 재개/중지 관리자 명령을 제공해야 한다. portal/contact/manager/sales lifecycle 명령은 idempotency key와 PII-free reason을 요구하고 actor, 전후 portal·판매 상태, salesAction, request HMAC/result와 시각을 남겨야 한다. 관계 종료 cleanup은 reason/key/HMAC/result를 null 처리하고 비PII action/state/time을 보존해야 한다.
- SUSPENDED portal 재활성화는 retained active manager, verified contact email과 time-valid VERIFIED contract를 요구하고 contract 재검증만으로 portal/sales/handed-over ownership을 복구하지 않아야 한다.
- 영구 portal DISABLED, 거래 INACTIVE, open Fulfillment/Claim/Refund 없음이 모두 성립할 때만 관계 종료 연락 PII cleanup deadline을 설정해야 한다. Scheduler는 deadline에 Supplier를 잠그고 같은 조건을 다시 확인하며, 새 open work가 있으면 deadline을 clear/defer하고 계속 적격일 때만 Supplier와 approved application의 중복 PII/replay material을 함께 정리해야 한다.
- CREATE_NEW 공급처는 `Supplier.status=INACTIVE`, portal contract `UNVERIFIED`로 시작해야 한다. B-098의 per-supplier contract evidence가 `VERIFIED`, `effectiveAt <= now`, expiry 없음 또는 `now < expiresAt`일 때만 sales-status ACTIVE를 허용하고, global flag가 열린 뒤에도 portal 상품 공개·checkout은 Supplier lock 아래 이 time-valid contract와 ACTIVE를 재검증해야 한다. Contract EXPIRED/REVOKED와 lazy expiry는 sales INACTIVE, ACTIVE portal SUSPENDED, open invite 폐기와 모든 열린 supplier-owned portal Fulfillment의 Coreable 인계를 함께 처리해야 한다. Paid-work 조회/변경과 Claim grant는 time-valid contract를 직접 재검사하며, 재검증은 portal/sales/ownership을 자동 복구하지 않아야 한다.
- 모든 공급처 API는 인증된 담당자의 활성 공급처를 서버에서 결정하고 다른 공급처 리소스를 `404`로 숨겨야 한다.
- production feature flag가 false이면 외부 신청·초대 교환/callback·supplier route를 `404`로 닫아야 한다. ADMIN/resource scope와 저장된 key/hash/result replay를 먼저 확인한 뒤, 초대를 만들거나 보내는 새 신청 승인·재발급·연락 이메일 후속 발급은 mutation 전에 `409 SUPPLIER_PORTAL_NOT_RELEASED`로 거절해야 한다. 동일 완료 command는 token-free 결과만 replay하고 재발송하지 않으며, 초대 dispatch도 발송 직전에 flag를 재검사해 닫혔으면 `SKIPPED`로 끝내고 다시 연 뒤 새 key 재발급을 요구해야 한다.
- cookie 인증 supplier의 `POST`/`PUT`/`PATCH`/`DELETE`는 allowlist `Origin` 또는 same-origin `Referer`를 요구하고 둘 다 없거나 불일치하면 `403`으로 거절해야 한다.

### Product

- 고객은 판매 중인 상품 목록을 볼 수 있어야 한다.
- 고객은 상품 상세 정보, 이미지, 가격, 옵션을 볼 수 있어야 한다.
- 운영자는 상품을 등록, 수정, 숨김, 판매중지 처리할 수 있어야 한다.
- 운영자는 상품 옵션을 등록, 수정, 품절, 판매중지 처리할 수 있어야 한다.
- 기존 수동/Domeggook 상품과 옵션은 실제 재고 수량 대신 판매 상태를 가진다.
- Domeggook sync의 성공·실패 기록은 fetch에 사용한 `sourceItemNo`가 `Supplier -> fresh Product -> Option` 잠금 뒤 현재 값과 정확히 같을 때만 적용되어야 한다. V40의 durable `sourceAutoSoldOut`은 기본값과 backfill이 `false`여야 하고 sync가 confirmed unavailable로 실제 `ACTIVE -> SOLD_OUT`을 적용할 때만 `true`여야 한다. Target/자동 복구는 marker가 `true`인 `SOLD_OUT`만 포함하고, 공급처 MOQ가 10 이하이며 현재 가격이 양수·상한 이하이고 compliance가 `REJECTED`가 아니며 active option, thumbnail, active notice를 모두 갖춘 경우에만 `ACTIVE`로 복구한 뒤 marker를 지워야 한다. 성공한 admin status 명령은 같은 `SOLD_OUT` 재지정을 포함해 marker를 지워 수동 품절을 보호해야 한다.
- 상품 가격 변경은 변경 이후 생성되는 새 주문부터 적용되어야 한다.
- 결제 완료 주문은 결제 당시 가격과 주문 상품 스냅샷을 유지해야 한다.
- 결제 완료 주문은 주문 시점의 상품 요약, 상품 상세 버전, 상품 정보 제공 고시 버전 참조를 유지해야 한다.
- 운영자는 상품 상세 콘텐츠를 이미지와 HTML 블록으로 등록할 수 있어야 한다.
- 기존 상품 상세 HTML은 관리자가 입력할 수 있고, 공급처 포털 상품은 해당 공급처 담당자도 입력할 수 있다. 모든 HTML은 저장 시 안전하게 sanitize되어야 한다.
- 배송, 교환, 환불, 주문 후 품절 가능성 고지는 상품 상세 콘텐츠와 별도 정책 영역으로 노출되어야 한다.
- 운영자는 상품 대표 이미지를 1장 등록할 수 있어야 한다.
- 운영자는 상품 갤러리 이미지를 최대 10장 등록할 수 있어야 한다.
- 운영자는 상세 블록 이미지를 최대 50장 등록할 수 있어야 한다.
- 상품 이미지는 `jpg`, `jpeg`, `png`, `webp`만 허용하고 이미지당 최대 10MB로 제한해야 한다. 파일명 확장자와 실제 이미지 파일 시그니처가 모두 맞아야 한다.

### Supplier Portal Product — Implemented (`B-101`)

- 공급처 포털의 첫 상품 기능은 개별 상품 등록이어야 한다.
- 공급처 화면은 편집 중 내부 draft와 무관하게 `상품 등록` 동작 하나로 최종 검증·분류를 끝내고 별도의 승인 요청 단계를 요구하지 않아야 한다.
- 공급처 담당자는 자기 공급처의 상품, 옵션, 이미지, 상세, 상품정보제공고시만 등록·수정할 수 있어야 한다.
- 공급처 상세 IMAGE 블록은 같은 상품에 서버가 업로드한 DETAIL ProductImage만 참조하고 임의 URL/storage key나 다른 상품 이미지를 참조할 수 없어야 한다. 참조 중인 이미지는 블록 제거 전 단독 삭제할 수 없어야 한다.
- 무옵션 상품도 내부적으로 이름이 `기본`인 옵션 하나를 생성해 기존 주문 항목의 필수 option 참조를 유지해야 한다.
- 공급처는 상품명, 요약, 공급가, 옵션 공급가, 공급처 옵션코드, MOQ/주문단위, 이미지, 상세, 상품정보제공고시를 입력할 수 있어야 한다.
- 공급처 요청에서 `supplierId`, 고객 판매가, 판매 상태, 검토 상태와 인증 판단 결과를 받아서는 안 된다.
- 고객 판매가는 공급가와 현재 Coreable active 가격 정책으로 서버가 계산해야 하며 공급처가 직접 지정할 수 없어야 한다.
- 공급가와 옵션 공급가는 각각 0원 이상 1억원 이하, 계산된 고객 단가는 10억원 이하로 제한하고 장바구니·checkout·OrderItem·PaymentGroup 금액은 overflow 없는 exact 연산과 양수 snapshot 제약으로 보호해야 한다. 비공개 전환 뒤 0원이 된 기존 장바구니 항목은 조회 가능하되 checkout은 거절해야 한다.
- Portal 상품을 기존 관리자 상품/옵션 API로 수정하는 경우에도 고객가 입력을 신뢰하지 않고 active pricing policy로 상품과 모든 옵션을 원자적으로 재계산해야 한다.
- 구조 검증과 판매 준비 조건을 통과하고 사람 판단이 필요하지 않은 일반 상품은 반드시 자동 승인·공개되어야 한다.
- 인증, 카테고리, 법정 필수정보 또는 안전 규칙이 사람 판단을 요구하는 상품만 숨김 상태로 Coreable 검토 큐에 들어가야 한다.
- `CERTIFICATION_REVIEW`에 대한 Coreable 승인은 portal 사람 검토만 통과시켜야 하고 `complianceStatus`를 자동 변경해서는 안 된다. 기존 `PENDING`은 판매를 차단하지 않으며 `REJECTED`만 판매 준비를 차단해야 한다.
- 자동 분류가 알 수 없거나 필수 근거가 누락되거나 실행에 실패하면 자동 공개하지 않고 검토 큐에 넣어야 한다.
- 안전·인증·카테고리·고시처럼 분류에 영향이 있는 수정은 재분류하고 다시 위험해진 상품은 공개를 멈춰야 한다.
- 공개 상품 조회와 checkout은 모든 상품의 Supplier 거래 상태가 활성인지 검증해야 한다. Portal 상품은 global feature gate, `AUTO_APPROVED|APPROVED`, time-valid VERIFIED 계약과 active option도 함께 검증해야 한다.
- Coreable은 자동 공개 이후에도 상품을 숨김·판매중지할 수 있고 공급처는 그 상태를 덮어쓸 수 없어야 한다.
- 공급처 상품 응답은 allowlist인 표시 상태, 안전한 검토 사유 코드·전달 문구와 다음 행동만 제공하고 관리자 내부 메모·담당자·분류 trace를 노출하지 않아야 한다. 보완은 같은 편집 화면과 `상품 등록` 동작으로 다시 제출할 수 있어야 한다.
- 상품, 옵션, 이미지, 상세, 고시, 가격과 검토 변경은 actor user, actor type, 전후 값, 사유와 시각을 기록해야 한다. 공급처 안내와 내부 사유는 각각 500자 이하 single-line PII-free 문구여야 하며, before/after는 allowlisted business field만 저장하고 raw request, 연락처, 고객·주문 정보를 복제하지 않아야 한다.
- B-101은 기존 상품과 portal 상품을 immutable Product management channel로 구분한다. B-102는 이를 OrderItem에 snapshot한다. Product aggregate version은 모든 supplier/admin/source writer가 증가시키고 supplier/admin review는 expected version으로 stale 승인을 거절해야 한다. Admin/review/cart/checkout/source writer는 scalar supplier/ownership discovery 뒤 `Supplier -> fresh Product -> 모든 ProductOption(id)` 순서로 잠그고, lock 대기 뒤 fresh owner가 discovery/request tenant와 다르면 conflict 또는 tenant-safe `404`로 거절해야 한다. 기존 admin client는 additive optional precondition을 거쳐 호환 이관해야 한다.
- 최초 submit 시각을 immutable하게 저장해야 한다. 공급처는 자기 `SUPPLIER_PORTAL` 상품이 아직 한 번도 submit되지 않은 `DRAFT`이고 상품·모든 옵션에 CartItem/OrderItem 참조가 없을 때만 상품을 실제 삭제할 수 있어야 한다.
- 옵션 실제 삭제도 최초 submit 전 DRAFT에서만 허용하고, 대상 옵션의 CartItem/OrderItem 참조가 없어야 하며 최소 한 옵션을 남겨야 한다. 한 번이라도 submit·검토·공개됐거나 참조된 상품과 옵션은 삭제 대신 Coreable의 숨김·판매중지 상태로 보존해야 한다.
- 삭제는 expected Product version, tenant/management-channel guard와 scalar ownership discovery 뒤 `Supplier -> fresh Product -> 모든 Option(id)` 잠금을 사용해야 한다. Cart 추가와 checkout 참조 생성도 같은 잠금 계약과 fresh ownership/saleability·참조 guard를 사용해 stale owner, FK 오류나 유실 참조를 노출하지 않아야 한다.
- 실제 삭제 뒤에도 immutable subject id, actor, 삭제 전 version과 allowlisted before snapshot 이력은 남아야 한다. Live Product/ProductOption 이력 FK는 nullable이어야 하며, 이미지 metadata 삭제와 unique durable binary-cleanup job enqueue는 원자적으로 commit되어야 한다. Cleanup job이 생긴 key는 admin 재첨부를 거절하고, 반복 enqueue는 멱등이어야 한다. Worker는 삭제 직전 live ProductImage 참조가 있으면 binary를 보존하고 `LIVE_REFERENCE`로 완료하며, 실제 metadata 제거 시 같은 job을 다시 열어야 한다.
- 최초 등록, 검토, 보완, 재제출과 거절의 허용 상태 전이를 명시해야 한다. 보완 재제출은 자동 공개하지 않고 다시 검토로 보내며 supplier-safe message와 내부 reason을 분리해야 한다.
- B-100~B-103이 배포된 동안에도 production supplier portal feature gate를 닫아 고객 판매를 시작하지 않아야 한다. B-102 inventory guard와 B-103 fulfillment/privacy 기반은 필요조건이며 B-104~B-105, 개인정보·실 email·계약 gate가 모두 준비된 뒤에만 실제 공개·외부 route를 열어야 한다.

### Cart

- 고객은 상품 옵션을 장바구니에 담을 수 있어야 한다.
- 고객은 장바구니 수량을 변경하거나 항목을 삭제할 수 있어야 한다.
- 장바구니 항목은 주문 시점의 상품 상태를 다시 검증해야 한다.
- 장바구니는 서로 다른 배송 그룹의 상품을 담을 수 있어야 한다.
- 결제 시 장바구니 항목은 배송 그룹별 주문으로 분리되어야 한다.
- 고객은 여러 배송 그룹이 포함된 장바구니를 한 번에 결제할 수 있어야 한다.

### Order

- 고객은 장바구니 또는 상품 상세에서 주문서를 생성할 수 있어야 한다.
- 주문에는 주문자 정보, 배송지, 배송 메모, 주문 상품, 결제 금액이 주문 시점 snapshot으로 저장되어야 한다.
- 주문 상품에는 주문 시점의 상품명, 옵션명, 단가, 수량, 금액, 상품 요약, 상품 상세 버전, 상품 정보 제공 고시 버전 참조가 스냅샷으로 저장되어야 한다.
- MVP에서는 고객에게 별도 배송비를 청구하지 않고 배송비는 `0원`으로 표시되어야 한다.
- 고객에게 표시하는 배송비는 `0원`이며 공급처 배송비는 운영비로 처리해야 한다.
- 상품 판매가는 공급처 상품가에 active 가격 정책을 적용해 계산하고 공급처 배송비를 가격 계산에 더하지 않아야 한다.
- 한 주문은 하나의 배송 그룹만 포함해야 한다.
- 하나의 결제 그룹(PaymentGroup)은 여러 배송 그룹 주문을 포함할 수 있어야 한다.
- 고객 화면에는 공급처 대신 배송 그룹으로 표시해야 한다.
- 주문 생성 후 입금대기 상태로 진입해야 한다.
- 입금대기 주문은 기본 24시간 입금 기한을 가져야 한다.
- 기존 `UNTRACKED` 주문은 입금 기한이 지나면 관리자 미입금 취소로 종료할 수 있어야 한다.
- 관리자 입금확인이 완료되면 주문은 공급처 발주 대기 상태로 진입해야 한다.
- 고객은 주문서 정책 확인 전까지만 배송지를 직접 변경할 수 있어야 한다.
- 정책 확인 후 배송지 변경은 고객 문의를 통해 공급처 발주 전에만 운영 처리한다.
- 관리자가 공급처 발주 작업을 시작해 배송지가 잠긴 주문은 배송지 변경이 거절되어야 한다.
- 고객 주문 내역과 주문 상세는 내부 주문 상태가 아니라 고객용 표시 상태를 보여줘야 한다.
- 고객 주문 내역에는 관리자 입금확인 후 확정된 주문만 노출되어야 한다.
- 입금대기, 결제 만료, 결제 실패, 미입금 취소 상태는 일반 고객 주문 내역이 아니라 체크아웃 화면 또는 고객 문의에서 다뤄져야 한다.
- 관리자는 주문 목록과 주문 상세를 확인할 수 있어야 한다.
- 주문서에는 주문 상품, 결제 금액, 배송지, 배송/취소/환불 정책, 결제 후 품절 가능성, 품절 시 해당 배송 그룹 주문 금액 환불 안내를 확인하는 통합 체크박스가 있어야 한다.
- 주문서 통합 확인 체크박스를 선택하지 않으면 결제 요청을 진행할 수 없어야 한다.
- 결제 그룹(PaymentGroup) 시점에 고객이 확인한 정책 버전과 확인 시각을 저장해야 한다.

### Payment

- 현재 고객 결제 경로는 계좌입금이어야 한다.
- 하나의 결제 그룹(PaymentGroup)은 여러 배송 그룹 주문을 포함할 수 있어야 한다.
- 계좌입금 안내에는 입금 계좌, 예금주, 결제 그룹 총액, 입금자명, 입금 기한, 현금영수증 안내가 포함되어야 한다.
- 관리자는 실제 입금 내역과 서버 결제 그룹 총액을 확인한 뒤에만 입금확정할 수 있어야 한다.
- 입금확정은 `BANK_TRANSFER` 결제를 생성하고 결제 그룹과 포함 주문을 한 번만 승인해야 한다.
- B-068의 과거 입금 불일치 메모는 읽기 호환을 위해 보존한다. 현재는 식별된 양수 실제 입금액이 결제그룹 총액과 다르면 메모-only 입금대기로 두지 않고 B-102의 결제그룹 전액 환불 예외로 처리해야 한다. 어느 PaymentGroup인지 식별하지 못한 은행 거래는 주문을 추측해 변경하지 않는다.
- 미입금 주문은 관리자 취소 사유와 함께 종료할 수 있어야 한다.
- 중복 입금확인과 중복 checkout은 중복 결제 또는 중복 주문을 만들지 않아야 한다.
- 입금대기와 미입금 취소 주문은 일반 고객 주문 내역에 노출하지 않고 checkout 상태에서 안내해야 한다.
- 카드, 간편결제, PG 계좌이체·가상계좌는 제공하지 않아야 한다.

### Portal Inventory And Checkout Reservation — Implemented (`B-102`)

- 기존 COREABLE 상품 옵션은 migration에서 `UNTRACKED`로 유지하고, B-101에서 B-102 전에 생성된 portal option은 `TRACKED/onHand=0`으로 안전하게 이관해야 한다. 이후 신규 공급처 포털 옵션은 `TRACKED`를 기본으로 하되 공급처가 명시적으로 `UNTRACKED`를 선택할 수 있어야 한다.
- Portal/legacy 여부와 무관하게 식별된 양수 실입금액이 PaymentGroup 총액과 다르면 `PAYMENT_AMOUNT_MISMATCH`를 다른 판매불가·기한·재고 reason보다 먼저 선택해야 한다. 실제 입금자·금액·시각·거래 식별값과 관리자 사유를 exactly once 보존하고 Payment/PaymentGroup을 `PAYMENT_EXCEPTION`으로 기록해야 한다.
- 금액 불일치 명령은 `PAYMENT_PENDING`, `EXPIRED`와 미입금 취소만 완료된 `CANCELLED` 결제그룹을 받아야 한다. `CANCELLED` 그룹은 수령 Payment/Refund/Fulfillment가 없고 모든 포함 Order가 미입금 취소 결과인 경우에만 허용하며, 취소 뒤 발견된 실입금도 같은 전액 환불 경로로 처리해야 한다.
- 금액 불일치는 실제 수령액을 배송 그룹별로 배분하지 않고 `refundScope=PAYMENT_GROUP`, `orderId=null`, `reason=PAYMENT_AMOUNT_MISMATCH`, `refundAmount=actualDepositAmount`인 Refund를 결제그룹당 정확히 하나만 만들어야 한다. 모든 포함 Order는 `REFUND_REQUESTED`가 되고 남은 HELD 예약은 재확보·소비 없이 한 번만 해제되어야 한다.
- 금액 불일치 예외는 Fulfillment, 주소 잠금, 공급처 PII·알림·조회 결과를 만들지 않고 정상 주문 재개를 허용하지 않아야 한다. Coreable의 승인과 실제 계좌이체 증적 완료 후 Refund, Payment, PaymentGroup과 모든 포함 Order를 `REFUNDED`로 끝내며, 고객이 구매를 계속하려면 새 checkout을 만들어야 한다.
- 미입금 취소만 완료된 qualifying `CANCELLED` 결제그룹에서 정확한 금액이 뒤늦게 발견되면 portal/legacy 여부와 입금시각에 관계없이 주문을 재개하지 않아야 한다. 실제 입금 증적과 Payment/PaymentGroup `PAYMENT_EXCEPTION`, immutable 배송 그룹 금액의 Order별 `LATE_DEPOSIT_EXCEPTION` Refund를 exactly once 만들고, 그 합계가 실제 수령한 결제그룹 총액과 같아야 한다. 전액 환불 뒤 새 checkout만 허용해야 한다.
- `TRACKED` 옵션은 on-hand, reserved와 `available = on-hand - reserved`를 관리하고, `UNTRACKED` 옵션은 현재 판매 상태 기반 구매 가능 규칙을 유지해야 한다.
- 공급처는 Coreable 판매 상태와 별도인 `주문 받기`/`주문 중지` availability로 자기 옵션의 신규 checkout을 중지할 수 있어야 한다. 주문 받기는 Coreable 중지·숨김·안전 상태를 덮어쓸 수 없어야 한다.
- 고객에게 재고 모드나 `무제한` 문구를 노출하지 않고 구매 가능 또는 품절만 표시해야 한다.
- 공급처 UI는 `수량 관리 (권장)`을 기본으로 하고 0 이상의 on-hand를 필수로 받아 예약 동작을 안내해야 한다. `재고 수량 관리 안 함`을 선택하면 on-hand 입력을 숨기고 `주문 받기`/`주문 중지`로 구매 가능 여부를 통제한다는 도움말을 보여야 한다.
- Checkout 생성은 모든 영향 Supplier, Product, 모든 ProductOption을 각 id 순서로 잠그고 UNTRACKED-only 그룹도 생략하지 않아야 한다. 잠금 아래 Supplier ACTIVE, 상품·옵션·compliance·supplier availability를 다시 검사하고 portal-origin item의 Supplier에만 time-valid contract를 추가 검증한다. TRACKED 수량을 한 트랜잭션에서 예약하며 하나라도 실패하면 전체 checkout을 롤백해야 한다.
- 예약은 기본 24시간 유지되고 미입금 만료 또는 취소 시 정확히 한 번 해제되어야 한다.
- 정상·늦은 portal 입금은 `PaymentGroup -> Supplier(id) -> Product(id) -> 모든 ProductOption(id) -> Order/Fulfillment(id)` 순서로 잠그고 lifecycle/catalog/inventory writer와 직렬화해야 한다. 기한 안에 실제 입금이 확인되면 현재 판매·계약·안전 guard와 immutable inventory-mode snapshot을 재검증한 뒤 예약 수량을 소비해야 한다. `EXPIRED` 뒤 발견된 입금은 실제 입금시각이 기한 안이고 동일 guard와 모든 `TRACKED` 수량의 원자적 재확보가 성공한 경우에만 승인해야 한다. 미입금 `CANCELLED` 뒤 발견된 정확한 입금은 이 재개 경로에 들어가지 않아야 한다.
- portal snapshot 항목을 포함한 PaymentGroup의 실제 입금이 확인됐지만 Supplier/product/option/compliance/supplier availability가 실패하면 whole-group `SALE_UNAVAILABLE_AT_DEPOSIT` 예외로 보내고 공급처에 노출하지 않아야 한다. legacy-only PaymentGroup은 기존 validation 동작을 유지해야 한다.
- 금액이 정확한 qualifying 미입금 `CANCELLED`는 saleability·재고 재확보보다 먼저 terminal `LATE_DEPOSIT_EXCEPTION`으로 보내야 한다. 그 외 normal/expired portal 경로에서 현재 saleability 또는 immutable/current inventory mode가 불일치하면 `SALE_UNAVAILABLE_AT_DEPOSIT`으로 보내고, saleability가 통과했지만 늦은 입금 재확보가 실패하거나 실제 입금시각이 기한 이후이면 `LATE_DEPOSIT_EXCEPTION`으로 보내야 한다. scheduler가 아직 PAYMENT_PENDING을 만료하지 않았더라도 actual deposit timestamp가 늦으면 HELD를 한 번 해제하고 정상 주문으로 확정하지 않아야 한다.
- 늦은 입금 예외도 실제 수령한 `BANK_TRANSFER` Payment와 관리자 입금 증적을 exactly once 저장하되 공급처에는 노출하지 않아야 한다.
- 정확한 금액의 portal 예외 명령은 판매불가면 `SALE_UNAVAILABLE_AT_DEPOSIT`, 늦은 시각/재고실패면 `LATE_DEPOSIT_EXCEPTION` 사유로 배송 그룹마다 Refund를 하나만 자동 생성하고 Order를 `REFUND_REQUESTED`로 보내야 한다. 정상 주문으로 재개하지 않고 실제 계좌환불 후 `REFUNDED`로 끝내야 한다.
- 고객 checkout 결과와 주문 내역은 raw 예외나 입금 증적 대신 `입금 확인 및 환불 처리 중`을 표시하고 공급처에는 예외와 환불 존재를 모두 숨겨야 한다.
- Checkout은 Supplier -> Product -> 모든 ProductOption, 만료·입금은 PaymentGroup -> Supplier -> Product -> 모든 ProductOption -> Order/Fulfillment의 공통 순서를 사용해야 한다. 상품·재고 writer도 Product -> Option을 따르고 Product 뒤 Supplier 역순 잠금을 금지해 중복 예약·소비·해제와 stale saleability commit을 막아야 한다.

### Existing Supplier Fulfillment

- 관리자는 결제 완료 주문을 공급처 발주 대기 목록에서 볼 수 있어야 한다.
- 관리자는 결제 확정 후 영업일 기준 당일 또는 다음 영업일 안에 공급처 발주 작업을 시작할 수 있어야 한다.
- 관리자는 공급처 발주 작업 시작 시 배송지를 잠글 수 있어야 한다.
- 관리자는 공급처에 수동 발주한 뒤 공급처 발주 완료로 상태를 변경할 수 있어야 한다.
- 도매꾹 source snapshot이 완전한 주문은 입금확인 후 Private API 자동 발주 대상으로 전환되어야 한다.
- 자동 발주 직전 상품, 옵션, 수량, 공급가, 배송비와 e-money 잔액을 다시 검증해야 한다.
- 자동 발주의 timeout 또는 응답 유실은 최근 구매 주문을 대조하기 전까지 재시도하지 않아야 한다.
- 도매꾹 source snapshot이 없거나 자동 발주 대상이 아닌 주문은 기존 수동 발주 흐름을 유지해야 한다.
- 공급처 발주 완료 기록에는 공급처 주문번호, 발주 주소 스냅샷, 발주 관리자, 예상 출고일, 공급처 응답 메모가 포함되어야 한다.
- 공급처 발주 후 1영업일 안에 공급처 응답 또는 출고 예정일 확보가 필요하다.
- 공급처 발주 후 2영업일 이상 출고 예정이 불명확하면 고객 지연 안내 대상으로 관리되어야 한다.
- 공급처 품절이 발생하면 관리자는 주문을 품절 상태로 변경할 수 있어야 한다.
- 품절 상태의 주문은 고객 안내와 환불 처리 대상으로 관리되어야 한다.

### Supplier Portal Fulfillment — Implemented (`B-103`)

- 공급처 포털 주문은 관리자 입금확인이 성공하는 즉시 별도 수락·거절 단계 없이 해당 공급처의 출고 요청으로 노출되어야 한다.
- 입금확인 트랜잭션은 `TRACKED` 예약 소비, 주문 전환, 출고 요청 생성과 배송지 잠금을 함께 처리해야 한다.
- 배송 메모는 선택값, 최대 300자로 받고 trim한 결과가 비면 `null` 주문 snapshot으로 저장해야 한다.
- 공급처에 노출된 주문은 고객 셀프서비스 취소와 주소 변경을 허용하지 않고 Coreable 클레임 검토로 진행해야 한다.
- 공급처 주문 목록에는 주문번호, 처리 상태, 자기 상품 요약, 수량과 요청시각만 제공하고 고객 개인정보를 포함하지 않아야 한다.
- 공급처 처리 상태는 raw `SUPPLIER_ORDER_PENDING` 대신 `FULFILLMENT_REQUESTED` 같은 전용 표시값을 사용해 수락 대기 단계처럼 보이지 않아야 한다.
- 공급처 주문 상세에는 자기 공급처 배송에 필요한 수령인 이름·전화, 우편번호·주소, 배송 메모만 제공하고 고객 계정, 결제, 입금, 환불과 다른 공급처 정보를 제공하지 않아야 한다.
- 공급처의 전체 개인정보 접근 종료시각은 출고 요청 +60일로 저장해 시작해야 한다. cutoff scheduler/lazy read와 `OUT_OF_STOCK`, `CANCELLED`, `REFUND_REQUESTED`, `REFUNDED` terminal 상태는 즉시 Coreable 인계·마스킹한다. 송장마다 `min(현재 저장 cutoff, 해당 registeredAt+30일)`로 단축하고 void·교체·추가 송장이 늘리지 않게 하는 실제 Shipment writer는 Planned `B-104`가 맡는다.
- cutoff 시각부터(`now >= cutoff`) 한 글자 이름을 `*`, 두 글자 이상 이름을 첫 Unicode code point와 고정 `**`로 반환해야 한다. 전화번호는 숫자로 정규화해 4자리 이하면 전부 가리고, 5자리 이상이면 마지막 4자리만 남겨야 한다. 우편번호, 주소와 배송 메모는 `null`로 반환하고 응답에 `piiAccessLevel=MASKED`와 `Cache-Control: no-store`를 적용해야 한다.
- 공급처의 주문 상세 접근은 actor, 주문, 접근 근거와 시각만 기록하고 실제 개인정보 값이나 응답 본문을 로그에 복제하지 않아야 한다.
- Claim PII grant/extension은 각각 요청시각부터 최대 30일로 제한하고 append-only로 기록해야 한다. Grant/extension은 `RETURN_COORDINATION_REQUIRED|EXCHANGE_COORDINATION_REQUIRED|REFUND_COORDINATION_REQUIRED`, revoke는 `CLAIM_ACCESS_NO_LONGER_REQUIRED` reason code만 허용하고 자유문을 저장하지 않아야 한다. Claim이 `APPROVED`, `RETURN_WAITING`, `RETURN_RECEIVED`, `REFUND_PROCESSING`, `EXCHANGE_SHIPPING` 중 하나이고 deadline 전이며 이후 revoke가 없고 Supplier contract가 time-valid VERIFIED일 때만 전체 접근을 한시 재개해야 한다. 다른/terminal Claim 상태나 contract expiry/revoke는 즉시 무효화해야 한다.
- supplier paid-work list/detail와 출고 mutation은 time-valid VERIFIED contract, SUPPLIER_PORTAL+owner SUPPLIER를 요구해야 한다. Detail은 ACTIVE portal/current manager도 요구한다. COREABLE로 인계된 portal 주문은 cutoff/terminal 사유일 때 원래 supplier에게 MASKED로만 남고 active allowed-status Claim grant와 time-valid contract가 함께 있을 때만 read-only FULL로 재개되어야 한다. Contract expiry/revoke와 다른 인계 사유는 과거 grant와 무관하게 order detail을 닫아야 하며, 어떤 조회 예외도 송장·품절 mutation을 허용해서는 안 된다.
- 신규 출고 요청과 관리자 상품 검토 결과 알림은 time-valid VERIFIED contract와 active portal/manager를 가진 검증된 공급처 연락 이메일로만 보내고 고객 개인정보를 제목, 본문 또는 payload snapshot에 넣지 않아야 한다. 승인 클레임 작업 알림 type/template은 B-103에 포함하되 실제 claim-task 생성 producer는 Planned `B-105`가 연결해야 한다.
- 공급처 운영 email은 생성+7일까지만 retry하고 raw provider exception 대신 allowlisted/redacted failure code만 저장해야 한다. terminal+30일에 recipient와 legacy/free-text failure reason을 null 처리해야 한다. B-100은 기존 `notification_logs.recipient NOT NULL`을 nullable로 expand하고 entity/reader/writer 호환을 이관해야 한다.
- 기존 generic notification retry는 invite-linked row를 항상 거절하고, supplier operational row는 FAILED·recipient 존재·생성+7일 전·현재 lifecycle/time-valid contract/email 일치일 때만 허용해야 한다. SKIPPED/SENT/recipient-null/기한 종료 row는 terminal이어야 한다.
- invite 소비자와 catalog/inventory/lifecycle의 supplier actor FK는 B-098 관계 종료 보관기한 뒤 null 처리하고 비PII 행위 증적만 남겨야 한다. Shipment/shortage/claim의 supplier actor FK는 parent Order/Claim 법정 보존기한까지만 유지한 뒤 null 처리하거나 parent와 함께 파기해야 한다. PII access log는 1년 뒤 삭제해야 한다.
- 기존 Coreable 수동 발주와 Domeggook 자동 발주 흐름은 현재 동작과 API를 유지해야 한다.

### Existing Shipment

- 도매꾹 구매 주문에서 확인된 택배사와 송장번호는 기존 배송 정보로 동기화하고, 그 외 주문은 관리자가 직접 입력할 수 있어야 한다.
- 기존 Coreable 수동/Domeggook 배송은 주문 1개당 배송 1개로 처리되어야 한다.
- 기존 Coreable 수동/Domeggook 주문은 부분 출고와 분할 배송을 지원하지 않아야 한다.
- 기존 주문은 송장번호 입력 시 주문이 배송중 상태로 변경되어야 한다.
- 도매꾹 자동 발주 주문은 구매 주문 조회에서 택배사, 송장번호와 배송 상태를 동기화할 수 있어야 한다.
- 공급처 주문 조회에서 배송 완료가 확인되면 주문은 배송 완료 상태로 변경될 수 있어야 한다.
- 공급처 주문 동기화는 배송 상태를 앞으로만 진행시키고 관리자 수동 보정 상태를 임의로 되돌리거나 덮어쓰지 않아야 한다.
- 배송조회 실패 또는 송장번호 오류 시 관리자는 배송 상태를 수동 보정할 수 있어야 한다.
- 배송 상태 수동 보정은 사유와 이력을 기록해야 한다.
- 도매꾹 외 공급처 주문은 관리자가 택배사와 송장번호를 직접 입력할 수 있어야 한다.
- 고객은 주문 내역에서 배송 정보를 볼 수 있어야 한다.

### Supplier Portal Shipment — Planned (`B-104`)

- 공급처 포털 주문은 주문 1개에 여러 Shipment를 허용하고 각 Shipment에 주문 항목별 양수 수량을 할당할 수 있어야 한다.
- 첫 송장에서 allocation을 생략하면 아직 미할당된 전 수량을 기본 배정하고, 추가 송장은 명시적 allocation을 요구해야 한다.
- 같은 주문 항목에 대한 모든 Shipment 누적 할당량은 주문수량을 넘을 수 없으며 동시 등록은 주문과 항목 잠금으로 보호해야 한다.
- 공급처는 택배사 코드와 송장번호만 등록할 수 있고 실제 집하, 배송중, 배송완료 상태를 직접 입력할 수 없어야 한다.
- 서버는 택배사 코드와 송장번호로 공식 택배사 조회 링크를 생성하고 임의 URL을 저장하지 않아야 한다.
- 공급처 송장 등록은 실제 출고와 분리된 `TRACKING_REGISTERED`로 표현해야 한다.
- 실시간 택배사 상태 API는 공급처 포털 초기 범위에 포함하지 않고 고객은 공식 조회 링크를 사용하며 Coreable은 기존 수동 배송완료 보정을 유지해야 한다.
- 공급처는 배송완료 전 자기 송장의 택배사·번호만 version과 사유로 정정할 수 있어야 한다. allocation 오류는 Coreable이 Shipment를 `VOIDED`한 뒤 다시 등록하게 해 과거 할당 행을 수정·삭제하지 않아야 한다.
- Coreable은 배송완료 전 오등록 송장을 `VOIDED` 처리하고, 확인시각·사유를 증적으로 유효 Shipment를 배송완료 처리할 수 있어야 한다. 마지막 유효 송장 void는 주문을 출고 요청 상태로 되돌리고 그 밖에는 유효 송장 집계로 상태를 재계산해야 한다.
- 배송완료/시각정정은 `registeredAt <= deliveredAt <= evidenceObservedAt <= now` 순서를 만족해야 한다. Coreable은 portal 수동 배송완료 오입력을 후속 Claim/Refund 생성 전까지만 사유·idempotency·version guard로 `REOPEN_TRACKING`하거나 완료시각을 정정할 수 있어야 한다. 원래 증적은 이력에 보존하고 후속 처리가 있으면 `409`로 거절하며 고객에게 보이는 후퇴 정정을 알림으로 남겨야 한다.
- 모든 주문수량이 void되지 않은 Shipment에 할당되고 각 Shipment가 Coreable 배송완료 증적을 가진 경우에만 주문을 `DELIVERED`로 전환할 수 있어야 한다.
- 고객 주문 응답은 유효 Shipment별 택배사 코드·이름, 송장번호, 서버 생성 공식 조회 URL과 표시 상태를 제공해야 한다. `TRACKING_REGISTERED`는 `송장 등록 · 배송조회 가능`으로 표시하고 실제 집하·배송중으로 안내해서는 안 된다.
- Coreable로 인계된 SUPPLIER_PORTAL 주문은 같은 plural/allocation 규칙의 관리자 shipment 생성 경로로 처리할 수 있어야 한다. legacy 발주·단일 shipment·tracking-sync/manual-correction은 portal channel을 거절해야 한다.
- unique 제거 전 singular repository caller를 plural aggregate로 바꾸고 carrier code/name dual-write와 결정적 legacy backfill을 해야 한다. 기존 단일 Shipment 응답은 row가 있으면 가장 이른 non-voided row와 truncation 표시를 사용하고, row가 없으면 customer의 non-null READY placeholder와 admin의 null인 현재 shape를 각각 유지하며 plural을 canonical로 제공해야 한다.
- 복수 송장 portal 주문의 고객 Claim 기간은 legacy singular projection이 아니라 `max(non-voided deliveredAt)`인 주문 aggregate 배송완료시각을 기준으로 계산해야 한다.

### Cancellation And Refund

- 고객은 출고 전 주문 취소를 요청할 수 있어야 한다.
- 기존 `COREABLE_MANUAL`/`DOMEGGOOK_API` 주문에서 고객은 공급처 발주 전 상태인 `SUPPLIER_ORDER_PENDING`까지만 직접 주문을 취소할 수 있어야 한다.
- 기존 주문의 고객 직접 취소는 `SUPPLIER_ORDER_PENDING` 상태이면서 공급처 발주 작업과 주소 잠금이 시작되지 않은 경우에만 허용되어야 한다. `SUPPLIER_PORTAL` 주문은 입금확인과 동시에 주소가 잠기므로 고객 직접 취소를 허용하지 않아야 한다.
- 공급처 발주 작업 시작 후 고객 직접 취소는 거절되어야 한다.
- 공급처 발주 작업 시작 후 배송 전 취소는 취소 클레임 접수와 관리자 수동 심사로 진행되어야 한다.
- 배송 후 반품과 교환은 클레임 접수와 관리자 수동 심사로 진행되어야 한다.
- 관리자는 취소/환불/반품/교환 클레임을 수동으로 처리할 수 있어야 한다.
- 단순 변심 반품/교환 클레임은 배송 완료일로부터 7일 이내 접수된 건만 심사되어야 한다.
- 상품 하자, 오배송, 상품 정보와 다름, 배송 문제 클레임은 배송 완료일로부터 3개월 이내이면서 고객이 그 사실을 안 날 또는 알 수 있었던 날부터 30일 이내 접수된 건만 심사되어야 한다.
- 클레임 사유는 단순 변심, 상품 하자, 오배송, 상품 정보와 다름, 배송 문제로 시작해야 한다.
- 상품 하자, 오배송, 상품 정보와 다름, 배송 문제 클레임은 사진 증빙을 필수로 받아야 한다.
- 단순 변심 반품/교환 배송비는 고객 부담을 기본으로 해야 한다.
- 판매자 또는 배송 귀책 반품/교환 배송비는 운영자 부담을 기본으로 해야 한다.
- 클레임 처리 상태는 환불 상태와 분리되어야 한다.
- 반품 상품 입고가 필요한 계좌입금 환불은 입고 확인일로부터 3영업일 이내 실제 계좌환불을 완료하는 것을 목표로 해야 한다.
- 반환받을 상품이 없는 계좌입금 취소 환불은 취소 승인일로부터 3영업일 이내 실제 계좌환불을 완료하는 것을 목표로 해야 한다.
- 공급처 품절 주문은 환불 처리 대상으로 전환되어야 한다.
- 환불 완료 후 주문은 환불 완료 상태가 되어야 한다.
- 계좌입금 주문은 관리자가 실제 환불 이체 완료를 기록한 뒤에만 환불 완료 상태가 될 수 있어야 한다.
- `PAYMENT_AMOUNT_MISMATCH` 환불액은 주문 합계가 아니라 실제 수령액이어야 하며, 결제그룹 전체 환불 완료는 모든 포함 Order를 같은 트랜잭션에서 `REFUNDED`로 끝내야 한다.
- 관리자 환불 DTO와 고객 주문별 환불 요약은 nullable 단일 `orderId`를 전제하지 않고 `PAYMENT_GROUP` Refund를 모든 포함 Order에 투영할 수 있어야 한다. 기존 배송그룹 Refund 응답은 호환해야 한다.
- 환불 실행 실패 시 주문은 환불 완료 상태가 아니라 환불 실패, 재시도 필요, 또는 수동 확인 필요 상태로 남아야 한다.
- 환불 실패 건은 관리자 재시도 또는 수동 확인 큐에서 처리할 수 있어야 한다.
- 환불 기록에는 처리 관리자, 완료 시각, 사유와 환불 메모를 저장해야 한다.
- MVP에서는 배송 그룹 주문 단위 부분 취소/부분 환불을 지원해야 한다.
- 배송 그룹 주문 내부의 상품, 옵션, 수량 단위 부분 취소/부분 환불은 MVP에서 지원하지 않아야 한다.
- 특정 배송 그룹 주문이 공급처 품절이면 해당 배송 그룹 주문 금액만 부분 취소/환불해야 한다.
- 배송 그룹 주문 내부에서 일부 상품 또는 일부 수량만 품절이면 MVP에서는 해당 배송 그룹 주문 전체를 취소/환불해야 한다.

### Supplier Portal Shortage And Claim Facts — Planned (`B-105`)

- 공급처는 VOIDED 포함 Shipment가 한 번도 등록되지 않은 자기 출고 요청에만 품절을 보고할 수 있어야 한다.
- 품절 제출은 REPORTED를 만들고 Fulfillment만 Coreable에 인계하며 Order/Refund를 바꾸지 않아야 한다. Submit key/hash와 immutable supplier-safe result를 저장해 동일 submit만 최초 결과를 replay하고 같은 order의 새 key는 충돌해야 한다. Coreable review는 allowlisted code만 받고 free text를 저장하지 않으며, 승인 때만 배송 그룹 주문 전체에 기존 `OUT_OF_STOCK`/환불 서비스를 실행하고 거절하면 Refund 없이 Coreable owner를 유지해야 한다. 상품·옵션·수량 일부만 승인·환불하지 않아야 한다.
- 공급처는 환불을 승인, 거절 또는 완료할 수 없고 Coreable만 고객 안내, 클레임 결정과 실제 계좌환불을 수행해야 한다.
- 공급처는 Coreable이 만든 자기 공급처의 열린 claim task가 있을 때만 출고 중단 결과, 반품 안내, 반품 입고, 검수 결과 같은 요청 유형과 일치하는 구조화 사실을 idempotent·append-only로 기록할 수 있어야 한다. task detail은 자기 safe fact id/type/payload/correction reference/time을 보여줘 기존 사실을 바꾸지 않고 정정 row를 추가할 수 있어야 한다.
- Coreable task 생성과 supplier fact는 각각 idempotency key/request hash와 immutable actor-safe canonical result를 저장해 status/close/deadline 뒤에도 최초 응답을 replay해야 한다. 관리자는 task list/detail에서 Claim/order linkage, 내부 context와 전체 same-task fact history를 읽을 수 있어야 한다.
- 공급처 사실 입력 자체는 Claim, Order 또는 Refund 상태를 변경하지 않아야 한다.

### Admin Operations

- MVP 관리자 권한은 `ADMIN` 단일 역할로 시작해야 한다.
- 관리자 계정은 DB seed 또는 수동 등록으로만 부여되어야 한다.
- 관리자는 주문 상태를 임의 값으로 변경할 수 없어야 한다.
- 주문 상태 전이는 fromStatus, actor, action, guard, sideEffect, toStatus 기준 전이표로 검증되어야 한다.
- 관리자는 현재 주문 상태에서 허용된 다음 액션이 확보된 경우에만 주문을 진행시킬 수 있어야 한다.
- 관리자 주문 처리는 공급처 발주 완료, 공급처 품절, 송장번호 입력, 배송 수동 보정, 취소/환불 승인, 취소/환불 거절 같은 정해진 액션으로 제공되어야 한다.
- 관리자 주문 처리는 공급처 발주 작업 시작 액션을 포함해야 한다.
- 관리자 주문 처리는 클레임 승인, 클레임 거절, 증빙 요청, 반품 입고 확인, 교환 발송 처리 액션을 포함해야 한다.
- 자동 상태 되돌리기 버튼은 MVP에서 제공하지 않아야 한다.
- 잘못된 상태 변경은 관리자 정정 액션으로 처리하고 정정 사유를 기록해야 한다.
- 주문 상태 변경 이력은 MVP부터 기록되어야 한다.
- 주문 상태 변경 이력에는 action, guard result, side effect summary가 포함되어야 한다.
- 입금대기, 입금확인 완료, 품절, 배송 시작, 배송 완료, 지연 안내, 클레임 상태 변경, 환불 완료 알림은 `NotificationLog`로 기록되어야 한다.
- `PREPARING_SHIPMENT`은 MVP 주문 상태에서 사용하지 않아야 하며, 공급처 발주 완료 후 송장 입력 전 구간은 `SUPPLIER_ORDERED`로 표현해야 한다.
- 실제 계좌환불 완료 없는 환불 완료, 송장 없는 배송중, 배송 기록 없는 배송완료, 배송 후 품절 전이는 금지되어야 한다.
- 취소, 환불, 품절, 배송 수동 보정, 관리자 정정 액션은 사유 입력이 필수여야 한다.
- 상품 가격, 상품 판매 상태, 상품 옵션 판매 상태, 상품 공급처 변경은 MVP부터 변경 이력을 기록해야 한다.
- Implemented B-100~B-103 공급처 포털의 신청 승인, 초대 재발급·폐기, 상품 검토, 재고, 출고 요청·PII 접근은 공급처 tenant와 actor를 포함해 감사 기록을 남겨야 한다. Planned B-104 송장과 B-105 공급처 사실 변경도 같은 경계를 유지해야 한다.
- 공급처는 고객 결제, 환불, 클레임 승인·거절과 관리자 수동 보정을 수행할 수 없어야 한다.

### Legal And Customer Notice

- 이용약관, 개인정보처리방침, 배송 정책, 취소/환불 정책 페이지를 제공해야 한다.
- 정책 페이지는 고객 메뉴와 푸터에서 접근 가능해야 한다.
- 정책 페이지는 버전과 시행일을 가져야 한다.
- 푸터와 고객센터/회사 정보 페이지에는 상호, 대표자명, 사업자등록번호, 통신판매업 신고번호 또는 신고 면제 상태, 사업장 주소, 고객센터 전화번호, 고객센터 이메일, 개인정보 보호책임자, 호스팅 제공자를 표시해야 한다.
- 상품 상세에는 품목별 상품 정보 제공 고시, 배송/AS/반품/교환 정보를 표시해야 한다.
- 개인정보처리방침에는 처리 목적, 수집 항목, 보유 기간, 제3자 제공, 처리 위탁, 파기 절차, 정보주체 권리 행사 방법, 개인정보 보호책임자를 표시해야 한다.
- 개인정보 처리표는 수집 항목, 처리 목적, 보유 기간, 처리 위탁처, 제3자 제공 여부를 관리해야 한다.
- 주문, 배송, 결제, 환불, 클레임 처리 알림은 거래 알림으로 처리하고 선택 마케팅 수신 동의와 분리되어야 한다.
- 마케팅 알림은 채널별 선택 동의를 받은 고객에게만 발송되어야 한다.
- 상품 상세와 주문서 모두에서 결제 후 공급처 품절 가능성과 품절 시 해당 배송 그룹 주문 금액 환불 정책을 고지해야 한다.
- 고객 거래 알림은 SMS와 주문 상세 상태 표시로 시작하고, SMS 자격증명이 없으면 발송 로그를 `SKIPPED`로 남겨야 한다.
- 카카오 알림톡과 앱 푸시는 이후 범위로 둔다.
- 공급처 운영 알림은 이메일만 사용하고 SMS, 카카오 알림톡과 앱 푸시를 사용하지 않아야 한다. B-103은 출고 요청·상품 검토 결과 producer와 클레임 작업 type/template을 구현했고, 실제 클레임 작업 producer는 Planned B-105가 연결해야 한다.
- 공급처 이메일에는 고객 이름, 전화번호, 주소, 배송 메모, 결제·환불 정보를 넣지 않고 주문번호 또는 상품 식별자와 포털 링크만 포함해야 한다.
- 실제 법률 문구는 출시 전 별도 검토 대상이다.

## Non-Functional Requirements

- 주문 상태 변경은 추적 가능해야 한다.
- 결제 승인과 환불 완료는 서버와 관리자 권한 경계에서 검증해야 한다.
- 관리자 권한 API는 고객 권한과 분리되어야 한다.
- 금액 계산은 서버 기준으로 수행해야 한다.
- 주문 생성과 결제 확정 과정은 중복 요청에 안전해야 한다.
- 상태 전이와 알림 발송은 중복 요청에 안전해야 한다.
- 개인정보와 결제 관련 로그는 민감정보를 남기지 않아야 한다.
- 개인정보 보존 기간이 끝난 기록은 삭제 또는 비식별화되어야 한다.
- 법정 보존 기록은 일반 서비스 조회와 분리되어야 한다.
- 모든 공급처 조회와 변경은 인증된 사용자의 활성 portal-manager 연결로 범위를 제한하고 다른 공급처 리소스의 존재 여부를 노출하지 않아야 한다. 거래 상태는 별도의 신규 판매 gate로 사용해야 한다.
- 인증 cookie는 production에서 `HttpOnly`, `Secure`, `SameSite=Lax`를 사용해야 한다.
- 공급처 초대 token 원문, 신청자·공급처 연락처, 고객 개인정보, PII-bearing idempotency key/HMAC, 공급가, 이메일 본문과 내부 메모는 request/application 로그에 남기지 않아야 한다.
- 기존 수동/Domeggook 데이터와 API는 신규 포털 schema와 상태가 추가된 뒤에도 호환되어야 한다.

## Policy Requirements

- 고객에게 주문 후 공급처 품절 또는 출고 지연이 발생할 수 있음을 고지해야 한다.
- 품절 시 환불 정책을 명확히 표시해야 한다.
- 배송, 취소, 환불 정책은 고객 메뉴, 푸터, 상품 상세, 주문서에서 접근 가능해야 한다.
- 개인정보 처리방침과 사업자 정보는 고객이 항상 접근 가능한 위치에 제공되어야 한다.
