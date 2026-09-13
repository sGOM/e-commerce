import { useCallback, useEffect, useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import { productApi, sellerApi, type CreateOptionBody } from '../../api/endpoints'
import { ApiError, formatKRW } from '../../api/client'
import { productStatusLabel } from '../../labels'
import type { ProductDetail, ProductSummary, Seller } from '../../api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Card, CardContent } from '@/components/ui/card'

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

  if (loading) return <p className="py-10 text-center text-sm text-muted-foreground">불러오는 중…</p>

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <Button onClick={() => setShowForm((v) => !v)}>
          {showForm ? '닫기' : '+ 상품 등록'}
        </Button>
      </div>

      {showForm && (
        <CreateProductForm
          onCreated={() => {
            setShowForm(false)
            load()
          }}
        />
      )}

      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}

      {products.length === 0 ? (
        <p className="py-10 text-center text-sm text-muted-foreground">등록한 상품이 없습니다.</p>
      ) : (
        <ul className="space-y-2">
          {products.map((p) => (
            <li key={p.id}>
              <Card>
                <CardContent>
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="text-sm font-medium">{p.name}</p>
                      <p className="text-xs text-muted-foreground">
                        {formatKRW(p.basePrice)} · {productStatusLabel[p.status]}
                      </p>
                    </div>
                    <Button
                      type="button"
                      variant="link"
                      className="h-auto p-0 text-sm"
                      onClick={() => setExpanded(expanded === p.id ? null : p.id)}
                    >
                      {expanded === p.id ? '닫기' : '재고 관리'}
                    </Button>
                  </div>
                  {expanded === p.id && <StockManager productId={p.id} />}
                </CardContent>
              </Card>
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

  if (!detail) return <p className="mt-3 text-xs text-muted-foreground">옵션 불러오는 중…</p>

  return (
    <div className="mt-3 space-y-2 border-t border-border pt-3">
      {detail.options.map((o) => (
        <StockRow key={o.id} option={o} onSave={adjust} />
      ))}
      {msg && <p className="text-xs text-muted-foreground">{msg}</p>}
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
        <span className="text-muted-foreground">{formatKRW(option.price)} · 가용 {option.availableStock}</span>
      </span>
      <Input
        type="number"
        min={0}
        value={qty}
        onChange={(e) => setQty(Math.max(0, Number(e.target.value)))}
        className="w-20"
        aria-label={`${option.name} 재고 수량`}
      />
      <Button type="button" variant="outline" size="sm" onClick={() => onSave(option.id, qty)}>
        저장
      </Button>
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

  return (
    <Card>
      <CardContent>
        <form onSubmit={submit} className="space-y-3">
          <div className="grid gap-3 sm:grid-cols-2">
            <Input required placeholder="상품명" aria-label="상품명" value={name} onChange={(e) => setName(e.target.value)} />
            <Input required type="number" min={0} placeholder="기본가" aria-label="기본가" value={basePrice} onChange={(e) => setBasePrice(Number(e.target.value))} />
          </div>
          <Textarea placeholder="설명 (선택)" aria-label="설명" value={description} onChange={(e) => setDescription(e.target.value)} rows={2} />
          <select
            value={status}
            onChange={(e) => setStatus(e.target.value as 'DRAFT' | 'ON_SALE')}
            aria-label="판매 상태"
            className="h-9 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
          >
            <option value="ON_SALE">판매중</option>
            <option value="DRAFT">준비중</option>
          </select>

          <label className="flex items-center gap-2 text-sm text-muted-foreground">
            <input
              type="checkbox"
              checked={dawnDeliveryEligible}
              onChange={(e) => setDawnDeliveryEligible(e.target.checked)}
            />
            새벽배송 가능 상품
          </label>

          <div className="space-y-2">
            <p className="text-xs font-medium text-muted-foreground">옵션</p>
            {options.map((o, i) => (
              <div key={i} className="grid grid-cols-2 gap-2 sm:grid-cols-4">
                <Input required placeholder="옵션명" aria-label="옵션명" value={o.name} onChange={(e) => setOpt(i, { name: e.target.value })} />
                <Input required placeholder="SKU" aria-label="SKU" value={o.sku} onChange={(e) => setOpt(i, { sku: e.target.value })} />
                <Input type="number" min={0} placeholder="추가금" aria-label="추가금" value={o.additionalPrice} onChange={(e) => setOpt(i, { additionalPrice: Number(e.target.value) })} />
                <Input type="number" min={0} placeholder="재고" aria-label="재고" value={o.stockQuantity} onChange={(e) => setOpt(i, { stockQuantity: Number(e.target.value) })} />
              </div>
            ))}
            <Button
              type="button"
              variant="link"
              className="h-auto p-0 text-xs"
              onClick={() => setOptions((o) => [...o, emptyOption()])}
            >
              + 옵션 추가
            </Button>
          </div>

          {error && (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          )}
          <Button type="submit" disabled={submitting} className="w-full">
            {submitting ? '등록 중…' : '상품 등록'}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
