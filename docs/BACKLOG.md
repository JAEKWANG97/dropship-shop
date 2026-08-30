# Backlog

이 파일이 현재 작업 큐의 기준이다. 혼자 개발하는 동안 Linear와 GitHub Issues는 기본으로 사용하지 않는다.

## Now

### B-099 외부 공급처 포털 정책과 구현 설계

Status: Review Ready

Notes:
- Coreable이 단일 판매자 책임을 유지하면서 승인된 공급처가 자기 상품·재고·출고만 처리하는 포털을 설계한다.
- 기존 B-097 실제 연락과 B-098 계약·상품 반영은 그대로 보존하며, 포털 구현이 외부 연락·계약·운영 활성화를 자동 실행하지 않는다.
- 현재 구현과 충돌하는 재고 미관리, 관리자 발주 단계, 단일 Shipment 정책은 기존 데이터와 API를 보존하는 expand-contract 방식으로 전환한다.
- 상세 기준은 `docs/supplier-portal-design.md`다.

Tasks:
- [x] 공급처 신청, 관리자 승인, 이메일 초대, Kakao 연결과 1공급처 1담당자 경계를 확정한다.
- [x] 개별 상품 등록, Coreable 가격 통제, 일반 상품 자동 공개와 위험 상품 검토 정책을 확정한다.
- [x] `TRACKED`/`UNTRACKED` 재고, 24시간 예약, 금액 불일치 결제그룹 전액 환불, 만료와 늦은 입금 처리 정책을 확정한다.
- [x] 입금확인 즉시 출고 요청, 최소 PII, 접근기한과 감사 로그를 확정한다.
- [x] 복수 송장, 수량 할당, 공식 택배사 링크와 배송완료 경계를 확정한다.
- [x] 공급처 품절·클레임 사실 입력과 Coreable 최종 승인·환불 경계를 확정한다.
- [x] 정책, decision log, product scope, domain, ERD, API, order flow, architecture 문서를 `Planned` 상태로 동기화한다.
- [x] 후속 구현을 B-100~B-105의 review 가능한 단위로 분리한다.

### B-096 전문 공급처 발굴과 연락 우선순위

Status: Ready for Contact

Notes:
- 도매꾹 의존도를 낮추기 위해 국내 안전용품 제조사·도매·MRO 공급 후보 29곳을 조사했다.
- 근거 있는 적격 업체 17곳 중 코레카·크레텍·아주세이프티를 1차 연락 대상으로 정했다.
- 실제 연락, 계약, 상품 반영은 이 조사와 분리하며 사용자 승인 전 수행하지 않는다.

Tasks:
- [x] 공개 카탈로그의 카테고리·가격·공급 공백을 분석한다.
- [x] 공식 B2B·제조·유통 근거가 있는 후보를 최대 30곳 범위에서 조사한다.
- [x] 가격·카테고리·운영·신뢰·데이터 기준으로 점수와 신뢰도를 기록한다.
- [x] 1차·2차 연락 순위와 업체별 질문을 작성한다.
- [x] 비교 조건이 다른 가격과 공개되지 않은 거래 조건을 `확인 필요`로 구분한다.
- [ ] 실제 연락 전에 상위 업체와 문의 문안을 사용자가 최종 확인한다.

## Next

### B-100 공급처 신청·승인·초대와 tenant 인증

Status: Review Ready

Notes:
- B-099의 확정 설계를 기준으로 공개 신청부터 이메일 초대와 Kakao 세션 연결까지 구현한다.
- 기존 CUSTOMER/ADMIN 저장 role을 바꾸지 않고 활성 supplier manager 연결로 공급처 권한을 파생한다.

