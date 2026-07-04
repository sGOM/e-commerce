import { useEffect, useState } from 'react'
import { notificationApi } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'

const NOTIFICATIONS_EVENT = 'notifications-changed'
// 서버 푸시/웹소켓 인프라가 없어(범위 밖) 짧은 주기 폴링으로 대체한다. 헤더 배지 용도라 너무 잦으면
// 트래픽 낭비이므로 60초로 절충. 즉시 반영이 필요한 사용자 행동(읽음 처리)은 이벤트로 별도 갱신한다.
const POLL_INTERVAL_MS = 60_000

/** 알림 읽음 처리 등 알림함 상태가 바뀐 후 배지를 즉시 갱신하도록 알린다. */
export function notifyNotificationsChanged() {
  window.dispatchEvent(new Event(NOTIFICATIONS_EVENT))
}

/**
 * 헤더 알림 벨의 안읽음 개수.
 * - 비로그인 시 null(배지 숨김).
 * - 로그인 시 60초 주기 폴링 + notifyNotificationsChanged() 이벤트로 즉시 갱신.
 */
export function useUnreadNotificationCount(): number | null {
  const { user, loading } = useAuth()
  const [count, setCount] = useState<number | null>(null)

  useEffect(() => {
    if (loading) return
    if (!user) {
      setCount(null)
      return
    }
    let alive = true
    const load = () =>
      notificationApi
        .list({ unreadOnly: true, size: 1 })
        .then((p) => alive && setCount(p.unreadCount))
        .catch(() => alive && setCount(0))

    load()
    const timer = window.setInterval(load, POLL_INTERVAL_MS)
    window.addEventListener(NOTIFICATIONS_EVENT, load)
    return () => {
      alive = false
      window.clearInterval(timer)
      window.removeEventListener(NOTIFICATIONS_EVENT, load)
    }
  }, [user, loading])

  return count
}
