import Link from "next/link";

type PaymentExceptionPageProps = {
  searchParams: Promise<{ checkoutNumber?: string }>;
};

export default async function PaymentExceptionPage({ searchParams }: PaymentExceptionPageProps) {
  const { checkoutNumber } = await searchParams;

  return (
    <section className="narrow-page">
      <p className="eyebrow">Payment</p>
      <h1>결제 확인이 보류되었습니다</h1>
      <p>
        승인 금액, 만료, 정책 확인, 상품 판매 상태 중 하나가 맞지 않아 서버 검토가 필요할 수 있습니다.
      </p>
      <Link className="button primary" href={checkoutNumber ? `/checkout/${checkoutNumber}` : "/orders"}>
        상태 확인
      </Link>
    </section>
  );
}
