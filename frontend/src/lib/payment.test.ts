// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Order } from '../api/types'

vi.mock('../api/endpoints', () => ({ orderApi: { pay: vi.fn(), payGuest: vi.fn() } }))

const order = {
  orderId: 7,
  orderNumber: 'ORD-20260924-ABC',
  payableAmount: 25_000,
  subOrders: [{ items: [{ productName: '사과' }, { productName: '배' }] }],
} as unknown as Order

describe('payment', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
    vi.resetModules()
    sessionStorage.clear()
  })

  it('클라이언트 키가 없으면 Mock PG 로 즉시 결제한다', async () => {
    const { payMemberOrder } = await import('./payment')
    const { orderApi } = await import('../api/endpoints')

    expect(await payMemberOrder(order)).toBe('paid')
    expect(orderApi.pay).toHaveBeenCalledWith(7)
  })

  it('클라이언트 키가 있으면 서버 금액·주문번호로 토스 결제창을 열고 복귀용 정보를 남긴다', async () => {
    vi.stubEnv('VITE_TOSS_CLIENT_KEY', 'test_ck_x')
    const requestPayment = vi.fn().mockResolvedValue(undefined)
    const payment = vi.fn(() => ({ requestPayment }))
    vi.stubGlobal('TossPayments', Object.assign(vi.fn(() => ({ payment })), { ANONYMOUS: 'ANON' }))
    const { payMemberOrder, readPendingPayment } = await import('./payment')

    expect(await payMemberOrder(order)).toBe('redirected')

    expect(payment).toHaveBeenCalledWith({ customerKey: 'ANON' })
    expect(requestPayment).toHaveBeenCalledWith(
      expect.objectContaining({
        method: 'CARD',
        amount: { currency: 'KRW', value: 25_000 },
        orderId: 'ORD-20260924-ABC',
        orderName: '사과 외 1건',
      }),
    )
    expect(readPendingPayment()).toEqual({ orderId: 7, orderNumber: 'ORD-20260924-ABC', amount: 25_000 })
  })
})
