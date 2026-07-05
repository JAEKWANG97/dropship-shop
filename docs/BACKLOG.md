# Backlog

이 파일이 현재 작업 큐의 기준이다. 혼자 개발하는 동안 Linear와 GitHub Issues는 기본으로 사용하지 않는다.

## Now

### B-040 GitHub Actions Docker build 최적화

Status: Done

Notes:
- 현재 AWS 테스트 배포 병목은 EC2 pull/up이 아니라 GitHub Actions의 `build-and-push`, 특히 Web ARM64 Docker image build다.
- 기준 실행 시간은 `verify 2m58s`, `build-and-push 7m39s`, `deploy 1m12s`다.
- 1차 최적화는 Docker BuildKit GitHub Actions cache를 사용한다.
- 2026-07-05 `15ab8e9` 배포 기준 실행 시간은 `verify 3m10s`, `build-and-push 5m43s`, `deploy 1m09s`다. Web image cache warm-up 이후 `build-and-push`는 기준 대비 약 1m56s 줄었다.

Tasks:
- [x] API Docker build에 GitHub Actions cache scope를 추가한다.
- [x] Web Docker build에 GitHub Actions cache scope를 추가한다.
- [x] 배포 문서에 build cache 정책과 한계를 기록한다.
- [x] cache warm-up 이후 앱 변경 배포 시간을 비교한다.

### B-059 AWS 계정/서버 보안 정리

Status: In Progress

