"use client";

/* eslint-disable @next/next/no-img-element */

import { useState } from "react";

type ProductImageProps = {
  src: string | null;
  alt: string;
  className?: string;
};

export function ProductImage({ src, alt, className }: ProductImageProps) {
  const [failedSrc, setFailedSrc] = useState<string | null>(null);

  if (!src || failedSrc === src) {
    return <div className={`image-placeholder ${className ?? ""}`}>이미지 없음</div>;
  }

  return (
    <img
      alt={alt}
      className={className}
      onError={() => setFailedSrc(src)}
      ref={(image) => {
        if (image?.complete && image.naturalWidth === 0) setFailedSrc(src);
      }}
      src={src}
    />
  );
}
