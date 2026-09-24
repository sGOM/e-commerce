import { useEffect, useState } from 'react'
import { adminApi, categoryApi } from '../../api/endpoints'
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
          <Input
            required
            placeholder="쿠폰명"
            aria-label="쿠폰명"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
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
            <Input
              required
              type="number"
              min={0}
              aria-label="할인값"
              value={discountValue}
              onChange={(e) => setDiscountValue(Number(e.target.value))}
            />
          </div>
          <div className="flex gap-2">
            <Input
              type="number"
              min={0}
              placeholder="최소주문금액"
              aria-label="최소주문금액"
              value={minOrderAmount}
              onChange={(e) => setMinOrderAmount(Number(e.target.value))}
            />
            <Input
              type="number"
              min={0}
              placeholder="최대할인(정률, 선택)"
              aria-label="최대할인"
              value={maxDiscountAmount}
              onChange={(e) => setMaxDiscountAmount(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="coupon-valid-from" className="text-xs text-muted-foreground">
              유효 시작
            </Label>
            <Input
              id="coupon-valid-from"
              required
              type="datetime-local"
              value={validFrom}
              onChange={(e) => setValidFrom(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="coupon-valid-until" className="text-xs text-muted-foreground">
              유효 종료
            </Label>
            <Input
              id="coupon-valid-until"
              required
              type="datetime-local"
              value={validUntil}
              onChange={(e) => setValidUntil(e.target.value)}
            />
          </div>
          <Input
            placeholder="발급 대상 userId (쉼표구분, 선택)"
            aria-label="발급 대상 userId"
            value={issueTo}
            onChange={(e) => setIssueTo(e.target.value)}
          />
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
  const [categories, setCategories] = useState<Category[]>([])
  const [name, setName] = useState('')
  const [parentId, setParentId] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const load = () =>
    categoryApi
      .list()
      .then(setCategories)
      .catch(() => setError('카테고리를 불러오지 못했습니다.'))
  useEffect(() => {
    load()
  }, [])

  // 등록·수정·삭제 공통: 실패 메시지를 보여주고 성공하면 목록을 다시 읽는다
  const run = async (action: () => Promise<unknown>) => {
    setSubmitting(true)
    setError(null)
    try {
      await action()
      await load()
      return true
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '요청 실패')
      return false
    } finally {
      setSubmitting(false)
    }
  }

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (await run(() => adminApi.createCategory(name, parentId ? Number(parentId) : null))) {
      setName('')
      setParentId('')
    }
  }

  return (
    <Card>
      <CardContent className="space-y-4">
        <form onSubmit={submit} className="space-y-3">
          <h2 className="font-bold">카테고리 관리</h2>
          <Input
            required
            placeholder="카테고리명"
            aria-label="카테고리명"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <select
            aria-label="상위 카테고리"
            value={parentId}
            onChange={(e) => setParentId(e.target.value)}
            className={selectClass}
          >
            <option value="">상위 없음(최상위)</option>
            {categories.map((c) => (
              <option key={c.categoryId} value={c.categoryId}>
                {c.name}
              </option>
            ))}
          </select>
          <Button type="submit" disabled={submitting} className="w-full">
            카테고리 등록
          </Button>
        </form>
        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}
        <ul className="divide-y">
          {categories.map((c) => (
            <CategoryRow key={c.categoryId} category={c} categories={categories} disabled={submitting} run={run} />
          ))}
        </ul>
      </CardContent>
    </Card>
  )
}

function CategoryRow({
  category,
  categories,
  disabled,
  run,
}: {
  category: Category
  categories: Category[]
  disabled: boolean
  run: (action: () => Promise<unknown>) => Promise<boolean>
}) {
  const [name, setName] = useState(category.name)
  const [parentId, setParentId] = useState(category.parentId?.toString() ?? '')
  const [sortOrder, setSortOrder] = useState(category.sortOrder)

  const save = () =>
    run(() =>
      adminApi.updateCategory(category.categoryId, { name, parentId: parentId ? Number(parentId) : null, sortOrder }),
    )
  const remove = () => {
    if (confirm(`'${category.name}' 카테고리를 삭제할까요?`)) run(() => adminApi.deleteCategory(category.categoryId))
  }

  return (
    <li className="flex flex-wrap items-center gap-2 py-2">
      <Input
        aria-label={`${category.name} 이름`}
        value={name}
        onChange={(e) => setName(e.target.value)}
        className="min-w-0 flex-1"
      />
      <select
        aria-label={`${category.name} 상위`}
        value={parentId}
        onChange={(e) => setParentId(e.target.value)}
        className={`${selectClass} w-32`}
      >
        <option value="">최상위</option>
        {categories
          .filter((c) => c.categoryId !== category.categoryId)
          .map((c) => (
            <option key={c.categoryId} value={c.categoryId}>
              {c.name}
            </option>
          ))}
      </select>
      <Input
        type="number"
        aria-label={`${category.name} 정렬`}
        value={sortOrder}
        onChange={(e) => setSortOrder(Number(e.target.value))}
        className="w-16"
      />
      <Button type="button" size="sm" variant="outline" disabled={disabled || !name.trim()} onClick={save}>
        저장
      </Button>
      <Button type="button" size="sm" variant="destructive" disabled={disabled} onClick={remove}>
        삭제
      </Button>
    </li>
  )
}
