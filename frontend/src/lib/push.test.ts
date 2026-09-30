import { afterEach, describe, expect, it, vi } from 'vitest'
import { base64UrlToBytes, disablePush, enablePush } from './push'
import { pushApi } from '../api/endpoints'

vi.mock('../api/endpoints', () => ({ pushApi: { publicKey: vi.fn(), subscribe: vi.fn() } }))

describe('push', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it('base64url VAPID 키를 패딩 없이도 바이트로 바꾼다', () => {
    expect(Array.from(base64UrlToBytes('-_8'))).toEqual([0xfb, 0xff])
  })

  it('서버에 VAPID 키가 없으면 권한을 묻지 않고 unavailable', async () => {
    const requestPermission = vi.fn()
    vi.stubGlobal('Notification', { requestPermission })
    vi.mocked(pushApi.publicKey).mockResolvedValue({ publicKey: null })

    expect(await enablePush()).toBe('unavailable')
    expect(requestPermission).not.toHaveBeenCalled()
  })

  it('권한을 거부하면 구독하지 않는다', async () => {
    vi.stubGlobal('Notification', { requestPermission: vi.fn().mockResolvedValue('denied') })
    vi.mocked(pushApi.publicKey).mockResolvedValue({ publicKey: 'AQAB' })

    expect(await enablePush()).toBe('denied')
    expect(pushApi.subscribe).not.toHaveBeenCalled()
  })

  it('권한을 받으면 서비스워커로 구독해 서버에 등록한다', async () => {
    const json = { endpoint: 'https://fcm.googleapis.com/fcm/send/x', keys: { p256dh: 'p', auth: 'a' } }
    const subscribe = vi.fn().mockResolvedValue({ toJSON: () => json })
    const register = vi.fn().mockResolvedValue({ pushManager: { subscribe } })
    vi.stubGlobal('Notification', { requestPermission: vi.fn().mockResolvedValue('granted') })
    vi.stubGlobal('navigator', { serviceWorker: { register, ready: Promise.resolve() } })
    vi.mocked(pushApi.publicKey).mockResolvedValue({ publicKey: 'AQAB' })

    expect(await enablePush()).toBe('enabled')
    expect(register).toHaveBeenCalledWith('/sw.js')
    expect(subscribe).toHaveBeenCalledWith({ userVisibleOnly: true, applicationServerKey: new Uint8Array([1, 0, 1]) })
    expect(pushApi.subscribe).toHaveBeenCalledWith(json)
  })

  it('로그아웃 때 부르는 해지는 이 브라우저의 구독을 끊는다', async () => {
    const unsubscribe = vi.fn().mockResolvedValue(true)
    vi.stubGlobal('navigator', {
      serviceWorker: {
        getRegistration: vi.fn().mockResolvedValue({ pushManager: { getSubscription: async () => ({ unsubscribe }) } }),
      },
    })
    vi.stubGlobal('PushManager', class {})
    vi.stubGlobal('Notification', {})

    await disablePush()

    expect(unsubscribe).toHaveBeenCalled()
  })
})
