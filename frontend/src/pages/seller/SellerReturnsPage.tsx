import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import { sellerApi } from '../../api/endpoints'
import { ApiError, formatKRW } from '../../api/client'
import { returnReasonLabel, returnStatusLabel } from '../../labels'
import type { OrderReturn } from '../../api/types'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'

type Action = 'approve' | 'complete' | 'reject'

const DONE_MESSAGE: Record<Action, string> = {
  approve: '반품을 승인했습니다. 회수를 진행하세요.',
  complete: '검수를 완료하고 환불했습니다.',
  reject: '반품을 거절했습니다.',
}

/** 판매자 반품 처리 — 요청 승인(회수 시작) → 검수 완료(환불) / 요청·검수 거절. */
export default function SellerReturnsPage() {
  const [returns, setReturns] = useState<OrderReturn[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)

  const load = useCallback(async () => {
    try {
      setReturns(await sellerApi.returns())
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '반품 목록을 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const act = async (r: OrderReturn, action: Action) => {
    if (action === 'complete' && !confirm(`검수를 완료하고 ${formatKRW(r.refundAmount)}을 환불할까요?`)) return
    if (action === 'reject' && !confirm('반품을 거절할까요? 주문은 반품 전 상태로 돌아갑니다.')) return
    setBusyId(r.returnId)
    setError(null)
    try {
      await sellerApi.returnAction(r.returnId, action)
      toast.success(DONE_MESSAGE[action])
      await load()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '반품 처리에 실패했습니다.')
    } finally {
      setBusyId(null)
    }
  }

  if (loading) return <p className="py-10 text-center text-sm text-muted-foreground">불러오는 중…</p>

  return (
    <div className="space-y-3">
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
      {returns.length === 0 ? (
        <p className="py-10 text-center text-sm text-muted-foreground">반품 요청이 없습니다.</p>
      ) : (
        returns.map((r) => (
          <Card key={r.returnId}>
            <CardContent className="space-y-2">
              <div className="flex items-center justify-between">
                <p className="text-sm font-medium">{r.orderNumber}</p>
                <span className="rounded bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                  {returnStatusLabel[r.status]}
                </span>
              </div>
              <p className="text-sm">
                {returnReasonLabel[r.reason]}
                {r.detail && <span className="text-muted-foreground"> · {r.detail}</span>}
              </p>
              <p className="text-xs text-muted-foreground">
                환불 예정 {formatKRW(r.refundAmount)}
                {r.returnFee > 0 && ` (반품 배송비 ${formatKRW(r.returnFee)} 차감)`}
              </p>
              {(r.status === 'REQUESTED' || r.status === 'COLLECTING') && (
                <div className="flex gap-2">
                  {r.status === 'REQUESTED' ? (
                    <Button size="sm" disabled={busyId === r.returnId} onClick={() => act(r, 'approve')}>
                      승인(회수 시작)
                    </Button>
                  ) : (
                    <Button size="sm" disabled={busyId === r.returnId} onClick={() => act(r, 'complete')}>
                      검수 완료·환불
                    </Button>
                  )}
                  <Button size="sm" variant="outline" disabled={busyId === r.returnId} onClick={() => act(r, 'reject')}>
                    거절
                  </Button>
                </div>
              )}
            </CardContent>
          </Card>
        ))
      )}
    </div>
  )
}
