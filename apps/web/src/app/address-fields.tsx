"use client";

import Script from "next/script";
import { useId, useRef, useState } from "react";

const POSTCODE_SCRIPT =
  "https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js";

type PostcodeData = {
  zonecode: string;
  roadAddress: string;
  jibunAddress: string;
  userSelectedType: "R" | "J";
};

declare global {
  interface Window {
    daum?: {
      Postcode: new (options: {
        oncomplete: (data: PostcodeData) => void;
        width: string;
        height: string;
      }) => { embed: (element: HTMLElement) => void };
    };
  }
}

type AddressFieldsProps = {
  postalCode?: string;
  address1?: string;
  address2?: string;
};

export function AddressFields({
  postalCode = "",
  address1 = "",
  address2 = "",
}: AddressFieldsProps) {
  const id = useId();
  const detailRef = useRef<HTMLInputElement>(null);
  const searchRef = useRef<HTMLDivElement>(null);
  const [postcodeReady, setPostcodeReady] = useState(false);
  const [scriptError, setScriptError] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [postalCodeValue, setPostalCodeValue] = useState(postalCode);
  const [addressValue, setAddressValue] = useState(address1);

  function searchAddress() {
    const Postcode = window.daum?.Postcode;
    if (!Postcode) {
      setScriptError(true);
      return;
    }
    setSearchOpen(true);
    requestAnimationFrame(() => {
      const container = searchRef.current;
      if (!container) return;
      container.replaceChildren();
      new Postcode({
        width: "100%",
        height: "100%",
        oncomplete(data) {
          setPostalCodeValue(data.zonecode);
          setAddressValue(
            data.userSelectedType === "R"
              ? data.roadAddress || data.jibunAddress
              : data.jibunAddress || data.roadAddress,
          );
          setSearchOpen(false);
          requestAnimationFrame(() => detailRef.current?.focus());
        },
      }).embed(container);
    });
  }

  return (
    <div className="address-fields">
      <Script
        src={POSTCODE_SCRIPT}
        strategy="afterInteractive"
        onReady={() => setPostcodeReady(true)}
        onError={() => setScriptError(true)}
      />
      <div className="address-search-row">
        <label htmlFor={`${id}-postal-code`}>
          우편번호
          <input
            id={`${id}-postal-code`}
            name="postalCode"
            required
            value={postalCodeValue}
            onChange={(event) => setPostalCodeValue(event.target.value)}
          />
        </label>
        <button className="button" type="button" onClick={searchAddress} disabled={!postcodeReady}>
          {postcodeReady ? "주소 검색" : "주소 검색 준비 중"}
        </button>
      </div>
      {searchOpen ? (
        <div className="address-search-panel">
          <div className="address-search-panel-header">
            <strong>주소 검색</strong>
            <button className="button secondary" type="button" onClick={() => setSearchOpen(false)}>
              닫기
            </button>
          </div>
          <div className="address-search-embed" ref={searchRef} />
        </div>
      ) : null}
      <label htmlFor={`${id}-address1`}>
        주소
        <input
          id={`${id}-address1`}
          name="address1"
          required
          value={addressValue}
          onChange={(event) => setAddressValue(event.target.value)}
        />
      </label>
      <label htmlFor={`${id}-address2`}>
        상세 주소
        <input
          id={`${id}-address2`}
          ref={detailRef}
          name="address2"
          defaultValue={address2}
        />
      </label>
      {scriptError ? (
        <p className="field-error" role="alert">
          주소 검색을 불러오지 못했습니다. 우편번호와 주소를 직접 입력해 주세요.
        </p>
      ) : null}
    </div>
  );
}
