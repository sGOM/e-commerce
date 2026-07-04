import { useCallback, useEffect, useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import { productApi, sellerApi, type CreateOptionBody } from '../../api/endpoints'
import { ApiError, formatKRW } from '../../api/client'
import { productStatusLabel } from '../../labels'
import type { ProductDetail, ProductSummary, Seller } from '../../api/types'

export default function SellerProductsPage() {
  const store = useOutletContext<Seller | null>()
  const sellerId = store?.sellerId

  const [products, setProducts] = useState<ProductSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [expanded, setExpanded] = useState<number | null>(null)

  const load = useCallback(async () => {
    if (sellerId == null) return
    setLoading(true)
    try {
      const page = await productApi.search({ sellerId, size: 100 })
      setProducts(page.content)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setLoading(false)
    }
  }, [sellerId])

  useEffect(() => {
    load()
  }, [load])

  if (loading) return <p className="py-10 text-center text-slate-400">불러오는 중…</p>

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <button
          onClick={() => setShowForm((v) => !v)}
          className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-700"
        >
          {showForm ? '닫기' : '+ 상품 등록'}
        </button>
      </div>

      {showForm && (
        <CreateProductForm
          onCreated={() => {
            setShowForm(false)
            load()
          }}
        />
      )}

      {error && <p className="text-sm text-red-500">{error}</p>}

      {products.length === 0 ? (
        <p className="py-10 text-center text-slate-400">등록한 상품이 없습니다.</p>
      ) : (
        <ul className="space-y-2">
          {products.map((p) => (
            <li key={p.id} className="rounded-xl border bg-white p-4">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium">{p.name}</p>
                  <p className="text-xs text-slate-400">
                    {formatKRW(p.basePrice)} · {productStatusLabel[p.status]}
                  </p>
                </div>
                <button
                  onClick={() => setExpanded(expanded === p.id ? null : p.id)}
                  className="text-sm text-indigo-600"
                >
                  {expanded === p.id ? '닫기' : '재고 관리'}
                </button>
              </div>
              {expanded === p.id && <StockManager productId={p.id} />}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/** 상품 상세를 불러와 옵션별 재고를 절대값으로 조정한다. */
function StockManager({ productId }: { productId: number }) {
  const [detail, setDetail] = useState<ProductDetail | null>(null)
  const [msg, setMsg] = useState<string | null>(null)

  const load = useCallback(() => {
    productApi.detail(productId).then(setDetail)
  }, [productId])
  useEffect(() => {
    load()
  }, [load])

  const adjust = async (optionId: number, quantity: number) => {
    setMsg(null)
    try {
      await sellerApi.adjustStock(productId, optionId, quantity)
      setMsg('재고를 변경했습니다.')
      load()
    } catch (e) {
      setMsg(e instanceof ApiError ? e.message : '변경 실패')
    }
  }

  if (!detail) return <p className="mt-3 text-xs text-slate-400">옵션 불러오는 중…</p>

  return (
    <div className="mt-3 space-y-2 border-t pt-3">
      {detail.options.map((o) => (
        <StockRow key={o.id} option={o} onSave={adjust} />
      ))}
      {msg && <p className="text-xs text-slate-500">{msg}</p>}
    </div>
  )
}

function StockRow({
  option,
  onSave,
}: {
  option: { id: number; name: string; price: number; availableStock: number }
  onSave: (optionId: number, quantity: number) => void
}) {
  const [qty, setQty] = useState(option.availableStock)
  return (
    <div className="flex items-center justify-between gap-2 text-sm">
      <span className="flex-1">
        {option.name}{' '}
        <span className="text-slate-400">{formatKRW(option.price)} · 가용 {option.availableStock}</span>
      </span>
      <input
        type="number"
        min={0}
        value={qty}
        onChange={(e) => setQty(Math.max(0, Number(e.target.value)))}
        className="w-20 rounded border px-2 py-1 text-sm"
      />
      <button
        onClick={() => onSave(option.id, qty)}
        className="rounded border px-3 py-1 text-xs text-slate-600 hover:bg-slate-50"
      >
        저장
      </button>
    </div>
  )
}

const emptyOption = (): CreateOptionBody => ({
  name: '',
  sku: '',
  additionalPrice: 0,
  stockQuantity: 0,
})

function CreateProductForm({ onCreated }: { onCreated: () => void }) {
  const [name, setName] = useState('')
  const [basePrice, setBasePrice] = useState(0)
  const [description, setDescription] = useState('')
  const [status, setStatus] = useState<'DRAFT' | 'ON_SALE'>('ON_SALE')
  const [dawnDeliveryEligible, setDawnDeliveryEligible] = useState(false)
  const [options, setOptions] = useState<CreateOptionBody[]>([emptyOption()])
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const setOpt = (i: number, patch: Partial<CreateOptionBody>) =>
    setOptions((opts) => opts.map((o, idx) => (idx === i ? { ...o, ...patch } : o)))

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await sellerApi.createProduct({
        name,
        basePrice,
        description: description || undefined,
        status,
        dawnDeliveryEligible,
        options,
      })
      onCreated()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '등록 실패')
      setSubmitting(false)
    }
  }

  const field = 'rounded-lg border px-3 py-2 text-sm'

  return (
    <form onSubmit={submit} className="space-y-3 rounded-xl border bg-white p-5">
      <div className="grid gap-3 sm:grid-cols-2">
        <input required placeholder="상품명" value={name} onChange={(e) => setName(e.target.value)} className={field} />
        <input required type="number" min={0} placeholder="기본가" value={basePrice} onChange={(e) => setBasePrice(Number(e.target.value))} className={field} />
      </div>
      <textarea placeholder="설명 (선택)" value={description} onChange={(e) => setDescription(e.target.value)} className={`${field} w-full`} rows={2} />
      <select value={status} onChange={(e) => setStatus(e.target.value as 'DRAFT' | 'ON_SALE')} className={field}>
        <option value="ON_SALE">판매중</option>
        <option value="DRAFT">준비중</option>
      </select>

      <label className="flex items-center gap-2 text-sm text-slate-600">
        <input
          type="checkbox"
          checked={dawnDeliveryEligible}
          onChange={(e) => setDawnDeliveryEligible(e.target.checked)}
        />
        새벽배송 가능 상품
      </label>

      <div className="space-y-2">
        <p className="text-xs font-medium text-slate-500">옵션</p>
        {options.map((o, i) => (
          <div key={i} className="grid grid-cols-2 gap-2 sm:grid-cols-4">
            <input required placeholder="옵션명" value={o.name} onChange={(e) => setOpt(i, { name: e.target.value })} className={field} />
            <input required placeholder="SKU" value={o.sku} onChange={(e) => setOpt(i, { sku: e.target.value })} className={field} />
            <input type="number" min={0} placeholder="추가금" value={o.additionalPrice} onChange={(e) => setOpt(i, { additionalPrice: Number(e.target.value) })} className={field} />
            <input type="number" min={0} placeholder="재고" value={o.stockQuantity} onChange={(e) => setOpt(i, { stockQuantity: Number(e.target.value) })} className={field} />
          </div>
        ))}
        <button type="button" onClick={() => setOptions((o) => [...o, emptyOption()])} className="text-xs text-indigo-600">
          + 옵션 추가
        </button>
      </div>

      {error && <p className="text-sm text-red-500">{error}</p>}
      <button disabled={submitting} className="w-full rounded-xl bg-indigo-600 py-2.5 font-semibold text-white hover:bg-indigo-700 disabled:bg-slate-300">
        {submitting ? '등록 중…' : '상품 등록'}
      </button>
    </form>
  )
}
