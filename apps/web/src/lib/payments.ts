import { cookies } from "next/headers";
import { apiSendWithCookie } from "./api";

export async function confirmTossPaymentRequest(
  checkoutNumber: string,
  paymentKey: string,
  amount: number,
) {
  return apiSendWithCookie(
    "/api/payments/toss/confirm",
    (await cookies()).toString(),
    {
      method: "POST",
      body: JSON.stringify({
        checkoutNumber,
        paymentKey,
        amount,
      }),
    },
  );
}
