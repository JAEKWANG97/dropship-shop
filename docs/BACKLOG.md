# Backlog

이 파일이 현재 작업 큐의 기준이다. 혼자 개발하는 동안 Linear와 GitHub Issues는 기본으로 사용하지 않는다.

## Now

### B-016 테스트 배포 및 운영 readiness 점검

Status: Todo

Notes:
- `coreable-saf.com` 도메인은 확보됨.
- 아직 정식 서비스 오픈이 아니라 테스트 배포와 외부 연동 준비용 배포다.
- Toss live 심사, 통신판매업 신고, 실결제 오픈은 배포 URL 확인 후 별도 단계로 진행한다.

Tasks:
- [ ] 테스트 배포 아키텍처를 확정한다.
- [ ] production/staging env 변수 목록을 배포 서버에 등록한다.
- [ ] 상품 이미지 local volume 경로와 `APP_STORAGE_*` 값을 확정한다.
- [ ] `/api/health`, readiness, liveness를 배포 환경에서 확인한다.
- [ ] DB migration dry run과 backup/snapshot 상태를 확인한다.
- [ ] Web/API 도메인과 HTTPS를 연결한다.
- [ ] 배포 URL 기준 Playwright smoke를 실행한다.
- [ ] Toss live 심사에 필요한 홈페이지/정책/사업자 정보 접근 경로를 확인한다.
- [ ] Toss live 승인이 완료되면 live key 전환 작업을 별도 진행한다.

### B-001 Toss Payments sandbox 결제 플로우 완성

Status: Todo

Notes:
- 웹 결제창 호출과 성공 리다이렉트 서버 confirm 연결은 구현됨.
- 실제 sandbox key로 결제창/승인 검증 후 완료 처리.

Tasks:
- [ ] Toss test client key를 로컬 env에 설정한다.
- [ ] Toss test secret key를 로컬 env에 설정한다.
- [ ] Toss sandbox 결제창에서 성공 결제를 실행한다.
- [ ] Success redirect 후 backend confirm 호출을 확인한다.
- [ ] 주문 상태가 `SUPPLIER_ORDER_PENDING`으로 바뀌는지 확인한다.
- [ ] Toss 실패/취소 redirect 화면을 확인한다.
- [ ] 중복 success redirect 또는 중복 confirm 요청이 같은 결제 결과를 반환하는지 확인한다.
- [ ] 결제 예외 발생 시 고객 화면과 관리자 payment exception queue를 함께 확인한다.

### B-002 소셜 로그인 실브라우저 검증

Status: Todo

Notes:
- 고객 회원 유형은 구분하지 않는다. 소셜 로그인 사용자는 모두 같은 고객 흐름을 사용한다.
- 고객 사업자 프로필은 현재 필요하지 않으므로 MVP 범위에서 제외한다.

Tasks:
- [ ] Google 실계정 로그인, callback, session cookie를 브라우저에서 확인한다.
- [ ] Kakao 실계정 로그인, callback, session cookie를 브라우저에서 확인한다.
- [ ] Naver 실계정 로그인, callback, session cookie를 브라우저에서 확인한다.
- [ ] 로그인 후 `/account`에서 현재 사용자 정보가 보이는지 확인한다.

### B-003 관리자 주문 처리 액션을 실제 운영 흐름에 연결하기

Status: In Progress

Notes:
- 관리자 주문 상세 상태 표시와 발주/품절/송장 액션 form은 웹에 연결됨.
- 관리자 화면 노출은 확인됨.
- 실제 액션 실행 성공/실패 검증은 출시 전 최종 테스트로 남김.

