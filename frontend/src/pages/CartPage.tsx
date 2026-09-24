import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Minus, Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { cartApi } from '../api/endpoints'
import { formatKRW } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { notifyCartChanged } from '../hooks/useCartCount'
import { readGuestCart, removeGuestItem, setGuestItemQuantity } from '../cart/guestCart'
import type { Cart } from '../api/types'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { Skeleton } from '@/components/ui/skeleton'

function CartSkeleton() {
  return (
    <div className="grid gap-6 lg:grid-cols-3">
      <div className="space-y-3 lg:col-span-2">
        {Array.from({ length: 3 }, (_, i) => (
          <div key={i} className="flex items-center gap-4 rounded-lg border border-border bg-card p-4">
            <Skeleton className="size-16 rounded-md" />
            <div className="flex-1 space-y-2">
              <Skeleton className="h-4 w-2/3" />
              <Skeleton className="h-3 w-1/3" />
              <Skeleton className="h-4 w-20" />
            </div>
            <Skeleton className="h-11 w-32" />
          </div>
        ))}
      </div>
      <Skeleton className="h-48 w-full rounded-lg" />
    </div>
  )
}

export default function CartPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const isGuest = !user

  const [cart, setCart] = useState<Cart | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState<number | null>(null)

  const load = useCallback(async () => {
    try {
      if (isGuest) {
        const lines = readGuestCart()
        setCart(lines.length === 0 ? { items: [], totalQuantity: 0, totalPrice: 0 } : await cartApi.guestPreview(lines))
      } else {
        setCart(await cartApi.get())
      }
    } catch (e) {
      toast.error((e as Error).message)
    } finally {
      setLoading(false)
    }
  }, [isGuest])

  useEffect(() => {
    load()
  }, [load])

  // 회원은 itemId, 게스트는 optionId 로 항목을 다룬다.
  const updateQty = async (key: { itemId: number | null; optionId: number }, quantity: number) => {
    if (quantity < 1) return
    setBusy(key.optionId)
    try {
      if (isGuest) {
        setGuestItemQuantity(key.optionId, quantity)
        await load()
      } else {
        setCart(await cartApi.updateItem(key.itemId!, quantity))
        notifyCartChanged()
      }
    } catch (e) {
      toast.error((e as Error).message)
    } finally {
      setBusy(null)
    }
  }

  const remove = async (key: { itemId: number | null; optionId: number }) => {
    setBusy(key.optionId)
    try {
      if (isGuest) {
        removeGuestItem(key.optionId)
        await load()
      } else {
        setCart(await cartApi.removeItem(key.itemId!))
        notifyCartChanged()
      }
      toast.success('삭제했습니다.')
    } catch (e) {
      toast.error((e as Error).message)
    } finally {
      setBusy(null)
    }
  }

  if (loading) return <CartSkeleton />

  const hasBlocked = cart?.items.some((i) => !i.purchasable) ?? false

  return (
    <div>
      <h1 className="mb-6 flex items-center gap-2 text-2xl font-bold">
        장바구니
        {isGuest && <Badge variant="outline">비회원</Badge>}
      </h1>

      {!cart || cart.items.length === 0 ? (
        <div className="mx-auto max-w-sm py-16 text-center">
          <p className="text-4xl">🛒</p>
          <p className="mt-3 text-sm text-muted-foreground">장바구니가 비어 있습니다.</p>
          <Button asChild className="mt-4">
            <Link to="/">상품 보러 가기</Link>
          </Button>
        </div>
      ) : (
        <div className="grid gap-6 lg:grid-cols-3">
          <ul className="space-y-3 lg:col-span-2">
            {cart.items.map((item) => (
              <li key={item.optionId}>
                <Card className="flex flex-row items-center gap-4 p-4">
                  <div className="flex size-16 shrink-0 items-center justify-center rounded-md bg-muted text-2xl">
                    🛍️
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium">{item.productName}</p>
                    <p className="truncate text-xs text-muted-foreground">{item.optionName}</p>
                    <p className="mt-1 text-sm font-bold text-primary">{formatKRW(item.unitPrice)}</p>
                    {!item.purchasable && (
                      <p className="mt-1 text-xs font-medium text-destructive">재고 부족(가용 {item.availableStock})</p>
                    )}
                  </div>
                  <div className="flex flex-col items-end gap-2">
                    <div className="flex items-center gap-1">
                      <Button
                        variant="outline"
                        size="icon"
                        className="size-11"
                        aria-label="수량 감소"
                        disabled={busy === item.optionId || item.quantity <= 1}
                        onClick={() => updateQty(item, item.quantity - 1)}
                      >
                        <Minus className="size-4" />
                      </Button>
                      <span className="w-8 text-center text-sm tabular-nums">{item.quantity}</span>
                      <Button
                        variant="outline"
                        size="icon"
                        className="size-11"
                        aria-label="수량 증가"
                        disabled={busy === item.optionId}
                        onClick={() => updateQty(item, item.quantity + 1)}
                      >
                        <Plus className="size-4" />
                      </Button>
                    </div>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="size-11 text-muted-foreground hover:text-destructive"
                      aria-label="삭제"
                      disabled={busy === item.optionId}
                      onClick={() => remove(item)}
                    >
                      <Trash2 className="size-4" />
                    </Button>
                  </div>
                </Card>
              </li>
            ))}
          </ul>

          <Card className="h-fit gap-0 p-5 lg:sticky lg:top-20">
            <div className="flex justify-between text-sm text-muted-foreground">
              <span>총 수량</span>
              <span className="tabular-nums">{cart.totalQuantity}개</span>
            </div>
            <Separator className="my-3" />
            <div className="flex justify-between font-bold">
              <span>합계</span>
              <span className="text-primary tabular-nums">{formatKRW(cart.totalPrice)}</span>
            </div>
            <Button onClick={() => navigate('/checkout')} disabled={hasBlocked} className="mt-5 h-12 w-full text-base">
              주문하기
            </Button>
            {isGuest && (
              <p className="mt-2 text-center text-xs text-muted-foreground">
                비회원으로 주문하거나 로그인 시 장바구니가 병합됩니다.
              </p>
            )}
          </Card>
        </div>
      )}
    </div>
  )
}
