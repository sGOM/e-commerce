import { useEffect, useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { sellerApi } from '../../api/endpoints'
import { ApiError } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import type { Seller } from '../../api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Card, CardContent } from '@/components/ui/card'
import { cn } from '@/lib/utils'

/** 판매자 백오피스 공통 레이아웃: 역할 확인 → 미입점이면 신청, 심사중이면 안내, 활성이면 서브내비 + Outlet. */
export default function SellerLayout() {
  const { user } = useAuth()
  const isSeller = user?.roles.includes('ROLE_SELLER') ?? false

  const [store, setStore] = useState<Seller | null>(null)
  const [loading, setLoading] = useState(isSeller)
  const [storeName, setStoreName] = useState('')
  const [description, setDescription] = useState('')
  const [applied, setApplied] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!isSeller) {
      setLoading(false)
      return
    }
    sellerApi
      .myStore()
      .then(setStore)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [isSeller])

  const apply = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await sellerApi.apply(storeName, description || undefined)
      setApplied(true)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '신청에 실패했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) return <p className="py-20 text-center text-sm text-muted-foreground">불러오는 중…</p>

  // 판매자 권한 없음 → 입점 신청
  if (!isSeller) {
    return (
      <div className="mx-auto max-w-md">
        <h1 className="mb-6 text-xl font-bold">판매자 입점</h1>
        {applied ? (
          <div className="rounded-xl bg-warning/10 p-5 text-center text-sm text-warning">
            입점 신청이 접수되었습니다. 관리자 승인 후 <b>다시 로그인</b>하면 판매자 기능을 사용할 수 있습니다.
          </div>
        ) : (
          <Card>
            <CardContent>
              <form onSubmit={apply} className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="storeName">상점명</Label>
                  <Input
                    id="storeName"
                    required
                    placeholder="상점명"
                    value={storeName}
                    onChange={(e) => setStoreName(e.target.value)}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="storeDescription">상점 소개 (선택)</Label>
                  <Textarea
                    id="storeDescription"
                    placeholder="상점 소개 (선택)"
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    rows={3}
                  />
                </div>
                {error && (
                  <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
                    {error}
                  </p>
                )}
                <Button type="submit" disabled={submitting} className="h-11 w-full">
                  {submitting ? '신청 중…' : '입점 신청'}
                </Button>
              </form>
            </CardContent>
          </Card>
        )}
      </div>
    )
  }

  const tab = ({ isActive }: { isActive: boolean }) =>
    cn(
      'pb-2 text-sm',
      isActive ? 'border-b-2 border-primary font-semibold text-primary' : 'text-muted-foreground',
    )

  return (
    <div>
      <div className="mb-2 flex items-center justify-between">
        <h1 className="text-xl font-bold">{store?.storeName ?? '판매자 센터'}</h1>
        {store && (
          <span className="rounded bg-primary/10 px-2 py-0.5 text-xs text-primary">
            {store.status}
          </span>
        )}
      </div>
      <nav className="mb-6 flex gap-6 border-b border-border">
        <NavLink to="/seller/products" className={tab}>
          상품
        </NavLink>
        <NavLink to="/seller/orders" className={tab}>
          주문/배송
        </NavLink>
        <NavLink to="/seller/settlements" className={tab}>
          정산
        </NavLink>
        <NavLink to="/seller/flash-sales" className={tab}>
          타임딜
        </NavLink>
      </nav>
      {error && (
        <p role="alert" className="mb-4 text-sm text-destructive">
          {error}
        </p>
      )}
      <Outlet context={store} />
    </div>
  )
}