Tasks:
- [x] 공급처 신청과 관리자 승인/거절을 keyed-HMAC 기반으로 idempotent하게 구현하고, review key/action/mode/대상/reason/result를 durable하게 저장하며, review-time deadline lazy expiry와 미검토 90일 EXPIRED·REJECTED review+90일 비식별화 scheduler를 추가한다.
- [x] digest만 저장하는 만료·폐기 가능한 1회용 초대와 PENDING_ACTIVATION/no-manager/unverified guard의 allowlisted reason-code 재발급, terminal+30일 recipient/key/HMAC cleanup과 기존 NotificationLog recipient nullable 호환 migration을 구현한다.
- [x] Kakao 전용 초대 수락과 `/api/supplier/me`를 구현한다.
- [x] portal 접근 상태와 Supplier 판매 상태를 분리하고 정지·해제·연락 이메일 변경의 명시적 `salesAction`, immutable handover command history, additive Fulfillment channel/owner schema와 기존 열린 주문의 Coreable operational-owner 인계를 구현한다. 신규 KEEP fallback 활성화는 B-103이 맡는다.
- [x] CREATE_NEW는 INACTIVE/contract UNVERIFIED로 만들고 sales-status ACTIVE와 portal 상품 saleability에 B-098 per-supplier effective/expiry 포함 time-valid VERIFIED guard를 연결한다. Terminal/overdue contract는 supplier 권한·paid-work/Claim-grant 접근을 닫고 open work를 Coreable로 인계해야 한다.
- [x] `/api/supplier/**` tenant guard와 cookie mutation CSRF/origin 방어를 구현한다.
- [x] production 기본 off인 supplier portal feature gate와 개인정보·email·계약 activation guard를 구현하고, flag-off 승인/재발급/초대 dispatch가 mutation 전에 fail closed하는 회귀 테스트를 추가한다.
- [x] 신청·초대·권한·타 공급처 접근 회귀 테스트와 UI를 추가한다.

### B-101 공급처 개별 상품 등록과 검토 흐름

Status: Review Ready

Notes:
- CSV 없이 개별 등록부터 제공하고, 일반 상품은 자동 공개하되 안전·필수정보 위험만 Coreable이 검토한다.

Tasks:
- [x] 공급처 소유 상품·기본 옵션·이미지·상세·상품 고시 CRUD를 구현한다. 최초 submit 전 DRAFT만 scalar ownership discovery와 `Supplier -> fresh Product -> Option` lock 아래 hard-delete하고 CartItem/OrderItem 참조, stale ownership, 제출 이력과 마지막 옵션을 막으며, 그 밖의 상품은 숨김·판매중지로 보존한다.
- [x] 공급처 화면의 단일 `상품 등록` 동작이 내부 draft submit과 분류를 끝내도록 구현한다.
- [x] 공급처가 supplierId, 고객 판매가, 판매/검토 상태를 위조하지 못하게 한다.
- [x] option 총공급가에 동일 정책을 적용하는 deterministic Coreable 가격 계산, monotonic policy version/full calculator snapshot과 별도 product review 상태·분류기를 구현한다.
- [x] Product managementChannel/version과 pricing-policy version을 backfill하고 모든 legacy admin/review/cart/checkout/source writer를 scalar supplier/ownership discovery 뒤 `Supplier -> fresh Product -> Option` lock, stale ownership 재검증, 같은 aggregate version·actor history와 Portal active-policy 재계산으로 이관한다. Domeggook success/failure는 locked `sourceItemNo` 일치 때만 적용하고, V40 `sourceAutoSoldOut=false` provenance를 실제 source `ACTIVE -> SOLD_OUT`에만 설정해 marker-backed target/readiness recovery와 admin status clear로 수동 품절을 보호한다. 기존 범위 밖 공급가·고객가와 base+option 10억원 초과를 사전 스캔해 명시적 데이터 정정 승인 전 migration을 막고, exact 금액 연산과 DB snapshot 제약을 추가한다.
- [x] 자동 승인, 보완 요청, 승인, 거절과 PII-free review 문구, allowlisted business-field snapshot의 actor 기반 변경 이력을 구현한다. `CERTIFICATION_REVIEW` 승인은 compliance를 자동 변경하지 않고 기존 `PENDING` 판매 호환성을 유지한다. 삭제 이력은 immutable subject id와 nullable live FK로 보존하고 image metadata와 unique durable binary-cleanup job을 함께 commit하며 tombstone 재첨부 거절, live-reference 방어와 같은 job reopen을 적용한다.
- [x] 공급처용 allowlist 검토 상태·사유·안내·다음 행동과 같은 화면 재제출 UX를 구현하고 내부 review 정보 비노출을 검증한다.
- [x] 공개/관리자/공급처 응답 노출 차이와 HTML/image 안전 테스트를 추가한다. 미제출 DRAFT 삭제 성공, 제출/CartItem/OrderItem/마지막 옵션/tenant/version 거절, cart·checkout의 동일 lock contract 단위 검증과 기존 참조·stale ownership 통합 guard, 삭제 후 404·감사이력을 검증한다. 삭제 가능한 미제출 DRAFT는 정상 구매 API에서 판매불가이므로 인위적인 동시 구매 성공 경로를 만들지 않는다. 실제 multipart upload부터 DETAIL/notice/submit/public까지의 등록 lifecycle, PostgreSQL partial-unique thumbnail swap smoke, cleanup tombstone 거절·`LIVE_REFERENCE`·동일 job reopen/retry 증거를 추가한다.

