import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { productApi } from '../api/endpoints'
import { formatKRW } from '../api/client'
import { productStatusLabel } from '../labels'
import type { PageResponse, ProductSummary } from '../api/types'

export default function ProductListPage() {
  const [params, setParams] = useSearchParams()
  const keyword = params.get('keyword') ?? ''
  const page = Number(params.get('page') ?? '0')

  const [input, setInput] = useState(keyword)
  const [data, setData] = useState<PageResponse<ProductSummary> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setLoading(true)
    setError(null)
    productApi
      .search({ keyword: keyword || undefined, page })
      .then(setData)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [keyword, page])

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    setParams(input ? { keyword: input } : {})
  }

  const goPage = (p: number) => {
    const next: Record<string, string> = { page: String(p) }
    if (keyword) next.keyword = keyword
    setParams(next)
  }

  return (
    <div>
      <form onSubmit={submit} className="mb-6 flex gap-2">
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

      {loading && <p className="py-20 text-center text-slate-400">불러오는 중…</p>}
      {error && <p className="py-20 text-center text-red-500">{error}</p>}

      {data && !loading && (
        <>
          {data.content.length === 0 ? (
            <p className="py-20 text-center text-slate-400">상품이 없습니다.</p>
          ) : (
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4">
              {data.content.map((p) => (
                <Link
                  key={p.id}
                  to={`/products/${p.id}`}
                  className="rounded-xl border bg-white p-4 transition hover:shadow-md"
                >
                  <div className="mb-2 flex aspect-square items-center justify-center rounded-lg bg-slate-100 text-3xl">
                    🛍️
                  </div>
                  <p className="truncate text-xs text-slate-400">{p.storeName}</p>
                  <p className="truncate text-sm font-medium">{p.name}</p>
                  <p className="mt-1 font-bold text-indigo-600">
                    {formatKRW(p.basePrice)}
                  </p>
                  {p.status !== 'ON_SALE' && (
                    <span className="mt-1 inline-block rounded bg-slate-200 px-2 py-0.5 text-xs text-slate-600">
                      {productStatusLabel[p.status]}
                    </span>
                  )}
                </Link>
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
                    i === page
                      ? 'bg-indigo-600 text-white'
                      : 'bg-white text-slate-600 hover:bg-slate-100'
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
