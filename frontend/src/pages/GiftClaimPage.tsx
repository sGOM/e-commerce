import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { giftApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { giftClaimStatusLabel } from '../labels'
import type { GiftClaim, GiftPreview } from '../api/types'

/**
 * 선물 수령 페이지(`/gift/:token`). 로그인 없이 접근 가능 — 토큰이 유일한 인가 수단이다
 * (`docs/planning/gift-order.md` AC6~AC8). 가격 정보는 노출하지 않는다.
 */
export default function GiftClaimPage() {
  const { token } = useParams()

  const [preview, setPreview] = useState<GiftPreview | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [claimed, setClaimed] = useState<GiftClaim | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [claimError, setClaimError] = useState<string | null>(null)

  const [form, setForm] = useState({
    receiverName: '',
    receiverPhone: '',
    zipcode: '',
    address1: '',
    address2: '',
  })

  useEffect(() => {
    if (!token) return
    giftApi
      .preview(token)
      .then(setPreview)
      .catch((e) => setError(e instanceof ApiError ? e.message : '선물 정보를 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
  }, [token])

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm({ ...form, [k]: e.target.value })

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!token) return
    setSubmitting(true)
    setClaimError(null)
    try {
      const result = await giftApi.claim(token, {
        receiverName: form.receiverName,
        receiverPhone: form.receiverPhone,
        zipcode: form.zipcode,
        address1: form.address1,
        address2: form.address2 || undefined,
      })
      setClaimed(result)
    } catch (e) {
      setClaimError(e instanceof ApiError ? e.message : '배송지 등록에 실패했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  const field = 'w-full rounded-lg border px-3 py-2 text-sm'

  if (loading) return <p className="py-20 text-center text-slate-400">불러오는 중…</p>
  if (error) return <p className="py-20 text-center text-red-500">{error}</p>
  if (!preview) return null

  const status = claimed?.status ?? preview.status

  return (
    <div className="mx-auto max-w-lg space-y-6">
      <div className="rounded-2xl border border-pink-200 bg-pink-50/60 p-6 text-center">
        <p className="text-3xl">🎁</p>
        <h1 className="mt-2 text-lg font-bold text-pink-700">선물이 도착했어요!</h1>
        <p className="mt-1 text-sm text-slate-500">{preview.senderName}님이 보낸 선물이에요.</p>
        {preview.giftMessage && (
          <p className="mt-4 rounded-lg bg-white p-4 text-sm text-slate-700">&ldquo;{preview.giftMessage}&rdquo;</p>
        )}
      </div>

      <div className="rounded-xl border bg-white p-5">
        <h2 className="mb-3 font-bold">주문 상품</h2>
        <ul className="space-y-1.5 text-sm">
          {preview.items.map((item, idx) => (
            <li key={idx} className="flex justify-between">
              <span>
                {item.productName} <span className="text-slate-400">{item.optionName}</span>
              </span>
              <span className="text-slate-500">{item.quantity}개</span>
            </li>
          ))}
        </ul>
        <p className="mt-3 text-xs text-slate-400">주문번호 {preview.orderNumber}</p>
      </div>

      {status === 'CLAIMED' && (
        <div className="rounded-xl bg-green-50 p-5 text-center text-sm text-green-700">
          {claimed
            ? '배송지를 등록했습니다! 곧 배송이 시작됩니다. 소중히 받아주세요 🎉'
            : '이미 배송지를 등록해 수락이 완료된 선물이에요.'}
        </div>
      )}
      {status === 'EXPIRED' && (
        <div className="rounded-xl bg-slate-100 p-5 text-center text-sm text-slate-500">
          {giftClaimStatusLabel.EXPIRED} — 기한 내에 배송지를 입력하지 않아 선물이 자동으로 취소되고
          전액 환불되었습니다.
        </div>
      )}
      {status === 'CANCELED' && (
        <div className="rounded-xl bg-slate-100 p-5 text-center text-sm text-slate-500">
          보내신 분이 선물을 취소했습니다.
        </div>
      )}

      {status === 'PENDING' && (
        <form onSubmit={submit} className="space-y-3 rounded-xl border bg-white p-5">
          <h2 className="font-bold">배송지를 입력하고 선물 받기</h2>
          <p className="text-xs text-slate-400">
            만료 기한: {new Date(preview.expiresAt).toLocaleString('ko-KR')}까지 · 1회만 등록할 수 있어요.
          </p>
          <div className="grid gap-3 sm:grid-cols-2">
            <input required placeholder="받는 분 성함" value={form.receiverName} onChange={set('receiverName')} className={field} />
            <input required placeholder="받는 분 연락처" value={form.receiverPhone} onChange={set('receiverPhone')} className={field} />
            <input required placeholder="우편번호" value={form.zipcode} onChange={set('zipcode')} className={field} />
            <input required placeholder="기본 주소" value={form.address1} onChange={set('address1')} className={field} />
            <input placeholder="상세 주소 (선택)" value={form.address2} onChange={set('address2')} className={`${field} sm:col-span-2`} />
          </div>
          {claimError && <p className="text-sm text-red-500">{claimError}</p>}
          <button
            type="submit"
            disabled={submitting}
            className="w-full rounded-xl bg-pink-600 py-3 font-semibold text-white hover:bg-pink-700 disabled:bg-slate-300"
          >
            {submitting ? '등록 중…' : '배송지 등록하고 선물 받기'}
          </button>
        </form>
      )}
    </div>
  )
}
