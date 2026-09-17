import { useEffect, useState } from 'react'
import { adminPointApi } from '../../api/endpoints'
import { ApiError } from '../../api/client'
import type { PointPolicy } from '../../api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent } from '@/components/ui/card'

/** 관리자 포인트 운영: 적립 정책(적립률·유효기간) 변경 + 만료 포인트 수동 소멸. */
export default function AdminPointsPage() {
  const [form, setForm] = useState<PointPolicy | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    adminPointApi
      .getPolicy()
      .then(setForm)
      .catch((e) => setError((e as Error).message))
  }, [])

  const run = async (action: () => Promise<string>) => {
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      setMessage(await action())
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '처리 실패')
    } finally {
      setBusy(false)
    }
  }

  const save = (e: React.FormEvent) => {
    e.preventDefault()
    if (!form) return
    run(async () => {
      setForm(await adminPointApi.updatePolicy(form))
      return '정책이 저장되었습니다.'
    })
  }

  const expire = () => {
    if (!confirm('만료일이 지난 포인트를 지금 소멸 처리하시겠습니까?')) return
    run(async () => `${(await adminPointApi.expire()).expiredTotal.toLocaleString()}P 를 소멸 처리했습니다.`)
  }

  return (
    <Card>
      <CardContent className="space-y-4">
        {form ? (
          <form onSubmit={save} className="space-y-3">
            <h3 className="font-semibold">포인트 적립 정책</h3>
            <div className="grid grid-cols-2 gap-3 sm:max-w-md">
              <div className="space-y-1">
                <Label htmlFor="earn-rate" className="text-xs text-muted-foreground">
                  적립률(bp, 100 = 1%)
                </Label>
                <Input
                  id="earn-rate"
                  type="number"
                  min={0}
                  value={form.earnRateBp}
                  onChange={(e) => setForm({ ...form, earnRateBp: Number(e.target.value) })}
                />
              </div>
              <div className="space-y-1">
                <Label htmlFor="expiry-days" className="text-xs text-muted-foreground">
                  유효기간(일)
                </Label>
                <Input
                  id="expiry-days"
                  type="number"
                  min={1}
                  value={form.expiryDays}
                  onChange={(e) => setForm({ ...form, expiryDays: Number(e.target.value) })}
                />
              </div>
            </div>
            <Button type="submit" disabled={busy}>
              정책 저장
            </Button>
          </form>
        ) : (
          !error && <p className="text-sm text-muted-foreground">불러오는 중…</p>
        )}
        <div className="flex items-center gap-3 border-t border-border pt-4">
          <Button type="button" variant="outline" disabled={busy} onClick={expire}>
            만료 포인트 소멸 실행
          </Button>
          <span className="text-xs text-muted-foreground">스케줄러와 같은 작업을 즉시 실행합니다.</span>
        </div>
        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}
        {message && <p className="text-sm text-success">{message}</p>}
      </CardContent>
    </Card>
  )
}
