// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import PasswordResetPage from './PasswordResetPage'
import { authApi } from '../api/endpoints'

vi.mock('../api/endpoints', () => ({
  authApi: { requestPasswordReset: vi.fn(), confirmPasswordReset: vi.fn() },
}))

const renderAt = (path: string) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/reset-password" element={<PasswordResetPage />} />
        <Route path="/login" element={<p>로그인 화면</p>} />
      </Routes>
    </MemoryRouter>,
  )

describe('PasswordResetPage', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('토큰이 없으면 메일 요청 폼을 보여주고 계정 존재 여부를 알리지 않는다', async () => {
    vi.mocked(authApi.requestPasswordReset).mockResolvedValue(undefined)
    renderAt('/reset-password')

    fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'a@example.com' } })
    fireEvent.click(screen.getByRole('button', { name: '재설정 메일 받기' }))

    await waitFor(() => expect(authApi.requestPasswordReset).toHaveBeenCalledWith('a@example.com'))
    expect(await screen.findByText(/가입된 주소라면/)).toBeTruthy()
  })

  it('토큰이 있으면 새 비밀번호를 설정하고 로그인 화면으로 보낸다', async () => {
    vi.mocked(authApi.confirmPasswordReset).mockResolvedValue(undefined)
    renderAt('/reset-password?token=tok-1')

    fireEvent.change(screen.getByLabelText('새 비밀번호'), { target: { value: 'newpassword1' } })
    fireEvent.click(screen.getByRole('button', { name: '비밀번호 변경' }))

    await waitFor(() => expect(authApi.confirmPasswordReset).toHaveBeenCalledWith('tok-1', 'newpassword1'))
    expect(await screen.findByText('로그인 화면')).toBeTruthy()
  })
})