### B-102 공급처 옵션 재고와 24시간 주문 예약

Status: Review Ready

Notes:
- 기존 COREABLE 옵션은 `UNTRACKED`, B-101에서 먼저 생긴 portal 옵션은 `TRACKED/onHand=0`, B-102 이후 새 포털 옵션은 `TRACKED` 기본으로 이관한다.

Tasks:
- [x] Product review와 분리된 option inventoryVersion, on-hand/reserved 불변식, stale canonical conflict와 replay response·immutable change history를 가진 idempotent 재고 수정을 구현한다.
- [x] 재고 이력은 immutable subject option id와 nullable live FK를 사용해 미제출 DRAFT option 삭제 뒤에도 audit과 `(subjectOptionId,idempotencyKey)` replay uniqueness가 보존되게 한다.
- [x] 기존/portal option과 OrderItem의 management-channel·inventory-mode·reservation-status snapshot을 expand-contract로 backfill하고, 기존 portal-origin OrderItem은 오분류하지 않도록 migration preflight에서 중단한다.
- [x] `수량 관리 (권장)`/`재고 수량 관리 안 함`, 공급처 `주문 받기`/`주문 중지`, mode별 validation·도움말과 고객 내부 재고/`무제한` 비노출 UI를 구현한다.
- [x] checkout/입금이 Supplier→Product→모든 Option(UNTRACKED 포함)을 공통 순서로 잠그고 catalog/inventory writer도 Product→Option을 따르게 해 PAUSE·상품상태·주문중지와 직렬화하며 원자적 예약/소비를 구현한다.
- [x] 미입금 취소·24시간 만료 자동 해제와 중복 실행 guard를 구현한다.
- [x] normal/late portal 입금 명령의 key/hash/immutable result replay, portal-origin contract lazy expiry를 포함한 Supplier lifecycle/saleability lock recheck, 기한 내 늦은 입금의 원자적 재확보와 실패 `PAYMENT_EXCEPTION`을 구현한다.
- [x] portal/legacy 공통 금액 불일치 명령이 전체 실입금 증적과 `PAYMENT_EXCEPTION`, 실제 수령액의 단일 `PAYMENT_GROUP/PAYMENT_AMOUNT_MISMATCH` Refund, 모든 Order `REFUND_REQUESTED`, HELD exactly-once 해제와 supplier 비노출을 원자·멱등 처리하도록 구현한다.
- [x] 기존 group-scope/비양수/cross-PaymentGroup Refund 사전 스캔으로 부적합 데이터의 migration을 차단한 뒤 금액 불일치 Refund의 nullable order/schema scope 제약, payment-group partial unique와 Payment/Order 동일 aggregate 복합 FK를 추가한다. Coreable 승인·수동 완료는 별도 key/hash/result replay로 정확한 실입금액 한 건만 반환한 뒤 Payment/PaymentGroup/모든 Order를 `REFUNDED`로 끝내도록 구현한다.
- [x] 늦은 입금과 portal/legacy 미입금취소 뒤 exact receipt의 Payment 증적, Order별 `LATE_DEPOSIT_EXCEPTION` Refund 자동 생성과 exception 전용 환불 경로를 구현한다. qualifying `CANCELLED`는 입금시각·재고와 무관하게 재개하지 않고 supplier 비노출·새 checkout으로 끝내며, PaymentGroup의 `PAYMENT_EXCEPTION` 환불 guard는 승인된 B-102 received-payment exception Refund에만 좁게 확장한다.
- [x] normal exact receipt의 판매불가·mode mismatch·scheduler 지연 기한초과를 `SALE_UNAVAILABLE_AT_DEPOSIT|LATE_DEPOSIT_EXCEPTION` whole-PaymentGroup exception outcome과 사유가 맞는 Order별 Refund로 exactly once 처리한다.
- [x] PostgreSQL 동시성·migration smoke, 부족·초과/복수 배송그룹/만료·미입금취소 경합/동일·변경 replay/수동환불 완료, 공급처 404·무알림과 재고 상태 회귀 테스트를 추가한다. exact-after-cancel은 portal/legacy, 기한 전후 입금시각, 미입금취소 동시성, refundable amount 복구, Refund 합계, 거절·금액변경 금지, Refund별 완료 replay, 부분/전체 완료와 절대 재개되지 않는 동작을 검증한다. `CANCELLED`+금액 불일치는 최우선 단일 `PAYMENT_AMOUNT_MISMATCH` group Refund, exact `CANCELLED`+saleability/late 결합은 cancellation-terminal `LATE_DEPOSIT_EXCEPTION`이어야 하며, prior Payment/Refund/Fulfillment 또는 미입금취소가 아닌 혼합 Order가 있으면 `409` 무변경이어야 한다. 고객 응답은 환불 처리 상태와 해당 환불액만 노출하고 입금자·거래 식별값·관리자 사유·계좌 증적을 숨기며, admin은 full evidence와 scope-correct identifiers를 보고, supplier는 list/detail `404`, email·알림·PII log가 모두 없어야 한다. Group-scope admin 응답은 nullable `orderId` 대신 `paymentGroupId`와 `appliedOrderIds`를 검증한다.

