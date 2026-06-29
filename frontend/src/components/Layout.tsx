import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export default function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = async () => {
    await logout()
    navigate('/')
  }

  const navClass = ({ isActive }: { isActive: boolean }) =>
    isActive ? 'text-indigo-600 font-semibold' : 'text-slate-600 hover:text-slate-900'

  return (
    <div className="min-h-screen flex flex-col">
      <header className="border-b bg-white">
        <div className="mx-auto max-w-5xl px-4 h-14 flex items-center justify-between gap-4">
          <Link to="/" className="text-lg font-bold text-indigo-600">
            마켓
          </Link>
          <nav className="flex items-center gap-4 text-sm">
            <NavLink to="/" className={navClass} end>
              상품
            </NavLink>
            <NavLink to="/cart" className={navClass}>
              장바구니
            </NavLink>
            {user ? (
              <>
                <NavLink to="/orders" className={navClass}>
                  내 주문
                </NavLink>
                <NavLink to="/my" className={navClass}>
                  쿠폰/포인트
                </NavLink>
                <span className="text-slate-400">|</span>
                <span className="text-slate-600">{user.name}님</span>
                <button
                  onClick={handleLogout}
                  className="text-slate-600 hover:text-slate-900"
                >
                  로그아웃
                </button>
              </>
            ) : (
              <>
                <NavLink to="/orders/lookup" className={navClass}>
                  주문조회
                </NavLink>
                <NavLink to="/login" className={navClass}>
                  로그인
                </NavLink>
              </>
            )}
          </nav>
        </div>
      </header>

      <main className="flex-1 mx-auto w-full max-w-5xl px-4 py-6">
        <Outlet />
      </main>

      <footer className="border-t bg-white py-4 text-center text-xs text-slate-400">
        B2C 마켓플레이스 데모 · Spring Boot + React
      </footer>
    </div>
  )
}
