# Account Policy

Status: Confirmed

## Purpose

회원가입, 로그인, 비회원 주문, 관리자 권한 정책을 정한다.

## Policy Areas

- 회원가입 방식
- 로그인 방식
- 비회원 주문 허용 여부
- 배송지 저장 방식
- 관리자 계정 생성 방식
- 관리자 권한 범위
- 계정 정지/탈퇴 처리
- 개인정보 수집 최소 항목
- 탈퇴 후 재가입 처리

## Initial Direction

- 비회원 주문은 MVP에서 제외한다.
- 고객은 로그인 후 주문할 수 있다.
- 고객 로그인은 소셜 로그인만 지원한다.
- 고객 로그인 화면에는 카카오 로그인만 노출한다.
- 이메일/비밀번호 로그인은 MVP에서 제공하지 않는다.
- 관리자도 고객과 같은 소셜 로그인 흐름을 사용한다.
- DB에 관리자 권한으로 등록된 계정만 관리자 기능에 접근할 수 있다.

## Confirmed Policy

- MVP에서는 비회원 주문을 허용하지 않는다.
- 모든 주문은 로그인한 고객 계정에 연결된다.
- 고객은 주문을 생성하기 전에 로그인해야 한다.
- 비회원 장바구니는 MVP 범위에 포함하지 않는다.
- 비회원이 주문 또는 장바구니 진입을 시도하면 로그인 화면으로 유도한다.
- 고객 로그인 화면은 카카오 소셜 로그인만 제공한다.
- Google/Naver OAuth 백엔드는 기존 계정 호환을 위해 유지하지만 일반 고객 로그인 화면에는 노출하지 않는다.
- 이메일/비밀번호 기반 고객 로그인은 MVP 범위에서 제외한다.
- 첫 가입 또는 첫 소셜 로그인 완료 시 이용약관과 개인정보처리방침 동의를 받는다.
- 고객 계정은 소셜 제공자와 제공자 user id를 기준으로 식별한다.
- 소셜 로그인에서 필수로 저장하는 값은 제공자, 제공자 user id, 표시 이름으로 시작한다.
- 카카오 로그인은 `profile_nickname`, `account_email` 동의를 요청하고 유효하고 인증된 이메일이 제공되면 저장한다.
- 카카오 계정에 이메일이 없거나 사용자가 선택 동의를 거부하면 이메일은 소셜 로그인 필수 수집 항목으로 강제하지 않는다.
- 이메일 또는 전화번호 같은 고객 연락처는 주문/배송/클레임에 필요한 시점에 서비스 화면에서 별도로 수집한다.
- 소셜 제공자가 이메일을 제공하지 않는 경우 DB의 `email` 필드는 내부 식별용 placeholder를 저장할 수 있으며, 이 값을 고객 연락 또는 알림 수신 주소로 사용하지 않는다.
- 고객 회원 유형은 구분하지 않는다. 소셜 로그인 사용자는 모두 같은 고객 흐름을 사용한다.
- 고객 사업자 프로필은 MVP에서 수집하지 않는다.
- 실제 운영에서는 로그인 후 고객 필수 정보 완료 상태를 확인한다.
- 고객 휴대폰 번호는 주문과 배송 연락을 위해 직접 입력받고 형식만 검증한다.
- 기존 SMS OTP API와 인증 이력은 호환성을 위해 유지하지만 필수 정보 완료나 checkout 조건으로 사용하지 않는다.
- NICE, KCB, Toss 인증 같은 CI/DI 기반 본인확인은 성인인증, 중복가입 방지, 실명확인이 필요해질 때 검토한다.
- 같은 이메일이더라도 제공자가 다르면 별도 계정으로 시작한다.
- 관리자 로그인도 별도 비밀번호 로그인 없이 소셜 로그인만 사용한다.
- 관리자 권한은 DB에 등록된 계정에만 부여한다.
- 로그인 성공 후 API는 JWT access token을 HttpOnly cookie로 내려준다.
- MVP 인증은 stateless access token 방식으로 시작하고, refresh token은 MVP 이후로 미룬다.
- 필수 이용약관/개인정보처리방침 현재 버전에 동의하지 않은 고객은 주문 생성으로 진행할 수 없다.
- 상품 조회와 장바구니 조회/수정은 동의 전에도 허용하지만, `POST /api/checkouts`는 현재 필수 동의가 있어야 한다.

## Supplier Portal Account Policy — Implemented (`B-100`), Production Gated

