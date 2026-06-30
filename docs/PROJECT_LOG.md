# Project Log

## 2026-06-29 23:24 KST

- 관련 항목: B-004
- 작업: 관리자 상품 등록 화면에서 대표 이미지 파일 업로드를 기존 admin product image upload API와 연결했다.
- 문제·고민: 백엔드 업로드 API와 이미지 metadata 저장 API는 이미 있으므로, 새 이미지 저장소나 프론트 미리보기 상태를 추가하면 범위가 커진다.
- 해결방안: 상품 생성 후 파일이 있을 때만 multipart upload를 실행하고, 반환된 imageUrl을 기존 이미지 metadata API에 THUMBNAIL로 저장했다. 상품 목록은 기존 ProductImage 컴포넌트를 재사용해 대표 이미지 미리보기를 표시한다.
- 결정: B-004는 상품 등록 시 대표 이미지 업로드까지 완료로 보고, 갤러리 다중 이미지·수정 화면·정렬 UI는 상세 블록/상품 관리 후속 작업에서 다룬다.
- 후속작업: 실제 브라우저에서 jpg/png/webp 업로드와 5MB 초과/미지원 확장자 실패 메시지를 확인한다.

## 2026-06-29 22:16 KST

- 관련 항목: B-017
- 작업: 로그인 후 고객 필수 정보와 SMS OTP 휴대폰 번호 인증 1차 구현을 진행했다.
- 문제·고민: 사업자회원/사업자 프로필은 필요 없지만, 실제 운영에서는 주문·배송·클레임 연락 가능한 고객 정보가 필요하다. NICE/PASS 본인확인은 비용과 개인정보 부담이 커서 현재 쇼핑몰 목적에는 과하다.
- 해결방안: 고객 회원 유형은 하나로 유지하고, 필수 정보는 이름, 연락 가능한 이메일, 인증된 휴대폰 번호로 제한했다. 휴대폰 번호는 SMS OTP로 소유 확인하고, 인증번호는 hash 저장, 5분 만료, 재발송 제한, 시도 제한을 적용했다.
- 결정: MVP는 SMS OTP 번호 인증으로 진행하고, CI/DI 기반 본인확인은 성인인증, 중복가입 방지, 실명확인이 필요해질 때 검토한다.
- 후속작업: 실제 운영 SMS provider 설정과 구현을 연결하고, 개인정보 수집/보관 고지와 운영 env 목록을 정리한다.

## 2026-06-29 16:24 KST

- 관련 항목: B-001
- 작업: Toss Payments 연동 진행 기준을 테스트 키 우선 개발로 정리했다.
- 문제·고민: live PG 심사에는 홈페이지 주소와 사업자/정책 정보가 필요하지만, 현재는 배포 전이라 실운영 연동을 완료할 수 없다.
- 해결방안: 로컬/스테이징은 Toss Payments test client key와 test secret key로 결제창과 서버 confirm을 검증하고, live 전환은 배포 URL 확보 이후로 미룬다.
- 결정: 현재 개발은 테스트 키 기준으로 진행하며 test/live key 모두 커밋하지 않는다.
- 후속작업: 테스트 키를 로컬 env에 넣은 뒤 sandbox 결제창, 성공 redirect, 서버 승인, 실패/예외 화면을 실제 브라우저에서 확인한다.

## 2026-06-29 16:28 KST

- 작업: 서비스명을 `코어블SAF`로 확정하고 고객-facing 브랜드 표기를 갱신했다.
- 문제·고민: 기존 화면에는 임시명 `SafeHub Pro`가 남아 있었고, 저장소명까지 바꾸면 불필요한 변경 범위가 커진다.
- 해결방안: 웹 레이아웃과 제품/결정 문서의 서비스명만 교체하고 기술 식별자는 유지했다.
- 결정: 고객에게 노출되는 서비스명은 `코어블SAF`로 한다.
- 후속작업: 이후 디자인/배포/PG 심사 문서에는 `코어블SAF` 명칭을 사용한다.

## 2026-06-29 17:46 KST

- 관련 항목: B-001
- 작업: Toss Payments 연동 상태와 작업 관리 기준을 다시 정리했다.
- 문제·고민: Toss Payments live 승인이 아직 나지 않아 live key 또는 실제 운영 결제로는 검증할 수 없다.
- 해결방안: 승인 전까지는 Toss Payments test/sandbox key만 사용해 결제창, success redirect, backend confirm, 실패/예외 화면을 검증한다.
- 결정: live 승인이 완료되기 전에는 테스트 키 기준으로만 개발하고, live key 전환은 별도 배포/PG 승인 작업으로 미룬다.
- 후속작업: Toss test client key와 test secret key를 로컬 env에 넣은 뒤 `docs/BACKLOG.md`의 Toss sandbox 결제 플로우 항목을 이어서 검증한다.

## 2026-06-29 17:54 KST

- 관련 항목: WORKFLOW
- 작업: markdown 기반 작업 관리 문서의 연결 방식을 정했다.
- 문제·고민: `BACKLOG`, `TODO`, `PROJECT_LOG`가 따로 관리되면 큰 작업, 하위 체크리스트, 결정 기록이 분리되어 추적이 어려워진다.
- 해결방안: backlog 작업에 `B-001` 같은 ID를 붙이고, TODO 섹션과 PROJECT_LOG의 `관련 항목`이 같은 ID를 참조하도록 한다.
- 결정: `BACKLOG = 큰 작업`, `TODO = 하위 task/checklist`, `PROJECT_LOG = 결정 이유와 작업 맥락`으로 사용한다.
- 후속작업: 새 goal을 시작할 때 관련 backlog ID를 먼저 확인하고, 필요한 TODO와 PROJECT_LOG를 같은 ID로 갱신한다.

## 2026-06-29 17:57 KST

- 관련 항목: WORKFLOW
- 작업: 현재 남은 제품/운영 작업을 backlog story와 TODO checklist로 재정리했다.
- 문제·고민: backlog에는 큰 항목만 있고 TODO에는 Toss 결제 항목만 있어, 다음 작업을 고를 때 세부 실행 단위가 부족했다.
- 해결방안: 출시 전 필요한 인증, 결제, 관리자 주문 처리, 상품 관리, 배송, 법적 고지, 모바일 QA, 배포 readiness 항목을 `B-###` 기준으로 나눴다.
- 결정: 당장 개발할 큰 작업은 `docs/BACKLOG.md`, 각 작업의 하위 실행 항목은 `docs/TODO.md`에서 관리한다.
- 후속작업: 다음 goal을 시작할 때 `docs/BACKLOG.md`의 `Now` 항목 중 하나를 선택하고, 해당 `B-###`의 TODO를 완료 기준으로 사용한다.

## 2026-06-29 18:01 KST

- 관련 항목: WORKFLOW
- 작업: `BACKLOG`와 `TODO`를 한 파일로 합쳤다.
- 문제·고민: 혼자 개발하는 상황에서 backlog와 TODO를 분리하면 story와 하위 task를 계속 왕복해야 하고, goal 시작 시 읽을 문서가 늘어난다.
- 해결방안: `docs/TODO.md`를 제거하고 `docs/BACKLOG.md`의 각 `B-###` 항목 아래 `Tasks:` checklist를 둔다.
- 결정: 작업 관리는 `docs/BACKLOG.md`, 결정 이유와 맥락 기록은 `docs/PROJECT_LOG.md`로 단순화한다.
- 후속작업: 새 goal은 관련 `B-###`의 `Tasks:`를 완료 기준으로 사용한다.
