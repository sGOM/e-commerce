import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { authApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

/**
 * 비밀번호 분실 재설정. 토큰 없이 들어오면 메일 요청, 메일 링크(`?token=`)로 들어오면 새 비밀번호 설정.
 * 요청 결과는 가입 여부를 알려주지 않는다(계정 열거 방지).
 */
export default function PasswordResetPage() {
  const [params] = useSearchParams()
  const token = params.get('token')
  const navigate = useNavigate()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const run = async (action: () => Promise<void>) => {
    setSubmitting(true)
    setError(null)
    try {
      await action()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '처리에 실패했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  const requestMail = (e: React.FormEvent) => {
    e.preventDefault()
    run(async () => {
      await authApi.requestPasswordReset(email)
      setSent(true)
    })
  }

  const confirm = (e: React.FormEvent) => {
    e.preventDefault()
    run(async () => {
      await authApi.confirmPasswordReset(token!, password)
      navigate('/login', { replace: true })
    })
  }

  return (
    <div className="grid min-h-[70vh] place-items-center py-8">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>{token ? '새 비밀번호 설정' : '비밀번호 찾기'}</CardTitle>
          <CardDescription>
            {token
              ? '새로 사용할 비밀번호를 입력하세요.'
              : '가입한 이메일로 재설정 링크를 보내드립니다.'}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {token ? (
            <form onSubmit={confirm} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="new-password">새 비밀번호</Label>
                <Input
                  id="new-password"
                  type="password"
                  required
                  minLength={8}
                  maxLength={64}
                  autoComplete="new-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
                <p className="text-xs text-muted-foreground">8~64자</p>
              </div>
              {error && (
                <p role="alert" className="text-sm text-destructive">
                  {error}
                </p>
              )}
              <Button type="submit" disabled={submitting} className="h-11 w-full">
                {submitting ? '변경 중…' : '비밀번호 변경'}
              </Button>
            </form>
          ) : sent ? (
            <p className="py-4 text-sm text-muted-foreground">
              가입된 주소라면 재설정 메일을 보냈습니다. 링크는 30분 동안 한 번만 쓸 수 있습니다.
            </p>
          ) : (
            <form onSubmit={requestMail} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="reset-email">이메일</Label>
                <Input
                  id="reset-email"
                  type="email"
                  required
                  autoComplete="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </div>
              {error && (
                <p role="alert" className="text-sm text-destructive">
                  {error}
                </p>
              )}
              <Button type="submit" disabled={submitting} className="h-11 w-full">
                {submitting ? '보내는 중…' : '재설정 메일 받기'}
              </Button>
            </form>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
