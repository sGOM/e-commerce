import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import { adminDeliverySlotApi, type CreateDeliverySlotBody } from '../../api/endpoints'
import { ApiError, formatKRW } from '../../api/client'
import { deliverySlotTypeLabel } from '../../labels'
import type { DeliverySlot, DeliverySlotType, PageResponse } from '../../api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent } from '@/components/ui/card'
import { cn } from '@/lib/utils'

const typeFilters: (DeliverySlotType | '')[] = ['', 'DAWN', 'DAYTIME']
const selectClass =
  'h-9 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50'

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
          <Input
            type="date"
            aria-label="날짜 필터"
            value={date}
            onChange={(e) => {
              setDate(e.target.value)
              setPage(0)
            }}
            className="w-auto"
          />
          {typeFilters.map((t) => (
            <Button
              key={t || 'all'}
              type="button"
              size="sm"
              variant={type === t ? 'default' : 'outline'}
              className="rounded-full"
              onClick={() => {
                setType(t)
                setPage(0)
              }}
            >
              {t ? deliverySlotTypeLabel[t] : '전체'}
            </Button>
          ))}
        </div>
        <Button onClick={() => setShowForm((v) => !v)}>
          {showForm ? '닫기' : '+ 슬롯 개설'}
        </Button>
      </div>

      {showForm && (
        <CreateSlotForm
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
      {loading ? (
        <p className="py-10 text-center text-sm text-muted-foreground">불러오는 중…</p>
      ) : !data || data.content.length === 0 ? (
        <p className="py-10 text-center text-sm text-muted-foreground">등록된 배송 슬롯이 없습니다.</p>
      ) : (
        <>
          <ul className="space-y-2">
            {data.content.map((slot) => (
              <li key={slot.id}>
                <Card className="flex-row items-center justify-between gap-2 p-4">
                  <div className="min-w-0">
                    <p className="text-sm font-medium">
                      {slot.slotDate} {slot.startTime.slice(0, 5)}~{slot.endTime.slice(0, 5)}
                      <span className="ml-2 rounded bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                        {deliverySlotTypeLabel[slot.type]}
                      </span>
                    </p>
                    <p className="mt-0.5 text-xs text-muted-foreground">
                      예약 {slot.reservedCount}/{slot.capacity} · 잔여 {slot.remaining} ·{' '}
                      {slot.extraFee > 0 ? `추가요금 ${formatKRW(slot.extraFee)}` : '추가요금 없음'} ·{' '}
                      권역 {slot.regionScope ?? '전국'}
                    </p>
                    <p className="mt-0.5 text-xs text-muted-foreground">
                      마감 {new Date(slot.cutoffAt).toLocaleString('ko-KR')}
                    </p>
                  </div>
                  <span
                    className={cn(
                      'shrink-0 rounded px-2 py-0.5 text-xs',
                      slot.expired ? 'bg-muted text-muted-foreground' : 'bg-success/10 text-success',
                    )}
                  >
                    {slot.expired ? '마감' : '모집중'}
                  </span>
                </Card>
              </li>
            ))}
          </ul>

          {data.totalPages > 1 && (
            <div className="flex justify-center gap-1">
              {Array.from({ length: data.totalPages }, (_, i) => (
                <button
                  key={i}
                  onClick={() => setPage(i)}
                  className={cn(
                    'h-8 w-8 rounded text-sm',
                    i === page
                      ? 'bg-primary text-primary-foreground'
                      : 'border border-input bg-background text-muted-foreground',
                  )}
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
    <Card>
      <CardContent>
        <form onSubmit={submit} className="space-y-3">
          <h2 className="font-bold">배송 슬롯 개설</h2>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-1">
              <Label htmlFor="slot-date" className="text-xs text-muted-foreground">
                날짜
              </Label>
              <Input id="slot-date" required type="date" value={slotDate} onChange={(e) => setSlotDate(e.target.value)} />
            </div>
            <div className="space-y-1">
              <Label htmlFor="slot-type" className="text-xs text-muted-foreground">
                유형
              </Label>
              <select
                id="slot-type"
                value={type}
                onChange={(e) => setType(e.target.value as DeliverySlotType)}
                className={selectClass}
              >
                <option value="DAWN">새벽배송</option>
                <option value="DAYTIME">주간배송</option>
              </select>
            </div>
            <div className="space-y-1">
              <Label htmlFor="slot-start" className="text-xs text-muted-foreground">
                시작 시각
              </Label>
              <Input id="slot-start" required type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} />
            </div>
            <div className="space-y-1">
              <Label htmlFor="slot-end" className="text-xs text-muted-foreground">
                종료 시각
              </Label>
              <Input id="slot-end" required type="time" value={endTime} onChange={(e) => setEndTime(e.target.value)} />
            </div>
            <div className="space-y-1 sm:col-span-2">
              <Label htmlFor="slot-cutoff" className="text-xs text-muted-foreground">
                주문 마감 시각
              </Label>
              <Input id="slot-cutoff" required type="datetime-local" value={cutoffAt} onChange={(e) => setCutoffAt(e.target.value)} />
            </div>
            <div className="space-y-1">
              <Label htmlFor="slot-capacity" className="text-xs text-muted-foreground">
                정원
              </Label>
              <Input id="slot-capacity" required type="number" min={1} value={capacity} onChange={(e) => setCapacity(Number(e.target.value))} />
            </div>
            <div className="space-y-1">
              <Label htmlFor="slot-extra-fee" className="text-xs text-muted-foreground">
                추가 배송비(원)
              </Label>
              <Input id="slot-extra-fee" type="number" min={0} value={extraFee} onChange={(e) => setExtraFee(Number(e.target.value))} />
            </div>
            <div className="space-y-1 sm:col-span-2">
              <Label htmlFor="slot-region" className="text-xs text-muted-foreground">
                권역(우편번호 접두사, 비우면 전국 공통)
              </Label>
              <Input
                id="slot-region"
                placeholder="예: 06, 07 (선택)"
                value={regionScope}
                onChange={(e) => setRegionScope(e.target.value)}
              />
            </div>
          </div>
          {error && (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          )}
          <Button type="submit" disabled={submitting}>
            {submitting ? '등록 중…' : '슬롯 등록'}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