### B-103 공급처 출고 요청·최소 PII·이메일 알림

Status: Review Ready

Notes:
- 입금확인 완료 즉시 수락 단계 없이 공급처에 출고 요청을 보여준다.
- 실제 email 전달 검증 전에는 production supplier activation을 열지 않는다.
- B-103 구현과 문서 동기화, 전체 API/PostgreSQL/Web 검증 및 독립 구현·테스트 검토를 완료했다. Production supplier activation과 실제 email 검증은 별도 release gate로 남는다.
- 송장 등록 시 cutoff 단축은 Implemented B-104, `SUPPLIER_CLAIM_WORK_REQUESTED` 실제 producer는 Planned B-105가 소유한다.

Tasks:
- [x] portal fulfillment 생성, 요청시각·배송지 잠금과 portal access가 비활성인 `salesAction=KEEP` 신규 주문의 `COREABLE_MANUAL` fallback을 구현한다.
- [x] PII 없는 목록과 stable order-item id·수량을 가진 최소 상세 DTO를 구현해 B-104 allocation 입력 경계를 준비한다.
- [x] 요청 +60일 stored cutoff, scheduler/read-lazy·terminal takeover, cutoff/terminal MASKED read와 PII-free 사유의 승인 클레임 read-only FULL 한시 재개, time-valid contract 직접 guard, admin takeover command history와 PII 접근 로그 1년 삭제를 구현한다.
- [x] checkout 배송 메모를 최대 300자로 trim하고 blank를 `null` snapshot으로 저장하며 공급처 최소 PII 상세 응답에 `Cache-Control: no-store`를 적용한다.
- [x] PII 없는 신규 출고 요청·관리자 상품 검토 결과 producer와 세 운영 email type/template을 구현하고 dispatch/retry마다 active manager·time-valid contract·검증 이메일을 재확인해 old/unauthorized recipient를 `SKIPPED` 처리하며 raw exception 미저장, invite generic-retry 금지, supplier FAILED-only 7일 retry, terminal+30일 recipient/legacy failure reason cleanup을 적용한다. 클레임 작업 producer는 B-105까지 연결하지 않는다.
- [x] V42 delivery memo, Claim PII grant/access log와 notification retention index를 expand migration으로 추가한다.
- [x] 추가된 API 통합 테스트와 PostgreSQL V42 smoke, 전체 API suite, Web lint/build, 공급처 출고 Web 계약 Playwright를 실행하고 독립 검토를 반영한다.

### B-104 공급처 복수 송장과 공식 배송조회 링크

Status: Review Ready

Notes:
- 송장 등록과 실제 출고를 분리하고 택배사 실시간 API 없이 공식 조회 링크를 제공한다.
- B-104 구현과 문서 동기화, 전체 API/PostgreSQL/Web 검증 및 독립 backend/migration/test 검토를 완료했다. Production supplier activation과 실제 택배 배송건 검증은 별도 release gate로 남는다.
- Production `APP_SUPPLIER_PORTAL_ENABLED=false`를 유지하며 새 admin portal-Shipment 생성도 저장된 동일 replay 외에는 차단한다. 기존 증거의 조회·정정·void·배송 보정은 안전 운영 경로로 남긴다.

