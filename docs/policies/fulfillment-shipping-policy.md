# Fulfillment And Shipping Policy

Status: Confirmed

## Purpose

공급처 발주, 출고, 송장 입력, 배송비, 배송 상태 기준을 정한다.

## Policy Areas

- 공급처 발주 방식
- 발주 처리 주체
- 배송비 계산 방식
- 복수 공급처 주문 처리
- 송장번호 입력 방식
- 배송 상태 변경 방식
- 출고 지연 처리
- 공급처 발주 SLA
- 배송지 잠금 기준

## Initial Direction

- 공급처 발주는 관리자가 수동 처리한다.
- 송장번호도 관리자가 수동 입력한다.
- 고객에게 별도 배송비를 청구하지 않고 상품 판매가에 배송비를 포함한다.
- 복수 배송 그룹 장바구니는 결제 그룹(PaymentGroup)으로 한 번에 결제하고, 배송 그룹별 주문으로 분리한다.

## Confirmed Policy

- 도매꾹 source snapshot이 있는 주문은 입금확인 후 Private API와 선충전 e-money로 자동 발주하고, 그 외 주문은 관리자가 수동 처리한다.
- 결제 확정 후 공급처 발주 작업 시작 SLA는 영업일 기준 당일 또는 다음 영업일로 둔다.
- 오후 3시 전 결제 완료 주문은 당일 공급처 발주 작업 시작을 목표로 한다.
- 오후 3시 이후, 주말, 공휴일 결제 완료 주문은 다음 영업일 공급처 발주 작업 시작을 목표로 한다.
- 관리자가 공급처 발주 작업을 시작하면 `supplierOrderStartedAt`을 기록하고 배송지를 잠근다.
- 배송지 잠금은 별도 주문 상태를 추가하지 않고 `addressLockedAt`으로 판단한다.
- 배송지 잠금 이후 고객 직접 배송지 변경은 거절하고, 고객 문의와 관리자 수동 처리 대상으로 둔다.
- 고객 저장 배송지는 주소록으로만 사용하고, 주문에는 주문 시점 배송지 스냅샷을 저장한다.
- 도매꾹 자동 발주 주문은 구매 주문 조회에서 택배사와 송장번호를 동기화하고, 그 외 주문은 관리자가 수동 입력한다.
- 공급처 발주 후 1영업일 안에 공급처 응답 또는 출고 예정일 확보를 목표로 한다.
- 공급처 발주 후 2영업일 이상 출고 예정이 불명확하면 고객에게 지연 안내를 보낸다.
- 공급처 품절이 확인되면 즉시 품절 안내와 배송 그룹 주문 단위 환불 흐름으로 전환한다.
- 공급처 발주 증빙으로 공급처 주문번호, 발주 주소 스냅샷, 발주 관리자, 예상 출고일, 공급처 응답 메모를 기록한다.
- 자동 발주 전 상품·옵션 판매상태, 공급가, 배송비, e-money 잔액을 재검증하고, 응답 유실 시 공급처 주문 대사 전에는 재주문하지 않는다.
- 고객에게 별도 배송비를 청구하지 않는다.
- 공급처 배송비는 운영 원가·마진 판단에 사용하되 active 가격 계산식이 `sourcePrice`에 자동 가산하지 않는다.
- 주문의 배송비 표시 금액은 `0원`으로 시작한다.
- 무료배송 기준 금액은 MVP에서 사용하지 않는다.
- MVP에서 한 주문은 하나의 배송 그룹만 포함한다.
- 배송 그룹은 같은 공급처 상품 묶음을 의미한다.
- 장바구니에는 여러 배송 그룹의 상품을 담을 수 있다.
- 결제 시 장바구니 상품은 배송 그룹별 주문으로 분리되지만, 고객은 장바구니 전체를 한 번에 결제할 수 있다.
- 하나의 계좌입금 결제 그룹은 여러 배송 그룹 주문을 포함할 수 있다.
- 고객 화면에는 `공급처` 대신 `배송 그룹`이라는 표현을 사용한다.
- 모든 배송 그룹의 배송비는 `0원`으로 표시한다.
- 도매꾹 자동 발주 주문은 구매 주문 조회에서 택배사, 송장번호와 배송 상태를 동기화한다.
- 그 외 공급처 주문은 관리자가 택배사와 송장번호를 입력하고 배송 상태를 보정한다.
- MVP 배송 모델은 주문 1개당 배송 1개로 시작한다.
- 부분 출고와 분할 배송은 MVP에서 지원하지 않는다.
- 도매꾹 구매 주문 조회에서 배송 완료가 확인되면 주문은 `DELIVERED` 상태로 전환할 수 있다.
- 배송조회 실패, 택배사 장애, 송장번호 오류가 있을 수 있으므로 관리자는 배송 상태를 수동 보정할 수 있어야 한다.
- 공급처 주문 동기화는 배송 상태를 앞으로만 진행시키며, 관리자 수동 보정 상태를 임의로 되돌리거나 덮어쓰지 않는다.
- 관리자 수동 배송 보정은 사유와 이력을 필수로 기록한다.
- 도매꾹 주문 조회 장애가 주문, 입금확인과 환불 기능을 막아서는 안 된다.
- 별도 택배사 배송조회 API는 초기 출시 필수 범위에서 제외한다.

