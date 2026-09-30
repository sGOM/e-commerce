import { pushApi } from '../api/endpoints'

/** 이 브라우저가 웹 푸시를 지원하는지(서비스워커 + Push API + 알림 권한). */
export function pushSupported(): boolean {
  return 'serviceWorker' in navigator && 'PushManager' in globalThis && 'Notification' in globalThis
}

/** VAPID 공개키(base64url) → pushManager.subscribe 의 applicationServerKey 형식. */
export function base64UrlToBytes(value: string): Uint8Array<ArrayBuffer> {
  const base64 = (value + '='.repeat((4 - (value.length % 4)) % 4)).replace(/-/g, '+').replace(/_/g, '/')
  return Uint8Array.from(atob(base64), (c) => c.charCodeAt(0))
}

/** 현재 이 브라우저의 푸시 구독(없으면 null). */
export async function currentPushSubscription(): Promise<PushSubscription | null> {
  const registration = await navigator.serviceWorker.getRegistration()
  return (await registration?.pushManager.getSubscription()) ?? null
}

/**
 * 알림 권한을 받고 서비스워커로 구독해 서버에 등록한다.
 * 'denied' 는 사용자가 권한을 거부한 것, 'unavailable' 은 서버에 VAPID 키가 없는 것.
 */
export async function enablePush(): Promise<'enabled' | 'denied' | 'unavailable'> {
  const { publicKey } = await pushApi.publicKey()
  if (!publicKey) return 'unavailable'
  if ((await Notification.requestPermission()) !== 'granted') return 'denied'
  const registration = await navigator.serviceWorker.register('/sw.js')
  await navigator.serviceWorker.ready
  const subscription = await registration.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: base64UrlToBytes(publicKey),
  })
  await pushApi.subscribe(subscription.toJSON())
  return 'enabled'
}

/**
 * 브라우저 구독만 해지한다 — 서버 행은 다음 발송 때 푸시 서비스의 410 응답으로 지워진다.
 * 로그아웃·탈퇴에서도 불러 공용 PC 에서 이전 회원의 알림이 계속 뜨지 않게 한다(미지원 브라우저는 무시).
 */
export async function disablePush(): Promise<void> {
  if (!pushSupported()) return
  await (await currentPushSubscription())?.unsubscribe()
}