Tasks:
- [x] Shipment 1:N과 order item 수량 allocation을 expand-contract migration으로 구현한다.
- [x] 단일 송장 기본 전체 할당, 분할 출고 opt-in과 추가 송장 명시 할당을 구현한다.
- [x] over-allocation·중복·타 주문 item·동시 등록을 거절한다.
- [x] carrier registry 기반 공식 URL과 `TRACKING_REGISTERED` 표시를 구현한다.
- [x] 고객 plural shipment 응답과 `송장 등록 · 배송조회 가능` 링크 UI를 구현하고 legacy singular 응답을 호환한다.
- [x] action+actor+canonical body를 hash하는 supplier/admin 공유-key creation/action idempotency와 immutable result, Shipment version 0 backfill·writer 이관, optimistic guard, 배송완료 전 carrier/tracking 정정과 allocation 오류 void+재등록을 구현한다.
- [x] Coreable void/배송완료와 후속 Claim/Refund 전 guarded 배송완료 재개·시각 정정 이력을 구현한다.
- [x] 각 송장 등록이 stored cutoff를 `min(current, registeredAt+30일)`로만 단축하고 void/replacement가 늘리지 않도록 B-103 경계를 확장한다.
- [x] `TRACKING_REGISTERED`를 Claim/refund transition guard와 customer/admin allowlist에 이관하고 Claim 기준시각을 `max(non-voided deliveredAt)` aggregate로 교체한다.
- [x] Shipment와 admin portal-shipment가 Order→Fulfillment→all Shipment→OrderItems 공통 lock을 공유하도록 구현한다. Shortage report lock/guard 확장은 B-105가 맡는다.
- [x] 모든 Claim/Refund writer가 parent Order를 해당 row보다 먼저 잠그도록 이관한다. Payment-origin Refund는 PaymentGroup→Supplier→Product→Option→Order→Refund 전역 순서를 유지해 배송완료 correction과 후속 처리 생성이 경합하지 않게 한다.
- [x] 기존 단일 shipment 응답·Domeggook tracking·관리자 수동 보정·취소 Claim 호환 테스트를 추가한다.

### B-105 공급처 품절 보고와 클레임 사실 입력

Status: Todo

Notes:
- 공급처는 사실만 입력하고 Claim/Refund 최종 결정과 실제 계좌환불은 Coreable만 수행한다.

Tasks:
- [ ] 송장 등록 전 배송 그룹 주문 전체 품절을 REPORTED로 기록하고 Order/Refund를 바꾸지 않은 채 Coreable owner로 인계하는 supplier list/detail을 구현한다. Submit hash와 immutable safe result로 동일 key만 replay하고 같은 order의 새 key는 충돌시킨다.
- [ ] `supplier_shortage_reports` 추가 뒤 supplier/admin Shipment service의 공통 lock을 Order→Fulfillment→report→all Shipment→OrderItems로 확장하고 open REPORTED report가 admin portal-shipment와 경합하지 않게 막는다.
- [ ] Coreable shortage list/detail과 free-text 없는 allowlisted-code 승인·거절을 구현한다. 승인만 기존 out-of-stock/refund service를 실행하고 거절은 Coreable owner를 유지한다.
- [ ] Coreable이 만드는 orderNumber·자기 상품 요약 포함 safe claim task, 정정 가능한 same-task fact history projection과 request hash/immutable result를 저장하는 idempotent append-only supplier fact 입력·tenant 경계를 구현한다.
- [ ] Claim terminal/dueAt에서 열린 task를 닫고 create/fact/close의 allowed status와 idempotency를 구현한다.
- [ ] 공급처 입력이 Claim/Order/Refund 상태를 직접 변경하지 않는 회귀 테스트를 추가한다.
- [ ] Coreable 승인·거절·반품입고·환불 기존 흐름과 통합 검증한다.

### B-057 관리자 회원 관리와 알림 발송 이력

Status: Todo

Notes:
- 고객 이름/이메일/전화로 계정을 찾고 주문 이력을 보는 CS 조회 수단이 필요하다.
- SMS 발송 로그와 retry API는 구현됐지만 관리자 화면이 없다.

Tasks:
- [ ] 관리자 회원 목록/검색과 상세 주문 이력을 추가한다.
- [ ] 관리자 알림 발송 이력 화면과 실패 건 재발송 버튼을 기존 API에 연결한다.
- [ ] 회원 상세와 주문 상세를 상호 링크한다.
- [ ] 접근 권한과 개인정보 노출 범위를 검토한다.
- [ ] 회귀 테스트를 추가한다.

### B-097 전문 공급처 실제 연락과 거래조건 검증

Status: Todo

Notes:
- B-096의 상위 업체에 실제 연락해 도매가·MOQ·직배송·상품 데이터 사용권을 확인한다.
- 연락·문의 전송은 사용자가 연락 대상과 문안을 확인한 뒤 진행한다.

Tasks:
- [ ] 1차 연락 3곳의 담당자와 문의 채널을 최종 확인한다.
- [ ] 승인된 문안으로 문의하고 답변 원문과 확인일을 기록한다.
- [ ] 동일 모델 공급가·VAT·배송비를 도매꾹 및 Coreable과 다시 비교한다.
- [ ] 계약 후보를 확정하고 실제 상품 반영 개발을 별도 backlog로 만든다.

