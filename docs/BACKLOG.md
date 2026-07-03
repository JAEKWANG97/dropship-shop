# Backlog

이 파일이 현재 작업 큐의 기준이다. 혼자 개발하는 동안 Linear와 GitHub Issues는 기본으로 사용하지 않는다.

## Now

### B-040 GitHub Actions Docker build 최적화

Status: In Progress

Notes:
- 현재 AWS 테스트 배포 병목은 EC2 pull/up이 아니라 GitHub Actions의 `build-and-push`, 특히 Web ARM64 Docker image build다.
- 기준 실행 시간은 `verify 2m58s`, `build-and-push 7m39s`, `deploy 1m12s`다.
- 1차 최적화는 Docker BuildKit GitHub Actions cache를 사용한다.

Tasks:
- [x] API Docker build에 GitHub Actions cache scope를 추가한다.
- [x] Web Docker build에 GitHub Actions cache scope를 추가한다.
- [x] 배포 문서에 build cache 정책과 한계를 기록한다.
- [ ] cache warm-up 이후 앱 변경 배포 시간을 비교한다.

### B-039 AWS EC2 Docker CI/CD 배포

Status: In Progress

Notes:
- `coreable-saf.com` 배포를 GitHub Actions, GHCR, EC2 Docker Compose로 반복 가능하게 만든다.
- 비용 우선으로 `t4g.micro` 단일 서버에 Web/API/PostgreSQL/업로드 이미지를 함께 둔다.
- GitHub-hosted Actions SSH 배포를 위해 SSH 보안그룹은 key-only `0.0.0.0/0`로 둔다. 장기 운영 전 SSM 또는 fixed egress runner로 좁히는 것을 검토한다.
- S3/RDS/CloudFront는 테스트 URL 확보 뒤 필요 시 전환한다.
- EC2 `43.200.135.171` 기준 Docker 배포와 host-local health check는 성공했다.
- Cloudflare DNS는 proxied A record로 연결했고, 운영 기준 HTTPS는 nginx + Cloudflare Origin Certificate + Full (strict)로 전환한다.

Tasks:
- [x] API/Web Dockerfile을 추가한다.
- [x] EC2 production compose와 reverse proxy 설정을 추가한다.
- [x] GitHub Actions CI와 deploy workflow를 추가한다.
- [x] AWS EC2/Elastic IP 생성 스크립트와 서버 bootstrap 스크립트를 추가한다.
- [x] EC2 Docker 배포 운영 문서를 추가한다.
- [x] AWS EC2, Elastic IP, 보안그룹을 생성한다.
- [x] 서버에 Docker, compose, env, proxy 설정을 준비한다.
- [x] GitHub Secrets를 등록하고 main 배포를 실행한다.
- [x] Cloudflare DNS를 Elastic IP로 연결한다.
- [x] nginx origin TLS를 적용하고 Cloudflare Full (strict)로 전환한다.
- [x] 배포 URL health를 확인한다. EC2 내부 health, origin HTTPS, Cloudflare 경유 URL 확인됨.
- [ ] 배포 URL 기준 browser smoke를 확인한다.

### B-016 테스트 배포 및 운영 readiness 점검

Status: Todo

Notes:
- `coreable-saf.com` 도메인은 확보됨.
- 배포 baseline은 B-039의 AWS EC2 Docker Compose 기준으로 진행한다.
- 아직 실결제 오픈 전이며, 외부 연동 준비용 운영 기준 배포로 본다.
- MVP 결제는 Toss Payments가 아니라 고객 직접 계좌입금과 관리자 입금확인 흐름으로 전환한다.
- Toss live 심사와 live key 전환은 후순위로 미루고, 통신판매업 신고와 실주문 운영 준비는 계좌입금 기준으로 확인한다.

Tasks:
- [x] 테스트 배포 아키텍처를 확정한다.
- [ ] production/staging env 변수 목록을 배포 서버에 등록한다.
- [ ] 상품 이미지 local volume 경로와 `APP_STORAGE_*` 값을 확정한다.
- [x] `/api/health`, readiness를 배포 환경에서 확인한다.
- [ ] DB migration dry run을 확인한다.
- [x] DB backup과 root volume snapshot 상태를 확인한다.
- [x] Web/API 도메인과 HTTPS를 연결한다.
- [ ] 배포 URL 기준 Playwright smoke를 실행한다.
- [ ] 계좌입금 주문/입금확인 플로우 기준 공개 정책, 회사 정보, 고객센터 접근 경로를 확인한다.
- [x] 실주문 전 DB와 업로드 이미지 backup/restore 방법을 확인한다.

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
- B-041 계좌입금 전환 후 공급처 발주는 관리자 입금확인 완료 주문만 대상으로 재검증한다.

Tasks:
- [x] 관리자 주문 상세에서 현재 주문/결제/배송/환불 상태를 확인할 수 있게 한다.
- [x] 발주 시작 액션을 실제 API에 연결하고 사유 입력을 받는다.
- [x] 공급처 발주 완료 액션을 실제 API에 연결하고 발주번호/예상출고일을 입력받는다.
- [x] 공급처 품절 액션을 실제 API에 연결하고 환불 흐름 진입을 확인한다.
- [x] 송장 입력 액션을 실제 API에 연결한다.
- [ ] 각 액션 성공 후 주문 목록/상세 상태가 갱신되는지 확인한다. 출시 전 최종 테스트에서 실행.
- [ ] 권한/validation/API 실패가 빈 화면이 아니라 오류 안내로 보이는지 확인한다. 출시 전 최종 테스트에서 실행.

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
- [ ] 호스팅 제공자와 계좌입금/구매안전서비스 정보를 실제 값으로 교체한다.
- [ ] 통신판매업 신고에 필요한 구매안전서비스 이용확인증 확보 방법을 확인한다. B-041의 확보 방안 결정과 연결한다.
- [ ] 현금영수증 발급 준비 상태를 출시 차단 체크리스트에 추가한다.
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

### B-001 Toss Payments sandbox 결제 플로우 완성

Status: Deferred

Notes:
- `B-001`은 기존 Toss Payments 결제 플로우 이슈 번호로 보존한다. 다른 결제 방식 작업에 재사용하지 않는다.
- MVP 결제는 B-041 계좌입금 주문/입금확인 플로우로 전환한다.
- Toss Payments PG 연동은 주문량, 입금확인 운영 부담, 구매전환, 구매안전서비스 요건을 보고 재검토한다.
- 웹 결제창 호출과 성공 리다이렉트 서버 confirm 연결 코드는 남아 있지만 고객 주 경로에서는 우선 제외한다.

Tasks:
- [ ] Toss Payments 재도입 여부와 카드/간편결제/PG 계좌이체/가상계좌 범위를 다시 정한다.
- [ ] Toss test client key를 로컬 env에 설정한다.
- [ ] Toss test secret key를 로컬 env에 설정한다.
- [ ] Toss sandbox 결제창에서 성공 결제를 실행한다.
- [ ] Success redirect 후 backend confirm 호출을 확인한다.
- [ ] 주문 상태가 `SUPPLIER_ORDER_PENDING`으로 바뀌는지 확인한다.
- [ ] Toss 실패/취소 redirect 화면을 확인한다.
- [ ] 중복 success redirect 또는 중복 confirm 요청이 같은 결제 결과를 반환하는지 확인한다.
- [ ] 결제 예외 발생 시 고객 화면과 관리자 payment exception queue를 함께 확인한다.

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