Status: `B-100` onboarding, Kakao activation, dynamic authority, lifecycle and application/invite retention are Implemented without changing existing customer/admin roles. `B-098` contract evidence automation and post-relationship cleanup remain Planned, so production activation stays disabled pending all release gates.

- 외부 공급처 신청은 로그인 없이 받지만, 공급처 포털 접근은 Coreable 승인과 이메일 초대 수락을 모두 거쳐야 한다.
- 공급처 신청은 active `SUPPLIER_APPLICATION_PRIVACY` exact version을 서버에서 검증해 canonical 동의시각과 함께 저장하고, 정규화 연락 이메일별 non-expired `SUBMITTED` 또는 `APPROVED` 신청을 합쳐 하나만 허용한다. 새 submit은 matching overdue SUBMITTED를 lock 아래 EXPIRED·cleanup한 뒤 중복을 판단한다.
- 사람의 신청 처리는 `SUBMITTED -> APPROVED|REJECTED`만 허용하고, 생성 90일 동안 미검토인 신청은 scheduler가 EXPIRED로 끝내고 연락 PII를 정리한다. 승인은 CREATE_NEW 또는 manager/invite/application/portal 이력이 한 번도 없는 legacy DISABLED Supplier를 id로 명시하는 LINK_EXISTING이며 이름/email 자동 매칭과 영구종료 supplier 재연결을 하지 않는다.
- 승인된 공급처는 신청 연락 이메일로 만료되는 1회용 초대를 받고 Kakao 로그인으로 담당자 계정을 연결한다. LINK_EXISTING은 거래상태를 유지하되 Supplier 연락 이메일을 신청 email로 동기화하고 검증시각을 비운 뒤 같은 주소로 초대한다. 이메일/비밀번호 공급처 로그인은 만들지 않는다.
- 이메일 링크 접근을 연락 이메일 소유 확인으로 사용하며 Kakao가 제공한 이메일과 초대 이메일의 일치를 요구하지 않는다.
- 공급처 초대는 Kakao 전용 invite context와 OAuth state를 결합한다. Google/Naver callback이나 invite context 없는 callback은 초대를 소비할 수 없다.
- 초대 승인 발급·재발급은 supplier별 issuance idempotency key/request hash를 저장한다. 동일 retry는 최초 결과를 반환하고 새 key의 재발급만 open invite를 폐기·교체한다. 만료·폐기·소비·연결 충돌은 계정 존재를 숨기는 안전 오류와 새 초대 요청 행동만 보여준다.
- 한 공급처에는 활성 담당자 한 명만 연결한다. 재초대 또는 담당자 교체는 관리자가 기존 미사용 초대나 연결을 폐기한 뒤 수행한다.
- 기존 `CUSTOMER` 또는 `ADMIN` 저장 role은 초대 수락으로 바꾸지 않는다. 활성 supplier manager 연결에서 `ROLE_SUPPLIER`를 파생하며 기존 고객/관리자 권한과 함께 사용할 수 있다.
- 기존 `Supplier.status=ACTIVE/INACTIVE`는 catalog·거래 가능 상태로 유지하고 portal 권한 상태는 `DISABLED`, `PENDING_ACTIVATION`, `ACTIVE`, `SUSPENDED`로 별도 관리한다.
- active user, `portalStatus=ACTIVE`와 현재 manager 연결로 supplier 권한을 부여하되 terminal 또는 overdue VERIFIED contract는 즉시 권한을 막는다. 최초 UNVERIFIED onboarding은 비PII catalog 작업만 허용한다. `Supplier.status`는 신규 catalog 판매·checkout만 막는 별도 gate이며 `INACTIVE`여도 time-valid contract가 있는 담당자만 이미 입금확인된 주문을 계속 출고할 수 있다.
- 포털 정지는 manager 연결을 보존한 `SUSPENDED`, 담당자 해제·교체는 manager를 비운 `PENDING_ACTIVATION`, 영구 종료는 `DISABLED`로 구분한다. 기존 결제완료 portal 주문은 Coreable 인계 대상으로 고정하고 재활성화 시 자동으로 되돌리지 않는다.
- 정지·해제 요청은 `salesAction=KEEP|PAUSE`를 필수로 받고 PAUSE를 명시한 경우에만 `Supplier.status=INACTIVE`로 바꾼다. UI는 PAUSE를 안전한 기본값으로 표시하지만 서버가 판매 상태를 숨겨서 바꾸지 않는다. Implemented B-103은 KEEP이면 portal 접근이 돌아올 때까지 신규 입금확인 주문을 `COREABLE_MANUAL`로 보내며, portal 재개도 판매 상태를 자동 복구하지 않는다.
- 연락 이메일 변경도 `salesAction=KEEP|PAUSE`를 필수로 받는다. 검증시각과 manager 연결을 초기화하고 미사용 초대를 폐기한 뒤 `PENDING_ACTIVATION` 상태에서 새 이메일 재초대를 요구한다. Implemented B-103은 KEEP 중 신규 입금확인 주문에 같은 Coreable fallback을 적용한다.
- 판매 재개/중지는 portal 상태 변경과 별도의 관리자 sales-status 명령으로 ACTIVE/INACTIVE를 명시한다. portal/contact/manager/sales lifecycle 명령은 idempotency key와 PII-free reason을 요구하고 actor, 전후 portal·판매 상태, salesAction, request HMAC/result와 시각을 기록한다. 관계 종료 cleanup은 reason/key/HMAC/result를 null 처리하고 비PII action/state/time을 보존한다. 판매 재개는 인계 주문이나 portal 상태를 복구하지 않는다.
- SUSPENDED portal 재활성화는 retained active manager, verified contact email과 time-valid VERIFIED contract를 요구한다. Contract 재검증만으로 portal/sales/handed-over owner를 복구하지 않는다.
- CREATE_NEW 승인은 Supplier를 `INACTIVE`, portal contract를 `UNVERIFIED`로 만든다. B-098 증적이 VERIFIED이고 effective time이 도래했으며 expiry가 지나지 않은 경우만 sales-status ACTIVE를 허용한다. Global flag 뒤에도 portal 상품 checkout/입금은 Supplier lock 아래 overdue evidence를 lazy EXPIRED로 바꾼 뒤 ACTIVE+time-valid VERIFIED를 검증한다. EXPIRED/REVOKED는 sales INACTIVE, ACTIVE portal SUSPENDED, open invite 폐기와 열린 supplier-owned portal work의 Coreable 인계를 함께 처리하며 재검증이 portal/sales/owner를 자동 복구하지 않는다.
- REJECTED application 연락 PII는 review+90일에 지우고 APPROVED 연락은 Supplier 운영 기록으로 유지한다. 영구 portal DISABLED, trade INACTIVE, open Fulfillment/Claim/Refund 없음이 모두 성립할 때 B-098/privacy notice의 관계 종료 deadline을 설정하고, scheduler가 Supplier lock 아래 조건을 다시 확인해 새 open work면 clear/defer한다. 계속 적격일 때만 Supplier와 approved application의 중복 연락 PII/replay material을 함께 지우며, invite 수신 이메일·issuance key/HMAC는 소비·폐기·만료 +30일에 정리한다.
- Production supplier portal flag가 false이면 외부 신청·초대 수락 경로를 숨긴다. Scope와 저장된 idempotency replay를 먼저 확인한 뒤 새 신청 승인/invite 재발급은 mutation 전에 `SUPPLIER_PORTAL_NOT_RELEASED`로 거절하고, 동일 완료 command는 token-free 결과만 반환한다. Dispatch도 발송 직전 flag를 재검사해 닫혔으면 `SKIPPED`로 끝내고 다시 연 뒤 새 key 재발급을 요구하되, 신청 거절·portal 정지/종료·retention cleanup은 계속 허용한다.
- Invite의 consumed user와 catalog/inventory/lifecycle supplier actor FK도 B-098 관계 종료 보관기한 뒤 null 처리하고 비PII action/state/time만 남긴다. 거래 이행 Shipment/shortage/claim actor는 parent Order/Claim 법정 보존 규칙을 따른다.
- 활성 공급처 담당자는 연결을 먼저 관리자에게 해제받기 전까지 고객 셀프서비스 회원 탈퇴를 할 수 없다.