### B-059 운영 보안 마감

Status: In Progress

Notes:
- 80/443은 Cloudflare 공개 IPv4 대역으로 제한했고 DB/API 포트는 외부에 노출하지 않는다.
- SSH 22 인바운드는 제거했고 GitHub Actions 배포도 OIDC 단기 자격증명과 SSM으로 전환했다.
- 남은 핵심은 root/장기 access key 제거다.

Tasks:
- [x] 80/443 인바운드를 Cloudflare IP 대역으로 제한하고 직접 origin 접속 차단을 확인한다.
- [x] 미사용 보안그룹을 삭제한다.
- [ ] root 액세스 키의 마지막 사용을 확인하고 삭제한다.
- [ ] `cli-user`의 장기 `AdministratorAccess` 키를 로테이션하고 사람/배포 권한을 필요한 범위로 줄인다.
- [x] EC2에 S3 백업 전용 instance role을 연결하고 `/root/.aws`의 정적 백업 키를 제거한다.
- [x] EC2 `unattended-upgrades`가 enabled/active인지 확인한다.
- [x] SSH 22 인바운드를 제거하고 SSM 명령 실행을 검증한다.
- [x] GitHub Actions 배포를 main 전용 OIDC 역할과 대상 EC2 전용 SSM 권한으로 전환한다.
- [x] Next.js/PostCSS/sharp high advisory를 해소하고 `npm audit` 0건을 확인한다.

### B-062 최소 운영 모니터링과 비용 알림

Status: In Progress

Notes:
- AWS Budget 월 한도는 50달러로 유지하고 실제 비용 15·25·50달러 단계별 이메일 알림을 사용한다.
- EC2 상태·CPU credit·DB 백업 실패·백업 지연 CloudWatch alarm 4개는 SNS 이메일 구독과 함께 `OK`다.
- S3 DB 백업은 매일 생성되지만 주간 EBS snapshot DLM 정책은 `sts:AssumeRole` 오류 상태다.
- 새 관측 플랫폼은 도입하지 않고 AWS 기본 기능과 기존 container 상태 확인으로 시작한다.

Tasks:
- [x] AWS Budget 월 50달러 한도와 실제 비용 15·25·50달러 단계별 이메일 알림을 확인한다.
- [x] EC2 status check 실패와 CPU credit 부족 CloudWatch alarm을 추가한다.
- [ ] 메모리, swap, Docker container 재시작 횟수를 확인할 최소 수집/알림 방식을 정한다.
- [x] 마지막 S3 DB 백업 시각과 백업 실패를 감지하는 알림을 추가한다.
- [ ] DLM execution role의 trust policy를 수정하고 주간 EBS snapshot이 실제 생성되는지 확인한다.
- [x] 최신 DB dump와 상품 이미지 backup으로 복구 리허설을 1회 실행한다. B-038에서 검증했다.
- [ ] 장애 알림 수신 후 확인할 명령과 대응 순서를 운영 문서에 남긴다.

### B-094 일반 작업장갑 카테고리와 기존 후보 재검토

Status: In Progress

Tasks:
- [x] `개인보호구 > 일반 작업장갑` 카테고리와 원본 안전장갑 경로 매핑을 추가한다.
- [x] 조건부 배송·비완제품 제외 규칙을 유지한 재검토 manifest를 만든다.
- [ ] 참조 상품을 포함한 적격 후보를 운영 dry-run, 백업, import 순서로 반영한다.

## Deferred

### B-098 전문 공급처 계약과 상품 반영

Status: Todo

Notes:
- B-097에서 거래조건과 상품 데이터 사용권이 검증된 공급처만 대상으로 한다.
- 계약 체결, 공급처별 importer·동기화 개발, 운영 상품 반영은 각각 검토 가능한 작업 단위로 나눈다.

Tasks:
- [ ] 계약·정산·반품·개인정보 처리 범위를 검토한다.
- [ ] 공급처별 계약 version·effective/expiry·검증/만료/폐기·검증 관리자 증적과 idempotent history를 기록하고, expected current version/버전당 terminal 1건 guard로 stale scheduler·관리자 명령을 막는다. Terminal/lazy expiry는 sales INACTIVE, portal SUSPENDED, invite 폐기와 open work Coreable 인계를 원자적으로 수행하고 time-valid VERIFIED만 sales/paid-work/Claim-grant 접근이 되게 한다.
- [ ] privacy notice에 관계 종료 후 공급처 연락 PII 보관기간을 확정하고 영구종료+미처리 업무 없음 이후 Supplier/approved application 중복 연락정보 cleanup을 구현한다.
- [ ] invite 소비자와 catalog/inventory/lifecycle supplier actor FK는 관계 종료 보관기한에, shipment/shortage/claim actor FK는 parent 법정 보존기한에 null/delete하도록 retention migration과 scheduler를 구현한다.
- [ ] 확정된 공급처의 SKU·가격·재고·MOQ·이미지 제공 방식을 문서화한다.
- [ ] 공급처별 최소 importer와 동기화 범위를 별도 구현 backlog로 분리한다.
- [ ] dry-run·백업·검수 뒤 승인된 상품만 운영에 반영한다.

