import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { orderApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import OrderView from '../components/OrderView'
import { useAuth } from '../auth/AuthContext'
import type { Order } from '../api/types'

interface LocationState {
  orderNumber?: string
  ordererPhone?: string
  justOrdered?: boolean
}

export default function GuestOrderLookupPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation() as { state?: LocationState }

  const [orderNumber, setOrderNumber] = useState(location.state?.orderNumber ?? '')
  const [phone, setPhone] = useState(location.state?.ordererPhone ?? '')
  const [order, setOrder] = useState<Order | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [claiming, setClaiming] = useState(false)
  const justOrdered = location.state?.justOrdered ?? false

  const lookup = async (num: string, ph: string) => {
    setLoading(true)
    setError(null)
    try {
      setOrder(await orderApi.guestLookup(num, ph))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '조회에 실패했습니다.')
      setOrder(null)
    } finally {
      setLoading(false)
    }
  }

  // 주문 직후 진입하면 자동 조회한다.
  useEffect(() => {
    if (location.state?.orderNumber && location.state?.ordererPhone) {
      lookup(location.state.orderNumber, location.state.ordererPhone)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    lookup(orderNumber, phone)
  }

  // 로그인 상태면 이 게스트 주문을 내 계정에 연결할 수 있다.
  const claim = async () => {
    if (!order) return
    setClaiming(true)
    setError(null)
    try {
      const claimed = await orderApi.claim(orderNumber, phone)
      navigate(`/orders/${claimed.orderId}`)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '연결에 실패했습니다.')
      setClaiming(false)
    }
  }

  const field = 'w-full rounded-lg border px-3 py-2 text-sm'

  return (
    <div className="mx-auto max-w-xl">
      <h1 className="mb-6 text-xl font-bold">비회원 주문 조회</h1>

      {justOrdered && (
        <div className="mb-6 rounded-xl bg-green-50 p-4 text-center text-sm text-green-700">
          ✅ 주문이 접수되었습니다. 주문번호로 진행 상태를 확인하세요.
        </div>
      )}

      <form onSubmit={submit} className="mb-8 space-y-3 rounded-xl border bg-white p-5">
        <input
          required
          placeholder="주문번호 (예: ORD-20260629-XXXX)"
          value={orderNumber}
          onChange={(e) => setOrderNumber(e.target.value)}
          className={field}
        />
        <input
          required
          placeholder="주문 시 입력한 연락처"
          value={phone}
          onChange={(e) => setPhone(e.target.value)}
          className={field}
        />
        {error && <p className="text-sm text-red-500">{error}</p>}
        <button
          disabled={loading}
          className="w-full rounded-xl bg-indigo-600 py-3 font-semibold text-white hover:bg-indigo-700 disabled:bg-slate-300"
        >
          {loading ? '조회 중…' : '주문 조회'}
        </button>
      </form>

      {order && (
        <>
          <OrderView order={order} />
          {user && (
            <button
              onClick={claim}
              disabled={claiming}
              className="mt-6 w-full rounded-xl border border-indigo-200 py-3 text-sm font-semibold text-indigo-600 hover:bg-indigo-50 disabled:opacity-50"
            >
              {claiming ? '연결 중…' : '이 주문을 내 계정에 연결하기'}
            </button>
          )}
        </>
      )}
    </div>
  )
}
