"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { ProductImage } from "@/app/products/product-image";
import { PRODUCT_CATEGORIES, categoryPath } from "@/lib/categories";
import {
  PRODUCT_IMAGE_ACCEPT,
  createSupplierProduct,
  deleteSupplierImage,
  deleteSupplierOption,
  deleteSupplierProduct,
  getSupplierProduct,
  inventoryActionError,
  isProductVersionError,
  orderSupplierImages,
  productActionError,
  replaceSupplierDetailBlocks,
  replaceSupplierNotice,
  saveSupplierOption,
  saveSupplierInventory,
  submitSupplierProduct,
  supplierDetailBlocksWithoutImage,
  supplierStatusView,
  updateSupplierProduct,
  uploadSupplierImage,
  validateProductImage,
  SupplierProductApiError,
  type SupplierProduct,
  type SupplierOptionInventory,
  type SupplierProductNotice,
} from "@/lib/supplier-products";

type EditableOption = {
  key: string;
  id: string | null;
  name: string;
  sourceOptionCode: string;
  sourceAdditionalPrice: number;
  deletable: boolean;
  inventoryVersion: number;
  supplierAvailability: "AVAILABLE" | "UNAVAILABLE";
  inventoryMode: "TRACKED" | "UNTRACKED";
  onHandQuantity: number | null;
  reservedQuantity: number;
  availableQuantity: number | null;
  inventoryDirty: boolean;
};

const EMPTY_NOTICE: SupplierProductNotice = {
  productInfoNotice: "",
  shippingInfo: "",
  asInfo: "",
  returnExchangeInfo: "",
  noticeRows: [{ label: "", value: "" }],
};

