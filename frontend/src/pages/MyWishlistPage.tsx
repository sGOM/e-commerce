import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { wishlistApi } from '../api/endpoints'
import { ApiError, formatKRW } from '../api/client'
import { productStatusLabel } from '../labels'
import { useWishlist } from '../hooks/useWishlist'
import type { PageResponse, WishlistResponse } from '../api/types'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { cn } from '@/lib/utils'

const PAGE_SIZE = 20

/** 마이페이지 위시리스트(찜) 목록 — 가격 인하 항목을 강조 배지로 보여주고, 인하만 보기 필터를 지원한다. */
export default function MyWishlistPage() {
  const { toggle } = useWishlist()
  const [priceDropOnly, setPriceDropOnly] = useState(false)
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<WishlistResponse> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(() => {
    setLoading(true)
    wishlistApi
      .my({ priceDropOnly, page, size: PAGE_SIZE })
      .then(setData)
      .catch((e) => setError(e instanceof ApiError ? e.message : '불러오기에 실패했습니다.'))
      .finally(() => setLoading(false))
  }, [priceDropOnly, page])

  useEffect(() => {
    load()
  }, [load])

  const remove = async (item: WishlistResponse) => {
    try {
      await toggle(item.productId)
      toast.success('위시리스트에서 삭제했습니다.')
      setData((prev) =>
        prev ? { ...prev, content: prev.content.filter((w) => w.id !== item.id) } : prev,
      )
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : '삭제에 실패했습니다.')
    }
  }

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-xl font-bold">찜한 상품</h1>
        <label className="flex items-center gap-2 text-sm text-muted-foreground">
          <input
            type="checkbox"
            checked={priceDropOnly}
            onChange={(e) => {
              setPriceDropOnly(e.target.checked)
              setPage(0)
            }}
            className="size-4"
          />
          가격 인하만 보기
        </label>
      </div>

      {loading ? (
        <p className="py-20 text-center text-sm text-muted-foreground">불러오는 중…</p>
      ) : error ? (
        <p className="py-20 text-center text-sm text-destructive">{error}</p>
      ) : !data || data.content.length === 0 ? (
        <p className="py-20 text-center text-sm text-muted-foreground">
          {priceDropOnly ? '가격이 인하된 찜 상품이 없습니다.' : '찜한 상품이 없습니다.'}
        </p>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2">
          {data.content.map((w) => (
            <li key={w.id}>
              <Card className={cn('p-4', w.isPriceDropped && 'border-primary/40 bg-primary/5')}>
                <div className="flex items-start justify-between gap-2">
                  <Link to={`/products/${w.productId}`} className="min-w-0 flex-1 hover:underline">
                    <p className="truncate font-semibold">{w.productName}</p>
                  </Link>
                  {w.productStatus !== 'ON_SALE' && (
                    <Badge variant="secondary">{productStatusLabel[w.productStatus]}</Badge>
                  )}
                </div>

                <div className="mt-2 flex items-baseline gap-2">
                  <span className="text-lg font-bold text-primary">{formatKRW(w.currentPrice)}</span>
                  {w.isPriceDropped && (
                    <>
                      <span className="text-sm text-muted-foreground line-through">
                        {formatKRW(w.baselinePrice)}
                      </span>
                      <Badge variant="destructive">{w.priceDropRate}% 인하</Badge>
                    </>
                  )}
                </div>

                <div className="mt-3 flex items-center justify-between">
                  <p className="text-xs text-muted-foreground">
                    찜한 날짜 {new Date(w.createdAt).toLocaleDateString('ko-KR')}
                  </p>
                  <Button type="button" variant="outline" size="sm" onClick={() => remove(w)}>
                    찜 해제
                  </Button>
                </div>
              </Card>
            </li>
          ))}
        </ul>
      )}

      {data && data.totalPages > 1 && (
        <div className="mt-8 flex items-center justify-center gap-3">
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={page === 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            이전
          </Button>
          <span className="text-sm tabular-nums text-muted-foreground">
            {page + 1} / {data.totalPages}
          </span>
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={page >= data.totalPages - 1}
            onClick={() => setPage((p) => p + 1)}
          >
            다음
          </Button>
        </div>
      )}
    </div>
  )
}
