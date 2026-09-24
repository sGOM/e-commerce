import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { orderApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import OrderView from '../components/OrderView'
import { payGuestOrder, tossEnabled } from '../lib/payment'
import { useAuth } from '../auth/AuthContext'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent } from '@/components/ui/card'
import type { Order } from '../api/types'

interface LocationState {
  orderNumber?: string
  ordererPhone?: string
  justOrdered?: boolean
}

export default function GuestOrderLookupPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation() as { state?: LocationState }

  const [orderNumber, setOrderNumber] = useState(location.state?.orderNumber ?? '')
  const [phone, setPhone] = useState(location.state?.ordererPhone ?? '')
  const [order, setOrder] = useState<Order | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [claiming, setClaiming] = useState(false)
  const [paying, setPaying] = useState(false)
  const justOrdered = location.state?.justOrdered ?? false

  const lookup = async (num: string, ph: string) => {
    setLoading(true)
    setError(null)
    try {
      setOrder(await orderApi.guestLookup(num, ph))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '조회에 실패했습니다.')
      setOrder(null)
    } finally {
      setLoading(false)
    }
  }

  // 주문 직후 진입하면 자동 조회한다.
  useEffect(() => {
    if (location.state?.orderNumber && location.state?.ordererPhone) {
      lookup(location.state.orderNumber, location.state.ordererPhone)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    lookup(orderNumber, phone)
  }

  // 결제 전(CREATED) 주문은 여기서 결제할 수 있다(주문 직후 결제 실패 시 재시도 경로).
  const pay = async () => {
    if (!order) return
    setPaying(true)
    setError(null)
    try {
      if ((await payGuestOrder(order, phone)) === 'redirected') return
      await lookup(order.orderNumber, phone)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '결제에 실패했습니다.')
    } finally {
      setPaying(false)
    }
  }

  // 로그인 상태면 이 게스트 주문을 내 계정에 연결할 수 있다.
  const claim = async () => {
    if (!order) return
    setClaiming(true)
    setError(null)
    try {
      const claimed = await orderApi.claim(orderNumber, phone)
      navigate(`/orders/${claimed.orderId}`)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '연결에 실패했습니다.')
      setClaiming(false)
    }
  }

  return (
    <div className="mx-auto max-w-xl">
      <h1 className="mb-6 text-xl font-bold">비회원 주문 조회</h1>

      {justOrdered && (
        <div className="mb-6 rounded-xl bg-success/10 p-4 text-center text-sm text-success">
          ✅ 주문이 접수되었습니다. 주문번호로 진행 상태를 확인하세요.
        </div>
      )}

      <Card className="mb-8">
        <CardContent>
          <form onSubmit={submit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="orderNumber">주문번호</Label>
              <Input
                id="orderNumber"
                required
                placeholder="주문번호 (예: ORD-20260629-XXXX)"
                value={orderNumber}
                onChange={(e) => setOrderNumber(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="ordererPhone">연락처</Label>
              <Input
                id="ordererPhone"
                required
                placeholder="주문 시 입력한 연락처"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
              />
            </div>
            {error && (
              <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
                {error}
              </p>
            )}
            <Button type="submit" disabled={loading} className="h-11 w-full">
              {loading ? '조회 중…' : '주문 조회'}
            </Button>
          </form>
        </CardContent>
      </Card>

      {order && (
        <>
          <OrderView order={order} />
          {order.status === 'CREATED' && (
            <Button onClick={pay} disabled={paying} className="mt-6 h-11 w-full">
              {paying ? '결제 중…' : tossEnabled ? '결제하기' : '결제하기 (Mock PG)'}
            </Button>
          )}
          {user && (
            <Button variant="outline" onClick={claim} disabled={claiming} className="mt-6 h-11 w-full">
              {claiming ? '연결 중…' : '이 주문을 내 계정에 연결하기'}
            </Button>
          )}
        </>
      )}
    </div>
  )
}
