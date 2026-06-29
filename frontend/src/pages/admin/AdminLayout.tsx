import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'

/** 관리자 백오피스 공통 레이아웃: ROLE_ADMIN 게이트 + 서브내비. */
export default function AdminLayout() {
  const { user } = useAuth()
  const isAdmin = user?.roles.includes('ROLE_ADMIN') ?? false

  if (!isAdmin) {
    return (
      <p className="py-20 text-center text-slate-400">관리자 전용 페이지입니다.</p>
    )
  }

  const tab = ({ isActive }: { isActive: boolean }) =>
    `pb-2 text-sm ${isActive ? 'border-b-2 border-indigo-600 font-semibold text-indigo-600' : 'text-slate-500'}`

  return (
    <div>
      <h1 className="mb-2 text-xl font-bold">관리자 센터</h1>
      <nav className="mb-6 flex gap-6 border-b">
        <NavLink to="/admin/sellers" className={tab}>
          셀러 심사
        </NavLink>
        <NavLink to="/admin/orders" className={tab}>
          주문/환불
        </NavLink>
        <NavLink to="/admin/coupons" className={tab}>
          쿠폰/카테고리
        </NavLink>
        <NavLink to="/admin/settlements" className={tab}>
          정산
        </NavLink>
      </nav>
      <Outlet />
    </div>
  )
}
