/** @jsxImportSource react */

import Link from "next/link";
import type { ReactNode } from "react";
import {
  supplierOrderStatusView,
  type SupplierOrderDetail,
  type SupplierOrderSummary,
} from "@/lib/supplier-orders";

export function SupplierOrdersView({
  orders,
  loading,
  error,
}: {
  orders: SupplierOrderSummary[];
  loading: boolean;
  error: string;
}) {
  return (
    <div className="supplier-page">
      <div className="admin-heading">
        <div>
          <h1>출고 요청</h1>
          <p>입금 확인이 끝난 주문만 표시됩니다. 주문을 열어 배송에 필요한 정보만 확인하세요.</p>
        </div>
      </div>

      {error ? <div className="notice danger"><strong>출고 요청을 불러오지 못했습니다</strong><span>{error}</span></div> : null}
      <section className="admin-panel">
        <div className="admin-panel-head"><h2>처리할 주문</h2><span>{loading ? "불러오는 중" : `${orders.length}건`}</span></div>
        <div className="admin-inquiry-list">
          {orders.map((order) => {
            const status = supplierOrderStatusView(order.status);
            return (
              <Link className="admin-inquiry-card" href={`/supplier/orders/${encodeURIComponent(order.orderNumber)}`} key={order.orderNumber}>
                <div><strong>{order.orderNumber}</strong><span className={`admin-badge ${status.tone}`}>{status.label}</span></div>
                <dl>
                  <div><dt>상품</dt><dd>{itemSummary(order)}</dd></div>
                  <div><dt>총 수량</dt><dd>{order.items.reduce((total, item) => total + item.quantity, 0)}개</dd></div>
                  <div><dt>요청시각</dt><dd>{dateTime(order.requestedAt)}</dd></div>
                </dl>
              </Link>
            );
          })}
          {!loading && !error && orders.length === 0 ? (
            <div className="admin-empty compact"><strong>처리할 출고 요청이 없습니다</strong><span>새 주문이 입금 확인되면 이곳에 표시됩니다.</span></div>
          ) : null}
        </div>
      </section>
    </div>
  );
}

export function SupplierOrderDetailView({
  order,
  children,
}: {
  order: SupplierOrderDetail;
  children?: ReactNode;
}) {
  const status = supplierOrderStatusView(order.status);
  const masked = order.piiAccessLevel !== "FULL";

  return (
    <div className="supplier-page">
      <div className="admin-heading">
        <div>
          <Link className="admin-text-link" href="/supplier/orders">출고 요청 목록</Link>
          <h1>{order.orderNumber}</h1>
          <p>이 주문의 배송에 필요한 정보만 표시됩니다.</p>
        </div>
        <span className={`admin-badge ${status.tone}`}>{status.label}</span>
      </div>

      {masked ? (
        <div className="notice">
          <strong>배송정보가 가려졌습니다</strong>
          <span>현재 Coreable에서 후속 처리를 맡아 주소와 배송 메모를 표시하지 않습니다.</span>
        </div>
      ) : (
        <div className="notice">
          <strong>배송 목적으로만 확인해 주세요</strong>
          <span>수령 정보는 출고 처리에만 사용하고 별도로 저장하지 마세요.</span>
        </div>
      )}

      <section className="admin-panel">
        <div className="admin-panel-head"><h2>요청 정보</h2><span>{order.piiAccessLevel === "FULL" ? "배송정보 열람 가능" : "배송정보 가림"}</span></div>
        <dl className="summary-list">
          <Row label="처리 상태" value={status.label} />
          <Row label="요청시각" value={dateTime(order.requestedAt)} />
          <Row label="배송정보 열람 종료" value={dateTime(order.piiAccessUntil)} />
        </dl>
      </section>

      <section className="admin-panel">
        <div className="admin-panel-head"><h2>받는 사람</h2><span>최소 배송정보</span></div>
        <dl className="summary-list">
          <Row label="이름" value={order.recipient.name ?? "-"} />
          <Row label="연락처" value={order.recipient.phone ?? "-"} />
          {!masked ? <Row label="주소" value={address(order)} /> : null}
          {!masked ? <Row label="배송 메모" value={order.recipient.deliveryMemo ?? "없음"} /> : null}
        </dl>
      </section>

      <section className="admin-panel">
        <div className="admin-panel-head"><h2>주문 상품</h2><span>{order.items.length}개 항목</span></div>
        <div className="admin-list">
          {order.items.map((item) => (
            <div key={item.orderItemId}>
              <strong>{item.productName || "상품"} / {item.optionName || "기본"}</strong>
              <span>주문 {item.quantity}개 · 할당 {item.allocatedQuantity}개 · 남음 {item.remainingQuantity}개</span>
            </div>
          ))}
        </div>
      </section>

      {children}
    </div>
  );
}

function itemSummary(order: SupplierOrderSummary) {
  const first = order.items[0];
  if (!first) return "상품 정보 확인 필요";
  const suffix = order.items.length > 1 ? ` 외 ${order.items.length - 1}건` : "";
  return `${first.productName || "상품"} / ${first.optionName || "기본"}${suffix}`;
}

function Row({ label, value }: { label: string; value: string }) {
  return <div><dt>{label}</dt><dd>{value}</dd></div>;
}

function address(order: SupplierOrderDetail) {
  const recipient = order.recipient;
  return [recipient.postalCode ? `(${recipient.postalCode})` : "", recipient.address1, recipient.address2]
    .filter(Boolean)
    .join(" ") || "-";
}

function dateTime(value: string | null) {
  return value ? new Date(value).toLocaleString("ko-KR") : "-";
}
