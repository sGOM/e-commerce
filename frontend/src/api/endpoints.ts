import { api } from './client'
import type {
  AdminDeliverySubscription,
  AdminLoyaltyTierResponse,
  AdminMembership,
  AdminUser,
  AuditLog,
  AppNotification,
  Cart,
  CartReminderBatchResult,
  Category,
  CollectionDetail,
  CollectionStatus,
  CollectionSummary,
  Coupon,
  DeliveryRegion,
  DeliverySlot,
  DeliverySlotType,
  DeliverySubscription,
  DeliverySubscriptionBillingKey,
  DeliverySubscriptionBillingRunResult,
  DeliverySubscriptionHistory,
  DeliverySubscriptionPolicy,
  DeliverySubscriptionStatus,
  FlashSale,
  FlashSaleStatus,
  GiftClaim,
  GiftClaimStatus,
  GiftExpiryBatchResult,
  GiftPolicy,
  GiftPreview,
  IssuedCoupon,
  LoyaltyTier,
  LoyaltyTierBatchResult,
  Membership,
  MembershipBillingHistory,
  MembershipBillingKey,
  MembershipBillingRunResult,
  MembershipPlan,
  MembershipPolicy,
  MembershipStatus,
  MyLoyaltyTierResponse,
  Order,
  OrderStatus,
  OrderSummary,
  PageResponse,
  PointPolicy,
  Payment,
  PointSummary,
  PopularProduct,
  ProductDetail,
  ProductSummary,
  RestockAlert,
  Review,
  ReviewableOrderItem,
  ReviewRewardPolicy,
  ReviewSort,
  ReviewStatus,
  Seller,
  SellerStatus,
  SellerSubOrder,
  ProductSort,
  SellerProduct,
  Settlement,
  SettlementStatus,
  SubOrderStatus,
  User,
  UserAddress,
  UserStatus,
  WishlistResponse,
} from './types'

// ----- 인증 -----
export const authApi = {
  me: () => api.get<User>('/api/auth/me'),
  login: (email: string, password: string) =>
    api.post<User>('/api/auth/login', { email, password }),
  signup: (email: string, password: string, name: string) =>
    api.post<User>('/api/auth/signup', { email, password, name }),
  logout: () => api.post<void>('/api/auth/logout'),
  /** 소셜 전용 계정(비밀번호 미설정)은 currentPassword 없이 설정할 수 있다. */
  changePassword: (newPassword: string, currentPassword?: string) =>
    api.patch<void>('/api/auth/password', { currentPassword, newPassword }),
}

// ----- 내 쿠폰/포인트 -----
export const meApi = {
  coupons: () => api.get<IssuedCoupon[]>('/api/me/coupons'),
  points: () => api.get<PointSummary>('/api/me/points'),
}

// ----- 배송지 주소록(회원 전용) -----
export interface AddressBody {
  label?: string
  receiverName: string
  receiverPhone: string
  zipcode: string
  address1: string
  address2?: string
  /** 등록 시에만 반영된다. 수정에서 기본 여부는 setDefault 로 바꾼다. */
  isDefault?: boolean
}

export const addressApi = {
  list: () => api.get<UserAddress[]>('/api/me/addresses'),
  create: (body: AddressBody) => api.post<UserAddress>('/api/me/addresses', body),
  update: (id: number, body: AddressBody) => api.put<UserAddress>(`/api/me/addresses/${id}`, body),
  setDefault: (id: number) => api.patch<UserAddress>(`/api/me/addresses/${id}/default`),
  remove: (id: number) => api.del<void>(`/api/me/addresses/${id}`),
}

// ----- 유료 멤버십(구독) — 회원 전용, 카드(빌링키) 등록 후 가입 가능 -----
export const membershipApi = {
  // Mock 게이트웨이는 형식만 검증(숫자 12~16자리)하고 실 카드 통신은 하지 않는다.
  registerBillingKey: (cardNumber: string) =>
    api.post<MembershipBillingKey>('/api/me/membership/billing-key', { cardNumber }),
  subscribe: (plan: MembershipPlan = 'BASIC') =>
    api.post<Membership>('/api/me/membership', { plan }),
  // 미가입 시 404(MEMBERSHIP-001) — 호출부에서 ApiError 로 잡아 가입 CTA 를 보여준다.
  my: () => api.get<Membership>('/api/me/membership'),
  cancel: () => api.del<Membership>('/api/me/membership'),
}

