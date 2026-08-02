"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { PRODUCT_CATEGORIES } from "@/lib/categories";

const CATEGORY_GROUPS = Object.entries(
  Object.groupBy(PRODUCT_CATEGORIES, (category) => category[0]),
);

export function CategoryMenu() {
  const [open, setOpen] = useState(false);
  const menuRef = useRef<HTMLDetailsElement>(null);

  useEffect(() => {
    if (!open) return;

    const closeOutside = (event: PointerEvent) => {
      if (!menuRef.current?.contains(event.target as Node)) setOpen(false);
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };

    document.addEventListener("pointerdown", closeOutside);
    document.addEventListener("keydown", closeOnEscape);
    return () => {
      document.removeEventListener("pointerdown", closeOutside);
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, [open]);

  return (
    <details
      className="header-category-menu"
      onToggle={(event) => setOpen(event.currentTarget.open)}
      open={open}
      ref={menuRef}
    >
      <summary aria-label="카테고리 메뉴" title="카테고리">
        <svg aria-hidden="true" viewBox="0 0 24 24">
          <path d="M4 7h16M4 12h16M4 17h16" />
        </svg>
        <span>카테고리</span>
      </summary>
      <div className="header-category-panel">
        <div className="header-category-panel-heading">
          <strong>전체 카테고리</strong>
          <Link href="/products" onClick={() => setOpen(false)}>전체 상품 보기</Link>
        </div>
        <div className="header-category-groups">
          {CATEGORY_GROUPS.map(([group, categories]) => (
            <details className="header-category-group" key={group} name="header-category-group">
              <summary>
                <span>{group}</span>
                <svg aria-hidden="true" viewBox="0 0 24 24">
                  <path d="m8 10 4 4 4-4" />
                </svg>
              </summary>
              <div>
                <Link
                  className="header-category-group-all"
                  href={`/products?group=${encodeURIComponent(group)}`}
                  onClick={() => setOpen(false)}
                >
                  {group} 전체 보기
                </Link>
                {categories?.map((category) => (
                  <Link
                    href={`/products?category=${category[2]}`}
                    key={category[2]}
                    onClick={() => setOpen(false)}
                  >
                    {category[3]}
                  </Link>
                ))}
              </div>
            </details>
          ))}
        </div>
      </div>
    </details>
  );
}
