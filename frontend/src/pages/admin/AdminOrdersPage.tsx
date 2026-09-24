import { useCallback, useEffect, useState } from 'react'
import { adminApi } from '../../api/endpoints'
import { ApiError, formatKRW } from '../../api/client'
import { orderStatusLabel } from '../../labels'
import type { OrderStatus, OrderSummary, PageResponse } from '../../api/types'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { cn } from '@/lib/utils'

export default function AdminOrdersPage() {
  const [status, setStatus] = useState<OrderStatus | ''>('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<OrderSummary> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setData(await adminApi.searchOrders({ status: status || undefined, page }))
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setLoading(false)
    }
  }, [status, page])

  useEffect(() => {
    load()
  }, [load])

  const refund = async (orderId: number) => {
    if (!confirm(`주문 #${orderId} 을(를) 환불 처리하시겠습니까?`)) return
    setError(null)
    try {
      await adminApi.refundOrder(orderId)
      load()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '환불 실패')
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-2">
        {(['', 'CREATED', 'PAID', 'CANCELED'] as const).map((s) => (
          <Button
            key={s || 'all'}
            type="button"
            size="sm"
            variant={status === s ? 'default' : 'outline'}
            className="rounded-full"
            onClick={() => {
              setStatus(s)
              setPage(0)
            }}
          >
            {s ? orderStatusLabel[s] : '전체'}
          </Button>
        ))}
      </div>

      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
      {loading ? (
        <p className="py-10 text-center text-sm text-muted-foreground">불러오는 중…</p>
      ) : !data || data.content.length === 0 ? (
        <p className="py-10 text-center text-sm text-muted-foreground">주문이 없습니다.</p>
      ) : (
        <>
          <ul className="space-y-2">
            {data.content.map((o) => (
              <li key={o.orderId}>
                <Card className="flex-row items-center justify-between p-4">
                  <div>
                    <p className="text-sm font-medium">{o.orderNumber}</p>
                    <p className="text-xs text-muted-foreground">{new Date(o.createdAt).toLocaleString('ko-KR')}</p>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className="rounded bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                      {orderStatusLabel[o.status]}
                    </span>
                    <span className="font-semibold text-primary">{formatKRW(o.payableAmount)}</span>
                    {o.status !== 'CANCELED' && (
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        className="border-destructive/30 text-destructive hover:bg-destructive/10"
                        onClick={() => refund(o.orderId)}
                      >
                        환불
                      </Button>
                    )}
                  </div>
                </Card>
              </li>
            ))}
          </ul>

          {data.totalPages > 1 && (
            <div className="flex justify-center gap-1">
              {Array.from({ length: data.totalPages }, (_, i) => (
                <button
                  key={i}
                  onClick={() => setPage(i)}
                  className={cn(
                    'h-8 w-8 rounded text-sm',
                    i === page
                      ? 'bg-primary text-primary-foreground'
                      : 'border border-input bg-background text-muted-foreground',
                  )}
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