### B-030 출시 전 법적/소비자 고지 정리

Status: Skipped

Notes:
- 친구와 만드는 비판매 취미 서비스에서는 구매안전서비스·홈택스·실판매 법무 확인을 진행하지 않는다.
- 실제 고객 주문과 입금을 받기 전에 이 항목을 다시 `Now`로 올려 출시 차단 조건을 재검증한다.
- 고객 결제 경로는 계좌입금과 관리자 입금확인으로 확정했으며 PG 결제는 도입하지 않는다.

Tasks:
- [x] 사업자/정책/고객센터/구매안전 출시 차단 체크리스트를 만든다.
- [x] 정책 페이지의 고객 노출 `MVP` 표현을 제거한다.
- [x] 회사 정보에 공정위 통신판매사업자 등록현황 안내 링크를 추가한다.
- [x] 상품 등록 기준에 안전용품 인증/상품정보제공고시 체크를 추가한다.
- [x] 간이과세자 통신판매업 신고 면제 상태를 고객 화면에 반영한다.
- [x] 고객센터 전화번호, 이메일, 운영 시간을 실제 값으로 교체한다.
- [x] 호스팅 제공자를 Amazon Web Services로 표시한다.
- [ ] 실제 판매 전 계좌입금 구매안전서비스 방식과 고객 선택 흐름을 확정한다.
- [x] 현금영수증 발급 절차와 담당자를 홈택스 수동 발급으로 정하고 고객 안내에 반영한다.
- [x] 운영 계좌 은행명·계좌번호·예금주를 `APP_BANK_TRANSFER_*`에 설정하고 실제 입금 안내 화면을 검증한다.
- [ ] 실제 판매 전 홈택스 가맹·발급 권한과 세금계산서 발급 가능 여부를 확인한다.
- [ ] 실제 판매 전 개인정보 처리 위탁·제3자 제공 현황과 초기 판매 상품의 인증·고시를 확인한다.
- [x] 공개 취소/환불 정책에서 미사용 PG 문구를 제거하고 계좌입금 수동 환불 기준만 남긴다. B-067에서 처리했다.
- [x] 이용약관과 주문 정책 버전은 `2026-08-02`, 개인정보처리방침은 `2026-08-04`로 확정하고 고객 화면에는 시행일만 표시한다.
- [ ] 공개 정책 원본과 주문 동의 버전의 단일 변경 절차를 정한다.
- [ ] 고객센터의 반품 주소 직접 노출을 제거하고 반품 신청 후 안내 방식으로 통일한다.
- [ ] 문서에 남은 `prelaunch-*` 공개 설명과 이미지 저장소의 S3 필수 표현을 현재 운영 기준으로 교정한다.

### B-061 고객 문의 운영과 개인정보 동의 보강

Status: Skipped

Notes:
- 문의 접수, 관리자 처리, 고객 전용 조회 링크와 개인정보 동의 증적은 구현됐다.
- 이메일을 실제 발송하지 않고 사이트에서 답변을 확인하는 현재 운영에서는 SES 도메인/DKIM과 production access를 진행하지 않는다.
- 실제 이메일 알림을 사용하기로 결정할 때만 재개한다.

Tasks:
- [x] 문의 수집 목적, 수집 항목, 보유 기간, 동의 거부권과 불이익을 `/support`에 명시한다.
- [x] 문의 접수 시 동의 정책 버전과 동의 시각을 저장한다.
- [x] 문의 상태(`RECEIVED`, `IN_PROGRESS`, `ANSWERED`, `CLOSED`)와 관리자 메모/답변 기록을 추가한다.
- [x] 관리자 문의 목록에 상태 필터와 처리 동선을 연결한다.
- [x] 고객 전용 HMAC 조회 링크와 답변 상태 화면을 추가한다.
- [x] 비로그인 문의 접수에 기본 rate limit을 적용하고 과도한 요청을 명확히 거절한다.
- [x] 문의 접수일 3년 후 자동 삭제를 적용한다.
- [x] AWS SES 답변 이메일 발송과 실패/재시도 이력을 연결한다.
- [x] 공개 접수, 관리자 처리, 개인정보 동의 증적에 대한 API/Web 회귀 테스트를 추가한다.
- [x] Playwright에서 관리자 상태 변경, 답변 저장, 이메일 재시도와 고객 조회 반영을 실제 form action으로 검증한다.
- [ ] 실제 이메일 알림 도입 시 SES 도메인/DKIM, production access와 고객 도착을 확인한다.

