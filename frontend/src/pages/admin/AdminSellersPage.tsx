import { useCallback, useEffect, useState } from 'react'
import { adminApi } from '../../api/endpoints'
import { ApiError } from '../../api/client'
import type { Seller, SellerStatus } from '../../api/types'

const statusLabel: Record<SellerStatus, string> = {
  PENDING: '심사대기',
  ACTIVE: '영업중',
  SUSPENDED: '정지',
}

export default function AdminSellersPage() {
  const [filter, setFilter] = useState<SellerStatus | ''>('PENDING')
  const [sellers, setSellers] = useState<Seller[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setSellers(await adminApi.listSellers(filter || undefined))
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setLoading(false)
    }
  }, [filter])

  useEffect(() => {
    load()
  }, [load])

  const review = async (id: number, approved: boolean) => {
    setError(null)
    try {
      await adminApi.approveSeller(id, approved)
      load()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '처리 실패')
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex gap-2">
        {(['PENDING', 'ACTIVE', 'SUSPENDED', ''] as const).map((s) => (
          <button
            key={s || 'all'}
            onClick={() => setFilter(s)}
            className={`rounded-full px-3 py-1 text-sm ${
              filter === s ? 'bg-indigo-600 text-white' : 'bg-white text-slate-600 border'
            }`}
          >
            {s ? statusLabel[s] : '전체'}
          </button>
        ))}
      </div>

      {error && <p className="text-sm text-red-500">{error}</p>}
      {loading ? (
        <p className="py-10 text-center text-slate-400">불러오는 중…</p>
      ) : sellers.length === 0 ? (
        <p className="py-10 text-center text-slate-400">해당 상태의 셀러가 없습니다.</p>
      ) : (
        <ul className="space-y-2">
          {sellers.map((s) => (
            <li
              key={s.sellerId}
              className="flex items-center justify-between rounded-xl border bg-white p-4"
            >
              <div>
                <p className="text-sm font-medium">{s.storeName}</p>
                <p className="text-xs text-slate-400">
                  {s.description || '소개 없음'} · userId {s.userId}
                </p>
              </div>
              {s.status === 'PENDING' ? (
                <div className="flex gap-2">
                  <button
                    onClick={() => review(s.sellerId, true)}
                    className="rounded bg-indigo-600 px-3 py-1 text-sm font-semibold text-white hover:bg-indigo-700"
                  >
                    승인
                  </button>
                  <button
                    onClick={() => review(s.sellerId, false)}
                    className="rounded border px-3 py-1 text-sm text-slate-600 hover:bg-slate-50"
                  >
                    거절
                  </button>
                </div>
              ) : (
                <span className="rounded bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
                  {statusLabel[s.status]}
                </span>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
