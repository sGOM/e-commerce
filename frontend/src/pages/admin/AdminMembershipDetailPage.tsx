import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { adminMembershipApi } from '../../api/endpoints'
import { ApiError, formatKRW } from '../../api/client'
import { billingHistoryStatusLabel, membershipStatusLabel } from '../../labels'
import type { AdminMembership, MembershipBillingHistory, MembershipStatus } from '../../api/types'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

function statusBadgeVariant(status: MembershipStatus): 'default' | 'secondary' | 'destructive' | 'outline' {
  if (status === 'ACTIVE') return 'default'
  if (status === 'PAST_DUE') return 'destructive'
  if (status === 'CANCELED') return 'secondary'
  return 'outline'
}

export default function AdminMembershipDetailPage() {
  const { id } = useParams<{ id: string }>()
  const membershipId = Number(id)
  const [membership, setMembership] = useState<AdminMembership | null>(null)
  const [histories, setHistories] = useState<MembershipBillingHistory[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [m, h] = await Promise.all([
        adminMembershipApi.detail(membershipId),
        adminMembershipApi.billingHistories(membershipId),
      ])
      setMembership(m)
      setHistories(h)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '불러오기에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }, [membershipId])

  useEffect(() => {
    load()
  }, [load])

  return (
    <div className="max-w-2xl space-y-6">
      <Link to="/admin/memberships" className="text-sm text-muted-foreground hover:underline">
        ← 목록으로
      </Link>

      {loading ? (
        <p className="py-10 text-center text-muted-foreground">불러오는 중…</p>
      ) : error ? (
        <p className="py-10 text-center text-destructive">{error}</p>
      ) : membership ? (
        <>
          <Card>
            <CardHeader className="flex-row items-center justify-between space-y-0">
              <CardTitle>멤버십 #{membership.id}</CardTitle>
              <Badge variant={statusBadgeVariant(membership.status)}>
                {membershipStatusLabel[membership.status]}
              </Badge>
            </CardHeader>
            <CardContent>
              <dl className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <dt className="text-xs text-muted-foreground">회원 ID</dt>
                  <dd>#{membership.userId}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">플랜</dt>
                  <dd>{membership.plan}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">구독료</dt>
                  <dd>{formatKRW(membership.price)}/월</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">가입일</dt>
                  <dd>{new Date(membership.startAt).toLocaleDateString('ko-KR')}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">다음 결제일</dt>
                  <dd>{new Date(membership.nextBillingAt).toLocaleDateString('ko-KR')}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">결제 실패 횟수</dt>
                  <dd>{membership.billingFailureCount}회</dd>
                </div>
                {membership.canceledAt && (
                  <div>
                    <dt className="text-xs text-muted-foreground">해지 예약일</dt>
                    <dd>{new Date(membership.canceledAt).toLocaleDateString('ko-KR')}</dd>
                  </div>
                )}
                {membership.gracePeriodEndsAt && (
                  <div>
                    <dt className="text-xs text-muted-foreground">유예기간 종료일</dt>
                    <dd>{new Date(membership.gracePeriodEndsAt).toLocaleDateString('ko-KR')}</dd>
                  </div>
                )}
              </dl>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">결제 이력</CardTitle>
            </CardHeader>
            <CardContent>
              {histories.length === 0 ? (
                <p className="py-6 text-center text-sm text-muted-foreground">결제 이력이 없습니다.</p>
              ) : (
                <ul className="divide-y divide-border text-sm">
                  {histories.map((h) => (
                    <li key={h.id} className="flex items-center justify-between gap-2 py-2">
                      <div>
                        <p>주기 {new Date(h.cycleAt).toLocaleDateString('ko-KR')}</p>
                        <p className="text-xs text-muted-foreground">
                          시도 {new Date(h.attemptedAt).toLocaleString('ko-KR')}
                          {h.failureReason && ` · ${h.failureReason}`}
                        </p>
                      </div>
                      <Badge variant={h.status === 'SUCCESS' ? 'default' : 'destructive'}>
                        {billingHistoryStatusLabel[h.status]}
                      </Badge>
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>
        </>
      ) : null}
    </div>
  )
}
