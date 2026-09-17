import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Minus, Plus } from 'lucide-react'
import { toast } from 'sonner'
import {
  cartApi,
  deliverySubscriptionApi,
  flashSaleApi,
  productApi,
  restockAlertApi,
  type CreateDeliverySubscriptionBody,
} from '../api/endpoints'
import { ApiError, formatKRW } from '../api/client'
import { addGuestItem } from '../cart/guestCart'
import { useAuth } from '../auth/AuthContext'
import { notifyCartChanged } from '../hooks/useCartCount'
import { useCountdown } from '../hooks/useCountdown'
import { productStatusLabel } from '../labels'
import type { FlashSale, ProductDetail, ProductStatus } from '../api/types'
import { StarRatingDisplay } from '../components/StarRating'
import ProductReviews from '../components/ProductReviews'
import WishlistButton from '../components/WishlistButton'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Separator } from '@/components/ui/separator'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

const CYCLE_PRESETS = [7, 14, 30]

function StatusBadge({ status }: { status: ProductStatus }) {
  if (status === 'ON_SALE') return null
  const variant = status === 'SOLD_OUT' ? 'destructive' : 'secondary'
  return <Badge variant={variant}>{productStatusLabel[status]}</Badge>
}

function DetailSkeleton() {
  return (
    <div className="grid gap-8 md:grid-cols-2">
      <Skeleton className="aspect-square w-full rounded-xl" />
      <div className="space-y-4">
        <Skeleton className="h-4 w-24" />
        <Skeleton className="h-8 w-3/4" />
        <Skeleton className="h-8 w-32" />
        <Skeleton className="h-20 w-full" />
        <Skeleton className="h-10 w-full" />
        <Skeleton className="h-12 w-full" />
      </div>
    </div>
  )
}

