# Pricing Policy

Status: Confirmed

## Confirmed Policy

- `sourcePrice` is supplier cost and is visible only to admins.
- `basePrice` is the customer-facing sale price and includes expected shipping cost.
- Default sale price is `sourcePrice * 1.25`, rounded up to the active rounding unit.
- The default 25% markup is commission 5%, tax/fee buffer 10%, overhead 5%, and safety margin 5%.
- The tax/fee buffer is an internal pricing buffer, not tax settlement logic.
- Products without a numeric supplier cost must not be automatically published.
- Paid order item price snapshots are not changed after product price edits.

## Deferred

- Supplier-specific margin rates.
- Category-specific margin rates.
- Settlement, cost of goods sold reports, and tax filing logic.
