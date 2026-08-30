import { AdminProductReviewDetail } from "./review-detail";

type PageProps = { params: Promise<{ productId: string }> };

export default async function AdminProductReviewDetailPage({ params }: PageProps) {
  const { productId } = await params;
  return <AdminProductReviewDetail productId={productId} />;
}
