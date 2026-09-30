// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import MyMembershipPage from './MyMembershipPage'
import { membershipApi } from '../api/endpoints'

vi.mock('../api/endpoints', () => ({
  membershipApi: { my: vi.fn(), coupons: vi.fn(), claimCoupon: vi.fn(), cancel: vi.fn() },
}))

const activeMembership = {
  status: 'ACTIVE',
  price: 4_900,
  benefitActive: true,
  benefits: { pointEarnMultiplierBp: 15_000 },
  startAt: '2026-09-01T00:00:00Z',
  nextBillingAt: '2026-11-01T00:00:00Z',
}

const coupon = {
  couponId: 7,
  name: '멤버십 데이 3천원',
  discountType: 'FIXED',
  discountValue: 3_000,
  minOrderAmount: 0,
  maxDiscountAmount: null,
  validUntil: '2026-10-31T00:00:00Z',
  claimed: false,
}

describe('MyMembershipPage 멤버십 전용 쿠폰', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('혜택 활성 회원은 전용 쿠폰을 받고, 받은 쿠폰은 받음으로 바뀐다', async () => {
    vi.mocked(membershipApi.my).mockResolvedValue(activeMembership as never)
    vi.mocked(membershipApi.coupons)
      .mockResolvedValueOnce([coupon] as never)
      .mockResolvedValueOnce([{ ...coupon, claimed: true }] as never)
    vi.mocked(membershipApi.claimCoupon).mockResolvedValue(undefined)
    render(<MyMembershipPage />)

    fireEvent.click(await screen.findByRole('button', { name: '받기' }))

    await waitFor(() => expect(membershipApi.claimCoupon).toHaveBeenCalledWith(7))
    expect(await screen.findByRole('button', { name: '받음' })).toHaveProperty('disabled', true)
  })

  it('혜택이 꺼진 회원에게는 쿠폰 목록을 조회하지 않는다', async () => {
    vi.mocked(membershipApi.my).mockResolvedValue({
      ...activeMembership,
      status: 'EXPIRED',
      benefitActive: false,
    } as never)
    render(<MyMembershipPage />)

    expect(await screen.findByText('혜택이 현재 적용되지 않습니다')).toBeTruthy()
    expect(membershipApi.coupons).not.toHaveBeenCalled()
  })
})
