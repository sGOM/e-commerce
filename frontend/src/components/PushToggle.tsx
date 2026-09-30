import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { pushApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { currentPushSubscription, disablePush, enablePush, pushSupported } from '../lib/push'
import { Button } from '@/components/ui/button'

type State = 'hidden' | 'on' | 'off'

/**
 * 브라우저 푸시 켜기/끄기(ROADMAP 6.3). 재입고·가격 인하처럼 메일로도 가는 알림이 이 브라우저로도 온다.
 * 브라우저가 지원하지 않거나 서버에 VAPID 키가 없으면 아예 보이지 않는다.
 */
export default function PushToggle() {
  const [state, setState] = useState<State>('hidden')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!pushSupported()) return
    Promise.all([pushApi.publicKey(), currentPushSubscription()])
      .then(([{ publicKey }, subscription]) => {
        if (!publicKey) return
        setState(subscription && Notification.permission === 'granted' ? 'on' : 'off')
      })
      .catch(() => {
        // 상태를 모르면 토글을 숨긴다 — 알림함 자체는 계속 쓸 수 있다
      })
  }, [])

  if (state === 'hidden') return null

  const toggle = async () => {
    setBusy(true)
    try {
      if (state === 'on') {
        await disablePush()
        setState('off')
        toast.success('이 브라우저의 푸시 알림을 껐습니다.')
        return
      }
      const result = await enablePush()
      if (result === 'enabled') {
        setState('on')
        toast.success('이 브라우저로 푸시 알림을 보내드릴게요.')
      } else if (result === 'denied') {
        toast.error('브라우저에서 알림 권한을 허용해야 받을 수 있습니다.')
      } else {
        setState('hidden')
      }
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : '푸시 알림 설정에 실패했습니다.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mb-6 flex items-center justify-between gap-4 rounded-lg border border-border bg-card p-4">
      <div>
        <p className="text-sm font-medium">브라우저 푸시 알림</p>
        <p className="text-xs text-muted-foreground">재입고·가격 인하 같은 소식을 사이트를 닫아도 받아볼 수 있어요.</p>
      </div>
      <Button type="button" size="sm" variant={state === 'on' ? 'outline' : 'default'} disabled={busy} onClick={toggle}>
        {state === 'on' ? '끄기' : '켜기'}
      </Button>
    </div>
  )
}
