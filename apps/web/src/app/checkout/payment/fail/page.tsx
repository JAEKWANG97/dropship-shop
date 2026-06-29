import Link from "next/link";

type PaymentFailPageProps = {
  searchParams: Promise<{
    checkoutNumber?: string;
    orderId?: string;
    code?: string;
    message?: string;
  }>;
};

export default async function PaymentFailPage({ searchParams }: PaymentFailPageProps) {
  const params = await searchParams;
  const checkoutNumber = params.checkoutNumber ?? params.orderId;

  return (
    <section className="narrow-page">
      <p className="eyebrow">Payment</p>
      <h1>결제가 완료되지 않았습니다</h1>
      <p>{params.message ?? "Toss 결제창에서 실패 또는 취소가 발생했습니다."}</p>
      {params.code ? <p>오류 코드: {params.code}</p> : null}
      <Link className="button primary" href={checkoutNumber ? `/checkout/${checkoutNumber}` : "/cart"}>
        다시 시도
      </Link>
    </section>
  );
}
