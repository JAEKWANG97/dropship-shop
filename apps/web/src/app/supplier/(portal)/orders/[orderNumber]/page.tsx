"use client";

import Link from "next/link";
import { use, useEffect, useState } from "react";
import { SupplierOrderDetailView } from "../order-views";
import {
  getSupplierOrder,
  SupplierOrderApiError,
  type SupplierOrderDetail,
} from "@/lib/supplier-orders";

type PageProps = { params: Promise<{ orderNumber: string }> };

export default function SupplierOrderDetailPage({ params }: PageProps) {
  const { orderNumber } = use(params);
  const [order, setOrder] = useState<SupplierOrderDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    getSupplierOrder(orderNumber)
      .then((value) => active && setOrder(value))
      .catch((reason) => active && setError(orderErrorMessage(reason)))
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [orderNumber]);

  if (loading) return <div className="supplier-page"><div className="notice">출고 요청을 불러오는 중입니다.</div></div>;
  if (!order) return <div className="supplier-page"><div className="notice danger">{error}</div><Link className="button" href="/supplier/orders">출고 요청 목록</Link></div>;

  return <SupplierOrderDetailView order={order} />;
}

function orderErrorMessage(error: unknown) {
  if (error instanceof SupplierOrderApiError && error.status === 403) {
    return "현재 계약 또는 포털 권한으로는 이 출고 요청을 확인할 수 없습니다.";
  }
  if (error instanceof SupplierOrderApiError && error.status === 404) {
    return "출고 요청을 찾을 수 없습니다.";
  }
  return "출고 요청을 불러오지 못했습니다. 잠시 뒤 다시 시도해 주세요.";
}
