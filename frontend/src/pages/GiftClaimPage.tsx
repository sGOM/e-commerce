import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { giftApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { giftClaimStatusLabel } from '../labels'
import type { GiftClaim, GiftPreview } from '../api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent } from '@/components/ui/card'

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

  if (loading) return <p className="py-20 text-center text-sm text-muted-foreground">불러오는 중…</p>
  if (error) return <p className="py-20 text-center text-sm text-destructive">{error}</p>
  if (!preview) return null

  const status = claimed?.status ?? preview.status

  return (
    <div className="mx-auto max-w-lg space-y-6">
      <div className="rounded-2xl border border-pink-200 bg-pink-50/60 p-6 text-center dark:border-pink-900 dark:bg-pink-950/30">
        <p className="text-3xl">🎁</p>
        <h1 className="mt-2 text-lg font-bold text-pink-700 dark:text-pink-300">선물이 도착했어요!</h1>
        <p className="mt-1 text-sm text-muted-foreground">{preview.senderName}님이 보낸 선물이에요.</p>
        {preview.giftMessage && (
          <p className="mt-4 rounded-lg bg-card p-4 text-sm text-card-foreground">
            &ldquo;{preview.giftMessage}&rdquo;
          </p>
        )}
      </div>

      <Card>
        <CardContent>
          <h2 className="mb-3 font-bold">주문 상품</h2>
          <ul className="space-y-1.5 text-sm">
            {preview.items.map((item, idx) => (
              <li key={idx} className="flex justify-between">
                <span>
                  {item.productName} <span className="text-muted-foreground">{item.optionName}</span>
                </span>
                <span className="text-muted-foreground">{item.quantity}개</span>
              </li>
            ))}
          </ul>
          <p className="mt-3 text-xs text-muted-foreground">주문번호 {preview.orderNumber}</p>
        </CardContent>
      </Card>

      {status === 'CLAIMED' && (
        <div className="rounded-xl bg-success/10 p-5 text-center text-sm text-success">
          {claimed
            ? '배송지를 등록했습니다! 곧 배송이 시작됩니다. 소중히 받아주세요 🎉'
            : '이미 배송지를 등록해 수락이 완료된 선물이에요.'}
        </div>
      )}
      {status === 'EXPIRED' && (
        <div className="rounded-xl bg-muted p-5 text-center text-sm text-muted-foreground">
          {giftClaimStatusLabel.EXPIRED} — 기한 내에 배송지를 입력하지 않아 선물이 자동으로 취소되고 전액
          환불되었습니다.
        </div>
      )}
      {status === 'CANCELED' && (
        <div className="rounded-xl bg-muted p-5 text-center text-sm text-muted-foreground">
          보내신 분이 선물을 취소했습니다.
        </div>
      )}

      {status === 'PENDING' && (
        <Card>
          <CardContent>
            <form onSubmit={submit} className="space-y-4">
              <h2 className="font-bold">배송지를 입력하고 선물 받기</h2>
              <p className="text-xs text-muted-foreground">
                만료 기한: {new Date(preview.expiresAt).toLocaleString('ko-KR')}까지 · 1회만 등록할 수 있어요.
              </p>
              <div className="grid gap-3 sm:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="receiverName">받는 분 성함</Label>
                  <Input
                    id="receiverName"
                    required
                    placeholder="받는 분 성함"
                    value={form.receiverName}
                    onChange={set('receiverName')}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="receiverPhone">받는 분 연락처</Label>
                  <Input
                    id="receiverPhone"
                    required
                    placeholder="받는 분 연락처"
                    value={form.receiverPhone}
                    onChange={set('receiverPhone')}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="zipcode">우편번호</Label>
                  <Input id="zipcode" required placeholder="우편번호" value={form.zipcode} onChange={set('zipcode')} />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="address1">기본 주소</Label>
                  <Input
                    id="address1"
                    required
                    placeholder="기본 주소"
                    value={form.address1}
                    onChange={set('address1')}
                  />
                </div>
                <div className="space-y-2 sm:col-span-2">
                  <Label htmlFor="address2">상세 주소 (선택)</Label>
                  <Input
                    id="address2"
                    placeholder="상세 주소 (선택)"
                    value={form.address2}
                    onChange={set('address2')}
                  />
                </div>
              </div>
              {claimError && (
                <p role="alert" className="text-sm text-destructive">
                  {claimError}
                </p>
              )}
              <Button
                type="submit"
                disabled={submitting}
                className="h-11 w-full bg-pink-600 text-white hover:bg-pink-700"
              >
                {submitting ? '등록 중…' : '배송지 등록하고 선물 받기'}
              </Button>
            </form>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
