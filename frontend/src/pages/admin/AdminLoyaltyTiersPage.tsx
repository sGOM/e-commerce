import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import { adminCartReminderApi, adminLoyaltyTierApi } from '../../api/endpoints'
import { ApiError, formatKRW } from '../../api/client'
import { loyaltyTierBadgeClass, loyaltyTierLabel } from '../../labels'
import type { AdminLoyaltyTierResponse, LoyaltyTier, PageResponse } from '../../api/types'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

const tierFilters: LoyaltyTier[] = ['BRONZE', 'SILVER', 'GOLD', 'VIP']

/** 관리자 로열티 등급 조회 + 수동 재계산. 장바구니 이탈 리마인드 수동 트리거도 함께 둔다(운영용, 선택). */
export default function AdminLoyaltyTiersPage() {
  const [tier, setTier] = useState<LoyaltyTier>('BRONZE')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<AdminLoyaltyTierResponse> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [recalculating, setRecalculating] = useState(false)
  const [runningReminder, setRunningReminder] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setData(await adminLoyaltyTierApi.search({ tier, page }))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '불러오기에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }, [tier, page])

  useEffect(() => {
    load()
  }, [load])

  const recalculate = async () => {
    if (!confirm('전 회원 로열티 등급을 지금 수동으로 재계산하시겠습니까?')) return
    setRecalculating(true)
    try {
      const result = await adminLoyaltyTierApi.recalculate()
      toast.success(
        `등급 재계산을 실행했습니다. 상승 ${result.upgradedCount}건 / 하락 ${result.downgradedCount}건 / 유지 ${result.unchangedCount}건 / 오류 ${result.erroredCount}건`,
      )
      load()
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : '재계산 실행에 실패했습니다.')
    } finally {
      setRecalculating(false)
    }
  }

  const runCartReminder = async () => {
    if (!confirm('장바구니 이탈 리마인드 배치를 지금 수동으로 실행하시겠습니까?')) return
    setRunningReminder(true)
    try {
      const result = await adminCartReminderApi.run()
      toast.success(`리마인드 발송 ${result.remindedCount}건 / 오류 ${result.erroredCount}건`)
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : '배치 실행에 실패했습니다.')
    } finally {
      setRunningReminder(false)
    }
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle className="text-base">운영 배치</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-wrap gap-2">
          <Button type="button" size="sm" disabled={recalculating} onClick={recalculate}>
            {recalculating ? '실행 중…' : '로열티 등급 재계산 수동 실행'}
          </Button>
          <Button
            type="button"
            size="sm"
            variant="outline"
            disabled={runningReminder}
            onClick={runCartReminder}
          >
            {runningReminder ? '실행 중…' : '장바구니 이탈 리마인드 수동 실행'}
          </Button>
        </CardContent>
      </Card>

      <div className="flex flex-wrap items-center gap-2">
        {tierFilters.map((t) => (
          <button
            key={t}
            onClick={() => {
              setTier(t)
              setPage(0)
            }}
            className={`rounded-full px-3 py-1 text-sm ${
              tier === t
                ? 'bg-primary text-primary-foreground'
                : 'border border-border bg-background text-muted-foreground'
            }`}
          >
            {loyaltyTierLabel[t]}
          </button>
        ))}
      </div>

      {error && <p className="text-sm text-destructive">{error}</p>}
      {loading ? (
        <p className="py-10 text-center text-muted-foreground">불러오는 중…</p>
      ) : !data || data.content.length === 0 ? (
        <p className="py-10 text-center text-muted-foreground">조건에 맞는 회원이 없습니다.</p>
      ) : (
        <>
          <ul className="space-y-2">
            {data.content.map((t) => (
              <li key={t.userId}>
                <div className="flex items-center justify-between gap-2 rounded-xl border border-border bg-card p-4">
                  <div className="min-w-0">
                    <p className="text-sm font-medium">회원 #{t.userId}</p>
                    <p className="mt-0.5 text-xs text-muted-foreground">
                      최근 12개월 순구매액 {formatKRW(t.netPurchaseAmount12m)}
                      {t.calculatedAt &&
                        ` · 산정 시각 ${new Date(t.calculatedAt).toLocaleString('ko-KR')}`}
                    </p>
                  </div>
                  <Badge className={loyaltyTierBadgeClass[t.tier]}>{loyaltyTierLabel[t.tier]}</Badge>
                </div>
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
                    i === page
                      ? 'bg-primary text-primary-foreground'
                      : 'border border-border bg-background text-muted-foreground'
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
