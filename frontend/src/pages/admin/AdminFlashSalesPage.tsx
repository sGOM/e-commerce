import { useCallback, useEffect, useState } from 'react'
import { adminFlashSaleApi } from '../../api/endpoints'
import { ApiError, formatKRW } from '../../api/client'
import { flashSalePhaseLabel } from '../../labels'
import { useCountdown } from '../../hooks/useCountdown'
import type { FlashSale, FlashSalePhase, FlashSaleStatus, PageResponse } from '../../api/types'

const statusFilters: (FlashSaleStatus | '')[] = ['', 'ACTIVE', 'CANCELED']
const statusFilterLabel: Record<FlashSaleStatus | '', string> = {
  '': '전체',
  ACTIVE: '정상(진행/예정/종료)',
  CANCELED: '강제종료됨',
}

const phaseBadgeClass: Record<FlashSalePhase, string> = {
  SCHEDULED: 'bg-amber-50 text-amber-600',
  ONGOING: 'bg-green-50 text-green-600',
  ENDED: 'bg-slate-100 text-slate-400',
  CANCELED: 'bg-red-50 text-red-500',
}

/** 관리자 타임딜 편성 — 검색(행정 상태별) + 강제 종료. 실시간 진행 단계(phase)는 목록에서 함께 노출. */
export default function AdminFlashSalesPage() {
  const [status, setStatus] = useState<FlashSaleStatus | ''>('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<FlashSale> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [cancelingId, setCancelingId] = useState<number | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setData(await adminFlashSaleApi.search({ status: status || undefined, page }))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '불러오기에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }, [status, page])

  useEffect(() => {
    load()
  }, [load])

  const cancel = async (id: number) => {
    if (!confirm('이 타임딜을 강제 종료하시겠습니까?')) return
    setCancelingId(id)
    setError(null)
    try {
      await adminFlashSaleApi.cancel(id)
      await load()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '강제 종료에 실패했습니다.')
    } finally {
      setCancelingId(null)
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex gap-2">
        {statusFilters.map((s) => (
          <button
            key={s || 'all'}
            onClick={() => {
              setStatus(s)
              setPage(0)
            }}
            className={`rounded-full px-3 py-1 text-sm ${
              status === s ? 'bg-indigo-600 text-white' : 'border bg-white text-slate-600'
            }`}
          >
            {statusFilterLabel[s]}
          </button>
        ))}
      </div>

      {error && <p className="text-sm text-red-500">{error}</p>}
      {loading ? (
        <p className="py-10 text-center text-slate-400">불러오는 중…</p>
      ) : !data || data.content.length === 0 ? (
        <p className="py-10 text-center text-slate-400">등록된 타임딜이 없습니다.</p>
      ) : (
        <>
          <ul className="space-y-2">
            {data.content.map((fs) => (
              <FlashSaleRow
                key={fs.id}
                flashSale={fs}
                canceling={cancelingId === fs.id}
                onCancel={() => cancel(fs.id)}
              />
            ))}
          </ul>

          {data.totalPages > 1 && (
            <div className="flex justify-center gap-1">
              {Array.from({ length: data.totalPages }, (_, i) => (
                <button
                  key={i}
                  onClick={() => setPage(i)}
                  className={`h-8 w-8 rounded text-sm ${
                    i === page ? 'bg-indigo-600 text-white' : 'border bg-white text-slate-600'
                  }`}
                >
                  {i + 1}
                </button>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  )
}

function FlashSaleRow({
  flashSale,
  canceling,
  onCancel,
}: {
  flashSale: FlashSale
  canceling: boolean
  onCancel: () => void
}) {
  const { label, ended } = useCountdown(flashSale.endAt)
  const cancelable = flashSale.phase === 'SCHEDULED' || flashSale.phase === 'ONGOING'

  return (
    <li className="flex items-center justify-between gap-2 rounded-xl border bg-white p-4">
      <div className="min-w-0">
        <p className="truncate text-sm font-medium">
          {flashSale.productName} <span className="text-slate-400">{flashSale.optionName}</span>
        </p>
        <p className="mt-0.5 truncate text-xs text-slate-400">
          {flashSale.storeName} · {formatKRW(flashSale.salePrice)}
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
      <div className="flex shrink-0 items-center gap-3">
        <span
          className={`rounded px-2 py-0.5 text-xs ${phaseBadgeClass[flashSale.phase]}`}
        >
          {flashSalePhaseLabel[flashSale.phase]}
        </span>
        {cancelable && (
          <button
            onClick={onCancel}
            disabled={canceling}
            className="rounded border border-red-200 px-3 py-1 text-xs text-red-500 hover:bg-red-50 disabled:opacity-50"
          >
            {canceling ? '처리 중…' : '강제 종료'}
          </button>
        )}
      </div>
    </li>
  )
}
