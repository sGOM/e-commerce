import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { cartApi } from '../api/endpoints'
import { formatKRW } from '../api/client'
import type { Cart } from '../api/types'

export default function CartPage() {
  const navigate = useNavigate()
  const [cart, setCart] = useState<Cart | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    cartApi
      .get()
      .then(setCart)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  const updateQty = async (itemId: number, quantity: number) => {
    if (quantity < 1) return
    try {
      setCart(await cartApi.updateItem(itemId, quantity))
    } catch (e) {
      setError((e as Error).message)
    }
  }

  const remove = async (itemId: number) => {
    try {
      setCart(await cartApi.removeItem(itemId))
    } catch (e) {
      setError((e as Error).message)
    }
  }

  if (loading) return <p className="py-20 text-center text-slate-400">불러오는 중…</p>

  return (
    <div>
      <h1 className="mb-6 text-xl font-bold">장바구니</h1>
      {error && <p className="mb-4 text-sm text-red-500">{error}</p>}

      {!cart || cart.items.length === 0 ? (
        <div className="py-20 text-center text-slate-400">
          <p>장바구니가 비어 있습니다.</p>
          <Link to="/" className="mt-2 inline-block text-indigo-600">
            상품 보러 가기 →
          </Link>
        </div>
      ) : (
        <div className="grid gap-6 md:grid-cols-3">
          <ul className="space-y-3 md:col-span-2">
            {cart.items.map((item) => (
              <li
                key={item.itemId}
                className="flex items-center gap-4 rounded-xl border bg-white p-4"
              >
                <div className="flex h-16 w-16 items-center justify-center rounded-lg bg-slate-100 text-2xl">
                  🛍️
                </div>
                <div className="flex-1">
                  <p className="text-sm font-medium">{item.productName}</p>
                  <p className="text-xs text-slate-400">{item.optionName}</p>
                  <p className="mt-1 text-sm font-semibold text-indigo-600">
                    {formatKRW(item.unitPrice)}
                  </p>
                  {!item.purchasable && (
                    <p className="text-xs text-red-500">
                      재고 부족(가용 {item.availableStock})
                    </p>
                  )}
                </div>
                <div className="flex items-center gap-1">
                  <button
                    onClick={() => updateQty(item.itemId!, item.quantity - 1)}
                    className="h-7 w-7 rounded border text-slate-600"
                  >
                    −
                  </button>
                  <span className="w-8 text-center text-sm">{item.quantity}</span>
                  <button
                    onClick={() => updateQty(item.itemId!, item.quantity + 1)}
                    className="h-7 w-7 rounded border text-slate-600"
                  >
                    +
                  </button>
                </div>
                <button
                  onClick={() => remove(item.itemId!)}
                  className="text-xs text-slate-400 hover:text-red-500"
                >
                  삭제
                </button>
              </li>
            ))}
          </ul>

          <div className="h-fit rounded-xl border bg-white p-5">
            <div className="flex justify-between text-sm text-slate-600">
              <span>총 수량</span>
              <span>{cart.totalQuantity}개</span>
            </div>
            <div className="mt-2 flex justify-between font-bold">
              <span>합계</span>
              <span className="text-indigo-600">{formatKRW(cart.totalPrice)}</span>
            </div>
            <button
              onClick={() => navigate('/checkout')}
              disabled={cart.items.some((i) => !i.purchasable)}
              className="mt-5 w-full rounded-xl bg-indigo-600 py-3 font-semibold text-white hover:bg-indigo-700 disabled:bg-slate-300"
            >
              주문하기
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
