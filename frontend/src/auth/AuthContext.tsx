import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react'
import { authApi, cartApi } from '../api/endpoints'
import { ApiError } from '../api/client'
import { clearGuestCart, readGuestCart } from '../cart/guestCart'
import type { User } from '../api/types'

interface AuthState {
  user: User | null
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  signup: (email: string, password: string, name: string) => Promise<void>
  logout: () => Promise<void>
  /** 서버에서 역할을 다시 읽어 세션 권한까지 갱신한다(예: 판매자 승인 직후). */
  refresh: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    try {
      setUser(await authApi.me())
    } catch (e) {
      // 미인증(401)은 정상 상태 — 비로그인으로 둔다.
      if (!(e instanceof ApiError) || e.status !== 401) console.error(e)
      setUser(null)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  // 로그인 직후 게스트(localStorage) 장바구니를 서버로 병합하고 비운다.
  const mergeGuestCart = async () => {
    const lines = readGuestCart()
    if (lines.length === 0) return
    try {
      await cartApi.merge(lines)
      clearGuestCart()
    } catch (e) {
      console.error('장바구니 병합 실패', e)
    }
  }

  const login = async (email: string, password: string) => {
    setUser(await authApi.login(email, password))
    await mergeGuestCart()
  }
  const signup = async (email: string, password: string, name: string) => {
    await authApi.signup(email, password, name)
    setUser(await authApi.login(email, password))
    await mergeGuestCart()
  }
  const logout = async () => {
    await authApi.logout()
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, signup, logout, refresh }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
