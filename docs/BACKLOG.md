# Backlog

이 파일이 현재 작업 큐의 기준이다. 혼자 개발하는 동안 Linear와 GitHub Issues는 기본으로 사용하지 않는다.

## Now

### B-030 출시 전 법적/소비자 고지 정리

Status: In Progress

Notes:
- 법률 자문 대체가 아니라 개발/운영 출시 차단 체크리스트로 관리한다.
- 고객 결제 경로는 계좌입금과 관리자 입금확인으로 확정했으며 PG 결제는 도입하지 않는다.
- 기준 문서는 `docs/legal-launch-checklist.md`다.
- 공개 사업자/정책 정보의 실제 데이터 소스는 `apps/web/src/lib/legal.ts`다.

Tasks:
- [x] 사업자/정책/고객센터/구매안전 출시 차단 체크리스트를 만든다.
- [x] 정책 페이지의 고객 노출 `MVP` 표현을 제거한다.
- [x] 회사 정보에 공정위 통신판매사업자 등록현황 안내 링크를 추가한다.
- [x] 상품 등록 기준에 안전용품 인증/상품정보제공고시 체크를 추가한다.
- [x] 간이과세자 통신판매업 신고 면제 상태를 고객 화면에 반영한다.
- [x] 고객센터 전화번호, 이메일, 운영 시간을 실제 값으로 교체한다.
- [x] 호스팅 제공자를 Amazon Web Services로 표시한다.
- [ ] 계좌입금 구매안전서비스 방식을 확정하고 실제 안내 문구로 교체한다.
- [x] 현금영수증 발급 절차와 담당자를 홈택스 수동 발급으로 정하고 고객 안내에 반영한다.
- [ ] 사업자 업종의 현금영수증 의무발행 해당 여부와 홈택스 가맹/발급 권한을 실제 계정에서 확인한다.
- [ ] 간이과세자 세금계산서 발급 가능 여부를 확인하고 사업자 고객 안내를 정한다.
- [ ] 개인정보 처리 위탁처와 제3자 제공 현황을 실제 운영 기준으로 확정한다.
- [ ] 공개 취소/환불 정책에서 미사용 PG 문구를 제거하고 계좌입금 수동 환불 기준만 남긴다. B-067에서 처리한다.
- [ ] `prelaunch-*` 정책 버전과 시행일을 실제 오픈 버전으로 확정한다.
- [ ] 초기 판매 상품별 안전인증/KC/상품정보제공고시 항목을 확인한다.

### B-026 초기 판매 상품 데이터 준비

Status: In Progress

Notes:
- 실제 판매 상품은 `docs/product-registration-guide.md` 기준으로 검수한다.
- 브라우저 crop UI와 CSV/엑셀 일괄 등록은 현재 범위에서 제외한다.

Tasks:
- [x] 대표 이미지와 상세 이미지 권장 규격을 정한다.
- [x] 관리자 상품 등록/상세 콘텐츠 화면에 이미지 규격 안내를 추가한다.
- [x] 상품 등록 기준 문서를 만든다.
- [ ] 초기 판매 상품 10~20개를 가격·옵션·이미지·고시·인증까지 검수한다.
- [ ] 검수 완료 상품만 `ACTIVE`로 전환한다.
- [ ] 고객 상품 목록, 상품 상세, 장바구니에서 실제 상품 노출을 확인한다.

### B-002 소셜 로그인 실브라우저 검증

Status: Todo

Notes:
- 고객 회원 유형은 구분하지 않는다. 소셜 로그인 사용자는 모두 같은 고객 흐름을 사용한다.
- 고객 사업자 프로필은 현재 범위에서 제외한다.

Tasks:
- [ ] Google 실계정 로그인, callback, session cookie를 브라우저에서 확인한다.
- [ ] Kakao 실계정 로그인, callback, session cookie를 브라우저에서 확인한다.
- [ ] Naver 실계정 로그인, callback, session cookie를 브라우저에서 확인한다.
- [ ] 로그인 후 `/account`에서 현재 사용자 정보와 필수정보 입력 흐름을 확인한다.
- [ ] provider 취소/거절과 callback 실패가 빈 화면이 아닌 오류 흐름으로 보이는지 확인한다.

### B-061 고객 문의 운영과 개인정보 동의 보강

Status: In Progress

Notes:
- 현재 `/support` 접수와 관리자 목록 조회는 가능하지만, 문의 처리 상태·답변·담당 메모가 없어 운영 흐름이 끝나지 않는다.
- 비로그인 문의의 개인정보 동의는 목적, 항목, 보유 기간, 거부권을 명시하고 동의 증적을 남겨야 한다.
- CAPTCHA는 바로 도입하지 않고 Cloudflare 또는 애플리케이션 rate limit부터 적용한다.

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
- [ ] SES 도메인/DKIM 인증과 production access 승인 후 실제 고객 이메일 발송을 확인한다.

### B-059 운영 보안 마감

Status: In Progress

Notes:
- 80/443은 Cloudflare 공개 IPv4 대역으로 제한했고 DB/API 포트는 외부에 노출하지 않는다.
- 남은 핵심은 root/장기 access key 제거, EC2 정적 자격증명 축소, OS와 웹 의존성 보안 패치다.

