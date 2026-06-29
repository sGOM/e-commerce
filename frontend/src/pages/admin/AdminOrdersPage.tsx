import { useCallback, useEffect, useState } from 'react'
import { adminApi } from '../../api/endpoints'
import { ApiError, formatKRW } from '../../api/client'
import { orderStatusLabel } from '../../labels'
import type { OrderStatus, OrderSummary, PageResponse } from '../../api/types'

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
      <div className="flex gap-2">
        {(['', 'CREATED', 'PAID', 'CANCELED'] as const).map((s) => (
          <button
            key={s || 'all'}
            onClick={() => {
              setStatus(s)
              setPage(0)
            }}
            className={`rounded-full px-3 py-1 text-sm ${
              status === s ? 'bg-indigo-600 text-white' : 'border bg-white text-slate-600'
            }`}
          >
            {s ? orderStatusLabel[s] : '전체'}
          </button>
        ))}
      </div>

      {error && <p className="text-sm text-red-500">{error}</p>}
      {loading ? (
        <p className="py-10 text-center text-slate-400">불러오는 중…</p>
      ) : !data || data.content.length === 0 ? (
        <p className="py-10 text-center text-slate-400">주문이 없습니다.</p>
      ) : (
        <>
          <ul className="space-y-2">
            {data.content.map((o) => (
              <li
                key={o.orderId}
                className="flex items-center justify-between rounded-xl border bg-white p-4"
              >
                <div>
                  <p className="text-sm font-medium">{o.orderNumber}</p>
                  <p className="text-xs text-slate-400">
                    {new Date(o.createdAt).toLocaleString('ko-KR')}
                  </p>
                </div>
                <div className="flex items-center gap-3">
                  <span className="rounded bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
                    {orderStatusLabel[o.status]}
                  </span>
                  <span className="font-semibold text-indigo-600">
                    {formatKRW(o.payableAmount)}
                  </span>
                  {o.status !== 'CANCELED' && (
                    <button
                      onClick={() => refund(o.orderId)}
                      className="rounded border border-red-200 px-3 py-1 text-xs text-red-500 hover:bg-red-50"
                    >
                      환불
                    </button>
                  )}
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
                    i === page ? 'bg-indigo-600 text-white' : 'border bg-white text-slate-600'
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
