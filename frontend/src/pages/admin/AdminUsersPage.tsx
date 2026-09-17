import { useCallback, useEffect, useState } from 'react'
import { adminUserApi } from '../../api/endpoints'
import { ApiError } from '../../api/client'
import { userStatusLabel } from '../../labels'
import type { AdminUser, PageResponse, UserStatus } from '../../api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'

const statuses = Object.keys(userStatusLabel) as UserStatus[]

/** 관리자 회원 관리: 검색, 상태 변경(잠금/휴면), 판매자·관리자 역할 부여/회수. */
export default function AdminUsersPage() {
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<UserStatus | ''>('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<AdminUser> | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      setData(await adminUserApi.search({ keyword: keyword || undefined, status: status || undefined, page }))
    } catch (e) {
      setError((e as Error).message)
    }
  }, [keyword, status, page])

  useEffect(() => {
    load()
  }, [load])

  const run = async (message: string, action: () => Promise<unknown>) => {
    if (!confirm(message)) return
    setError(null)
    try {
      await action()
      await load()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '처리 실패')
    }
  }

  const roleButton = (u: AdminUser, role: string, label: string) => {
    const has = u.roles.includes(role)
    return (
      <Button
        type="button"
        size="sm"
        variant="outline"
        onClick={() =>
          run(`${u.email} 의 ${label} 역할을 ${has ? '회수' : '부여'}하시겠습니까?`, () =>
            has ? adminUserApi.revokeRole(u.id, role) : adminUserApi.grantRole(u.id, role),
          )
        }
      >
        {label} {has ? '회수' : '부여'}
      </Button>
    )
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-2">
        <Input
          value={keyword}
          onChange={(e) => {
            setKeyword(e.target.value)
            setPage(0)
          }}
          placeholder="이메일 검색"
          aria-label="이메일 검색"
          className="w-56"
        />
        <select
          value={status}
          onChange={(e) => {
            setStatus(e.target.value as UserStatus | '')
            setPage(0)
          }}
          aria-label="상태 필터"
          className="h-9 rounded-md border border-input bg-background px-2 text-sm"
        >
          <option value="">전체 상태</option>
          {statuses.map((s) => (
            <option key={s} value={s}>
              {userStatusLabel[s]}
            </option>
          ))}
        </select>
      </div>

      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
      {!data ? (
        <p className="py-10 text-center text-sm text-muted-foreground">불러오는 중…</p>
      ) : data.content.length === 0 ? (
        <p className="py-10 text-center text-sm text-muted-foreground">조건에 맞는 회원이 없습니다.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="text-left text-xs text-muted-foreground">
              <tr>
                <th className="py-2">ID</th>
                <th>이메일 / 이름</th>
                <th>역할</th>
                <th>가입일</th>
                <th>상태</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {data.content.map((u) => (
                <tr key={u.id} className="border-t border-border">
                  <td className="py-2">{u.id}</td>
                  <td>
                    <div>{u.email}</div>
                    <div className="text-xs text-muted-foreground">{u.name}</div>
                  </td>
                  <td className="text-xs">{u.roles.map((r) => r.replace('ROLE_', '')).join(', ')}</td>
                  <td className="text-xs">{new Date(u.createdAt).toLocaleDateString()}</td>
                  <td>
                    <select
                      value={u.status}
                      aria-label={`${u.email} 상태`}
                      onChange={(e) => {
                        const next = e.target.value as UserStatus
                        run(`${u.email} 을(를) ${userStatusLabel[next]} 상태로 변경하시겠습니까?`, () =>
                          adminUserApi.changeStatus(u.id, next),
                        )
                      }}
                      className="h-8 rounded-md border border-input bg-background px-2 text-sm"
                    >
                      {statuses.map((s) => (
                        <option key={s} value={s}>
                          {userStatusLabel[s]}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td className="space-x-1 whitespace-nowrap text-right">
                    {roleButton(u, 'ROLE_SELLER', '판매자')}
                    {roleButton(u, 'ROLE_ADMIN', '관리자')}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {data.totalPages > 1 && (
            <div className="mt-3 flex justify-center gap-1">
              {Array.from({ length: data.totalPages }, (_, i) => (
                <button
                  key={i}
                  onClick={() => setPage(i)}
                  className={cn(
                    'h-8 w-8 rounded text-sm',
                    i === page
                      ? 'bg-primary text-primary-foreground'
                      : 'border border-input bg-background text-muted-foreground',
                  )}
                >
                  {i + 1}
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