## System Impact

- `SUPPLIER_ORDER_PENDING` 주문을 관리자 작업 큐로 보여줘야 한다. Implemented by DS-11.
- 공급처 발주 작업 시작 액션은 `supplierOrderStartedAt`, `addressLockedAt`, `addressLockedByAdminId`를 기록해야 한다. Implemented by DS-12.
- 관리자 액션으로 `SUPPLIER_ORDERED`, `OUT_OF_STOCK`, `SHIPPED` 상태가 변경된다. DS-12 implements `SUPPLIER_ORDERED` and `OUT_OF_STOCK`; DS-13 implements `SHIPPED`.
- 공급처 발주 완료 액션은 공급처 주문번호, 발주 주소 스냅샷, 발주 관리자, 예상 출고일, 공급처 응답 메모를 저장해야 한다. Implemented by DS-12.
- 송장번호 없는 주문은 `SHIPPED` 상태로 전환할 수 없다. Implemented by DS-13.
- 배송비는 고객에게 별도 청구하지 않으므로 주문 금액 계산에서 shippingFee는 `0`으로 시작한다.
- 상품 운영자는 상품 판매가에 예상 배송비와 공급처 비용을 반영해야 한다.
- 공급처별 실제 배송비 차이는 상품 마진에 반영되며, 고객 결제 단계에서 별도 배송비로 분리하지 않는다.
- 한 주문에 여러 공급처 상품을 섞지 않으므로 품절, 배송, 환불 처리는 배송 그룹 주문 단위로 제한할 수 있다.
- 장바구니 전체 1회 결제를 지원하므로 결제와 배송 그룹 주문을 연결하는 결제 그룹(PaymentGroup) 모델이 필요하다.
- 배송 그룹 주문이 품절되면 해당 배송 그룹 주문 금액만 부분 취소/환불할 수 있어야 한다.
- 배송 그룹 주문 내부의 일부 상품, 옵션, 수량 단위 부분 취소/환불은 MVP에서 지원하지 않는다.
- 주문 생성 시 장바구니 항목을 공급처 기준 배송 그룹으로 나누는 로직이 필요하다.
- 고객 주문 내역에는 배송 그룹별로 생성된 주문이 각각 표시된다.
- 배송 상태 자동 동기화를 위한 carrier, trackingNumber, trackingSyncedAt 필드를 둔다. Implemented by DS-35.
- MVP에서는 `Shipment`를 주문당 1개만 생성한다.
- 배송조회 동기화 실패를 `trackingSyncFailureReason`으로 기록하고 재시도할 수 있어야 한다. Implemented by DS-35.
- 자동 배송조회와 관리자 수동 보정이 충돌하지 않도록 상태 변경 이력을 남겨야 한다. Implemented by DS-36.
- 수동 배송 보정은 `manualOverride`, `manualCorrectionReason`, `manualCorrectedByAdminId` 같은 정보를 남겨야 한다. Implemented by DS-36.
- 배송조회 연동 방식 선택을 위한 별도 기술 조사 이슈가 필요하다.
- 고객 배송 정책 페이지는 `GET /api/policies/shipping`으로 노출한다. Implemented by DS-16.

## Supplier Portal Fulfillment And Shipping — B-103 Implemented, B-104 Planned

Status: B-103 immediate fulfillment routing, address lock, minimum PII, takeover and email foundation are Implemented. B-104 multiple Shipment/allocation/tracking behavior remains Planned. Existing Coreable manual and Domeggook fulfillment and shipment behavior remains compatible.

- 관리자 입금확인이 성공하면 활성 포털 공급처의 주문을 즉시 출고 요청으로 노출하고 같은 트랜잭션에서 재고 예약 소비, Fulfillment `requestedAt`과 `addressLockedAt`을 기록한다.
- portal 접근이 정지·해제됐지만 관리자가 `salesAction=KEEP`으로 판매를 유지한 공급처의 신규 입금확인 주문은 `SUPPLIER_PORTAL`에 쌓지 않고 `COREABLE_MANUAL`로 라우팅한다. 기존 결제완료 portal 주문은 원래 channel을 보존한 채 operational owner와 인계 증적을 Coreable로 고정하며 재활성화로 자동 재배정하지 않는다.
- 거래 상태만 INACTIVE인 공급처는 time-valid VERIFIED contract가 있을 때 기존 결제완료 portal 주문을 마무리할 수 있다. Contract EXPIRED/REVOKED는 ACTIVE portal을 SUSPENDED로 바꾸고 열린 supplier-owned portal 주문을 `CONTRACT_EXPIRED|CONTRACT_REVOKED` 증적으로 Coreable에 인계하며, 재검증·재활성화가 자동 재배정하지 않는다.
- Fulfillment channel/owner/handover additive schema와 lifecycle takeover writer는 B-100이 먼저 소유하고, B-103이 portal 요청 생성과 KEEP fallback을 활성화한다.
- 공급처 수락 단계를 만들지 않는다. 내부 Order는 `SUPPLIER_ORDER_PENDING`을 사용하되 공급처 화면에는 `FULFILLMENT_REQUESTED`로 매핑하고 기존 관리자 발주 시작·완료 단계를 건너뛴다.
- 배송 메모는 선택값, 최대 300자로 받고 trim 뒤 공백-only면 `null` 주문 snapshot으로 저장한다. 공급처 상세는 이 값과 배송에 필요한 최소 수령인·주소만 `no-store`로 제공한다.

