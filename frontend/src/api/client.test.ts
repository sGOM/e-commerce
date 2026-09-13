// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, formatKRW } from './client'

function mockFetch(status: number, body?: unknown) {
  const fn = vi.fn(
    async (_input: RequestInfo | URL, _init?: RequestInit) =>
      new Response(body === undefined ? null : JSON.stringify(body), { status }),
  )
  vi.stubGlobal('fetch', fn)
  return fn
}

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
})

describe('api client', () => {
  it('변경 요청에 XSRF-TOKEN 쿠키를 헤더로 싣고 공통 응답의 data 를 꺼낸다', async () => {
    document.cookie = 'XSRF-TOKEN=abc%3D1'
    const fetchMock = mockFetch(200, { success: true, code: 'OK', message: null, data: { id: 7 } })

    await expect(api.post('/api/x', { a: 1 })).resolves.toEqual({ id: 7 })

    const init = fetchMock.mock.calls[0][1] as RequestInit & { headers: Record<string, string> }
    expect(init.headers['X-XSRF-TOKEN']).toBe('abc=1')
    expect(init.headers['Content-Type']).toBe('application/json')
    expect(init.body).toBe('{"a":1}')
    expect(init.credentials).toBe('include')
  })

  it('GET 요청에는 CSRF 헤더를 싣지 않는다', async () => {
    document.cookie = 'XSRF-TOKEN=abc'
    const fetchMock = mockFetch(200, { success: true, code: 'OK', message: null, data: [] })

    await api.get('/api/x')

    const init = fetchMock.mock.calls[0][1] as RequestInit & { headers: Record<string, string> }
    expect(init.headers['X-XSRF-TOKEN']).toBeUndefined()
  })

  it('실패 응답은 서버 코드와 HTTP 상태를 가진 ApiError 로 던진다', async () => {
    mockFetch(409, { success: false, code: 'REVIEW-002', message: '이미 작성한 리뷰입니다.', data: null })

    const err = await api.post('/api/reviews', {}).catch((e: unknown) => e)

    expect(err).toBeInstanceOf(ApiError)
    expect(err).toMatchObject({ code: 'REVIEW-002', status: 409, message: '이미 작성한 리뷰입니다.' })
  })

  it('본문 없는 실패는 HTTP 상태로 코드를 만든다', async () => {
    mockFetch(502)

    await expect(api.get('/api/x')).rejects.toMatchObject({ code: 'HTTP-502', status: 502 })
  })

  it('본문 없는 성공(204)은 undefined 를 돌려준다', async () => {
    mockFetch(204)

    await expect(api.del('/api/x')).resolves.toBeUndefined()
  })
})

describe('formatKRW', () => {
  it('천 단위 구분과 원 단위를 붙인다', () => {
    expect(formatKRW(1234567)).toBe('1,234,567원')
  })
})
