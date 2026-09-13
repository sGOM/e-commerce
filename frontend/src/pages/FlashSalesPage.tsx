import { useEffect, useState } from 'react'
import { flashSaleApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import FlashSaleCard from '../components/FlashSaleCard'
import type { FlashSale } from '../api/types'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'

/** 고객 타임딜 목록 — 진행 중(ONGOING)만, 마감임박순(백엔드 정렬 그대로 노출). */
export default function FlashSalesPage() {
  const [flashSales, setFlashSales] = useState<FlashSale[] | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    setLoading(true)
    setError(null)
    flashSaleApi
      .list()
      .then(setFlashSales)
      .catch((e) => setError(e instanceof ApiError ? e.message : '불러오기에 실패했습니다.'))
      .finally(() => setLoading(false))
  }, [reloadKey])

  return (
    <div>
      <h1 className="mb-1 text-xl font-bold">⏰ 타임딜</h1>
      <p className="mb-6 text-sm text-muted-foreground">
        한정 시간·한정 수량 특가입니다. 소진되면 조기 종료될 수 있어요.
      </p>

      {loading && (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
          {Array.from({ length: 8 }, (_, i) => (
            <Skeleton key={i} className="aspect-[3/4] w-full rounded-lg" />
          ))}
        </div>
      )}

      {error && !loading && (
        <div className="mx-auto max-w-sm rounded-lg border border-border bg-card p-8 text-center shadow-sm">
          <p className="text-2xl">⚠️</p>
          <p className="mt-2 text-sm text-muted-foreground">{error}</p>
          <Button variant="outline" className="mt-4" onClick={() => setReloadKey((k) => k + 1)}>
            다시 시도
          </Button>
        </div>
      )}

      {flashSales && !loading && !error && (
        flashSales.length === 0 ? (
          <p className="py-20 text-center text-sm text-muted-foreground">
            진행 중인 타임딜이 없습니다.
          </p>
        ) : (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            {flashSales.map((fs) => (
              <FlashSaleCard key={fs.id} flashSale={fs} />
            ))}
          </div>
        )
      )}
    </div>
  )
}