Tasks:
- [x] 관리자 주문 상세에서 현재 주문/결제/배송/환불 상태를 확인할 수 있게 한다.
- [x] 발주 시작 액션을 실제 API에 연결하고 사유 입력을 받는다.
- [x] 공급처 발주 완료 액션을 실제 API에 연결하고 발주번호/예상출고일을 입력받는다.
- [x] 공급처 품절 액션을 실제 API에 연결하고 환불 흐름 진입을 확인한다.
- [x] 송장 입력 액션을 실제 API에 연결한다.
- [ ] 각 액션 성공 후 주문 목록/상세 상태가 갱신되는지 확인한다. 출시 전 최종 테스트에서 실행.
- [ ] 권한/validation/API 실패가 빈 화면이 아니라 오류 안내로 보이는지 확인한다. 출시 전 최종 테스트에서 실행.

## Next

### B-013 출시 전 모바일/디자인 QA 정리

Status: Todo

Tasks:
- [ ] 모바일 header wrapping을 확인한다.
- [ ] 상품 카드 밀도와 이미지 비율을 모바일/데스크톱에서 확인한다.
- [ ] checkout form이 작은 화면에서 겹치지 않는지 확인한다.
- [ ] 로그인/계정/주문/관리자 주요 화면의 빈 상태와 오류 상태를 점검한다.
- [ ] 실제 상품 사진 fixture가 준비되면 local seed 이미지를 교체한다.

### B-026 초기 판매 상품 데이터 준비

Status: In Progress

Notes:
- 실제 판매 상품명, 가격, 공급처, 이미지 파일은 운영자가 준비한다.
- 브라우저 crop UI와 CSV/엑셀 일괄 등록은 이번 범위에서 제외한다.
- 상품 등록 기준은 `docs/product-registration-guide.md`를 따른다.

Tasks:
- [x] 대표 이미지와 상세 이미지 권장 규격을 정한다.
- [x] 관리자 상품 등록/상세 콘텐츠 화면에 이미지 규격 안내를 추가한다.
- [x] 상품 등록 기준 문서를 만든다.
- [ ] 초기 판매 상품 10~20개를 등록한다.
- [ ] 고객 상품 목록, 상품 상세, 장바구니에서 등록 상품 노출을 확인한다.

### B-030 출시 전 법적/소비자 고지 정리

Status: In Progress

Notes:
- 법률 자문 대체가 아니라 개발/운영 출시 차단 체크리스트로 관리한다.
- 실제 결제 오픈 전까지 준비중 항목은 실제 값으로 교체해야 한다.
- 기준 문서는 `docs/legal-launch-checklist.md`다.

Tasks:
- [x] 사업자/정책/고객센터/구매안전 출시 차단 체크리스트를 만든다.
- [x] 정책 페이지의 고객 노출 `MVP` 표현을 제거한다.
- [x] 회사 정보에 공정위 통신판매사업자 등록현황 안내 링크를 추가한다.
- [x] 상품 등록 기준에 안전용품 인증/상품정보제공고시 체크를 추가한다.
- [ ] 통신판매업 신고번호와 신고 기관을 실제 값으로 교체한다.
- [ ] 고객센터 전화번호, 이메일, 운영 시간을 실제 값으로 교체한다.
- [ ] 호스팅 제공자와 결제/구매안전서비스 정보를 실제 값으로 교체한다.
- [ ] 초기 판매 상품별 안전인증/KC/상품정보제공고시 항목을 확인한다.

### B-033 상품 원가/판매가/마진 정책 관리

Status: In Progress

Notes:
- 공급처 원가와 고객 판매가를 분리한다.
- 기본 판매가는 공급가 25% 증액 후 100원 단위 올림으로 계산한다.
- 정산, 세금 신고, 공급처별 마진율은 이번 범위에서 제외한다.

Tasks:
- [x] `products.source_price`와 active pricing policy schema를 추가한다.
- [x] 관리자 상품 API에 `sourcePrice`를 추가하고 공개 API에는 노출하지 않는다.
- [x] 관리자 가격 정책 화면을 추가한다.
- [x] 관리자 상품 상세에서 계산 판매가를 적용할 수 있게 한다.
- [x] 도매꾹 import가 원가와 계산 판매가를 분리해 적재하게 한다.
- [ ] 기존 등록 상품의 원가/판매가를 운영자가 검수한다.

