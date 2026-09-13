import { useEffect, useState } from 'react'
import { meApi } from '../api/endpoints'
import { formatKRW } from '../api/client'
import type { IssuedCoupon, PointSummary, PointTransactionType } from '../api/types'
import { Card } from '@/components/ui/card'
import { cn } from '@/lib/utils'

const pointTypeLabel: Record<PointTransactionType, string> = {
  EARN: '적립',
  USE: '사용',
  CANCEL_USE: '사용취소 환원',
  CANCEL_EARN: '적립취소 회수',
  EXPIRE: '만료',
}

export default function MyPage() {
  const [coupons, setCoupons] = useState<IssuedCoupon[]>([])
  const [points, setPoints] = useState<PointSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    Promise.all([meApi.coupons(), meApi.points()])
      .then(([c, p]) => {
        setCoupons(c)
        setPoints(p)
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  if (loading) return <p className="py-20 text-center text-sm text-muted-foreground">불러오는 중…</p>
  if (error) return <p className="py-20 text-center text-sm text-destructive">{error}</p>

  return (
    <div className="space-y-8">
      <section>
        <h1 className="mb-4 text-xl font-bold">내 포인트</h1>
        <Card className="p-5">
          <p className="text-sm text-muted-foreground">사용 가능 포인트</p>
          <p className="text-2xl font-bold text-primary">
            {formatKRW(points?.balance ?? 0)}
          </p>
          {points && points.transactions.length > 0 && (
            <ul className="mt-4 divide-y divide-border text-sm">
              {points.transactions.slice(0, 10).map((t, i) => (
                <li key={i} className="flex justify-between py-2">
                  <span className="text-muted-foreground">
                    {pointTypeLabel[t.type]}
                    {t.orderId ? ` · 주문 #${t.orderId}` : ''}
                  </span>
                  <span className={t.amount >= 0 ? 'text-foreground' : 'text-destructive'}>
                    {t.amount >= 0 ? '+' : ''}
                    {t.amount.toLocaleString('ko-KR')}P
                  </span>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </section>

      <section>
        <h2 className="mb-4 text-xl font-bold">내 쿠폰</h2>
        {coupons.length === 0 ? (
          <p className="py-10 text-center text-sm text-muted-foreground">보유한 쿠폰이 없습니다.</p>
        ) : (
          <ul className="grid gap-3 sm:grid-cols-2">
            {coupons.map((c) => (
              <li key={c.issuedCouponId}>
                <Card className={cn('p-4', c.used && 'opacity-60')}>
                  <div className="flex items-center justify-between">
                    <p className="font-semibold">{c.name}</p>
                    {c.used && (
                      <span className="rounded bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                        사용완료
                      </span>
                    )}
                  </div>
                  <p className="mt-1 text-lg font-bold text-primary">
                    {c.discountType === 'RATE'
                      ? `${c.discountValue}% 할인`
                      : `${formatKRW(c.discountValue)} 할인`}
                  </p>
                  <p className="mt-1 text-xs text-muted-foreground">
                    {formatKRW(c.minOrderAmount)} 이상 ·{' '}
                    {new Date(c.validUntil).toLocaleDateString('ko-KR')}까지
                  </p>
                </Card>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}
