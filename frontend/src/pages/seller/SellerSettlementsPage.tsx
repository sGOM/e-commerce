import { useEffect, useState } from 'react'
import { sellerApi } from '../../api/endpoints'
import { formatKRW } from '../../api/client'
import type { Settlement } from '../../api/types'

export default function SellerSettlementsPage() {
  const [items, setItems] = useState<Settlement[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    sellerApi
      .settlements()
      .then(setItems)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  if (loading) return <p className="py-10 text-center text-slate-400">불러오는 중…</p>
  if (error) return <p className="py-10 text-center text-red-500">{error}</p>

  return items.length === 0 ? (
    <p className="py-10 text-center text-slate-400">정산 내역이 없습니다.</p>
  ) : (
    <ul className="space-y-3">
      {items.map((s) => (
        <li key={s.settlementId} className="rounded-xl border bg-white p-4">
          <div className="flex items-center justify-between">
            <p className="text-sm text-slate-400">
              {new Date(s.createdAt).toLocaleDateString('ko-KR')} · {s.settledCount}건
            </p>
            <span
              className={`rounded px-2 py-0.5 text-xs ${
                s.status === 'PAID'
                  ? 'bg-green-50 text-green-600'
                  : 'bg-amber-50 text-amber-600'
              }`}
            >
              {s.status === 'PAID' ? '지급완료' : '지급대기'}
            </span>
          </div>
          <div className="mt-2 space-y-1 text-sm text-slate-600">
            <div className="flex justify-between">
              <span>판매액</span>
              <span>{formatKRW(s.salesAmount)}</span>
            </div>
            <div className="flex justify-between text-red-500">
              <span>수수료</span>
              <span>-{formatKRW(s.commissionAmount)}</span>
            </div>
            <div className="flex justify-between border-t pt-1 font-bold text-slate-900">
              <span>지급액</span>
              <span className="text-indigo-600">{formatKRW(s.payoutAmount)}</span>
            </div>
          </div>
        </li>
      ))}
    </ul>
  )
}
