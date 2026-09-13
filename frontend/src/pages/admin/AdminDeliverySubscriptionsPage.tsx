import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { adminDeliverySubscriptionApi } from '../../api/endpoints'
import { ApiError } from '../../api/client'
import { deliverySubscriptionStatusLabel } from '../../labels'
import type { AdminDeliverySubscription, DeliverySubscriptionPolicy, DeliverySubscriptionStatus, PageResponse } from '../../api/types'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

const statusFilters: (DeliverySubscriptionStatus | '')[] = ['', 'ACTIVE', 'PAUSED', 'CANCELED']

function statusBadgeVariant(status: DeliverySubscriptionStatus): 'default' | 'secondary' | 'outline' {
  if (status === 'ACTIVE') return 'default'
  if (status === 'PAUSED') return 'secondary'
  return 'outline'
}

function PolicyPanel() {
  const [policy, setPolicy] = useState<DeliverySubscriptionPolicy | null>(null)
  const [form, setForm] = useState<DeliverySubscriptionPolicy | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    adminDeliverySubscriptionApi
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
      const updated = await adminDeliverySubscriptionApi.updatePolicy(form)
      setPolicy(updated)
      setForm(updated)
      toast.success('정기배송 정책을 변경했습니다.')
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
        <CardTitle className="text-base">정기배송 정책</CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={save} className="space-y-3">
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            <div>
              <Label className="mb-1 text-xs text-muted-foreground">자동 정지 임계 연속 실패(회)</Label>
              <Input
                type="number"
                min={1}
                value={form.maxConsecutiveFailures}
                onChange={(e) => setForm({ ...form, maxConsecutiveFailures: Number(e.target.value) })}
              />
            </div>
            <div>
              <Label className="mb-1 text-xs text-muted-foreground">스킵 마감(배송일 며칠 전까지)</Label>
              <Input
                type="number"
                min={0}
                value={form.skipDeadlineDays}
                onChange={(e) => setForm({ ...form, skipDeadlineDays: Number(e.target.value) })}
              />
            </div>
          </div>
          {error && <p className="text-sm text-destructive">{error}</p>}
          <Button type="submit" disabled={saving} size="sm">
            {saving ? '저장 중…' : '정책 저장'}
          </Button>
          {policy && (
            <p className="text-xs text-muted-foreground">
              현재: 연속 실패 {policy.maxConsecutiveFailures}회 시 자동 정지 · 배송일 {policy.skipDeadlineDays}일
              전까지 스킵 가능
            </p>
          )}
        </form>
      </CardContent>
    </Card>
  )
}

export default function AdminDeliverySubscriptionsPage() {
  const [status, setStatus] = useState<DeliverySubscriptionStatus | ''>('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<AdminDeliverySubscription> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [runningBatch, setRunningBatch] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setData(await adminDeliverySubscriptionApi.search({ status: status || undefined, page }))
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
    if (!confirm('정기배송 배치를 지금 수동으로 실행하시겠습니까?')) return
    setRunningBatch(true)
    try {
      const result = await adminDeliverySubscriptionApi.runBilling()
      toast.success(
        `배치를 실행했습니다. 주문생성 ${result.orderCreated}건 / 재고부족스킵 ${result.skippedOutOfStock}건 / ` +
          `사용자스킵 ${result.skippedByUser}건 / 결제실패 ${result.paymentFailed}건 / 자동정지 ${result.autoPaused}건 / 오류 ${result.errored}건`,
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
                status === s ? 'bg-primary text-primary-foreground' : 'border border-border bg-background text-muted-foreground'
              }`}
            >
              {s ? deliverySubscriptionStatusLabel[s] : '전체'}
            </button>
          ))}
        </div>
        <Button type="button" size="sm" disabled={runningBatch} onClick={runBilling}>
          {runningBatch ? '실행 중…' : '정기배송 배치 수동 실행'}
        </Button>
      </div>

      {error && <p className="text-sm text-destructive">{error}</p>}
      {loading ? (
        <p className="py-10 text-center text-muted-foreground">불러오는 중…</p>
      ) : !data || data.content.length === 0 ? (
        <p className="py-10 text-center text-muted-foreground">조건에 맞는 정기배송이 없습니다.</p>
      ) : (
        <>
          <ul className="space-y-2">
            {data.content.map((s) => (
              <li key={s.id}>
                <Link
                  to={`/admin/delivery-subscriptions/${s.id}`}
                  className="flex items-center justify-between gap-2 rounded-xl border border-border bg-card p-4 hover:bg-muted/50"
                >
                  <div className="min-w-0">
                    <p className="text-sm font-medium">
                      정기배송 #{s.id} · 회원 #{s.userId} · 옵션 #{s.optionId}
                    </p>
                    <p className="mt-0.5 text-xs text-muted-foreground">
                      {s.quantity}개 · {s.cycleDays}일마다 · 다음 배송일 {new Date(s.nextOrderAt).toLocaleDateString('ko-KR')}
                      {s.consecutiveFailureCount > 0 && ` · 결제실패 ${s.consecutiveFailureCount}회`}
                      {s.skipRequested && ' · 스킵예약'}
                    </p>
                  </div>
                  <Badge variant={statusBadgeVariant(s.status)}>{deliverySubscriptionStatusLabel[s.status]}</Badge>
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
                    i === page ? 'bg-primary text-primary-foreground' : 'border border-border bg-background text-muted-foreground'
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