Tasks:
- [x] 80/443 인바운드를 Cloudflare IP 대역으로 제한하고 직접 origin 접속 차단을 확인한다.
- [x] 미사용 보안그룹을 삭제한다.
- [ ] root 액세스 키의 마지막 사용을 확인하고 삭제한다.
- [ ] `cli-user`의 장기 `AdministratorAccess` 키를 로테이션하고 사람/배포 권한을 필요한 범위로 줄인다.
- [ ] EC2에 S3 백업 전용 instance role을 연결하고 `/root/.aws`의 정적 백업 키를 제거한다.
- [ ] EC2 `unattended-upgrades` 활성 여부를 확인한다.
- [ ] SSH 22 공개를 SSM Session Manager로 전환할지 B-052와 함께 결정한다.
- [ ] Next.js/PostCSS moderate advisory가 해소되는 패치 버전으로 갱신하고 `npm audit`를 다시 확인한다.

### B-033 상품 원가/판매가/마진 정책 관리

Status: In Progress

Notes:
- 공급처 원가와 고객 판매가를 분리한다.
- 기본 판매가는 공급가 25% 증액 후 100원 단위 반올림으로 계산한다.
- 정산, 세금 신고, 공급처별 마진율은 현재 범위에서 제외한다.

Tasks:
- [x] `products.source_price`와 active pricing policy schema를 추가한다.
- [x] 관리자 상품 API에 `sourcePrice`를 추가하고 공개 API에는 노출하지 않는다.
- [x] 관리자 가격 정책 화면을 추가한다.
- [x] 관리자 상품 상세에서 계산 판매가를 적용할 수 있게 한다.
- [x] 상품 import가 원가와 계산 판매가를 분리해 적재하게 한다.
- [ ] 기존 등록 상품의 원가, 배송비 포함 원가, 판매가, 마진을 운영자가 검수한다.

## Next

### B-067 Toss Payments 미사용 코드와 문서 제거

Status: Todo

Notes:
- 고객 결제 수단은 계좌입금과 관리자 입금확인으로 확정했다.
- 과거 결제·환불 데이터와 enum 호환성은 보존하되 실행 가능한 Toss 경로만 제거한다.

Tasks:
- [ ] 고객용 Toss confirm/fail 화면과 Web helper를 제거한다.
- [ ] Toss confirm/webhook API, REST client, 결제 예외·환불 PG 호출 경로를 제거한다.
- [ ] 운영 env와 배포 설정에서 Toss key를 제거한다.
- [ ] 공개 정책과 개인정보 처리 항목에서 미사용 PG/Toss 문구를 제거한다.
- [ ] `docs/api-spec.md`, `docs/order-flow.md`, `docs/architecture.md`, API README를 계좌입금 전용 계약으로 정리한다.
- [ ] 기존 계좌입금 주문·취소·수동 환불 회귀 테스트를 통과시킨다.

### B-065 관리자 주문 목록 서버 페이징과 필터

Status: Todo

Notes:
- 상품 검수와 별개인 주문 운영 성능 작업으로 분리한다.

Tasks:
- [ ] 관리자 주문 목록 API를 서버 측 페이징과 상태 필터로 전환한다.
- [ ] 기존 주문 상세 query와 관리자 액션 동선을 유지한다.
- [ ] API 회귀 테스트와 Playwright 관리자 주문 smoke를 갱신한다.

### B-062 최소 운영 모니터링과 비용 알림

Status: Todo

Notes:
- 서버는 현재 비용 절감을 위해 정지된 상태다. 다시 시작하기 전에 최소 알림을 구성한다.
- 새 관측 플랫폼은 도입하지 않고 AWS 기본 기능과 기존 container 상태 확인으로 시작한다.

Tasks:
- [ ] 기존 AWS Budget 한도를 월 50달러에서 15달러로 조정하고 이메일 알림을 확인한다.
- [ ] EC2 status check 실패와 CPU credit 부족 CloudWatch alarm을 추가한다.
- [ ] 메모리, swap, Docker container 재시작 횟수를 확인할 최소 수집/알림 방식을 정한다.
- [ ] 마지막 S3 DB 백업 시각과 백업 실패를 감지하는 알림을 추가한다.
- [ ] 장애 알림 수신 후 확인할 명령과 대응 순서를 운영 문서에 남긴다.

### B-013 출시 전 모바일/디자인 QA 정리

Status: Todo

Notes:
- B-026 실제 상품, B-056 공개 차단, B-064 검수 흐름, B-061 문의 화면이 준비된 뒤 최종 스크린샷 QA를 실행한다.

Tasks:
- [ ] 모바일 header wrapping을 확인한다.
- [ ] 상품 카드 밀도와 이미지 비율을 모바일/데스크톱에서 확인한다.
- [ ] checkout form이 작은 화면에서 겹치지 않는지 확인한다.
- [ ] 로그인/계정/주문/관리자 주요 화면의 빈 상태와 오류 상태를 점검한다.
- [ ] 공개 정책/회사/고객문의 화면의 모바일 overflow와 문구를 확인한다.

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

## Later

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

### B-008 검색/필터 고도화

Status: Todo

Tasks:
- [ ] 고객 상품 목록 검색 기준을 상품명/요약/카테고리 중심으로 정리한다.
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
- [ ] 필요 시 role/permission 모델을 설계한다.

## Working Rules

- 작업 시작 전 `Now`에서 하나를 고른다.
- 작은 버그와 문구 수정은 backlog 항목을 만들지 않아도 된다.
- 큰 기능, 정책, 결제, 주문 상태, DB 변경은 backlog에 남긴다.
- 각 backlog 항목의 하위 task/checklist는 같은 항목 아래 `Tasks:`로 관리한다.
- 완료하면 `docs/BACKLOG_DONE.md`로 옮기고 git commit으로 이력을 남긴다.
- PR과 외부 이슈는 팀 협업, 배포 전 리뷰, 큰 리스크 변경에만 사용한다.
- `docs/PROJECT_LOG.md`는 관련 backlog ID로 연결한다.
