"use client";

import { useEffect, useState } from "react";
import { publicApiUrl } from "@/lib/api";

type Inquiry = {
  subject: string;
  message: string;
  status: "RECEIVED" | "IN_PROGRESS" | "ANSWERED" | "CLOSED";
  answer: string | null;
  createdAt: string;
  answeredAt: string | null;
};

const statusLabel = {
  RECEIVED: "접수",
  IN_PROGRESS: "처리 중",
  ANSWERED: "답변 완료",
  CLOSED: "종료",
};

export function InquiryLookup({ inquiryId }: { inquiryId: string }) {
  const [inquiry, setInquiry] = useState<Inquiry | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    const token = new URLSearchParams(window.location.hash.slice(1)).get("token");
    if (!token) {
      Promise.resolve().then(() => setError("유효한 문의 조회 링크가 필요합니다."));
      return;
    }

    fetch(publicApiUrl(`/api/customer-inquiries/${inquiryId}/lookup`), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ lookupToken: token }),
      cache: "no-store",
    })
      .then(async (response) => {
        if (!response.ok) throw new Error();
        setInquiry((await response.json()) as Inquiry);
      })
      .catch(() => setError("문의 정보를 확인할 수 없습니다. 접수 시 받은 링크를 다시 확인해 주세요."));
  }, [inquiryId]);

  if (error) {
    return <div className="notice danger">{error}</div>;
  }
  if (!inquiry) {
    return <div className="notice">문의 정보를 불러오는 중입니다.</div>;
  }

  return (
    <div className="inquiry-lookup-content">
      <div className="inquiry-lookup-head">
        <strong>{inquiry.subject}</strong>
        <span>{statusLabel[inquiry.status]}</span>
      </div>
      <section>
        <h2>문의 내용</h2>
        <p>{inquiry.message}</p>
        <time dateTime={inquiry.createdAt}>{new Date(inquiry.createdAt).toLocaleString("ko-KR")}</time>
      </section>
      <section>
        <h2>답변</h2>
        <p>{inquiry.answer ?? "운영자가 문의를 확인하고 있습니다."}</p>
        {inquiry.answeredAt ? (
          <time dateTime={inquiry.answeredAt}>{new Date(inquiry.answeredAt).toLocaleString("ko-KR")}</time>
        ) : null}
      </section>
    </div>
  );
}
