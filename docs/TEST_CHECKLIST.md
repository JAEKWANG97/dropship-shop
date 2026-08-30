# Test Checklist

출시 전 기능 검증 범위와 완료 기준이다. 자동 테스트 통과와 실제 외부 서비스 검증을 구분한다.

## Automated

- [x] API 전체 테스트: 인증, 권한, validation, 상품, 장바구니, 주문서, 계좌입금, 주문, 취소·클레임, 배송, 문의
- [x] PostgreSQL Testcontainers: 전체 migration, JPA validate, readiness
- [x] Web lint와 production build
- [x] Desktop/Mobile Playwright 전체 suite
- [x] 공개·고객·관리자 페이지 horizontal overflow 및 CSP 오류 없음
- [x] 상품 탐색·필터·상세·장바구니 추가
- [x] 배송지 검색 mock·수정·주문 동의 후 잠금
- [x] 계좌입금 주문서·주문 상세·취소·클레임 진입
- [x] 관리자 상품 필터·검수·상태 변경·원상복구
- [x] 관리자 주문 상세·메모·상태별 액션 노출
- [x] 고객 문의 접수·관리자 답변·고객 보호 조회
- [x] 정책·404·권한 없음·판매 중단 상태
- [x] Desktop/Mobile 핵심 snapshot

### B-102 Supplier Inventory And Reservation

- [x] TRACKED/UNTRACKED 재고 불변식, option-local version conflict, idempotent replay와 삭제 후 audit 보존
- [x] 24시간 예약·소비·해제, 복수 배송그룹, 부족 재고와 checkout/취소 경합
- [x] normal/late/mismatch 입금 명령 replay와 판매 불가·만료·미입금 취소 후 전액 환불 경계
- [x] 고객 응답 증적 비노출, 공급처 결제/환불 404·무알림, 관리자 full evidence·scope identifier
- [x] PostgreSQL V41 fresh/upgrade/preflight/default/FK·lock smoke와 H2 전체 회귀
- [x] 재고 409 후 입력 보존·이미지 중복 방지, 환불 승인/수동 완료, 만료/취소 주문 탐색 Web 계약

### B-103 Supplier Fulfillment And Minimum PII

- [x] portal-eligible, KEEP fallback, mixed/legacy, 결제 예외 routing matrix와 입금확인·Fulfillment·주소 잠금의 원자 rollback을 검증한다.
- [x] 비로그인·일반 회원·ADMIN·supplier 권한, active tenant/time-valid contract, 타 공급처·결제 예외 `404`를 검증한다.
- [x] 목록 PII 부재, 상세 allowlist·자기 item만·stable order-item id·초기 allocation 0/remaining ordered, exact masking과 `Cache-Control: no-store`를 검증한다.
- [x] 요청 +60일 직전/동일/직후, scheduler/read-lazy/terminal takeover, 중복 실행과 admin takeover 동일·변경 replay를 검증한다.
- [x] Claim grant/extension/revoke의 허용 상태·expected latest id·각 요청 최대 30일·contract guard·동일/변경 replay와 MASKED/read-only FULL 전환을 검증한다.
- [x] 배송 메모의 null·blank-to-null·trim·300자 경계와 customer/admin/supplier snapshot 응답을 검증한다.
- [x] 출고 요청·상품 검토 email의 PII-free payload, dispatch/retry 재검증, raw exception 비저장, invite retry 차단, 7일 retry·30일 cleanup과 B-105 전 claim-work producer 미호출을 검증한다.
- [x] V42 fresh/upgrade의 delivery memo 길이, grant/access-log FK·unique·index와 기존 null/backfill 호환을 PostgreSQL에서 검증한다.
- [x] 전체 API suite, Web lint/build와 공급처 출고 목록·상세 desktop/mobile 계약 Playwright를 검증한다.

### B-104 Supplier Multiple Shipments And Tracking Links

