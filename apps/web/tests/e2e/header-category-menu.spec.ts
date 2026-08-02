import { expect, test } from "@playwright/test";

test("header category menu navigates by group and category", async ({ page }) => {
  await page.goto("/");

  const menu = page.locator(".header-category-menu");
  await menu.locator(":scope > summary").click();
  await expect(menu).toHaveAttribute("open", "");
  await expect(menu.getByRole("link", { name: "전체 상품 보기" })).toBeVisible();

  const personalProtectiveEquipment = menu
    .locator(".header-category-group")
    .filter({ hasText: "개인보호구" });
  await personalProtectiveEquipment.locator("summary").click();
  await personalProtectiveEquipment.getByRole("link", { name: "안전모", exact: true }).click();

  await expect(page).toHaveURL(/\/products\?category=PPE_SAFETY_HELMET$/);
  await expect(menu).not.toHaveAttribute("open", "");
});
