type ProductImageProps = {
  src: string | null;
  alt: string;
  className?: string;
};

export function ProductImage({ src, alt, className }: ProductImageProps) {
  if (!src) {
    return <div className={`image-placeholder ${className ?? ""}`}>이미지 없음</div>;
  }

  // eslint-disable-next-line @next/next/no-img-element
  return <img alt={alt} className={className} src={src} />;
}
