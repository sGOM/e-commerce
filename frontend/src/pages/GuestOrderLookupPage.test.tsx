// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import GuestOrderLookupPage from './GuestOrderLookupPage'
import { orderApi } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'

vi.mock('../api/endpoints', () => ({ orderApi: { guestLookup: vi.fn(), payGuest: vi.fn(), claim: vi.fn() } }))
vi.mock('../auth/AuthContext', () => ({ useAuth: vi.fn() }))
vi.mock('../components/OrderView', () => ({
  default: ({ order }: { order: { status: string } }) => <p>상태 {order.status}</p>,
}))

describe('GuestOrderLookupPage', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('결제 전 주문은 결제하기로 결제하고 다시 조회한다', async () => {
    vi.mocked(useAuth).mockReturnValue({ user: null } as never)
    vi.mocked(orderApi.guestLookup)
      .mockResolvedValueOnce({ orderNumber: 'ORD-1', status: 'CREATED' } as never)
      .mockResolvedValueOnce({ orderNumber: 'ORD-1', status: 'PAID' } as never)
    vi.mocked(orderApi.payGuest).mockResolvedValue({ status: 'PAID' } as never)
    render(
      <MemoryRouter
        initialEntries={[{ pathname: '/orders/lookup', state: { orderNumber: 'ORD-1', ordererPhone: '010-1' } }]}
      >
        <GuestOrderLookupPage />
      </MemoryRouter>,
    )

    fireEvent.click(await screen.findByRole('button', { name: '결제하기 (Mock PG)' }))

    await waitFor(() => expect(orderApi.payGuest).toHaveBeenCalledWith('ORD-1', '010-1'))
    expect(await screen.findByText('상태 PAID')).toBeTruthy()
    expect(screen.queryByRole('button', { name: '결제하기 (Mock PG)' })).toBeNull()
  })
})