// ----- 재입고 알림 -----
export const restockAlertApi = {
  // 옵션별 신청 여부를 알려주는 단건 조회 API가 없어, 전체 목록을 받아 optionId로 클라이언트에서 매칭한다.
  myAlerts: (page = 0, size = 50) =>
    api.get<PageResponse<RestockAlert>>(`/api/me/restock-alerts?page=${page}&size=${size}`),
  subscribe: (optionId: number) =>
    api.post<RestockAlert>(`/api/products/options/${optionId}/restock-alerts`),
  unsubscribe: (optionId: number) =>
    api.del<void>(`/api/products/options/${optionId}/restock-alerts`),
}

// ----- 위시리스트(찜) + 가격 인하 알림(회원 전용) -----
export const wishlistApi = {
  my: (params: { priceDropOnly?: boolean; page?: number; size?: number } = {}) => {
    const q = new URLSearchParams()
    if (params.priceDropOnly) q.set('priceDropOnly', 'true')
    q.set('page', String(params.page ?? 0))
    q.set('size', String(params.size ?? 20))
    return api.get<PageResponse<WishlistResponse>>(`/api/me/wishlist?${q.toString()}`)
  },
  add: (productId: number) => api.post<WishlistResponse>('/api/me/wishlist', { productId }),
  remove: (productId: number) => api.del<void>(`/api/me/wishlist/${productId}`),
}

// ----- 로열티 등급(회원 전용) -----
export const loyaltyTierApi = {
  my: () => api.get<MyLoyaltyTierResponse>('/api/me/loyalty-tier'),
}

// ----- 로열티 등급(관리자) -----
export const adminLoyaltyTierApi = {
  search: (params: { tier?: LoyaltyTier; page?: number; size?: number } = {}) => {
    const q = new URLSearchParams()
    q.set('tier', params.tier ?? 'BRONZE')
    q.set('page', String(params.page ?? 0))
    q.set('size', String(params.size ?? 20))
    return api.get<PageResponse<AdminLoyaltyTierResponse>>(`/api/admin/loyalty-tiers?${q.toString()}`)
  },
  detail: (userId: number) => api.get<AdminLoyaltyTierResponse>(`/api/admin/loyalty-tiers/${userId}`),
  recalculate: () =>
    api.post<LoyaltyTierBatchResult>('/api/admin/loyalty-tiers/recalculate/run'),
}

// ----- 장바구니 이탈 리마인드(관리자 수동 트리거) -----
export const adminCartReminderApi = {
  run: () => api.post<CartReminderBatchResult>('/api/admin/cart-reminders/run'),
}

// ----- 범용 인앱 알림함 (재입고 외 향후 알림도 재사용) -----
export interface NotificationPage extends PageResponse<AppNotification> {
  unreadCount: number
}

export const notificationApi = {
  list: (params: { unreadOnly?: boolean; page?: number; size?: number } = {}): Promise<NotificationPage> => {
    const q = new URLSearchParams()
    if (params.unreadOnly) q.set('unreadOnly', 'true')
    q.set('page', String(params.page ?? 0))
    q.set('size', String(params.size ?? 20))
    // 서버 응답은 { notifications: Page<AppNotification>, unreadCount } 형태다.
    // 호출부(벨/알림함)는 플랫한 NotificationPage(= Page + unreadCount)를 기대하므로 여기서 병합해 준다.
    return api
      .get<{ notifications: PageResponse<AppNotification>; unreadCount: number }>(
        `/api/me/notifications?${q.toString()}`,
      )
      .then((d) => ({ ...d.notifications, unreadCount: d.unreadCount }))
  },
  markRead: (id: number) => api.patch<AppNotification>(`/api/me/notifications/${id}/read`),
}

// ----- 내 리뷰(마이페이지) -----
export const meReviewApi = {
  // HIDDEN 포함 — 화면에서 "관리자 숨김" 안내로 구분해 보여준다.
  myReviews: (page = 0, size = 20) =>
    api.get<PageResponse<Review>>(`/api/me/reviews?page=${page}&size=${size}`),
  reviewable: () =>
    api.get<ReviewableOrderItem[]>('/api/me/orders/reviewable'),
}

// ----- 카테고리(공개) -----
export const categoryApi = {
  list: () => api.get<Category[]>('/api/categories'),
}

