import Link from "next/link";

type PaymentExceptionPageProps = {
  searchParams: Promise<{ checkoutNumber?: string; status?: string }>;
};

export default async function PaymentExceptionPage({ searchParams }: PaymentExceptionPageProps) {
  const { checkoutNumber, status } = await searchParams;
  const message = exceptionMessage(status);

  return (
    <section className="narrow-page">
      <p className="eyebrow">Payment</p>
      <h1>결제 확인이 보류되었습니다</h1>
      <p>{message}</p>
      <Link className="button primary" href={checkoutNumber ? `/checkout/${checkoutNumber}` : "/orders"}>
        상태 확인
      </Link>
      <Link className="button" href="/orders">
        주문 내역
      </Link>
    </section>
  );
}

function exceptionMessage(status?: string) {
  if (status === "401" || status === "403") {
    return "로그인 세션 또는 권한 문제로 결제 승인을 확인하지 못했습니다. 다시 로그인한 뒤 주문서 상태를 확인해 주세요.";
  }
  if (status === "502") {
    return "Toss Payments 승인 확인 요청이 실패했습니다. 결제가 승인되었을 수 있으니 주문서 상태를 먼저 확인해 주세요.";
  }
  if (status === "400") {
    return "결제 금액, 주문서 만료, 정책 확인, 상품 판매 상태 중 하나가 맞지 않아 주문 확정이 보류되었습니다.";
  }
  return "서버가 결제 승인을 확정하지 못했습니다. 중복 결제를 피하려면 주문서 상태를 먼저 확인해 주세요.";
}
