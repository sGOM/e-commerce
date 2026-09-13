import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Search } from 'lucide-react'
import { categoryApi, collectionApi, flashSaleApi, productApi } from '../api/endpoints'
import ProductCard, { ProductCardSkeleton } from '../components/ProductCard'
import FlashSaleCard from '../components/FlashSaleCard'
import type {
  Category,
  CollectionSummary,
  FlashSale,
  PageResponse,
  PopularProduct,
  ProductSummary,
} from '../api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Pagination,
  PaginationContent,
  PaginationEllipsis,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from '@/components/ui/pagination'
import { cn } from '@/lib/utils'

/** 페이지 번호 목록(첫/마지막/현재±1 + 말줄임). */
function pageItems(current: number, total: number): (number | 'ellipsis')[] {
  const picked = new Set<number>([0, total - 1])
  for (let i = current - 1; i <= current + 1; i++) {
    if (i >= 0 && i < total) picked.add(i)
  }
  const sorted = [...picked].sort((a, b) => a - b)
  const out: (number | 'ellipsis')[] = []
  let prev = -1
  for (const p of sorted) {
    if (prev >= 0 && p - prev > 1) out.push('ellipsis')
    out.push(p)
    prev = p
  }
  return out
}

export default function ProductListPage() {
  const [params, setParams] = useSearchParams()
  const keyword = params.get('keyword') ?? ''
  const categoryId = params.get('categoryId') ? Number(params.get('categoryId')) : null
  const page = Number(params.get('page') ?? '0')

  const [input, setInput] = useState(keyword)
  const [categories, setCategories] = useState<Category[]>([])
  const [collections, setCollections] = useState<CollectionSummary[]>([])
  const [flashSales, setFlashSales] = useState<FlashSale[]>([])
  const [popular, setPopular] = useState<PopularProduct[]>([])
  const [data, setData] = useState<PageResponse<ProductSummary> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  // 카테고리는 한 번만 로드
  useEffect(() => {
    categoryApi.list().then(setCategories).catch(() => setCategories([]))
  }, [])

  // 인기 상품·기획전은 기본 화면(검색·필터 없을 때)에만 노출
  const isDefaultView = !keyword && categoryId == null
  useEffect(() => {
    if (isDefaultView) {
      productApi.popular(8).then(setPopular).catch(() => setPopular([]))
      collectionApi.list().then(setCollections).catch(() => setCollections([]))
      flashSaleApi.list().then(setFlashSales).catch(() => setFlashSales([]))
    } else {
      setPopular([])
      setCollections([])
      setFlashSales([])
    }
  }, [isDefaultView])

  useEffect(() => {
    setLoading(true)
    setError(null)
    productApi
      .search({ keyword: keyword || undefined, categoryId: categoryId ?? undefined, page })
      .then(setData)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [keyword, categoryId, page, reloadKey])

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    const next: Record<string, string> = {}
    if (input) next.keyword = input
    if (categoryId != null) next.categoryId = String(categoryId)
    setParams(next)
  }

  const selectCategory = (id: number | null) => {
    const next: Record<string, string> = {}
    if (keyword) next.keyword = keyword
    if (id != null) next.categoryId = String(id)
    setParams(next)
  }

  const goPage = (p: number) => {
    const next: Record<string, string> = { page: String(p) }
    if (keyword) next.keyword = keyword
    if (categoryId != null) next.categoryId = String(categoryId)
    setParams(next)
  }

  const resetFilters = () => {
    setInput('')
    setParams({})
  }

  const hasFilter = Boolean(keyword) || categoryId != null

  return (
    <div>
      {/* 검색 바 */}
      <form onSubmit={submit} className="mb-4 flex gap-2">
        <div className="relative flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="상품명 검색"
            aria-label="상품명 검색"
            className="pl-9"
          />
        </div>
        <Button type="submit">검색</Button>
      </form>

      {/* 카테고리 필터 */}
      {categories.length > 0 && (
        <div
          role="group"
          aria-label="카테고리 필터"
          className="mb-6 flex gap-2 overflow-x-auto pb-1 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
        >
          <Button
            type="button"
            variant={categoryId == null ? 'default' : 'outline'}
            size="sm"
            aria-pressed={categoryId == null}
            onClick={() => selectCategory(null)}
            className="shrink-0 rounded-full"
          >
            전체
          </Button>
          {categories.map((c) => (
            <Button
              key={c.categoryId}
              type="button"
              variant={categoryId === c.categoryId ? 'default' : 'outline'}
              size="sm"
              aria-pressed={categoryId === c.categoryId}
              onClick={() => selectCategory(c.categoryId)}
              className="shrink-0 rounded-full"
            >
              {c.name}
            </Button>
          ))}
        </div>
      )}

      {/* 타임딜 캐러셀 — 진행 중(ONGOING)만, 카운트다운·진행률 포함 */}
      {isDefaultView && flashSales.length > 0 && (
        <section className="mb-8">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-lg font-semibold">⏰ 타임딜</h2>
            <Link
              to="/flash-sales"
              className="text-sm text-muted-foreground hover:text-foreground hover:underline"
            >
              전체보기
            </Link>
          </div>
          <div className="flex gap-3 overflow-x-auto pb-1 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
            {flashSales.slice(0, 8).map((fs) => (
              <FlashSaleCard key={fs.id} flashSale={fs} className="w-44 shrink-0 sm:w-52" />
            ))}
          </div>
        </section>
      )}

      {/* 기획전 캐러셀 — 인기상품(자동 랭킹)과 달리 MD가 수동 편성한 컬렉션 */}
      {isDefaultView && collections.length > 0 && (
        <section className="mb-8">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-lg font-semibold">🎁 기획전</h2>
            <Link
              to="/collections"
              className="text-sm text-muted-foreground hover:text-foreground hover:underline"
            >
              전체보기
            </Link>
          </div>
          <div className="flex gap-3 overflow-x-auto pb-1 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
            {collections.map((c) => (
              <Link
                key={c.id}
                to={`/collections/${c.id}`}
                className="group relative flex aspect-[16/9] w-64 shrink-0 flex-col justify-end overflow-hidden rounded-lg border border-border bg-muted p-3 shadow-sm transition-shadow hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 sm:w-72"
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
                  <p className="truncate font-semibold">{c.title}</p>
                  {c.subtitle && <p className="truncate text-xs text-white/85">{c.subtitle}</p>}
                </div>
              </Link>
            ))}
          </div>
        </section>
      )}

      {/* 인기 상품 */}
      {isDefaultView && popular.length > 0 && (
        <section className="mb-8">
          <h2 className="mb-3 text-lg font-semibold">🔥 인기 상품</h2>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            {popular.map((p) => (
              <ProductCard
                key={p.id}
                id={p.id}
                name={p.name}
                basePrice={p.basePrice}
                status={p.status}
                storeName={p.storeName}
                badge={`${p.soldQuantity.toLocaleString('ko-KR')}개 판매`}
                avgRating={p.avgRating}
                reviewCount={p.reviewCount}
              />
            ))}
          </div>
        </section>
      )}

      {/* 전체 상품 */}
      {isDefaultView && (
        <h2 className="mb-3 text-lg font-semibold">전체 상품</h2>
      )}

      {loading && (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
          {Array.from({ length: 8 }, (_, i) => (
            <ProductCardSkeleton key={i} />
          ))}
        </div>
      )}

      {error && !loading && (
        <div className="mx-auto max-w-sm rounded-lg border border-border bg-card p-8 text-center shadow-sm">
          <p className="text-2xl">⚠️</p>
          <p className="mt-2 text-sm text-muted-foreground">{error}</p>
          <Button
            variant="outline"
            className="mt-4"
            onClick={() => setReloadKey((k) => k + 1)}
          >
            다시 시도
          </Button>
        </div>
      )}

      {data && !loading && !error && (
        <>
          {data.content.length === 0 ? (
            <div className="mx-auto max-w-sm py-16 text-center">
              <p className="text-4xl">🔍</p>
              <p className="mt-3 text-sm text-muted-foreground">
                조건에 맞는 상품이 없습니다.
              </p>
              {hasFilter && (
                <Button variant="outline" className="mt-4" onClick={resetFilters}>
                  필터 초기화
                </Button>
              )}
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
              {data.content.map((p) => (
                <ProductCard
                  key={p.id}
                  id={p.id}
                  name={p.name}
                  basePrice={p.basePrice}
                  status={p.status}
                  storeName={p.storeName}
                  avgRating={p.avgRating}
                  reviewCount={p.reviewCount}
                />
              ))}
            </div>
          )}

          {data.totalPages > 1 && (
            <Pagination className="mt-8">
              <PaginationContent>
                <PaginationItem>
                  <PaginationPrevious
                    text="이전"
                    href="#"
                    aria-disabled={page === 0}
                    className={cn(
                      'h-11',
                      page === 0 && 'pointer-events-none opacity-50',
                    )}
                    onClick={(e) => {
                      e.preventDefault()
                      if (page > 0) goPage(page - 1)
                    }}
                  />
                </PaginationItem>

                {/* 모바일 축약: n / N */}
                <PaginationItem className="sm:hidden">
                  <span className="px-3 text-sm tabular-nums text-muted-foreground">
                    {page + 1} / {data.totalPages}
                  </span>
                </PaginationItem>

                {/* 데스크톱: 페이지 번호 */}
                {pageItems(page, data.totalPages).map((it, i) =>
                  it === 'ellipsis' ? (
                    <PaginationItem key={`e${i}`} className="hidden sm:flex">
                      <PaginationEllipsis />
                    </PaginationItem>
                  ) : (
                    <PaginationItem key={it} className="hidden sm:flex">
                      <PaginationLink
                        href="#"
                        isActive={it === page}
                        className="size-11"
                        onClick={(e) => {
                          e.preventDefault()
                          goPage(it)
                        }}
                      >
                        {it + 1}
                      </PaginationLink>
                    </PaginationItem>
                  ),
                )}

                <PaginationItem>
                  <PaginationNext
                    text="다음"
                    href="#"
                    aria-disabled={page >= data.totalPages - 1}
                    className={cn(
                      'h-11',
                      page >= data.totalPages - 1 &&
                        'pointer-events-none opacity-50',
                    )}
                    onClick={(e) => {
                      e.preventDefault()
                      if (page < data.totalPages - 1) goPage(page + 1)
                    }}
                  />
                </PaginationItem>
              </PaginationContent>
            </Pagination>
          )}
        </>
      )}
    </div>
  )
}
