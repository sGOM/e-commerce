// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import AdminUsersPage from './AdminUsersPage'
import { adminUserApi } from '../../api/endpoints'
import type { AdminUser, PageResponse } from '../../api/types'

vi.mock('../../api/endpoints', () => ({
  adminUserApi: { search: vi.fn(), changeStatus: vi.fn(), grantRole: vi.fn(), revokeRole: vi.fn() },
}))

const user = (over: Partial<AdminUser> = {}): AdminUser => ({
  id: 7,
  email: 'buyer@example.com',
  name: '구매자',
  status: 'ACTIVE',
  roles: ['ROLE_USER'],
  createdAt: '2026-09-01T00:00:00Z',
  ...over,
})

const page = (content: AdminUser[]): PageResponse<AdminUser> => ({
  content,
  page: 0,
  size: 20,
  totalElements: content.length,
  totalPages: 1,
  last: true,
})

describe('AdminUsersPage', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('회원을 잠그면 상태 변경 API 를 호출하고 목록을 갱신한다', async () => {
    vi.mocked(adminUserApi.search)
      .mockResolvedValueOnce(page([user()]))
      .mockResolvedValueOnce(page([user({ status: 'LOCKED' })]))
    vi.mocked(adminUserApi.changeStatus).mockResolvedValue(user({ status: 'LOCKED' }))
    vi.stubGlobal('confirm', () => true)

    render(<AdminUsersPage />)
    fireEvent.change(await screen.findByLabelText('buyer@example.com 상태'), { target: { value: 'LOCKED' } })

    expect(adminUserApi.changeStatus).toHaveBeenCalledWith(7, 'LOCKED')
    expect(await screen.findByDisplayValue('잠김')).toBeTruthy()
  })

  it('판매자 역할이 없으면 부여, 있으면 회수한다', async () => {
    vi.mocked(adminUserApi.search).mockResolvedValue(page([user({ roles: ['ROLE_SELLER', 'ROLE_USER'] })]))
    vi.mocked(adminUserApi.revokeRole).mockResolvedValue(user())
    vi.stubGlobal('confirm', () => true)

    render(<AdminUsersPage />)
    fireEvent.click(await screen.findByRole('button', { name: '판매자 회수' }))

    expect(adminUserApi.revokeRole).toHaveBeenCalledWith(7, 'ROLE_SELLER')
    expect(adminUserApi.grantRole).not.toHaveBeenCalled()
  })
})
