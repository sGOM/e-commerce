import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import { membershipApi } from '../api/endpoints'
import { ApiError, formatKRW } from '../api/client'
import { membershipStatusLabel } from '../labels'
import type { Membership } from '../api/types'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/** bp(10000=1.0배) -> "1.5배" 표기. */
function multiplierLabel(bp: number): string {
  return `${(bp / 10_000).toFixed(1).replace(/\.0$/, '')}배`
}

function statusBadgeVariant(status: Membership['status']): 'default' | 'secondary' | 'destructive' | 'outline' {
  if (status === 'ACTIVE') return 'default'
  if (status === 'PAST_DUE') return 'destructive'
  if (status === 'CANCELED') return 'secondary'
  return 'outline'
}

/** 마이페이지: 유료 멤버십(구독) 카드. 미가입 시 혜택 소개 + 카드 등록 + 가입, 가입 시 상태/혜택 + 해지. */
export default function MyMembershipPage() {
  const [membership, setMembership] = useState<Membership | null>(null)
  const [notSubscribed, setNotSubscribed] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const m = await membershipApi.my()
      setMembership(m)
      setNotSubscribed(false)
    } catch (e) {
      if (e instanceof ApiError && e.code === 'MEMBERSHIP-001') {
        setMembership(null)
        setNotSubscribed(true)
      } else {
        setError(e instanceof ApiError ? e.message : '멤버십 정보를 불러오지 못했습니다.')
      }
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const cancel = async () => {
    if (!confirm('멤버십을 해지하시겠습니까? 이미 결제한 기간까지는 혜택이 유지됩니다.')) return
    try {
      const m = await membershipApi.cancel()
      setMembership(m)
      toast.success('멤버십 해지가 예약되었습니다. 남은 이용 기간까지는 혜택이 유지됩니다.')
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : '해지에 실패했습니다.')
    }
  }

  if (loading) return <p className="py-20 text-center text-sm text-muted-foreground">불러오는 중…</p>
  if (error) return <p className="py-20 text-center text-sm text-destructive">{error}</p>

  return (
    <div className="max-w-xl space-y-6">
      <h1 className="text-xl font-bold">멤버십</h1>
      {notSubscribed ? (
        <SubscribeFlow onSubscribed={load} />
      ) : membership ? (
        <MembershipSummary membership={membership} onCancel={cancel} />
      ) : null}
    </div>
  )
}

function MembershipSummary({
  membership,
  onCancel,
}: {
  membership: Membership
  onCancel: () => void
}) {
  const cancelable = membership.status === 'ACTIVE' || membership.status === 'PAST_DUE'
  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between space-y-0">
        <CardTitle>BASIC 멤버십</CardTitle>
        <Badge variant={statusBadgeVariant(membership.status)}>
          {membershipStatusLabel[membership.status]}
        </Badge>
      </CardHeader>
      <CardContent className="space-y-4 text-sm">
        <p className="text-2xl font-bold text-primary">{formatKRW(membership.price)}<span className="ml-1 text-sm font-normal text-muted-foreground">/월</span></p>

        <div className="space-y-1 rounded-lg border border-border bg-muted/40 p-3">
          <p className="font-medium">
            {membership.benefitActive ? '지금 적용 중인 혜택' : '혜택이 현재 적용되지 않습니다'}
          </p>
          <ul className="space-y-0.5 text-muted-foreground">
            <li>
              무료배송: <span className="font-medium text-foreground">{membership.benefits.freeShipping ? '적용' : '미적용'}</span>
            </li>
            <li>
              포인트 적립:{' '}
              <span className="font-medium text-foreground">
                {multiplierLabel(membership.benefits.pointEarnMultiplierBp)} 적립
              </span>
            </li>
          </ul>
        </div>

        {membership.status === 'CANCELED' && (
          <p className="rounded-lg bg-warning/10 p-3 text-warning">
            해지가 예약되었습니다. {new Date(membership.nextBillingAt).toLocaleDateString('ko-KR')}까지는 혜택이 유지되고,
            이후 자동으로 만료됩니다.
          </p>
        )}
        {membership.status === 'PAST_DUE' && (
          <p className="rounded-lg bg-destructive/10 p-3 text-destructive">
            정기결제가 실패해 재시도 중입니다. 카드 정보를 확인해 주세요. 유예 기간 내 재시도에 실패하면 혜택이 종료됩니다.
          </p>
        )}
        {membership.status === 'EXPIRED' && (
          <p className="rounded-lg bg-muted p-3 text-muted-foreground">
            멤버십이 만료되었습니다. 다시 가입하면 새로운 구독 주기가 시작됩니다.
          </p>
        )}

        <dl className="grid grid-cols-2 gap-2 text-muted-foreground">
          <div>
            <dt className="text-xs">가입일</dt>
            <dd>{new Date(membership.startAt).toLocaleDateString('ko-KR')}</dd>
          </div>
          <div>
            <dt className="text-xs">
              {membership.status === 'CANCELED' ? '혜택 종료 예정일' : '다음 결제일'}
            </dt>
            <dd>{new Date(membership.nextBillingAt).toLocaleDateString('ko-KR')}</dd>
          </div>
        </dl>

        {cancelable && (
          <Button type="button" variant="outline" onClick={onCancel} className="w-full">
            멤버십 해지
          </Button>
        )}
        {membership.status === 'EXPIRED' && (
          <p className="text-center text-xs text-muted-foreground">
            재가입은 이 페이지를 새로고침한 뒤 진행해 주세요.
          </p>
        )}
      </CardContent>
    </Card>
  )
}

