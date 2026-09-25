// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import OrderView from './OrderView'
import { orderApi } from '../api/endpoints'
import type { Order } from '../api/types'

vi.mock('../api/endpoints', () => ({ orderApi: { tracking: vi.fn() } }))

const order = {
  orderId: 1,
  orderNumber: 'ORD-1',
  status: 'PAID',
  totalAmount: 10000,
  discountAmount: 0,
  pointUsed: 0,
  deliveryFeeTotal: 3000,
  payableAmount: 13000,
  ordererName: '구매',
  shippingAddress: null,
  createdAt: '2026-09-25T00:00:00Z',
  isGift: false,
  giftMessage: null,
  subOrders: [
    {
      subOrderId: 9,
      sellerId: 1,
      storeName: '상점',
      status: 'SHIPPED',
      subtotal: 10000,
      deliverySlotId: null,
      deliveryFee: 3000,
      items: [],
      courier: 'CJ대한통운',
      trackingNumber: '555',
    },
  ],
} as unknown as Order

describe('OrderView 배송 조회', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('회원 상세에서는 송장과 배송 조회 이력을 보여준다', async () => {
    vi.mocked(orderApi.tracking).mockResolvedValue({
      courier: 'CJ대한통운',
      trackingNumber: '555',
      supported: true,
      delivered: true,
      events: [{ time: '2026-09-25 14:00', location: '부산', description: '배달완료' }],
    })
    render(<OrderView order={order} trackable />)

    expect(screen.getByText('555')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '배송 조회' }))

    expect(await screen.findByText('배달완료')).toBeTruthy()
    expect(screen.getByText('배송 완료')).toBeTruthy()
    expect(orderApi.tracking).toHaveBeenCalledWith(9)
  })

  it('게스트 조회(trackable 없음)는 송장번호만 보여준다', () => {
    render(<OrderView order={order} />)

    expect(screen.getByText('555')).toBeTruthy()
    expect(screen.queryByRole('button', { name: '배송 조회' })).toBeNull()
  })
})
