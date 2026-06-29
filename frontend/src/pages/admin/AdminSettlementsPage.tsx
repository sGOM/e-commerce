import { useEffect, useState } from 'react'
import { adminApi } from '../../api/endpoints'
import { ApiError, formatKRW } from '../../api/client'
import type { Settlement } from '../../api/types'

export default function AdminSettlementsPage() {
  const [rateBp, setRateBp] = useState<number | ''>('')
  const [savedRate, setSavedRate] = useState<number | null>(null)
  const [generated, setGenerated] = useState<Settlement[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [msg, setMsg] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    adminApi
      .getSettlementPolicy()
      .then((p) => {
        setRateBp(p.commissionRateBp)
        setSavedRate(p.commissionRateBp)
      })
      .catch((e) => setError(e.message))
  }, [])

  const saveRate = async () => {
    if (rateBp === '') return
    setError(null)
    setMsg(null)
    try {
      const p = await adminApi.updateSettlementPolicy(Number(rateBp))
      setSavedRate(p.commissionRateBp)
      setMsg('수수료율을 변경했습니다.')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '변경 실패')
    }
  }

  const generate = async () => {
    setBusy(true)
    setError(null)
    setMsg(null)
    try {
      const list = await adminApi.generateSettlements()
      setGenerated(list)
      setMsg(`정산서 ${list.length}건을 생성했습니다.`)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '정산 생성 실패')
    } finally {
      setBusy(false)
    }
  }

  const pay = async (id: number) => {
    setError(null)
    try {
      const updated = await adminApi.paySettlement(id)
      setGenerated((list) =>
        (list ?? []).map((s) => (s.settlementId === id ? updated : s)),
      )
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '지급 실패')
    }
  }

  return (
    <div className="space-y-6">
      <section className="rounded-xl border bg-white p-5">
        <h2 className="mb-3 font-bold">플랫폼 수수료율</h2>
        <div className="flex items-center gap-2">
          <input
            type="number"
            min={0}
            max={10000}
            value={rateBp}
            onChange={(e) => setRateBp(e.target.value === '' ? '' : Number(e.target.value))}
            className="w-32 rounded-lg border px-3 py-2 text-sm"
          />
          <span className="text-sm text-slate-500">
            bp (100 = 1%{savedRate != null ? `, 현재 ${savedRate / 100}%` : ''})
          </span>
          <button
            onClick={saveRate}
            className="rounded-lg border px-3 py-2 text-sm text-slate-600 hover:bg-slate-50"
          >
            저장
          </button>
        </div>
      </section>

      <section className="rounded-xl border bg-white p-5">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="font-bold">정산 생성</h2>
          <button
            onClick={generate}
            disabled={busy}
            className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-700 disabled:bg-slate-300"
          >
            {busy ? '생성 중…' : '미정산분 정산 생성'}
          </button>
        </div>

        {error && <p className="text-sm text-red-500">{error}</p>}
        {msg && <p className="text-sm text-green-600">{msg}</p>}

        {generated && generated.length > 0 && (
          <ul className="mt-3 space-y-2">
            {generated.map((s) => (
              <li
                key={s.settlementId}
                className="flex items-center justify-between rounded-lg border p-3 text-sm"
              >
                <div>
                  <p className="font-medium">{s.storeName}</p>
                  <p className="text-xs text-slate-400">
                    판매 {formatKRW(s.salesAmount)} · 수수료 {formatKRW(s.commissionAmount)} ·{' '}
                    {s.settledCount}건
                  </p>
                </div>
                <div className="flex items-center gap-3">
                  <span className="font-semibold text-indigo-600">
                    {formatKRW(s.payoutAmount)}
                  </span>
                  {s.status === 'PAID' ? (
                    <span className="rounded bg-green-50 px-2 py-0.5 text-xs text-green-600">
                      지급완료
                    </span>
                  ) : (
                    <button
                      onClick={() => pay(s.settlementId)}
                      className="rounded bg-indigo-600 px-3 py-1 text-xs font-semibold text-white hover:bg-indigo-700"
                    >
                      지급
                    </button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
        {generated && generated.length === 0 && (
          <p className="mt-3 text-sm text-slate-400">정산할 미정산 주문이 없습니다.</p>
        )}
      </section>
    </div>
  )
}