export default function ProductDetailPage() {
  const { id } = useParams()
  const productId = Number(id)
  const { user } = useAuth()
  const navigate = useNavigate()

  const [product, setProduct] = useState<ProductDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [optionId, setOptionId] = useState<number | null>(null)
  const [quantity, setQuantity] = useState(1)
  const [submitting, setSubmitting] = useState(false)

  // 옵션별 재입고 알림 신청 여부 — 단건 조회 API가 없어 내 신청 전체 목록을 받아 optionId로 매칭한다.
  const [pendingAlertOptionIds, setPendingAlertOptionIds] = useState<Set<number>>(new Set())
  const [alertSubmitting, setAlertSubmitting] = useState(false)

  // 진행 중 타임딜 — 상품 상세 DTO에 딜 정보가 없어 공개 목록을 받아 productOptionId로 매칭한다.
  const [flashSales, setFlashSales] = useState<FlashSale[]>([])

  useEffect(() => {
    setLoading(true)
    productApi
      .detail(productId)
      .then((p) => {
        setProduct(p)
        setOptionId(p.options[0]?.id ?? null)
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [productId])

  useEffect(() => {
    flashSaleApi.list().then(setFlashSales).catch(() => setFlashSales([]))
  }, [productId])

  useEffect(() => {
    if (!user) {
      setPendingAlertOptionIds(new Set())
      return
    }
    restockAlertApi
      .myAlerts()
      .then((p) =>
        setPendingAlertOptionIds(
          new Set(p.content.filter((a) => a.status === 'PENDING').map((a) => a.optionId)),
        ),
      )
      .catch(() => setPendingAlertOptionIds(new Set()))
  }, [user])

  const selected = product?.options.find((o) => o.id === optionId) ?? null
  const maxStock = selected?.availableStock ?? 1
  const soldOut = (selected?.availableStock ?? 0) <= 0
  const alertRequested = optionId != null && pendingAlertOptionIds.has(optionId)

  const flashSaleByOption = new Map(flashSales.map((fs) => [fs.productOptionId, fs]))
  const activeFlashSale = optionId != null ? flashSaleByOption.get(optionId) : undefined

  const toggleRestockAlert = async () => {
    if (!optionId) return
    if (!user) {
      toast.info('로그인 후 재입고 알림을 신청할 수 있습니다.', {
        action: { label: '로그인', onClick: () => navigate('/login') },
      })
      return
    }
    setAlertSubmitting(true)
    try {
      if (alertRequested) {
        await restockAlertApi.unsubscribe(optionId)
        setPendingAlertOptionIds((prev) => {
          const next = new Set(prev)
          next.delete(optionId)
          return next
        })
        toast.success('재입고 알림 신청을 취소했습니다.')
      } else {
        await restockAlertApi.subscribe(optionId)
        setPendingAlertOptionIds((prev) => new Set(prev).add(optionId))
        toast.success('재입고 시 알림으로 알려드릴게요.')
      }
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : '요청에 실패했습니다.')
    } finally {
      setAlertSubmitting(false)
    }
  }

  const addToCart = async () => {
    if (!optionId) return
    // 비회원은 localStorage 게스트 장바구니에 담는다(서버 저장은 회원 전용).
    if (!user) {
      addGuestItem(optionId, quantity)
      toast.success('장바구니에 담았습니다.', {
        description: '비회원 장바구니에 담김',
        action: { label: '장바구니 보기', onClick: () => navigate('/cart') },
      })
      return
    }
    setSubmitting(true)
    try {
      await cartApi.addItem(optionId, quantity)
      notifyCartChanged()
      toast.success('장바구니에 담았습니다.', {
        action: { label: '장바구니 보기', onClick: () => navigate('/cart') },
      })
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : '담기에 실패했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) return <DetailSkeleton />

  if (error && !product) {
    return (
      <div className="mx-auto max-w-sm rounded-lg border border-border bg-card p-8 text-center shadow-sm">
        <p className="text-2xl">⚠️</p>
        <p className="mt-2 text-sm text-muted-foreground">{error}</p>
        <Button asChild variant="outline" className="mt-4">
          <Link to="/">목록으로</Link>
        </Button>
      </div>
    )
  }
  if (!product) return null

  const purchasable =
    product.status === 'ON_SALE' && (selected?.availableStock ?? 0) > 0

  const setQty = (n: number) => setQuantity(Math.min(maxStock, Math.max(1, n)))

  return (
    <div className="grid gap-8 md:grid-cols-2">
      <div className="flex aspect-square items-center justify-center overflow-hidden rounded-xl bg-muted text-6xl">
        {product.imageUrl ? (
          <img src={product.imageUrl} alt={product.name} className="size-full object-cover" />
        ) : (
          '🛍️'
        )}
      </div>

      <div>
        <div className="flex items-start justify-between gap-2">
          <p className="text-sm text-muted-foreground">{product.storeName}</p>
          <WishlistButton productId={product.id} />
        </div>
        <h1 className="mt-1 text-2xl font-bold">{product.name}</h1>
        {product.reviewCount > 0 && (
          <a href="#reviews" className="mt-2 inline-flex items-center gap-1">
            <StarRatingDisplay rating={product.avgRating} reviewCount={product.reviewCount} />
          </a>
        )}
        {product.status !== 'ON_SALE' && (
          <div className="mt-2">
            <StatusBadge status={product.status} />
          </div>
        )}

        {activeFlashSale ? (
          <FlashSaleBanner flashSale={activeFlashSale} />
        ) : (
          <p className="mt-4 text-2xl font-bold text-primary">
            {formatKRW(selected?.price ?? product.basePrice)}
          </p>
        )}

        {product.description && (
          <p className="mt-4 whitespace-pre-line text-sm text-muted-foreground">
            {product.description}
          </p>
        )}

        <Separator className="my-6" />

        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="option-select">옵션</Label>
            <Select
              value={optionId != null ? String(optionId) : undefined}
              onValueChange={(v) => {
                setOptionId(Number(v))
                setQuantity(1)
              }}
            >
              <SelectTrigger id="option-select" className="w-full">
                <SelectValue placeholder="옵션 선택" />
              </SelectTrigger>
              <SelectContent>
                {product.options.map((o) => (
                  <SelectItem
                    key={o.id}
                    value={String(o.id)}
                    disabled={o.availableStock <= 0}
                  >
                    {o.name} · {formatKRW(o.price)}
                    {o.availableStock <= 0
                      ? ' (품절)'
                      : ` (재고 ${o.availableStock})`}
                    {flashSaleByOption.has(o.id) && ' ⏰ 타임딜'}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <Label htmlFor="qty-input">수량</Label>
            <div className="flex items-center gap-2">
              <Button
                type="button"
                variant="outline"
                size="icon"
                className="size-11"
                aria-label="수량 감소"
                disabled={quantity <= 1}
                onClick={() => setQty(quantity - 1)}
              >
                <Minus className="size-4" />
              </Button>
              <input
                id="qty-input"
                type="number"
                min={1}
                max={maxStock}
                value={quantity}
                onChange={(e) => setQty(Number(e.target.value))}
                className="h-11 w-16 rounded-md border border-input bg-background text-center text-base outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />
              <Button
                type="button"
                variant="outline"
                size="icon"
                className="size-11"
                aria-label="수량 증가"
                disabled={quantity >= maxStock}
                onClick={() => setQty(quantity + 1)}
              >
                <Plus className="size-4" />
              </Button>
            </div>
          </div>
        </div>

        {soldOut ? (
          <Button
            type="button"
            variant={alertRequested ? 'outline' : 'default'}
            onClick={toggleRestockAlert}
            disabled={alertSubmitting}
            className="mt-6 h-12 w-full text-base"
          >
            {alertRequested ? '재입고 알림 신청됨 (취소)' : '재입고 알림 신청'}
          </Button>
        ) : (
          <div className="mt-6 space-y-2">
            <Button
              onClick={addToCart}
              disabled={!purchasable || submitting}
              className="h-12 w-full text-base"
            >
              {purchasable ? '장바구니에 담기' : '구매할 수 없는 상품'}
            </Button>
            {purchasable && optionId != null && selected && (
              <DeliverySubscribeEntry
                optionId={optionId}
                optionName={selected.name}
                maxStock={maxStock}
              />
            )}
          </div>
        )}
      </div>

      <div className="md:col-span-2">
        <Separator className="mb-8" />
        <ProductReviews productId={productId} />
      </div>
    </div>
  )
}

/**
 * 정기배송 신청 진입점 — 일반 담기 버튼 옆 보조 버튼(기획서 §6). 클릭 시 옵션/수량/주기/배송지 폼을
 * 펼치고, 결제수단(빌링키) 미등록 시(DSUB-002) 카드 등록 폼을 이어서 보여준다. 멤버십(`MyMembershipPage`)의
 * "카드 먼저 등록" UX 패턴을 재사용하되, 정기배송 전용 빌링키 테이블을 쓴다.
 */
function DeliverySubscribeEntry({
  optionId,
  optionName,
  maxStock,
}: {
  optionId: number
  optionName: string
  maxStock: number
}) {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)

  const toggle = () => {
    if (!user) {
      toast.info('로그인 후 정기배송을 신청할 수 있습니다.', {
        action: { label: '로그인', onClick: () => navigate('/login') },
      })
      return
    }
    setOpen((v) => !v)
  }

  return (
    <>
      <Button type="button" variant="outline" onClick={toggle} className="h-11 w-full">
        {open ? '정기배송 신청 접기' : '정기배송으로 신청'}
      </Button>
      {open && user && (
        <DeliverySubscribeForm
          optionId={optionId}
          optionName={optionName}
          maxStock={maxStock}
          ordererName={user.name}
          ordererEmail={user.email}
          onDone={() => setOpen(false)}
        />
      )}
    </>
  )
}

function DeliverySubscribeForm({
  optionId,
  optionName,
  maxStock,
  ordererName,
  ordererEmail,
  onDone,
}: {
  optionId: number
  optionName: string
  maxStock: number
  ordererName: string
  ordererEmail: string
  onDone: () => void
}) {
  const [quantity, setQuantity] = useState(1)
  const [cycleDays, setCycleDays] = useState(CYCLE_PRESETS[0])
  const [startImmediately, setStartImmediately] = useState(true)
  const [form, setForm] = useState({
    ordererName,
    ordererPhone: '',
    ordererEmail,
    receiverName: '',
    receiverPhone: '',
    zipcode: '',
    address1: '',
    address2: '',
  })
  const [needsCard, setNeedsCard] = useState(false)
  const [cardNumber, setCardNumber] = useState('')
  const [registering, setRegistering] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm({ ...form, [k]: e.target.value })

  const registerCard = async (e: React.FormEvent) => {
    e.preventDefault()
    setRegistering(true)
    setError(null)
    try {
      await deliverySubscriptionApi.registerBillingKey(cardNumber.trim())
      toast.success('카드가 등록되었습니다. 다시 신청해 주세요.')
      setNeedsCard(false)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '카드 등록에 실패했습니다.')
    } finally {
      setRegistering(false)
    }
  }

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    const body: CreateDeliverySubscriptionBody = {
      optionId,
      quantity,
      cycleDays,
      ordererName: form.ordererName,
      ordererPhone: form.ordererPhone,
      ordererEmail: form.ordererEmail,
      shippingAddress: {
        receiverName: form.receiverName,
        receiverPhone: form.receiverPhone,
        zipcode: form.zipcode,
        address1: form.address1,
        address2: form.address2 || undefined,
      },
      startImmediately,
    }
    try {
      await deliverySubscriptionApi.create(body)
      toast.success('정기배송이 등록되었습니다.', {
        action: { label: '내 정기배송 보기', onClick: () => window.location.assign('/my/delivery-subscriptions') },
      })
      onDone()
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : '정기배송 등록에 실패했습니다.'
      setError(msg)
      if (e instanceof ApiError && e.code === 'DSUB-002') {
        setNeedsCard(true)
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Card className="mt-2">
      <CardHeader>
        <CardTitle className="text-base">정기배송 신청 · {optionName}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {needsCard && (
          <form onSubmit={registerCard} className="space-y-2 rounded-lg border border-border bg-muted/40 p-3">
            <p className="text-sm font-medium">먼저 결제수단(카드)을 등록해 주세요.</p>
            <div className="flex flex-col gap-2 sm:flex-row">
              <Input
                required
                inputMode="numeric"
                placeholder="카드번호(숫자만, 테스트 환경)"
                value={cardNumber}
                onChange={(e) => setCardNumber(e.target.value)}
              />
              <Button type="submit" disabled={registering} className="shrink-0">
                {registering ? '등록 중…' : '카드 등록'}
              </Button>
            </div>
          </form>
        )}

        <form onSubmit={submit} className="space-y-3 text-sm">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label className="mb-1 text-xs text-muted-foreground">수량</Label>
              <Input
                type="number"
                min={1}
                max={maxStock}
                value={quantity}
                onChange={(e) => setQuantity(Math.min(maxStock, Math.max(1, Number(e.target.value))))}
              />
            </div>
            <div>
              <Label className="mb-1 text-xs text-muted-foreground">배송 주기</Label>
              <Select value={String(cycleDays)} onValueChange={(v) => setCycleDays(Number(v))}>
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {CYCLE_PRESETS.map((d) => (
                    <SelectItem key={d} value={String(d)}>
                      {d}일마다
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <Input required placeholder="주문자 이름" value={form.ordererName} onChange={set('ordererName')} />
            <Input required placeholder="주문자 연락처" value={form.ordererPhone} onChange={set('ordererPhone')} />
            <Input
              required
              type="email"
              placeholder="주문자 이메일"
              value={form.ordererEmail}
              onChange={set('ordererEmail')}
              className="col-span-2"
            />
          </div>

          <Separator />

          <div className="grid grid-cols-2 gap-3">
            <Input required placeholder="받는 분" value={form.receiverName} onChange={set('receiverName')} />
            <Input required placeholder="받는 분 연락처" value={form.receiverPhone} onChange={set('receiverPhone')} />
            <Input required placeholder="우편번호" value={form.zipcode} onChange={set('zipcode')} />
            <Input required placeholder="기본 주소" value={form.address1} onChange={set('address1')} />
            <Input
              placeholder="상세 주소(선택)"
              value={form.address2}
              onChange={set('address2')}
              className="col-span-2"
            />
          </div>

          <label className="flex items-center gap-2 text-xs text-muted-foreground">
            <input
              type="checkbox"
              checked={startImmediately}
              onChange={(e) => setStartImmediately(e.target.checked)}
            />
            등록 즉시 첫 회차 주문(체크 해제 시 다음 주기부터 시작)
          </label>

          {error && <p className="text-sm text-destructive">{error}</p>}
          <Button type="submit" disabled={submitting} className="w-full">
            {submitting ? '신청 중…' : '정기배송 신청'}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}

const URGENT_MS = 10 * 60 * 1000 // 종료 임박(10분 이내) 경고색 강조

/** 진행 중 타임딜 배너 — 정가 대신 특가로 가격 표시를 전환하고 카운트다운을 노출한다. */
function FlashSaleBanner({ flashSale }: { flashSale: FlashSale }) {
  const { label, ended, remainingMs } = useCountdown(flashSale.endAt)
  const urgent = remainingMs > 0 && remainingMs <= URGENT_MS

  return (
    <div className="mt-4 rounded-lg border border-destructive/30 bg-destructive/5 p-4">
      <div className="flex items-center justify-between gap-2">
        <span className="text-sm font-semibold text-destructive">⏰ 타임딜 진행중</span>
        <span
          role="timer"
          aria-live="off"
          className={`text-sm font-semibold tabular-nums ${
            urgent ? 'text-destructive' : 'text-muted-foreground'
          }`}
        >
          {ended ? '곧 종료됩니다' : `남은 시간 ${label}`}
        </span>
      </div>
      <div className="mt-2 flex items-baseline gap-2">
        <span className="text-2xl font-bold text-destructive">
          {formatKRW(flashSale.salePrice)}
        </span>
        <span className="text-sm text-muted-foreground line-through">
          {formatKRW(flashSale.originalPrice)}
        </span>
      </div>
      <p className="mt-1 text-xs text-muted-foreground">
        {flashSale.remainingQuantity > 0
          ? `한정 ${flashSale.remainingQuantity}개 남음`
          : '한도 소진 — 곧 종료됩니다'}
      </p>
    </div>
  )
}
