import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { cartApi, productApi } from '../api/endpoints'
import { ApiError, formatKRW } from '../api/client'
import { addGuestItem } from '../cart/guestCart'
import { useAuth } from '../auth/AuthContext'
import { productStatusLabel } from '../labels'
import type { ProductDetail } from '../api/types'

export default function ProductDetailPage() {
  const { id } = useParams()
  const productId = Number(id)
  const { user } = useAuth()

  const [product, setProduct] = useState<ProductDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [optionId, setOptionId] = useState<number | null>(null)
  const [quantity, setQuantity] = useState(1)
  const [message, setMessage] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    setLoading(true)
    productApi
      .detail(productId)
      .then((p) => {
        setProduct(p)
        setOptionId(p.options[0]?.id ?? null)
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [productId])

  const selected = product?.options.find((o) => o.id === optionId) ?? null

  const addToCart = async () => {
    if (!optionId) return
    setMessage(null)
    setError(null)
    // 비회원은 localStorage 게스트 장바구니에 담는다(서버 저장은 회원 전용).
    if (!user) {
      addGuestItem(optionId, quantity)
      setMessage('장바구니에 담았습니다. (비회원)')
      return
    }
    setSubmitting(true)
    try {
      await cartApi.addItem(optionId, quantity)
      setMessage('장바구니에 담았습니다.')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '담기에 실패했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) return <p className="py-20 text-center text-slate-400">불러오는 중…</p>
  if (error && !product) return <p className="py-20 text-center text-red-500">{error}</p>
  if (!product) return null

  const purchasable =
    product.status === 'ON_SALE' && (selected?.availableStock ?? 0) > 0

  return (
    <div className="grid gap-8 md:grid-cols-2">
      <div className="flex aspect-square items-center justify-center rounded-2xl bg-slate-100 text-6xl">
        🛍️
      </div>

      <div>
        <p className="text-sm text-slate-400">{product.storeName}</p>
        <h1 className="mt-1 text-2xl font-bold">{product.name}</h1>
        {product.status !== 'ON_SALE' && (
          <span className="mt-2 inline-block rounded bg-slate-200 px-2 py-0.5 text-xs text-slate-600">
            {productStatusLabel[product.status]}
          </span>
        )}
        <p className="mt-4 text-2xl font-bold text-indigo-600">
          {formatKRW(selected?.price ?? product.basePrice)}
        </p>

        {product.description && (
          <p className="mt-4 whitespace-pre-line text-sm text-slate-600">
            {product.description}
          </p>
        )}

        <div className="mt-6 space-y-3">
          <label className="block text-sm font-medium">옵션</label>
          <select
            value={optionId ?? ''}
            onChange={(e) => setOptionId(Number(e.target.value))}
            className="w-full rounded-lg border px-3 py-2 text-sm"
          >
            {product.options.map((o) => (
              <option key={o.id} value={o.id} disabled={o.availableStock <= 0}>
                {o.name} · {formatKRW(o.price)}
                {o.availableStock <= 0 ? ' (품절)' : ` (재고 ${o.availableStock})`}
              </option>
            ))}
          </select>

          <div className="flex items-center gap-3">
            <label className="text-sm font-medium">수량</label>
            <input
              type="number"
              min={1}
              max={selected?.availableStock ?? 1}
              value={quantity}
              onChange={(e) => setQuantity(Math.max(1, Number(e.target.value)))}
              className="w-20 rounded-lg border px-3 py-2 text-sm"
            />
          </div>
        </div>

        {message && <p className="mt-4 text-sm text-green-600">{message}</p>}
        {error && product && <p className="mt-4 text-sm text-red-500">{error}</p>}

        <button
          onClick={addToCart}
          disabled={!purchasable || submitting}
          className="mt-6 w-full rounded-xl bg-indigo-600 py-3 font-semibold text-white hover:bg-indigo-700 disabled:bg-slate-300"
        >
          {purchasable ? '장바구니에 담기' : '구매할 수 없는 상품'}
        </button>
      </div>
    </div>
  )
}
