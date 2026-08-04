"use client";

import { useState } from "react";
import { addCartItem } from "@/app/cart/actions";
import { SubmitButton } from "@/app/submit-button";

type PurchaseOption = {
  id: string;
  name: string;
  additionalPrice: number;
};

type PurchaseFormProps = {
  basePrice: number;
  formId: string;
  minimumOrderQuantity: number;
  mobile?: boolean;
  options: PurchaseOption[];
  orderQuantityStep: number;
  productId: string;
};

function formatPrice(value: number) {
  return `${value.toLocaleString("ko-KR")}원`;
}

function quantityError(quantity: number, minimum: number, step: number) {
  if (!Number.isInteger(quantity)) return "수량은 정수로 입력해 주세요.";
  if (quantity < minimum) return `최소 ${minimum}개부터 주문할 수 있습니다.`;
  if (quantity > 99) return "최대 99개까지 주문할 수 있습니다.";
  if (quantity % step !== 0) {
    const first = Math.ceil(minimum / step) * step;
    return `${step}개 단위로 입력해 주세요. 예: ${first}, ${first + step}, ${first + step * 2}`;
  }
  return "";
}

function optionLabel(option: PurchaseOption) {
  return option.additionalPrice === 0
    ? `${option.name} 추가금 없음`
    : `${option.name} +${formatPrice(option.additionalPrice)}`;
}

export function PurchaseForm({
  basePrice,
  formId,
  minimumOrderQuantity,
  mobile = false,
  options,
  orderQuantityStep,
  productId,
}: PurchaseFormProps) {
  const [optionId, setOptionId] = useState(options[0]?.id ?? "");
  const [quantityText, setQuantityText] = useState(String(minimumOrderQuantity));
  const selectedOption = options.find((option) => option.id === optionId) ?? options[0];
  const quantity = Number(quantityText);
  const error = quantityError(quantity, minimumOrderQuantity, orderQuantityStep);
  const unitPrice = basePrice + (selectedOption?.additionalPrice ?? 0);
  const totalAmount = error ? 0 : unitPrice * quantity;
  const errorId = `${formId}-quantity-error`;

  return (
    <form
      action={addCartItem}
      className={`cart-add-form ${mobile ? "mobile-purchase-form" : "desktop-purchase-form"}`}
      id={formId}
    >
      <input name="productId" type="hidden" value={productId} />
      <label>
        옵션
        <select
          name="productOptionId"
          required
          value={optionId}
          onChange={(event) => setOptionId(event.target.value)}
        >
          {options.map((option) => (
            <option key={option.id} value={option.id}>
              {optionLabel(option)}
            </option>
          ))}
        </select>
      </label>
      <label>
        수량
        <input
          aria-describedby={error ? errorId : undefined}
          aria-invalid={Boolean(error)}
          max="99"
          min={minimumOrderQuantity}
          name="quantity"
          step={orderQuantityStep}
          type="number"
          value={quantityText}
          onChange={(event) => setQuantityText(event.target.value)}
        />
        <span className={error ? "quantity-error" : "quantity-help"} id={errorId} aria-live="polite">
          {error || `${orderQuantityStep}개 단위로 주문할 수 있습니다.`}
        </span>
      </label>
      <dl className="purchase-quantity-summary">
        <div>
          <dt>개당 가격</dt>
          <dd>{formatPrice(unitPrice)}</dd>
        </div>
        <div>
          <dt>최소 주문 수량</dt>
          <dd>{minimumOrderQuantity}개</dd>
        </div>
        <div>
          <dt>주문 단위</dt>
          <dd>{orderQuantityStep}개</dd>
        </div>
        <div>
          <dt>총 상품금액</dt>
          <dd>{error ? "수량 확인 필요" : formatPrice(totalAmount)}</dd>
        </div>
      </dl>
      <div className="product-action-row">
        <SubmitButton
          className="button"
          disabled={Boolean(error)}
          name="intent"
          pendingLabel="담는 중..."
          value="cart"
        >
          장바구니
        </SubmitButton>
        <SubmitButton
          className="button primary"
          disabled={Boolean(error)}
          name="intent"
          pendingLabel="이동 중..."
          value="checkout"
        >
          바로구매
        </SubmitButton>
      </div>
    </form>
  );
}