// ----- 상품 -----
export const productApi = {
  search: (params: {
    keyword?: string
    categoryId?: number
    sellerId?: number
    minPrice?: number
    maxPrice?: number
    sort?: ProductSort
    page?: number
    size?: number
  }) => {
    const q = new URLSearchParams()
    if (params.keyword) q.set('keyword', params.keyword)
    if (params.categoryId != null) q.set('categoryId', String(params.categoryId))
    if (params.sellerId != null) q.set('sellerId', String(params.sellerId))
    if (params.minPrice != null) q.set('minPrice', String(params.minPrice))
    if (params.maxPrice != null) q.set('maxPrice', String(params.maxPrice))
    if (params.sort) q.set('sort', params.sort)
    q.set('page', String(params.page ?? 0))
    q.set('size', String(params.size ?? 20))
    return api.get<PageResponse<ProductSummary>>(`/api/products?${q.toString()}`)
  },
  popular: (limit = 8) =>
    api.get<PopularProduct[]>(`/api/products/popular?limit=${limit}`),
  detail: (id: number) => api.get<ProductDetail>(`/api/products/${id}`),
}

// ----- 기획전/컬렉션(공개) -----
export const collectionApi = {
  // 진행 중(PUBLISHED + 기간 내 + 상품 1개 이상)만 노출, displayOrder 순.
  list: () => api.get<CollectionSummary[]>('/api/collections'),
  detail: (id: number) => api.get<CollectionDetail>(`/api/collections/${id}`),
}

// ----- 타임딜(공개) -----
export const flashSaleApi = {
  // 진행 중(ONGOING)만 마감임박순으로 내려온다. 응답은 화면 표시용 — 실제 가격은 주문 시점에 서버가 재계산.
  list: () => api.get<FlashSale[]>('/api/flash-sales'),
  detail: (id: number) => api.get<FlashSale>(`/api/flash-sales/${id}`),
}

// ----- 배송 슬롯(공개 조회) -----
export const deliverySlotApi = {
  // 인증 불필요 — 게스트도 체크아웃 전에 조회한다. date 없으면 서버가 오늘(KST)로 간주.
  list: (params: { postalCode?: string; date?: string }) => {
    const q = new URLSearchParams()
    if (params.postalCode) q.set('postalCode', params.postalCode)
    if (params.date) q.set('date', params.date)
    return api.get<DeliverySlot[]>(`/api/delivery-slots?${q.toString()}`)
  },
}

// ----- 상품 리뷰(공개 조회 + 작성/수정/삭제/신고는 회원) -----
export interface CreateReviewBody {
  orderItemId: number
  rating: number
  content: string
  imageUrls?: string[]
}
export interface UpdateReviewBody {
  rating: number
  content: string
  imageUrls?: string[]
}

export const reviewApi = {
  // 상품 상세 리뷰 탭 — 공개(비로그인) 접근 가능, HIDDEN 은 서버에서 제외되고 작성자명은 마스킹된다.
  byProduct: (
    productId: number,
    params: { sort?: ReviewSort; photoOnly?: boolean; page?: number; size?: number },
  ) => {
    const q = new URLSearchParams()
    q.set('sort', params.sort ?? 'LATEST')
    if (params.photoOnly) q.set('photoOnly', 'true')
    q.set('page', String(params.page ?? 0))
    q.set('size', String(params.size ?? 10))
    return api.get<PageResponse<Review>>(`/api/products/${productId}/reviews?${q.toString()}`)
  },
  create: (body: CreateReviewBody) => api.post<Review>('/api/reviews', body),
  update: (id: number, body: UpdateReviewBody) =>
    api.patch<Review>(`/api/reviews/${id}`, body),
  remove: (id: number) => api.del<void>(`/api/reviews/${id}`),
  report: (id: number, reason: string) =>
    api.post<void>(`/api/reviews/${id}/reports`, { reason }),
}

export interface CreateOptionBody {
  name: string
  sku: string
  additionalPrice: number
  stockQuantity: number
}
export interface CreateProductBody {
  name: string
  basePrice: number
  description?: string
  status: 'DRAFT' | 'ON_SALE' | 'SOLD_OUT' | 'HIDDEN'
  dawnDeliveryEligible?: boolean
  imageUrl?: string
  options: CreateOptionBody[]
}

