import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'

export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation() as { state?: { from?: string } }
  const from = location.state?.from ?? '/'

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await login(email, password)
      navigate(from, { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '로그인에 실패했습니다.')
      setSubmitting(false)
    }
  }

  return (
    <div className="grid min-h-[70vh] place-items-center py-8">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle className="text-xl">로그인</CardTitle>
          <CardDescription>이메일과 비밀번호로 로그인하세요.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={submit} className="space-y-4">
            {error && (
              <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
                {error}
              </p>
            )}
            <div className="space-y-2">
              <Label htmlFor="email">
                이메일 <span className="text-destructive">*</span>
              </Label>
              <Input
                id="email"
                type="email"
                autoComplete="email"
                required
                aria-required="true"
                placeholder="you@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">
                비밀번호 <span className="text-destructive">*</span>
              </Label>
              <Input
                id="password"
                type="password"
                autoComplete="current-password"
                required
                aria-required="true"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>
            <Button type="submit" disabled={submitting} className="h-11 w-full">
              {submitting && <Loader2 className="size-4 animate-spin" />}
              {submitting ? '로그인 중…' : '로그인'}
            </Button>
            <Link to="/reset-password" className="block text-center text-sm text-muted-foreground hover:underline">
              비밀번호를 잊으셨나요?
            </Link>
          </form>
        </CardContent>
        <CardFooter className="justify-center text-sm text-muted-foreground">
          계정이 없으신가요?
          <Link to="/signup" className="ml-1 font-medium text-primary hover:underline">
            회원가입
          </Link>
        </CardFooter>
      </Card>
    </div>
  )
}
