import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { cartApi, orderApi, type CreateOrderBody } from '../api/endpoints'
import { ApiError, formatKRW } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type { Cart } from '../api/types'

export default function CheckoutPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const [cart, setCart] = useState<Cart | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [form, setForm] = useState({
    ordererName: user?.name ?? '',
    ordererPhone: '',
    ordererEmail: user?.email ?? '',
    receiverName: '',
    receiverPhone: '',
    zipcode: '',
    address1: '',
    address2: '',
  })

  useEffect(() => {
    cartApi
      .get()
      .then((c) => {
        if (c.items.length === 0) navigate('/cart')
        else setCart(c)
      })
      .catch((e) => setError(e.message))
  }, [navigate])

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm({ ...form, [k]: e.target.value })

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    const body: CreateOrderBody = {
      ordererName: form.ordererName,
      ordererPhone: form.ordererPhone,
      ordererEmail: form.ordererEmail,
      shippingAddress: {
        receiverName: form.receiverName,
        receiverPhone: form.receiverPhone,
        zipcode: form.zipcode,
        address1: form.address1,
        address2: form.address2 || undefined,
      },
    }
    try {
      const order = await orderApi.create(body)
      // Mock PG 즉시 결제 → 결제 완료 후 주문 상세로 이동
      await orderApi.pay(order.orderId)
      navigate(`/orders/${order.orderId}`, { state: { justPaid: true } })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '주문에 실패했습니다.')
      setSubmitting(false)
    }
  }

  const field = 'w-full rounded-lg border px-3 py-2 text-sm'

  return (
    <form onSubmit={submit} className="grid gap-6 md:grid-cols-3">
      <div className="space-y-6 md:col-span-2">
        <section className="rounded-xl border bg-white p-5">
          <h2 className="mb-3 font-bold">주문자 정보</h2>
          <div className="grid gap-3 sm:grid-cols-2">
            <input required placeholder="이름" value={form.ordererName} onChange={set('ordererName')} className={field} />
            <input required placeholder="연락처" value={form.ordererPhone} onChange={set('ordererPhone')} className={field} />
            <input required type="email" placeholder="이메일" value={form.ordererEmail} onChange={set('ordererEmail')} className={`${field} sm:col-span-2`} />
          </div>
        </section>

        <section className="rounded-xl border bg-white p-5">
          <h2 className="mb-3 font-bold">배송지</h2>
          <div className="grid gap-3 sm:grid-cols-2">
            <input required placeholder="받는 분" value={form.receiverName} onChange={set('receiverName')} className={field} />
            <input required placeholder="받는 분 연락처" value={form.receiverPhone} onChange={set('receiverPhone')} className={field} />
            <input required placeholder="우편번호" value={form.zipcode} onChange={set('zipcode')} className={field} />
            <input required placeholder="기본 주소" value={form.address1} onChange={set('address1')} className={field} />
            <input placeholder="상세 주소 (선택)" value={form.address2} onChange={set('address2')} className={`${field} sm:col-span-2`} />
          </div>
        </section>
      </div>

      <div className="h-fit rounded-xl border bg-white p-5">
        <h2 className="mb-3 font-bold">결제 요약</h2>
        {cart && (
          <>
            <div className="flex justify-between text-sm text-slate-600">
              <span>상품 {cart.totalQuantity}개</span>
              <span>{formatKRW(cart.totalPrice)}</span>
            </div>
            <div className="mt-2 flex justify-between font-bold">
              <span>결제 금액</span>
              <span className="text-indigo-600">{formatKRW(cart.totalPrice)}</span>
            </div>
          </>
        )}
        {error && <p className="mt-3 text-sm text-red-500">{error}</p>}
        <button
          type="submit"
          disabled={submitting || !cart}
          className="mt-5 w-full rounded-xl bg-indigo-600 py-3 font-semibold text-white hover:bg-indigo-700 disabled:bg-slate-300"
        >
          {submitting ? '결제 중…' : '결제하기 (Mock PG)'}
        </button>
        <p className="mt-2 text-center text-xs text-slate-400">
          데모 결제는 외부 PG 없이 즉시 승인됩니다.
        </p>
      </div>
    </form>
  )
}
