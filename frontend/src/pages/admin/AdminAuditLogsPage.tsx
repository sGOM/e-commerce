import { useCallback, useEffect, useState } from 'react'
import { adminAuditLogApi } from '../../api/endpoints'
import type { AuditLog, PageResponse } from '../../api/types'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'

const methods = ['', 'POST', 'PUT', 'PATCH', 'DELETE']

/** 관리자 감사 로그 조회(읽기 전용). */
export default function AdminAuditLogsPage() {
  const [userId, setUserId] = useState('')
  const [method, setMethod] = useState('')
  const [uriKeyword, setUriKeyword] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<AuditLog> | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      setData(
        await adminAuditLogApi.search({
          userId: userId ? Number(userId) : undefined,
          method: method || undefined,
          uriKeyword: uriKeyword || undefined,
          page,
        }),
      )
    } catch (e) {
      setError((e as Error).message)
    }
  }, [userId, method, uriKeyword, page])

  useEffect(() => {
    load()
  }, [load])

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-2">
        <Input
          value={userId}
          onChange={(e) => {
            setUserId(e.target.value)
            setPage(0)
          }}
          placeholder="회원 ID"
          aria-label="회원 ID"
          inputMode="numeric"
          className="w-28"
        />
        <select
          value={method}
          onChange={(e) => {
            setMethod(e.target.value)
            setPage(0)
          }}
          aria-label="메서드"
          className="h-9 rounded-md border border-input bg-background px-2 text-sm"
        >
          {methods.map((m) => (
            <option key={m} value={m}>
              {m || '전체 메서드'}
            </option>
          ))}
        </select>
        <Input
          value={uriKeyword}
          onChange={(e) => {
            setUriKeyword(e.target.value)
            setPage(0)
          }}
          placeholder="URI 포함"
          aria-label="URI 포함"
          className="w-56"
        />
      </div>

      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
      {!data ? (
        <p className="py-10 text-center text-sm text-muted-foreground">불러오는 중…</p>
      ) : data.content.length === 0 ? (
        <p className="py-10 text-center text-sm text-muted-foreground">조건에 맞는 로그가 없습니다.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="text-left text-xs text-muted-foreground">
              <tr>
                <th className="py-2">시각</th>
                <th>회원</th>
                <th>요청</th>
                <th>상태</th>
                <th>소요</th>
                <th>IP</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((l) => (
                <tr key={l.id} className="border-t border-border align-top">
                  <td className="whitespace-nowrap py-2 text-xs">{new Date(l.createdAt).toLocaleString()}</td>
                  <td>{l.userId ?? '-'}</td>
                  <td className="break-all">
                    <span className="font-mono text-xs">{l.method}</span> {l.uri}
                    {l.payload && (
                      <details className="text-xs text-muted-foreground">
                        <summary>payload</summary>
                        <pre className="whitespace-pre-wrap">{l.payload}</pre>
                      </details>
                    )}
                  </td>
                  <td className={cn(l.statusCode >= 400 && 'text-destructive')}>{l.statusCode}</td>
                  <td className="whitespace-nowrap">{l.durationMs}ms</td>
                  <td className="text-xs">{l.ip ?? '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {data.totalPages > 1 && (
            <div className="mt-3 flex items-center justify-center gap-3 text-sm">
              <button
                disabled={page === 0}
                onClick={() => setPage(page - 1)}
                className="rounded border border-input px-3 py-1 disabled:opacity-40"
              >
                이전
              </button>
              <span className="text-muted-foreground">
                {page + 1} / {data.totalPages}
              </span>
              <button
                disabled={page + 1 >= data.totalPages}
                onClick={() => setPage(page + 1)}
                className="rounded border border-input px-3 py-1 disabled:opacity-40"
              >
                다음
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