// ----- 이미지 업로드 -----
export const uploadApi = {
  image: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return api.post<{ url: string }>('/api/uploads', form)
  },
}

// ----- 관리자 대시보드 -----
export interface AdminDashboard {
  from: string
  to: string
  orderCount: number
  gmv: number
  newUserCount: number
  daily: { date: string; orderCount: number; gmv: number }[]
}

export const adminDashboardApi = {
  get: (from: string, to: string) => api.get<AdminDashboard>(`/api/admin/dashboard?from=${from}&to=${to}`),
}

// ----- 판매자 백오피스 -----
export interface SellerDashboard {
  from: string
  to: string
  orderCount: number
  salesAmount: number
  unsettledAmount: number
  pendingPayoutAmount: number
}

export const sellerApi = {
  dashboard: (from: string, to: string) =>
    api.get<SellerDashboard>(`/api/seller/dashboard?from=${from}&to=${to}`),
  apply: (storeName: string, description?: string) =>
    api.post<Seller>('/api/seller/apply', { storeName, description }),
  myStore: () => api.get<Seller>('/api/seller/store'),
  myProducts: () => api.get<SellerProduct[]>('/api/seller/products'),
  createProduct: (body: CreateProductBody) =>
    api.post<unknown>('/api/seller/products', body),
  adjustStock: (productId: number, optionId: number, quantity: number) =>
    api.patch<unknown>(`/api/seller/products/${productId}/stock`, { optionId, quantity }),
  listOrders: (status?: SubOrderStatus) =>
    api.get<SellerSubOrder[]>(
      `/api/seller/orders${status ? `?status=${status}` : ''}`,
    ),
  ship: (subOrderId: number, courier: string, trackingNumber: string) =>
    api.post<SellerSubOrder>(`/api/seller/orders/${subOrderId}/ship`, {
      courier,
      trackingNumber,
    }),
  settlements: () => api.get<Settlement[]>('/api/seller/settlements'),
}

// ----- 타임딜(판매자) -----
export interface CreateFlashSaleBody {
  productOptionId: number
  salePrice: number
  startAt: string
  endAt: string
  limitQuantity: number
}

export const sellerFlashSaleApi = {
  list: () => api.get<FlashSale[]>('/api/seller/flash-sales'),
  create: (body: CreateFlashSaleBody) => api.post<FlashSale>('/api/seller/flash-sales', body),
}

export interface CreateCouponBody {
  name: string
  discountType: 'RATE' | 'FIXED'
  discountValue: number
  minOrderAmount: number
  maxDiscountAmount?: number | null
  validFrom: string
  validUntil: string
  issueToUserIds?: number[]
}

// ----- 관리자 백오피스 -----
export const adminApi = {
  listSellers: (status?: SellerStatus) =>
    api.get<Seller[]>(`/api/admin/sellers${status ? `?status=${status}` : ''}`),
  approveSeller: (sellerId: number, approved: boolean) =>
    api.patch<Seller>(`/api/admin/sellers/${sellerId}/approve`, { approved }),
  searchOrders: (params: { status?: OrderStatus; page?: number }) => {
    const q = new URLSearchParams()
    if (params.status) q.set('status', params.status)
    q.set('page', String(params.page ?? 0))
    return api.get<PageResponse<OrderSummary>>(`/api/admin/orders?${q.toString()}`)
  },
  refundOrder: (orderId: number) => api.post<Order>(`/api/admin/orders/${orderId}/refund`),
  createCoupon: (body: CreateCouponBody) => api.post<Coupon>('/api/admin/coupons', body),
  createCategory: (name: string, parentId?: number | null) =>
    api.post<Category>('/api/admin/categories', { name, parentId }),
  updateCategory: (categoryId: number, body: { name: string; parentId: number | null; sortOrder: number }) =>
    api.put<Category>(`/api/admin/categories/${categoryId}`, body),
  deleteCategory: (categoryId: number) => api.del<void>(`/api/admin/categories/${categoryId}`),
  listSettlements: (params: { status?: SettlementStatus; page?: number }) => {
    const q = new URLSearchParams()
    if (params.status) q.set('status', params.status)
    q.set('page', String(params.page ?? 0))
    return api.get<PageResponse<Settlement>>(`/api/admin/settlements?${q.toString()}`)
  },
  generateSettlements: () => api.post<Settlement[]>('/api/admin/settlements'),
  paySettlement: (settlementId: number) =>
    api.patch<Settlement>(`/api/admin/settlements/${settlementId}/pay`),
  getSettlementPolicy: () =>
    api.get<{ commissionRateBp: number }>('/api/admin/settlements/policy'),
  updateSettlementPolicy: (commissionRateBp: number) =>
    api.patch<{ commissionRateBp: number }>('/api/admin/settlements/policy', {
      commissionRateBp,
    }),
}

