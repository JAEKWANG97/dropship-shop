import { SupplierProductForm } from "../product-form";

type PageProps = { params: Promise<{ productId: string }> };

export default async function SupplierProductDetailPage({ params }: PageProps) {
  const { productId } = await params;
  return <SupplierProductForm productId={productId} />;
}
