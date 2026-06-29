import { Link } from 'react-router-dom'
import { formatKRW } from '../api/client'
import { productStatusLabel } from '../labels'
import type { ProductStatus } from '../api/types'

interface Props {
  id: number
  name: string
  basePrice: number
  status: ProductStatus
  storeName: string
  badge?: string // 예: "32개 판매"
}

/** 상품 카드(목록·인기 공용). */
export default function ProductCard({ id, name, basePrice, status, storeName, badge }: Props) {
  return (
    <Link
      to={`/products/${id}`}
      className="relative rounded-xl border bg-white p-4 transition hover:shadow-md"
    >
      {badge && (
        <span className="absolute right-2 top-2 rounded-full bg-indigo-600 px-2 py-0.5 text-xs font-semibold text-white">
          {badge}
        </span>
      )}
      <div className="mb-2 flex aspect-square items-center justify-center rounded-lg bg-slate-100 text-3xl">
        🛍️
      </div>
      <p className="truncate text-xs text-slate-400">{storeName}</p>
      <p className="truncate text-sm font-medium">{name}</p>
      <p className="mt-1 font-bold text-indigo-600">{formatKRW(basePrice)}</p>
      {status !== 'ON_SALE' && (
        <span className="mt-1 inline-block rounded bg-slate-200 px-2 py-0.5 text-xs text-slate-600">
          {productStatusLabel[status]}
        </span>
      )}
    </Link>
  )
}
