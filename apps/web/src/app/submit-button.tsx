"use client";

import { useFormStatus } from "react-dom";
import type { ButtonHTMLAttributes, ReactNode } from "react";

type SubmitButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  children: ReactNode;
  pendingLabel?: string;
};

export function SubmitButton({
  children,
  disabled,
  pendingLabel = "처리 중...",
  type = "submit",
  ...props
}: SubmitButtonProps) {
  const { pending } = useFormStatus();
  const isDisabled = Boolean(disabled || pending);

  return (
    <button {...props} aria-disabled={isDisabled} disabled={isDisabled} type={type}>
      {pending ? pendingLabel : children}
    </button>
  );
}
