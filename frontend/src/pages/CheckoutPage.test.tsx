// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import CheckoutPage from './CheckoutPage'
import { addressApi, cartApi, meApi, membershipApi, orderApi } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { addGuestItem, readGuestCart } from '../cart/guestCart'
import type { Cart, IssuedCoupon, UserAddress } from '../api/types'

vi.mock('../api/endpoints', () => ({
  addressApi: { list: vi.fn() },
  cartApi: { get: vi.fn(), guestPreview: vi.fn() },
  deliverySlotApi: { list: vi.fn() },
  meApi: { coupons: vi.fn(), points: vi.fn() },
  membershipApi: { my: vi.fn() },
  orderApi: { create: vi.fn(), createGuest: vi.fn(), pay: vi.fn() },
}))
vi.mock('../auth/AuthContext', () => ({ useAuth: vi.fn() }))

const cart: Cart = {
  items: [
    {
      itemId: 11,
      optionId: 1,
      productId: 100,
      productName: '티셔츠',
      optionName: 'M',
      unitPrice: 10000,
      quantity: 2,
      lineTotal: 20000,
      availableStock: 10,
      purchasable: true,
      sellerId: 1,
      storeName: '상점',
      dawnDeliveryEligible: false,
    },
  ],
  totalQuantity: 2,
  totalPrice: 20000,
}

const address = (over: Partial<UserAddress>): UserAddress => ({
  addressId: 1,
  label: null,
  isDefault: false,
  receiverName: '수령인',
  receiverPhone: '010-0000-0000',
  zipcode: '00000',
  address1: '주소',
  address2: null,
  ...over,
})

function asMember({ coupons = [] as IssuedCoupon[], addresses = [] as UserAddress[] } = {}) {
  vi.mocked(useAuth).mockReturnValue({ user: { name: '회원', email: 'm@example.com' } } as never)
  vi.mocked(cartApi.get).mockResolvedValue(cart)
  vi.mocked(meApi.coupons).mockResolvedValue(coupons)
  vi.mocked(meApi.points).mockResolvedValue({ balance: 0 } as never)
  vi.mocked(membershipApi.my).mockRejectedValue(new Error('미가입'))
  vi.mocked(addressApi.list).mockResolvedValue(addresses)
}

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/checkout']}>
      <Routes>
        <Route path="/checkout" element={<CheckoutPage />} />
        <Route path="/orders/lookup" element={<p>주문 조회 화면</p>} />
      </Routes>
    </MemoryRouter>,
  )

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
  localStorage.clear()
})

describe('CheckoutPage', () => {
  it('회원은 기본 배송지로 배송지 폼이 미리 채워진다', async () => {
    asMember({
      addresses: [
        address({ addressId: 1, receiverName: '다른 사람', address1: '부산 1' }),
        address({ addressId: 2, isDefault: true, receiverName: '홍길동', address1: '서울 강남구 1' }),
      ],
    })
    renderPage()

    expect(await screen.findByDisplayValue('서울 강남구 1')).toBeTruthy()
    expect((screen.getByLabelText('받는 분') as HTMLInputElement).value).toBe('홍길동')
  })

  it('쿠폰을 고르면 결제 금액 미리보기에 할인이 반영된다', async () => {
    asMember({
      coupons: [
        {
          issuedCouponId: 5,
          name: '10% 할인',
          discountType: 'RATE',
          discountValue: 10,
          minOrderAmount: 0,
          maxDiscountAmount: null,
          validFrom: '2026-01-01T00:00:00Z',
          validUntil: '2099-01-01T00:00:00Z',
          used: false,
        },
      ],
    })
    renderPage()
    await screen.findByRole('option', { name: /10% 할인/ })

    fireEvent.change(screen.getByLabelText('쿠폰'), { target: { value: '5' } })

    expect(await screen.findByText('-2,000원')).toBeTruthy()
    expect(screen.getByText('18,000원')).toBeTruthy()
  })

  it('비회원 주문은 주문 생성 후 장바구니를 비우고 주문 조회로 이동한다', async () => {
    vi.mocked(useAuth).mockReturnValue({ user: null } as never)
    addGuestItem(1, 2)
    vi.mocked(cartApi.guestPreview).mockResolvedValue(cart)
    vi.mocked(orderApi.createGuest).mockResolvedValue({ orderNumber: 'ORD-1' } as never)
    renderPage()
    await screen.findByText('상품 2개')

    const fill = (label: string, value: string) => fireEvent.change(screen.getByLabelText(label), { target: { value } })
    fill('이름', '비회원')
    fill('연락처', '010-1111-2222')
    fill('이메일', 'guest@example.com')
    fill('받는 분', '수령인')
    fill('받는 분 연락처', '010-3333-4444')
    fill('우편번호', '12345')
    fill('기본 주소', '서울 1')
    fireEvent.click(screen.getByRole('button', { name: '비회원 주문하기' }))

    expect(await screen.findByText('주문 조회 화면')).toBeTruthy()
    expect(orderApi.createGuest).toHaveBeenCalledWith(
      expect.objectContaining({
        ordererName: '비회원',
        items: [{ optionId: 1, quantity: 2 }],
        shippingAddress: expect.objectContaining({ zipcode: '12345', address2: undefined }),
      }),
    )
    expect(readGuestCart()).toEqual([])
  })
})
