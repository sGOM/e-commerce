import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { adminShippingPolicyApi, shippingPolicyApi } from '../../api/endpoints'
import { ApiError, formatKRW } from '../../api/client'
import type { ShippingPolicy } from '../../api/types'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/**
 * 배송비·반품 정책(관리자). 기본 배송비는 판매자 단위로 부과되고, 반품 배송비는 단순변심 반품 때 환불에서 빼
 * 판매자에게 정산된다(ROADMAP 7.3). 새 주문·새 반품 요청부터 적용된다(기존 건은 스냅샷 유지).
 */
export default function ShippingPolicyCard() {
  const [form, setForm] = useState<ShippingPolicy | null>(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    shippingPolicyApi
      .get()
      .then(setForm)
      .catch((e) => setError(e instanceof ApiError ? e.message : '배송비 정책을 불러오지 못했습니다.'))
  }, [])

  if (!form) return error ? <p className="text-sm text-destructive">{error}</p> : null

  const save = async (e: React.FormEvent) => {
    e.preventDefault()
    setSaving(true)
    setError(null)
    try {
      setForm(await adminShippingPolicyApi.update(form))
      toast.success('배송비 정책을 저장했습니다. 새 주문·새 반품부터 적용됩니다.')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '저장에 실패했습니다.')
    } finally {
      setSaving(false)
    }
  }

  const field = (key: keyof ShippingPolicy, label: string, hint: string, min: number) => (
    <div className="space-y-1">
      <Label htmlFor={`shipping-${key}`}>{label}</Label>
      <Input
        id={`shipping-${key}`}
        type="number"
        min={min}
        required
        value={form[key]}
        onChange={(e) => setForm({ ...form, [key]: Number(e.target.value) })}
      />
      <p className="text-xs text-muted-foreground">{hint}</p>
    </div>
  )

  return (
    <Card>
      <CardContent>
        <form onSubmit={save} className="space-y-3">
          <h2 className="font-bold">배송비·반품 정책</h2>
          <div className="grid gap-3 sm:grid-cols-3">
            {field('baseFee', '기본 배송비(원)', `판매자마다 부과 · 현재 ${formatKRW(form.baseFee)}`, 0)}
            {field('returnFee', '반품 배송비(원)', '단순변심만 환불에서 차감, 판매자에게 정산', 0)}
            {field('returnWindowDays', '반품 가능 일수', '구매확정 후 이 기간 안에만 반품', 1)}
          </div>
          {error && (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          )}
          <Button type="submit" size="sm" disabled={saving}>
            {saving ? '저장 중…' : '정책 저장'}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