## Later

### B-082 상품 상세 데스크톱 스티키 구매 패널

Status: Todo

Notes:
- 데스크톱 상품 상세에서 옵션·수량·총액·구매 버튼을 스크롤 중에도 확인할 수 있게 한다.
- 기존 구매 form을 재사용하고 신규 API, DB, dependency는 추가하지 않는다.
- 모바일은 현재 하단 고정 구매바를 유지하며 상단 상품정보 탭 고정은 별도 이슈로 둔다.

Tasks:
- [ ] 데스크톱 구매 패널에 헤더 높이를 고려한 `position: sticky`를 적용한다.
- [ ] 상세 콘텐츠와 푸터를 가리거나 겹치지 않는지 확인한다.
- [ ] 옵션·수량 변경 시 총액과 장바구니·바로구매 동작이 기존과 동일한지 확인한다.
- [ ] Desktop `1440x1000`과 Mobile `390x844`에서 레이아웃과 overflow를 검증한다.

### B-052 무중단 배포 도입

Status: Todo

Notes:
- 현재 compose 교체 배포는 API 중단이 발생한다. 실제 주문량이 생기고 인스턴스 메모리를 늘릴 때 도입한다.
- 방식은 기존 nginx를 활용한 같은 호스트 blue-green으로 제한한다.

Tasks:
- [ ] 도입 시점과 인스턴스 비용을 확정한다.
- [ ] compose blue/green 서비스와 nginx upstream 전환 스크립트를 추가한다.
- [ ] deploy workflow를 헬스체크 게이트 기반 전환으로 바꾼다.
- [ ] 전환 실패 시 기존 컨테이너 유지 rollback을 확인한다.
- [ ] DB migration expand-contract 규칙을 배포 문서에 기록한다.

### B-055 인프라 비용 최적화

Status: Todo

Notes:
- 현재 t4g.micro는 smoke에서 메모리 여유가 작았지만 서버가 정지된 오픈 전 단계다.
- 실제 주문 전 t4g.small 필요성을 재확인하고, 사이즈가 안정된 뒤에만 약정을 검토한다.

Tasks:
- [ ] 서버 재가동 후 동일 부하 smoke로 t4g.micro 유지/업그레이드를 결정한다.
- [ ] 필요하면 t4g.small로 업그레이드하고 메모리/swap을 재측정한다.
- [ ] 1~2개월 운영 후 사이즈가 고정되면 1년 Compute Savings Plan을 검토한다.
- [ ] S3 Gateway Endpoint, Elastic IP 유지, EBS 크기 등 추가 절감 효과를 비용 기준으로 비교한다.

### B-009 관리자 통계 대시보드

Status: Todo

Tasks:
- [ ] 실제 DB 집계 기준을 정한다.
- [ ] 매출/주문/품절/환불 지표를 과장 없이 보여준다.
- [ ] mock operational data가 다시 들어가지 않게 확인한다.

### B-010 관리자 권한 세분화

Status: Todo

Tasks:
- [ ] 현재 `ADMIN` 단일 권한으로 충분한 범위를 정리한다.
- [ ] 상품/주문/환불/정책 관리 권한 분리가 필요한 시점을 정한다.
- [ ] 필요 시 role/permission 모델을 설계한다.

## Working Rules

- 작업 시작 전 `Now`에서 하나를 고른다.
- 작은 버그와 문구 수정은 backlog 항목을 만들지 않아도 된다.
- 큰 기능, 정책, 결제, 주문 상태, DB 변경은 backlog에 남긴다.
- 각 backlog 항목의 하위 task/checklist는 같은 항목 아래 `Tasks:`로 관리한다.
- 완료하면 `docs/BACKLOG_DONE.md`로 옮기고 git commit으로 이력을 남긴다.
- PR과 외부 이슈는 팀 협업, 배포 전 리뷰, 큰 리스크 변경에만 사용한다.
- `docs/PROJECT_LOG.md`는 관련 backlog ID로 연결한다.
