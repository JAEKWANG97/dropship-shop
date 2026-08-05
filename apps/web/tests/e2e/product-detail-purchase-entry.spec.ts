import { expect, test } from "@playwright/test";
import {
  activeProductId,
  addCookie,
  expectNoHorizontalOverflow,
  requireCustomerCookie,
} from "./helpers";

test("guest product detail shows purchase controls and redirects to login on submit", async ({ page }, testInfo) => {
  const productId = await activeProductId();
  await page.goto(`/products/${productId}`);
  const purchasePanel = page.locator(".product-purchase-panel");
  const mobile = testInfo.project.name === "mobile";
  const purchaseForm = page.locator(mobile ? ".mobile-purchase-form" : ".desktop-purchase-form");
  const mobileBar = page.locator(".mobile-purchase-bar");

  if (mobile) {
    await expect(page.locator(".site-header")).toBeHidden();
    await expect(page.locator(".product-mobile-topbar")).toBeVisible();
    await expect(page.locator(".mobile-bottom-nav")).toBeVisible();
    await expect(mobileBar.getByRole("button", { name: "장바구니", exact: true })).toBeVisible();
    await expect(mobileBar.getByRole("button", { name: "바로구매", exact: true })).toBeVisible();
    await mobileBar.getByRole("button", { name: "장바구니", exact: true }).click();
  } else {
    await expect(purchasePanel.locator(".product-action-row")).toBeVisible();
  }
  await expect(purchaseForm.getByLabel("옵션")).toBeVisible();
  await expect(purchaseForm.getByLabel("수량")).toBeVisible();
  const cartButton = purchaseForm.getByRole("button", { name: "장바구니", exact: true });
  const checkoutButton = purchaseForm.getByRole("button", { name: "바로구매", exact: true });
  await expect(cartButton).toBeVisible();
  await expect(checkoutButton).toBeVisible();
  await expect(purchasePanel.getByRole("link", {
    name: "배송비는 상품 가격에 포함되어 있으며, 주문은 배송 그룹 단위로 처리됩니다.",
  })).toHaveAttribute("href", "/policies/shipping");
  await expect(purchasePanel.getByRole("link", {
    name: "취소, 반품, 교환, 환불은 주문 상태와 공급처 발주 여부에 따라 처리됩니다.",
  })).toHaveAttribute("href", "/policies/cancellation-refund");
  await expect(page.getByRole("link", { name: "로그인하고 계속하기" })).toHaveCount(0);
  await expect(page.locator(".product-hero-copy")).not.toContainText("로그인이 필요합니다");
  await expectNoHorizontalOverflow(page);

  await cartButton.click();
  await page.waitForURL(/\/login\?/);

  const url = new URL(page.url());
  expect(url.pathname).toBe("/login");
  expect(url.searchParams.get("redirectTo")).toBe(`/products/${productId}`);
});

test("mobile purchase bar submits the product form", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "mobile", "Mobile purchase bar is mobile-only.");

  const productId = await activeProductId();
  await page.goto(`/products/${productId}`);

  const mobileBar = page.locator(".mobile-purchase-bar");
  const mobileNav = page.locator(".mobile-bottom-nav");
  await expect(mobileBar).toBeVisible();
  await expect(mobileNav).toBeVisible();
  await expect(mobileBar.getByRole("button", { name: "장바구니", exact: true })).toBeVisible();
  await expect(mobileBar.getByRole("button", { name: "바로구매", exact: true })).toBeVisible();
  await expect(mobileBar.locator(".mobile-purchase-price")).toHaveCount(0);
  const [initialMobileBarBox, mobileNavBox] = await Promise.all([
    mobileBar.boundingBox(),
    mobileNav.boundingBox(),
  ]);
  expect(initialMobileBarBox?.height).toBeLessThanOrEqual(70);
  expect(mobileNavBox).not.toBeNull();
  expect(initialMobileBarBox).not.toBeNull();
  expect(initialMobileBarBox!.y + initialMobileBarBox!.height).toBeLessThanOrEqual(mobileNavBox!.y + 1);
  await expectNoHorizontalOverflow(page);

  await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
  const [lastFooterLinkBox, mobileBarBox] = await Promise.all([
    page.locator(".site-footer a").last().boundingBox(),
    mobileBar.boundingBox(),
  ]);
  expect(lastFooterLinkBox).not.toBeNull();
  expect(mobileBarBox).not.toBeNull();
  expect(lastFooterLinkBox!.y + lastFooterLinkBox!.height).toBeLessThanOrEqual(mobileBarBox!.y);

  await mobileBar.getByRole("button", { name: "장바구니", exact: true }).click();
  const mobilePurchaseForm = page.locator(".mobile-purchase-form");
  await expect(mobilePurchaseForm).toBeVisible();
  await expect(mobilePurchaseForm.getByLabel("옵션")).toBeVisible();
  await expect(mobilePurchaseForm.getByLabel("수량")).toBeVisible();
  await mobilePurchaseForm.getByRole("button", { name: "장바구니", exact: true }).click();
  await page.waitForURL(/\/login\?/);

  const url = new URL(page.url());
  expect(url.pathname).toBe("/login");
  expect(url.searchParams.get("redirectTo")).toBe(`/products/${productId}`);
});

test("catalog shows related categories only after a search", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "desktop", "Desktop search facets are desktop-only.");

  await page.goto("/products");
  await expect(page.locator("aside.catalog-sidebar")).toHaveCount(0);

  await page.goto("/products?q=%EC%95%88%EC%A0%84");
  const sidebar = page.locator("aside.catalog-sidebar");
  await expect(sidebar.getByRole("heading", { name: "관련 카테고리" })).toBeVisible();
  await expect(sidebar.getByRole("link", { name: /전체 검색 결과/ })).toHaveClass(/active/);
  await expect(sidebar.getByRole("link", { name: /개인보호구/ })).toHaveCount(0);
});

test("customer can add product detail item to cart", async ({ page, context }, testInfo) => {
  const [productId, customerCookie] = await Promise.all([
    activeProductId(),
    requireCustomerCookie(),
  ]);

  await addCookie(context, customerCookie);
  await page.goto(`/products/${productId}`);
  const mobile = testInfo.project.name === "mobile";
  if (mobile) {
    await page.locator(".mobile-purchase-bar").getByRole("button", { name: "장바구니", exact: true }).click();
  }
  const cartButton = page
    .locator(mobile ? ".mobile-purchase-form" : ".desktop-purchase-form")
    .getByRole("button", { name: "장바구니", exact: true });
  await cartButton.click();

  await page.waitForURL(/\/cart\?/);
  await expect(page.locator(".notice").first()).toContainText("장바구니에 담았습니다.");
  await expectNoHorizontalOverflow(page);
});