export function SupplierProductForm({ productId }: { productId?: string }) {
  const router = useRouter();
  const [product, setProduct] = useState<SupplierProduct | null>(null);
  const [name, setName] = useState("");
  const [summary, setSummary] = useState("");
  const [sourcePrice, setSourcePrice] = useState(0);
  const [minimumOrderQuantity, setMinimumOrderQuantity] = useState(1);
  const [orderQuantityStep, setOrderQuantityStep] = useState(1);
  const [categoryCode, setCategoryCode] = useState<string>(PRODUCT_CATEGORIES[0][2]);
  const [options, setOptions] = useState<EditableOption[]>([emptyOption("option-0")]);
  const [files, setFiles] = useState<File[]>([]);
  const [detailFiles, setDetailFiles] = useState<File[]>([]);
  const [detailHtml, setDetailHtml] = useState("");
  const [notice, setNotice] = useState<SupplierProductNotice>(EMPTY_NOTICE);
  const [loading, setLoading] = useState(Boolean(productId));
  const [saving, setSaving] = useState(false);
  const [progress, setProgress] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [needsRefresh, setNeedsRefresh] = useState(false);
  const inventoryCommands = useRef(new Map<string, { signature: string; key: string }>());

  useEffect(() => {
    if (!productId) return;
    let active = true;
    getSupplierProduct(productId)
      .then((value) => {
        if (!active) return;
        hydrate(value);
      })
      .catch(() => active && setMessage("상품 정보를 불러오지 못했습니다."))
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [productId]);

  const status = useMemo(() => product ? supplierStatusView(product) : null, [product]);
  const editable = !productId || status?.editable === true;
  const controls = supplierProductControlState(editable, saving);

  function hydrate(value: SupplierProduct) {
    setProduct(value);
    setName(value.name);
    setSummary(value.summary);
    setSourcePrice(value.sourcePrice);
    setMinimumOrderQuantity(value.minimumOrderQuantity);
    setOrderQuantityStep(value.orderQuantityStep);
    setCategoryCode(value.categoryCode || PRODUCT_CATEGORIES[0][2]);
    setOptions(value.options.length > 0
      ? value.options.map((option) => ({
        key: option.id,
        id: option.id,
        name: option.name,
        sourceOptionCode: option.sourceOptionCode,
        sourceAdditionalPrice: option.sourceAdditionalPrice,
        deletable: option.deletable,
        inventoryVersion: option.inventoryVersion,
        supplierAvailability: option.supplierAvailability,
        inventoryMode: option.inventoryMode,
        onHandQuantity: option.onHandQuantity,
        reservedQuantity: option.reservedQuantity,
        availableQuantity: option.availableQuantity,
        inventoryDirty: false,
      }))
      : [emptyOption("option-0")]);
    setDetailHtml(value.detailBlocks.filter((block) => block.type === "HTML").map((block) => block.htmlContent).join("\n"));
    setNotice(value.notice.noticeRows.length > 0 ? value.notice : { ...value.notice, noticeRows: [{ label: "", value: "" }] });
    setNeedsRefresh(false);
  }

  async function register(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!editable || saving) return;
    const fileError = [...files, ...detailFiles].map(validateProductImage).find(Boolean);
    if (fileError) {
      setMessage(fileError);
      return;
    }
    const invalidInventory = options.find(inventoryValidation);
    if (invalidInventory) {
      setMessage(inventoryValidation(invalidInventory));
      return;
    }
    const publicImageCount = product?.images.filter((image) => image.type !== "DETAIL").length ?? 0;
    if (publicImageCount + files.length > 11) {
      setMessage("대표 이미지 1개와 갤러리 이미지 10개까지만 등록할 수 있습니다.");
      return;
    }
    const detailImageCount = product?.images.filter((image) => image.type === "DETAIL").length ?? 0;
    if (detailImageCount + detailFiles.length > 50) {
      setMessage("상세 이미지는 50개까지만 등록할 수 있습니다.");
      return;
    }

    setSaving(true);
    setMessage(null);
    let currentId = product?.id || "";
    let version = product?.version ?? -1;
    let inventoryFailure = false;
    try {
      const input = { name: name.trim(), summary: summary.trim(), sourcePrice, minimumOrderQuantity, orderQuantityStep, categoryCode };
      setProgress(product ? "기본 정보를 저장하고 있습니다." : "등록 초안을 준비하고 있습니다.");
      const baseResult = product
        ? await updateSupplierProduct(product.id, input, version)
        : await createSupplierProduct(input);
      currentId = baseResult.productId || currentId;
      version = requiredVersion(baseResult.version);
      if (!currentId) throw new Error("Missing product id");

      let serverProduct = await getSupplierProduct(currentId);
      version = serverProduct.version;

      setProgress("이미지를 등록하고 있습니다.");
      let hasThumbnail = serverProduct.images.some((image) => image.type === "THUMBNAIL");
      for (const [index, file] of files.entries()) {
        const type = !hasThumbnail && index === 0 ? "THUMBNAIL" as const : "GALLERY" as const;
        const result = await uploadSupplierImage(currentId, file, type, name.trim(), version);
        version = requiredVersion(result.version);
        hasThumbnail ||= type === "THUMBNAIL";
      }
      if (files.length > 0) {
        serverProduct = await getSupplierProduct(currentId);
        const orderedImages = serverProduct.images
          .filter((image) => image.type !== "DETAIL")
          .sort((left, right) => {
            if (left.type === "THUMBNAIL") return -1;
            if (right.type === "THUMBNAIL") return 1;
            return left.sortOrder - right.sortOrder;
          });
        const result = await orderSupplierImages(currentId, orderedImages, serverProduct.version);
        version = requiredVersion(result.version);
      }
      for (const file of detailFiles) {
        const result = await uploadSupplierImage(currentId, file, "DETAIL", name.trim(), version);
        version = requiredVersion(result.version);
      }

      setProgress("옵션을 저장하고 있습니다.");
      const defaultOption = serverProduct.options[0];
      for (const [index, option] of options.entries()) {
        const wasNew = !option.id;
        const optionId = option.id || (index === 0 ? defaultOption?.id ?? null : null);
        const beforeOptionIds = new Set(serverProduct.options.map((candidate) => candidate.id));
        const result = await saveSupplierOption(currentId, optionId, {
          name: option.name.trim() || "기본",
          sourceOptionCode: option.sourceOptionCode.trim(),
          sourceAdditionalPrice: option.sourceAdditionalPrice,
          sortOrder: index,
        }, version);
        version = requiredVersion(result.version);
        const savedOption = optionId
          ? result.options.find((candidate) => candidate.id === optionId)
          : result.options.find((candidate) => !beforeOptionIds.has(candidate.id));
        if (!savedOption) throw new Error("Missing saved option");
        serverProduct = result;
        setOptions((current) => current.map((candidate, optionIndex) => optionIndex === index
          ? { ...candidate, id: savedOption.id, inventoryVersion: wasNew ? savedOption.inventoryVersion : candidate.inventoryVersion }
          : candidate));

        if (wasNew || option.inventoryDirty) {
          setProgress(`'${option.name.trim() || "기본"}' 재고를 저장하고 있습니다.`);
          inventoryFailure = true;
          const expectedInventoryVersion = wasNew ? savedOption.inventoryVersion : option.inventoryVersion;
          const inventory = await saveSupplierInventory(currentId, savedOption.id, {
            inventoryVersion: expectedInventoryVersion,
            supplierAvailability: option.supplierAvailability,
            inventoryMode: option.inventoryMode,
            onHandQuantity: option.inventoryMode === "TRACKED" ? option.onHandQuantity : null,
          }, inventoryCommandKey(option, currentId, savedOption.id, expectedInventoryVersion));
          inventoryFailure = false;
          applyInventory(savedOption.id, inventory);
          inventoryCommands.current.delete(option.key);
        }
      }

      setProgress("상세 설명을 저장하고 있습니다.");
      serverProduct = await getSupplierProduct(currentId);
      const existingImageBlocks = serverProduct.detailBlocks.filter((block) => block.type === "IMAGE");
      const referencedImageIds = new Set(existingImageBlocks.map((block) => block.productImageId));
      const detailBlocks = [
        ...(detailHtml.trim() ? [{ type: "HTML" as const, htmlContent: detailHtml.trim(), sortOrder: 0 }] : []),
        ...existingImageBlocks.map((block, index) => ({
          type: "IMAGE" as const,
          productImageId: block.productImageId,
          sortOrder: index + 1,
          altText: block.altText || undefined,
        })),
        ...serverProduct.images
          .filter((image) => image.type === "DETAIL" && !referencedImageIds.has(image.id))
          .map((image, index) => ({
            type: "IMAGE" as const,
            productImageId: image.id,
            sortOrder: existingImageBlocks.length + index + 1,
            altText: image.altText || undefined,
          })),
      ];
      const detailResult = await replaceSupplierDetailBlocks(currentId, detailBlocks, serverProduct.version);
      version = requiredVersion(detailResult.version);

      setProgress("상품 고시를 저장하고 있습니다.");
      const noticeResult = await replaceSupplierNotice(currentId, {
        ...notice,
        productInfoNotice: notice.productInfoNotice.trim(),
        shippingInfo: notice.shippingInfo.trim(),
        asInfo: notice.asInfo.trim(),
        returnExchangeInfo: notice.returnExchangeInfo.trim(),
        noticeRows: notice.noticeRows
          .map((row) => ({ label: row.label.trim(), value: row.value.trim() }))
          .filter((row) => row.label && row.value),
      }, version);
      version = requiredVersion(noticeResult.version);

      setProgress("상품을 저장하고 분류하고 있습니다.");
      await submitSupplierProduct(currentId, version);
      const completed = await getSupplierProduct(currentId);
      hydrate(completed);
      setFiles([]);
      setDetailFiles([]);
      setMessage("상품 등록이 완료되었습니다.");
      setProgress("");
      if (!productId) router.replace(`/supplier/products/${encodeURIComponent(currentId)}`);
      router.refresh();
    } catch (error) {
      if (inventoryFailure) {
        showInventoryError(error, version);
        setFiles((current) => selectedFilesAfterInventoryError(current, Boolean(productId)));
        setDetailFiles((current) => selectedFilesAfterInventoryError(current, Boolean(productId)));
      } else showActionError(error);
      setProgress(currentId ? "저장된 초안에서 다시 이어갈 수 있습니다." : "");
      if (currentId && !productId) router.replace(`/supplier/products/${encodeURIComponent(currentId)}`);
    } finally {
      setSaving(false);
    }
  }

  async function deleteDraft() {
    if (!product?.deletable || !editable || saving || !window.confirm("이 초안을 삭제할까요? 등록한 이미지도 함께 정리됩니다.")) return;
    setSaving(true);
    setMessage(null);
    try {
      await deleteSupplierProduct(product.id, product.version);
      router.push("/supplier/products");
      router.refresh();
    } catch (error) {
      showActionError(error);
    } finally {
      setSaving(false);
    }
  }

  async function removeOption(index: number) {
    const option = options[index];
    if (!option || !editable || saving || options.length <= 1) return;
    if (!option.id) {
      setOptions((current) => current.filter((_, optionIndex) => optionIndex !== index));
      return;
    }
    if (!product || !option.deletable || !window.confirm(`옵션 '${option.name}'을 삭제할까요?`)) return;
    setSaving(true);
    setMessage(null);
    try {
      await deleteSupplierOption(product.id, option.id, product.version);
      await reloadProduct("옵션을 삭제했습니다.");
    } catch (error) {
      showActionError(error);
    } finally {
      setSaving(false);
    }
  }

  async function saveInventoryOnly(index: number) {
    const option = options[index];
    if (!product || !option?.id || saving) return;
    const validationMessage = inventoryValidation(option);
    if (validationMessage) {
      setMessage(validationMessage);
      return;
    }
    setSaving(true);
    setMessage(null);
    setProgress(`'${option.name}' 재고를 저장하고 있습니다.`);
    try {
      const inventory = await saveSupplierInventory(product.id, option.id, {
        inventoryVersion: option.inventoryVersion,
        supplierAvailability: option.supplierAvailability,
        inventoryMode: option.inventoryMode,
        onHandQuantity: option.inventoryMode === "TRACKED" ? option.onHandQuantity : null,
      }, inventoryCommandKey(option, product.id, option.id));
      applyInventory(option.id, inventory);
      inventoryCommands.current.delete(option.key);
      setMessage("재고를 저장했습니다.");
      setProgress("");
    } catch (error) {
      const canonicalConflict = error instanceof SupplierProductApiError && Boolean(error.currentInventory);
      showInventoryError(error);
      setProgress(canonicalConflict
        ? "최신 재고를 확인하고 필요한 값을 다시 입력해 주세요."
        : "저장 결과가 불확실하면 같은 내용으로 다시 시도해 주세요.");
    } finally {
      setSaving(false);
    }
  }

  async function removeImage(imageId: string) {
    const image = product?.images.find((candidate) => candidate.id === imageId);
    if (!product || !image?.deletable || !editable || saving
      || !window.confirm(`${image.type === "DETAIL" ? "상세" : "상품"} 이미지를 삭제할까요?`)) return;
    setSaving(true);
    setMessage(null);
    try {
      await deleteSupplierImage(product.id, image.id, product.version);
      await reloadProduct("이미지를 삭제했습니다.");
    } catch (error) {
      showActionError(error);
    } finally {
      setSaving(false);
    }
  }

  async function unlinkDetailImage(imageId: string) {
    if (!product || !editable || saving) return;
    const remainingBlocks = supplierDetailBlocksWithoutImage(product.detailBlocks, imageId);
    if (remainingBlocks.length === product.detailBlocks.length
      || !window.confirm("이 이미지를 상세 설명에서 제외할까요? 이미지는 삭제되지 않습니다.")) return;
    setSaving(true);
    setMessage(null);
    try {
      await replaceSupplierDetailBlocks(product.id, remainingBlocks, product.version);
      await reloadProduct("상세 설명에서 이미지를 제외했습니다. 이제 이미지 파일을 삭제할 수 있습니다.");
    } catch (error) {
      showActionError(error);
    } finally {
      setSaving(false);
    }
  }

  async function chooseThumbnail(imageId: string) {
    if (!product || !editable || saving) return;
    const presentationImages = product.images.filter((image) => image.type !== "DETAIL");
    if (!presentationImages.some((image) => image.id === imageId)) return;
    setSaving(true);
    setMessage(null);
    try {
      const orderedImages = [
        ...presentationImages.filter((image) => image.id === imageId),
        ...presentationImages.filter((image) => image.id !== imageId),
      ].map((image, index) => ({
        ...image,
        type: index === 0 ? "THUMBNAIL" as const : "GALLERY" as const,
        sortOrder: index,
      }));
      await orderSupplierImages(product.id, orderedImages, product.version);
      await reloadProduct("대표 이미지를 변경했습니다. 이전 대표 이미지는 이제 삭제할 수 있습니다.");
    } catch (error) {
      showActionError(error);
    } finally {
      setSaving(false);
    }
  }

  async function reloadProduct(successMessage = "최신 상품 내용을 다시 불러왔습니다.") {
    if (!product) return;
    const latest = await getSupplierProduct(product.id);
    hydrate(latest);
    setNeedsRefresh(false);
    setMessage(successMessage);
  }

  function showActionError(error: unknown) {
    setMessage(productActionError(error));
    setNeedsRefresh(isProductVersionError(error));
  }

  function showInventoryError(error: unknown, acceptedProductVersion?: number) {
    if (error instanceof SupplierProductApiError && error.currentInventory) {
      applyInventory(error.currentInventory.optionId, error.currentInventory);
    }
    setProduct((current) => supplierProductAfterInventoryError(current, acceptedProductVersion));
    setMessage(inventoryActionError(error));
    setNeedsRefresh(false);
  }

  if (loading) return <div className="supplier-page"><div className="notice">상품 정보를 불러오는 중입니다.</div></div>;
  if (productId && !product) {
    return <div className="supplier-page"><div className="notice danger">{message ?? "상품을 찾을 수 없습니다."}</div></div>;
  }

  return (
    <div className="supplier-page">
      <div className="admin-heading">
        <div>
          <Link className="admin-text-link" href="/supplier/products">상품 목록</Link>
          <h1>{product ? product.name : "상품 등록"}</h1>
          <p>한 번의 상품 등록으로 초안, 이미지, 옵션, 상세, 고시 저장과 상품 분류를 순서대로 처리합니다.</p>
        </div>
        {status ? <span className={`admin-badge ${status.tone}`}>{status.label}</span> : null}
      </div>

      {status ? (
        <div className={`notice ${status.tone === "danger" ? "danger" : ""}`}>
          <strong>{status.reasonLabel ?? status.label}</strong>
          <span>{status.message ?? status.nextLabel}</span>
        </div>
      ) : null}
      {status?.editWarning ? <div className="notice warning" data-testid="supplier-edit-warning" role="note"><strong>수정 전 확인</strong><span>{status.editWarning}</span></div> : null}
      {message ? <div className="notice" role="status"><strong>알림</strong><span>{message}</span>{needsRefresh && product ? <button className="button" onClick={() => reloadProduct()} type="button">최신 내용 다시 불러오기</button> : null}</div> : null}

      <form aria-busy={saving} className="admin-form" onSubmit={register}>
        <fieldset disabled={controls.productDisabled}>
          <section className="admin-panel">
            <h2>기본 정보</h2>
            <div className="admin-form-grid">
              <label>상품명<input maxLength={200} onChange={(event) => setName(event.target.value)} required value={name} /></label>
              <label>공급가<input min={0} onChange={(event) => setSourcePrice(number(event.target.value, 0))} required type="number" value={sourcePrice} /><span className="field-help">고객 판매가는 Coreable 정책으로 계산됩니다.</span></label>
              <label className="wide">요약 설명<input maxLength={500} onChange={(event) => setSummary(event.target.value)} required value={summary} /></label>
              <label className="wide">카테고리<select onChange={(event) => setCategoryCode(event.target.value)} required value={categoryCode}>{PRODUCT_CATEGORIES.map((category) => <option key={category[2]} value={category[2]}>{categoryPath(category[2])}</option>)}</select></label>
              <label>최소 주문수량<input max={99} min={1} onChange={(event) => setMinimumOrderQuantity(number(event.target.value, 1))} required type="number" value={minimumOrderQuantity} /></label>
              <label>주문 증가단위<input max={99} min={1} onChange={(event) => setOrderQuantityStep(number(event.target.value, 1))} required type="number" value={orderQuantityStep} /></label>
            </div>
          </section>

          <section className="admin-panel">
            <div className="admin-panel-head"><h2>상품 이미지</h2><span>파일당 10MB 이하</span></div>
            {product?.images.length ? <p className="field-help">등록된 이미지 {product.images.length}개 · 새 파일은 기존 이미지 뒤에 추가됩니다.</p> : null}
            {product?.images.some((image) => image.type !== "DETAIL") ? (
              <div className="supplier-product-image-grid">
                {product.images.filter((image) => image.type !== "DETAIL").map((image) => (
                  <figure className="supplier-product-image-card" key={image.id}>
                    <ProductImage alt={image.altText || product.name} className="supplier-product-image" src={image.imageUrl} />
                    <figcaption><strong>{image.type === "THUMBNAIL" ? "대표 이미지" : "갤러리 이미지"}</strong><span>{image.altText || "대체 텍스트 없음"}</span></figcaption>
                    {editable && image.type === "GALLERY" ? <button className="button" disabled={saving} onClick={() => chooseThumbnail(image.id)} type="button">대표로 지정</button> : null}
                    {editable && image.deletable ? <button className="button" disabled={saving} onClick={() => removeImage(image.id)} type="button">이미지 삭제</button> : editable && image.type === "THUMBNAIL" && product.images.some((candidate) => candidate.type === "GALLERY") ? <span className="field-help">다른 이미지를 대표로 지정한 뒤 삭제할 수 있습니다.</span> : null}
                  </figure>
                ))}
              </div>
            ) : null}
            <label>대표·갤러리 이미지<input accept={PRODUCT_IMAGE_ACCEPT} multiple onChange={(event) => setFiles(Array.from(event.target.files ?? []))} type="file" /><span className="field-help">JPG, PNG, WEBP만 허용하며 첫 이미지는 대표 이미지가 됩니다. 파일 서명은 서버가 다시 검사합니다.</span></label>
          </section>
        </fieldset>

        <section className="admin-panel">
            <div className="admin-panel-head"><h2>옵션</h2><button className="button" disabled={controls.productDisabled} onClick={() => setOptions((current) => [...current, emptyOption(globalThis.crypto.randomUUID())])} type="button">옵션 추가</button></div>
            <p className="field-help">재고만 저장하면 상품 정보와 Coreable 검토 상태는 바뀌지 않습니다.</p>
            <div className="admin-form">
              {options.map((option, index) => (
                <div className="admin-form-grid admin-option-card" key={option.key}>
                  <label>옵션명<input disabled={controls.productDisabled} maxLength={200} onChange={(event) => updateOption(index, "name", event.target.value)} required value={option.name} /></label>
                  <label>공급처 옵션코드<input disabled={controls.productDisabled} maxLength={200} onChange={(event) => updateOption(index, "sourceOptionCode", event.target.value)} value={option.sourceOptionCode} /></label>
                  <label>추가 공급가<input disabled={controls.productDisabled} min={0} onChange={(event) => updateOption(index, "sourceAdditionalPrice", number(event.target.value, 0))} required type="number" value={option.sourceAdditionalPrice} /></label>
                  <label>재고 관리<select disabled={controls.inventoryDisabled} onChange={(event) => updateInventoryOption(index, "inventoryMode", event.target.value)} value={option.inventoryMode}>
                    <option value="TRACKED">수량 관리 (권장)</option>
                    <option value="UNTRACKED">재고 수량 관리 안 함</option>
                  </select><span className="field-help">{option.inventoryMode === "TRACKED" ? "주문서 생성 중 수량이 예약되어 판매 가능 수량이 줄어듭니다." : "수량 대신 주문 받기·중지로 신규 주문을 관리합니다."}</span></label>
                  {option.inventoryMode === "TRACKED" ? <label>현재 재고 수량<input disabled={controls.inventoryDisabled} min={0} onChange={(event) => updateInventoryOption(index, "onHandQuantity", number(event.target.value, 0))} required step={1} type="number" value={option.onHandQuantity ?? 0} /><span className="field-help">예약 {option.reservedQuantity.toLocaleString("ko-KR")}개 · 판매 가능 {(option.availableQuantity ?? 0).toLocaleString("ko-KR")}개</span></label> : null}
                  <label>신규 주문<select disabled={controls.inventoryDisabled} onChange={(event) => updateInventoryOption(index, "supplierAvailability", event.target.value)} value={option.supplierAvailability}>
                    <option value="AVAILABLE">주문 받기</option>
                    <option value="UNAVAILABLE">주문 중지</option>
                  </select>{option.supplierAvailability === "UNAVAILABLE" ? <span className="field-help">이미 만들어진 미입금 주문은 입금 시점 상태에 따라 환불될 수 있습니다.</span> : null}</label>
                  {product && option.id ? <button className="button" disabled={controls.inventoryDisabled || !option.inventoryDirty} onClick={() => saveInventoryOnly(index)} type="button">재고만 저장</button> : null}
                  {editable && (option.id ? option.deletable : options.length > 1) ? <button className="button" disabled={saving} onClick={() => removeOption(index)} type="button">옵션 삭제</button> : null}
                </div>
              ))}
            </div>
        </section>

        <fieldset disabled={controls.productDisabled}>
          <section className="admin-panel">
            <h2>상세 설명</h2>
            <label>HTML 상세<textarea maxLength={20000} onChange={(event) => setDetailHtml(event.target.value)} rows={10} value={detailHtml} /><span className="field-help">입력 내용은 여기서 HTML로 실행하지 않습니다. 저장 시 서버 허용 목록으로 정제됩니다.</span></label>
            <label>상세 이미지<input accept={PRODUCT_IMAGE_ACCEPT} multiple onChange={(event) => setDetailFiles(Array.from(event.target.files ?? []))} type="file" /><span className="field-help">DETAIL 이미지로 먼저 등록한 뒤 서버가 발급한 같은 상품의 이미지 ID만 상세 블록에 연결합니다.</span></label>
            {product?.images.some((image) => image.type === "DETAIL") ? (
              <div className="supplier-product-image-grid">
                {product.images.filter((image) => image.type === "DETAIL").map((image) => (
                  <figure className="supplier-product-image-card" key={image.id}>
                    <ProductImage alt={image.altText || "상세 이미지"} className="supplier-product-image" src={image.imageUrl} />
                    <figcaption><strong>상세 이미지</strong><span>{image.deletable ? "상세에서 제외됨 · 삭제 가능" : "상세 블록에서 사용 중"}</span></figcaption>
                    {editable && product.detailBlocks.some((block) => block.type === "IMAGE" && block.productImageId === image.id)
                      ? <button className="button" disabled={saving} onClick={() => unlinkDetailImage(image.id)} type="button">상세에서 제외</button>
                      : null}
                    {editable && image.deletable ? <button className="button" disabled={saving} onClick={() => removeImage(image.id)} type="button">이미지 삭제</button> : null}
                  </figure>
                ))}
              </div>
            ) : null}
          </section>

          <section className="admin-panel">
            <h2>상품 고시·운영 안내</h2>
            <div className="admin-form-grid">
              <label className="wide">상품정보 고시<textarea onChange={(event) => setNoticeField("productInfoNotice", event.target.value)} required rows={3} value={notice.productInfoNotice} /></label>
              <label className="wide">배송 안내<textarea onChange={(event) => setNoticeField("shippingInfo", event.target.value)} required rows={3} value={notice.shippingInfo} /></label>
              <label className="wide">A/S 안내<textarea onChange={(event) => setNoticeField("asInfo", event.target.value)} required rows={3} value={notice.asInfo} /></label>
              <label className="wide">반품·교환 안내<textarea onChange={(event) => setNoticeField("returnExchangeInfo", event.target.value)} required rows={3} value={notice.returnExchangeInfo} /></label>
            </div>
            {notice.noticeRows.map((row, index) => (
              <div className="admin-form-grid" key={`notice-${index}`}>
                <label>고시 항목<input maxLength={500} onChange={(event) => updateNoticeRow(index, "label", event.target.value)} value={row.label} /></label>
                <label>고시 값<input onChange={(event) => updateNoticeRow(index, "value", event.target.value)} value={row.value} /></label>
              </div>
            ))}
            <button className="button" onClick={() => setNotice((current) => ({ ...current, noticeRows: [...current.noticeRows, { label: "", value: "" }] }))} type="button">고시 항목 추가</button>
          </section>
        </fieldset>

        {editable ? <button className="button primary" disabled={saving} type="submit">{saving ? "상품 등록 중..." : "상품 등록"}</button> : null}
        {editable && product?.deletable ? <button className="button" disabled={saving} onClick={deleteDraft} type="button">초안 삭제</button> : null}
        {progress ? <p aria-live="polite" className="field-help">{progress}</p> : null}
      </form>
    </div>
  );

  function updateOption(index: number, field: "name" | "sourceOptionCode" | "sourceAdditionalPrice", value: string | number) {
    setOptions((current) => current.map((option, optionIndex) => optionIndex === index ? { ...option, [field]: value } : option));
  }

  function updateInventoryOption(index: number, field: "inventoryMode" | "supplierAvailability" | "onHandQuantity", value: string | number) {
    setOptions((current) => current.map((option, optionIndex) => {
      if (optionIndex !== index) return option;
      if (field === "inventoryMode") {
        const inventoryMode = value === "UNTRACKED" ? "UNTRACKED" : "TRACKED";
        return {
          ...option,
          inventoryMode,
          onHandQuantity: inventoryMode === "TRACKED" ? option.onHandQuantity ?? option.reservedQuantity : null,
          availableQuantity: inventoryMode === "TRACKED"
            ? Math.max(0, (option.onHandQuantity ?? option.reservedQuantity) - option.reservedQuantity)
            : null,
          inventoryDirty: true,
        };
      }
      if (field === "supplierAvailability") {
        return { ...option, supplierAvailability: value === "AVAILABLE" ? "AVAILABLE" : "UNAVAILABLE", inventoryDirty: true };
      }
      const onHandQuantity = typeof value === "number" ? value : 0;
      return {
        ...option,
        onHandQuantity,
        availableQuantity: Math.max(0, onHandQuantity - option.reservedQuantity),
        inventoryDirty: true,
      };
    }));
  }

  function applyInventory(optionId: string, inventory: SupplierOptionInventory) {
    setOptions((current) => current.map((option) => option.id === optionId ? {
      ...option,
      inventoryVersion: inventory.inventoryVersion,
      supplierAvailability: inventory.supplierAvailability,
      inventoryMode: inventory.inventoryMode,
      onHandQuantity: inventory.onHandQuantity,
      reservedQuantity: inventory.reservedQuantity,
      availableQuantity: inventory.availableQuantity,
      inventoryDirty: false,
    } : option));
  }

  function inventoryCommandKey(
    option: EditableOption,
    currentProductId: string,
    currentOptionId: string,
    expectedInventoryVersion = option.inventoryVersion,
  ) {
    const signature = JSON.stringify([
      currentProductId,
      currentOptionId,
      expectedInventoryVersion,
      option.supplierAvailability,
      option.inventoryMode,
      option.inventoryMode === "TRACKED" ? option.onHandQuantity : null,
    ]);
    const existing = inventoryCommands.current.get(option.key);
    if (existing?.signature === signature) return existing.key;
    const command = { signature, key: globalThis.crypto.randomUUID() };
    inventoryCommands.current.set(option.key, command);
    return command.key;
  }

  function setNoticeField(field: keyof Omit<SupplierProductNotice, "noticeRows">, value: string) {
    setNotice((current) => ({ ...current, [field]: value }));
  }

  function updateNoticeRow(index: number, field: "label" | "value", value: string) {
    setNotice((current) => ({
      ...current,
      noticeRows: current.noticeRows.map((row, rowIndex) => rowIndex === index ? { ...row, [field]: value } : row),
    }));
  }
}

