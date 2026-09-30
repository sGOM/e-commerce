// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import ShippingPolicyCard from './ShippingPolicyCard'
import { adminShippingPolicyApi, shippingPolicyApi } from '../../api/endpoints'

vi.mock('../../api/endpoints', () => ({
  shippingPolicyApi: { get: vi.fn() },
  adminShippingPolicyApi: { update: vi.fn() },
}))

describe('ShippingPolicyCard', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('현재 정책을 채워 보여주고, 바꾼 반품 배송비와 기간을 저장한다', async () => {
    vi.mocked(shippingPolicyApi.get).mockResolvedValue({ baseFee: 3_000, returnFee: 6_000, returnWindowDays: 7 })
    vi.mocked(adminShippingPolicyApi.update).mockImplementation(async (body) => body as never)
    render(<ShippingPolicyCard />)

    fireEvent.change(await screen.findByLabelText('반품 배송비(원)'), { target: { value: '5000' } })
    fireEvent.change(screen.getByLabelText('반품 가능 일수'), { target: { value: '14' } })
    fireEvent.click(screen.getByRole('button', { name: '정책 저장' }))

    await waitFor(() =>
      expect(adminShippingPolicyApi.update).toHaveBeenCalledWith({
        baseFee: 3_000,
        returnFee: 5_000,
        returnWindowDays: 14,
      }),
    )
  })
})
