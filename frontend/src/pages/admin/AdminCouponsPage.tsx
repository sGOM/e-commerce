import { useState } from 'react'
import { adminApi } from '../../api/endpoints'
import { ApiError } from '../../api/client'
import type { Category, Coupon, DiscountType } from '../../api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent } from '@/components/ui/card'

export default function AdminCouponsPage() {
  return (
    <div className="grid gap-6 md:grid-cols-2">
      <CouponForm />
      <CategoryForm />
    </div>
  )
}

const selectClass =
  'h-9 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50'

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

  return (
    <Card>
      <CardContent>
        <form onSubmit={submit} className="space-y-3">
          <h2 className="font-bold">쿠폰 발행</h2>
          <Input required placeholder="쿠폰명" aria-label="쿠폰명" value={name} onChange={(e) => setName(e.target.value)} />
          <div className="flex gap-2">
            <select
              value={discountType}
              onChange={(e) => setDiscountType(e.target.value as DiscountType)}
              aria-label="할인 유형"
              className={selectClass}
            >
              <option value="RATE">정률(%)</option>
              <option value="FIXED">정액(원)</option>
            </select>
            <Input required type="number" min={0} aria-label="할인값" value={discountValue} onChange={(e) => setDiscountValue(Number(e.target.value))} />
          </div>
          <div className="flex gap-2">
            <Input type="number" min={0} placeholder="최소주문금액" aria-label="최소주문금액" value={minOrderAmount} onChange={(e) => setMinOrderAmount(Number(e.target.value))} />
            <Input type="number" min={0} placeholder="최대할인(정률, 선택)" aria-label="최대할인" value={maxDiscountAmount} onChange={(e) => setMaxDiscountAmount(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="coupon-valid-from" className="text-xs text-muted-foreground">
              유효 시작
            </Label>
            <Input id="coupon-valid-from" required type="datetime-local" value={validFrom} onChange={(e) => setValidFrom(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="coupon-valid-until" className="text-xs text-muted-foreground">
              유효 종료
            </Label>
            <Input id="coupon-valid-until" required type="datetime-local" value={validUntil} onChange={(e) => setValidUntil(e.target.value)} />
          </div>
          <Input placeholder="발급 대상 userId (쉼표구분, 선택)" aria-label="발급 대상 userId" value={issueTo} onChange={(e) => setIssueTo(e.target.value)} />
          {error && (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          )}
          {created && (
            <p className="text-sm text-success">
              발행 완료: {created.name} ({created.issuedCount}명 발급)
            </p>
          )}
          <Button type="submit" disabled={submitting} className="w-full">
            {submitting ? '발행 중…' : '쿠폰 발행'}
          </Button>
        </form>
      </CardContent>
    </Card>
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

  return (
    <Card>
      <CardContent>
        <form onSubmit={submit} className="space-y-3">
          <h2 className="font-bold">카테고리 등록</h2>
          <Input required placeholder="카테고리명" aria-label="카테고리명" value={name} onChange={(e) => setName(e.target.value)} />
          <Input type="number" min={1} placeholder="상위 카테고리 id (선택)" aria-label="상위 카테고리 id" value={parentId} onChange={(e) => setParentId(e.target.value)} />
          {error && (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          )}
          {created && (
            <p className="text-sm text-success">
              등록 완료: #{created.categoryId} {created.name}
            </p>
          )}
          <Button type="submit" disabled={submitting} className="w-full">
            {submitting ? '등록 중…' : '카테고리 등록'}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
