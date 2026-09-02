# Pricing Policy

Status: Confirmed

## Confirmed Policy

- `sourcePrice` is supplier cost and is visible only to admins and the authenticated manager of the owning supplier. It is never public.
- `basePrice` is the customer-facing sale price.
- Default sale price is `sourcePrice * 1.25`, rounded to the nearest active rounding unit.
- If the supplier provides `price.resale.minimum`, round that minimum up to the active rounding unit and do not set a lower customer-facing sale price.
- Supplier shipping fees are not added to `sourcePrice` or `basePrice`.
- Customers are not charged a separate shipping fee; supplier shipping is treated as an operating cost.
- The default 25% markup is commission 5%, tax/fee buffer 10%, overhead 5%, and safety margin 5%.
- The tax/fee buffer is an internal pricing buffer, not tax settlement logic.
- Products without a numeric supplier cost must not be automatically published.
- Paid order item price snapshots are not changed after product price edits.

## Supplier Portal Pricing — Implemented (B-101)

Status: Implemented (B-101)

- 인증된 공급처 담당자는 자기 공급처 상품의 `sourcePrice`만 조회·입력할 수 있다.
- 공급처 요청은 `basePrice`, 고객 판매 상태 또는 검토 상태를 설정할 수 없다.
- 승인된 공급가 변경이 적용될 때 Coreable 서버가 active pricing policy로 `basePrice`를 계산해 저장한다.
- 옵션 고객가는 동일 계산기를 총 공급원가에 적용한다. `basePrice=price(sourcePrice)`, `optionCustomerTotal=price(sourcePrice+sourceAdditionalPrice)`, `additionalPrice=optionCustomerTotal-basePrice`이며 `price`는 같은 markup, resale-minimum floor와 rounding unit을 적용한다. `sourcePrice`와 `sourceAdditionalPrice`는 각각 0원 이상 1억원 이하 정수 KRW, 계산된 고객 단가는 10억원 이하로 제한한다. 합산·수량 곱은 exact 연산으로 overflow를 거절한다.
- 도매꾹이 음수 option delta를 반환하면 모든 option의 최저 delta를 `sourcePrice`에 한 번 반영하고 각 option delta에서 같은 값을 빼서 nonnegative 표현으로 정규화한다. 각 option의 `sourcePrice+sourceAdditionalPrice` 총액은 정규화 전과 정확히 같아야 하며, 정규화 결과가 공급가 범위를 벗어나거나 overflow이면 동기화를 거절하고 기존 값을 유지한다.
- Portal 상품을 기존 관리자 상품/옵션 API로 수정해도 요청의 `basePrice`/`additionalPrice`는 가격 결정 권한이 아니다. 서버가 active policy를 잠그고 모든 고객가를 다시 계산하며 applied policy id/version과 full calculator snapshot 이력을 함께 갱신한다.
- B-101은 existing pricing policy에 monotonic version을 추가한다. 상품/옵션 공급가 적용 이력의 after 상태는 basePrice, 모든 option additionalPrice, applied policy id/version와 rates·rounding·minimum의 immutable calculator snapshot을 저장하고, before 상태는 이전 applied policy id/version과 가격을 저장한다. 이전 계산 근거는 그 정책이 적용된 선행 이력의 immutable after snapshot에 남아 in-place 정책 변경과 섞이지 않는다.
- 다른 공급처의 공급가와 고객 주문의 공급가 스냅샷은 공급처에 노출하지 않는다.

## Deferred

- Supplier-specific margin rates.
- Category-specific margin rates.
- Settlement, cost of goods sold reports, and tax filing logic.
