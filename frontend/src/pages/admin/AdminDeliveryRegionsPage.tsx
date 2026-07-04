import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import { adminDeliveryRegionApi } from '../../api/endpoints'
import { ApiError } from '../../api/client'
import type { DeliveryRegion } from '../../api/types'

const field = 'w-full rounded-lg border px-3 py-2 text-sm'

/** 관리자 새벽배송 가능 지역(우편번호 접두사 화이트리스트) 관리. */
export default function AdminDeliveryRegionsPage() {
  const [regions, setRegions] = useState<DeliveryRegion[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)

  // 등록 폼
  const [prefix, setPrefix] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setRegions(await adminDeliveryRegionApi.list())
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '불러오기에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const create = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await adminDeliveryRegionApi.create({ postalCodePrefix: prefix.trim(), dawnDeliveryAvailable: true })
      setPrefix('')
      toast.success('배송 가능 지역을 등록했습니다.')
      await load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '등록에 실패했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  const toggle = async (region: DeliveryRegion) => {
    setBusyId(region.id)
    setError(null)
    try {
      await adminDeliveryRegionApi.update(region.id, !region.dawnDeliveryAvailable)
      await load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '변경에 실패했습니다.')
    } finally {
      setBusyId(null)
    }
  }

  const remove = async (region: DeliveryRegion) => {
    if (!confirm(`${region.postalCodePrefix} 지역을 삭제하시겠습니까?`)) return
    setBusyId(region.id)
    setError(null)
    try {
      await adminDeliveryRegionApi.remove(region.id)
      toast.success('삭제했습니다.')
      await load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '삭제에 실패했습니다.')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="space-y-4">
      <form onSubmit={create} className="flex gap-2 rounded-xl border bg-white p-5">
        <input
          required
          placeholder="우편번호 접두사 (예: 06)"
          value={prefix}
          onChange={(e) => setPrefix(e.target.value)}
          className={field}
        />
        <button
          disabled={submitting || !prefix.trim()}
          className="shrink-0 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-700 disabled:bg-slate-300"
        >
          {submitting ? '등록 중…' : '지역 추가'}
        </button>
      </form>

      {error && <p className="text-sm text-red-500">{error}</p>}
      {loading ? (
        <p className="py-10 text-center text-slate-400">불러오는 중…</p>
      ) : regions.length === 0 ? (
        <p className="py-10 text-center text-slate-400">등록된 새벽배송 가능 지역이 없습니다.</p>
      ) : (
        <ul className="space-y-2">
          {regions.map((r) => (
            <li key={r.id} className="flex items-center justify-between gap-2 rounded-xl border bg-white p-4">
              <div>
                <p className="text-sm font-medium">{r.postalCodePrefix}</p>
                <p className="text-xs text-slate-400">
                  {r.dawnDeliveryAvailable ? '새벽배송 가능' : '새벽배송 불가'}
                </p>
              </div>
              <div className="flex shrink-0 gap-2">
                <button
                  onClick={() => toggle(r)}
                  disabled={busyId === r.id}
                  className="rounded border px-3 py-1 text-xs text-slate-600 hover:bg-slate-50 disabled:opacity-50"
                >
                  {r.dawnDeliveryAvailable ? '불가로 전환' : '가능으로 전환'}
                </button>
                <button
                  onClick={() => remove(r)}
                  disabled={busyId === r.id}
                  className="rounded border border-red-200 px-3 py-1 text-xs text-red-500 hover:bg-red-50 disabled:opacity-50"
                >
                  삭제
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
