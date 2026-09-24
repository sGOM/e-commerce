import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { orderApi } from '../api/endpoints'
import { formatKRW } from '../api/client'
import { orderStatusLabel } from '../labels'
import type { OrderSummary, PageResponse } from '../api/types'
import { Card } from '@/components/ui/card'

export default function MyOrdersPage() {
  const [data, setData] = useState<PageResponse<OrderSummary> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    orderApi
      .myOrders()
      .then(setData)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  if (loading) return <p className="py-20 text-center text-sm text-muted-foreground">불러오는 중…</p>
  if (error) return <p className="py-20 text-center text-sm text-destructive">{error}</p>

  return (
    <div>
      <h1 className="mb-6 text-xl font-bold">내 주문</h1>
      {!data || data.content.length === 0 ? (
        <p className="py-20 text-center text-sm text-muted-foreground">주문 내역이 없습니다.</p>
      ) : (
        <ul className="space-y-3">
          {data.content.map((o) => (
            <li key={o.orderId}>
              <Link to={`/orders/${o.orderId}`}>
                <Card className="flex-row items-center justify-between p-4 hover:shadow-sm">
                  <div>
                    <p className="text-sm font-medium">{o.orderNumber}</p>
                    <p className="text-xs text-muted-foreground">{new Date(o.createdAt).toLocaleString('ko-KR')}</p>
                  </div>
                  <div className="text-right">
                    <span className="rounded bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                      {orderStatusLabel[o.status]}
                    </span>
                    <p className="mt-1 font-bold text-primary">{formatKRW(o.payableAmount)}</p>
                  </div>
                </Card>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