// ----- 타임딜(관리자) -----
export const adminFlashSaleApi = {
  search: (params: { status?: FlashSaleStatus | ''; sellerId?: number; page?: number; size?: number }) => {
    const q = new URLSearchParams()
    if (params.status) q.set('status', params.status)
    if (params.sellerId != null) q.set('sellerId', String(params.sellerId))
    q.set('page', String(params.page ?? 0))
    q.set('size', String(params.size ?? 20))
    return api.get<PageResponse<FlashSale>>(`/api/admin/flash-sales?${q.toString()}`)
  },
  detail: (id: number) => api.get<FlashSale>(`/api/admin/flash-sales/${id}`),
  create: (body: CreateFlashSaleBody) => api.post<FlashSale>('/api/admin/flash-sales', body),
  cancel: (id: number) => api.patch<FlashSale>(`/api/admin/flash-sales/${id}/cancel`),
}

// ----- 관리자 리뷰 검수 -----
export const adminReviewApi = {
  search: (params: {
    status?: ReviewStatus
    productId?: number
    userId?: number
    reported?: boolean
    page?: number
  }) => {
    const q = new URLSearchParams()
    if (params.status) q.set('status', params.status)
    if (params.productId != null) q.set('productId', String(params.productId))
    if (params.userId != null) q.set('userId', String(params.userId))
    if (params.reported != null) q.set('reported', String(params.reported))
    q.set('page', String(params.page ?? 0))
    return api.get<PageResponse<Review>>(`/api/admin/reviews?${q.toString()}`)
  },
  changeStatus: (id: number, status: 'VISIBLE' | 'HIDDEN') =>
    api.patch<Review>(`/api/admin/reviews/${id}/status`, { status }),
  getPolicy: () => api.get<ReviewRewardPolicy>('/api/admin/review-policy'),
  updatePolicy: (body: Partial<ReviewRewardPolicy>) =>
    api.patch<ReviewRewardPolicy>('/api/admin/review-policy', body),
}

// ----- 관리자 기획전/컬렉션 편성 -----
export interface CollectionRequestBody {
  title: string
  subtitle?: string
  bannerImageUrl?: string
  startAt: string
  endAt: string
  displayOrder: number
}

export const adminCollectionApi = {
  search: (params: { status?: CollectionStatus; page?: number; size?: number }) => {
    const q = new URLSearchParams()
    if (params.status) q.set('status', params.status)
    q.set('page', String(params.page ?? 0))
    q.set('size', String(params.size ?? 20))
    return api.get<PageResponse<CollectionSummary>>(`/api/admin/collections?${q.toString()}`)
  },
  detail: (id: number) => api.get<CollectionDetail>(`/api/admin/collections/${id}`),
  create: (body: CollectionRequestBody) =>
    api.post<CollectionDetail>('/api/admin/collections', body),
  update: (id: number, body: CollectionRequestBody) =>
    api.put<CollectionDetail>(`/api/admin/collections/${id}`, body),
  changeStatus: (id: number, status: CollectionStatus) =>
    api.patch<CollectionDetail>(`/api/admin/collections/${id}/status`, { status }),
  // 상품 편성 전체 교체 — productIds 순서 = 노출 순서(displayOrder). 부분추가/삭제 API는 없다.
  replaceProducts: (id: number, productIds: number[]) =>
    api.put<CollectionDetail>(`/api/admin/collections/${id}/products`, { productIds }),
}

export interface GuestCartLine {
  optionId: number
  quantity: number
}