## System Impact

- 비회원 주문을 제외하면 주문, 결제, 배송지, 환불 흐름이 단순해진다.
- 모든 주문은 `userId`를 가진다.
- 관리자 API는 일반 고객 API와 권한 검증을 분리해야 한다.
- 주문, 결제, 환불, 배송 조회 API는 authenticated user 기준으로 소유권을 검증한다.
- 주문 조회에서 이메일/전화번호 기반 비회원 주문 조회 기능은 MVP에 필요하지 않다.
- 비밀번호 저장, 이메일 인증, 비밀번호 재설정 기능은 MVP에 필요하지 않다.
- 사용자 테이블은 소셜 로그인 제공자와 제공자별 식별자를 저장해야 한다.
- OAuth callback, redirect URI, provider token/userinfo 요청 로직이 필요하며 DS-30에서 기반 구현을 제공한다.
- API 인증은 `ACCESS_TOKEN` HttpOnly cookie를 읽어 user id를 검증하고, 현재 DB의 사용자 상태와 role을 기준으로 권한을 판단한다.
- 운영에서는 인증 cookie에 `Secure`를 적용하고 HTTPS 환경에서만 사용한다.
- 첫 가입 또는 첫 소셜 로그인 완료 후 약관 동의 상태를 검증해야 한다.
- 약관 동의 기록에는 정책 버전과 동의 시각이 필요하다.
- 같은 필수 약관/개인정보 버전에 대한 중복 동의 요청은 기존 동의 기록을 반환한다.
- 기존 휴대폰 인증 API를 호출하는 경우 인증번호는 평문으로 저장하지 않고 hash로 저장하며, 만료 시간, 재발송 제한, 시도 횟수 제한을 유지한다.
- 휴대폰 번호 변경 시 기존 인증 시각은 초기화하지만 재인증을 요구하지 않는다.
- 계정 병합은 MVP에서 제공하지 않는다.
- 관리자 여부는 소셜 제공자가 아니라 내부 DB 권한으로 판단한다.
- 관리자 후보 계정은 provider와 provider user id 기준으로 사전 등록할 수 있어야 한다.
- 회원 탈퇴 시 `User.status`를 `DELETED`로 바꾸고 고객 프로필과 소셜 계정 연결은 삭제 또는 비식별화해야 한다.
- 법정 보존이 필요한 주문, 결제, 배송, 클레임 기록은 탈퇴 후에도 분리 보관해야 한다.
- 탈퇴 후 같은 소셜 계정으로 재가입하면 새 고객 계정으로 생성하고 기존 주문 내역은 고객 화면에 자동 복구하지 않는다.
- MVP 회원 탈퇴는 즉시 완전 삭제가 아니라 `status=DELETED`, `deleted_at`, `anonymized_at` 기록과 개인식별정보 비식별화로 처리한다.
- 탈퇴 시 `email`은 `deleted-{userId}@deleted.local`, `display_name`은 `탈퇴회원`, `phone_number`와 `phone_verified_at`은 null로 바꾼다.
- MVP 소셜 연결은 별도 `social_accounts` 테이블이 아니라 `users.provider/provider_user_id`에 저장되어 있으므로, 탈퇴 시 `provider_user_id`를 `deleted-{userId}`로 비식별화한다. 이로써 같은 소셜 계정 재로그인은 새 계정을 만든다.
- 진행 중 주문, 환불, 클레임이 하나라도 있으면 회원 탈퇴를 즉시 처리하지 않고 400으로 거부한다. 진행 중 주문은 `DELIVERED`, `CANCELLED`, `REFUNDED`, `EXPIRED`가 아닌 주문이다. 진행 중 환불은 `COMPLETED`, `REJECTED`가 아닌 환불이고, 진행 중 클레임은 `COMPLETED`, `REJECTED`, `WITHDRAWN`이 아닌 클레임이다.
- 이번 MVP에서는 별도 `LegalRetentionRecord` 색인 테이블을 만들지 않는다. 주문, 결제, 배송, 환불, 클레임, 약관 동의 기록은 비식별화된 유저 row를 참조한 채 보존해 참조 무결성과 법정 보존 근거를 유지한다.

