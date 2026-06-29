import { useEffect, useState } from 'react'
import { useLocation, useParams } from 'react-router-dom'
import { orderApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import OrderView from '../components/OrderView'
import type { Order } from '../api/types'

export default function OrderDetailPage() {
  const { id } = useParams()
  const orderId = Number(id)
  const location = useLocation() as { state?: { justPaid?: boolean } }

  const [order, setOrder] = useState<Order | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [canceling, setCanceling] = useState(false)

  useEffect(() => {
    orderApi
      .detail(orderId)
      .then(setOrder)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [orderId])

  const cancel = async () => {
    if (!confirm('주문을 취소하시겠습니까?')) return
    setCanceling(true)
    setError(null)
    try {
      setOrder(await orderApi.cancel(orderId))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '취소에 실패했습니다.')
    } finally {
      setCanceling(false)
    }
  }

  if (loading) return <p className="py-20 text-center text-slate-400">불러오는 중…</p>
  if (error && !order) return <p className="py-20 text-center text-red-500">{error}</p>
  if (!order) return null

  const cancelable =
    order.status !== 'CANCELED' &&
    !order.subOrders.some((s) => s.status === 'SHIPPED' || s.status === 'DELIVERED')

  return (
    <div className="space-y-6">
      {location.state?.justPaid && (
        <div className="rounded-xl bg-green-50 p-4 text-center text-sm text-green-700">
          🎉 결제가 완료되었습니다!
        </div>
      )}

      <OrderView order={order} />

      {error && <p className="text-sm text-red-500">{error}</p>}
      {cancelable && (
        <button
          onClick={cancel}
          disabled={canceling}
          className="w-full rounded-xl border border-red-200 py-3 text-sm font-semibold text-red-500 hover:bg-red-50 disabled:opacity-50"
        >
          {canceling ? '취소 중…' : '주문 취소'}
        </button>
      )}
    </div>
  )
}