// ----- 장바구니 -----
export const cartApi = {
  get: () => api.get<Cart>('/api/cart'),
  addItem: (optionId: number, quantity: number) =>
    api.post<Cart>('/api/cart/items', { optionId, quantity }),
  updateItem: (itemId: number, quantity: number) =>
    api.patch<Cart>(`/api/cart/items/${itemId}`, { quantity }),
  removeItem: (itemId: number) => api.del<Cart>(`/api/cart/items/${itemId}`),
  // 게스트 장바구니 계산(무상태) — localStorage 항목을 그대로 보내 현재가/재고/구매가능 계산
  guestPreview: (items: GuestCartLine[]) =>
    api.post<Cart>('/api/cart/guest', { items }),
  // 로그인 시 게스트 장바구니를 서버 장바구니로 병합
  merge: (items: GuestCartLine[]) => api.post<Cart>('/api/cart/merge', { items }),
}

export interface ShippingAddressBody {
  receiverName: string
  receiverPhone: string
  zipcode: string
  address1: string
  address2?: string
}

/** 배송 슬롯 선택(SubOrder=판매자 단위, `docs/planning/delivery-slot.md` AC6). */
export interface DeliverySlotSelectionBody {
  sellerId: number
  deliverySlotId: number
}

/**
 * 선물 주문([isGift] = true, `docs/planning/gift-order.md`)은 [shippingAddress] 를 생략하고
 * [giftMessage] 를 담을 수 있다 — 수령자가 나중에 링크로 배송지를 입력한다. 배송 슬롯은 배송지 기반
 * 검증이 필요해 선물 주문에서는 지원하지 않는다(비어있지 않으면 서버가 GIFT-005로 거부).
 */
export interface CreateOrderBody {
  ordererName: string
  ordererPhone: string
  ordererEmail: string
  shippingAddress?: ShippingAddressBody | null
  issuedCouponId?: number | null
  usePoint?: number
  deliverySlotSelections?: DeliverySlotSelectionBody[]
  isGift?: boolean
  giftMessage?: string | null
}

export interface GuestOrderBody {
  ordererName: string
  ordererPhone: string
  ordererEmail: string
  shippingAddress: ShippingAddressBody
  items: GuestCartLine[]
  deliverySlotSelections?: DeliverySlotSelectionBody[]
}

// ----- 주문 / 결제 -----
export const orderApi = {
  create: (body: CreateOrderBody) => api.post<Order>('/api/orders', body),
  createGuest: (body: GuestOrderBody) => api.post<Order>('/api/orders/guest', body),
  guestLookup: (orderNumber: string, ordererPhone: string) =>
    api.post<Order>('/api/orders/guest/lookup', { orderNumber, ordererPhone }),
  claim: (orderNumber: string, ordererPhone: string) =>
    api.post<Order>('/api/orders/claim', { orderNumber, ordererPhone }),
  myOrders: (page = 0, size = 20) =>
    api.get<PageResponse<OrderSummary>>(`/api/orders?page=${page}&size=${size}`),
  detail: (orderId: number) => api.get<Order>(`/api/orders/${orderId}`),
  cancel: (orderId: number) => api.post<Order>(`/api/orders/${orderId}/cancel`),
  pay: (orderId: number) => api.post<Payment>(`/api/payments/${orderId}`),
  payGuest: (orderNumber: string, ordererPhone: string) =>
    api.post<Payment>('/api/payments/guest', { orderNumber, ordererPhone }),
  // 하위 주문(판매자 단위) 수령 확인(구매확정). SHIPPED → DELIVERED. 이후 해당 항목에 리뷰를 쓸 수 있다.
  confirmDelivery: (subOrderId: number) =>
    api.post<Order>(`/api/orders/sub-orders/${subOrderId}/confirm-delivery`),
  // 선물 링크 상태 재확인(회원, 본인 주문). 주문 생성 직후 안내한 공유 링크가 만료/수락되었는지 확인한다.
  giftStatus: (orderId: number) => api.get<GiftClaim>(`/api/orders/${orderId}/gift`),
  // 구매자의 선물 주문 취소(수락 전이면 전액 환불, 이후는 일반 취소 정책과 동일).
  cancelGift: (orderId: number) => api.post<Order>(`/api/orders/${orderId}/gift/cancel`),
}

// ----- 선물하기(공개, 수령자 — 로그인 불필요) -----
export const giftApi = {
  // 미리보기(상품/보낸사람/메시지, 가격 미노출). 만료/취소/이미수락 상태도 status 로 함께 내려온다.
  preview: (token: string) => api.get<GiftPreview>(`/api/gift/${token}`),
  // 배송지 입력 및 수락(1회성). 실패 시 GIFT-002~004 코드로 상태별 안내를 구분한다.
  claim: (token: string, shippingAddress: ShippingAddressBody) =>
    api.post<GiftClaim>(`/api/gift/${token}/claim`, { shippingAddress }),
}

