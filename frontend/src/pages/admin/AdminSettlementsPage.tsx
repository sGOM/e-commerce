import { useCallback, useEffect, useState } from 'react'
import { adminApi } from '../../api/endpoints'
import { ApiError, formatKRW } from '../../api/client'
import type { Settlement, SettlementStatus } from '../../api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent } from '@/components/ui/card'

const STATUS_FILTERS: { value: SettlementStatus | ''; label: string }[] = [
  { value: '', label: '전체' },
  { value: 'PENDING', label: '지급대기' },
  { value: 'PAID', label: '지급완료' },
]

export default function AdminSettlementsPage() {
  const [rateBp, setRateBp] = useState<number | ''>('')
  const [savedRate, setSavedRate] = useState<number | null>(null)
  const [settlements, setSettlements] = useState<Settlement[]>([])
  const [status, setStatus] = useState<SettlementStatus | ''>('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
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

  const loadList = useCallback(async () => {
    try {
      const res = await adminApi.listSettlements({ status: status || undefined, page })
      setSettlements(res.content)
      setTotalPages(res.totalPages)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '정산 목록 조회 실패')
    }
  }, [status, page])

  useEffect(() => {
    loadList()
  }, [loadList])

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
      setMsg(list.length > 0 ? `정산서 ${list.length}건을 생성했습니다.` : '정산할 미정산 주문이 없습니다.')
      await loadList()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '정산 생성 실패')
    } finally {
      setBusy(false)
    }
  }

  const pay = async (id: number) => {
    setError(null)
    try {
      await adminApi.paySettlement(id)
      await loadList()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '지급 실패')
    }
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardContent>
          <h2 className="mb-3 font-bold">플랫폼 수수료율</h2>
          <div className="flex items-center gap-2">
            <Input
              type="number"
              min={0}
              max={10000}
              value={rateBp}
              onChange={(e) => setRateBp(e.target.value === '' ? '' : Number(e.target.value))}
              className="w-32"
              aria-label="플랫폼 수수료율(bp)"
            />
            <span className="text-sm text-muted-foreground">
              bp (100 = 1%{savedRate != null ? `, 현재 ${savedRate / 100}%` : ''})
            </span>
            <Button type="button" variant="outline" onClick={saveRate}>
              저장
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
            <h2 className="font-bold">정산 목록</h2>
            <Button type="button" disabled={busy} onClick={generate}>
              {busy ? '생성 중…' : '미정산분 정산 생성'}
            </Button>
          </div>

          <div className="mb-3 flex gap-2" role="group" aria-label="정산 상태 필터">
            {STATUS_FILTERS.map((f) => (
              <Button
                key={f.value}
                type="button"
                size="sm"
                variant={status === f.value ? 'default' : 'outline'}
                aria-pressed={status === f.value}
                onClick={() => {
                  setStatus(f.value)
                  setPage(0)
                }}
              >
                {f.label}
              </Button>
            ))}
          </div>

          {error && (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          )}
          {msg && <p className="text-sm text-success">{msg}</p>}

          {settlements.length === 0 ? (
            <p className="mt-3 text-sm text-muted-foreground">정산 내역이 없습니다.</p>
          ) : (
            <ul className="mt-3 space-y-2">
              {settlements.map((s) => (
                <li
                  key={s.settlementId}
                  className="flex items-center justify-between rounded-lg border border-border p-3 text-sm"
                >
                  <div>
                    <p className="font-medium">{s.storeName}</p>
                    <p className="text-xs text-muted-foreground">
                      판매 {formatKRW(s.salesAmount)} · 수수료 {formatKRW(s.commissionAmount)} · {s.settledCount}건 ·{' '}
                      {new Date(s.createdAt).toLocaleDateString('ko-KR')}
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className="font-semibold text-primary">{formatKRW(s.payoutAmount)}</span>
                    {s.status === 'PAID' ? (
                      <span className="rounded bg-success/10 px-2 py-0.5 text-xs text-success">지급완료</span>
                    ) : (
                      <Button type="button" size="sm" onClick={() => pay(s.settlementId)}>
                        지급
                      </Button>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}

          {totalPages > 1 && (
            <div className="mt-3 flex items-center justify-center gap-3 text-sm">
              <Button
                type="button"
                size="sm"
                variant="outline"
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
              >
                이전
              </Button>
              <span>
                {page + 1} / {totalPages}
              </span>
              <Button
                type="button"
                size="sm"
                variant="outline"
                disabled={page + 1 >= totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                다음
              </Button>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
