# Backlog

이 파일이 현재 작업 큐의 기준이다. 혼자 개발하는 동안 Linear와 GitHub Issues는 기본으로 사용하지 않는다.

## Now

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

### B-002 소셜 로그인 실브라우저 검증 및 사업자 프로필 입력/조회 흐름 만들기

Status: Todo

Tasks:
- [ ] Google 실계정 로그인, callback, session cookie를 브라우저에서 확인한다.
- [ ] Kakao 실계정 로그인, callback, session cookie를 브라우저에서 확인한다.
- [ ] Naver 실계정 로그인, callback, session cookie를 브라우저에서 확인한다.
- [ ] 로그인 후 `/account`에서 현재 사용자 정보가 보이는지 확인한다.
- [ ] 사업자 프로필 필수 필드를 확정한다.
- [ ] 사업자 프로필 저장/조회 API를 설계한다.
- [ ] 고객 화면에서 사업자 프로필 입력/수정 흐름을 구현한다.
- [ ] 주문/결제 전 사업자 프로필 필요 여부를 정책 문서에 반영한다.

### B-003 관리자 주문 처리 액션을 실제 운영 흐름에 연결하기

Status: Todo

Tasks:
- [ ] 관리자 주문 상세에서 현재 주문/결제/배송/환불 상태를 확인할 수 있게 한다.
- [ ] 발주 시작 액션을 실제 API에 연결하고 사유 입력을 받는다.
- [ ] 공급처 발주 완료 액션을 실제 API에 연결하고 발주번호/예상출고일을 입력받는다.
- [ ] 공급처 품절 액션을 실제 API에 연결하고 환불 흐름 진입을 확인한다.
- [ ] 송장 입력 액션을 실제 API에 연결한다.
- [ ] 각 액션 성공 후 주문 목록/상세 상태가 갱신되는지 확인한다.
- [ ] 권한/validation/API 실패가 빈 화면이 아니라 오류 안내로 보이는지 확인한다.

## Next

### B-004 상품 이미지 업로드

Status: Todo

Tasks:
- [ ] 관리자 상품 등록/수정 화면에서 이미지 파일 선택 UI를 만든다.
- [ ] 기존 admin product image upload API와 연결한다.
- [ ] 업로드 후 반환 URL/object key를 상품 이미지 metadata에 반영한다.
- [ ] 썸네일/갤러리 이미지 미리보기를 제공한다.
- [ ] 허용 확장자, 파일 크기, 실패 메시지를 확인한다.

### B-005 상품 상세 HTML/이미지 블록 관리

Status: Todo

Tasks:
- [ ] 관리자 화면에서 IMAGE/HTML 상세 블록을 추가/삭제/정렬할 수 있게 한다.
- [ ] HTML 상세 입력과 이미지 상세 입력을 구분한다.
- [ ] 정책/배송/환불/품절 고지는 상품 상세 이미지가 아니라 별도 정책 영역으로 유지한다.
- [ ] 상세 블록 저장 후 고객 상품 상세에서 동일 순서로 노출되는지 확인한다.
- [ ] 상세 변경 이력이 기록되는지 확인한다.

### B-006 상품 옵션/판매 상태 관리 화면 정리

Status: Todo

Tasks:
- [ ] 옵션명, 추가금액, 판매 상태 수정 UI를 만든다.
- [ ] 상품 판매 상태와 옵션 판매 상태가 별도로 보이게 한다.
- [ ] 품절/숨김/판매중지 상태가 고객 상품 목록과 상세에 올바르게 반영되는지 확인한다.
- [ ] 가격 변경 이후 새 주문에만 적용되는지 회귀 테스트를 확인한다.
- [ ] 변경 사유 입력과 변경 이력 조회 위치를 정리한다.

### B-007 송장 입력과 배송조회 운영 화면 연결

Status: Todo

Tasks:
- [ ] 관리자 주문 상세에서 택배사와 송장번호를 입력한다.
- [ ] 송장 입력 후 주문 상태가 `SHIPPED`로 전환되는지 확인한다.
- [ ] 관리자 수동 배송조회 retry 버튼을 연결한다.
- [ ] 내부 배송조회 sync token 설정과 스케줄러 호출 방식을 배포 환경에 맞게 정리한다.
- [ ] 배송조회 실패 사유가 관리자 화면에 보이는지 확인한다.
- [ ] 배송완료 sync 후 주문 상태가 `DELIVERED`로 전환되는지 확인한다.

### B-012 사업자 정보/개인정보 처리표 관리자 설정

Status: Todo

Tasks:
- [ ] 사업자 정보 public API에 노출할 실제 필드를 확인한다.
- [ ] 관리자 사업자 정보 수정 API를 구현한다.
- [ ] 개인정보 처리표 관리자 교체 API를 구현한다.
- [ ] footer/menu에서 사업자 정보와 개인정보 처리표 접근 경로를 확인한다.
- [ ] 실제 사업자등록/통신판매업 신고 정보가 준비되기 전 placeholder 정책을 정한다.

### B-013 출시 전 모바일/디자인 QA 정리

Status: Todo

Tasks:
- [ ] 모바일 header wrapping을 확인한다.
- [ ] 상품 카드 밀도와 이미지 비율을 모바일/데스크톱에서 확인한다.
- [ ] checkout form이 작은 화면에서 겹치지 않는지 확인한다.
- [ ] 로그인/계정/주문/관리자 주요 화면의 빈 상태와 오류 상태를 점검한다.
- [ ] 실제 상품 사진 fixture가 준비되면 local seed 이미지를 교체한다.

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

### B-016 배포 및 운영 readiness 점검

Status: Todo

Tasks:
- [ ] 배포 URL을 확보한다.
- [ ] Toss live 심사에 필요한 홈페이지/정책/사업자 정보 접근 경로를 확인한다.
- [ ] Toss live 승인이 완료되면 live key 전환 작업을 별도 진행한다.
- [ ] production env 변수 목록을 배포 플랫폼에 등록한다.
- [ ] `/api/health`, readiness, liveness를 staging/production에서 확인한다.
- [ ] DB migration dry run과 backup/snapshot 상태를 확인한다.

## Done

- [x] 내부 배송조회 동기화 API 토큰 보호 및 관리자 API 실패 표시
- [x] 카카오 OAuth 로그인 수정
- [x] 관리자 mock operational data 제거
- [x] 로컬 상품 이미지 fixture를 API upload URL 기준으로 정리
- [x] 고객/관리자 로그인 상태별 헤더 정책 정리

## Working Rules

- 작업 시작 전 `Now`에서 하나를 고른다.
- 작은 버그와 문구 수정은 backlog 항목을 만들지 않아도 된다.
- 큰 기능, 정책, 결제, 주문 상태, DB 변경은 backlog에 남긴다.
- 각 backlog 항목의 하위 task/checklist는 같은 항목 아래 `Tasks:`로 관리한다.
- 완료하면 항목을 `Done`으로 옮기고 git commit으로 이력을 남긴다.
- PR과 외부 이슈는 팀 협업, 배포 전 리뷰, 큰 리스크 변경에만 사용한다.
- PROJECT_LOG는 관련 backlog ID로 연결한다.