// ----- 선물하기(관리자) -----
export const adminGiftClaimApi = {
  search: (params: { status?: GiftClaimStatus | ''; page?: number; size?: number }) => {
    const q = new URLSearchParams()
    if (params.status) q.set('status', params.status)
    q.set('page', String(params.page ?? 0))
    q.set('size', String(params.size ?? 20))
    return api.get<PageResponse<GiftClaim>>(`/api/admin/gift-claims?${q.toString()}`)
  },
  getPolicy: () => api.get<GiftPolicy>('/api/admin/gift-claims/policy'),
  updatePolicy: (expiryDays: number) =>
    api.patch<GiftPolicy>('/api/admin/gift-claims/policy', { expiryDays }),
  runExpiry: () => api.post<GiftExpiryBatchResult>('/api/admin/gift-claims/expire/run'),
}

// ----- 배송 슬롯(관리자) -----
export interface CreateDeliverySlotBody {
  slotDate: string // "YYYY-MM-DD"
  startTime: string // "HH:mm"
  endTime: string
  type: DeliverySlotType
  cutoffAt: string // ISO Instant
  capacity: number
  regionScope?: string | null
  extraFee?: number
}

export const adminDeliverySlotApi = {
  search: (params: { date?: string; type?: DeliverySlotType | ''; page?: number; size?: number }) => {
    const q = new URLSearchParams()
    if (params.date) q.set('date', params.date)
    if (params.type) q.set('type', params.type)
    q.set('page', String(params.page ?? 0))
    q.set('size', String(params.size ?? 20))
    return api.get<PageResponse<DeliverySlot>>(`/api/admin/delivery-slots?${q.toString()}`)
  },
  detail: (id: number) => api.get<DeliverySlot>(`/api/admin/delivery-slots/${id}`),
  create: (body: CreateDeliverySlotBody) => api.post<DeliverySlot>('/api/admin/delivery-slots', body),
}

// ----- 관리자 멤버십 운영 -----
export const adminMembershipApi = {
  search: (params: { status?: MembershipStatus | ''; page?: number; size?: number }) => {
    const q = new URLSearchParams()
    if (params.status) q.set('status', params.status)
    q.set('page', String(params.page ?? 0))
    q.set('size', String(params.size ?? 20))
    return api.get<PageResponse<AdminMembership>>(`/api/admin/memberships?${q.toString()}`)
  },
  detail: (id: number) => api.get<AdminMembership>(`/api/admin/memberships/${id}`),
  billingHistories: (id: number) =>
    api.get<MembershipBillingHistory[]>(`/api/admin/memberships/${id}/billing-histories`),
  runBilling: () => api.post<MembershipBillingRunResult>('/api/admin/memberships/billing/run'),
  getPolicy: () => api.get<MembershipPolicy>('/api/admin/memberships/policy'),
  updatePolicy: (body: Partial<MembershipPolicy>) =>
    api.patch<MembershipPolicy>('/api/admin/memberships/policy', body),
}

// ----- 정기배송 구독(회원) — 멤버십과 별도 빌링키 테이블, 상품 옵션 단위 자동 재주문 -----
export interface CreateDeliverySubscriptionBody {
  optionId: number
  quantity: number
  cycleDays: number
  ordererName: string
  ordererPhone: string
  ordererEmail: string
  shippingAddress: ShippingAddressBody
  startImmediately?: boolean
}

export const deliverySubscriptionApi = {
  // Mock 게이트웨이는 형식만 검증(숫자 12~16자리)하고 실 카드 통신은 하지 않는다. 멤버십과 별개 빌링키.
  registerBillingKey: (cardNumber: string) =>
    api.post<DeliverySubscriptionBillingKey>('/api/me/delivery-subscriptions/billing-key', { cardNumber }),
  create: (body: CreateDeliverySubscriptionBody) =>
    api.post<DeliverySubscription>('/api/me/delivery-subscriptions', body),
  my: () => api.get<DeliverySubscription[]>('/api/me/delivery-subscriptions'),
  histories: (id: number) =>
    api.get<DeliverySubscriptionHistory[]>(`/api/me/delivery-subscriptions/${id}/histories`),
  pause: (id: number) => api.patch<DeliverySubscription>(`/api/me/delivery-subscriptions/${id}/pause`),
  resume: (id: number) => api.patch<DeliverySubscription>(`/api/me/delivery-subscriptions/${id}/resume`),
  skipNext: (id: number) => api.post<DeliverySubscription>(`/api/me/delivery-subscriptions/${id}/skip-next`),
  cancel: (id: number) => api.del<DeliverySubscription>(`/api/me/delivery-subscriptions/${id}`),
}

