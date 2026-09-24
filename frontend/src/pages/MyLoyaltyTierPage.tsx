import { useEffect, useState } from 'react'
import { loyaltyTierApi } from '../api/endpoints'
import { ApiError, formatKRW } from '../api/client'
import { loyaltyTierBadgeClass, loyaltyTierLabel } from '../labels'
import type { MyLoyaltyTierResponse } from '../api/types'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

/** 마이페이지 로열티 등급 카드 — 진행바로 다음 등급까지 남은 금액을 시각화한다. */
export default function MyLoyaltyTierPage() {
  const [tier, setTier] = useState<MyLoyaltyTierResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    loyaltyTierApi
      .my()
      .then(setTier)
      .catch((e) => setError(e instanceof ApiError ? e.message : '등급 정보를 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
  }, [])

  if (loading) return <p className="py-20 text-center text-sm text-muted-foreground">불러오는 중…</p>
  if (error) return <p className="py-20 text-center text-sm text-destructive">{error}</p>
  if (!tier) return null

  // 다음 등급까지 진행률 — 현재 금액 / (현재 금액 + 남은 금액). 최고 등급(VIP)은 nextTier가 없다.
  const total = tier.netPurchaseAmount12m + (tier.amountToNextTier ?? 0)
  const progress = total > 0 ? Math.min(100, Math.round((tier.netPurchaseAmount12m / total) * 100)) : 100

  return (
    <div className="max-w-xl space-y-6">
      <h1 className="text-xl font-bold">내 등급</h1>
      <Card>
        <CardHeader className="flex-row items-center justify-between space-y-0">
          <CardTitle>로열티 등급</CardTitle>
          <Badge className={loyaltyTierBadgeClass[tier.tier]}>{loyaltyTierLabel[tier.tier]}</Badge>
        </CardHeader>
        <CardContent className="space-y-4 text-sm">
          <div>
            <p className="text-xs text-muted-foreground">최근 12개월 순구매액</p>
            <p className="text-2xl font-bold text-primary">{formatKRW(tier.netPurchaseAmount12m)}</p>
          </div>

          {tier.nextTier ? (
            <div className="space-y-2">
              <div className="flex items-center justify-between text-xs text-muted-foreground">
                <span>
                  다음 등급 <span className="font-medium text-foreground">{loyaltyTierLabel[tier.nextTier]}</span>까지
                </span>
                <span>{formatKRW(tier.amountToNextTier ?? 0)} 남음</span>
              </div>
              <div className="h-2 w-full overflow-hidden rounded-full bg-muted">
                <div className="h-full rounded-full bg-primary transition-all" style={{ width: `${progress}%` }} />
              </div>
            </div>
          ) : (
            <p className="rounded-lg bg-violet-500/10 p-3 text-violet-700 dark:text-violet-400">
              최고 등급(VIP)에 도달했습니다.
            </p>
          )}

          <div className="rounded-lg border border-border bg-muted/40 p-3 text-muted-foreground">
            등급 혜택: 전용 쿠폰 — 등급이 오르면 등급 전용 쿠폰이 자동 발급되어 내 쿠폰함에서 확인할 수 있습니다.
          </div>

          {tier.calculatedAt && (
            <p className="text-xs text-muted-foreground">
              마지막 산정 시각: {new Date(tier.calculatedAt).toLocaleString('ko-KR')}
            </p>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
