import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { adminMembershipApi } from '../../api/endpoints'
import { ApiError, formatKRW } from '../../api/client'
import { membershipStatusLabel } from '../../labels'
import type { AdminMembership, MembershipPolicy, MembershipStatus, PageResponse } from '../../api/types'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

const statusFilters: (MembershipStatus | '')[] = ['', 'ACTIVE', 'PAST_DUE', 'CANCELED', 'EXPIRED']

function statusBadgeVariant(status: MembershipStatus): 'default' | 'secondary' | 'destructive' | 'outline' {
  if (status === 'ACTIVE') return 'default'
  if (status === 'PAST_DUE') return 'destructive'
  if (status === 'CANCELED') return 'secondary'
  return 'outline'
}

function PolicyPanel() {
  const [policy, setPolicy] = useState<MembershipPolicy | null>(null)
  const [form, setForm] = useState<MembershipPolicy | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    adminMembershipApi
      .getPolicy()
      .then((p) => {
        setPolicy(p)
        setForm(p)
      })
      .catch((e) => setError(e instanceof ApiError ? e.message : '정책을 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
  }, [])

  const save = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!form) return
    setSaving(true)
    setError(null)
    try {
      const updated = await adminMembershipApi.updatePolicy(form)
      setPolicy(updated)
      setForm(updated)
      toast.success('멤버십 정책을 변경했습니다.')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '저장에 실패했습니다.')
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <p className="py-6 text-center text-sm text-muted-foreground">불러오는 중…</p>
  if (!form) return null

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">멤버십 정책</CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={save} className="space-y-3">
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            <div>
              <Label className="mb-1 text-xs text-muted-foreground">월 구독료(원)</Label>
              <Input
                type="number"
                min={0}
                value={form.monthlyPrice}
                onChange={(e) => setForm({ ...form, monthlyPrice: Number(e.target.value) })}
              />
            </div>
            <div>
              <Label className="mb-1 text-xs text-muted-foreground">포인트 배율(bp, 10000=1배)</Label>
              <Input
                type="number"
                min={10_000}
                value={form.pointEarnMultiplierBp}
                onChange={(e) => setForm({ ...form, pointEarnMultiplierBp: Number(e.target.value) })}
              />
            </div>
            <div>
              <Label className="mb-1 text-xs text-muted-foreground">최대 재시도(회)</Label>
              <Input
                type="number"
                min={0}
                value={form.maxRetryCount}
                onChange={(e) => setForm({ ...form, maxRetryCount: Number(e.target.value) })}
              />
            </div>
            <div>
              <Label className="mb-1 text-xs text-muted-foreground">유예기간(일)</Label>
              <Input
                type="number"
                min={0}
                value={form.graceDays}
                onChange={(e) => setForm({ ...form, graceDays: Number(e.target.value) })}
              />
            </div>
            <label className="flex items-center gap-2 self-end pb-2 text-sm">
              <input
                type="checkbox"
                checked={form.freeShippingEnabled}
                onChange={(e) => setForm({ ...form, freeShippingEnabled: e.target.checked })}
              />
              무료배송 활성화
            </label>
          </div>
          {error && <p className="text-sm text-destructive">{error}</p>}
          <Button type="submit" disabled={saving} size="sm">
            {saving ? '저장 중…' : '정책 저장'}
          </Button>
          {policy && (
            <p className="text-xs text-muted-foreground">
              현재: {formatKRW(policy.monthlyPrice)}/월 · 포인트 {policy.pointEarnMultiplierBp / 10_000}배 ·
              {policy.freeShippingEnabled ? ' 무료배송 ON' : ' 무료배송 OFF'} · 재시도 {policy.maxRetryCount}회 · 유예{' '}
              {policy.graceDays}일
            </p>
          )}
        </form>
      </CardContent>
    </Card>
  )
}

export default function AdminMembershipsPage() {
  const [status, setStatus] = useState<MembershipStatus | ''>('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<AdminMembership> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [runningBatch, setRunningBatch] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setData(await adminMembershipApi.search({ status: status || undefined, page }))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '불러오기에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }, [status, page])

  useEffect(() => {
    load()
  }, [load])

  const runBilling = async () => {
    if (!confirm('정기결제 배치를 지금 수동으로 실행하시겠습니까?')) return
    setRunningBatch(true)
    try {
      const result = await adminMembershipApi.runBilling()
      toast.success(
        `정기결제 배치를 실행했습니다. 갱신 ${result.renewed}건 / 재시도대기 ${result.failed}건 / 만료 ${result.expired}건`,
      )
      load()
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : '배치 실행에 실패했습니다.')
    } finally {
      setRunningBatch(false)
    }
  }

  return (
    <div className="space-y-6">
      <PolicyPanel />

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2">
          {statusFilters.map((s) => (
            <button
              key={s || 'all'}
              onClick={() => {
                setStatus(s)
                setPage(0)
              }}
              className={`rounded-full px-3 py-1 text-sm ${
                status === s
                  ? 'bg-primary text-primary-foreground'
                  : 'border border-border bg-background text-muted-foreground'
              }`}
            >
              {s ? membershipStatusLabel[s] : '전체'}
            </button>
          ))}
        </div>
        <Button type="button" size="sm" disabled={runningBatch} onClick={runBilling}>
          {runningBatch ? '실행 중…' : '정기결제 배치 수동 실행'}
        </Button>
      </div>

      {error && <p className="text-sm text-destructive">{error}</p>}
      {loading ? (
        <p className="py-10 text-center text-muted-foreground">불러오는 중…</p>
      ) : !data || data.content.length === 0 ? (
        <p className="py-10 text-center text-muted-foreground">조건에 맞는 멤버십이 없습니다.</p>
      ) : (
        <>
          <ul className="space-y-2">
            {data.content.map((m) => (
              <li key={m.id}>
                <Link
                  to={`/admin/memberships/${m.id}`}
                  className="flex items-center justify-between gap-2 rounded-xl border border-border bg-card p-4 hover:bg-muted/50"
                >
                  <div className="min-w-0">
                    <p className="text-sm font-medium">
                      멤버십 #{m.id} · 회원 #{m.userId} · {m.plan}
                    </p>
                    <p className="mt-0.5 text-xs text-muted-foreground">
                      {formatKRW(m.price)}/월 · 다음 결제일 {new Date(m.nextBillingAt).toLocaleDateString('ko-KR')}
                      {m.billingFailureCount > 0 && ` · 결제실패 ${m.billingFailureCount}회`}
                    </p>
                  </div>
                  <Badge variant={statusBadgeVariant(m.status)}>{membershipStatusLabel[m.status]}</Badge>
                </Link>
              </li>
            ))}
          </ul>

          {data.totalPages > 1 && (
            <div className="flex justify-center gap-1">
              {Array.from({ length: data.totalPages }, (_, i) => (
                <button
                  key={i}
                  onClick={() => setPage(i)}
                  className={`h-8 w-8 rounded text-sm ${
                    i === page
                      ? 'bg-primary text-primary-foreground'
                      : 'border border-border bg-background text-muted-foreground'
                  }`}
                >
                  {i + 1}
                </button>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  )
}
