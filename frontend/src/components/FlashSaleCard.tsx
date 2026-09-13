import { Link } from 'react-router-dom'
import { formatKRW } from '../api/client'
import { useCountdown } from '../hooks/useCountdown'
import type { FlashSale } from '../api/types'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'

const URGENT_MS = 10 * 60 * 1000 // 10분 이내는 경고색 강조(기획 §6)

interface Props {
  flashSale: FlashSale
  className?: string
}

/** 타임딜 카드(홈 캐러셀·타임딜 페이지 공용). 카운트다운·진행률·정가 취소선+특가를 표시한다. */
export default function FlashSaleCard({ flashSale, className }: Props) {
  const { productId, productName, optionName, storeName, originalPrice, salePrice } = flashSale
  const { label, ended, remainingMs } = useCountdown(flashSale.endAt)
  const urgent = remainingMs > 0 && remainingMs <= URGENT_MS
  const discountRate = Math.round((1 - salePrice / originalPrice) * 100)
  const soldRate = Math.min(
    100,
    Math.round((flashSale.soldQuantity / Math.max(1, flashSale.limitQuantity)) * 100),
  )

  return (
    <Link
      to={`/products/${productId}`}
      className={cn(
        'group relative flex flex-col rounded-lg border border-border bg-card p-3 shadow-sm transition-shadow hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2',
        className,
      )}
    >
      <div className="relative mb-3 flex aspect-square items-center justify-center rounded-md bg-muted text-4xl">
        🛍️
        {discountRate > 0 && (
          <Badge variant="destructive" className="absolute left-2 top-2">
            {discountRate}%
          </Badge>
        )}
      </div>

      <p className="truncate text-xs text-muted-foreground">{storeName}</p>
      <p className="mt-0.5 line-clamp-2 text-sm font-medium">
        {productName}
        <span className="text-muted-foreground"> · {optionName}</span>
      </p>

      <div className="mt-1 flex items-baseline gap-1.5">
        <span className="text-base font-bold text-primary">{formatKRW(salePrice)}</span>
        <span className="text-xs text-muted-foreground line-through">
          {formatKRW(originalPrice)}
        </span>
      </div>

      {/* 진행률(소진율) */}
      <div className="mt-2">
        <div
          role="progressbar"
          aria-label="타임딜 소진율"
          aria-valuenow={soldRate}
          aria-valuemin={0}
          aria-valuemax={100}
          className="h-1.5 w-full overflow-hidden rounded-full bg-muted"
        >
          <div
            className="h-full rounded-full bg-primary transition-all"
            style={{ width: `${soldRate}%` }}
          />
        </div>
        <p className="mt-1 text-[11px] text-muted-foreground">
          {flashSale.remainingQuantity > 0
            ? `${flashSale.remainingQuantity}개 남음`
            : '한도 소진'}
        </p>
      </div>

      {/* 카운트다운 */}
      <p
        role="timer"
        aria-live="off"
        className={cn(
          'mt-2 text-xs font-semibold tabular-nums',
          ended ? 'text-muted-foreground' : urgent ? 'text-destructive' : 'text-foreground',
        )}
      >
        {ended ? '타임딜 종료' : `남은 시간 ${label}`}
      </p>
    </Link>
  )
}
