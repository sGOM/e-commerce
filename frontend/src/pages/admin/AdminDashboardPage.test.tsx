// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import AdminDashboardPage from './AdminDashboardPage'
import { adminDashboardApi } from '../../api/endpoints'

vi.mock('../../api/endpoints', () => ({ adminDashboardApi: { get: vi.fn() } }))

describe('AdminDashboardPage', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('요약 지표와 일별 GMV 막대를 날짜마다 보여준다', async () => {
    vi.mocked(adminDashboardApi.get).mockResolvedValue({
      from: '2026-09-15',
      to: '2026-09-17',
      orderCount: 3,
      gmv: 45000,
      newUserCount: 7,
      daily: [
        { date: '2026-09-15', orderCount: 1, gmv: 15000 },
        { date: '2026-09-16', orderCount: 0, gmv: 0 },
        { date: '2026-09-17', orderCount: 2, gmv: 30000 },
      ],
    })

    render(<AdminDashboardPage />)

    expect(await screen.findByText('7명')).toBeTruthy()
    expect(screen.getByText('3건')).toBeTruthy()
    expect(screen.getAllByRole('listitem')).toHaveLength(3)
    expect(screen.getByRole('listitem', { name: /9월 17일.*2건/ })).toBeTruthy()
  })
})
