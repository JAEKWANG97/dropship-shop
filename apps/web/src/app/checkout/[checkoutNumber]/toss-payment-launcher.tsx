"use client";

import { useMemo, useState } from "react";
import { formatPrice } from "@/lib/catalog";

const TOSS_SDK_SCRIPT_ID = "toss-payments-sdk";
const TOSS_SDK_URL = "https://js.tosspayments.com/v1/payment";

type TossPaymentsInstance = {
  requestPayment: (
    method: string,
    options: {
      amount: number;
      orderId: string;
      orderName: string;
      successUrl: string;
      failUrl: string;
    },
  ) => Promise<void>;
};

declare global {
  interface Window {
    TossPayments?: (clientKey: string) => TossPaymentsInstance;
  }
}

type TossPaymentLauncherProps = {
  clientKey: string;
  checkoutNumber: string;
  amount: number;
  orderName: string;
};

function loadTossSdk() {
  if (window.TossPayments) {
    return Promise.resolve();
  }

  const existingScript = document.getElementById(TOSS_SDK_SCRIPT_ID) as
    | HTMLScriptElement
    | null;

  if (existingScript) {
    return new Promise<void>((resolve, reject) => {
      existingScript.addEventListener("load", () => resolve(), { once: true });
      existingScript.addEventListener("error", () => reject(new Error("Toss SDK load failed")), {
        once: true,
      });
    });
  }

  return new Promise<void>((resolve, reject) => {
    const script = document.createElement("script");
    script.id = TOSS_SDK_SCRIPT_ID;
    script.src = TOSS_SDK_URL;
    script.async = true;
    script.addEventListener("load", () => resolve(), { once: true });
    script.addEventListener("error", () => reject(new Error("Toss SDK load failed")), {
      once: true,
    });
    document.head.appendChild(script);
  });
}

export function TossPaymentLauncher({
  clientKey,
  checkoutNumber,
  amount,
  orderName,
}: TossPaymentLauncherProps) {
  const [isOpening, setIsOpening] = useState(false);
  const [error, setError] = useState("");
  const encodedCheckoutNumber = useMemo(
    () => encodeURIComponent(checkoutNumber),
    [checkoutNumber],
  );

  async function openPaymentWindow() {
    if (!clientKey) {
      return;
    }

    setError("");
    setIsOpening(true);

    try {
      await loadTossSdk();
      const tossPayments = window.TossPayments?.(clientKey);

      if (!tossPayments) {
        throw new Error("Toss SDK is not ready");
      }

      await tossPayments.requestPayment("카드", {
        amount,
        orderId: checkoutNumber,
        orderName,
        successUrl: `${window.location.origin}/checkout/payment/success?checkoutNumber=${encodedCheckoutNumber}`,
        failUrl: `${window.location.origin}/checkout/payment/fail?checkoutNumber=${encodedCheckoutNumber}`,
      });
    } catch {
      setError("결제창을 열지 못했습니다. 잠시 후 다시 시도해 주세요.");
      setIsOpening(false);
    }
  }

  return (
    <div className="checkout-form">
      <h2>Toss 결제</h2>
      <p>결제창에서 승인하면 서버가 결제 금액과 주문 상태를 다시 확인합니다.</p>
      <div className="summary-list compact">
        <div>
          <span>주문번호</span>
          <strong>{checkoutNumber}</strong>
        </div>
        <div>
          <span>결제 금액</span>
          <strong>{formatPrice(amount)}</strong>
        </div>
      </div>
      {!clientKey ? (
        <div className="notice">
          <strong>결제 준비 중</strong>
          <span>현재 결제 설정이 완료되지 않아 결제창을 열 수 없습니다.</span>
        </div>
      ) : null}
      {error ? (
        <div className="notice">
          <strong>결제창 오류</strong>
          <span>{error}</span>
        </div>
      ) : null}
      <button
        className="button primary"
        disabled={!clientKey || isOpening}
        onClick={openPaymentWindow}
        type="button"
      >
        {isOpening ? "결제창 여는 중" : "Toss 결제하기"}
      </button>
    </div>
  );
}
