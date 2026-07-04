import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { notificationApi } from '../api/endpoints'
import { notifyNotificationsChanged } from '../hooks/useNotifications'
import { ApiError } from '../api/client'
import type { AppNotification, PageResponse } from '../api/types'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

function timeAgo(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime()
  const min = Math.floor(diffMs / 60_000)
  if (min < 1) return '방금 전'
  if (min < 60) return `${min}분 전`
  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour}시간 전`
  return `${Math.floor(hour / 24)}일 전`
}

const PAGE_SIZE = 20

/** 인앱 알림함 전체 목록 — 재입고 알림 외 향후 알림도 이 화면 하나에서 함께 노출된다. */
export default function NotificationsPage() {
  const navigate = useNavigate()
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<AppNotification> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = () => {
    setLoading(true)
    notificationApi
      .list({ unreadOnly, page, size: PAGE_SIZE })
      .then(setData)
      .catch((e) => setError(e instanceof ApiError ? e.message : '불러오기에 실패했습니다.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
  }, [unreadOnly, page])

  const handleSelect = async (n: AppNotification) => {
    if (!n.isRead) {
      try {
        await notificationApi.markRead(n.id)
        setData((prev) =>
          prev
            ? {
                ...prev,
                content: prev.content.map((it) =>
                  it.id === n.id ? { ...it, isRead: true } : it,
                ),
              }
            : prev,
        )
        notifyNotificationsChanged()
      } catch {
        // 읽음 처리 실패는 조용히 무시 — 다음 새로고침에서 다시 시도된다.
      }
    }
    if (n.linkUrl) navigate(n.linkUrl)
  }

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-xl font-bold">알림함</h1>
        <label className="flex items-center gap-2 text-sm text-muted-foreground">
          <input
            type="checkbox"
            checked={unreadOnly}
            onChange={(e) => {
              setUnreadOnly(e.target.checked)
              setPage(0)
            }}
            className="size-4"
          />
          안읽음만 보기
        </label>
      </div>

      {loading ? (
        <p className="py-20 text-center text-sm text-muted-foreground">불러오는 중…</p>
      ) : error ? (
        <p className="py-20 text-center text-sm text-destructive">{error}</p>
      ) : !data || data.content.length === 0 ? (
        <p className="py-20 text-center text-sm text-muted-foreground">알림이 없습니다.</p>
      ) : (
        <ul className="space-y-2">
          {data.content.map((n) => (
            <li key={n.id}>
              <button
                type="button"
                onClick={() => handleSelect(n)}
                className={cn(
                  'flex w-full flex-col items-start gap-1 rounded-lg border border-border bg-card p-4 text-left transition-colors hover:bg-accent',
                  !n.isRead && 'border-primary/30 bg-accent/40',
                )}
              >
                <div className="flex w-full items-center gap-2">
                  {!n.isRead && (
                    <span className="size-2 shrink-0 rounded-full bg-primary" aria-hidden="true" />
                  )}
                  <span className="font-medium">{n.title}</span>
                  <span className="ml-auto shrink-0 text-xs text-muted-foreground">
                    {timeAgo(n.createdAt)}
                  </span>
                </div>
                <p className="text-sm text-muted-foreground">{n.body}</p>
              </button>
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