function SubscribeFlow({ onSubscribed }: { onSubscribed: () => void }) {
  const [cardNumber, setCardNumber] = useState('')
  const [registering, setRegistering] = useState(false)
  const [cardRegistered, setCardRegistered] = useState(false)
  const [cardLast4, setCardLast4] = useState<string | null>(null)
  const [subscribing, setSubscribing] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const registerCard = async (e: React.FormEvent) => {
    e.preventDefault()
    setRegistering(true)
    setError(null)
    try {
      const res = await membershipApi.registerBillingKey(cardNumber.trim())
      setCardRegistered(true)
      setCardLast4(res.cardLast4)
      toast.success('카드가 등록되었습니다.')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '카드 등록에 실패했습니다.')
    } finally {
      setRegistering(false)
    }
  }

  const subscribe = async () => {
    setSubscribing(true)
    setError(null)
    try {
      await membershipApi.subscribe('BASIC')
      toast.success('멤버십 구독이 시작되었습니다.')
      onSubscribed()
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : '구독 시작에 실패했습니다.'
      setError(msg)
      if (e instanceof ApiError && e.code === 'MEMBERSHIP-003') {
        setCardRegistered(false)
      }
    } finally {
      setSubscribing(false)
    }
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>멤버십 혜택</CardTitle>
        </CardHeader>
        <CardContent>
          <ul className="list-disc space-y-1 pl-5 text-sm text-muted-foreground">
            <li>매 주문 무료배송</li>
            <li>포인트 우대 적립(일반 회원보다 더 많이 적립)</li>
            <li>멤버십 전용 쿠폰 수령 가능</li>
          </ul>
          <p className="mt-3 text-xs text-muted-foreground">
            월 정기 구독료가 결제되며, 언제든 해지할 수 있습니다. 해지해도 이미 결제한 기간까지는 혜택이 유지됩니다.
          </p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <span className="flex size-5 shrink-0 items-center justify-center rounded-full bg-primary text-xs text-primary-foreground">
              1
            </span>
            결제수단(카드) 등록
          </CardTitle>
        </CardHeader>
        <CardContent>
          {cardRegistered ? (
            <p className="text-sm text-muted-foreground">
              등록 완료 · 카드번호 ****-****-****-{cardLast4}
            </p>
          ) : (
            <form onSubmit={registerCard} className="flex flex-col gap-3 sm:flex-row sm:items-end">
              <div className="flex-1">
                <Label htmlFor="card-number" className="mb-1 block text-xs text-muted-foreground">
                  카드번호(숫자만, 테스트 환경 — 실제 결제는 일어나지 않습니다)
                </Label>
                <Input
                  id="card-number"
                  required
                  inputMode="numeric"
                  placeholder="1234567812345678"
                  value={cardNumber}
                  onChange={(e) => setCardNumber(e.target.value)}
                />
              </div>
              <Button type="submit" disabled={registering}>
                {registering ? '등록 중…' : '카드 등록'}
              </Button>
            </form>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <span className="flex size-5 shrink-0 items-center justify-center rounded-full bg-primary text-xs text-primary-foreground">
              2
            </span>
            구독 시작
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {error && <p className="text-sm text-destructive">{error}</p>}
          <Button type="button" className="w-full" disabled={subscribing} onClick={subscribe}>
            {subscribing ? '처리 중…' : '멤버십 구독 시작'}
          </Button>
          <p className="text-center text-xs text-muted-foreground">
            카드를 먼저 등록해야 구독을 시작할 수 있습니다.
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
