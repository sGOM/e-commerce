import { Link } from 'react-router-dom'
import { formatKRW } from '../api/client'
import { productStatusLabel } from '../labels'
import type { ProductStatus } from '../api/types'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { StarRatingDisplay } from './StarRating'

interface Props {
  id: number
  name: string
  basePrice: number
  status: ProductStatus
  storeName: string
  badge?: string // 예: "32개 판매"
  avgRating?: number
  reviewCount?: number
}

/** 상태 배지: 부록 A 색 매핑(품절=destructive, 준비중/숨김=secondary). */
function StatusBadge({ status }: { status: ProductStatus }) {
  if (status === 'ON_SALE') return null
  const variant = status === 'SOLD_OUT' ? 'destructive' : 'secondary'
  return (
    <Badge variant={variant} className="absolute right-2 top-2">
      {productStatusLabel[status]}
    </Badge>
  )
}

/** 상품 카드(목록·인기 공용). 카드 전체가 상세 링크. */
export default function ProductCard({
  id,
  name,
  basePrice,
  status,
  storeName,
  badge,
  avgRating,
  reviewCount,
}: Props) {
  return (
    <Link
      to={`/products/${id}`}
      className="group relative flex flex-col rounded-lg border border-border bg-card p-3 shadow-sm transition-shadow hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
    >
      <div className="relative mb-3 flex aspect-square items-center justify-center rounded-md bg-muted text-4xl">
        🛍️
        <StatusBadge status={status} />
        {badge && (
          <Badge variant="secondary" className="absolute left-2 top-2">
            {badge}
          </Badge>
        )}
      </div>
      <p className="truncate text-xs text-muted-foreground">{storeName}</p>
      <p className="mt-0.5 line-clamp-2 text-sm font-medium">{name}</p>
      <p className="mt-1 text-base font-bold text-primary">{formatKRW(basePrice)}</p>
      {reviewCount != null && reviewCount > 0 && (
        <StarRatingDisplay
          rating={avgRating ?? 0}
          reviewCount={reviewCount}
          className="mt-1"
        />
      )}
    </Link>
  )
}

/** 목록 로딩용 스켈레톤(카드와 동일한 형태). */
export function ProductCardSkeleton() {
  return (
    <div className="flex flex-col rounded-lg border border-border bg-card p-3 shadow-sm">
      <Skeleton className="mb-3 aspect-square w-full rounded-md" />
      <Skeleton className="h-3 w-1/2" />
      <Skeleton className="mt-1.5 h-4 w-4/5" />
      <Skeleton className="mt-2 h-5 w-1/3" />
    </div>
  )
}
