import { useCallback, useEffect, useState } from 'react'
import { useLocation, useParams } from 'react-router-dom'
import { toast } from 'sonner'
import { orderApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { giftClaimStatusLabel } from '../labels'
import OrderView from '../components/OrderView'
import type { GiftClaim, Order } from '../api/types'

/** 선물 링크 공유/상태 카드 — 구매자(본인)의 선물 주문 상세에서만 노출된다. */
function GiftStatusCard({
  order,
  shareToken,
}: {
  order: Order
  /** 주문 생성 직후에만 내려오는 토큰(공유 링크 조립용). 새로고침/재방문 시에는 없다. */
  shareToken?: string | null
}) {
  const [claim, setClaim] = useState<GiftClaim | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setClaim(await orderApi.giftStatus(order.orderId))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '선물 링크 상태를 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }, [order.orderId])

  useEffect(() => {
    load()
  }, [load])

  const shareUrl = (token: string) => `${window.location.origin}/gift/${token}`

  const copyLink = async (token: string) => {
    const url = shareUrl(token)
    try {
      await navigator.clipboard.writeText(url)
      toast.success('공유 링크를 복사했습니다.')
    } catch {
      window.prompt('아래 링크를 복사하세요.', url)
    }
  }

  return (
    <div className="rounded-xl border border-pink-200 bg-pink-50/50 p-5">
      <h2 className="mb-3 font-bold text-pink-700">🎁 선물 링크</h2>

      {shareToken && (
        <div className="mb-4 space-y-2 rounded-lg border border-pink-200 bg-white p-3">
          <p className="text-sm font-medium text-slate-700">공유 링크가 발급되었어요. 수령자에게 전달하세요.</p>
          <div className="flex flex-col gap-2 sm:flex-row">
            <input
              readOnly
              value={shareUrl(shareToken)}
              onFocus={(e) => e.currentTarget.select()}
              className="w-full rounded-lg border px-3 py-2 text-xs text-slate-600 sm:flex-1"
            />
            <button
              type="button"
              onClick={() => copyLink(shareToken)}
              className="shrink-0 rounded-lg bg-pink-600 px-4 py-2 text-sm font-semibold text-white hover:bg-pink-700"
            >
              링크 복사
            </button>
          </div>
        </div>
      )}

      {loading ? (
        <p className="text-sm text-slate-400">링크 상태 확인 중…</p>
      ) : error ? (
        <p className="text-sm text-red-500">{error}</p>
      ) : claim ? (
        <div className="space-y-2">
          <div className="flex items-center justify-between text-sm">
            <span className="text-slate-500">상태</span>
            <span
              className={`rounded px-2 py-0.5 text-xs font-semibold ${
                claim.status === 'CLAIMED'
                  ? 'bg-green-100 text-green-700'
                  : claim.status === 'PENDING'
                    ? 'bg-amber-100 text-amber-700'
                    : 'bg-slate-100 text-slate-500'
              }`}
            >
              {giftClaimStatusLabel[claim.status]}
            </span>
          </div>
          <p className="text-xs text-slate-400">
            만료 기한: {new Date(claim.expiresAt).toLocaleString('ko-KR')}
            {claim.claimedAt && ` · 수락 완료: ${new Date(claim.claimedAt).toLocaleString('ko-KR')}`}
          </p>
          <button
            type="button"
            onClick={load}
            className="rounded-lg border px-3 py-1.5 text-xs font-medium text-slate-600 hover:bg-white"
          >
            상태 새로고침
          </button>
        </div>
      ) : null}
    </div>
  )
}

export default function OrderDetailPage() {
  const { id } = useParams()
  const orderId = Number(id)
  const location = useLocation() as {
    state?: { justPaid?: boolean; giftClaimToken?: string | null }
  }

  const [order, setOrder] = useState<Order | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [canceling, setCanceling] = useState(false)
  const [confirmingSubOrderId, setConfirmingSubOrderId] = useState<number | null>(null)

  useEffect(() => {
    orderApi
      .detail(orderId)
      .then(setOrder)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [orderId])

  const confirmDelivery = async (subOrderId: number) => {
    if (!confirm('상품을 수령하셨나요? 확인 후에는 되돌릴 수 없으며, 리뷰를 작성할 수 있게 됩니다.'))
      return
    setConfirmingSubOrderId(subOrderId)
    setError(null)
    try {
      setOrder(await orderApi.confirmDelivery(subOrderId))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '수령 확인에 실패했습니다.')
    } finally {
      setConfirmingSubOrderId(null)
    }
  }

  const cancel = async () => {
    const confirmMsg = order?.isGift
      ? '선물 주문을 취소하시겠습니까? 결제 금액이 전액 환불됩니다.'
      : '주문을 취소하시겠습니까?'
    if (!confirm(confirmMsg)) return
    setCanceling(true)
    setError(null)
    try {
      // 선물 주문은 전용 엔드포인트를 사용한다(수락 전 링크를 함께 마감, AC11).
      setOrder(await (order?.isGift ? orderApi.cancelGift(orderId) : orderApi.cancel(orderId)))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '취소에 실패했습니다.')
    } finally {
      setCanceling(false)
    }
  }

  if (loading) return <p className="py-20 text-center text-slate-400">불러오는 중…</p>
  if (error && !order) return <p className="py-20 text-center text-red-500">{error}</p>
  if (!order) return null

  const cancelable =
    order.status !== 'CANCELED' &&
    !order.subOrders.some((s) => s.status === 'SHIPPED' || s.status === 'DELIVERED')

  return (
    <div className="space-y-6">
      {location.state?.justPaid && (
        <div className="rounded-xl bg-green-50 p-4 text-center text-sm text-green-700">
          🎉 결제가 완료되었습니다!
        </div>
      )}

      {order.isGift && (
        <GiftStatusCard order={order} shareToken={location.state?.giftClaimToken} />
      )}

      <OrderView
        order={order}
        onConfirmDelivery={confirmDelivery}
        confirmingSubOrderId={confirmingSubOrderId}
      />

      {error && <p className="text-sm text-red-500">{error}</p>}
      {cancelable && (
        <button
          onClick={cancel}
          disabled={canceling}
          className="w-full rounded-xl border border-red-200 py-3 text-sm font-semibold text-red-500 hover:bg-red-50 disabled:opacity-50"
        >
          {canceling ? '취소 중…' : order.isGift ? '선물 주문 취소' : '주문 취소'}
        </button>
      )}
    </div>
  )
}
