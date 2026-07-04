import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Bell } from 'lucide-react'
import { notificationApi } from '../api/endpoints'
import { notifyNotificationsChanged, useUnreadNotificationCount } from '../hooks/useNotifications'
import type { AppNotification } from '../api/types'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { cn } from '@/lib/utils'

const PREVIEW_SIZE = 8

function timeAgo(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime()
  const min = Math.floor(diffMs / 60_000)
  if (min < 1) return '방금 전'
  if (min < 60) return `${min}분 전`
  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour}시간 전`
  return `${Math.floor(hour / 24)}일 전`
}

/**
 * 헤더 알림 벨 + 안읽음 배지 + 드롭다운 미리보기.
 * 재입고 알림 외에 향후 알림(멤버십/정기배송/타임딜 등)도 이 컴포넌트를 그대로 재사용한다.
 */
export default function NotificationBell() {
  const unreadCount = useUnreadNotificationCount()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState<AppNotification[]>([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!open) return
    setLoading(true)
    notificationApi
      .list({ size: PREVIEW_SIZE })
      .then((p) => setItems(p.content))
      .catch(() => setItems([]))
      .finally(() => setLoading(false))
  }, [open])

  const handleSelect = async (n: AppNotification) => {
    if (!n.isRead) {
      try {
        await notificationApi.markRead(n.id)
        setItems((prev) => prev.map((it) => (it.id === n.id ? { ...it, isRead: true } : it)))
        notifyNotificationsChanged()
      } catch {
        // 읽음 처리 실패는 조용히 무시 — 다음 목록 조회에서 다시 시도된다.
      }
    }
    setOpen(false)
    if (n.linkUrl) navigate(n.linkUrl)
  }

  const label =
    unreadCount != null && unreadCount > 0 ? `알림, 안읽음 ${unreadCount}개` : '알림'

  return (
    <DropdownMenu open={open} onOpenChange={setOpen}>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" className="relative" aria-label={label}>
          <Bell className="size-5" />
          {unreadCount != null && unreadCount > 0 && (
            <Badge
              className="absolute -right-1 -top-1 size-5 justify-center rounded-full p-0 text-[10px] tabular-nums"
              aria-hidden="true"
            >
              {unreadCount > 99 ? '99+' : unreadCount}
            </Badge>
          )}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-80">
        <DropdownMenuLabel>알림</DropdownMenuLabel>
        <DropdownMenuSeparator />
        {loading ? (
          <p className="px-2 py-4 text-center text-sm text-muted-foreground">불러오는 중…</p>
        ) : items.length === 0 ? (
          <p className="px-2 py-4 text-center text-sm text-muted-foreground">알림이 없습니다.</p>
        ) : (
          items.map((n) => (
            <DropdownMenuItem
              key={n.id}
              onSelect={() => handleSelect(n)}
              className={cn(
                'flex flex-col items-start gap-0.5 whitespace-normal py-2',
                !n.isRead && 'bg-accent/60',
              )}
            >
              <div className="flex w-full items-center gap-1.5">
                {!n.isRead && (
                  <span
                    className="size-1.5 shrink-0 rounded-full bg-primary"
                    aria-hidden="true"
                  />
                )}
                <span className="truncate text-sm font-medium">{n.title}</span>
              </div>
              <span className="line-clamp-2 text-xs text-muted-foreground">{n.body}</span>
              <span className="text-[11px] text-muted-foreground">{timeAgo(n.createdAt)}</span>
            </DropdownMenuItem>
          ))
        )}
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild>
          <button
            type="button"
            className="w-full text-center text-sm text-primary"
            onClick={() => {
              setOpen(false)
              navigate('/notifications')
            }}
          >
            알림 전체보기
          </button>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
