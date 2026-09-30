import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { notificationPreferenceApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { notificationTypeLabel } from '../labels'
import type { NotificationPreference } from '../api/types'

/**
 * 알림 수신 설정 — 메일·푸시로도 오는 알림을 종류별로 끈다. 인앱 알림함은 항상 받는다.
 * 체크를 바꾸면 그 줄만 바로 저장한다(저장 버튼 없음).
 */
export default function NotificationPreferencesCard() {
  const [prefs, setPrefs] = useState<NotificationPreference[] | null>(null)

  useEffect(() => {
    notificationPreferenceApi
      .get()
      .then(setPrefs)
      .catch(() => setPrefs(null))
  }, [])

  if (!prefs) return null

  const toggle = async (pref: NotificationPreference, channel: 'email' | 'push') => {
    const next = { ...pref, [channel]: !pref[channel] }
    const previous = prefs
    setPrefs(prefs.map((p) => (p.type === pref.type ? next : p)))
    try {
      setPrefs(await notificationPreferenceApi.update([next]))
    } catch (e) {
      setPrefs(previous)
      toast.error(e instanceof ApiError ? e.message : '알림 설정을 저장하지 못했습니다.')
    }
  }

  return (
    <details className="mb-6 rounded-lg border border-border bg-card p-4">
      <summary className="cursor-pointer text-sm font-medium">알림 받을 방법 설정</summary>
      <p className="mt-2 text-xs text-muted-foreground">알림함에는 항상 쌓이고, 메일·푸시로 받을지만 고릅니다.</p>
      <table className="mt-3 w-full text-sm">
        <thead>
          <tr className="text-left text-xs text-muted-foreground">
            <th className="py-1 font-normal">알림</th>
            <th className="w-16 py-1 text-center font-normal">메일</th>
            <th className="w-16 py-1 text-center font-normal">푸시</th>
          </tr>
        </thead>
        <tbody>
          {prefs.map((p) => (
            <tr key={p.type} className="border-t border-border">
              <td className="py-2">{notificationTypeLabel[p.type] ?? p.type}</td>
              {(['email', 'push'] as const).map((channel) => (
                <td key={channel} className="text-center">
                  <input
                    type="checkbox"
                    className="size-4"
                    aria-label={`${notificationTypeLabel[p.type] ?? p.type} ${channel === 'email' ? '메일' : '푸시'}`}
                    checked={p[channel]}
                    onChange={() => toggle(p, channel)}
                  />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </details>
  )
}
