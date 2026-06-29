import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'

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
    <div className="mx-auto max-w-sm py-10">
      <h1 className="mb-6 text-center text-xl font-bold">회원가입</h1>
      <form onSubmit={submit} className="space-y-3">
        <input
          required
          placeholder="이름"
          value={form.name}
          onChange={set('name')}
          className="w-full rounded-lg border px-3 py-2 text-sm"
        />
        <input
          required
          type="email"
          placeholder="이메일"
          value={form.email}
          onChange={set('email')}
          className="w-full rounded-lg border px-3 py-2 text-sm"
        />
        <input
          required
          type="password"
          placeholder="비밀번호 (8~64자)"
          value={form.password}
          onChange={set('password')}
          className="w-full rounded-lg border px-3 py-2 text-sm"
        />
        {error && <p className="text-sm text-red-500">{error}</p>}
        <button
          disabled={submitting}
          className="w-full rounded-xl bg-indigo-600 py-3 font-semibold text-white hover:bg-indigo-700 disabled:bg-slate-300"
        >
          {submitting ? '가입 중…' : '회원가입'}
        </button>
      </form>
      <p className="mt-4 text-center text-sm text-slate-500">
        이미 계정이 있으신가요?{' '}
        <Link to="/login" className="text-indigo-600">
          로그인
        </Link>
      </p>
    </div>
  )
}
