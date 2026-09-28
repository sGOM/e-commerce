// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import LoginPage from './LoginPage'
import { authApi } from '../api/endpoints'

vi.mock('../api/endpoints', () => ({ authApi: { oauth2Providers: vi.fn() } }))
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ login: vi.fn() }) }))

const renderAt = (path = '/login') =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <LoginPage />
    </MemoryRouter>,
  )

describe('LoginPage 소셜 로그인', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('서버에 설정된 제공자만 로그인 버튼으로 보여준다', async () => {
    vi.mocked(authApi.oauth2Providers).mockResolvedValue(['github'])
    renderAt()

    const link = await screen.findByRole('link', { name: 'GitHub로 로그인' })
    expect(link.getAttribute('href')).toBe('/oauth2/authorization/github')
    expect(screen.queryByRole('link', { name: /Google/ })).toBeNull()
  })

  it('설정된 제공자가 없으면 소셜 로그인 버튼이 없다', async () => {
    vi.mocked(authApi.oauth2Providers).mockResolvedValue([])
    renderAt()

    await vi.waitFor(() => expect(authApi.oauth2Providers).toHaveBeenCalled())
    expect(screen.queryByRole('link', { name: /로그인$/ })).toBeNull()
  })

  it('소셜 로그인 실패로 돌아오면 사유를 보여준다', async () => {
    vi.mocked(authApi.oauth2Providers).mockResolvedValue([])
    renderAt(`/login?error=${encodeURIComponent(encodeURIComponent('이메일을 제공받지 못했습니다.'))}`)

    expect((await screen.findByRole('alert')).textContent).toContain('이메일을 제공받지 못했습니다.')
  })
})
