import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { orderApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { clearPendingPayment, readPendingPayment } from '../lib/payment'
import { Card } from '@/components/ui/card'

/**
 * 토스 결제창 복귀 화면(`/payments/toss/success|fail`). 성공이면 쿼리의 주문번호·금액이 결제 요청 때 저장한 값과
 * 같은지 확인한 뒤(공식 가이드 권고) 서버 승인을 요청한다. 금액의 최종 검증은 서버가 주문 금액으로 다시 한다.
 */
export default function TossPaymentResultPage() {
  const { result } = useParams()
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)
  const started = useRef(false) // StrictMode 이중 실행 방지

  const [pending] = useState(readPendingPayment)
  const retryLink = !pending ? '/' : 'orderId' in pending ? `/orders/${pending.orderId}` : '/orders/lookup'
  const retryState = pending && 'ordererPhone' in pending
    ? { orderNumber: pending.orderNumber, ordererPhone: pending.ordererPhone }
    : undefined

  useEffect(() => {
    if (result !== 'success' || started.current) return
    started.current = true
    const paymentKey = params.get('paymentKey')
    if (!pending || !paymentKey || params.get('orderId') !== pending.orderNumber || Number(params.get('amount')) !== pending.amount) {
      setError('결제 정보가 올바르지 않습니다. 주문 내역에서 결제 상태를 확인해 주세요.')
      return
    }
    const confirm = async () => {
      try {
        if ('orderId' in pending) {
          await orderApi.pay(pending.orderId, paymentKey)
          clearPendingPayment()
          navigate(`/orders/${pending.orderId}`, {
            replace: true,
            state: { justPaid: true, giftClaimToken: pending.giftClaimToken },
          })
        } else {
          await orderApi.payGuest(pending.orderNumber, pending.ordererPhone, paymentKey)
          clearPendingPayment()
          navigate('/orders/lookup', {
            replace: true,
            state: { orderNumber: pending.orderNumber, ordererPhone: pending.ordererPhone, justOrdered: true },
          })
        }
      } catch (err) {
        setError(err instanceof ApiError ? err.message : '결제 승인에 실패했습니다.')
      }
    }
    void confirm()
  }, [result, params, pending, navigate])

  const message = result === 'fail' ? params.get('message') ?? '결제가 취소되었습니다.' : error

  if (!message) {
    return <p className="py-20 text-center text-sm text-muted-foreground">결제를 확인하는 중…</p>
  }
  return (
    <Card className="mx-auto max-w-md space-y-4 p-6 text-center">
      <p role="alert" className="text-sm text-destructive">{message}</p>
      <p className="text-xs text-muted-foreground">주문은 저장되어 있어 주문 화면에서 다시 결제할 수 있습니다.</p>
      <Link to={retryLink} state={retryState} className="text-sm font-medium underline">
        주문으로 돌아가기
      </Link>
    </Card>
  )
}
