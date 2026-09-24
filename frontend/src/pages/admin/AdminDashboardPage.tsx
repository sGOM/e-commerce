import { useEffect, useState } from 'react'
import { adminDashboardApi, type AdminDashboard } from '../../api/endpoints'
import { formatKRW } from '../../api/client'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/** Date -> "YYYY-MM-DD" (로컬 타임존 기준) */
function toDateStr(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

const dayLabel = (date: string) => {
  const [, m, d] = date.split('-').map(Number)
  return `${m}월 ${d}일`
}

/** 관리자 대시보드: 기간 GMV·주문 수·신규 가입 + 일별 GMV 막대. */
export default function AdminDashboardPage() {
  const [to, setTo] = useState(() => toDateStr(new Date()))
  const [from, setFrom] = useState(() => toDateStr(new Date(Date.now() - 29 * 86_400_000)))
  const [data, setData] = useState<AdminDashboard | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!from || !to) return
    let active = true
    setError(null)
    adminDashboardApi
      .get(from, to)
      .then((d) => active && setData(d))
      .catch((e) => active && setError(e.message))
    return () => {
      active = false
    }
  }, [from, to])

  const stats = data && [
    { label: 'GMV', value: formatKRW(data.gmv), hint: '결제된 주문의 상품 판매액(취소 제외, 할인 전)' },
    { label: '주문 수', value: `${data.orderCount.toLocaleString('ko-KR')}건`, hint: '기간 내 결제된 주문' },
    { label: '신규 가입', value: `${data.newUserCount.toLocaleString('ko-KR')}명`, hint: '기간 내 가입한 회원' },
  ]
  const max = data ? Math.max(1, ...data.daily.map((d) => d.gmv)) : 1

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end gap-3">
        <div className="space-y-1">
          <Label htmlFor="admin-dashboard-from" className="text-xs text-muted-foreground">
            시작일
          </Label>
          <Input
            id="admin-dashboard-from"
            type="date"
            value={from}
            max={to}
            onChange={(e) => setFrom(e.target.value)}
          />
        </div>
        <div className="space-y-1">
          <Label htmlFor="admin-dashboard-to" className="text-xs text-muted-foreground">
            종료일
          </Label>
          <Input id="admin-dashboard-to" type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} />
        </div>
      </div>
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
      {data && stats ? (
        <>
          <div className="grid gap-3 sm:grid-cols-3">
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
          <Card>
            <CardContent>
              <p className="mb-3 text-sm font-medium">일별 GMV</p>
              {/* 막대마다 aria-label·title 로 날짜/금액/주문 수를 노출(호버 툴팁 + 스크린리더 표 대용) */}
              <ul className="flex h-40 items-end gap-0.5 border-b border-border">
                {data.daily.map((d) => {
                  const label = `${dayLabel(d.date)} ${formatKRW(d.gmv)} · ${d.orderCount}건`
                  return (
                    <li
                      key={d.date}
                      aria-label={label}
                      title={label}
                      className="group flex h-full flex-1 items-end hover:bg-muted/60"
                    >
                      <div
                        className="w-full rounded-t-[4px] bg-primary group-hover:opacity-80"
                        style={{ height: `${(d.gmv / max) * 100}%` }}
                      />
                    </li>
                  )
                })}
              </ul>
              <div className="mt-1 flex justify-between text-xs text-muted-foreground">
                <span>{dayLabel(data.from)}</span>
                <span>최고 {formatKRW(max === 1 ? 0 : max)}</span>
                <span>{dayLabel(data.to)}</span>
              </div>
            </CardContent>
          </Card>
        </>
      ) : (
        !error && <p className="py-10 text-center text-sm text-muted-foreground">불러오는 중…</p>
      )}
    </div>
  )
}
