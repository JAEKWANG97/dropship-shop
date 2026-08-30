"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { use, useEffect, useRef, useState, type FormEvent } from "react";
import { SupplierOrderDetailView } from "../order-views";
import {
  listSupplierShortageReports,
  reportSupplierShortage,
  SHORTAGE_REASON_CODES,
  type ShortageReasonCode,
} from "@/lib/supplier-claims";
import {
  correctSupplierShipment,
  createSupplierShipment,
  getSupplierOrder,
  loadSupplierShipmentRefresh,
  listSupplierCarriers,
  listSupplierShipments,
  releaseSupplierCommandKey,
  releaseSupplierShipmentCommandKey,
  recoverSupplierShipmentConflict,
  SupplierOrderApiError,
  supplierCommandKey,
  supplierOrderStatusView,
  supplierShortageReportingAllowed,
  supplierShipmentCommandKey,
  supplierShipmentRegistrationAllowed,
  type ShipmentAllocation,
  type SupplierCarrier,
  type SupplierOrderDetail,
  type SupplierShipment,
  type SupplierShipmentCollection,
} from "@/lib/supplier-orders";

type PageProps = { params: Promise<{ orderNumber: string }> };

function unavailableShipmentState(): SupplierShipmentCollection {
  return {
    shipments: [],
    unallocatedItems: [],
    allocationComplete: null,
    canRegisterShipment: false,
    canReportShortage: false,
    nextAction: "CONTACT_COREABLE",
  };
}

export default function SupplierOrderDetailPage({ params }: PageProps) {
  const { orderNumber } = use(params);
  return <SupplierOrderDetailContent key={orderNumber} orderNumber={orderNumber} />;
}

