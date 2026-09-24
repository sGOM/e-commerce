import { useCallback, useEffect, useState } from 'react'
import { sellerApi } from '../../api/endpoints'
import { ApiError, formatKRW } from '../../api/client'
import { subOrderStatusLabel } from '../../labels'
import type { SellerSubOrder } from '../../api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent } from '@/components/ui/card'

export default function SellerOrdersPage() {
  const [orders, setOrders] = useState<SellerSubOrder[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setOrders(await sellerApi.listOrders())
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  if (loading) return <p className="py-10 text-center text-sm text-muted-foreground">불러오는 중…</p>
  if (error) return <p className="py-10 text-center text-sm text-destructive">{error}</p>

  return (
    <div className="space-y-3">
      {orders.length === 0 ? (
        <p className="py-10 text-center text-sm text-muted-foreground">판매 주문이 없습니다.</p>
      ) : (
        orders.map((o) => <SellerOrderCard key={o.subOrderId} order={o} onShipped={load} />)
      )}
    </div>
  )
}

function SellerOrderCard({ order, onShipped }: { order: SellerSubOrder; onShipped: () => void }) {
  const [courier, setCourier] = useState('')
  const [tracking, setTracking] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  // 송장 등록 가능: 결제완료/상품준비 상태
  const shippable = order.status === 'PAID' || order.status === 'PREPARING'

  const ship = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await sellerApi.ship(order.subOrderId, courier, tracking)
      onShipped()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '송장 등록 실패')
      setSubmitting(false)
    }
  }

  return (
    <Card>
      <CardContent>
        <div className="mb-2 flex items-center justify-between">
          <p className="text-sm font-medium">{order.orderNumber}</p>
          <span className="rounded bg-muted px-2 py-0.5 text-xs text-muted-foreground">
            {subOrderStatusLabel[order.status]}
          </span>
        </div>
        <ul className="mb-2 space-y-1 text-sm text-muted-foreground">
          {order.items.map((it, i) => (
            <li key={i} className="flex justify-between">
              <span>
                {it.productName}{' '}
                <span className="text-muted-foreground">
                  {it.optionName} · {it.quantity}개
                </span>
              </span>
              <span>{formatKRW(it.lineTotal)}</span>
            </li>
          ))}
        </ul>

        {order.shipment ? (
          <p className="text-xs text-muted-foreground">
            🚚 {order.shipment.courier} · {order.shipment.trackingNumber}
          </p>
        ) : shippable ? (
          <form onSubmit={ship} className="flex flex-wrap items-center gap-2 border-t border-border pt-3">
            <Input
              required
              placeholder="택배사"
              aria-label="택배사"
              value={courier}
              onChange={(e) => setCourier(e.target.value)}
              className="w-28"
            />
            <Input
              required
              placeholder="송장번호"
              aria-label="송장번호"
              value={tracking}
              onChange={(e) => setTracking(e.target.value)}
              className="flex-1"
            />
            <Button type="submit" size="sm" disabled={submitting}>
              발송
            </Button>
            {error && (
              <p role="alert" className="w-full text-xs text-destructive">
                {error}
              </p>
            )}
          </form>
        ) : null}
      </CardContent>
    </Card>
  )
}
