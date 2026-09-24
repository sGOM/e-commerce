import { useCallback, useEffect, useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import { productApi, sellerFlashSaleApi } from '../../api/endpoints'
import { ApiError, formatKRW } from '../../api/client'
import { flashSalePhaseLabel } from '../../labels'
import { useCountdown } from '../../hooks/useCountdown'
import type { FlashSale, FlashSalePhase, ProductDetail, ProductSummary, Seller } from '../../api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent } from '@/components/ui/card'

const phaseBadgeClass: Record<FlashSalePhase, string> = {
  SCHEDULED: 'bg-warning/10 text-warning',
  ONGOING: 'bg-success/10 text-success',
  ENDED: 'bg-muted text-muted-foreground',
  CANCELED: 'bg-destructive/10 text-destructive',
}

/** 판매자 타임딜 백오피스 — 내 타임딜 목록 + 등록 폼(옵션/특가/기간/한도수량). 가격 원가/셀러는 서버가 도출. */
export default function SellerFlashSalesPage() {
  const store = useOutletContext<Seller | null>()
  const sellerId = store?.sellerId

  const [flashSales, setFlashSales] = useState<FlashSale[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showForm, setShowForm] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setFlashSales(await sellerFlashSaleApi.list())
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '불러오기에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  if (loading) return <p className="py-10 text-center text-sm text-muted-foreground">불러오는 중…</p>

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <Button onClick={() => setShowForm((v) => !v)}>{showForm ? '닫기' : '+ 타임딜 등록'}</Button>
      </div>

      {showForm && sellerId != null && (
        <CreateFlashSaleForm
          sellerId={sellerId}
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

      {flashSales.length === 0 ? (
        <p className="py-10 text-center text-sm text-muted-foreground">등록한 타임딜이 없습니다.</p>
      ) : (
        <ul className="space-y-2">
          {flashSales.map((fs) => (
            <FlashSaleRow key={fs.id} flashSale={fs} />
          ))}
        </ul>
      )}
    </div>
  )
}

function FlashSaleRow({ flashSale }: { flashSale: FlashSale }) {
  const { label, ended } = useCountdown(flashSale.endAt)
  return (
    <li>
      <Card>
        <CardContent>
          <div className="flex items-center justify-between gap-2">
            <div className="min-w-0">
              <p className="truncate text-sm font-medium">
                {flashSale.productName} <span className="text-muted-foreground">{flashSale.optionName}</span>
              </p>
              <p className="mt-0.5 text-xs text-muted-foreground">
                {formatKRW(flashSale.salePrice)}
                <span className="ml-1 line-through">{formatKRW(flashSale.originalPrice)}</span>
                {' · '}
                {flashSale.soldQuantity}/{flashSale.limitQuantity}개 판매
              </p>
              <p className="mt-0.5 text-xs text-muted-foreground">
                {new Date(flashSale.startAt).toLocaleString('ko-KR')} ~{' '}
                {new Date(flashSale.endAt).toLocaleString('ko-KR')}
                {flashSale.phase === 'ONGOING' && !ended && (
                  <span className="ml-1 font-medium text-primary">· 남은 시간 {label}</span>
                )}
              </p>
            </div>
            <span className={`shrink-0 rounded px-2 py-0.5 text-xs ${phaseBadgeClass[flashSale.phase]}`}>
              {flashSalePhaseLabel[flashSale.phase]}
            </span>
          </div>
        </CardContent>
      </Card>
    </li>
  )
}

function CreateFlashSaleForm({ sellerId, onCreated }: { sellerId: number; onCreated: () => void }) {
  const [products, setProducts] = useState<ProductSummary[]>([])
  const [productId, setProductId] = useState<number | ''>('')
  const [productDetail, setProductDetail] = useState<ProductDetail | null>(null)
  const [optionId, setOptionId] = useState<number | ''>('')
  const [salePrice, setSalePrice] = useState(0)
  const [startAt, setStartAt] = useState('')
  const [endAt, setEndAt] = useState('')
  const [limitQuantity, setLimitQuantity] = useState(10)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    productApi
      .search({ sellerId, size: 100 })
      .then((page) => setProducts(page.content))
      .catch(() => setProducts([]))
  }, [sellerId])

  useEffect(() => {
    if (productId === '') {
      setProductDetail(null)
      setOptionId('')
      return
    }
    productApi.detail(productId).then((d) => {
      setProductDetail(d)
      setOptionId(d.options[0]?.id ?? '')
    })
  }, [productId])

  const selectedOption = productDetail?.options.find((o) => o.id === optionId)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (optionId === '') return
    setSubmitting(true)
    setError(null)
    try {
      await sellerFlashSaleApi.create({
        productOptionId: optionId,
        salePrice,
        startAt: new Date(startAt).toISOString(),
        endAt: new Date(endAt).toISOString(),
        limitQuantity,
      })
      onCreated()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '등록에 실패했습니다.')
      setSubmitting(false)
    }
  }

  const selectClass =
    'h-9 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50'

  return (
    <Card>
      <CardContent>
        <form onSubmit={submit} className="space-y-3">
          <h2 className="font-bold">타임딜 등록</h2>

          <div className="space-y-1">
            <Label htmlFor="fs-product" className="text-xs text-muted-foreground">
              대상 상품
            </Label>
            <select
              id="fs-product"
              required
              value={productId}
              onChange={(e) => setProductId(e.target.value ? Number(e.target.value) : '')}
              className={selectClass}
            >
              <option value="">상품 선택</option>
              {products.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </div>

          {productDetail && (
            <div className="space-y-1">
              <Label htmlFor="fs-option" className="text-xs text-muted-foreground">
                대상 옵션
              </Label>
              <select
                id="fs-option"
                required
                value={optionId}
                onChange={(e) => setOptionId(e.target.value ? Number(e.target.value) : '')}
                className={selectClass}
              >
                {productDetail.options.map((o) => (
                  <option key={o.id} value={o.id}>
                    {o.name} · 정가 {formatKRW(o.price)} (재고 {o.availableStock})
                  </option>
                ))}
              </select>
            </div>
          )}

          <div className="space-y-1">
            <Label htmlFor="fs-price" className="text-xs text-muted-foreground">
              특가(원){selectedOption && ` — 정가 ${formatKRW(selectedOption.price)}보다 낮아야 합니다`}
            </Label>
            <Input
              id="fs-price"
              required
              type="number"
              min={0}
              value={salePrice}
              onChange={(e) => setSalePrice(Number(e.target.value))}
            />
          </div>

          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-1">
              <Label htmlFor="fs-start" className="text-xs text-muted-foreground">
                시작 시각
              </Label>
              <Input
                id="fs-start"
                required
                type="datetime-local"
                value={startAt}
                onChange={(e) => setStartAt(e.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="fs-end" className="text-xs text-muted-foreground">
                종료 시각
              </Label>
              <Input
                id="fs-end"
                required
                type="datetime-local"
                value={endAt}
                onChange={(e) => setEndAt(e.target.value)}
              />
            </div>
          </div>

          <div className="space-y-1">
            <Label htmlFor="fs-limit" className="text-xs text-muted-foreground">
              한도 수량
            </Label>
            <Input
              id="fs-limit"
              required
              type="number"
              min={1}
              value={limitQuantity}
              onChange={(e) => setLimitQuantity(Number(e.target.value))}
            />
          </div>

          {error && (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          )}
          <Button type="submit" disabled={submitting || optionId === ''} className="w-full">
            {submitting ? '등록 중…' : '타임딜 등록'}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
