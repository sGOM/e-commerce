// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import MyPage from './MyPage'
import { authApi, meApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'

vi.mock('../api/endpoints', () => ({
  authApi: { changePassword: vi.fn(), withdraw: vi.fn() },
  meApi: { coupons: vi.fn(), points: vi.fn() },
}))
vi.mock('../auth/AuthContext', () => ({ useAuth: vi.fn() }))

const refresh = vi.fn()

const renderPage = () => {
  vi.mocked(useAuth).mockReturnValue({ refresh } as never)
  vi.mocked(meApi.coupons).mockResolvedValue([])
  vi.mocked(meApi.points).mockResolvedValue({ balance: 0, transactions: [] } as never)
  vi.stubGlobal(
    'confirm',
    vi.fn(() => true),
  ) // happy-dom 에는 confirm 이 없다
  render(
    <MemoryRouter initialEntries={['/my']}>
      <Routes>
        <Route path="/my" element={<MyPage />} />
        <Route path="/" element={<p>홈 화면</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('MyPage 회원 탈퇴', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('비밀번호로 탈퇴하면 인증 상태를 새로 불러오고 홈으로 이동한다', async () => {
    vi.mocked(authApi.withdraw).mockResolvedValue(undefined)
    renderPage()

    fireEvent.change(await screen.findByLabelText('탈퇴 확인 비밀번호'), { target: { value: 'Passw0rd!' } })
    fireEvent.click(screen.getByRole('button', { name: '회원 탈퇴' }))

    expect(await screen.findByText('홈 화면')).toBeTruthy()
    expect(authApi.withdraw).toHaveBeenCalledWith('Passw0rd!')
    expect(refresh).toHaveBeenCalled()
  })

  it('서버가 거부하면 이유를 보여주고 페이지에 머문다', async () => {
    vi.mocked(authApi.withdraw).mockRejectedValue(
      new ApiError('배송이 끝나지 않은 주문이 있어 탈퇴할 수 없습니다.', 'USER-005', 409),
    )
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: '회원 탈퇴' }))

    await waitFor(() => expect(screen.getByRole('alert').textContent).toContain('배송이 끝나지 않은 주문'))
    expect(refresh).not.toHaveBeenCalled()
  })
})
