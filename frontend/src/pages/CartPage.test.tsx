// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import CartPage from './CartPage'
import { cartApi } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import type { Cart, CartItem } from '../api/types'

vi.mock('../api/endpoints', () => ({
  cartApi: { get: vi.fn(), guestPreview: vi.fn(), updateItem: vi.fn(), removeItem: vi.fn() },
}))
vi.mock('../auth/AuthContext', () => ({ useAuth: vi.fn() }))

const item = (over: Partial<CartItem> = {}): CartItem => ({
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
  ...over,
})
const cartOf = (...items: CartItem[]): Cart => ({
  items,
  totalQuantity: items.reduce((s, i) => s + i.quantity, 0),
  totalPrice: items.reduce((s, i) => s + i.unitPrice * i.quantity, 0),
})

const asMember = () => vi.mocked(useAuth).mockReturnValue({ user: { name: '회원' } } as never)
const renderPage = () => render(<CartPage />, { wrapper: MemoryRouter })

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
  localStorage.clear()
})

describe('CartPage', () => {
  it('비회원 장바구니가 비어 있으면 서버 조회 없이 안내를 보여준다', async () => {
    vi.mocked(useAuth).mockReturnValue({ user: null } as never)
    renderPage()

    expect(await screen.findByText('장바구니가 비어 있습니다.')).toBeTruthy()
    expect(cartApi.guestPreview).not.toHaveBeenCalled()
  })

  it('재고가 부족한 항목이 있으면 주문하기를 막는다', async () => {
    asMember()
    vi.mocked(cartApi.get).mockResolvedValue(cartOf(item({ purchasable: false, availableStock: 1 })))
    renderPage()

    expect(await screen.findByText('재고 부족(가용 1)')).toBeTruthy()
    expect((screen.getByRole('button', { name: '주문하기' }) as HTMLButtonElement).disabled).toBe(true)
  })

  it('회원이 수량을 올리면 서버 응답으로 합계를 갱신한다', async () => {
    asMember()
    vi.mocked(cartApi.get).mockResolvedValue(cartOf(item()))
    vi.mocked(cartApi.updateItem).mockResolvedValue(cartOf(item({ quantity: 3 })))
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: '수량 증가' }))

    expect(await screen.findByText('30,000원')).toBeTruthy()
    expect(cartApi.updateItem).toHaveBeenCalledWith(11, 3)
  })
})