The tracking and multiple-Shipment rules below remain Planned in B-104:

- 공급처 담당자는 time-valid VERIFIED contract가 있는 자기 공급처 주문에만 택배사 코드와 송장번호를 직접 등록한다.
- 포털 주문은 주문 1개에 여러 Shipment를 허용하고 각 Shipment에 주문 항목별 양수 수량을 할당한다. 누적 할당량은 주문수량을 넘을 수 없다.
- 첫 Shipment는 allocation을 생략하면 모든 미할당 수량을 기본 배정하고, 추가 Shipment는 명시적 allocation을 요구한다. 동시 등록은 주문·항목 잠금으로 검증한다.
- 송장 등록은 실제 집하·출고 또는 배송완료 증거가 아니므로 `TRACKING_REGISTERED`로 표시한다. 공급처는 `SHIPPED`나 `DELIVERED`를 직접 설정하지 않는다.
- 서버는 지원 택배사 registry와 송장번호로 공식 배송조회 URL을 생성한다. 공급처가 임의 조회 URL을 저장할 수 없고 별도 live 택배사 API 연동은 이 범위에서 제외한다.
- 새 portal Shipment는 registry의 carrier code와 기존 non-null canonical carrier name을 dual-write한다. legacy carrier는 결정적 mapping만 code를 backfill하고 나머지는 공식 URL 없이 유지한다.
- 공급처는 배송완료 전까지만 택배사·송장 오입력을 idempotency/version guard와 사유로 정정할 수 있다. allocation 오류는 Coreable void 뒤 재등록하며 Shipment와 ShipmentItem을 삭제·수정하지 않는다.
- Coreable 관리자는 idempotency/version guard, 사유와 `registeredAt <= deliveredAt <= evidenceObservedAt <= now`인 근거시각을 입력해 송장을 정정·무효화하거나 Shipment별 배송완료를 수동 확정할 수 있다. 완료시각 정정도 같은 ordering을 요구한다. 마지막 유효 송장 void는 출고 요청 상태로 되돌리고 그 외에는 유효 송장 aggregate를 재계산한다.
- 잘못된 portal 수동 배송완료는 이후 Claim/Refund가 생성되기 전에만 tracking 재개 또는 delivered-at 정정을 허용한다. 원래 증적은 이력에 남기고 후속 처리가 있으면 `409`로 차단하며 고객에게 보이는 상태 후퇴는 알림을 남긴다.
- 모든 주문수량이 void되지 않은 Shipment에 할당되고 모든 Shipment에 Coreable이 확인한 배송완료 근거가 기록된 경우에만 주문을 `DELIVERED`로 전환한다.
- 고객 주문 응답은 유효 Shipment별 공식 택배사 URL을 서버에서 생성해 제공하고 `TRACKING_REGISTERED`를 `송장 등록 · 배송조회 가능`으로 표시한다. 이 문구는 집하 또는 배송중 증거가 아니다.
- Coreable 인계된 `SUPPLIER_PORTAL + owner=COREABLE` 주문은 같은 plural/allocation service의 admin portal-shipment 명령으로 처리한다. supplier list와 mutation은 owner SUPPLIER를 요구하고, legacy 발주 시작/완료·단일 shipment·tracking-sync/manual-correction은 portal channel을 거절한다.

### Mixed System Impact

- B-100/B-103은 Fulfillment channel과 requestedAt을 저장해 `COREABLE_MANUAL`, `DOMEGGOOK_API`, `SUPPLIER_PORTAL` 흐름을 분리한다.
- Shipment의 기존 order unique 제약은 singular repository caller를 plural aggregate로 전환한 뒤 제거하고 `shipment_items` allocation을 추가한다. 기존 Shipment는 해당 주문 전체 수량 allocation으로 backfill한다.
- 고객/admin API는 row가 있으면 기존 단일 `shipment` 응답을 가장 이른 non-voided row와 truncation flag로 유지한다. row가 없으면 customer는 현행 non-null READY placeholder, admin은 현행 null을 유지하면서 `shipments[]`와 allocation 완료 여부를 canonical로 추가한다.
- 복수 송장 portal 주문의 Claim 기간은 마지막 유효 배송완료인 `max(non-voided deliveredAt)`을 기준으로 한다.
- 송장 등록·정정은 idempotency와 tenant를 검증하고, 관리자 정정·무효화·배송완료 액션은 사유·근거시각·전후 상태를 보존한다.

## Open Questions

None
