import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import { deliverySubscriptionApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { deliverySubscriptionHistoryResultLabel, deliverySubscriptionStatusLabel } from '../labels'
import type { DeliverySubscription, DeliverySubscriptionHistory, DeliverySubscriptionStatus } from '../api/types'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

function statusBadgeVariant(status: DeliverySubscriptionStatus): 'default' | 'secondary' | 'destructive' | 'outline' {
  if (status === 'ACTIVE') return 'default'
  if (status === 'PAUSED') return 'secondary'
  return 'outline'
}

/** 마이페이지: 정기배송 관리 — 상품별 카드(다음 배송일, 주기, 액션), 회차별 이력. */
export default function MyDeliverySubscriptionsPage() {
  const [subscriptions, setSubscriptions] = useState<DeliverySubscription[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setSubscriptions(await deliverySubscriptionApi.my())
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '정기배송 목록을 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const updateOne = (updated: DeliverySubscription) =>
    setSubscriptions((prev) => prev.map((s) => (s.id === updated.id ? updated : s)))

  if (loading) return <p className="py-20 text-center text-sm text-muted-foreground">불러오는 중…</p>
  if (error) return <p className="py-20 text-center text-sm text-destructive">{error}</p>

  return (
    <div className="max-w-2xl space-y-6">
      <h1 className="text-xl font-bold">정기배송 관리</h1>
      {subscriptions.length === 0 ? (
        <p className="py-20 text-center text-sm text-muted-foreground">
          신청한 정기배송이 없습니다. 상품 상세 페이지에서 정기배송을 신청해 보세요.
        </p>
      ) : (
        <ul className="space-y-4">
          {subscriptions.map((s) => (
            <li key={s.id}>
              <DeliverySubscriptionCard subscription={s} onChange={updateOne} />
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function DeliverySubscriptionCard({
  subscription,
  onChange,
}: {
  subscription: DeliverySubscription
  onChange: (s: DeliverySubscription) => void
}) {
  const [busy, setBusy] = useState(false)
  const [historyOpen, setHistoryOpen] = useState(false)
  const [histories, setHistories] = useState<DeliverySubscriptionHistory[] | null>(null)
  const [historyLoading, setHistoryLoading] = useState(false)
  const [historyError, setHistoryError] = useState<string | null>(null)

  const canceled = subscription.status === 'CANCELED'
  const active = subscription.status === 'ACTIVE'
  const paused = subscription.status === 'PAUSED'

  const run = async (action: () => Promise<DeliverySubscription>, successMessage: string) => {
    setBusy(true)
    try {
      onChange(await action())
      toast.success(successMessage)
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : '요청에 실패했습니다.')
    } finally {
      setBusy(false)
    }
  }

  const pause = () => run(() => deliverySubscriptionApi.pause(subscription.id), '정기배송을 일시정지했습니다.')
  const resume = () => run(() => deliverySubscriptionApi.resume(subscription.id), '정기배송을 재개했습니다.')
  const skipNext = () => {
    if (!confirm('다음 회차를 건너뛰시겠습니까?')) return
    run(() => deliverySubscriptionApi.skipNext(subscription.id), '다음 회차를 건너뜁니다.')
  }
  const cancelSubscription = () => {
    if (!confirm('정기배송을 해지하시겠습니까? 이미 생성된 주문에는 영향이 없습니다.')) return
    run(() => deliverySubscriptionApi.cancel(subscription.id), '정기배송을 해지했습니다.')
  }

  const toggleHistory = async () => {
    const next = !historyOpen
    setHistoryOpen(next)
    if (next && histories === null) {
      setHistoryLoading(true)
      setHistoryError(null)
      try {
        setHistories(await deliverySubscriptionApi.histories(subscription.id))
      } catch (e) {
        setHistoryError(e instanceof ApiError ? e.message : '이력을 불러오지 못했습니다.')
      } finally {
        setHistoryLoading(false)
      }
    }
  }

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between space-y-0">
        <CardTitle className="text-base">
          {subscription.productName} · {subscription.optionName}
        </CardTitle>
        <Badge variant={statusBadgeVariant(subscription.status)}>
          {deliverySubscriptionStatusLabel[subscription.status]}
        </Badge>
      </CardHeader>
      <CardContent className="space-y-4 text-sm">
        <dl className="grid grid-cols-2 gap-2 text-muted-foreground">
          <div>
            <dt className="text-xs">수량 · 주기</dt>
            <dd className="text-foreground">
              {subscription.quantity}개 · {subscription.cycleDays}일마다
            </dd>
          </div>
          <div>
            <dt className="text-xs">{canceled ? '해지일' : '다음 배송일'}</dt>
            <dd className="text-foreground">
              {canceled && subscription.canceledAt
                ? new Date(subscription.canceledAt).toLocaleDateString('ko-KR')
                : new Date(subscription.nextOrderAt).toLocaleDateString('ko-KR')}
            </dd>
          </div>
        </dl>

        {subscription.skipRequested && active && (
          <p className="rounded-lg bg-muted p-2.5 text-xs text-muted-foreground">
            다음 회차 건너뛰기가 예약되어 있습니다.
          </p>
        )}
        {subscription.consecutiveFailureCount > 0 && !canceled && (
          <p className="rounded-lg bg-destructive/10 p-2.5 text-xs text-destructive">
            최근 결제 실패 {subscription.consecutiveFailureCount}회 누적 — 카드 정보를 확인해 주세요.
          </p>
        )}

        {!canceled && (
          <div className="flex flex-wrap gap-2">
            {active && (
              <Button type="button" size="sm" variant="outline" disabled={busy} onClick={pause}>
                일시정지
              </Button>
            )}
            {paused && (
              <Button type="button" size="sm" variant="outline" disabled={busy} onClick={resume}>
                재개
              </Button>
            )}
            {active && (
              <Button
                type="button"
                size="sm"
                variant="outline"
                disabled={busy || subscription.skipRequested}
                onClick={skipNext}
                title={subscription.skipRequested ? '이미 다음 회차 건너뛰기가 예약되어 있습니다.' : undefined}
              >
                {subscription.skipRequested ? '건너뛰기 예약됨' : '다음 회차 건너뛰기'}
              </Button>
            )}
            <Button type="button" size="sm" variant="destructive" disabled={busy} onClick={cancelSubscription}>
              해지
            </Button>
          </div>
        )}

        <Button type="button" size="sm" variant="ghost" onClick={toggleHistory} className="px-0">
          {historyOpen ? '실행 이력 접기' : '실행 이력 보기'}
        </Button>

        {historyOpen && (
          <div className="rounded-lg border border-border">
            {historyLoading ? (
              <p className="py-6 text-center text-xs text-muted-foreground">불러오는 중…</p>
            ) : historyError ? (
              <p className="py-6 text-center text-xs text-destructive">{historyError}</p>
            ) : !histories || histories.length === 0 ? (
              <p className="py-6 text-center text-xs text-muted-foreground">실행 이력이 없습니다.</p>
            ) : (
              <ul className="divide-y divide-border text-xs">
                {histories.map((h) => (
                  <li key={h.id} className="flex items-center justify-between gap-2 p-3">
                    <div>
                      <p>{new Date(h.attemptedAt).toLocaleString('ko-KR')}</p>
                      {h.detail && <p className="mt-0.5 text-muted-foreground">{h.detail}</p>}
                      {h.orderId && <p className="mt-0.5 text-muted-foreground">주문 #{h.orderId}</p>}
                    </div>
                    <Badge variant={h.result === 'ORDER_CREATED' ? 'default' : 'secondary'}>
                      {deliverySubscriptionHistoryResultLabel[h.result]}
                    </Badge>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
