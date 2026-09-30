// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import NotificationPreferencesCard from './NotificationPreferencesCard'
import { notificationPreferenceApi } from '../api/endpoints'

vi.mock('../api/endpoints', () => ({ notificationPreferenceApi: { get: vi.fn(), update: vi.fn() } }))

const prefs = [
  { type: 'RESTOCK', email: true, push: true },
  { type: 'PRICE_DROP', email: true, push: true },
] as const

describe('NotificationPreferencesCard', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('체크를 끄면 그 종류·채널만 바로 저장한다', async () => {
    vi.mocked(notificationPreferenceApi.get).mockResolvedValue([...prefs])
    vi.mocked(notificationPreferenceApi.update).mockImplementation(async ([changed]) =>
      prefs.map((p) => (p.type === changed.type ? changed : p)),
    )
    render(<NotificationPreferencesCard />)

    fireEvent.click(await screen.findByLabelText('찜한 상품 가격 인하 메일'))

    await waitFor(() =>
      expect(notificationPreferenceApi.update).toHaveBeenCalledWith([{ type: 'PRICE_DROP', email: false, push: true }]),
    )
    expect(screen.getByLabelText('찜한 상품 가격 인하 메일')).toHaveProperty('checked', false)
    expect(screen.getByLabelText('재입고 메일')).toHaveProperty('checked', true)
  })

  it('저장에 실패하면 체크를 되돌린다', async () => {
    vi.mocked(notificationPreferenceApi.get).mockResolvedValue([...prefs])
    vi.mocked(notificationPreferenceApi.update).mockRejectedValue(new Error('network'))
    render(<NotificationPreferencesCard />)

    fireEvent.click(await screen.findByLabelText('재입고 푸시'))

    await waitFor(() => expect(screen.getByLabelText('재입고 푸시')).toHaveProperty('checked', true))
  })
})
