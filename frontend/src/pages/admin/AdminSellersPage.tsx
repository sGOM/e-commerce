import { useCallback, useEffect, useState } from 'react'
import { adminApi } from '../../api/endpoints'
import { ApiError } from '../../api/client'
import type { Seller, SellerStatus } from '../../api/types'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'

const statusLabel: Record<SellerStatus, string> = {
  PENDING: '심사대기',
  ACTIVE: '영업중',
  SUSPENDED: '정지',
}

export default function AdminSellersPage() {
  const [filter, setFilter] = useState<SellerStatus | ''>('PENDING')
  const [sellers, setSellers] = useState<Seller[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setSellers(await adminApi.listSellers(filter || undefined))
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setLoading(false)
    }
  }, [filter])

  useEffect(() => {
    load()
  }, [load])

  const review = async (id: number, approved: boolean) => {
    setError(null)
    try {
      await adminApi.approveSeller(id, approved)
      load()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '처리 실패')
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-2">
        {(['PENDING', 'ACTIVE', 'SUSPENDED', ''] as const).map((s) => (
          <Button
            key={s || 'all'}
            type="button"
            size="sm"
            variant={filter === s ? 'default' : 'outline'}
            className="rounded-full"
            onClick={() => setFilter(s)}
          >
            {s ? statusLabel[s] : '전체'}
          </Button>
        ))}
      </div>

      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
      {loading ? (
        <p className="py-10 text-center text-sm text-muted-foreground">불러오는 중…</p>
      ) : sellers.length === 0 ? (
        <p className="py-10 text-center text-sm text-muted-foreground">해당 상태의 셀러가 없습니다.</p>
      ) : (
        <ul className="space-y-2">
          {sellers.map((s) => (
            <li key={s.sellerId}>
              <Card className="flex-row items-center justify-between p-4">
                <div>
                  <p className="text-sm font-medium">{s.storeName}</p>
                  <p className="text-xs text-muted-foreground">
                    {s.description || '소개 없음'} · userId {s.userId}
                  </p>
                </div>
                {s.status === 'PENDING' ? (
                  <div className="flex gap-2">
                    <Button type="button" size="sm" onClick={() => review(s.sellerId, true)}>
                      승인
                    </Button>
                    <Button type="button" size="sm" variant="outline" onClick={() => review(s.sellerId, false)}>
                      거절
                    </Button>
                  </div>
                ) : (
                  <span className="rounded bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                    {statusLabel[s.status]}
                  </span>
                )}
              </Card>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
