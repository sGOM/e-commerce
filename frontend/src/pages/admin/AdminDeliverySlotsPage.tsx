import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import { adminDeliverySlotApi, type CreateDeliverySlotBody } from '../../api/endpoints'
import { ApiError, formatKRW } from '../../api/client'
import { deliverySlotTypeLabel } from '../../labels'
import type { DeliverySlot, DeliverySlotType, PageResponse } from '../../api/types'

const field = 'w-full rounded-lg border px-3 py-2 text-sm'
const typeFilters: (DeliverySlotType | '')[] = ['', 'DAWN', 'DAYTIME']

/** 관리자 배송 슬롯 관리 — 목록(날짜/유형 필터 + 페이지네이션) + 개설 폼(AC1/AC2). */
export default function AdminDeliverySlotsPage() {
  const [date, setDate] = useState('')
  const [type, setType] = useState<DeliverySlotType | ''>('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<DeliverySlot> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showForm, setShowForm] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setData(await adminDeliverySlotApi.search({ date: date || undefined, type: type || undefined, page }))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '불러오기에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }, [date, type, page])

  useEffect(() => {
    load()
  }, [load])

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2">
          <input
            type="date"
            value={date}
            onChange={(e) => {
              setDate(e.target.value)
              setPage(0)
            }}
            className="rounded-lg border px-3 py-1.5 text-sm"
          />
          {typeFilters.map((t) => (
            <button
              key={t || 'all'}
              onClick={() => {
                setType(t)
                setPage(0)
              }}
              className={`rounded-full px-3 py-1 text-sm ${
                type === t ? 'bg-indigo-600 text-white' : 'border bg-white text-slate-600'
              }`}
            >
              {t ? deliverySlotTypeLabel[t] : '전체'}
            </button>
          ))}
        </div>
        <button
          onClick={() => setShowForm((v) => !v)}
          className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-700"
        >
          {showForm ? '닫기' : '+ 슬롯 개설'}
        </button>
      </div>

      {showForm && (
        <CreateSlotForm
          onCreated={() => {
            setShowForm(false)
            load()
          }}
        />
      )}

      {error && <p className="text-sm text-red-500">{error}</p>}
      {loading ? (
        <p className="py-10 text-center text-slate-400">불러오는 중…</p>
      ) : !data || data.content.length === 0 ? (
        <p className="py-10 text-center text-slate-400">등록된 배송 슬롯이 없습니다.</p>
      ) : (
        <>
          <ul className="space-y-2">
            {data.content.map((slot) => (
              <li key={slot.id} className="flex items-center justify-between gap-2 rounded-xl border bg-white p-4">
                <div className="min-w-0">
                  <p className="text-sm font-medium">
                    {slot.slotDate} {slot.startTime.slice(0, 5)}~{slot.endTime.slice(0, 5)}
                    <span className="ml-2 rounded bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
                      {deliverySlotTypeLabel[slot.type]}
                    </span>
                  </p>
                  <p className="mt-0.5 text-xs text-slate-400">
                    예약 {slot.reservedCount}/{slot.capacity} · 잔여 {slot.remaining} ·{' '}
                    {slot.extraFee > 0 ? `추가요금 ${formatKRW(slot.extraFee)}` : '추가요금 없음'} ·{' '}
                    권역 {slot.regionScope ?? '전국'}
                  </p>
                  <p className="mt-0.5 text-xs text-slate-400">
                    마감 {new Date(slot.cutoffAt).toLocaleString('ko-KR')}
                  </p>
                </div>
                <span
                  className={`shrink-0 rounded px-2 py-0.5 text-xs ${
                    slot.expired ? 'bg-slate-100 text-slate-400' : 'bg-green-50 text-green-600'
                  }`}
                >
                  {slot.expired ? '마감' : '모집중'}
                </span>
              </li>
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

function CreateSlotForm({ onCreated }: { onCreated: () => void }) {
  const [slotDate, setSlotDate] = useState('')
  const [startTime, setStartTime] = useState('')
  const [endTime, setEndTime] = useState('')
  const [type, setType] = useState<DeliverySlotType>('DAWN')
  const [cutoffAt, setCutoffAt] = useState('')
  const [capacity, setCapacity] = useState(50)
  const [regionScope, setRegionScope] = useState('')
  const [extraFee, setExtraFee] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      const body: CreateDeliverySlotBody = {
        slotDate,
        startTime,
        endTime,
        type,
        cutoffAt: new Date(cutoffAt).toISOString(),
        capacity,
        regionScope: regionScope.trim() || undefined,
        extraFee,
      }
      await adminDeliverySlotApi.create(body)
      toast.success('배송 슬롯을 등록했습니다.')
      onCreated()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '등록에 실패했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={submit} className="space-y-3 rounded-xl border bg-white p-5">
      <h2 className="font-bold">배송 슬롯 개설</h2>
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block text-xs text-slate-500">
          날짜
          <input
            required
            type="date"
            value={slotDate}
            onChange={(e) => setSlotDate(e.target.value)}
            className={`mt-1 ${field}`}
          />
        </label>
        <label className="block text-xs text-slate-500">
          유형
          <select value={type} onChange={(e) => setType(e.target.value as DeliverySlotType)} className={`mt-1 ${field}`}>
            <option value="DAWN">새벽배송</option>
            <option value="DAYTIME">주간배송</option>
          </select>
        </label>
        <label className="block text-xs text-slate-500">
          시작 시각
          <input
            required
            type="time"
            value={startTime}
            onChange={(e) => setStartTime(e.target.value)}
            className={`mt-1 ${field}`}
          />
        </label>
        <label className="block text-xs text-slate-500">
          종료 시각
          <input
            required
            type="time"
            value={endTime}
            onChange={(e) => setEndTime(e.target.value)}
            className={`mt-1 ${field}`}
          />
        </label>
        <label className="block text-xs text-slate-500 sm:col-span-2">
          주문 마감 시각
          <input
            required
            type="datetime-local"
            value={cutoffAt}
            onChange={(e) => setCutoffAt(e.target.value)}
            className={`mt-1 ${field}`}
          />
        </label>
        <label className="block text-xs text-slate-500">
          정원
          <input
            required
            type="number"
            min={1}
            value={capacity}
            onChange={(e) => setCapacity(Number(e.target.value))}
            className={`mt-1 ${field}`}
          />
        </label>
        <label className="block text-xs text-slate-500">
          추가 배송비(원)
          <input
            type="number"
            min={0}
            value={extraFee}
            onChange={(e) => setExtraFee(Number(e.target.value))}
            className={`mt-1 ${field}`}
          />
        </label>
        <label className="block text-xs text-slate-500 sm:col-span-2">
          권역(우편번호 접두사, 비우면 전국 공통)
          <input
            placeholder="예: 06, 07 (선택)"
            value={regionScope}
            onChange={(e) => setRegionScope(e.target.value)}
            className={`mt-1 ${field}`}
          />
        </label>
      </div>
      {error && <p className="text-sm text-red-500">{error}</p>}
      <button
        disabled={submitting}
        className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-700 disabled:bg-slate-300"
      >
        {submitting ? '등록 중…' : '슬롯 등록'}
      </button>
    </form>
  )
}