## Later

### B-008 검색/필터 고도화

Status: Todo

Tasks:
- [ ] 고객 상품 목록 검색 기준을 상품명/요약/카테고리 중심으로 정리한다.
- [ ] 관리자 상품/주문 검색 조건을 실제 운영 필드 기준으로 정리한다.
- [ ] 필터가 빈 결과와 API 실패를 구분해 보여주는지 확인한다.

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
- [ ] 권한 세분화가 필요하면 DB role/permission 모델을 설계한다.

### B-011 알림/메일/문자 발송

Status: Todo

Tasks:
- [ ] 현재 notification log trigger가 필요한 이벤트를 모두 남기는지 확인한다.
- [ ] 실제 메일/SMS/알림톡 provider 선택을 보류 또는 확정한다.
- [ ] 실패 알림 retry API와 관리자 화면 연결 범위를 정한다.
- [ ] 공급처 지연 안내 trigger와 고객 안내 문구를 정리한다.

### B-014 회원 탈퇴 요청 흐름

Status: Todo

Tasks:
- [ ] 회원 탈퇴 요청 정책과 보관/삭제 경계를 확인한다.
- [ ] `POST /api/me/deletion-request` API를 구현한다.
- [ ] 미완료 주문/환불/클레임이 있을 때 탈퇴 요청 처리 방식을 정한다.
- [ ] 고객 계정 화면에 탈퇴 요청 진입점을 추가한다.

### B-015 고객 클레임 조회/증빙/교환 흐름 고도화

Status: Todo

Tasks:
- [ ] 고객 주문별 클레임 목록 API를 구현한다.
- [ ] 고객 클레임 상세 API를 구현한다.
- [ ] 관리자 증빙 요청/반품 수령/교환 발송 액션 범위를 정한다.
- [ ] 판매자 귀책 클레임의 인지일/증빙 파일 입력 정책을 정한다.
- [ ] 고객 화면에서 클레임 처리 상태를 확인할 수 있게 한다.

### B-037 배포 환경 부하 smoke

Status: Todo

Notes:
- Oracle VM/API가 실제로 떠 있는 뒤에만 진행한다.
- 목적은 한계 측정이 아니라 상품 목록/상세/장바구니/체크아웃 기본 부하에서 장애가 나는지 확인하는 것이다.

Tasks:
- [ ] 배포 URL 기준 짧은 k6 smoke 시나리오를 만든다.
- [ ] `/products`, `/products/{id}`, `/cart`, `/checkout` 응답 시간과 오류율을 확인한다.
- [ ] 부하 기준과 VM 업그레이드 판단선을 문서에 남긴다.

### B-038 실오픈 전 성능/보안 baseline 점검

Status: Todo

Notes:
- 테스트 배포와 HTTPS 연결 후 진행한다.
- Lighthouse와 OWASP ZAP baseline만 우선 사용한다.

Tasks:
- [ ] Lighthouse 모바일 성능/접근성 baseline을 확인한다.
- [ ] 공개 페이지 ZAP baseline scan을 실행한다.
- [ ] 실결제 오픈 전 차단 수준의 보안/정책 이슈를 정리한다.

## Working Rules

- 작업 시작 전 `Now`에서 하나를 고른다.
- 작은 버그와 문구 수정은 backlog 항목을 만들지 않아도 된다.
- 큰 기능, 정책, 결제, 주문 상태, DB 변경은 backlog에 남긴다.
- 각 backlog 항목의 하위 task/checklist는 같은 항목 아래 `Tasks:`로 관리한다.
- 완료하면 `docs/BACKLOG_DONE.md`로 옮기고 git commit으로 이력을 남긴다.
- PR과 외부 이슈는 팀 협업, 배포 전 리뷰, 큰 리스크 변경에만 사용한다.
- PROJECT_LOG는 관련 backlog ID로 연결한다.
