import { Heart } from 'lucide-react'
import { toast } from 'sonner'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useWishlist } from '../hooks/useWishlist'
import { ApiError } from '../api/client'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

/**
 * 찜(위시리스트) 토글 버튼 — 상품 카드/상세 공용. 비로그인 시 로그인 유도 토스트만 띄운다
 * (`docs/planning/wishlist-price-alert.md`).
 */
export default function WishlistButton({
  productId,
  className,
}: {
  productId: number
  className?: string
}) {
  const { user } = useAuth()
  const navigate = useNavigate()
  const { ids, pendingIds, toggle } = useWishlist()
  const wishlisted = ids?.has(productId) ?? false
  const isPending = pendingIds.has(productId)

  const handleClick = async (e: React.MouseEvent) => {
    e.preventDefault()
    e.stopPropagation()
    if (!user) {
      toast.info('로그인 후 찜할 수 있습니다.', {
        action: { label: '로그인', onClick: () => navigate('/login') },
      })
      return
    }
    try {
      await toggle(productId)
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '요청에 실패했습니다.')
    }
  }

  return (
    <Button
      type="button"
      variant="secondary"
      size="icon"
      aria-label={wishlisted ? '찜 해제' : '찜하기'}
      aria-pressed={wishlisted}
      disabled={isPending}
      onClick={handleClick}
      className={cn('size-8 rounded-full shadow-sm', className)}
    >
      <Heart className={cn('size-4', wishlisted && 'fill-destructive text-destructive')} />
    </Button>
  )
}
