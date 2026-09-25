import { useState } from 'react'
import { ApiError, formatKRW } from '../api/client'
import { orderApi } from '../api/endpoints'
import { orderStatusLabel, subOrderStatusLabel } from '../labels'
import type { Order, SubOrder, Tracking } from '../api/types'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'

interface Props {
  order: Order
  /** 하위 주문 수령 확인(구매확정) 콜백. 회원 주문 상세에서만 전달 — 미전달 시 버튼을 노출하지 않는다. */
  onConfirmDelivery?: (subOrderId: number) => void
  confirmingSubOrderId?: number | null
  /** 배송 조회 버튼 노출(회원 본인 주문 상세에서만 — 게스트 조회는 송장번호만 보여준다) */
  trackable?: boolean
}

/** 주문 상세 표시(주문 헤더·판매자별 하위주문·배송지·결제정보). 회원 상세/게스트 조회 공용. */
export default function OrderView({ order, onConfirmDelivery, confirmingSubOrderId, trackable }: Props) {
  return (
    <div className="space-y-6">
      <Card>
        <CardContent>
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-lg font-bold">{order.orderNumber}</h1>
              <p className="text-xs text-muted-foreground">{new Date(order.createdAt).toLocaleString('ko-KR')}</p>
            </div>
            <span className="rounded bg-muted px-2 py-1 text-xs text-muted-foreground">
              {orderStatusLabel[order.status]}
            </span>
          </div>
        </CardContent>
      </Card>

      {order.subOrders.map((sub) => (
        <Card key={sub.subOrderId}>
          <CardContent>
            <div className="mb-3 flex items-center justify-between">
              <p className="text-sm font-semibold">{sub.storeName}</p>
              <span className="rounded bg-primary/10 px-2 py-0.5 text-xs text-primary">
                {subOrderStatusLabel[sub.status]}
              </span>
            </div>
            {sub.deliverySlotId != null && (
              <p className="mb-2 rounded bg-accent px-2 py-1 text-xs text-accent-foreground">
                🕐 배송 슬롯 예약됨
                {sub.deliveryFee > 0 ? ` · 배송비 ${formatKRW(sub.deliveryFee)}` : ' · 배송비 무료'}
              </p>
            )}
            <ul className="space-y-2">
              {sub.items.map((item, idx) => (
                <li key={idx} className="flex justify-between text-sm">
                  <span>
                    {item.productName}{' '}
                    <span className="text-muted-foreground">
                      {item.optionName} · {item.quantity}개
                    </span>
                    {item.appliedSalePrice != null && (
                      <span className="ml-1.5 inline-flex items-center gap-1 rounded bg-destructive/10 px-1.5 py-0.5 text-xs font-semibold text-destructive">
                        ⏰ 특가
                      </span>
                    )}
                  </span>
                  <span className="text-right">
                    {item.appliedSalePrice != null && (
                      <span className="mr-1 text-xs text-muted-foreground line-through">
                        {formatKRW(item.unitPrice * item.quantity)}
                      </span>
                    )}
                    {formatKRW(item.lineTotal)}
                  </span>
                </li>
              ))}
            </ul>
            {sub.trackingNumber && <ShipmentInfo sub={sub} trackable={trackable} />}
            {sub.status === 'SHIPPED' && onConfirmDelivery && (
              <Button
                type="button"
                variant="outline"
                onClick={() => onConfirmDelivery(sub.subOrderId)}
                disabled={confirmingSubOrderId === sub.subOrderId}
                className="mt-3 h-11 w-full"
              >
                {confirmingSubOrderId === sub.subOrderId ? '처리 중…' : '수령 확인(구매확정)'}
              </Button>
            )}
            {sub.status === 'DELIVERED' && onConfirmDelivery && (
              <p className="mt-3 text-center text-xs text-muted-foreground">
                수령 확인 완료 · 마이페이지에서 리뷰를 작성할 수 있어요.
              </p>
            )}
          </CardContent>
        </Card>
      ))}

      <Card>
        <CardContent>
          <h2 className="mb-3 font-bold">
            배송지 {order.isGift && <span className="ml-1 text-sm font-normal text-pink-500">🎁 선물주문</span>}
          </h2>
          {order.isGift ? (
            <div className="space-y-2">
              <p className="text-sm text-muted-foreground">
                선물 주문은 수령자 프라이버시 보호를 위해 배송지를 구매자에게 공개하지 않습니다. 수령자가 공유 링크로
                배송지를 입력하면 배송이 시작됩니다.
              </p>
              {order.giftMessage && (
                <p className="rounded-lg bg-pink-50 p-3 text-sm text-pink-700 dark:bg-pink-950/40 dark:text-pink-300">
                  &ldquo;{order.giftMessage}&rdquo;
                </p>
              )}
            </div>
          ) : order.shippingAddress ? (
            <>
              <p className="text-sm">
                {order.shippingAddress.receiverName} · {order.shippingAddress.receiverPhone}
              </p>
              <p className="text-sm text-muted-foreground">
                ({order.shippingAddress.zipcode}) {order.shippingAddress.address1} {order.shippingAddress.address2}
              </p>
            </>
          ) : (
            <p className="text-sm text-muted-foreground">배송지 정보가 없습니다.</p>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <h2 className="mb-3 font-bold">결제 정보</h2>
          <div className="space-y-1 text-sm text-muted-foreground">
            <div className="flex justify-between">
              <span>상품 합계</span>
              <span>{formatKRW(order.totalAmount)}</span>
            </div>
            {order.discountAmount > 0 && (
              <div className="flex justify-between text-destructive">
                <span>쿠폰 할인</span>
                <span>-{formatKRW(order.discountAmount)}</span>
              </div>
            )}
            {order.pointUsed > 0 && (
              <div className="flex justify-between text-destructive">
                <span>포인트 사용</span>
                <span>-{formatKRW(order.pointUsed)}</span>
              </div>
            )}
            {order.deliveryFeeTotal > 0 && (
              <div className="flex justify-between">
                <span>배송비</span>
                <span>+{formatKRW(order.deliveryFeeTotal)}</span>
              </div>
            )}
            <div className="flex justify-between border-t border-border pt-2 font-bold text-foreground">
              <span>{order.status === 'PAID' ? '최종 결제 금액' : '주문 금액'}</span>
              <span className="text-primary">{formatKRW(order.payableAmount)}</span>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}

/** 송장 정보와(회원이면) 배송 조회 결과(ROADMAP 6.2). */
function ShipmentInfo({ sub, trackable }: { sub: SubOrder; trackable?: boolean }) {
  const [tracking, setTracking] = useState<Tracking | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = async () => {
    setError(null)
    try {
      setTracking(await orderApi.tracking(sub.subOrderId))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '배송 정보를 불러오지 못했습니다.')
    }
  }

  return (
    <div className="mt-3 rounded-lg bg-muted/50 p-3 text-sm">
      <div className="flex items-center justify-between gap-2">
        <span>
          📦 {sub.courier} <span className="font-mono">{sub.trackingNumber}</span>
        </span>
        {trackable && !tracking && (
          <Button type="button" size="sm" variant="outline" onClick={load}>
            배송 조회
          </Button>
        )}
      </div>
      {error && (
        <p role="alert" className="mt-2 text-destructive">
          {error}
        </p>
      )}
      {tracking &&
        (tracking.supported ? (
          <ol className="mt-2 space-y-1 text-xs text-muted-foreground">
            {tracking.events.map((e, i) => (
              <li key={i}>
                {e.time && `${e.time} · `}
                {e.location && `${e.location} · `}
                <span className="text-foreground">{e.description}</span>
              </li>
            ))}
            {tracking.delivered && <li className="font-semibold text-success">배송 완료</li>}
          </ol>
        ) : (
          <p className="mt-2 text-xs text-muted-foreground">
            이 택배사는 조회를 지원하지 않습니다. 택배사 사이트에서 확인해 주세요.
          </p>
        ))}
    </div>
  )
}
