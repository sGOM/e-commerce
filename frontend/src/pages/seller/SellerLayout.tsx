import { useEffect, useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { sellerApi } from '../../api/endpoints'
import { ApiError } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import type { Seller } from '../../api/types'

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

  if (loading) return <p className="py-20 text-center text-slate-400">불러오는 중…</p>

  // 판매자 권한 없음 → 입점 신청
  if (!isSeller) {
    return (
      <div className="mx-auto max-w-md">
        <h1 className="mb-6 text-xl font-bold">판매자 입점</h1>
        {applied ? (
          <div className="rounded-xl bg-amber-50 p-5 text-center text-sm text-amber-700">
            입점 신청이 접수되었습니다. 관리자 승인 후 <b>다시 로그인</b>하면 판매자 기능을 사용할 수 있습니다.
          </div>
        ) : (
          <form onSubmit={apply} className="space-y-3 rounded-xl border bg-white p-5">
            <input
              required
              placeholder="상점명"
              value={storeName}
              onChange={(e) => setStoreName(e.target.value)}
              className="w-full rounded-lg border px-3 py-2 text-sm"
            />
            <textarea
              placeholder="상점 소개 (선택)"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="w-full rounded-lg border px-3 py-2 text-sm"
              rows={3}
            />
            {error && <p className="text-sm text-red-500">{error}</p>}
            <button
              disabled={submitting}
              className="w-full rounded-xl bg-indigo-600 py-3 font-semibold text-white hover:bg-indigo-700 disabled:bg-slate-300"
            >
              {submitting ? '신청 중…' : '입점 신청'}
            </button>
          </form>
        )}
      </div>
    )
  }

  const tab = ({ isActive }: { isActive: boolean }) =>
    `pb-2 text-sm ${isActive ? 'border-b-2 border-indigo-600 font-semibold text-indigo-600' : 'text-slate-500'}`

  return (
    <div>
      <div className="mb-2 flex items-center justify-between">
        <h1 className="text-xl font-bold">{store?.storeName ?? '판매자 센터'}</h1>
        {store && (
          <span className="rounded bg-indigo-50 px-2 py-0.5 text-xs text-indigo-600">
            {store.status}
          </span>
        )}
      </div>
      <nav className="mb-6 flex gap-6 border-b">
        <NavLink to="/seller/products" className={tab}>
          상품
        </NavLink>
        <NavLink to="/seller/orders" className={tab}>
          주문/배송
        </NavLink>
        <NavLink to="/seller/settlements" className={tab}>
          정산
        </NavLink>
      </nav>
      {error && <p className="mb-4 text-sm text-red-500">{error}</p>}
      <Outlet context={store} />
    </div>
  )
}
