import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react'
import { wishlistApi } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'

// 회원 1인의 위시리스트가 매우 많지는 않다고 가정 — 카드/상세에서 즉시 하트 상태를 보여주기 위해
// 한 번에 넉넉히 받아 클라이언트에서 productId로 매칭한다(재입고 알림과 동일한 패턴).
const PAGE_SIZE = 200

interface WishlistState {
  // null = 비로그인(하트 비활성 처리는 호출측에서 useAuth().user 로 판단)
  ids: Set<number> | null
  pendingIds: Set<number>
  toggle: (productId: number) => Promise<void>
}

const WishlistContext = createContext<WishlistState | null>(null)

/** 위시리스트 하트 상태를 앱 전역에서 공유 — ProductCard/상세 페이지가 각자 재조회하지 않도록 한다. */
export function WishlistProvider({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth()
  const [ids, setIds] = useState<Set<number> | null>(null)
  const [pendingIds, setPendingIds] = useState<Set<number>>(new Set())

  const load = useCallback(() => {
    if (!user) {
      setIds(null)
      return
    }
    wishlistApi
      .my({ size: PAGE_SIZE })
      .then((p) => setIds(new Set(p.content.map((w) => w.productId))))
      .catch(() => setIds(new Set()))
  }, [user])

  useEffect(() => {
    if (loading) return
    load()
  }, [loading, load])

  const toggle = useCallback(
    async (productId: number) => {
      if (!user) return
      setPendingIds((prev) => new Set(prev).add(productId))
      try {
        if (ids?.has(productId)) {
          await wishlistApi.remove(productId)
          setIds((prev) => {
            const next = new Set(prev ?? [])
            next.delete(productId)
            return next
          })
        } else {
          await wishlistApi.add(productId)
          setIds((prev) => new Set(prev ?? []).add(productId))
        }
      } finally {
        setPendingIds((prev) => {
          const next = new Set(prev)
          next.delete(productId)
          return next
        })
      }
    },
    [user, ids],
  )

  return (
    <WishlistContext.Provider value={{ ids, pendingIds, toggle }}>
      {children}
    </WishlistContext.Provider>
  )
}

export function useWishlist(): WishlistState {
  const ctx = useContext(WishlistContext)
  if (!ctx) throw new Error('useWishlist must be used within WishlistProvider')
  return ctx
}
