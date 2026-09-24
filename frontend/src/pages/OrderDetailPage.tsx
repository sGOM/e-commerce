import { useCallback, useEffect, useState } from 'react'
import { useLocation, useParams } from 'react-router-dom'
import { toast } from 'sonner'
import { orderApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { giftClaimStatusLabel } from '../labels'
import OrderView from '../components/OrderView'
import { payMemberOrder } from '../lib/payment'
import type { GiftClaim, Order } from '../api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'

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
    <div className="rounded-xl border border-pink-200 bg-pink-50/50 p-5 dark:border-pink-900 dark:bg-pink-950/20">
      <h2 className="mb-3 font-bold text-pink-700 dark:text-pink-300">🎁 선물 링크</h2>

      {shareToken && (
        <div className="mb-4 space-y-2 rounded-lg border border-pink-200 bg-card p-3 dark:border-pink-900">
          <p className="text-sm font-medium text-foreground">공유 링크가 발급되었어요. 수령자에게 전달하세요.</p>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Input
              readOnly
              value={shareUrl(shareToken)}
              onFocus={(e) => e.currentTarget.select()}
              className="text-xs sm:flex-1"
            />
            <Button
              type="button"
              onClick={() => copyLink(shareToken)}
              className="shrink-0 bg-pink-600 text-white hover:bg-pink-700"
            >
              링크 복사
            </Button>
          </div>
        </div>
      )}

      {loading ? (
        <p className="text-sm text-muted-foreground">링크 상태 확인 중…</p>
      ) : error ? (
        <p className="text-sm text-destructive">{error}</p>
      ) : claim ? (
        <div className="space-y-2">
          <div className="flex items-center justify-between text-sm">
            <span className="text-muted-foreground">상태</span>
            <span
              className={`rounded px-2 py-0.5 text-xs font-semibold ${
                claim.status === 'CLAIMED'
                  ? 'bg-success/10 text-success'
                  : claim.status === 'PENDING'
                    ? 'bg-warning/10 text-warning'
                    : 'bg-muted text-muted-foreground'
              }`}
            >
              {giftClaimStatusLabel[claim.status]}
            </span>
          </div>
          <p className="text-xs text-muted-foreground">
            만료 기한: {new Date(claim.expiresAt).toLocaleString('ko-KR')}
            {claim.claimedAt && ` · 수락 완료: ${new Date(claim.claimedAt).toLocaleString('ko-KR')}`}
          </p>
          <Button type="button" variant="outline" size="sm" onClick={load}>
            상태 새로고침
          </Button>
        </div>
      ) : null}
    </div>
  )
}

export default function OrderDetailPage() {
  const { id } = useParams()
  const orderId = Number(id)
  const location = useLocation() as {
    state?: { justPaid?: boolean; giftClaimToken?: string | null; payError?: string }
  }

  const [order, setOrder] = useState<Order | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(location.state?.payError ?? null)
  const [canceling, setCanceling] = useState(false)
  const [paying, setPaying] = useState(false)
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

  // 결제 전(CREATED) 주문 재결제 — 토스 결제창에서 돌아오지 못했거나 결제가 실패한 경우의 재시도 경로.
  const pay = async () => {
    if (!order) return
    setPaying(true)
    setError(null)
    try {
      if ((await payMemberOrder(order)) === 'paid') setOrder(await orderApi.detail(orderId))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '결제에 실패했습니다.')
    } finally {
      setPaying(false)
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

  if (loading) return <p className="py-20 text-center text-sm text-muted-foreground">불러오는 중…</p>
  if (error && !order) return <p className="py-20 text-center text-sm text-destructive">{error}</p>
  if (!order) return null

  const cancelable =
    order.status !== 'CANCELED' &&
    !order.subOrders.some((s) => s.status === 'SHIPPED' || s.status === 'DELIVERED')

  return (
    <div className="space-y-6">
      {location.state?.justPaid && (
        <div className="rounded-xl bg-success/10 p-4 text-center text-sm text-success">
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

      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
      {order.status === 'CREATED' && (
        <Button type="button" onClick={pay} disabled={paying} className="h-12 w-full">
          {paying ? '결제 중…' : '결제하기'}
        </Button>
      )}
      {cancelable && (
        <Button
          type="button"
          variant="outline"
          onClick={cancel}
          disabled={canceling}
          className="h-12 w-full border-destructive/30 text-destructive hover:bg-destructive/10"
        >
          {canceling ? '취소 중…' : order.isGift ? '선물 주문 취소' : '주문 취소'}
        </Button>
      )}
    </div>
  )
}
