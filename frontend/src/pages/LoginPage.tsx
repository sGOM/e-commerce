import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'

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
    <div className="mx-auto max-w-sm py-10">
      <h1 className="mb-6 text-center text-xl font-bold">로그인</h1>
      <form onSubmit={submit} className="space-y-3">
        <input
          required
          type="email"
          placeholder="이메일"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="w-full rounded-lg border px-3 py-2 text-sm"
        />
        <input
          required
          type="password"
          placeholder="비밀번호"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className="w-full rounded-lg border px-3 py-2 text-sm"
        />
        {error && <p className="text-sm text-red-500">{error}</p>}
        <button
          disabled={submitting}
          className="w-full rounded-xl bg-indigo-600 py-3 font-semibold text-white hover:bg-indigo-700 disabled:bg-slate-300"
        >
          {submitting ? '로그인 중…' : '로그인'}
        </button>
      </form>
      <p className="mt-4 text-center text-sm text-slate-500">
        계정이 없으신가요?{' '}
        <Link to="/signup" className="text-indigo-600">
          회원가입
        </Link>
      </p>
    </div>
  )
}
