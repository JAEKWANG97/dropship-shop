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
- [ ] EC2 메모리·swap·CPU credit·컨테이너 재시작 상태 확인
