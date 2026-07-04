import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  cartApi,
  deliverySlotApi,
  meApi,
  membershipApi,
  orderApi,
  type DeliverySlotSelectionBody,
  type ShippingAddressBody,
} from '../api/endpoints'
import { ApiError, formatKRW } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { clearGuestCart, readGuestCart } from '../cart/guestCart'
import { deliverySlotTypeLabel } from '../labels'
import type { Cart, CartItem, DeliverySlot, IssuedCoupon, Membership } from '../api/types'

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

/** Date -> "YYYY-MM-DD" (로컬 타임존 기준, UTC 변환으로 인한 날짜 밀림 방지). */
function toDateStr(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

interface SellerGroup {
  sellerId: number
  storeName: string
  items: CartItem[]
  dawnEligible: boolean
}

/** 장바구니 항목을 판매자(SubOrder) 단위로 묶는다 — 배송 슬롯은 SubOrder 단위로 선택한다(AC6). */
function groupBySeller(items: CartItem[]): SellerGroup[] {
  const map = new Map<number, SellerGroup>()
  for (const item of items) {
    const group = map.get(item.sellerId) ?? {
      sellerId: item.sellerId,
      storeName: item.storeName,
      items: [],
      dawnEligible: false,
    }
    group.items.push(item)
    if (item.dawnDeliveryEligible) group.dawnEligible = true
    map.set(item.sellerId, group)
  }
  return [...map.values()]
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
  // 멤버십 혜택 인라인 안내(무료배송/포인트 우대) — 미가입/조회 실패 시 조용히 무시한다.
  const [membership, setMembership] = useState<Membership | null>(null)

  // 배송 슬롯(새벽배송/시간대 지정)
  const [slotDate, setSlotDate] = useState(() => toDateStr(new Date(Date.now() + 86400000))) // 기본값: 내일
  const [slots, setSlots] = useState<DeliverySlot[]>([])
  const [slotsLoading, setSlotsLoading] = useState(false)
  const [slotsError, setSlotsError] = useState<string | null>(null)
  // sellerId -> 선택한 deliverySlotId (null = 일반배송)
  const [selectedSlots, setSelectedSlots] = useState<Record<number, number | null>>({})

  // 선물하기(회원 전용, `docs/planning/gift-order.md`) — 켜면 배송지 입력을 생략하고 수령자가
  // 공유 링크로 나중에 입력한다. 배송 슬롯은 배송지 기반 검증이 필요해 선물 주문과 동시에 쓸 수 없다.
  const [isGift, setIsGift] = useState(false)
  const [giftMessage, setGiftMessage] = useState('')

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
          // 미가입(404)은 정상 상태이므로 조용히 무시한다.
          membershipApi.my().then(setMembership).catch(() => setMembership(null))
        }
      } catch (e) {
        setError((e as Error).message)
      }
    }
    loadCart()
  }, [isGuest, navigate])

  const sellerGroups = useMemo(() => groupBySeller(cart?.items ?? []), [cart])
  const eligibleGroups = useMemo(
    () => (isGift ? [] : sellerGroups.filter((g) => g.dawnEligible)),
    [sellerGroups, isGift],
  )

  // 날짜(선택 탭) 후보: 오늘 포함 앞으로 3일
  const dateOptions = useMemo(() => {
    return Array.from({ length: 3 }, (_, i) => {
      const d = new Date()
      d.setDate(d.getDate() + i)
      const label = i === 0 ? '오늘' : i === 1 ? '내일' : `${d.getMonth() + 1}/${d.getDate()}`
      return { value: toDateStr(d), label: `${label} (${d.getMonth() + 1}/${d.getDate()})` }
    })
  }, [])

  // 배송지 우편번호/날짜가 바뀌면 가용 슬롯을 다시 조회한다(새벽배송 대상 SubOrder 가 있을 때만).
  useEffect(() => {
    if (eligibleGroups.length === 0 || !form.zipcode || form.zipcode.trim().length < 3) {
      setSlots([])
      return
    }
    let active = true
    setSlotsLoading(true)
    setSlotsError(null)
    const timer = setTimeout(() => {
      deliverySlotApi
        .list({ postalCode: form.zipcode.trim(), date: slotDate })
        .then((res) => {
          if (active) setSlots(res)
        })
        .catch((e) => {
          if (active) setSlotsError(e instanceof ApiError ? e.message : '배송 슬롯을 불러오지 못했습니다.')
        })
        .finally(() => {
          if (active) setSlotsLoading(false)
        })
    }, 400)
    return () => {
      active = false
      clearTimeout(timer)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [eligibleGroups.length, form.zipcode, slotDate])

  // 날짜를 바꾸면 이전 날짜의 슬롯 id 는 더 이상 유효하지 않으므로 선택을 초기화한다.
  useEffect(() => {
    setSelectedSlots({})
  }, [slotDate])

  // 선물하기를 켜면 배송 슬롯 선택은 지원되지 않으므로(GIFT-005) 기존 선택을 비운다.
  useEffect(() => {
    if (isGift) setSelectedSlots({})
  }, [isGift])

  const selectSlot = (sellerId: number, slotId: number | null) =>
    setSelectedSlots((prev) => ({ ...prev, [sellerId]: slotId }))

  // 할인/포인트/배송비 미리보기 계산(서버가 최종 재계산)
  const subtotal = cart?.totalPrice ?? 0
  const selectedCoupon = coupons.find((c) => c.issuedCouponId === couponId) ?? null
  const discount = selectedCoupon ? couponDiscount(selectedCoupon, subtotal) : 0
  const maxPoint = Math.max(0, Math.min(pointBalance, subtotal - discount))
  const pointToUse = Math.max(0, Math.min(pointInput, maxPoint))
  const deliveryFeePreview = eligibleGroups.reduce((sum, g) => {
    const slotId = selectedSlots[g.sellerId]
    if (slotId == null) return sum
    const slot = slots.find((s) => s.id === slotId)
    return sum + (slot?.extraFee ?? 0)
  }, 0)
  const payable = subtotal - discount - pointToUse + deliveryFeePreview

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm({ ...form, [k]: e.target.value })

  const buildDeliverySlotSelections = (): DeliverySlotSelectionBody[] =>
    Object.entries(selectedSlots)
      .filter(([, slotId]) => slotId != null)
      .map(([sellerId, slotId]) => ({ sellerId: Number(sellerId), deliverySlotId: slotId as number }))

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
    const deliverySlotSelections = buildDeliverySlotSelections()
    try {
      if (isGuest) {
        // 비회원: 주문 생성(CREATED)까지. 결제는 회원 전용이므로 주문번호로 조회 안내.
        // 게스트 주문은 선물하기를 지원하지 않는다(구매자는 회원 전용, `docs/planning/gift-order.md` §4).
        const order = await orderApi.createGuest({
          ordererName: form.ordererName,
          ordererPhone: form.ordererPhone,
          ordererEmail: form.ordererEmail,
          shippingAddress,
          items: readGuestCart(),
          deliverySlotSelections,
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
          // 선물 주문은 배송지를 생략(수령자가 나중에 입력)하고 배송 슬롯 선택도 함께 쓸 수 없다.
          shippingAddress: isGift ? null : shippingAddress,
          issuedCouponId: couponId,
          usePoint: pointToUse,
          deliverySlotSelections: isGift ? [] : deliverySlotSelections,
          isGift,
          giftMessage: isGift ? giftMessage.trim() || null : null,
        })
        await orderApi.pay(order.orderId) // Mock PG 즉시 결제
        navigate(`/orders/${order.orderId}`, {
          state: { justPaid: true, giftClaimToken: order.giftClaimToken },
        })
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

        {!isGuest && (
          <section className="rounded-xl border bg-white p-5">
            <label className="flex cursor-pointer items-center justify-between gap-3">
              <span>
                <span className="font-bold">🎁 선물하기</span>
                <span className="mt-0.5 block text-xs text-slate-400">
                  배송지 없이 결제하고, 수령자가 링크로 직접 배송지를 입력하게 할 수 있어요. 새벽배송
                  슬롯은 선물 주문과 함께 선택할 수 없습니다.
                </span>
              </span>
              <input
                type="checkbox"
                className="size-5 shrink-0"
                checked={isGift}
                onChange={(e) => setIsGift(e.target.checked)}
              />
            </label>
            {isGift && (
              <div className="mt-4 border-t pt-4">
                <label className="mb-1 block text-xs font-medium text-slate-500">
                  선물 메시지 (선택)
                </label>
                <textarea
                  maxLength={1000}
                  placeholder="받는 분께 전할 메시지를 남겨보세요."
                  value={giftMessage}
                  onChange={(e) => setGiftMessage(e.target.value)}
                  className={`${field} min-h-24 resize-none`}
                />
              </div>
            )}
          </section>
        )}

        {isGift ? (
          <section className="rounded-xl border bg-white p-5">
            <h2 className="mb-2 font-bold">배송지</h2>
            <p className="rounded-lg bg-pink-50 p-3 text-sm text-pink-700">
              선물 주문은 배송지 입력을 생략합니다. 결제 완료 후 발급되는 공유 링크를 수령자에게
              전달하면, 수령자가 직접 배송지를 입력해 배송이 시작됩니다.
            </p>
          </section>
        ) : (
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
        )}

        {eligibleGroups.length > 0 && (
          <section className="rounded-xl border bg-white p-5">
            <h2 className="mb-1 font-bold">배송 슬롯 선택 (새벽배송)</h2>
            <p className="mb-3 text-xs text-slate-400">
              새벽배송 가능 상품이 포함된 판매자별로 원하는 배송 시간대를 선택하세요. 선택하지 않으면
              일반배송으로 진행됩니다.
            </p>

            {!form.zipcode.trim() ? (
              <p className="rounded-lg bg-slate-50 py-6 text-center text-sm text-slate-400">
                배송지 우편번호를 입력하면 예약 가능한 슬롯을 보여드려요.
              </p>
            ) : (
              <>
                <div className="mb-4 flex gap-2">
                  {dateOptions.map((d) => (
                    <button
                      key={d.value}
                      type="button"
                      onClick={() => setSlotDate(d.value)}
                      className={`rounded-full px-3 py-1 text-sm ${
                        slotDate === d.value ? 'bg-indigo-600 text-white' : 'border bg-white text-slate-600'
                      }`}
                    >
                      {d.label}
                    </button>
                  ))}
                </div>

                {slotsError && <p className="mb-3 text-sm text-red-500">{slotsError}</p>}
                {slotsLoading ? (
                  <p className="py-6 text-center text-sm text-slate-400">슬롯 조회 중…</p>
                ) : (
                  <div className="space-y-5">
                    {eligibleGroups.map((group) => (
                      <SellerSlotPicker
                        key={group.sellerId}
                        group={group}
                        slots={slots}
                        selectedSlotId={selectedSlots[group.sellerId] ?? null}
                        onSelect={(slotId) => selectSlot(group.sellerId, slotId)}
                      />
                    ))}
                  </div>
                )}
              </>
            )}
          </section>
        )}
      </div>

      <div className="h-fit space-y-4 rounded-xl border bg-white p-5">
        <h2 className="font-bold">결제 요약</h2>

        {membership?.benefitActive && (
          <p className="rounded-lg bg-indigo-50 p-2.5 text-xs text-indigo-600">
            멤버십 혜택 적용 중
            {membership.benefits.freeShipping && ' · 무료배송 적용됨'}
            {membership.benefits.pointEarnMultiplierBp > 10_000 &&
              ` · 포인트 ${(membership.benefits.pointEarnMultiplierBp / 10_000).toFixed(1).replace(/\.0$/, '')}배 적립 예정`}
          </p>
        )}

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
            {deliveryFeePreview > 0 && (
              <div className="flex justify-between">
                <span>배송비(새벽배송)</span>
                <span>+{formatKRW(deliveryFeePreview)}</span>
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

/** 판매자(SubOrder) 한 곳의 배송 슬롯 선택 UI. 마감/정원초과 슬롯은 선택 불가로 비활성화한다(AC4/AC8). */
function SellerSlotPicker({
  group,
  slots,
  selectedSlotId,
  onSelect,
}: {
  group: SellerGroup
  slots: DeliverySlot[]
  selectedSlotId: number | null
  onSelect: (slotId: number | null) => void
}) {
  const name = `delivery-slot-${group.sellerId}`

  if (slots.length === 0) {
    return (
      <div>
        <p className="mb-2 text-sm font-semibold">{group.storeName}</p>
        <p className="rounded-lg bg-slate-50 py-4 text-center text-xs text-slate-400">
          선택한 날짜에 예약 가능한 슬롯이 없습니다. 일반배송으로 진행됩니다.
        </p>
      </div>
    )
  }

  return (
    <div>
      <p className="mb-2 text-sm font-semibold">{group.storeName}</p>
      <div className="space-y-2">
        <label
          className={`flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-sm ${
            selectedSlotId === null ? 'border-indigo-500 bg-indigo-50' : 'border-slate-200'
          }`}
        >
          <input
            type="radio"
            name={name}
            checked={selectedSlotId === null}
            onChange={() => onSelect(null)}
          />
          일반배송 (슬롯 선택 안 함)
        </label>

        {slots.map((slot) => {
          const disabled = slot.expired || slot.remaining <= 0
          const cutoffLabel = new Date(slot.cutoffAt).toLocaleString('ko-KR', {
            month: 'numeric',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
          })
          const soon =
            !disabled && new Date(slot.cutoffAt).getTime() - Date.now() < 2 * 60 * 60 * 1000
          return (
            <label
              key={slot.id}
              className={`flex items-center gap-2 rounded-lg border px-3 py-2 text-sm ${
                disabled
                  ? 'cursor-not-allowed border-slate-100 bg-slate-50 text-slate-300'
                  : selectedSlotId === slot.id
                    ? 'cursor-pointer border-indigo-500 bg-indigo-50'
                    : 'cursor-pointer border-slate-200'
              }`}
            >
              <input
                type="radio"
                name={name}
                disabled={disabled}
                checked={selectedSlotId === slot.id}
                onChange={() => onSelect(slot.id)}
              />
              <span className="flex-1">
                <span className="font-medium">
                  {deliverySlotTypeLabel[slot.type]} · {slot.startTime.slice(0, 5)}~
                  {slot.endTime.slice(0, 5)}
                </span>
                <span className="ml-1 text-xs text-slate-400">
                  {slot.extraFee > 0 ? `+${formatKRW(slot.extraFee)}` : '배송비 무료'} · 잔여{' '}
                  {slot.remaining}/{slot.capacity}
                </span>
                <span className="block text-xs text-slate-400">
                  {disabled
                    ? slot.expired
                      ? '주문 마감되었습니다'
                      : '정원이 마감되었습니다'
                    : `${cutoffLabel}까지 주문 시 예약 가능${soon ? ' · 마감임박' : ''}`}
                </span>
              </span>
            </label>
          )
        })}
      </div>
    </div>
  )
}
