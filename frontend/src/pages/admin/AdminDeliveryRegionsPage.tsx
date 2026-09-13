import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import { adminDeliveryRegionApi } from '../../api/endpoints'
import { ApiError } from '../../api/client'
import type { DeliveryRegion } from '../../api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent } from '@/components/ui/card'

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
      <Card>
        <CardContent>
          <form onSubmit={create} className="flex gap-2">
            <Input
              required
              placeholder="우편번호 접두사 (예: 06)"
              aria-label="우편번호 접두사"
              value={prefix}
              onChange={(e) => setPrefix(e.target.value)}
            />
            <Button type="submit" disabled={submitting || !prefix.trim()} className="shrink-0">
              {submitting ? '등록 중…' : '지역 추가'}
            </Button>
          </form>
        </CardContent>
      </Card>

      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
      {loading ? (
        <p className="py-10 text-center text-sm text-muted-foreground">불러오는 중…</p>
      ) : regions.length === 0 ? (
        <p className="py-10 text-center text-sm text-muted-foreground">등록된 새벽배송 가능 지역이 없습니다.</p>
      ) : (
        <ul className="space-y-2">
          {regions.map((r) => (
            <li key={r.id}>
              <Card className="flex-row items-center justify-between gap-2 p-4">
                <div>
                  <p className="text-sm font-medium">{r.postalCodePrefix}</p>
                  <p className="text-xs text-muted-foreground">
                    {r.dawnDeliveryAvailable ? '새벽배송 가능' : '새벽배송 불가'}
                  </p>
                </div>
                <div className="flex shrink-0 gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={busyId === r.id}
                    onClick={() => toggle(r)}
                  >
                    {r.dawnDeliveryAvailable ? '불가로 전환' : '가능으로 전환'}
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={busyId === r.id}
                    onClick={() => remove(r)}
                    className="border-destructive/30 text-destructive hover:bg-destructive/10"
                  >
                    삭제
                  </Button>
                </div>
              </Card>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
