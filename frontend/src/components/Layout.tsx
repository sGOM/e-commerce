import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useTheme } from 'next-themes'
import { Menu, Moon, ShoppingCart, Sun } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { useCartCount } from '../hooks/useCartCount'
import NotificationBell from './NotificationBell'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Sheet,
  SheetClose,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@/components/ui/sheet'
import { cn } from '@/lib/utils'

const navClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    'text-sm transition-colors',
    isActive
      ? 'text-primary font-semibold'
      : 'text-muted-foreground hover:text-foreground',
  )

function ThemeToggle() {
  const { resolvedTheme, setTheme } = useTheme()
  const [mounted, setMounted] = useState(false)
  useEffect(() => setMounted(true), [])

  const isDark = resolvedTheme === 'dark'
  return (
    <Button
      variant="ghost"
      size="icon"
      aria-label="테마 전환"
      onClick={() => setTheme(isDark ? 'light' : 'dark')}
    >
      {mounted && isDark ? (
        <Sun className="size-5" />
      ) : (
        <Moon className="size-5" />
      )}
    </Button>
  )
}

function CartButton() {
  const count = useCartCount()
  const label = count != null && count > 0 ? `장바구니, ${count}개` : '장바구니'
  return (
    <Button
      asChild
      variant="ghost"
      size="icon"
      className="relative"
      aria-label={label}
    >
      <Link to="/cart">
        <ShoppingCart className="size-5" />
        {count != null && count > 0 && (
          <Badge
            className="absolute -right-1 -top-1 size-5 justify-center rounded-full p-0 text-[10px] tabular-nums"
            aria-hidden="true"
          >
            {count > 99 ? '99+' : count}
          </Badge>
        )}
      </Link>
    </Button>
  )
}

interface NavItem {
  to: string
  label: string
  end?: boolean
}

export default function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = async () => {
    await logout()
    navigate('/')
  }

  const primaryNav: NavItem[] = [
    { to: '/', label: '상품', end: true },
    { to: '/flash-sales', label: '타임딜' },
    { to: '/collections', label: '기획전' },
  ]
  const accountNav: NavItem[] = user
    ? [
        { to: '/orders', label: '내 주문' },
        { to: '/my', label: '쿠폰/포인트' },
        { to: '/my/loyalty-tier', label: '내 등급' },
        { to: '/my/wishlist', label: '찜한 상품' },
        { to: '/my/membership', label: '멤버십' },
        { to: '/my/delivery-subscriptions', label: '정기배송' },
        { to: '/my/reviews', label: '리뷰' },
        { to: '/my/restock-alerts', label: '재입고 알림' },
        { to: '/notifications', label: '알림함' },
        { to: '/seller', label: '판매자' },
        ...(user.roles.includes('ROLE_ADMIN')
          ? [{ to: '/admin', label: '관리자' }]
          : []),
      ]
    : [{ to: '/orders/lookup', label: '주문조회' }]

  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-40 border-b border-border bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/80">
        <div className="mx-auto flex h-14 max-w-7xl items-center gap-2 px-4 md:h-16 md:px-6">
          {/* 모바일 네비 트리거 */}
          <div className="md:hidden">
            <Sheet>
              <SheetTrigger asChild>
                <Button variant="ghost" size="icon" aria-label="메뉴 열기">
                  <Menu className="size-5" />
                </Button>
              </SheetTrigger>
              <SheetContent side="left" className="w-72">
                <SheetHeader>
                  <SheetTitle className="text-primary">마켓</SheetTitle>
                </SheetHeader>
                <nav className="flex flex-col gap-1 px-2">
                  {[...primaryNav, ...accountNav].map((item) => (
                    <SheetClose asChild key={item.to}>
                      <NavLink
                        to={item.to}
                        end={item.end}
                        className={({ isActive }) =>
                          cn(
                            'rounded-md px-3 py-2.5 text-sm transition-colors',
                            isActive
                              ? 'bg-accent font-semibold text-accent-foreground'
                              : 'text-muted-foreground hover:bg-muted hover:text-foreground',
                          )
                        }
                      >
                        {item.label}
                      </NavLink>
                    </SheetClose>
                  ))}
                  {user ? (
                    <SheetClose asChild>
                      <button
                        onClick={handleLogout}
                        className="mt-1 rounded-md px-3 py-2.5 text-left text-sm text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
                      >
                        로그아웃
                      </button>
                    </SheetClose>
                  ) : (
                    <SheetClose asChild key="login">
                      <NavLink
                        to="/login"
                        className="mt-1 rounded-md px-3 py-2.5 text-sm font-semibold text-primary"
                      >
                        로그인
                      </NavLink>
                    </SheetClose>
                  )}
                </nav>
              </SheetContent>
            </Sheet>
          </div>

          <Link to="/" className="text-lg font-bold text-primary">
            마켓
          </Link>

          {/* 데스크톱 인라인 네비 */}
          <nav className="ml-6 hidden items-center gap-5 md:flex">
            {primaryNav.map((item) => (
              <NavLink key={item.to} to={item.to} end={item.end} className={navClass}>
                {item.label}
              </NavLink>
            ))}
            {!user && (
              <NavLink to="/orders/lookup" className={navClass}>
                주문조회
              </NavLink>
            )}
          </nav>

          {/* 우측 액션 영역 */}
          <div className="ml-auto flex items-center gap-1">
            {user && <NotificationBell />}
            <CartButton />
            <ThemeToggle />
            {user ? (
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="ghost" size="sm" className="max-w-[10rem]">
                    <span className="truncate">{user.name}님</span>
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" className="w-48">
                  <DropdownMenuLabel className="truncate">
                    {user.email}
                  </DropdownMenuLabel>
                  <DropdownMenuSeparator />
                  {accountNav.map((item) => (
                    <DropdownMenuItem key={item.to} asChild>
                      <Link to={item.to}>{item.label}</Link>
                    </DropdownMenuItem>
                  ))}
                  <DropdownMenuSeparator />
                  <DropdownMenuItem onSelect={handleLogout}>
                    로그아웃
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            ) : (
              <Button asChild size="sm" className="ml-1">
                <Link to="/login">로그인</Link>
              </Button>
            )}
          </div>
        </div>
      </header>

      <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 md:px-6 md:py-8">
        <Outlet />
      </main>

      <footer className="border-t border-border py-6 text-center text-xs text-muted-foreground">
        B2C 마켓플레이스 데모 · Spring Boot + React
      </footer>
    </div>
  )
}
