import { orderApi } from '../api/endpoints'
import type { Order } from '../api/types'

/**
 * 결제 진입점. `VITE_TOSS_CLIENT_KEY`(API 개별 연동 클라이언트 키)가 있으면 토스 결제창으로 이동하고,
 * 없으면 기존처럼 즉시 결제(백엔드 Mock PG)한다. 백엔드도 `payment.gateway=toss` 여야 한다.
 *
 * 토스 흐름: 결제창 → successUrl(`/payments/toss/success?paymentKey&orderId&amount`) → 서버 승인(confirm).
 * 리다이렉트로 페이지 상태가 사라지므로 누구의 어떤 주문인지는 sessionStorage 에 남겨 둔다.
 * https://docs.tosspayments.com/sdk/v2/js
 */
const clientKey = import.meta.env.VITE_TOSS_CLIENT_KEY as string | undefined
const SDK_URL = 'https://js.tosspayments.com/v2/standard'
const PENDING_KEY = 'tossPendingPayment'

export const tossEnabled = Boolean(clientKey)

/** 결제창에서 돌아왔을 때 승인에 필요한 정보. 게스트는 연락처로 본인 확인한다. */
export type PendingPayment = { orderNumber: string; amount: number } & (
  | { orderId: number; giftClaimToken?: string | null }
  | { ordererPhone: string }
)

interface TossPaymentsSdk {
  payment(options: { customerKey: string }): {
    requestPayment(request: Record<string, unknown>): Promise<void>
  }
}
type TossPaymentsFactory = ((clientKey: string) => TossPaymentsSdk) & { ANONYMOUS: string }

let sdkPromise: Promise<TossPaymentsFactory> | null = null

function loadSdk(): Promise<TossPaymentsFactory> {
  const loaded = (window as unknown as { TossPayments?: TossPaymentsFactory }).TossPayments
  if (loaded) return Promise.resolve(loaded)
  sdkPromise ??= new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = SDK_URL
    script.onload = () => resolve((window as unknown as { TossPayments: TossPaymentsFactory }).TossPayments)
    script.onerror = () => {
      sdkPromise = null
      reject(new Error('결제 모듈을 불러오지 못했습니다.'))
    }
    document.head.appendChild(script)
  })
  return sdkPromise
}

function orderName(order: Order): string {
  const items = order.subOrders.flatMap((s) => s.items)
  const first = items[0]?.productName ?? '주문 상품'
  return (items.length > 1 ? `${first} 외 ${items.length - 1}건` : first).slice(0, 100)
}

/** 토스 결제창을 연다(페이지 이동). 호출자는 결제창 이탈이 reject 로 올 수도 있다고 보고 처리한다. */
async function requestTossPayment(order: Order, pending: PendingPayment) {
  sessionStorage.setItem(PENDING_KEY, JSON.stringify(pending))
  const TossPayments = await loadSdk()
  await TossPayments(clientKey!)
    .payment({ customerKey: TossPayments.ANONYMOUS })
    .requestPayment({
      method: 'CARD',
      amount: { currency: 'KRW', value: order.payableAmount },
      orderId: order.orderNumber,
      orderName: orderName(order),
      successUrl: `${window.location.origin}/payments/toss/success`,
      failUrl: `${window.location.origin}/payments/toss/fail`,
    })
}

/** 회원 결제. 토스면 결제창으로 이동하므로 반환 후 화면 전환은 Mock 경로에서만 의미가 있다. */
export async function payMemberOrder(order: Order): Promise<'paid' | 'redirected'> {
  if (!tossEnabled) {
    await orderApi.pay(order.orderId)
    return 'paid'
  }
  // 선물 공유 토큰은 주문 생성 응답에만 있어, 리다이렉트 뒤 상세 화면에 넘기려면 함께 보관한다.
  await requestTossPayment(order, {
    orderId: order.orderId,
    orderNumber: order.orderNumber,
    amount: order.payableAmount,
    giftClaimToken: order.giftClaimToken,
  })
  return 'redirected'
}

export async function payGuestOrder(order: Order, ordererPhone: string): Promise<'paid' | 'redirected'> {
  if (!tossEnabled) {
    await orderApi.payGuest(order.orderNumber, ordererPhone)
    return 'paid'
  }
  await requestTossPayment(order, { ordererPhone, orderNumber: order.orderNumber, amount: order.payableAmount })
  return 'redirected'
}

/** 결제창 복귀 시 저장해 둔 결제 정보. 승인이 끝나면 [clearPendingPayment] 로 지운다(재승인은 서버 멱등성이 막는다). */
export function readPendingPayment(): PendingPayment | null {
  try {
    const raw = sessionStorage.getItem(PENDING_KEY)
    return raw ? (JSON.parse(raw) as PendingPayment) : null
  } catch {
    return null
  }
}

export function clearPendingPayment() {
  sessionStorage.removeItem(PENDING_KEY)
}