- [x] 첫 송장 전체 기본 할당, 분할·추가 송장 명시 할당, 중복·과할당·타 주문 item 거절과 동일 Order 동시 등록 직렬화를 검증한다.
- [x] supplier/customer/admin role·tenant·owner 404/403, feature-off 신규 admin 생성 차단과 저장된 동일 creation replay, supplier-owned 기존 송장의 관리자 운영 액션을 검증한다.
- [x] creation/action key의 actor·action·canonical body 충돌, expected version 필수·빈 값 fail-closed·stale 거절, 불확실한 실패의 동일 key 재시도와 정정·void·재등록 이력 보존을 검증한다.
- [x] 전체 할당·Shipment별 Coreable 증거 이후만 배송완료, 오완료 재개·시각 정정과 후속 Claim/Refund 차단을 검증한다.
- [x] 각 등록의 PII cutoff 단축, void/replacement 비연장, `TRACKING_REGISTERED` 취소 경계와 마지막 non-voided 배송완료 Claim 기준을 검증한다.
- [x] legacy singular customer/admin projection, Domeggook tracking sync·관리자 수동 보정, portal channel 분리와 PostgreSQL UUID 순서의 high-bit batch 잠금을 검증한다.
- [x] V43 fresh/upgrade, legacy allocation·old-writer commit trigger, partial unique, actor index, cross-order allocation·parent reassignment 차단을 PostgreSQL 17에서 검증한다.
- [x] Web lint/build와 supplier/customer/admin shipment 계약을 Desktop/Mobile Playwright로 검증한다.

## Production Read-Only

- [x] 홈페이지·상품 목록·상품 상세·정책·회사·고객문의 접근
- [x] `/api/health`, `/actuator/health/readiness`
- [x] dev login 비노출, 고객·관리자 API 비로그인 차단
- [x] Kakao authorize가 운영 callback URI를 사용
- [x] 운영 판매 상태에 맞는 구매 CTA 또는 판매 준비 안내
- [x] 상품 이미지와 업로드 경로 응답

## External Live

다음 항목은 실제 계정, 비용 또는 운영 데이터 변경이 필요하므로 자동 suite와 분리한다.

- [ ] 실제 Kakao 로그인·동의·callback·세션·이메일 반영
- [ ] 실제 모바일/웹 Daum 주소 검색과 배송지 저장
- [ ] 실제 계좌 입금 후 관리자 입금 대조·확인·미일치 처리
- [x] 도매꾹 e-money 실제 발주·취소·상태 대사와 응답 유실 재시도 보호
- [x] 도매꾹 상품 가격·옵션·재고 20개 dry-run과 실제 반영
- [ ] 실제 택배 송장 등록·조회·배송완료 동기화
- [ ] SES 실제 고객 문의 답변 메일 도착·재시도
- [ ] S3 백업 생성·복원 리허설

## Performance

- [ ] 공개 상품 목록과 상세의 기준 응답시간 측정
- [ ] 관리자 상품 300개 이상 목록의 필터·페이지 응답 측정
- [ ] 주문 생성 중복 요청의 멱등성과 동시 요청 확인
- [ ] B-103 공급처 상세 동시 접근에서 owner takeover·접근 로그가 유실 또는 중복 상태 전이를 만들지 않는지 확인
- [ ] B-103 cutoff scheduler 100건 batch와 공급처 주문 목록의 query 수·응답시간 측정
- [ ] B-103 운영 email retry·retention cleanup batch의 처리량과 재시도 폭주 방지 확인
- [ ] B-104 실제 PostgreSQL에서 같은 Order의 supplier/admin 송장 등록 경합과 서로 반대 순서의 복수 Order tracking batch deadlock 부재 확인
- [ ] B-104 다품목·다송장 주문의 supplier/admin 목록 query 수와 lock 대기·응답시간 측정
- [ ] EC2 메모리·swap·CPU credit·컨테이너 재시작 상태 확인
