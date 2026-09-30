// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import OrderReturnCard from './OrderReturnCard'
import { returnApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import type { Order, SubOrderStatus } from '../api/types'

vi.mock('../api/endpoints', () => ({
  returnApi: { mine: vi.fn(), request: vi.fn() },
  shippingPolicyApi: { get: vi.fn().mockResolvedValue({ baseFee: 3_000, returnFee: 6_000, returnWindowDays: 7 }) },
}))

const orderWith = (status: SubOrderStatus) =>
  ({
    orderId: 1,
    subOrders: [{ subOrderId: 10, storeName: '가게', status, items: [] }],
  }) as unknown as Order

describe('OrderReturnCard', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('발송 전 하위 주문만 있으면 카드를 보이지 않는다', async () => {
    vi.mocked(returnApi.mine).mockResolvedValue([])
    render(<OrderReturnCard order={orderWith('PAID')} onRequested={vi.fn()} />)
    await waitFor(() => expect(returnApi.mine).toHaveBeenCalled())
    expect(screen.queryByRole('button', { name: '반품 신청' })).toBeNull()
  })

  it('배송중 하위 주문을 선택한 사유로 반품 신청하고 주문을 새로 불러온다', async () => {
    vi.mocked(returnApi.mine).mockResolvedValue([])
    vi.mocked(returnApi.request).mockResolvedValue({ refundAmount: 17_000 } as never)
    const onRequested = vi.fn()
    render(<OrderReturnCard order={orderWith('SHIPPED')} onRequested={onRequested} />)

    fireEvent.change(screen.getByLabelText('반품 사유'), { target: { value: 'DEFECTIVE' } })
    fireEvent.change(screen.getByLabelText('상세 내용(선택)'), { target: { value: '깨져서 왔어요' } })
    fireEvent.click(screen.getByRole('button', { name: '반품 신청' }))

    await waitFor(() => expect(onRequested).toHaveBeenCalled())
    expect(returnApi.request).toHaveBeenCalledWith(10, 'DEFECTIVE', '깨져서 왔어요')
  })

  it('서버가 거절하면 사유를 보여준다', async () => {
    vi.mocked(returnApi.mine).mockResolvedValue([])
    vi.mocked(returnApi.request).mockRejectedValue(new ApiError('반품 가능 기간이 지났습니다.', 'RETURN-003', 409))
    render(<OrderReturnCard order={orderWith('DELIVERED')} onRequested={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: '반품 신청' }))

    expect(await screen.findByRole('alert')).toHaveProperty('textContent', '반품 가능 기간이 지났습니다.')
  })

  it('정책의 반품 배송비와 반품 기간을 안내한다', async () => {
    vi.mocked(returnApi.mine).mockResolvedValue([])
    render(<OrderReturnCard order={orderWith('SHIPPED')} onRequested={vi.fn()} />)

    expect(await screen.findByText(/반품 배송비 6,000원이 환불액에서 차감됩니다. 구매확정 후 7일/)).toBeTruthy()
  })
})
