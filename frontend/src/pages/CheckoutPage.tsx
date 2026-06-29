import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { cartApi, meApi, orderApi, type ShippingAddressBody } from '../api/endpoints'
import { ApiError, formatKRW } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { clearGuestCart, readGuestCart } from '../cart/guestCart'
import type { Cart, IssuedCoupon } from '../api/types'

/** 쿠폰 할인 미리보기(서버 calculateDiscount 와 동일 규칙). 최종 금액은 서버가 재계산한다. */
function couponDiscount(coupon: IssuedCoupon, amount: number): number {
  if (amount < coupon.minOrderAmount) return 0
  const raw =
    coupon.discountType === 'RATE'
      ? Math.floor((amount * coupon.discountValue) / 100)
      : coupon.discountValue
  const capped = coupon.maxDiscountAmount ? Math.min(raw, coupon.maxDiscountAmount) : raw
  return Math.min(capped, amount)
}

export default function CheckoutPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const isGuest = !user

  const [cart, setCart] = useState<Cart | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // 회원 전용: 보유 쿠폰/포인트
  const [coupons, setCoupons] = useState<IssuedCoupon[]>([])
  const [pointBalance, setPointBalance] = useState(0)
  const [couponId, setCouponId] = useState<number | null>(null)
  const [pointInput, setPointInput] = useState(0)

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
    const loadCart = async () => {
      try {
        if (isGuest) {
          const lines = readGuestCart()
          if (lines.length === 0) return navigate('/cart')
          setCart(await cartApi.guestPreview(lines))
        } else {
          const c = await cartApi.get()
          if (c.items.length === 0) return navigate('/cart')
          setCart(c)
          // 사용 가능한 쿠폰(미사용)과 포인트 잔액 로드
          const [cps, pts] = await Promise.all([meApi.coupons(), meApi.points()])
          setCoupons(cps.filter((c) => !c.used))
          setPointBalance(pts.balance)
        }
      } catch (e) {
        setError((e as Error).message)
      }
    }
    loadCart()
  }, [isGuest, navigate])

  // 할인/포인트 미리보기 계산(서버가 최종 재계산)
  const subtotal = cart?.totalPrice ?? 0
  const selectedCoupon = coupons.find((c) => c.issuedCouponId === couponId) ?? null
  const discount = selectedCoupon ? couponDiscount(selectedCoupon, subtotal) : 0
  const maxPoint = Math.max(0, Math.min(pointBalance, subtotal - discount))
  const pointToUse = Math.max(0, Math.min(pointInput, maxPoint))
  const payable = subtotal - discount - pointToUse

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm({ ...form, [k]: e.target.value })

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    const shippingAddress: ShippingAddressBody = {
      receiverName: form.receiverName,
      receiverPhone: form.receiverPhone,
      zipcode: form.zipcode,
      address1: form.address1,
      address2: form.address2 || undefined,
    }
    try {
      if (isGuest) {
        // 비회원: 주문 생성(CREATED)까지. 결제는 회원 전용이므로 주문번호로 조회 안내.
        const order = await orderApi.createGuest({
          ordererName: form.ordererName,
          ordererPhone: form.ordererPhone,
          ordererEmail: form.ordererEmail,
          shippingAddress,
          items: readGuestCart(),
        })
        clearGuestCart()
        navigate('/orders/lookup', {
          state: {
            orderNumber: order.orderNumber,
            ordererPhone: form.ordererPhone,
            justOrdered: true,
          },
        })
      } else {
        const order = await orderApi.create({
          ordererName: form.ordererName,
          ordererPhone: form.ordererPhone,
          ordererEmail: form.ordererEmail,
          shippingAddress,
          issuedCouponId: couponId,
          usePoint: pointToUse,
        })
        await orderApi.pay(order.orderId) // Mock PG 즉시 결제
        navigate(`/orders/${order.orderId}`, { state: { justPaid: true } })
      }
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
          <h2 className="mb-3 font-bold">
            주문자 정보 {isGuest && <span className="text-sm font-normal text-slate-400">(비회원)</span>}
          </h2>
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

      <div className="h-fit space-y-4 rounded-xl border bg-white p-5">
        <h2 className="font-bold">결제 요약</h2>

        {!isGuest && cart && (
          <div className="space-y-3 border-b pb-4">
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-500">쿠폰</label>
              <select
                value={couponId ?? ''}
                onChange={(e) => setCouponId(e.target.value ? Number(e.target.value) : null)}
                className="w-full rounded-lg border px-3 py-2 text-sm"
              >
                <option value="">선택 안 함</option>
                {coupons.map((c) => {
                  const usable = subtotal >= c.minOrderAmount
                  return (
                    <option key={c.issuedCouponId} value={c.issuedCouponId} disabled={!usable}>
                      {c.name} (
                      {c.discountType === 'RATE'
                        ? `${c.discountValue}%`
                        : formatKRW(c.discountValue)}
                      )
                      {!usable ? ` · ${formatKRW(c.minOrderAmount)} 이상` : ''}
                    </option>
                  )
                })}
              </select>
              {coupons.length === 0 && (
                <p className="mt-1 text-xs text-slate-400">보유한 쿠폰이 없습니다.</p>
              )}
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-500">
                포인트 사용 (보유 {formatKRW(pointBalance)})
              </label>
              <div className="flex gap-2">
                <input
                  type="number"
                  min={0}
                  max={maxPoint}
                  value={pointInput}
                  onChange={(e) => setPointInput(Math.max(0, Number(e.target.value)))}
                  className="w-full rounded-lg border px-3 py-2 text-sm"
                />
                <button
                  type="button"
                  onClick={() => setPointInput(maxPoint)}
                  className="shrink-0 rounded-lg border px-3 text-xs text-slate-600 hover:bg-slate-50"
                >
                  전액
                </button>
              </div>
            </div>
          </div>
        )}

        {cart && (
          <div className="space-y-1 text-sm text-slate-600">
            <div className="flex justify-between">
              <span>상품 {cart.totalQuantity}개</span>
              <span>{formatKRW(subtotal)}</span>
            </div>
            {discount > 0 && (
              <div className="flex justify-between text-red-500">
                <span>쿠폰 할인</span>
                <span>-{formatKRW(discount)}</span>
              </div>
            )}
            {pointToUse > 0 && (
              <div className="flex justify-between text-red-500">
                <span>포인트 사용</span>
                <span>-{formatKRW(pointToUse)}</span>
              </div>
            )}
            <div className="flex justify-between border-t pt-2 font-bold text-slate-900">
              <span>{isGuest ? '주문 금액' : '결제 금액'}</span>
              <span className="text-indigo-600">{formatKRW(payable)}</span>
            </div>
          </div>
        )}
        {error && <p className="text-sm text-red-500">{error}</p>}
        <button
          type="submit"
          disabled={submitting || !cart}
          className="mt-5 w-full rounded-xl bg-indigo-600 py-3 font-semibold text-white hover:bg-indigo-700 disabled:bg-slate-300"
        >
          {submitting ? '처리 중…' : isGuest ? '비회원 주문하기' : '결제하기 (Mock PG)'}
        </button>
        <p className="mt-2 text-center text-xs text-slate-400">
          {isGuest
            ? '주문 후 주문번호와 연락처로 조회할 수 있습니다.'
            : '데모 결제는 외부 PG 없이 즉시 승인됩니다.'}
        </p>
      </div>
    </form>
  )
}
