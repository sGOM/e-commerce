import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import { returnApi } from '../api/endpoints'
import { ApiError, formatKRW } from '../api/client'
import { returnReasonLabel, returnStatusLabel } from '../labels'
import type { Order, ReturnReason, OrderReturn } from '../api/types'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'

const REASONS = Object.keys(returnReasonLabel) as ReturnReason[]

/**
 * 주문 상세의 반품 카드 — 이 주문의 반품 이력과, 발송·구매확정된 하위 주문(판매자 단위) 반품 신청 폼.
 * 기간·정산 여부·환불액은 서버가 판단/계산하므로 여기서는 결과만 보여준다.
 */
export default function OrderReturnCard({ order, onRequested }: { order: Order; onRequested: () => void }) {
  const [returns, setReturns] = useState<OrderReturn[]>([])
  const [subOrderId, setSubOrderId] = useState<number | null>(null)
  const [reason, setReason] = useState<ReturnReason>('CHANGE_OF_MIND')
  const [detail, setDetail] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const returnable = order.subOrders.filter((s) => s.status === 'SHIPPED' || s.status === 'DELIVERED')
  const selected = subOrderId ?? returnable[0]?.subOrderId ?? null

  const load = useCallback(async () => {
    try {
      const mine = await returnApi.mine()
      setReturns(mine.filter((r) => order.subOrders.some((s) => s.subOrderId === r.subOrderId)))
    } catch {
      // 이력 조회 실패는 신청을 막지 않는다
    }
  }, [order])

  useEffect(() => {
    load()
  }, [load])

  if (returnable.length === 0 && returns.length === 0) return null

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (selected == null) return
    setSubmitting(true)
    setError(null)
    try {
      const created = await returnApi.request(selected, reason, detail.trim() || undefined)
      toast.success(`반품을 요청했습니다. 예상 환불액 ${formatKRW(created.refundAmount)}`)
      setDetail('')
      setSubOrderId(null)
      await load()
      onRequested()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '반품 요청에 실패했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  const storeName = (id: number) => order.subOrders.find((s) => s.subOrderId === id)?.storeName ?? ''

  return (
    <div className="space-y-4 rounded-xl border border-border bg-card p-5">
      <h2 className="font-bold">반품</h2>

      {returns.length > 0 && (
        <ul className="space-y-2 text-sm">
          {returns.map((r) => (
            <li key={r.returnId} className="flex items-center justify-between gap-2">
              <span>
                {storeName(r.subOrderId)} · {returnReasonLabel[r.reason]}
              </span>
              <span className="text-muted-foreground">
                {returnStatusLabel[r.status]} · 환불 {formatKRW(r.refundAmount)}
                {r.returnFee > 0 && ` (반품 배송비 ${formatKRW(r.returnFee)} 차감)`}
              </span>
            </li>
          ))}
        </ul>
      )}

      {returnable.length > 0 && (
        <form onSubmit={submit} className="space-y-3">
          {returnable.length > 1 && (
            <div className="space-y-1">
              <Label htmlFor="return-sub-order">반품할 판매자 주문</Label>
              <select
                id="return-sub-order"
                value={selected ?? ''}
                onChange={(e) => setSubOrderId(Number(e.target.value))}
                className="h-9 w-full rounded-md border border-input bg-background px-3 text-sm"
              >
                {returnable.map((s) => (
                  <option key={s.subOrderId} value={s.subOrderId}>
                    {s.storeName}
                  </option>
                ))}
              </select>
            </div>
          )}
          <div className="space-y-1">
            <Label htmlFor="return-reason">반품 사유</Label>
            <select
              id="return-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value as ReturnReason)}
              className="h-9 w-full rounded-md border border-input bg-background px-3 text-sm"
            >
              {REASONS.map((r) => (
                <option key={r} value={r}>
                  {returnReasonLabel[r]}
                </option>
              ))}
            </select>
          </div>
          <div className="space-y-1">
            <Label htmlFor="return-detail">상세 내용(선택)</Label>
            <textarea
              id="return-detail"
              value={detail}
              maxLength={500}
              onChange={(e) => setDetail(e.target.value)}
              className="min-h-20 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
            />
          </div>
          <p className="text-xs text-muted-foreground">
            단순 변심은 반품 배송비가 환불액에서 차감됩니다. 구매확정 후에는 정해진 기간 안에만 반품할 수 있습니다.
          </p>
          {error && (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          )}
          <Button type="submit" variant="outline" disabled={submitting || selected == null} className="w-full">
            {submitting ? '요청 중…' : '반품 신청'}
          </Button>
        </form>
      )}
    </div>
  )
}