### Implemented B-100 Impact

- 공급처 권한은 JWT의 고정 role claim만 신뢰하지 않고, 요청 시점의 활성 user, portal 상태와 `suppliers.manager_user_id` 연결을 함께 확인해야 한다. Supplier 거래 상태는 공개 판매·checkout에서 별도로 확인한다.
- `/api/supplier/**`는 인증 principal에서 supplier tenant를 결정하고 요청 payload의 supplier id를 신뢰하지 않는다.
- 초대 token 원문은 저장하거나 로그에 남기지 않고 unique digest, 수신 이메일, 만료·사용·폐기 시각, 소비 user와 생성 admin만 저장한다. 공급처별 유효한 미사용 초대는 하나다.
- 초대 token 교환은 초대를 바로 소비하지 않고 5분짜리 HttpOnly/Secure/SameSite=Lax binding cookie와 OAuth state를 만든다. Kakao callback은 invite row를 잠그고 manager 연결, 연락 이메일 검증, portal 활성화와 초대 소비를 한 트랜잭션에서 처리한다.
- cookie 인증 supplier의 unsafe HTTP method는 설정된 web `Origin`만 허용한다. `Origin`이 없으면 same-origin `Referer`를 요구하며 둘 다 없거나 불일치하면 `403`으로 거절한다.

## Open Questions

None for MVP.
