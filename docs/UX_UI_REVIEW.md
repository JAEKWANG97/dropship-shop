# UX/UI Review

Date: 2026-06-29 KST
Role: Senior UX/UI reviewer
Scope: customer storefront, login/account entry, checkout entry, admin shell

## Executive Verdict

Current status: usable MVP, not yet professional commerce quality.

The product has the right broad structure: header, search, category entry, product cards, auth-gated flows, and admin shell. The main weakness is not layout existence; it is trust and purchase confidence. The current UI still feels like a developer seed/demo because product images are symbolic SVG placeholders, checkout/admin states expose implementation rough edges, and the top navigation lacks the stronger commerce affordances shown in the reference mockups.

## Priority Findings

### UX-001: Product Imagery Does Not Yet Sell The Product

Severity: Critical

Evidence:
- Actual home hero uses generated SVG placeholders with blank circles and English labels like `HELMET`, `BOOTS`, `VEST`.
- Reference mockups use recognizable safety gear photos with stronger product realism.

User impact:
- A buyer cannot visually trust what they are buying.
- The site reads as a prototype, not a purchasable B2B shop.

Recommendation:
- Replace local SVG seed images with real product-like images or asset-backed fixtures.
- Product cards, hero, category rows, and detail gallery should all show inspectable product visuals.
- Keep image placeholders only for true missing-image states.

### UX-002: Header Commerce Intent Is Too Weak

Severity: High

Evidence:
- Actual header has text links and a plain search box.
- Reference mockups use icon-led navigation and a strong orange `바로 구매하기` CTA.

User impact:
- Important actions compete equally: category, cart, order lookup, account, login.
- The user does not get a clear “buy now” cue from the global navigation.

Recommendation:
- Add icon+label affordances for cart/order/account.
- Add a persistent primary CTA such as `바로 구매하기` or route it to products.
- Keep logged-in state behavior from the QA fix: logged-in users should not see `로그인`.

### UX-003: Home Hero Lacks Real-World Context

Severity: High

Evidence:
- Actual hero is clean but abstract and boxed.
- Reference mockup uses construction-site context and real gear composition.

User impact:
- The first screen communicates category, but not operational credibility.
- B2B customers need fast confidence: “this shop understands jobsite purchasing.”

Recommendation:
- Use a full-width or wide hero image with construction/safety gear context.
- Keep headline copy, but reduce visual emptiness on the right side.
- Benefits should include short supporting text, not only labels.

### UX-004: Product Listing Filters Are Too Simplified For B2B Buying

Severity: Medium

Evidence:
- Current filters are category and price only.
- Reference mockup includes counts, checkbox-style filters, min/max inputs, MOQ filters, sort controls, and view modes.

User impact:
- For safety gear buying, users likely compare size, MOQ, supplier, price band, and availability.
- Current filtering works for demo data but will not scale to operational catalog browsing.

Recommendation:
- MVP next layer: category, price, sale status, MOQ, supplier.
- Avoid complex faceted search until catalog size justifies it, but add visible counts and selected filter clarity.

### UX-005: Product Cards Need Stronger Purchase Hierarchy

Severity: High

Evidence:
- Current cards show name, summary, price, meta, and text action bands.
- Reference cards show product image first, product code, price, MOQ, clear `장바구니` and `바로구매` buttons.

User impact:
- Current card is readable, but not retail-sharp.
- `장바구니` and `바로구매` appear as part of the card, not as decisive controls.

Recommendation:
- Separate card click area from purchase controls.
- Add minimum order quantity and product code/SKU when available.
- Make `바로구매` the visual primary button and `장바구니` secondary.

### UX-006: Account/Login Experience Is Functionally Correct But Under-Branded

Severity: Medium

Evidence:
- Login page intentionally avoids over-explaining, but the social login surface is sparse.
- The service is B2B and policy-heavy, but login does not reinforce trust or purpose.

User impact:
- Social login feels like a generic auth page, not part of the commerce experience.

Recommendation:
- Keep the page minimal.
- Add small trust cues near social buttons: no email/password, no guest checkout, business order tracking after login.
- Do not reintroduce long explanatory copy.

### UX-007: Admin UI Is Useful But Still Reads As Mock-Oriented

Severity: Medium

Evidence:
- Admin shell has dashboard/products/orders structure.
- Previous QA found mock order fallback leaking into production-like views; fixed in working tree.
- Admin data tables are visually clean but lack operational density and empty-state affordances.

User impact:
- Admin user can see pages, but empty states and next actions are not always explicit.

Recommendation:
- For empty orders, show a clear empty state: `처리할 주문이 없습니다`.
- For product list, keep table density but add quick status filters and row-level action targets later.
- Do not add heavy charts until real operational data exists.

## What Is Already Working

- Overall navigation structure is understandable.
- Header search is prominent.
- Catalog/product/detail/account/cart/checkout/admin routes exist and render.
- Auth-gated pages explain login requirements instead of crashing.
- The latest QA fixes improve login-state navigation, remove false admin mock orders, and fix empty-cart checkout priority.
- Color system has enough contrast between navy and orange for primary commerce actions.

## Recommended Design Direction

The service should move toward “quiet B2B commerce,” not a marketing landing page.

Use:
- Real product images.
- Dense but readable cards.
- Clear buying controls.
- Strong trust footer and business information.
- Simple status/empty states.
- Restrained navy/orange palette with white/gray surfaces.

Avoid:
- Abstract product placeholders.
- Decorative oversized panels that reduce product density.
- Mock operational data.
- Long explanatory login copy.
- New complex filtering logic before real catalog data supports it.

## Next Design Tasks

1. Replace seed/demo SVG images with realistic product image fixtures.
2. Update header to match the reference commerce affordances: icons, cart/order/account, primary buy CTA.
3. Tighten product cards: product image, name, model/SKU, price, MOQ, cart, buy.
4. Add professional empty states for admin orders and customer cart/orders.
5. Add footer business/trust information closer to the reference mockup.
6. After the above, run mobile viewport review for header wrapping, card density, and checkout forms.

## Bottom Line

Do not redesign the whole app yet. The fastest professional lift is imagery, header affordance, card hierarchy, and empty states. Those four changes will make the same backend and flow feel much closer to a real commerce product.
