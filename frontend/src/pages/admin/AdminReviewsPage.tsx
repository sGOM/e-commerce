import { useCallback, useEffect, useState } from 'react'
import { adminReviewApi } from '../../api/endpoints'
import { ApiError } from '../../api/client'
import { reviewStatusLabel } from '../../labels'
import { StarRatingDisplay } from '../../components/StarRating'
import type { PageResponse, Review, ReviewRewardPolicy, ReviewStatus } from '../../api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent } from '@/components/ui/card'
import { cn } from '@/lib/utils'

const statusFilters: (ReviewStatus | '')[] = ['', 'VISIBLE', 'REPORTED', 'HIDDEN']

function ReviewPolicyPanel() {
  const [policy, setPolicy] = useState<ReviewRewardPolicy | null>(null)
  const [form, setForm] = useState<ReviewRewardPolicy | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    adminReviewApi
      .getPolicy()
      .then((p) => {
        setPolicy(p)
        setForm(p)
      })
      .catch((e) => setError((e as Error).message))
      .finally(() => setLoading(false))
  }, [])

  const save = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!form) return
    setSaving(true)
    setError(null)
    setSaved(false)
    try {
      const updated = await adminReviewApi.updatePolicy(form)
      setPolicy(updated)
      setForm(updated)
      setSaved(true)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '저장 실패')
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <p className="py-6 text-center text-sm text-muted-foreground">불러오는 중…</p>
  if (!form) return null

  return (
    <Card>
      <CardContent>
        <form onSubmit={save} className="space-y-3">
          <h3 className="font-semibold">리뷰 정책</h3>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <div className="space-y-1">
              <Label htmlFor="text-review-point" className="text-xs text-muted-foreground">
                텍스트 리뷰 포인트
              </Label>
              <Input
                id="text-review-point"
                type="number"
                min={0}
                value={form.textReviewPoint}
                onChange={(e) => setForm({ ...form, textReviewPoint: Number(e.target.value) })}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="photo-review-point" className="text-xs text-muted-foreground">
                포토 리뷰 포인트
              </Label>
              <Input
                id="photo-review-point"
                type="number"
                min={0}
                value={form.photoReviewPoint}
                onChange={(e) => setForm({ ...form, photoReviewPoint: Number(e.target.value) })}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="reviewable-days" className="text-xs text-muted-foreground">
                작성 가능 기간(일)
              </Label>
              <Input
                id="reviewable-days"
                type="number"
                min={1}
                value={form.reviewableDays}
                onChange={(e) => setForm({ ...form, reviewableDays: Number(e.target.value) })}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="report-threshold" className="text-xs text-muted-foreground">
                신고 임계치(건)
              </Label>
              <Input
                id="report-threshold"
                type="number"
                min={1}
                value={form.reportThreshold}
                onChange={(e) => setForm({ ...form, reportThreshold: Number(e.target.value) })}
              />
            </div>
          </div>
          {error && (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          )}
          {saved && !error && <p className="text-sm text-success">정책이 저장되었습니다.</p>}
          <Button type="submit" disabled={saving}>
            {saving ? '저장 중…' : '정책 저장'}
          </Button>
          {policy && (
            <p className="text-xs text-muted-foreground">
              현재: 텍스트 {policy.textReviewPoint}P · 포토 {policy.photoReviewPoint}P · 작성기간{' '}
              {policy.reviewableDays}일 · 신고임계치 {policy.reportThreshold}건
            </p>
          )}
        </form>
      </CardContent>
    </Card>
  )
}