// ----- 정기배송 구독(관리자) -----
export const adminDeliverySubscriptionApi = {
  search: (params: { status?: DeliverySubscriptionStatus | ''; page?: number; size?: number }) => {
    const q = new URLSearchParams()
    if (params.status) q.set('status', params.status)
    q.set('page', String(params.page ?? 0))
    q.set('size', String(params.size ?? 20))
    return api.get<PageResponse<AdminDeliverySubscription>>(`/api/admin/delivery-subscriptions?${q.toString()}`)
  },
  detail: (id: number) => api.get<AdminDeliverySubscription>(`/api/admin/delivery-subscriptions/${id}`),
  histories: (id: number) =>
    api.get<DeliverySubscriptionHistory[]>(`/api/admin/delivery-subscriptions/${id}/histories`),
  runBilling: () =>
    api.post<DeliverySubscriptionBillingRunResult>('/api/admin/delivery-subscriptions/billing/run'),
  getPolicy: () => api.get<DeliverySubscriptionPolicy>('/api/admin/delivery-subscriptions/policy'),
  updatePolicy: (body: Partial<DeliverySubscriptionPolicy>) =>
    api.patch<DeliverySubscriptionPolicy>('/api/admin/delivery-subscriptions/policy', body),
}

// ----- 새벽배송 가능 지역(관리자, 우편번호 접두사 화이트리스트) -----
export interface CreateDeliveryRegionBody {
  postalCodePrefix: string
  dawnDeliveryAvailable?: boolean
}

export const adminDeliveryRegionApi = {
  list: () => api.get<DeliveryRegion[]>('/api/admin/delivery-regions'),
  create: (body: CreateDeliveryRegionBody) =>
    api.post<DeliveryRegion>('/api/admin/delivery-regions', body),
  update: (id: number, dawnDeliveryAvailable: boolean) =>
    api.patch<DeliveryRegion>(`/api/admin/delivery-regions/${id}`, { dawnDeliveryAvailable }),
  remove: (id: number) => api.del<void>(`/api/admin/delivery-regions/${id}`),
}

// ----- 관리자 회원 관리 -----
export const adminUserApi = {
  search: (params: { keyword?: string; status?: UserStatus; page?: number }) => {
    const q = new URLSearchParams()
    if (params.keyword) q.set('keyword', params.keyword)
    if (params.status) q.set('status', params.status)
    q.set('page', String(params.page ?? 0))
    return api.get<PageResponse<AdminUser>>(`/api/admin/users?${q.toString()}`)
  },
  changeStatus: (id: number, status: UserStatus) =>
    api.patch<AdminUser>(`/api/admin/users/${id}/status`, { status }),
  grantRole: (id: number, role: string) => api.post<AdminUser>(`/api/admin/users/${id}/roles`, { role }),
  revokeRole: (id: number, role: string) => api.del<AdminUser>(`/api/admin/users/${id}/roles/${role}`),
}

// ----- 관리자 감사 로그 -----
export const adminAuditLogApi = {
  search: (params: { userId?: number; method?: string; uriKeyword?: string; page?: number }) => {
    const q = new URLSearchParams()
    if (params.userId != null) q.set('userId', String(params.userId))
    if (params.method) q.set('method', params.method)
    if (params.uriKeyword) q.set('uriKeyword', params.uriKeyword)
    q.set('page', String(params.page ?? 0))
    return api.get<PageResponse<AuditLog>>(`/api/admin/audit-logs?${q.toString()}`)
  },
}

// ----- 관리자 포인트 정책/만료 -----
export const adminPointApi = {
  getPolicy: () => api.get<PointPolicy>('/api/admin/point-policy'),
  updatePolicy: (body: Partial<PointPolicy>) => api.patch<PointPolicy>('/api/admin/point-policy', body),
  expire: () => api.post<{ expiredTotal: number }>('/api/admin/points/expire'),
}
