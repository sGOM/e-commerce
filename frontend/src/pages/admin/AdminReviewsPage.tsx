import { useCallback, useEffect, useState } from 'react'
import { adminReviewApi } from '../../api/endpoints'
import { ApiError } from '../../api/client'
import { reviewStatusLabel } from '../../labels'
import { StarRatingDisplay } from '../../components/StarRating'
import type { PageResponse, Review, ReviewRewardPolicy, ReviewStatus } from '../../api/types'

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

  if (loading) return <p className="py-6 text-center text-sm text-slate-400">불러오는 중…</p>
  if (!form) return null

  const field = 'w-24 rounded border px-2 py-1 text-sm'

  return (
    <form onSubmit={save} className="space-y-3 rounded-xl border bg-white p-4">
      <h3 className="font-semibold">리뷰 정책</h3>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <label className="text-xs text-slate-500">
          텍스트 리뷰 포인트
          <input
            type="number"
            min={0}
            value={form.textReviewPoint}
            onChange={(e) => setForm({ ...form, textReviewPoint: Number(e.target.value) })}
            className={`mt-1 block ${field}`}
          />
        </label>
        <label className="text-xs text-slate-500">
          포토 리뷰 포인트
          <input
            type="number"
            min={0}
            value={form.photoReviewPoint}
            onChange={(e) => setForm({ ...form, photoReviewPoint: Number(e.target.value) })}
            className={`mt-1 block ${field}`}
          />
        </label>
        <label className="text-xs text-slate-500">
          작성 가능 기간(일)
          <input
            type="number"
            min={1}
            value={form.reviewableDays}
            onChange={(e) => setForm({ ...form, reviewableDays: Number(e.target.value) })}
            className={`mt-1 block ${field}`}
          />
        </label>
        <label className="text-xs text-slate-500">
          신고 임계치(건)
          <input
            type="number"
            min={1}
            value={form.reportThreshold}
            onChange={(e) => setForm({ ...form, reportThreshold: Number(e.target.value) })}
            className={`mt-1 block ${field}`}
          />
        </label>
      </div>
      {error && <p className="text-sm text-red-500">{error}</p>}
      {saved && !error && <p className="text-sm text-green-600">정책이 저장되었습니다.</p>}
      <button
        disabled={saving}
        className="rounded bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-700 disabled:bg-slate-300"
      >
        {saving ? '저장 중…' : '정책 저장'}
      </button>
      {policy && (
        <p className="text-xs text-slate-400">
          현재: 텍스트 {policy.textReviewPoint}P · 포토 {policy.photoReviewPoint}P · 작성기간{' '}
          {policy.reviewableDays}일 · 신고임계치 {policy.reportThreshold}건
        </p>
      )}
    </form>
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
          <button
            key={s || 'all'}
            onClick={() => {
              setStatus(s)
              setPage(0)
            }}
            className={`rounded-full px-3 py-1 text-sm ${
              status === s ? 'bg-indigo-600 text-white' : 'border bg-white text-slate-600'
            }`}
          >
            {s ? reviewStatusLabel[s] : '전체'}
          </button>
        ))}
        <label className="ml-2 flex items-center gap-1.5 text-sm text-slate-600">
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
        <input
          value={productId}
          onChange={(e) => {
            setProductId(e.target.value)
            setPage(0)
          }}
          placeholder="상품 ID"
          inputMode="numeric"
          className="w-28 rounded border px-2 py-1 text-sm"
        />
      </div>

      {error && <p className="text-sm text-red-500">{error}</p>}
      {loading ? (
        <p className="py-10 text-center text-slate-400">불러오는 중…</p>
      ) : !data || data.content.length === 0 ? (
        <p className="py-10 text-center text-slate-400">조건에 맞는 리뷰가 없습니다.</p>
      ) : (
        <>
          <ul className="space-y-2">
            {data.content.map((r) => (
              <li key={r.id} className="rounded-xl border bg-white p-4">
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <StarRatingDisplay rating={r.rating} />
                    <span className="text-xs text-slate-400">
                      상품 #{r.productId} · 작성자 #{r.userId}
                    </span>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="rounded bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
                      {reviewStatusLabel[r.status]}
                    </span>
                    {r.reportCount > 0 && (
                      <span className="rounded bg-red-50 px-2 py-0.5 text-xs text-red-500">
                        신고 {r.reportCount}건
                      </span>
                    )}
                  </div>
                </div>
                <p className="mt-2 whitespace-pre-line text-sm text-slate-700">{r.content}</p>
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
                    <button
                      onClick={() => changeStatus(r.id, 'VISIBLE')}
                      className="rounded border px-3 py-1 text-xs text-slate-600 hover:bg-slate-50"
                    >
                      복구
                    </button>
                  ) : (
                    <button
                      onClick={() => changeStatus(r.id, 'HIDDEN')}
                      className="rounded border border-red-200 px-3 py-1 text-xs text-red-500 hover:bg-red-50"
                    >
                      숨김 처리
                    </button>
                  )}
                </div>
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
                    i === page ? 'bg-indigo-600 text-white' : 'border bg-white text-slate-600'
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
