import type { ApiResponse } from './types'

/** 백엔드가 BusinessException/검증 실패를 공통 응답으로 줄 때의 에러 표현. */
export class ApiError extends Error {
  code: string
  status: number
  constructor(message: string, code: string, status: number) {
    super(message)
    this.code = code
    this.status = status
  }
}

/** XSRF-TOKEN 쿠키(httpOnly=false)를 읽어 변경 요청 헤더에 싣는다. */
function readCsrfToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : null
}

const MUTATING = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {}
  const isForm = body instanceof FormData // multipart 경계는 브라우저가 Content-Type 에 채운다
  if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json'
  if (MUTATING.has(method)) {
    const token = readCsrfToken()
    if (token) headers['X-XSRF-TOKEN'] = token
  }

  const res = await fetch(path, {
    method,
    headers,
    credentials: 'include', // 세션 쿠키 포함
    body: isForm ? body : body !== undefined ? JSON.stringify(body) : undefined,
  })

  // 본문이 없을 수 있는 응답(예: 204) 방어
  const text = await res.text()
  const payload = text ? (JSON.parse(text) as ApiResponse<T>) : null

  if (!res.ok || (payload && payload.success === false)) {
    const code = payload?.code ?? `HTTP-${res.status}`
    const message = payload?.message ?? '요청을 처리하지 못했습니다.'
    throw new ApiError(message, code, res.status)
  }
  return (payload?.data as T) ?? (undefined as T)
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  patch: <T>(path: string, body?: unknown) => request<T>('PATCH', path, body),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body),
  del: <T>(path: string) => request<T>('DELETE', path),
}

/** 변경 요청 전에 XSRF-TOKEN 쿠키가 발급되도록 한 번 호출(GET). */
export async function ensureCsrf(): Promise<void> {
  if (!readCsrfToken()) {
    await fetch('/api/products?size=1', { credentials: 'include' })
  }
}

/** KRW 정수 → "12,000원" */
export function formatKRW(value: number): string {
  return `${value.toLocaleString('ko-KR')}원`
}