export default function AdminReviewsPage() {
  const [status, setStatus] = useState<ReviewStatus | ''>('')
  const [reportedOnly, setReportedOnly] = useState(false)
  const [productId, setProductId] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<Review> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setData(
        await adminReviewApi.search({
          status: status || undefined,
          reported: reportedOnly || undefined,
          productId: productId ? Number(productId) : undefined,
          page,
        }),
      )
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setLoading(false)
    }
  }, [status, reportedOnly, productId, page])

  useEffect(() => {
    load()
  }, [load])

  const changeStatus = async (id: number, next: 'VISIBLE' | 'HIDDEN') => {
    const label = next === 'HIDDEN' ? '숨김 처리' : '복구'
    if (!confirm(`이 리뷰를 ${label}하시겠습니까?`)) return
    setError(null)
    try {
      await adminReviewApi.changeStatus(id, next)
      load()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '처리 실패')
    }
  }

  return (
    <div className="space-y-6">
      <ReviewPolicyPanel />

      <div className="flex flex-wrap items-center gap-2">
        {statusFilters.map((s) => (
          <Button
            key={s || 'all'}
            type="button"
            size="sm"
            variant={status === s ? 'default' : 'outline'}
            className="rounded-full"
            onClick={() => {
              setStatus(s)
              setPage(0)
            }}
          >
            {s ? reviewStatusLabel[s] : '전체'}
          </Button>
        ))}
        <label className="ml-2 flex items-center gap-1.5 text-sm text-muted-foreground">
          <input
            type="checkbox"
            checked={reportedOnly}
            onChange={(e) => {
              setReportedOnly(e.target.checked)
              setPage(0)
            }}
          />
          신고 검토 큐만
        </label>
        <Input
          value={productId}
          onChange={(e) => {
            setProductId(e.target.value)
            setPage(0)
          }}
          placeholder="상품 ID"
          aria-label="상품 ID"
          inputMode="numeric"
          className="w-28"
        />
      </div>

      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
      {loading ? (
        <p className="py-10 text-center text-sm text-muted-foreground">불러오는 중…</p>
      ) : !data || data.content.length === 0 ? (
        <p className="py-10 text-center text-sm text-muted-foreground">조건에 맞는 리뷰가 없습니다.</p>
      ) : (
        <>
          <ul className="space-y-2">
            {data.content.map((r) => (
              <li key={r.id}>
                <Card>
                  <CardContent>
                    <div className="flex items-center justify-between gap-2">
                      <div className="flex items-center gap-2">
                        <StarRatingDisplay rating={r.rating} />
                        <span className="text-xs text-muted-foreground">
                          상품 #{r.productId} · 작성자 #{r.userId}
                        </span>
                      </div>
                      <div className="flex items-center gap-2">
                        <span className="rounded bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                          {reviewStatusLabel[r.status]}
                        </span>
                        {r.reportCount > 0 && (
                          <span className="rounded bg-destructive/10 px-2 py-0.5 text-xs text-destructive">
                            신고 {r.reportCount}건
                          </span>
                        )}
                      </div>
                    </div>
                    <p className="mt-2 whitespace-pre-line text-sm text-foreground">{r.content}</p>
                    {r.imageUrls.length > 0 && (
                      <div className="mt-2 flex flex-wrap gap-2">
                        {r.imageUrls.map((url, i) => (
                          <img
                            key={i}
                            src={url}
                            alt={`리뷰 사진 ${i + 1}`}
                            className="size-14 rounded object-cover"
                          />
                        ))}
                      </div>
                    )}
                    <div className="mt-3 flex justify-end gap-2">
                      {r.status === 'HIDDEN' ? (
                        <Button type="button" size="sm" variant="outline" onClick={() => changeStatus(r.id, 'VISIBLE')}>
                          복구
                        </Button>
                      ) : (
                        <Button
                          type="button"
                          size="sm"
                          variant="outline"
                          className="border-destructive/30 text-destructive hover:bg-destructive/10"
                          onClick={() => changeStatus(r.id, 'HIDDEN')}
                        >
                          숨김 처리
                        </Button>
                      )}
                    </div>
                  </CardContent>
                </Card>
              </li>
            ))}
          </ul>

          {data.totalPages > 1 && (
            <div className="flex justify-center gap-1">
              {Array.from({ length: data.totalPages }, (_, i) => (
                <button
                  key={i}
                  onClick={() => setPage(i)}
                  className={cn(
                    'h-8 w-8 rounded text-sm',
                    i === page
                      ? 'bg-primary text-primary-foreground'
                      : 'border border-input bg-background text-muted-foreground',
                  )}
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
