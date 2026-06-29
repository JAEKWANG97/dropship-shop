# Project Log

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
