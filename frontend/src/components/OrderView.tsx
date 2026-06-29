import { formatKRW } from '../api/client'
import { orderStatusLabel, subOrderStatusLabel } from '../labels'
import type { Order } from '../api/types'

/** 주문 상세 표시(주문 헤더·판매자별 하위주문·배송지·결제정보). 회원 상세/게스트 조회 공용. */
export default function OrderView({ order }: { order: Order }) {
  return (
    <div className="space-y-6">
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
            <span>{order.status === 'PAID' ? '최종 결제 금액' : '주문 금액'}</span>
            <span className="text-indigo-600">{formatKRW(order.payableAmount)}</span>
          </div>
        </div>
      </div>
    </div>
  )
}
