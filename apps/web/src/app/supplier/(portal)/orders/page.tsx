"use client";

import { useEffect, useState } from "react";
import { SupplierOrdersView } from "./order-views";
import {
  listSupplierOrders,
  SupplierOrderApiError,
  type SupplierOrderSummary,
} from "@/lib/supplier-orders";

export default function SupplierOrdersPage() {
  const [orders, setOrders] = useState<SupplierOrderSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    listSupplierOrders()
      .then((value) => active && setOrders(value))
      .catch((reason) => active && setError(orderErrorMessage(reason)))
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, []);

  return <SupplierOrdersView orders={orders} loading={loading} error={error} />;
}

function orderErrorMessage(error: unknown) {
  if (error instanceof SupplierOrderApiError && error.status === 403) {
    return "현재 계약 또는 포털 권한으로는 출고 요청을 확인할 수 없습니다. Coreable에 문의해 주세요.";
  }
  return "잠시 뒤 다시 시도해 주세요.";
}
