# Pricing Policy

Status: Confirmed

## Confirmed Policy

- `sourcePrice` is supplier cost and is visible only to admins.
- `basePrice` is the customer-facing sale price.
- Default sale price is `sourcePrice * 1.25`, rounded to the nearest active rounding unit.
- If the supplier provides `price.resale.minimum`, round that minimum up to the active rounding unit and do not set a lower customer-facing sale price.
- Supplier shipping fees are not added to `sourcePrice` or `basePrice`.
- Customers are not charged a separate shipping fee; supplier shipping is treated as an operating cost.
- The default 25% markup is commission 5%, tax/fee buffer 10%, overhead 5%, and safety margin 5%.
- The tax/fee buffer is an internal pricing buffer, not tax settlement logic.
- Products without a numeric supplier cost must not be automatically published.
- Paid order item price snapshots are not changed after product price edits.

## Supplier Portal Pricing — Planned (B-101)

Status: Planned (B-101)

- 인증된 공급처 담당자는 자기 공급처 상품의 `sourcePrice`만 조회·입력할 수 있다.
- 공급처 요청은 `basePrice`, 고객 판매 상태 또는 검토 상태를 설정할 수 없다.
- 승인된 공급가 변경이 적용될 때 Coreable 서버가 active pricing policy로 `basePrice`를 계산해 저장한다.
- 옵션 고객가는 동일 계산기를 총 공급원가에 적용한다. `basePrice=price(sourcePrice)`, `optionCustomerTotal=price(sourcePrice+sourceAdditionalPrice)`, `additionalPrice=optionCustomerTotal-basePrice`이며 `price`는 같은 markup, resale-minimum floor와 rounding unit을 적용한다. 기존 고객/API의 비음수 option delta 계약을 유지하기 위해 `sourcePrice`와 `sourceAdditionalPrice`를 각각 0원 이상 정수 KRW로 제한한다.
- B-101은 existing pricing policy에 monotonic version을 추가한다. 상품/옵션 공급가 승인 적용은 basePrice, 모든 option additionalPrice, applied policy id/version, rates·rounding·minimum의 immutable calculator snapshot과 before/after 가격 이력을 한 트랜잭션에 저장해 부분 갱신이나 과거 계산 근거 유실을 허용하지 않는다.
- 다른 공급처의 공급가와 고객 주문의 공급가 스냅샷은 공급처에 노출하지 않는다.

## Deferred

- Supplier-specific margin rates.
- Category-specific margin rates.
- Settlement, cost of goods sold reports, and tax filing logic.
