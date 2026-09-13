import { useEffect, useState } from 'react'
import { sellerApi, type SellerDashboard } from '../../api/endpoints'
import { formatKRW } from '../../api/client'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/** Date -> "YYYY-MM-DD" (로컬 타임존 기준) */
function toDateStr(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export default function SellerDashboardPage() {
  const [to, setTo] = useState(() => toDateStr(new Date()))
  const [from, setFrom] = useState(() => toDateStr(new Date(Date.now() - 29 * 86_400_000)))
  const [data, setData] = useState<SellerDashboard | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!from || !to) return
    let active = true
    setError(null)
    sellerApi
      .dashboard(from, to)
      .then((d) => active && setData(d))
      .catch((e) => active && setError(e.message))
    return () => {
      active = false
    }
  }, [from, to])

  const stats = data && [
    { label: '주문 수', value: `${data.orderCount.toLocaleString('ko-KR')}건`, hint: '기간 내 주문 중 결제된 건(취소 제외)' },
    { label: '매출', value: formatKRW(data.salesAmount), hint: '위 주문의 판매액' },
    { label: '정산 전 판매액', value: formatKRW(data.unsettledAmount), hint: '아직 정산서가 만들어지지 않은 판매액' },
    { label: '지급 대기액', value: formatKRW(data.pendingPayoutAmount), hint: '정산 완료, 지급 전(수수료 차감)' },
  ]

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end gap-3">
        <div className="space-y-1">
          <Label htmlFor="dashboard-from" className="text-xs text-muted-foreground">
            시작일
          </Label>
          <Input id="dashboard-from" type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} />
        </div>
        <div className="space-y-1">
          <Label htmlFor="dashboard-to" className="text-xs text-muted-foreground">
            종료일
          </Label>
          <Input id="dashboard-to" type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} />
        </div>
      </div>
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
      {stats ? (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {stats.map((s) => (
            <Card key={s.label}>
              <CardContent>
                <p className="text-sm text-muted-foreground">{s.label}</p>
                <p className="mt-1 text-2xl font-bold tabular-nums">{s.value}</p>
                <p className="mt-1 text-xs text-muted-foreground">{s.hint}</p>
              </CardContent>
            </Card>
          ))}
        </div>
      ) : (
        !error && <p className="py-10 text-center text-sm text-muted-foreground">불러오는 중…</p>
      )}
    </div>
  )
}
