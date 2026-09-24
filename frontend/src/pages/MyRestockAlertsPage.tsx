import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { restockAlertApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { restockAlertStatusLabel } from '../labels'
import type { RestockAlert } from '../api/types'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'

/** 마이페이지: 내가 신청한 재입고 알림 목록 — PENDING 건은 취소 가능하다. */
export default function MyRestockAlertsPage() {
  const [alerts, setAlerts] = useState<RestockAlert[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [cancelingId, setCancelingId] = useState<number | null>(null)

  const load = () => {
    setLoading(true)
    restockAlertApi
      .myAlerts()
      .then((p) => setAlerts(p.content))
      .catch((e) => setError(e instanceof ApiError ? e.message : '불러오기에 실패했습니다.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
  }, [])

  const cancel = async (alert: RestockAlert) => {
    setCancelingId(alert.id)
    try {
      await restockAlertApi.unsubscribe(alert.optionId)
      toast.success('재입고 알림 신청을 취소했습니다.')
      load()
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : '취소에 실패했습니다.')
    } finally {
      setCancelingId(null)
    }
  }

  return (
    <div>
      <h1 className="mb-6 text-xl font-bold">재입고 알림 신청 목록</h1>
      {loading ? (
        <p className="py-20 text-center text-sm text-muted-foreground">불러오는 중…</p>
      ) : error ? (
        <p className="py-20 text-center text-sm text-destructive">{error}</p>
      ) : alerts.length === 0 ? (
        <p className="py-20 text-center text-sm text-muted-foreground">신청한 재입고 알림이 없습니다.</p>
      ) : (
        <ul className="space-y-3">
          {alerts.map((a) => (
            <li
              key={a.id}
              className="flex items-center justify-between gap-3 rounded-lg border border-border bg-card p-4"
            >
              <div className="min-w-0">
                <Link to={`/products/${a.productId}`} className="truncate text-sm font-medium hover:underline">
                  {a.productName}
                </Link>
                <p className="truncate text-xs text-muted-foreground">{a.optionName}</p>
                <p className="mt-1 text-xs text-muted-foreground">
                  신청일 {new Date(a.createdAt).toLocaleDateString('ko-KR')}
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                <Badge variant={a.status === 'NOTIFIED' ? 'default' : 'secondary'}>
                  {restockAlertStatusLabel[a.status]}
                </Badge>
                {a.status === 'PENDING' && (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={cancelingId === a.id}
                    onClick={() => cancel(a)}
                  >
                    취소
                  </Button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
