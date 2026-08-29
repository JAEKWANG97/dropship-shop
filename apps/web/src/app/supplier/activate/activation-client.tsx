"use client";

import { useCallback, useEffect, useRef, useState } from "react";

type ActivationState = "checking" | "ready" | "invalid" | "transient";

export function SupplierActivationClient() {
  const tokenRef = useRef("");
  const callbackErrorRef = useRef("");
  const [state, setState] = useState<ActivationState>("checking");
  const [retryAuthorization, setRetryAuthorization] = useState(false);

  const exchange = useCallback(async (token: string) => {
    try {
      const response = await fetch("/api/supplier-invites/session", {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ token }),
      });
      if (!response.ok) {
        setState(response.status >= 500 ? "transient" : "invalid");
        return;
      }
      setState("ready");
    } catch {
      setState("transient");
    }
  }, []);

  useEffect(() => {
    const queryError = new URLSearchParams(window.location.search).get("error") ?? "";
    if (queryError) callbackErrorRef.current = queryError;
    const callbackError = queryError || callbackErrorRef.current;
    if (callbackError) {
      if (queryError) window.history.replaceState(null, "", window.location.pathname);
      const timer = window.setTimeout(() => {
        const temporary = callbackError === "OAUTH_TEMPORARY_FAILURE";
        setRetryAuthorization(temporary);
        setState(temporary ? "transient" : "invalid");
      }, 0);
      return () => window.clearTimeout(timer);
    }
    const token = fragmentToken(window.location.hash) || tokenRef.current;
    if (window.location.hash) {
      window.history.replaceState(null, "", `${window.location.pathname}${window.location.search}`);
    }
    tokenRef.current = token;
    const timer = window.setTimeout(() => {
      if (token) void exchange(token);
      else setState("invalid");
    }, 0);
    return () => window.clearTimeout(timer);
  }, [exchange]);

  const retry = useCallback(() => {
    setState("checking");
    if (tokenRef.current) {
      void exchange(tokenRef.current);
    }
  }, [exchange]);

  return (
    <section className="narrow-page supplier-activation-card" aria-live="polite">
      <p className="eyebrow">공급처 포털</p>
      {state === "checking" ? (
        <>
          <h1>초대 링크를 확인하고 있습니다</h1>
          <p>확인이 끝나면 카카오 연결 화면으로 이동합니다.</p>
          <button className="button kakao" disabled type="button">카카오 연결 준비 중...</button>
        </>
      ) : state === "ready" ? (
        <>
          <h1>초대 링크를 확인했습니다</h1>
          <p>카카오 계정을 연결하면 공급처 담당자 등록이 완료됩니다.</p>
          <a className="button kakao" href="/api/supplier/auth/kakao/authorize">카카오로 연결</a>
        </>
      ) : state === "transient" ? (
        <>
          <h1>지금은 연결할 수 없습니다</h1>
          <p>잠시 후 다시 시도해 주세요.</p>
          {retryAuthorization ? (
            <a className="button kakao" href="/api/supplier/auth/kakao/authorize">
              카카오 연결 다시 시도
            </a>
          ) : (
            <button className="button kakao" onClick={retry} type="button">
              초대 링크 다시 확인
            </button>
          )}
        </>
      ) : (
        <>
          <h1>초대 링크를 다시 확인해 주세요</h1>
          <p>링크가 만료되었거나 이미 사용되었다면 Coreable에 새 초대를 요청해 주세요.</p>
        </>
      )}
    </section>
  );
}

function fragmentToken(hash: string) {
  const fragment = hash.startsWith("#") ? hash.slice(1) : hash;
  if (!fragment) return "";
  try {
    if (fragment.startsWith("token=")) return new URLSearchParams(fragment).get("token")?.trim() ?? "";
    return decodeURIComponent(fragment).trim();
  } catch {
    return "";
  }
}