Notes:
- 2026-07-05 계정 점검 결과: 이 컴퓨터의 CLI는 IAM 사용자(cli-user, AdministratorAccess)이고 root 키가 아니다. root MFA는 켜져 있으나 root 액세스 키가 계정에 존재한다.
- 서버 보안그룹은 80/443/22만 오픈이고 DB(5432)/API(8080)는 비노출로 양호했다.
- 80/443은 Cloudflare 공개 IPv4 대역 15개만 허용하도록 좁혔다. origin IP 직접 접속은 타임아웃으로 차단 확인. Cloudflare 대역 변경 시 재동기화 필요 (https://www.cloudflare.com/ips-v4).
- SSH 22 전세계 오픈(key-only)은 알려진 트레이드오프로 유지하고, SSM 전환은 B-052와 함께 검토한다.

Tasks:
- [x] 80/443 인바운드를 Cloudflare IP 대역으로 제한하고 사이트/직접 IP 접속을 검증한다.
- [ ] root 액세스 키를 삭제한다 (콘솔에서 root 로그인 필요, 운영자 진행. 삭제 전 "마지막 사용" 확인).
- [x] 미사용 보안그룹 5개를 삭제한다: RDP_Access, soup_backend, launch-wizard-1, swieogage-security-group, ssh_web_access. 2026-07-05 삭제 완료, 남은 보안그룹은 default와 coreable-saf-test-sg뿐.
- [ ] EC2 unattended-upgrades(OS 자동 보안 패치) 활성 여부를 확인한다.
- [ ] cli-user 액세스 키를 로테이션한다.
- [ ] SSH를 SSM Session Manager로 전환할지 B-052에서 결정한다.

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

### B-042 즉시 보안/운영 핫픽스

Status: Done

Notes:
- 외부 리뷰에서 확인된 작지만 위험도가 큰 항목을 먼저 닫는다.
- 계좌입금 전환으로 Toss 환불 재시도 위험은 당장 고객 돈 이중 환불로 이어지지 않지만, Toss 코드를 유지하는 동안에는 같은 멱등성 원칙을 맞춘다.

Tasks:
- [x] OAuth/login `redirectTo` 검증에서 백슬래시(`\`)를 차단한다.
- [x] Toss 환불 retry가 새 idempotency key를 만들지 않고 저장된 동일 key를 재사용하게 한다.
- [x] `SMS_SENS_ENABLED` 운영 기본값을 안전하게 `false`로 바꾸고 문서를 맞춘다.
- [x] GitHub Actions deploy workflow에 concurrency를 추가한다.
- [x] 배포 스크립트 말미에 오래된 Docker image prune을 추가한다.
- [x] EC2 compose에 API/Web/Postgres memory 제한과 API JVM heap 옵션을 둔다.

## Next

### B-043 체크아웃 중복 제출 및 주문 상태 경합 방지

Status: Done

Notes:
- Toss 실시간 결제는 보류하지만, 계좌입금에서도 더블클릭/동시 요청으로 중복 입금대기 주문이 생기면 CS와 입금확인이 꼬인다.
- 주문 상태 전이 전반이 read-check-write 방식에 낙관적 잠금이 없어, 고객 취소 vs 관리자 입금확인/발주 같은 동시 액션에서 lost update가 가능하다.

Tasks:
- [x] 같은 고객 장바구니 기준 동시 checkout 생성 경합을 막는 방식을 정한다.
- [x] 장바구니 잠금 또는 활성 checkout unique 제약을 구현한다.
- [x] 중복 제출 시 기존 입금대기 주문으로 안내하거나 명확한 오류를 반환한다.
- [x] `CustomerOrder`, `PaymentGroup` 등 핵심 엔티티에 `@Version` 낙관적 잠금을 도입하고 충돌 시 오류 응답/재시도 규칙을 정한다.
- [x] 동시 checkout 요청과 고객 취소 vs 관리자 액션 동시 실행 회귀 테스트를 추가한다.

### B-044 배송 후 반품/환불 플로우 완성

Status: Done

Notes:
- 배송완료 후 청약철회/반품이 들어왔을 때 현재는 `RETURN_WAITING` 이후 환불 완료까지의 운영 흐름이 부족하다.
- 계좌입금 MVP에서는 PG 취소가 아니라 관리자 수동 환불 기록과 주문/클레임 상태 전이를 중심으로 처리한다.

Tasks:
- [x] `DELIVERED` 이후 반품 요청, 승인, 반품대기, 반품수령, 환불처리, 완료 상태 전이를 정리한다.
- [x] 관리자 반품 수령과 수동 환불 완료 액션을 추가한다.
- [x] 고객 주문 상세에서 반품/환불 처리 상태를 확인할 수 있게 한다.
- [x] 취소/환불 정책 문서와 API 문서를 함께 갱신한다.
- [x] 배송완료 후 반품 환불 회귀 테스트를 추가한다.

### B-013 출시 전 모바일/디자인 QA 정리

Status: Todo

Tasks:
- [ ] 모바일 header wrapping을 확인한다.
- [ ] 상품 카드 밀도와 이미지 비율을 모바일/데스크톱에서 확인한다.
- [ ] checkout form이 작은 화면에서 겹치지 않는지 확인한다.
- [ ] 로그인/계정/주문/관리자 주요 화면의 빈 상태와 오류 상태를 점검한다.
- [ ] 실제 상품 사진 fixture가 준비되면 local seed 이미지를 교체한다.

### B-056 관리자 상품 검수 워크플로우와 목록 페이징

Status: Todo

Notes:
- 도매꾹 import로 HIDDEN 상품이 대량으로 들어오는 상황에서, 운영자가 검수 후 ACTIVE 전환하는 흐름을 관리자 화면에서 끝낼 수 있게 한다.
- 상품/주문 목록이 전체 로드 후 웹 필터 방식이라 상품 수백 개 기준으로 서버 측 페이징/필터로 전환한다.
- 검수 상태는 별도 컬럼 없이 기존 ProductStatus를 재활용한다: HIDDEN=검수 대기, ACTIVE=판매, STOPPED=판매 안 함.
- **검수 정책은 운영자와 결정 후 진행 (2주 후 미팅, 아래 질문지)**. 결정 전에는 페이징/정보 노출/원본 링크 저장 등 정책 무관 부분만 진행 가능하다.
- 운영자 결정 필요 질문:
  1. 첫 배치(HIDDEN 127개)를 전부 직접 볼 것인가, 시스템이 위험 플래그(인증 대상인데 인증정보 없음, 고시 미입력, 마진 이상치, 이미지 부족)를 붙인 것만 볼 것인가?
  2. 플래그 없는 상품(PASS)의 일괄 판매 시작을 허용할 것인가?
  3. 마진율이 몇 % 아래면 확인 대상으로 볼 것인가?
  4. "판매 안 함"으로 뺀 상품을 나중에 다시 살릴 필요가 있는가?

Tasks:
- [ ] 관리자 상품 목록을 서버 측 페이징/상태·카테고리·공급처 필터로 전환한다.
- [ ] products.source_url을 추가하고 import 스크립트가 채우게 한다.
- [ ] 상품 목록/상세에 검수 핵심 정보를 노출한다: 원가/판매가/마진, 도매꾹 원본 링크, 옵션 수, 고시/이미지 입력 여부.
- [ ] 관리자 주문 목록도 같은 페이징 방식으로 맞춘다.
- [ ] 회귀 테스트와 Playwright smoke를 갱신한다.
- [ ] (운영자 결정 후) 검수 플래그 계산과 전환 동선(개별/일괄)을 구현한다.

### B-057 관리자 회원 관리와 알림 발송 이력

Status: Todo

Notes:
- CS 대응의 기본 조회 수단이 없다. 고객 이름/이메일/전화로 계정을 찾고 주문 이력을 보는 화면이 필요하다.
- SMS 발송 로그(SENT/FAILED/SKIPPED)와 실패 재발송 API는 B-011에서 만들었지만 화면이 없어 실패를 눈으로 확인할 수 없다.
- 개인정보 표시는 CS에 필요한 최소 범위로 하고, 탈퇴/비식별화 계정은 구분 표시한다.

Tasks:
- [ ] 관리자 회원 목록/검색(이름, 이메일, 전화, 추천 코드)과 상세(주문 이력, 가입일, 상태)를 추가한다.
- [ ] 관리자 알림 발송 이력 화면을 추가하고 실패 건 재발송 버튼을 기존 retry API에 연결한다.
- [ ] 회원 상세와 주문 상세를 상호 링크한다.
- [ ] 접근 권한(ADMIN)과 개인정보 노출 범위를 검토한다.
- [ ] 회귀 테스트를 추가한다.

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
- 사업자 정보는 `apps/web/src/lib/legal.ts`의 `BUSINESS_PROFILE` 상수가 실제 화면 데이터 소스다. 신고번호가 나오면 이 상수의 `준비중` 값을 수정해 배포한다. 관리자 화면이나 DB 입력은 만들지 않는다.
- backend의 `business_profiles` 테이블과 legal disclosure API는 웹이 사용하지 않는 미사용 경로다. 정리 여부는 후속에서 판단한다.

Tasks:
- [x] 사업자/정책/고객센터/구매안전 출시 차단 체크리스트를 만든다.
- [x] 정책 페이지의 고객 노출 `MVP` 표현을 제거한다.
- [x] 회사 정보에 공정위 통신판매사업자 등록현황 안내 링크를 추가한다.
- [x] 상품 등록 기준에 안전용품 인증/상품정보제공고시 체크를 추가한다.
- [x] 간이과세자 통신판매업 신고 면제 대상 여부를 확인한다. 2026-07-05 간이과세자 확인됨 → 신고 의무 면제.
- [x] 신고번호 표기를 "통신판매업 신고 면제 사업자(간이과세자)"로 교체한다 (`apps/web/src/lib/legal.ts`). 토스 가맹이 신고증을 요구해 자진 신고하게 되면 실제 번호로 재교체한다.
- [ ] 간이과세자 세금계산서 발급 가능 여부(매출 구간)를 확인하고 법인/사업자 고객용 안내 문구를 정책 페이지에 추가한다.
- [x] 고객센터 전화번호, 이메일, 운영 시간을 실제 값으로 교체한다. 010-8277-7369, contact@coreable-saf.com(Cloudflare Email Routing → Gmail 포워딩), 평일 10:00-18:00.
- [ ] 호스팅 제공자와 계좌입금/구매안전서비스 정보를 실제 값으로 교체한다.
- [ ] 통신판매업 신고에 필요한 구매안전서비스 이용확인증 확보 방법을 확인한다. 토스페이먼츠 재도입 확정(B-001)에 따라 토스 가맹 신청 시 발급되는 이용확인증 경로를 우선 확인하고, 안 되면 은행 에스크로로 진행한다.
- [ ] 현금영수증 발급 준비 상태를 출시 차단 체크리스트에 추가한다.
- [ ] 초기 판매 상품별 안전인증/KC/상품정보제공고시 항목을 확인한다.

### B-033 상품 원가/판매가/마진 정책 관리

Status: In Progress

Notes:
- 공급처 원가와 고객 판매가를 분리한다.
- 기본 판매가는 공급가 25% 증액 후 100원 단위 반올림으로 계산한다.
- 정산, 세금 신고, 공급처별 마진율은 이번 범위에서 제외한다.

Tasks:
- [x] `products.source_price`와 active pricing policy schema를 추가한다.
- [x] 관리자 상품 API에 `sourcePrice`를 추가하고 공개 API에는 노출하지 않는다.
- [x] 관리자 가격 정책 화면을 추가한다.
- [x] 관리자 상품 상세에서 계산 판매가를 적용할 수 있게 한다.
- [x] 도매꾹 import가 원가와 계산 판매가를 분리해 적재하게 한다.
- [ ] 기존 등록 상품의 원가/판매가를 운영자가 검수한다.

## Later

### B-001 Toss Payments sandbox 결제 플로우 완성

Status: Deferred

Notes:
- `B-001`은 기존 Toss Payments 결제 플로우 이슈 번호로 보존한다. 다른 결제 방식 작업에 재사용하지 않는다.
- 2026-07-04 재도입 확정. 실행 시점은 상품 등록, 통신판매업 신고, 계좌이체 기준 오픈 이후 맨 마지막으로 한다.
- 연동 코드는 마지막이지만 토스 가맹 신청 자체는 먼저 진행한다. 가맹 시 발급되는 구매안전서비스 이용확인증으로 통신판매업 신고를 처리하면 은행 에스크로 절차를 대체할 수 있다.
- 계좌이체 플로우(B-041)는 제거하지 않고 토스 연동 후에도 결제 수단 선택지로 유지한다.
- 웹 결제창 호출과 성공 리다이렉트 서버 confirm 연결 코드는 남아 있지만 고객 주 경로에서는 우선 제외한다.

Tasks:
- [ ] 카드/간편결제/PG 계좌이체/가상계좌 중 1차 재도입 범위를 정한다.
- [ ] 토스페이먼츠 가맹 신청과 구매안전서비스 이용확인증 발급을 확인한다. 간이과세자 통신판매업 신고 면제 사업자로 가맹 가능한지 먼저 문의한다. (연동 코드보다 먼저, 운영자 진행)
- [ ] Toss test client key를 로컬 env에 설정한다.
- [ ] Toss test secret key를 로컬 env에 설정한다.
- [ ] Toss sandbox 결제창에서 성공 결제를 실행한다.
- [ ] Success redirect 후 backend confirm 호출을 확인한다.
- [ ] 주문 상태가 `SUPPLIER_ORDER_PENDING`으로 바뀌는지 확인한다.
- [ ] Toss 실패/취소 redirect 화면을 확인한다.
- [ ] 중복 success redirect 또는 중복 confirm 요청이 같은 결제 결과를 반환하는지 확인한다.
- [ ] 결제 예외 발생 시 고객 화면과 관리자 payment exception queue를 함께 확인한다.

### B-052 무중단 배포 도입

Status: Todo

Notes:
- 현재 배포는 compose 컨테이너 교체 방식이라 배포마다 API가 30초~1분 중단된다. 실주문이 흐르기 시작하면 도입한다.
- t4g.micro(1GB)에서는 전환 기간 동안 JVM 2개를 못 띄우므로 t4g.small 이상 업그레이드가 전제다.
- 방식은 기존 nginx를 활용한 같은 호스트 blue-green으로 한다. green 기동 → 헬스체크 → nginx upstream 전환 → blue 종료. 오케스트레이션 도구(k8s/ECS) 도입은 하지 않는다.
- 구버전/신버전 공존을 위해 DB 마이그레이션은 expand-contract(추가 → 전환 → 제거) 순서를 지킨다. 이 규칙은 도입 전부터 적용한다.

Tasks:
- [ ] 인스턴스 업그레이드 시점과 비용을 확정한다.
- [ ] compose에 API blue/green 서비스 구성과 nginx upstream 전환 스크립트를 추가한다.
- [ ] deploy workflow를 헬스체크 게이트 기반 전환으로 바꾼다.
- [ ] 전환 실패 시 rollback(기존 컨테이너 유지) 동작을 확인한다.
- [ ] 마이그레이션 expand-contract 규칙을 배포 문서에 기록한다.

### B-055 인프라 비용 최적화 (업그레이드 후 약정)

Status: Todo

Notes:
- 2026-07-05 smoke에서 t4g.micro의 available memory 271MB, swap 201MB 사용을 확인했다. 즉시 장애는 아니지만 오픈 전 t4g.small(2GB) 업그레이드를 전제로 한다.
- Lightsail 이전은 하지 않는다. 월 5천원 수준 절감 대비 EC2 기준으로 만든 배포/백업/보안그룹/문서 재구축 비용이 크다.
- 절감 수단은 이전이 아니라 약정이다. 사이즈 확정 후 1년 Compute Savings Plan(선결제 없음)으로 30~40% 할인해 Lightsail 수준 가격을 만든다.

Tasks:
- [ ] 오픈 직전 t4g.small로 업그레이드한다 (중지 → 타입 변경 → 시작, Elastic IP 유지, B-052 전제 조건).
- [ ] AWS Budgets 월 예산 알람을 설정한다.
- [ ] 오픈 후 1~2달 운영으로 t4g.small 사이즈가 맞는지 확인한다.
- [ ] 사이즈 확정 후 1년 Compute Savings Plan을 적용한다.

### B-008 검색/필터 고도화

Status: Todo

Notes:
- 관리자 상품/주문 목록의 페이징·필터는 B-056에서 먼저 처리한다. 이 항목은 고객 목록 검색 고도화 중심으로 남긴다.

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

### B-037 배포 환경 부하 smoke

Status: Done

Notes:
- 2026-07-05 `coreable-saf.com` 기준으로 k6 public smoke를 완료했다.
- 범위는 비로그인 공개 페이지/API로 제한했다: `/`, `/products`, `/products/{id}`, `/api/products`, `/api/health`.
- 5 VU 1분 -> 20 VU 2분에서 5xx 0%, p95 511.18ms, p99 887.50ms, RPS 6.15/s였다.
- EC2 `t4g.micro`는 테스트 중 available memory가 180MB까지 내려가고 swap이 317MB까지 올라갔다. 오픈 전 `t4g.small` 업그레이드 후 같은 시나리오로 재측정한다.

Tasks:
- [x] 배포 URL 기준 짧은 k6 smoke 시나리오를 만든다.
- [x] 공개 페이지/API 응답 시간과 오류율을 확인한다.
- [x] 부하 기준과 VM 업그레이드 판단선을 문서에 남긴다.

### B-038 실오픈 전 성능/보안 baseline 점검

Status: Done

Notes:
- 2026-07-05 Lighthouse mobile baseline과 OWASP ZAP passive baseline을 완료했다.
- Lighthouse Performance는 `/` 60, `/products` 73, 상품 상세 73이다. LCP가 18.8~24.3초로 가장 큰 개선 항목이다.
- ZAP baseline은 `FAIL-NEW 0`, `WARN-NEW 15`였다. 차단 수준의 즉시 취약점은 확인되지 않았지만 HTML 보안 헤더 hardening은 실결제 전 후속 후보로 남긴다.
- 세부 결과는 `docs/perf-security-baseline.md`에 기록했다.

Tasks:
- [x] Lighthouse 모바일 성능/접근성 baseline을 확인한다.
- [x] 공개 페이지 ZAP baseline scan을 실행한다.
- [x] 실결제 오픈 전 차단 수준의 보안/정책 이슈를 정리한다.

## Working Rules

- 작업 시작 전 `Now`에서 하나를 고른다.
- 작은 버그와 문구 수정은 backlog 항목을 만들지 않아도 된다.
- 큰 기능, 정책, 결제, 주문 상태, DB 변경은 backlog에 남긴다.
- 각 backlog 항목의 하위 task/checklist는 같은 항목 아래 `Tasks:`로 관리한다.
- 완료하면 `docs/BACKLOG_DONE.md`로 옮기고 git commit으로 이력을 남긴다.
- PR과 외부 이슈는 팀 협업, 배포 전 리뷰, 큰 리스크 변경에만 사용한다.
- PROJECT_LOG는 관련 backlog ID로 연결한다.
