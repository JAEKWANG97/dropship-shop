import Link from "next/link";
import { confirmTossPayment } from "../../actions";

type PaymentSuccessPageProps = {
  searchParams: Promise<{
    checkoutNumber?: string;
    paymentKey?: string;
    amount?: string;
  }>;
};

export default async function PaymentSuccessPage({ searchParams }: PaymentSuccessPageProps) {
  const params = await searchParams;
  const canConfirm = params.checkoutNumber && params.paymentKey && params.amount;

  return (
    <section className="narrow-page">
      <p className="eyebrow">Payment</p>
      <h1>결제 승인 확인</h1>
      {canConfirm ? (
        <form action={confirmTossPayment} className="checkout-form">
          <input name="checkoutNumber" type="hidden" value={params.checkoutNumber} />
          <input name="paymentKey" type="hidden" value={params.paymentKey} />
          <input name="amount" type="hidden" value={params.amount} />
          <p>Toss 승인 정보를 서버에 확인 요청합니다.</p>
          <button className="button primary" type="submit">
            서버 승인 확인
          </button>
        </form>
      ) : (
        <p>결제 승인 파라미터가 없으면 주문서에서 직접 paymentKey를 입력해 확인할 수 있습니다.</p>
      )}
      <Link className="button" href={params.checkoutNumber ? `/checkout/${params.checkoutNumber}` : "/orders"}>
        주문서로 돌아가기
      </Link>
    </section>
  );
}