function emptyOption(key: string): EditableOption {
  return {
    key,
    id: null,
    name: "기본",
    sourceOptionCode: "",
    sourceAdditionalPrice: 0,
    deletable: false,
    inventoryVersion: 0,
    supplierAvailability: "AVAILABLE",
    inventoryMode: "TRACKED",
    onHandQuantity: 0,
    reservedQuantity: 0,
    availableQuantity: 0,
    inventoryDirty: true,
  };
}

function inventoryValidation(option: EditableOption) {
  if (option.inventoryMode !== "TRACKED") return "";
  if (!Number.isInteger(option.onHandQuantity) || (option.onHandQuantity ?? -1) < 0) return "재고 수량은 0 이상의 정수로 입력해 주세요.";
  if ((option.onHandQuantity ?? 0) < option.reservedQuantity) return `예약된 ${option.reservedQuantity}개보다 재고를 낮출 수 없습니다.`;
  return "";
}

function number(value: string, fallback: number) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function requiredVersion(value: number) {
  if (value < 0) throw new Error("Missing version");
  return value;
}

export function supplierProductControlState(productEditable: boolean, saving: boolean) {
  return {
    productDisabled: !productEditable || saving,
    inventoryDisabled: saving,
  };
}

export function supplierProductAfterInventoryError(
  product: SupplierProduct | null,
  acceptedProductVersion?: number,
) {
  return product && acceptedProductVersion !== undefined
    ? { ...product, version: acceptedProductVersion }
    : product;
}

export function selectedFilesAfterInventoryError<T>(selectedFiles: T[], existingProduct: boolean) {
  return existingProduct ? [] : selectedFiles;
}
