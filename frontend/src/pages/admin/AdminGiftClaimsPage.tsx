import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import { adminGiftClaimApi } from '../../api/endpoints'
import { ApiError } from '../../api/client'
import { giftClaimStatusLabel } from '../../labels'
import type { GiftClaim, GiftClaimStatus, GiftPolicy, PageResponse } from '../../api/types'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

const statusFilters: (GiftClaimStatus | '')[] = ['', 'PENDING', 'CLAIMED', 'EXPIRED', 'CANCELED']

function statusBadgeVariant(status: GiftClaimStatus): 'default' | 'secondary' | 'destructive' | 'outline' {
  if (status === 'CLAIMED') return 'default'
  if (status === 'PENDING') return 'secondary'
  if (status === 'EXPIRED') return 'destructive'
  return 'outline'
}

function PolicyPanel() {
  const [policy, setPolicy] = useState<GiftPolicy | null>(null)
  const [form, setForm] = useState<GiftPolicy | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    adminGiftClaimApi
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
      const updated = await adminGiftClaimApi.updatePolicy(form.expiryDays)
      setPolicy(updated)
      setForm(updated)
      toast.success('선물 링크 정책을 변경했습니다.')
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
        <CardTitle className="text-base">선물 링크 정책</CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={save} className="space-y-3">
          <div className="max-w-xs">
            <Label className="mb-1 text-xs text-muted-foreground">만료 기한(일)</Label>
            <Input
              type="number"
              min={1}
              value={form.expiryDays}
              onChange={(e) => setForm({ ...form, expiryDays: Number(e.target.value) })}
            />
          </div>
          {error && <p className="text-sm text-destructive">{error}</p>}
          <Button type="submit" disabled={saving} size="sm">
            {saving ? '저장 중…' : '정책 저장'}
          </Button>
          {policy && (
            <p className="text-xs text-muted-foreground">
              현재: 발급 후 {policy.expiryDays}일 이내 미수락 시 자동 취소·환불
            </p>
          )}
        </form>
      </CardContent>
    </Card>
  )
}

export default function AdminGiftClaimsPage() {
  const [status, setStatus] = useState<GiftClaimStatus | ''>('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<GiftClaim> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [runningBatch, setRunningBatch] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setData(await adminGiftClaimApi.search({ status: status || undefined, page }))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '불러오기에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }, [status, page])

  useEffect(() => {
    load()
  }, [load])

  const runExpiry = async () => {
    if (!confirm('만료 대상 선물 링크를 지금 수동으로 취소·환불 처리하시겠습니까?')) return
    setRunningBatch(true)
    try {
      const result = await adminGiftClaimApi.runExpiry()
      toast.success(`만료 배치를 실행했습니다. 처리 ${result.expiredCount}건 / 오류 ${result.erroredCount}건`)
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
              {s ? giftClaimStatusLabel[s] : '전체'}
            </button>
          ))}
        </div>
        <Button type="button" size="sm" disabled={runningBatch} onClick={runExpiry}>
          {runningBatch ? '실행 중…' : '만료 배치 수동 실행'}
        </Button>
      </div>

      {error && <p className="text-sm text-destructive">{error}</p>}
      {loading ? (
        <p className="py-10 text-center text-muted-foreground">불러오는 중…</p>
      ) : !data || data.content.length === 0 ? (
        <p className="py-10 text-center text-muted-foreground">조건에 맞는 선물 링크가 없습니다.</p>
      ) : (
        <>
          <ul className="space-y-2">
            {data.content.map((c) => (
              <li
                key={c.token}
                className="flex items-center justify-between gap-2 rounded-xl border border-border bg-card p-4"
              >
                <div className="min-w-0">
                  <p className="text-sm font-medium">
                    주문 {c.orderNumber} · 주문 #{c.orderId}
                  </p>
                  <p className="mt-0.5 text-xs text-muted-foreground">
                    만료 {new Date(c.expiresAt).toLocaleString('ko-KR')}
                    {c.claimedAt && ` · 수락 ${new Date(c.claimedAt).toLocaleString('ko-KR')}`}
                  </p>
                </div>
                <Badge variant={statusBadgeVariant(c.status)}>{giftClaimStatusLabel[c.status]}</Badge>
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
