import { expect, test } from "@playwright/test";
import {
  addCookie,
  ensureSimpleReturnClaim,
  expectNoHorizontalOverflow,
  requireCustomerCookie,
  requireSeedOrderByStatus,
} from "./helpers";

const POSTCODE_SCRIPT =
  "https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js";

test("checkout detail shows bank-transfer deposit instructions", async ({ page, context }) => {
  const [customerCookie, paymentPendingOrder] = await Promise.all([
    requireCustomerCookie(),
    requireSeedOrderByStatus("PAYMENT_PENDING"),
  ]);

  await addCookie(context, customerCookie);
  await page.goto(`/checkout/${paymentPendingOrder.checkoutNumber}`);

  await expect(page.getByRole("heading", { name: new RegExp(`주문서 ${paymentPendingOrder.checkoutNumber}`) })).toBeVisible();
  await expect(page.getByRole("heading", { name: "계좌입금 안내" })).toBeVisible();
  await expect(page.locator("body")).toContainText("로컬 테스트 은행");
  await expect(page.locator("body")).toContainText("000-0000-0000");
  await expect(page.locator("body")).toContainText("가라사니");
  await expect(page.locator("body")).toContainText("로컬 주문 고객");
  await expect(page.locator("body")).toContainText("입금 금액");
  await expect(page.locator("body")).toContainText("입금 기한");
  await expectNoHorizontalOverflow(page);
});

test("checkout address search fills the shipping address", async ({ page, context }) => {
  const [customerCookie, paymentPendingOrder] = await Promise.all([
    requireCustomerCookie(),
    requireSeedOrderByStatus("PAYMENT_PENDING"),
  ]);
  await page.route(POSTCODE_SCRIPT, async (route) => {
    await route.fulfill({
      contentType: "application/javascript",
      body: `window.daum={Postcode:function(options){this.open=function(){options.oncomplete({zonecode:"05555",roadAddress:"서울특별시 송파구 테스트로 1",jibunAddress:"",userSelectedType:"R"});};}};`,
    });
  });
  await addCookie(context, customerCookie);
  await page.goto(`/checkout/${paymentPendingOrder.checkoutNumber}`);

  await page.getByRole("button", { name: "주소 검색" }).click();

  await expect(page.getByLabel("우편번호")).toHaveValue("05555");
  await expect(page.getByLabel("주소", { exact: true })).toHaveValue("서울특별시 송파구 테스트로 1");
  await expect(page.getByLabel("상세 주소")).toBeFocused();
});

test("customer orders page links to order detail with claim form and claim status", async ({ page, context }) => {
  const [customerCookie, deliveredOrder] = await Promise.all([
    requireCustomerCookie(),
    requireSeedOrderByStatus("DELIVERED"),
  ]);
  await ensureSimpleReturnClaim(deliveredOrder.orderId, customerCookie);

  await addCookie(context, customerCookie);
  await page.goto("/orders");
  await page.locator(`a[href="/orders/${deliveredOrder.orderId}"]`).click();

  await expect(page).toHaveURL(new RegExp(`/orders/${deliveredOrder.orderId}$`));
  await expect(page.getByRole("heading", { name: new RegExp(`주문 ${deliveredOrder.orderNumber}`) })).toBeVisible();
  await expect(page.getByText("주문 상태")).toBeVisible();
  await expect(page.getByRole("heading", { name: "클레임 처리 상태" })).toBeVisible();
  await expect(page.getByText("접수 상태")).toBeVisible();
  await expect(page.getByRole("heading", { name: "클레임 접수" })).toBeVisible();
  await expect(page.locator('input[name="evidenceFiles"]')).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test("order detail without claims still renders a stable claim entry form", async ({ page, context }) => {
  const [customerCookie, supplierOrderedOrder] = await Promise.all([
    requireCustomerCookie(),
    requireSeedOrderByStatus("SUPPLIER_ORDERED"),
  ]);

  await addCookie(context, customerCookie);
  await page.goto(`/orders/${supplierOrderedOrder.orderId}`);

  await expect(page.getByRole("heading", { name: new RegExp(`주문 ${supplierOrderedOrder.orderNumber}`) })).toBeVisible();
  await expect(page.getByRole("heading", { name: "클레임 처리 상태" })).toHaveCount(0);
  await expect(page.getByRole("heading", { name: "클레임 접수" })).toBeVisible();
  await expectNoHorizontalOverflow(page);
});
