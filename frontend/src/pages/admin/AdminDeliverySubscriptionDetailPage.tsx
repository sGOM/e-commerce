import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { adminDeliverySubscriptionApi } from '../../api/endpoints'
import { ApiError } from '../../api/client'
import { deliverySubscriptionHistoryResultLabel, deliverySubscriptionStatusLabel } from '../../labels'
import type {
  AdminDeliverySubscription,
  DeliverySubscriptionHistory,
  DeliverySubscriptionStatus,
} from '../../api/types'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

function statusBadgeVariant(status: DeliverySubscriptionStatus): 'default' | 'secondary' | 'outline' {
  if (status === 'ACTIVE') return 'default'
  if (status === 'PAUSED') return 'secondary'
  return 'outline'
}

export default function AdminDeliverySubscriptionDetailPage() {
  const { id } = useParams<{ id: string }>()
  const subscriptionId = Number(id)
  const [subscription, setSubscription] = useState<AdminDeliverySubscription | null>(null)
  const [histories, setHistories] = useState<DeliverySubscriptionHistory[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [s, h] = await Promise.all([
        adminDeliverySubscriptionApi.detail(subscriptionId),
        adminDeliverySubscriptionApi.histories(subscriptionId),
      ])
      setSubscription(s)
      setHistories(h)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '불러오기에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }, [subscriptionId])

  useEffect(() => {
    load()
  }, [load])

  return (
    <div className="max-w-2xl space-y-6">
      <Link to="/admin/delivery-subscriptions" className="text-sm text-muted-foreground hover:underline">
        ← 목록으로
      </Link>

      {loading ? (
        <p className="py-10 text-center text-muted-foreground">불러오는 중…</p>
      ) : error ? (
        <p className="py-10 text-center text-destructive">{error}</p>
      ) : subscription ? (
        <>
          <Card>
            <CardHeader className="flex-row items-center justify-between space-y-0">
              <CardTitle>정기배송 #{subscription.id}</CardTitle>
              <Badge variant={statusBadgeVariant(subscription.status)}>
                {deliverySubscriptionStatusLabel[subscription.status]}
              </Badge>
            </CardHeader>
            <CardContent>
              <dl className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <dt className="text-xs text-muted-foreground">회원 ID</dt>
                  <dd>#{subscription.userId}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">상품 옵션 ID</dt>
                  <dd>#{subscription.optionId}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">수량 · 주기</dt>
                  <dd>
                    {subscription.quantity}개 · {subscription.cycleDays}일마다
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">다음 배송일</dt>
                  <dd>{new Date(subscription.nextOrderAt).toLocaleDateString('ko-KR')}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">연속 결제실패</dt>
                  <dd>{subscription.consecutiveFailureCount}회</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">다음 회차 스킵 예약</dt>
                  <dd>{subscription.skipRequested ? '예' : '아니오'}</dd>
                </div>
                {subscription.canceledAt && (
                  <div>
                    <dt className="text-xs text-muted-foreground">해지일</dt>
                    <dd>{new Date(subscription.canceledAt).toLocaleDateString('ko-KR')}</dd>
                  </div>
                )}
              </dl>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">회차별 실행 이력</CardTitle>
            </CardHeader>
            <CardContent>
              {histories.length === 0 ? (
                <p className="py-6 text-center text-sm text-muted-foreground">실행 이력이 없습니다.</p>
              ) : (
                <ul className="divide-y divide-border text-sm">
                  {histories.map((h) => (
                    <li key={h.id} className="flex items-center justify-between gap-2 py-2">
                      <div>
                        <p>{new Date(h.attemptedAt).toLocaleString('ko-KR')}</p>
                        <p className="text-xs text-muted-foreground">
                          {h.detail}
                          {h.orderId && ` · 주문 #${h.orderId}`}
                        </p>
                      </div>
                      <Badge variant={h.result === 'ORDER_CREATED' ? 'default' : 'secondary'}>
                        {deliverySubscriptionHistoryResultLabel[h.result]}
                      </Badge>
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>
        </>
      ) : null}
    </div>
  )
}
