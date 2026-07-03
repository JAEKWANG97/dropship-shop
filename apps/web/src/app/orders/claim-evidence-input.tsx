"use client";

import { useEffect, useState } from "react";

type Preview = {
  name: string;
  url: string;
};

export function ClaimEvidenceInput() {
  const [previews, setPreviews] = useState<Preview[]>([]);

  useEffect(() => {
    return () => {
      previews.forEach((preview) => URL.revokeObjectURL(preview.url));
    };
  }, [previews]);

  return (
    <label>
      증빙 사진
      <input
        name="evidenceFiles"
        type="file"
        accept="image/jpeg,image/png,image/webp"
        multiple
        onChange={(event) => {
          previews.forEach((preview) => URL.revokeObjectURL(preview.url));
          const nextPreviews = Array.from(event.currentTarget.files ?? []).map((file) => ({
            name: file.name,
            url: URL.createObjectURL(file),
          }));
          setPreviews(nextPreviews);
        }}
      />
      <span className="form-help">상품 하자, 오배송, 상품 정보와 다름, 배송 문제는 사진 증빙이 필요합니다.</span>
      {previews.length > 0 ? (
        <div className="evidence-preview-grid">
          {previews.map((preview) => (
            <figure className="evidence-preview" key={preview.url}>
              <img alt={preview.name} src={preview.url} />
              <figcaption>{preview.name}</figcaption>
            </figure>
          ))}
        </div>
      ) : null}
    </label>
  );
}
