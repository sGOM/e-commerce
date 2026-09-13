import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { adminCollectionApi } from '../../api/endpoints'
import { ApiError } from '../../api/client'
import { collectionStatusLabel } from '../../labels'
import type { CollectionStatus, CollectionSummary, PageResponse } from '../../api/types'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { cn } from '@/lib/utils'

const statusFilters: (CollectionStatus | '')[] = ['', 'DRAFT', 'PUBLISHED', 'ENDED']

const statusBadgeClass: Record<CollectionStatus, string> = {
  DRAFT: 'bg-muted text-muted-foreground',
  PUBLISHED: 'bg-success/10 text-success',
  ENDED: 'bg-muted text-muted-foreground',
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
            <Button
              key={s || 'all'}
              type="button"
              size="sm"
              variant={status === s ? 'default' : 'outline'}
              className="rounded-full"
              onClick={() => {
                setStatus(s)
                setPage(0)
              }}
            >
              {s ? collectionStatusLabel[s] : '전체'}
            </Button>
          ))}
        </div>
        <Button asChild size="sm">
          <Link to="/admin/collections/new">+ 기획전 등록</Link>
        </Button>
      </div>

      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
      {loading ? (
        <p className="py-10 text-center text-sm text-muted-foreground">불러오는 중…</p>
      ) : !data || data.content.length === 0 ? (
        <p className="py-10 text-center text-sm text-muted-foreground">등록된 기획전이 없습니다.</p>
      ) : (
        <>
          <ul className="space-y-2">
            {data.content.map((c) => (
              <li key={c.id}>
                <Card className="py-0">
                  <Link to={`/admin/collections/${c.id}`} className="flex items-center justify-between gap-2 p-4">
                    <div className="min-w-0">
                      <p className="truncate font-medium">{c.title}</p>
                      <p className="mt-0.5 truncate text-xs text-muted-foreground">
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
                </Card>
              </li>
            ))}
          </ul>

          {data.totalPages > 1 && (
            <div className="flex justify-center gap-1">
              {Array.from({ length: data.totalPages }, (_, i) => (
                <button
                  key={i}
                  onClick={() => setPage(i)}
                  className={cn(
                    'h-8 w-8 rounded text-sm',
                    i === page
                      ? 'bg-primary text-primary-foreground'
                      : 'border border-input bg-background text-muted-foreground',
                  )}
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
