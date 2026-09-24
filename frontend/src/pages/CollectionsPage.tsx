import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { collectionApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import type { CollectionSummary } from '../api/types'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'

/** 고객 기획전 목록 — 진행 중(PUBLISHED + 기간 내 + 상품 1개 이상)만 노출, displayOrder 순. */
export default function CollectionsPage() {
  const [collections, setCollections] = useState<CollectionSummary[] | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    setLoading(true)
    setError(null)
    collectionApi
      .list()
      .then(setCollections)
      .catch((e) => setError(e instanceof ApiError ? e.message : '불러오기에 실패했습니다.'))
      .finally(() => setLoading(false))
  }, [reloadKey])

  return (
    <div>
      <h1 className="mb-6 text-xl font-bold">기획전</h1>

      {loading && (
        <div className="grid gap-4 sm:grid-cols-2">
          {Array.from({ length: 4 }, (_, i) => (
            <Skeleton key={i} className="aspect-[16/7] w-full rounded-lg" />
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

      {collections &&
        !loading &&
        !error &&
        (collections.length === 0 ? (
          <p className="py-20 text-center text-sm text-muted-foreground">진행 중인 기획전이 없습니다.</p>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2">
            {collections.map((c) => (
              <Link
                key={c.id}
                to={`/collections/${c.id}`}
                className="group relative flex aspect-[16/7] flex-col justify-end overflow-hidden rounded-lg border border-border bg-muted p-4 shadow-sm transition-shadow hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              >
                {c.bannerImageUrl && (
                  <img
                    src={c.bannerImageUrl}
                    alt=""
                    className="absolute inset-0 size-full object-cover transition-transform group-hover:scale-105"
                  />
                )}
                <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-black/10 to-transparent" />
                <div className="relative text-white">
                  <p className="text-lg font-bold">{c.title}</p>
                  {c.subtitle && <p className="mt-0.5 text-sm text-white/85">{c.subtitle}</p>}
                  <p className="mt-1 text-xs text-white/70">상품 {c.productCount}개</p>
                </div>
              </Link>
            ))}
          </div>
        ))}
    </div>
  )
}
