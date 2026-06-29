# Project Log

## 2026-06-29 16:24 KST

- 작업: Toss Payments 연동 진행 기준을 테스트 키 우선 개발로 정리했다.
- 문제·고민: live PG 심사에는 홈페이지 주소와 사업자/정책 정보가 필요하지만, 현재는 배포 전이라 실운영 연동을 완료할 수 없다.
- 해결방안: 로컬/스테이징은 Toss Payments test client key와 test secret key로 결제창과 서버 confirm을 검증하고, live 전환은 배포 URL 확보 이후로 미룬다.
- 결정: 현재 개발은 테스트 키 기준으로 진행하며 test/live key 모두 커밋하지 않는다.
- 후속작업: 테스트 키를 로컬 env에 넣은 뒤 sandbox 결제창, 성공 redirect, 서버 승인, 실패/예외 화면을 실제 브라우저에서 확인한다.
