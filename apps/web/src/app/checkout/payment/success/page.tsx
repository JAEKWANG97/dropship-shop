import Link from "next/link";
import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { confirmTossPaymentRequest } from "@/lib/payments";

type PaymentSuccessPageProps = {
  searchParams: Promise<{
    checkoutNumber?: string;
    orderId?: string;
    paymentKey?: string;
    amount?: string;
  }>;
};

export default async function PaymentSuccessPage({ searchParams }: PaymentSuccessPageProps) {
  const params = await searchParams;
  const checkoutNumber = params.checkoutNumber ?? params.orderId ?? "";
  const orderIdMatches = !params.checkoutNumber || !params.orderId || params.checkoutNumber === params.orderId;
  const paymentKey = params.paymentKey ?? "";
  const amount = Number(params.amount ?? "0");
  const canConfirm = Boolean(checkoutNumber && paymentKey && Number.isFinite(amount) && amount > 0 && orderIdMatches);

  if (canConfirm) {
    try {
      await confirmTossPaymentRequest(checkoutNumber, paymentKey, amount);
      revalidatePath(`/checkout/${checkoutNumber}`);
      revalidatePath("/orders");
    } catch {
      redirect(`/checkout/payment/exception?checkoutNumber=${encodeURIComponent(checkoutNumber)}`);
    }
  }

  return (
    <section className="narrow-page">
      <p className="eyebrow">Payment</p>
      <h1>{canConfirm ? "결제가 완료되었습니다" : "결제 승인 정보를 확인할 수 없습니다"}</h1>
      {canConfirm ? (
        <p>결제 승인을 서버에서 확인했고 주문이 공급처 발주 대기로 이동했습니다.</p>
      ) : !orderIdMatches ? (
        <p>결제창에서 돌아온 주문번호가 현재 주문서와 일치하지 않습니다. 주문서에서 다시 확인해 주세요.</p>
      ) : (
        <p>결제 승인 파라미터가 없거나 올바르지 않습니다. 주문서에서 다시 결제를 시도해 주세요.</p>
      )}
      <Link className="button primary" href={checkoutNumber ? `/checkout/${checkoutNumber}` : "/orders"}>
        상태 확인
      </Link>
      <Link className="button" href="/orders">
        주문 내역
      </Link>
    </section>
  );
}
