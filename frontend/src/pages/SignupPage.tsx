import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'

export default function SignupPage() {
  const { signup } = useAuth()
  const navigate = useNavigate()

  const [form, setForm] = useState({ email: '', password: '', name: '' })
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm({ ...form, [k]: e.target.value })

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await signup(form.email, form.password, form.name)
      navigate('/', { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '회원가입에 실패했습니다.')
      setSubmitting(false)
    }
  }

  return (
    <div className="grid min-h-[70vh] place-items-center py-8">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle className="text-xl">회원가입</CardTitle>
          <CardDescription>간단한 정보로 계정을 만드세요.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={submit} className="space-y-4">
            {error && (
              <p
                role="alert"
                className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive"
              >
                {error}
              </p>
            )}
            <div className="space-y-2">
              <Label htmlFor="name">
                이름 <span className="text-destructive">*</span>
              </Label>
              <Input
                id="name"
                autoComplete="name"
                required
                aria-required="true"
                value={form.name}
                onChange={set('name')}
              />
            </div>
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
                value={form.email}
                onChange={set('email')}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">
                비밀번호 <span className="text-destructive">*</span>
              </Label>
              <Input
                id="password"
                type="password"
                autoComplete="new-password"
                required
                aria-required="true"
                minLength={8}
                maxLength={64}
                aria-describedby="password-help"
                value={form.password}
                onChange={set('password')}
              />
              <p id="password-help" className="text-xs text-muted-foreground">
                8~64자로 입력하세요.
              </p>
            </div>
            <Button type="submit" disabled={submitting} className="h-11 w-full">
              {submitting && <Loader2 className="size-4 animate-spin" />}
              {submitting ? '가입 중…' : '회원가입'}
            </Button>
          </form>
        </CardContent>
        <CardFooter className="justify-center text-sm text-muted-foreground">
          이미 계정이 있으신가요?
          <Link to="/login" className="ml-1 font-medium text-primary hover:underline">
            로그인
          </Link>
        </CardFooter>
      </Card>
    </div>
  )
}