function SupplierOrderDetailContent({ orderNumber }: { orderNumber: string }) {
  const router = useRouter();
  const [order, setOrder] = useState<SupplierOrderDetail | null>(null);
  const [carriers, setCarriers] = useState<SupplierCarrier[]>([]);
  const [shipmentState, setShipmentState] = useState<SupplierShipmentCollection>({
    shipments: [],
    unallocatedItems: [],
    allocationComplete: null,
    canRegisterShipment: null,
    canReportShortage: null,
    nextAction: null,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [shipmentError, setShipmentError] = useState("");
  const [shortageError, setShortageError] = useState("");
  const [splitShipment, setSplitShipment] = useState(false);
  const [busy, setBusy] = useState(false);
  const commandKeys = useRef(new Map<string, string>());

  useEffect(() => {
    let active = true;
    Promise.allSettled([
      getSupplierOrder(orderNumber),
      listSupplierCarriers(),
      listSupplierShipments(orderNumber),
    ])
      .then(([orderResult, carrierResult, shipmentResult]) => {
        if (!active) return;
        if (orderResult.status === "rejected") {
          setOrder(null);
          setError(orderErrorMessage(orderResult.reason));
          return;
        }
        setOrder(orderResult.value);
        if (carrierResult.status === "fulfilled") {
          setCarriers(carrierResult.value);
        }
        if (shipmentResult.status === "fulfilled") {
          setShipmentState(shipmentResult.value);
        } else {
          setShipmentState(unavailableShipmentState());
        }
        if (carrierResult.status === "rejected" || shipmentResult.status === "rejected") {
          setShipmentError("송장 기능을 불러오지 못했습니다. 주문 정보는 확인할 수 있지만 송장 처리는 잠시 뒤 다시 시도해 주세요.");
        }
      })
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [orderNumber]);

  if (loading) return <div className="supplier-page"><div className="notice">출고 요청을 불러오는 중입니다.</div></div>;
  if (!order) return <div className="supplier-page"><div className="notice danger">{error}</div><Link className="button" href="/supplier/orders">출고 요청 목록</Link></div>;

  async function refresh() {
    const refreshed = await loadSupplierShipmentRefresh(orderNumber);
    if (refreshed.order) {
      setOrder(refreshed.order);
      setError("");
    } else {
      setOrder(null);
      setError(orderErrorMessage(refreshed.orderError));
    }
    if (refreshed.shipmentState) {
      setShipmentState(refreshed.shipmentState);
    } else {
      setShipmentState(unavailableShipmentState());
    }
    if (refreshed.orderError || refreshed.shipmentError) {
      throw new Error("Supplier shipment state refresh was incomplete");
    }
  }

  async function handleMutationFailure(reason: unknown) {
    if (reason instanceof SupplierOrderApiError && [403, 404, 409].includes(reason.status)) {
      setShipmentState(unavailableShipmentState());
      if (reason.status === 403 || reason.status === 404) {
        setOrder(null);
        setError(orderErrorMessage(reason));
      }
    }
    try {
      await recoverSupplierShipmentConflict(reason, refresh);
      setShipmentError(shipmentMutationError(reason));
    } catch {
      setShipmentError(`${shipmentMutationError(reason)} 최신 상태를 불러오지 못했습니다. 페이지를 새로고침해 주세요.`);
    }
  }

  async function registerShipment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const explicitAllocation = splitShipment || shipmentState.shipments.length > 0;
    const allocations = explicitAllocation ? shipmentAllocations(data) : undefined;
    if (explicitAllocation && allocations?.length === 0) {
      setShipmentError("분할 또는 추가 송장은 한 개 이상의 상품 수량을 입력해 주세요.");
      return;
    }

    const action = "create";
    setBusy(true);
    setShipmentError("");
    try {
      await createSupplierShipment(orderNumber, {
        carrierCode: field(data, "carrierCode"),
        trackingNumber: field(data, "trackingNumber"),
        ...(allocations ? { allocations } : {}),
      }, supplierShipmentCommandKey(commandKeys.current, action));
      await refresh();
      commandKeys.current.delete(action);
      form.reset();
      setSplitShipment(false);
    } catch (reason) {
      releaseSupplierShipmentCommandKey(commandKeys.current, action, reason);
      await handleMutationFailure(reason);
    } finally {
      setBusy(false);
    }
  }

  async function correctShipment(event: FormEvent<HTMLFormElement>, shipment: SupplierShipment) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const action = `correct:${shipment.shipmentId}`;
    if (shipment.version === null) {
      setShipmentState(unavailableShipmentState());
      setShipmentError("송장 버전 정보를 확인할 수 없어 정정할 수 없습니다. 페이지를 새로고침해 주세요.");
      return;
    }
    setBusy(true);
    setShipmentError("");
    try {
      await correctSupplierShipment(orderNumber, shipment.shipmentId, {
        expectedVersion: shipment.version,
        carrierCode: field(data, "carrierCode"),
        trackingNumber: field(data, "trackingNumber"),
        reason: field(data, "reason"),
      }, supplierShipmentCommandKey(commandKeys.current, action));
      await refresh();
      commandKeys.current.delete(action);
    } catch (reason) {
      releaseSupplierShipmentCommandKey(commandKeys.current, action, reason);
      await handleMutationFailure(reason);
    } finally {
      setBusy(false);
    }
  }

  async function reportShortage(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const reasonCode = field(data, "reasonCode") as ShortageReasonCode;
    if (!SHORTAGE_REASON_CODES.includes(reasonCode)) {
      setShortageError("품절 보고 사유를 다시 선택해 주세요.");
      return;
    }

    const action = `shortage:${reasonCode}`;
    setBusy(true);
    setShortageError("");
    try {
      const report = await reportSupplierShortage(
        orderNumber,
        reasonCode,
        supplierCommandKey(commandKeys.current, action),
      );
      commandKeys.current.delete(action);
      router.push(`/supplier/shortage-reports/${encodeURIComponent(report.reportId)}`);
    } catch (reason) {
      releaseSupplierCommandKey(commandKeys.current, action, reason);
      if (reason instanceof SupplierOrderApiError && [403, 404, 409].includes(reason.status)) {
        try {
          const existing = (await listSupplierShortageReports())
            .find((report) => report.orderNumber === orderNumber);
          if (existing) {
            router.push(`/supplier/shortage-reports/${encodeURIComponent(existing.reportId)}`);
            return;
          }
          const refreshed = await loadSupplierShipmentRefresh(orderNumber);
          setShipmentState(refreshed.shipmentState ?? unavailableShipmentState());
        } catch {
          setShipmentState(unavailableShipmentState());
        }
      }
      setShortageError(shortageMutationError(reason));
    } finally {
      setBusy(false);
    }
  }

  return (
    <SupplierOrderDetailView order={order}>
      <SupplierShortagePanel
        busy={busy}
        error={shortageError}
        onSubmit={reportShortage}
        shipmentState={shipmentState}
      />
      <SupplierShipmentPanel
        busy={busy}
        carriers={carriers}
        onCorrect={correctShipment}
        onRegister={registerShipment}
        order={order}
        setSplitShipment={setSplitShipment}
        shipmentError={shipmentError}
        shipmentState={shipmentState}
        splitShipment={splitShipment}
      />
    </SupplierOrderDetailView>
  );
}

function SupplierShortagePanel({
  busy,
  error,
  onSubmit,
  shipmentState,
}: {
  busy: boolean;
  error: string;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  shipmentState: SupplierShipmentCollection;
}) {
  const allowed = supplierShortageReportingAllowed(shipmentState);
  if (!allowed && !error) return null;

  return (
    <section className="admin-panel">
      <div className="admin-panel-head"><h2>품절 보고</h2><span>배송 그룹 주문 전체</span></div>
      {error ? <div className="notice danger"><strong>품절 보고를 완료하지 못했습니다</strong><span>{error}</span></div> : null}
      {allowed ? (
        <form className="admin-inline-form" onSubmit={onSubmit}>
          <label className="wide">
            품절 보고 사유
            <select defaultValue="OUT_OF_STOCK" name="reasonCode" required>
              <option value="OUT_OF_STOCK">상품 전체 품절</option>
              <option value="OPTION_UNAVAILABLE">주문 옵션 품절</option>
              <option value="QUANTITY_UNAVAILABLE">주문 수량 확보 불가</option>
            </select>
            <span className="field-help">보고하면 출고 요청이 Coreable로 인계됩니다. 고객 환불은 Coreable 검토 뒤 별도로 진행됩니다.</span>
          </label>
          <button className="button" disabled={busy} type="submit">품절 보고</button>
        </form>
      ) : (
        <div className="notice"><strong>현재 품절을 보고할 수 없습니다</strong><span>최신 상태는 품절 보고 내역에서 확인해 주세요.</span></div>
      )}
    </section>
  );
}

function SupplierShipmentPanel({
  busy,
  carriers,
  onCorrect,
  onRegister,
  order,
  setSplitShipment,
  shipmentError,
  shipmentState,
  splitShipment,
}: {
  busy: boolean;
  carriers: SupplierCarrier[];
  onCorrect: (event: FormEvent<HTMLFormElement>, shipment: SupplierShipment) => void;
  onRegister: (event: FormEvent<HTMLFormElement>) => void;
  order: SupplierOrderDetail;
  setSplitShipment: (value: boolean) => void;
  shipmentError: string;
  shipmentState: SupplierShipmentCollection;
  splitShipment: boolean;
}) {
  const hasShipmentHistory = shipmentState.shipments.length > 0;
  const remainingItems = shipmentState.unallocatedItems.length > 0
    ? shipmentState.unallocatedItems
    : order.items.filter((item) => item.remainingQuantity > 0);
  const canRegister = supplierShipmentRegistrationAllowed(shipmentState)
    && remainingItems.length > 0;
  const explicitAllocation = splitShipment || hasShipmentHistory;

  return (
    <section className="admin-panel">
      <div className="admin-panel-head">
        <h2>송장 등록</h2>
        <span>{shipmentState.shipments.filter((shipment) => shipment.status !== "VOIDED").length}건 유효</span>
      </div>

      {shipmentError ? <div className="notice danger"><strong>송장 처리를 완료하지 못했습니다</strong><span>{shipmentError}</span></div> : null}

      <div className="admin-list supplier-shipment-list">
        {shipmentState.shipments.map((shipment) => (
          <article className="supplier-shipment-card" key={shipment.shipmentId}>
            <div className="admin-panel-head">
              <strong>{shipment.carrierName || shipment.carrierCode || "택배사 확인 필요"} · {shipment.trackingNumber}</strong>
              <span className={`admin-badge ${shipment.status === "DELIVERED" ? "success" : "neutral"}`}>
                {shipmentStatusLabel(shipment.status)}
              </span>
            </div>
            <span>등록시각 {dateTime(shipment.registeredAt)}</span>
            <span>{allocationText(shipment, order)}</span>
            {shipment.officialTrackingUrl ? (
              <a className="button" href={shipment.officialTrackingUrl} target="_blank" rel="noreferrer">
                배송조회
              </a>
            ) : <span>공식 배송조회 링크를 제공하지 않는 택배사입니다.</span>}
            {shipment.editable ? (
              <form className="admin-inline-form" onSubmit={(event) => onCorrect(event, shipment)}>
                <CarrierSelect carriers={carriers} defaultValue={shipment.carrierCode ?? ""} />
                <label>
                  송장번호
                  <input defaultValue={shipment.trackingNumber} maxLength={100} name="trackingNumber" required />
                </label>
                <label className="wide">
                  정정 사유
                  <input maxLength={200} name="reason" required placeholder="예: 송장번호 오입력" />
                </label>
                <button className="button" disabled={busy} type="submit">택배사·송장 정정</button>
              </form>
            ) : null}
          </article>
        ))}
        {shipmentState.shipments.length === 0 ? (
          <div className="admin-empty compact"><strong>등록된 송장이 없습니다</strong><span>택배사와 송장번호를 등록해 주세요.</span></div>
        ) : null}
      </div>

      {canRegister ? (
        <form className="admin-inline-form supplier-shipment-form" onSubmit={onRegister}>
          <CarrierSelect carriers={carriers} />
          <label>
            송장번호
            <input maxLength={100} name="trackingNumber" required />
          </label>
          {!hasShipmentHistory ? (
            <label className="checkbox-row wide">
              <input
                checked={splitShipment}
                onChange={(event) => setSplitShipment(event.target.checked)}
                type="checkbox"
              />
              분할 출고
            </label>
          ) : null}
          {explicitAllocation ? (
            <div className="supplier-shipment-allocations wide">
              <strong>{hasShipmentHistory ? "추가 송장 수량" : "이번 송장 수량"}</strong>
              {remainingItems.map((item) => (
                <label key={item.orderItemId}>
                  {item.productName || "상품"} / {item.optionName || "기본"} (남음 {item.remainingQuantity}개)
                  <input
                    max={item.remainingQuantity}
                    min="0"
                    name={`allocation:${item.orderItemId}`}
                    step="1"
                    type="number"
                  />
                </label>
              ))}
            </div>
          ) : (
            <span className="field-help wide">첫 송장은 남은 전체 수량을 자동 할당합니다.</span>
          )}
          <button className="button primary" disabled={busy || carriers.length === 0} type="submit">
            {hasShipmentHistory ? "추가 송장 등록" : "송장 등록"}
          </button>
        </form>
      ) : (
        <div className="notice">
          <strong>송장을 추가할 수 없습니다</strong>
          <span>{shipmentState.nextAction === "CONTACT_COREABLE" ? "Coreable에 문의해 주세요." : "현재 처리 권한이나 남은 수량을 확인해 주세요."}</span>
        </div>
      )}
    </section>
  );
}

function CarrierSelect({ carriers, defaultValue = "" }: { carriers: SupplierCarrier[]; defaultValue?: string }) {
  return (
    <label>
      택배사
      <select defaultValue={defaultValue} name="carrierCode" required>
        <option disabled value="">택배사를 선택하세요</option>
        {carriers.map((carrier) => (
          <option key={carrier.carrierCode} value={carrier.carrierCode}>{carrier.carrierName}</option>
        ))}
      </select>
    </label>
  );
}

function shipmentAllocations(data: FormData): ShipmentAllocation[] {
  return Array.from(data.entries()).flatMap(([name, value]) => {
    if (!name.startsWith("allocation:") || typeof value !== "string") return [];
    const quantity = Number(value);
    return Number.isInteger(quantity) && quantity > 0
      ? [{ orderItemId: name.slice("allocation:".length), quantity }]
      : [];
  });
}

function allocationText(shipment: SupplierShipment, order: SupplierOrderDetail) {
  if (shipment.status === "VOIDED") return "무효 처리되어 현재 할당에 포함되지 않습니다.";
  if (shipment.allocations.length === 0) return "할당 정보가 없습니다.";
  return shipment.allocations.map((allocation) => {
    const item = order.items.find((candidate) => candidate.orderItemId === allocation.orderItemId);
    return `${item?.productName || "상품"} ${allocation.quantity}개`;
  }).join(" · ");
}

function shipmentStatusLabel(status: string) {
  if (status === "VOIDED") return "무효 처리";
  return supplierOrderStatusView(status).label;
}

function field(data: FormData, name: string) {
  const value = data.get(name);
  return typeof value === "string" ? value.trim() : "";
}

function shipmentMutationError(error: unknown) {
  if (error instanceof SupplierOrderApiError && error.status === 409) {
    return "다른 처리와 겹쳤거나 현재 상태에서 처리할 수 없습니다. 최신 상태를 확인해 주세요.";
  }
  if (error instanceof SupplierOrderApiError && error.status === 403) {
    return "현재 공급처 권한으로 처리할 수 없습니다.";
  }
  if (error instanceof SupplierOrderApiError && error.status === 404) {
    return "출고 요청이 인계되었거나 더 이상 처리할 수 없습니다.";
  }
  return "잠시 뒤 같은 내용으로 다시 시도해 주세요.";
}

function shortageMutationError(error: unknown) {
  if (error instanceof SupplierOrderApiError && error.status === 409) {
    return "다른 처리와 겹쳤습니다. 품절 보고 내역에서 최신 상태를 확인해 주세요.";
  }
  if (error instanceof SupplierOrderApiError && error.status === 403) {
    return "현재 계약 또는 포털 권한으로 보고할 수 없습니다.";
  }
  if (error instanceof SupplierOrderApiError && error.status === 404) {
    return "출고 요청이 인계되었거나 더 이상 보고할 수 없습니다.";
  }
  if (error instanceof SupplierOrderApiError && error.status < 500) {
    return "품절 보고 사유와 최신 주문 상태를 확인해 주세요.";
  }
  return "처리 결과를 확인하지 못했습니다. 같은 사유로 다시 시도하거나 품절 보고 내역을 확인해 주세요.";
}

function dateTime(value: string | null) {
  return value ? new Date(value).toLocaleString("ko-KR") : "-";
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
