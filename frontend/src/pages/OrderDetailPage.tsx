import { useEffect, useState } from 'react'
import { useLocation, useParams } from 'react-router-dom'
import { orderApi } from '../api/endpoints'
import { ApiError, formatKRW } from '../api/client'
import { orderStatusLabel, subOrderStatusLabel } from '../labels'
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

      <div className="rounded-xl border bg-white p-5">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-lg font-bold">{order.orderNumber}</h1>
            <p className="text-xs text-slate-400">
              {new Date(order.createdAt).toLocaleString('ko-KR')}
            </p>
          </div>
          <span className="rounded bg-slate-100 px-2 py-1 text-xs text-slate-600">
            {orderStatusLabel[order.status]}
          </span>
        </div>
      </div>

      {order.subOrders.map((sub) => (
        <div key={sub.subOrderId} className="rounded-xl border bg-white p-5">
          <div className="mb-3 flex items-center justify-between">
            <p className="text-sm font-semibold">{sub.storeName}</p>
            <span className="rounded bg-indigo-50 px-2 py-0.5 text-xs text-indigo-600">
              {subOrderStatusLabel[sub.status]}
            </span>
          </div>
          <ul className="space-y-2">
            {sub.items.map((item, idx) => (
              <li key={idx} className="flex justify-between text-sm">
                <span>
                  {item.productName}{' '}
                  <span className="text-slate-400">
                    {item.optionName} · {item.quantity}개
                  </span>
                </span>
                <span>{formatKRW(item.lineTotal)}</span>
              </li>
            ))}
          </ul>
        </div>
      ))}

      <div className="rounded-xl border bg-white p-5">
        <h2 className="mb-3 font-bold">배송지</h2>
        <p className="text-sm">
          {order.shippingAddress.receiverName} · {order.shippingAddress.receiverPhone}
        </p>
        <p className="text-sm text-slate-600">
          ({order.shippingAddress.zipcode}) {order.shippingAddress.address1}{' '}
          {order.shippingAddress.address2}
        </p>
      </div>

      <div className="rounded-xl border bg-white p-5">
        <h2 className="mb-3 font-bold">결제 정보</h2>
        <div className="space-y-1 text-sm text-slate-600">
          <div className="flex justify-between">
            <span>상품 합계</span>
            <span>{formatKRW(order.totalAmount)}</span>
          </div>
          {order.discountAmount > 0 && (
            <div className="flex justify-between text-red-500">
              <span>쿠폰 할인</span>
              <span>-{formatKRW(order.discountAmount)}</span>
            </div>
          )}
          {order.pointUsed > 0 && (
            <div className="flex justify-between text-red-500">
              <span>포인트 사용</span>
              <span>-{formatKRW(order.pointUsed)}</span>
            </div>
          )}
          <div className="flex justify-between border-t pt-2 font-bold text-slate-900">
            <span>최종 결제 금액</span>
            <span className="text-indigo-600">{formatKRW(order.payableAmount)}</span>
          </div>
        </div>
      </div>

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
