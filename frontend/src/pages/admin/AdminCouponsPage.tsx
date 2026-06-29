import { useState } from 'react'
import { adminApi } from '../../api/endpoints'
import { ApiError } from '../../api/client'
import type { Category, Coupon, DiscountType } from '../../api/types'

export default function AdminCouponsPage() {
  return (
    <div className="grid gap-6 md:grid-cols-2">
      <CouponForm />
      <CategoryForm />
    </div>
  )
}

function CouponForm() {
  const [name, setName] = useState('')
  const [discountType, setDiscountType] = useState<DiscountType>('RATE')
  const [discountValue, setDiscountValue] = useState(10)
  const [minOrderAmount, setMinOrderAmount] = useState(0)
  const [maxDiscountAmount, setMaxDiscountAmount] = useState('')
  const [validFrom, setValidFrom] = useState('')
  const [validUntil, setValidUntil] = useState('')
  const [issueTo, setIssueTo] = useState('')
  const [created, setCreated] = useState<Coupon | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    setCreated(null)
    try {
      const issueToUserIds = issueTo
        .split(',')
        .map((s) => Number(s.trim()))
        .filter((n) => Number.isFinite(n) && n > 0)
      const coupon = await adminApi.createCoupon({
        name,
        discountType,
        discountValue,
        minOrderAmount,
        maxDiscountAmount: maxDiscountAmount ? Number(maxDiscountAmount) : null,
        validFrom: new Date(validFrom).toISOString(),
        validUntil: new Date(validUntil).toISOString(),
        issueToUserIds,
      })
      setCreated(coupon)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '발행 실패')
    } finally {
      setSubmitting(false)
    }
  }

  const field = 'w-full rounded-lg border px-3 py-2 text-sm'

  return (
    <form onSubmit={submit} className="space-y-3 rounded-xl border bg-white p-5">
      <h2 className="font-bold">쿠폰 발행</h2>
      <input required placeholder="쿠폰명" value={name} onChange={(e) => setName(e.target.value)} className={field} />
      <div className="flex gap-2">
        <select value={discountType} onChange={(e) => setDiscountType(e.target.value as DiscountType)} className={field}>
          <option value="RATE">정률(%)</option>
          <option value="FIXED">정액(원)</option>
        </select>
        <input required type="number" min={0} value={discountValue} onChange={(e) => setDiscountValue(Number(e.target.value))} className={field} />
      </div>
      <div className="flex gap-2">
        <input type="number" min={0} placeholder="최소주문금액" value={minOrderAmount} onChange={(e) => setMinOrderAmount(Number(e.target.value))} className={field} />
        <input type="number" min={0} placeholder="최대할인(정률, 선택)" value={maxDiscountAmount} onChange={(e) => setMaxDiscountAmount(e.target.value)} className={field} />
      </div>
      <label className="block text-xs text-slate-500">유효 시작</label>
      <input required type="datetime-local" value={validFrom} onChange={(e) => setValidFrom(e.target.value)} className={field} />
      <label className="block text-xs text-slate-500">유효 종료</label>
      <input required type="datetime-local" value={validUntil} onChange={(e) => setValidUntil(e.target.value)} className={field} />
      <input placeholder="발급 대상 userId (쉼표구분, 선택)" value={issueTo} onChange={(e) => setIssueTo(e.target.value)} className={field} />
      {error && <p className="text-sm text-red-500">{error}</p>}
      {created && (
        <p className="text-sm text-green-600">
          발행 완료: {created.name} ({created.issuedCount}명 발급)
        </p>
      )}
      <button disabled={submitting} className="w-full rounded-xl bg-indigo-600 py-2.5 font-semibold text-white hover:bg-indigo-700 disabled:bg-slate-300">
        {submitting ? '발행 중…' : '쿠폰 발행'}
      </button>
    </form>
  )
}

function CategoryForm() {
  const [name, setName] = useState('')
  const [parentId, setParentId] = useState('')
  const [created, setCreated] = useState<Category | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    setCreated(null)
    try {
      const cat = await adminApi.createCategory(name, parentId ? Number(parentId) : null)
      setCreated(cat)
      setName('')
      setParentId('')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '등록 실패')
    } finally {
      setSubmitting(false)
    }
  }

  const field = 'w-full rounded-lg border px-3 py-2 text-sm'

  return (
    <form onSubmit={submit} className="space-y-3 rounded-xl border bg-white p-5">
      <h2 className="font-bold">카테고리 등록</h2>
      <input required placeholder="카테고리명" value={name} onChange={(e) => setName(e.target.value)} className={field} />
      <input type="number" min={1} placeholder="상위 카테고리 id (선택)" value={parentId} onChange={(e) => setParentId(e.target.value)} className={field} />
      {error && <p className="text-sm text-red-500">{error}</p>}
      {created && (
        <p className="text-sm text-green-600">
          등록 완료: #{created.categoryId} {created.name}
        </p>
      )}
      <button disabled={submitting} className="w-full rounded-xl bg-indigo-600 py-2.5 font-semibold text-white hover:bg-indigo-700 disabled:bg-slate-300">
        {submitting ? '등록 중…' : '카테고리 등록'}
      </button>
    </form>
  )
}
