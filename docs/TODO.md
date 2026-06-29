# TODO

이 파일은 작은 확인사항, 수동 검증, 운영 전 체크리스트를 관리한다.

## B-001 Toss Payments sandbox 결제 플로우 완성

- [ ] Toss test client key를 로컬 env에 설정한다.
- [ ] Toss test secret key를 로컬 env에 설정한다.
- [ ] Toss sandbox 결제창에서 성공 결제를 실행한다.
- [ ] Success redirect 후 backend confirm 호출을 확인한다.
- [ ] 주문 상태가 `SUPPLIER_ORDER_PENDING`으로 바뀌는지 확인한다.
- [ ] Toss 실패/취소 redirect 화면을 확인한다.

## Deployment

- [ ] 배포 URL 확보 후 Toss live 심사 준비
- [ ] Toss live key 전환은 별도 작업으로 진행
