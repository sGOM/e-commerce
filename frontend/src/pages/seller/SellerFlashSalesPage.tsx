import { useCallback, useEffect, useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import { productApi, sellerFlashSaleApi } from '../../api/endpoints'
import { ApiError, formatKRW } from '../../api/client'
import { flashSalePhaseLabel } from '../../labels'
import { useCountdown } from '../../hooks/useCountdown'
import type { FlashSale, FlashSalePhase, ProductDetail, ProductSummary, Seller } from '../../api/types'

const phaseBadgeClass: Record<FlashSalePhase, string> = {
  SCHEDULED: 'bg-amber-50 text-amber-600',
  ONGOING: 'bg-green-50 text-green-600',
  ENDED: 'bg-slate-100 text-slate-400',
  CANCELED: 'bg-red-50 text-red-500',
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

  if (loading) return <p className="py-10 text-center text-slate-400">불러오는 중…</p>

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <button
          onClick={() => setShowForm((v) => !v)}
          className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-700"
        >
          {showForm ? '닫기' : '+ 타임딜 등록'}
        </button>
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

      {error && <p className="text-sm text-red-500">{error}</p>}

      {flashSales.length === 0 ? (
        <p className="py-10 text-center text-slate-400">등록한 타임딜이 없습니다.</p>
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
    <li className="rounded-xl border bg-white p-4">
      <div className="flex items-center justify-between gap-2">
        <div className="min-w-0">
          <p className="truncate text-sm font-medium">
            {flashSale.productName} <span className="text-slate-400">{flashSale.optionName}</span>
          </p>
          <p className="mt-0.5 text-xs text-slate-400">
            {formatKRW(flashSale.salePrice)}
            <span className="ml-1 line-through">{formatKRW(flashSale.originalPrice)}</span>
            {' · '}
            {flashSale.soldQuantity}/{flashSale.limitQuantity}개 판매
          </p>
          <p className="mt-0.5 text-xs text-slate-400">
            {new Date(flashSale.startAt).toLocaleString('ko-KR')} ~{' '}
            {new Date(flashSale.endAt).toLocaleString('ko-KR')}
            {flashSale.phase === 'ONGOING' && !ended && (
              <span className="ml-1 font-medium text-indigo-600">· 남은 시간 {label}</span>
            )}
          </p>
        </div>
        <span
          className={`shrink-0 rounded px-2 py-0.5 text-xs ${phaseBadgeClass[flashSale.phase]}`}
        >
          {flashSalePhaseLabel[flashSale.phase]}
        </span>
      </div>
    </li>
  )
}

function CreateFlashSaleForm({
  sellerId,
  onCreated,
}: {
  sellerId: number
  onCreated: () => void
}) {
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

  const field = 'w-full rounded-lg border px-3 py-2 text-sm'

  return (
    <form onSubmit={submit} className="space-y-3 rounded-xl border bg-white p-5">
      <h2 className="font-bold">타임딜 등록</h2>

      <div className="space-y-1">
        <label className="block text-xs text-slate-500">대상 상품</label>
        <select
          required
          value={productId}
          onChange={(e) => setProductId(e.target.value ? Number(e.target.value) : '')}
          className={field}
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
          <label className="block text-xs text-slate-500">대상 옵션</label>
          <select
            required
            value={optionId}
            onChange={(e) => setOptionId(e.target.value ? Number(e.target.value) : '')}
            className={field}
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
        <label className="block text-xs text-slate-500">
          특가(원){selectedOption && ` — 정가 ${formatKRW(selectedOption.price)}보다 낮아야 합니다`}
        </label>
        <input
          required
          type="number"
          min={0}
          value={salePrice}
          onChange={(e) => setSalePrice(Number(e.target.value))}
          className={field}
        />
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <div className="space-y-1">
          <label className="block text-xs text-slate-500">시작 시각</label>
          <input
            required
            type="datetime-local"
            value={startAt}
            onChange={(e) => setStartAt(e.target.value)}
            className={field}
          />
        </div>
        <div className="space-y-1">
          <label className="block text-xs text-slate-500">종료 시각</label>
          <input
            required
            type="datetime-local"
            value={endAt}
            onChange={(e) => setEndAt(e.target.value)}
            className={field}
          />
        </div>
      </div>

      <div className="space-y-1">
        <label className="block text-xs text-slate-500">한도 수량</label>
        <input
          required
          type="number"
          min={1}
          value={limitQuantity}
          onChange={(e) => setLimitQuantity(Number(e.target.value))}
          className={field}
        />
      </div>

      {error && <p className="text-sm text-red-500">{error}</p>}
      <button
        disabled={submitting || optionId === ''}
        className="w-full rounded-xl bg-indigo-600 py-2.5 font-semibold text-white hover:bg-indigo-700 disabled:bg-slate-300"
      >
        {submitting ? '등록 중…' : '타임딜 등록'}
      </button>
    </form>
  )
}
