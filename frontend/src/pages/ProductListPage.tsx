import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { categoryApi, productApi } from '../api/endpoints'
import ProductCard from '../components/ProductCard'
import type { Category, PageResponse, PopularProduct, ProductSummary } from '../api/types'

export default function ProductListPage() {
  const [params, setParams] = useSearchParams()
  const keyword = params.get('keyword') ?? ''
  const categoryId = params.get('categoryId') ? Number(params.get('categoryId')) : null
  const page = Number(params.get('page') ?? '0')

  const [input, setInput] = useState(keyword)
  const [categories, setCategories] = useState<Category[]>([])
  const [popular, setPopular] = useState<PopularProduct[]>([])
  const [data, setData] = useState<PageResponse<ProductSummary> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // 카테고리는 한 번만 로드
  useEffect(() => {
    categoryApi.list().then(setCategories).catch(() => setCategories([]))
  }, [])

  // 인기 상품은 기본 화면(검색·필터 없을 때)에만 노출
  const isDefaultView = !keyword && categoryId == null
  useEffect(() => {
    if (isDefaultView) {
      productApi.popular(8).then(setPopular).catch(() => setPopular([]))
    } else {
      setPopular([])
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
  }, [keyword, categoryId, page])

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

  const chip = (active: boolean) =>
    `rounded-full px-3 py-1 text-sm whitespace-nowrap ${
      active ? 'bg-indigo-600 text-white' : 'border bg-white text-slate-600'
    }`

  return (
    <div>
      <form onSubmit={submit} className="mb-4 flex gap-2">
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="상품명 검색"
          className="flex-1 rounded-lg border px-3 py-2 text-sm"
        />
        <button className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-700">
          검색
        </button>
      </form>

      {categories.length > 0 && (
        <div className="mb-6 flex gap-2 overflow-x-auto pb-1">
          <button onClick={() => selectCategory(null)} className={chip(categoryId == null)}>
            전체
          </button>
          {categories.map((c) => (
            <button
              key={c.categoryId}
              onClick={() => selectCategory(c.categoryId)}
              className={chip(categoryId === c.categoryId)}
            >
              {c.name}
            </button>
          ))}
        </div>
      )}

      {isDefaultView && popular.length > 0 && (
        <section className="mb-8">
          <h2 className="mb-3 text-lg font-bold">🔥 인기 상품</h2>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            {popular.map((p) => (
              <ProductCard
                key={p.id}
                id={p.id}
                name={p.name}
                basePrice={p.basePrice}
                status={p.status}
                storeName={p.storeName}
                badge={`${p.soldQuantity.toLocaleString('ko-KR')}개 판매`}
              />
            ))}
          </div>
        </section>
      )}

      {loading && <p className="py-20 text-center text-slate-400">불러오는 중…</p>}
      {error && <p className="py-20 text-center text-red-500">{error}</p>}

      {data && !loading && (
        <>
          {isDefaultView && <h2 className="mb-3 text-lg font-bold">전체 상품</h2>}
          {data.content.length === 0 ? (
            <p className="py-20 text-center text-slate-400">상품이 없습니다.</p>
          ) : (
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4">
              {data.content.map((p) => (
                <ProductCard
                  key={p.id}
                  id={p.id}
                  name={p.name}
                  basePrice={p.basePrice}
                  status={p.status}
                  storeName={p.storeName}
                />
              ))}
            </div>
          )}

          {data.totalPages > 1 && (
            <div className="mt-8 flex justify-center gap-1">
              {Array.from({ length: data.totalPages }, (_, i) => (
                <button
                  key={i}
                  onClick={() => goPage(i)}
                  className={`h-8 w-8 rounded text-sm ${
                    i === page ? 'bg-indigo-600 text-white' : 'bg-white text-slate-600 hover:bg-slate-100'
                  }`}
                >
                  {i + 1}
                </button>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  )
}
