// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  addGuestItem,
  clearGuestCart,
  readGuestCart,
  removeGuestItem,
  setGuestItemQuantity,
  subscribeGuestCart,
} from './guestCart'

beforeEach(() => localStorage.clear())

describe('guestCart', () => {
  it('같은 옵션을 다시 담으면 수량을 합산한다', () => {
    addGuestItem(1, 2)
    addGuestItem(2, 1)
    addGuestItem(1, 3)

    expect(readGuestCart()).toEqual([
      { optionId: 1, quantity: 5 },
      { optionId: 2, quantity: 1 },
    ])
  })

  it('수량 변경은 1 미만이면 무시한다', () => {
    addGuestItem(1, 2)

    setGuestItemQuantity(1, 0)
    expect(readGuestCart()).toEqual([{ optionId: 1, quantity: 2 }])

    setGuestItemQuantity(1, 4)
    expect(readGuestCart()).toEqual([{ optionId: 1, quantity: 4 }])
  })

  it('항목 제거와 비우기', () => {
    addGuestItem(1, 1)
    addGuestItem(2, 1)

    removeGuestItem(1)
    expect(readGuestCart()).toEqual([{ optionId: 2, quantity: 1 }])

    clearGuestCart()
    expect(readGuestCart()).toEqual([])
  })

  it('깨지거나 배열이 아닌 저장값은 빈 장바구니로 취급한다', () => {
    localStorage.setItem('guest_cart', '{oops')
    expect(readGuestCart()).toEqual([])

    localStorage.setItem('guest_cart', '{"optionId":1}')
    expect(readGuestCart()).toEqual([])
  })

  it('변경되면 구독자에게 알리고, 구독 해제 후에는 알리지 않는다', () => {
    const listener = vi.fn()
    const unsubscribe = subscribeGuestCart(listener)

    addGuestItem(1, 1)
    expect(listener).toHaveBeenCalledTimes(1)

    unsubscribe()
    addGuestItem(1, 1)
    expect(listener).toHaveBeenCalledTimes(1)
  })
})
