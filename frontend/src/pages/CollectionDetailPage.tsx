import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { collectionApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import ProductCard, { ProductCardSkeleton } from '../components/ProductCard'
import type { CollectionDetail } from '../api/types'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'

/** 고객 기획전 상세 — 배너 + 편성 상품 그리드(displayOrder 순, ProductCard 재사용). */
export default function CollectionDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [collection, setCollection] = useState<CollectionDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    if (!id) return
    setLoading(true)
    setError(null)
    collectionApi
      .detail(Number(id))
      .then(setCollection)
      .catch((e) => setError(e instanceof ApiError ? e.message : '불러오기에 실패했습니다.'))
      .finally(() => setLoading(false))
  }, [id, reloadKey])

  if (loading) {
    return (
      <div>
        <Skeleton className="mb-6 aspect-[16/6] w-full rounded-lg" />
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
          {Array.from({ length: 8 }, (_, i) => (
            <ProductCardSkeleton key={i} />
          ))}
        </div>
      </div>
    )
  }

  if (error || !collection) {
    return (
      <div className="mx-auto max-w-sm py-16 text-center">
        <p className="text-2xl">⚠️</p>
        <p className="mt-2 text-sm text-muted-foreground">
          {error ?? '기획전을 찾을 수 없습니다.'}
        </p>
        <div className="mt-4 flex justify-center gap-2">
          <Button variant="outline" onClick={() => setReloadKey((k) => k + 1)}>
            다시 시도
          </Button>
          <Button asChild>
            <Link to="/collections">기획전 목록으로</Link>
          </Button>
        </div>
      </div>
    )
  }

  const products = [...collection.products].sort((a, b) => a.displayOrder - b.displayOrder)

  return (
    <div>
      <div className="relative mb-6 flex aspect-[16/6] flex-col justify-end overflow-hidden rounded-lg border border-border bg-muted p-5 shadow-sm sm:p-8">
        {collection.bannerImageUrl && (
          <img
            src={collection.bannerImageUrl}
            alt=""
            className="absolute inset-0 size-full object-cover"
          />
        )}
        <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-black/10 to-transparent" />
        <div className="relative text-white">
          <h1 className="text-xl font-bold sm:text-2xl">{collection.title}</h1>
          {collection.subtitle && (
            <p className="mt-1 text-sm text-white/85 sm:text-base">{collection.subtitle}</p>
          )}
        </div>
      </div>

      {products.length === 0 ? (
        <p className="py-16 text-center text-sm text-muted-foreground">
          현재 노출 가능한 상품이 없습니다.
        </p>
      ) : (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
          {products.map(({ product }) => (
            <ProductCard
              key={product.id}
              id={product.id}
              name={product.name}
              basePrice={product.basePrice}
              status={product.status}
              storeName={product.storeName}
              avgRating={product.avgRating}
              reviewCount={product.reviewCount}
              imageUrl={product.imageUrl}
            />
          ))}
        </div>
      )}
    </div>
  )
}
