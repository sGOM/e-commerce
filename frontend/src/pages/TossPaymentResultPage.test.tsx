// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import TossPaymentResultPage from './TossPaymentResultPage'
import { orderApi } from '../api/endpoints'

vi.mock('../api/endpoints', () => ({ orderApi: { pay: vi.fn(), payGuest: vi.fn() } }))

const renderAt = (url: string) =>
  render(
    <MemoryRouter initialEntries={[url]}>
      <Routes>
        <Route path="/payments/toss/:result" element={<TossPaymentResultPage />} />
        <Route path="/orders/:id" element={<p>주문 상세</p>} />
      </Routes>
    </MemoryRouter>,
  )

describe('TossPaymentResultPage', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
    sessionStorage.clear()
  })

  const savePending = () =>
    sessionStorage.setItem('tossPendingPayment', JSON.stringify({ orderId: 7, orderNumber: 'ORD-1', amount: 25000 }))

  it('복귀 정보가 일치하면 paymentKey 로 서버 승인 후 주문 상세로 이동한다', async () => {
    savePending()
    vi.mocked(orderApi.pay).mockResolvedValue({ status: 'PAID' } as never)

    renderAt('/payments/toss/success?paymentKey=pk_1&orderId=ORD-1&amount=25000')

    await waitFor(() => expect(orderApi.pay).toHaveBeenCalledWith(7, 'pk_1'))
    expect(await screen.findByText('주문 상세')).toBeTruthy()
    expect(sessionStorage.getItem('tossPendingPayment')).toBeNull()
  })

  it('금액이 요청 때와 다르면 승인하지 않는다', async () => {
    savePending()

    renderAt('/payments/toss/success?paymentKey=pk_1&orderId=ORD-1&amount=100')

    expect(await screen.findByRole('alert')).toBeTruthy()
    expect(orderApi.pay).not.toHaveBeenCalled()
  })

  it('실패 복귀는 토스 메시지를 보여준다', async () => {
    savePending()

    renderAt('/payments/toss/fail?code=PAY_PROCESS_CANCELED&message=사용자가 결제를 취소했습니다')

    expect((await screen.findByRole('alert')).textContent).toContain('사용자가 결제를 취소했습니다')
  })
})
