import { useEffect, useState } from 'react'
import { cartApi } from '../api/endpoints'
import { readGuestCart, subscribeGuestCart } from '../cart/guestCart'
import { useAuth } from '../auth/AuthContext'

const CART_EVENT = 'cart-changed'

/** 회원 장바구니 변경 후 헤더 배지를 갱신하도록 알린다. */
export function notifyCartChanged() {
  window.dispatchEvent(new Event(CART_EVENT))
}

/**
 * 헤더 장바구니 배지 개수.
 * - 회원: 서버 장바구니 총 수량(cartApi.get)
 * - 게스트: localStorage 장바구니 수량 합
 * 로딩 중(null)일 때는 배지를 숨겨 플리커를 방지한다.
 */
export function useCartCount(): number | null {
  const { user, loading } = useAuth()
  const [count, setCount] = useState<number | null>(null)

  useEffect(() => {
    if (loading) return
    let alive = true

    if (user) {
      const load = () =>
        cartApi
          .get()
          .then((c) => alive && setCount(c.totalQuantity))
          .catch(() => alive && setCount(0))
      load()
      window.addEventListener(CART_EVENT, load)
      return () => {
        alive = false
        window.removeEventListener(CART_EVENT, load)
      }
    }

    const compute = () =>
      setCount(readGuestCart().reduce((sum, l) => sum + l.quantity, 0))
    compute()
    const unsub = subscribeGuestCart(compute)
    return () => {
      alive = false
      unsub()
    }
  }, [user, loading])

  return count
}
