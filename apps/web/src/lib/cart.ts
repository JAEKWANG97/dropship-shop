import { cookies } from "next/headers";
import { apiGetWithCookie } from "./api";
import type { ProductOptionStatus, ProductStatus } from "./catalog";

export type CartItem = {
  id: string;
  productId: string;
  productOptionId: string;
  productName: string;
  optionName: string;
  quantity: number;
  unitPrice: number;
  lineAmount: number;
  productStatus: ProductStatus;
  optionStatus: ProductOptionStatus;
  thumbnailImageUrl: string | null;
  sellable: boolean;
  unavailableReason: string | null;
};

export type CartIssue = {
  cartItemId: string;
  code: string;
  message: string;
};

export type Cart = {
  cartId: string;
  items: CartItem[];
  subtotalAmount: number;
  checkoutAvailable: boolean;
  issues: CartIssue[];
};

export type CartValidation = {
  checkoutAvailable: boolean;
  issues: CartIssue[];
};

export async function getCart() {
  return apiGetWithCookie<Cart>("/api/cart", (await cookies()).toString());
}
