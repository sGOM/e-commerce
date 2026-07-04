import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { adminCollectionApi } from '../../api/endpoints'
import { ApiError } from '../../api/client'
import { collectionStatusLabel } from '../../labels'
import type { CollectionStatus, CollectionSummary, PageResponse } from '../../api/types'

const statusFilters: (CollectionStatus | '')[] = ['', 'DRAFT', 'PUBLISHED', 'ENDED']

const statusBadgeClass: Record<CollectionStatus, string> = {
  DRAFT: 'bg-slate-100 text-slate-600',
  PUBLISHED: 'bg-green-50 text-green-600',
  ENDED: 'bg-slate-100 text-slate-400',
}

export default function AdminCollectionsPage() {
  const [status, setStatus] = useState<CollectionStatus | ''>('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<CollectionSummary> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setData(await adminCollectionApi.search({ status: status || undefined, page }))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '불러오기에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }, [status, page])

  useEffect(() => {
    load()
  }, [load])

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex gap-2">
          {statusFilters.map((s) => (
            <button
              key={s || 'all'}
              onClick={() => {
                setStatus(s)
                setPage(0)
              }}
              className={`rounded-full px-3 py-1 text-sm ${
                status === s ? 'bg-indigo-600 text-white' : 'border bg-white text-slate-600'
              }`}
            >
              {s ? collectionStatusLabel[s] : '전체'}
            </button>
          ))}
        </div>
        <Link
          to="/admin/collections/new"
          className="rounded-lg bg-indigo-600 px-3 py-1.5 text-sm font-semibold text-white hover:bg-indigo-700"
        >
          + 기획전 등록
        </Link>
      </div>

      {error && <p className="text-sm text-red-500">{error}</p>}
      {loading ? (
        <p className="py-10 text-center text-slate-400">불러오는 중…</p>
      ) : !data || data.content.length === 0 ? (
        <p className="py-10 text-center text-slate-400">등록된 기획전이 없습니다.</p>
      ) : (
        <>
          <ul className="space-y-2">
            {data.content.map((c) => (
              <li key={c.id} className="rounded-xl border bg-white p-4">
                <Link to={`/admin/collections/${c.id}`} className="flex items-center justify-between gap-2">
                  <div className="min-w-0">
                    <p className="truncate font-medium">{c.title}</p>
                    <p className="mt-0.5 truncate text-xs text-slate-400">
                      {new Date(c.startAt).toLocaleDateString('ko-KR')} ~{' '}
                      {new Date(c.endAt).toLocaleDateString('ko-KR')} · 순서 {c.displayOrder} · 상품{' '}
                      {c.productCount}개
                    </p>
                  </div>
                  <span
                    className={`shrink-0 rounded px-2 py-0.5 text-xs ${statusBadgeClass[c.status]}`}
                  >
                    {collectionStatusLabel[c.status]}
                  </span>
                </Link>
              </li>
            ))}
          </ul>

          {data.totalPages > 1 && (
            <div className="flex justify-center gap-1">
              {Array.from({ length: data.totalPages }, (_, i) => (
                <button
                  key={i}
                  onClick={() => setPage(i)}
                  className={`h-8 w-8 rounded text-sm ${
                    i === page ? 'bg-indigo-600 text-white' : 'border bg-white text-slate-600'
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
