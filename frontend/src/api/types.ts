// 백엔드 공통 응답 규약: { success, code, message, data }
export interface ApiResponse<T> {
  success: boolean
  code: string
  message: string | null
  data: T
}

// 스프링 Page 를 감싼 PageResponse
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

export type ProductStatus = 'DRAFT' | 'ON_SALE' | 'SOLD_OUT' | 'HIDDEN'
export type ProductSort = 'LATEST' | 'PRICE_ASC' | 'PRICE_DESC' | 'RATING_DESC'

export interface ProductSummary {
  id: number
  name: string
  basePrice: number
  status: ProductStatus
  sellerId: number
  storeName: string
  categoryId: number | null
  categoryName: string | null
  avgRating: number
  reviewCount: number
  dawnDeliveryEligible: boolean
  imageUrl: string | null
}

export interface ProductOption {
  id: number
  name: string
  sku: string
  price: number
  availableStock: number
}

export interface PopularProduct {
  id: number
  name: string
  basePrice: number
  status: ProductStatus
  sellerId: number
  storeName: string
  categoryId: number | null
  categoryName: string | null
  soldQuantity: number
  avgRating: number
  reviewCount: number
  dawnDeliveryEligible: boolean
  imageUrl: string | null
}

export interface ProductDetail {
  id: number
  name: string
  description: string | null
  basePrice: number
  status: ProductStatus
  sellerId: number
  storeName: string
  categoryId: number | null
  categoryName: string | null
  options: ProductOption[]
  createdAt: string
  avgRating: number
  reviewCount: number
  dawnDeliveryEligible: boolean
  imageUrl: string | null
}

export interface CartItem {
  itemId: number | null
  optionId: number
  productId: number
  productName: string
  optionName: string
  unitPrice: number
  quantity: number
  lineTotal: number
  availableStock: number
  purchasable: boolean
  // 체크아웃에서 판매자(SubOrder) 단위로 묶어 배송 슬롯을 선택할 수 있도록 노출된다
  // (`docs/planning/delivery-slot.md` AC6).
  sellerId: number
  storeName: string
  dawnDeliveryEligible: boolean
}

export interface Cart {
  items: CartItem[]
  totalQuantity: number
  totalPrice: number
}

export type OrderStatus = 'CREATED' | 'PAID' | 'CANCELED'
export type SubOrderStatus = 'CREATED' | 'PAID' | 'PREPARING' | 'SHIPPED' | 'DELIVERED' | 'CANCELED'

export interface OrderItem {
  optionId: number
  productName: string
  optionName: string
  unitPrice: number
  quantity: number
  lineTotal: number
  // 타임딜이 적용된 항목만 채워진다 — unitPrice 는 항상 정가, appliedSalePrice 가 실제 청구 단가.
  flashSaleId: number | null
  appliedSalePrice: number | null
}

export interface SubOrder {
  subOrderId: number
  sellerId: number
  storeName: string
  status: SubOrderStatus
  subtotal: number
  // 새벽배송 슬롯을 선택한 SubOrder 만 채워진다(`docs/planning/delivery-slot.md`).
  deliverySlotId: number | null
  deliveryFee: number
  items: OrderItem[]
}

export interface ShippingAddress {
  receiverName: string
  receiverPhone: string
  zipcode: string
  address1: string
  address2: string | null
}

/** 회원 배송지 주소록 항목 — GET /api/me/addresses (기본 배송지가 먼저) */
export interface UserAddress extends ShippingAddress {
  addressId: number
  label: string | null
  isDefault: boolean
}

export interface Order {
  orderId: number
  orderNumber: string
  status: OrderStatus
  totalAmount: number
  discountAmount: number
  pointUsed: number
  deliveryFeeTotal: number
  payableAmount: number
  ordererName: string
  // 선물 주문(isGift)은 수령자 프라이버시 보호를 위해 항상 null로 마스킹된다(`docs/planning/gift-order.md` §4).
  shippingAddress: ShippingAddress | null
  createdAt: string
  subOrders: SubOrder[]
  isGift: boolean
  giftMessage: string | null
  // 주문 생성 응답에서만 채워진다(공유 링크 안내용). 재조회 시에는 giftApi.status(orderId)를 사용한다.
  giftClaimToken?: string | null
}

export interface OrderSummary {
  orderId: number
  orderNumber: string
  status: OrderStatus
  totalAmount: number
  payableAmount: number
  createdAt: string
}

export interface User {
  id: number
  email: string
  name: string
  status: string
  roles: string[]
  createdAt: string
}

export interface Payment {
  paymentId: number
  orderId: number
  status: string
  amount: number
  method: string
  transactionId: string | null
  paidAt: string | null
}

export type DiscountType = 'RATE' | 'FIXED'

export interface IssuedCoupon {
  issuedCouponId: number
  name: string
  discountType: DiscountType
  discountValue: number
  minOrderAmount: number
  maxDiscountAmount: number | null
  validFrom: string
  validUntil: string
  used: boolean
}

export type PointTransactionType = 'EARN' | 'USE' | 'CANCEL_USE' | 'CANCEL_EARN' | 'EXPIRE'

export interface PointTransaction {
  type: PointTransactionType
  amount: number
  orderId: number | null
  createdAt: string
}

export interface PointSummary {
  balance: number
  transactions: PointTransaction[]
}

export type SellerStatus = 'PENDING' | 'ACTIVE' | 'SUSPENDED'

export interface Seller {
  sellerId: number
  userId: number
  storeName: string
  description: string | null
  status: SellerStatus
}

export interface Shipment {
  courier: string
  trackingNumber: string
  status: string
  shippedAt: string | null
}

export interface SellerSubOrder {
  subOrderId: number
  orderNumber: string
  status: SubOrderStatus
  subtotal: number
  items: OrderItem[]
  shipment: Shipment | null
}

export interface Coupon {
  couponId: number
  name: string
  discountType: DiscountType
  discountValue: number
  minOrderAmount: number
  maxDiscountAmount: number | null
  validFrom: string
  validUntil: string
  issuedCount: number
}

export interface Category {
  categoryId: number
  name: string
  parentId: number | null
  sortOrder: number
}

export interface SellerOption {
  optionId: number
  name: string
  sku: string
  additionalPrice: number
  quantity: number
  reserved: number
  available: number
}

/** 판매자 본인 상품(DRAFT/HIDDEN 포함) — GET /api/seller/products */
export interface SellerProduct {
  productId: number
  name: string
  basePrice: number
  status: ProductStatus
  categoryId: number | null
  dawnDeliveryEligible: boolean
  imageUrl: string | null
  options: SellerOption[]
}

export type SettlementStatus = 'PENDING' | 'PAID'

export interface Settlement {
  settlementId: number
  sellerId: number
  storeName: string
  salesAmount: number
  commissionAmount: number
  payoutAmount: number
  settledCount: number
  status: SettlementStatus
  paidAt: string | null
  createdAt: string
}

// ----- 리뷰/포토리뷰 -----
export type ReviewStatus = 'VISIBLE' | 'REPORTED' | 'HIDDEN'
export type ReviewSort = 'LATEST' | 'RATING_DESC'

export interface Review {
  id: number
  orderItemId: number
  productId: number
  userId: number
  authorName: string
  rating: number
  content: string
  status: ReviewStatus
  reportCount: number
  imageUrls: string[]
  createdAt: string
  updatedAt: string
}

/** 마이페이지 "작성 가능한 리뷰" 목록 항목 — 배송완료(DELIVERED) 후 아직 리뷰를 쓰지 않은 주문항목. */
export interface ReviewableOrderItem {
  orderItemId: number
  subOrderId: number
  productId: number
  productName: string
  optionName: string
  deliveredAt: string | null
}

export interface ReviewRewardPolicy {
  textReviewPoint: number
  photoReviewPoint: number
  reviewableDays: number
  reportThreshold: number
}

// ----- 재입고 알림 -----
export type RestockAlertStatus = 'PENDING' | 'NOTIFIED' | 'CANCELED'

export interface RestockAlert {
  id: number
  productId: number
  productName: string
  optionId: number
  optionName: string
  status: RestockAlertStatus
  createdAt: string
  notifiedAt: string | null
}

// ----- 기획전/컬렉션 큐레이션 -----
export type CollectionStatus = 'DRAFT' | 'PUBLISHED' | 'ENDED'

export interface CollectionSummary {
  id: number
  title: string
  subtitle: string | null
  bannerImageUrl: string | null
  startAt: string
  endAt: string
  status: CollectionStatus
  displayOrder: number
  productCount: number
  createdAt: string
  updatedAt: string
}

export interface CollectionProductItem {
  displayOrder: number
  product: ProductSummary
}

export interface CollectionDetail {
  id: number
  title: string
  subtitle: string | null
  bannerImageUrl: string | null
  startAt: string
  endAt: string
  status: CollectionStatus
  displayOrder: number
  products: CollectionProductItem[]
  createdAt: string
  updatedAt: string
}

// ----- 타임딜(한정특가) -----
export type FlashSalePhase = 'SCHEDULED' | 'ONGOING' | 'ENDED' | 'CANCELED'
// 관리자 강제종료 여부만 표현하는 "행정 상태"(검색 조건용) — 실시간 진행 단계는 FlashSalePhase 참고.
export type FlashSaleStatus = 'ACTIVE' | 'CANCELED'

export interface FlashSale {
  id: number
  productOptionId: number
  productId: number
  productName: string
  optionName: string
  sellerId: number
  storeName: string
  originalPrice: number
  salePrice: number
  limitQuantity: number
  soldQuantity: number
  remainingQuantity: number
  startAt: string
  endAt: string
  phase: FlashSalePhase
  createdAt: string
  updatedAt: string
}

// ----- 배송 슬롯(새벽배송/시간대 지정) -----
export type DeliverySlotType = 'DAWN' | 'DAYTIME'

/** [remaining]/[expired] 는 조회 시점 기준 파생 값 — 재고 품절과 유사하게 취급한다. */
export interface DeliverySlot {
  id: number
  slotDate: string // LocalDate, "YYYY-MM-DD"
  startTime: string // LocalTime, "HH:mm:ss"
  endTime: string
  type: DeliverySlotType
  cutoffAt: string // Instant
  capacity: number
  reservedCount: number
  remaining: number
  regionScope: string | null
  extraFee: number
  expired: boolean
}

/** 새벽배송 가능 지역(우편번호 접두사 화이트리스트, 관리자 관리). */
export interface DeliveryRegion {
  id: number
  postalCodePrefix: string
  dawnDeliveryAvailable: boolean
}

// ----- 유료 멤버십(구독) -----
export type MembershipPlan = 'BASIC' | 'PREMIUM'
export type MembershipStatus = 'ACTIVE' | 'PAST_DUE' | 'CANCELED' | 'EXPIRED'
export type BillingHistoryStatus = 'SUCCESS' | 'FAILED'

/** 지금 적용 중인 혜택 값 — 체크아웃 인라인 안내에 그대로 쓸 수 있는 형태. */
export interface MembershipBenefitSummary {
  freeShipping: boolean
  pointEarnMultiplierBp: number
}

export interface MembershipBillingKey {
  cardLast4: string
  registeredAt: string
}

export interface Membership {
  id: number
  plan: MembershipPlan
  status: MembershipStatus
  price: number
  startAt: string
  nextBillingAt: string
  canceledAt: string | null
  benefitActive: boolean
  benefits: MembershipBenefitSummary
}

export interface MembershipBillingHistory {
  id: number
  cycleAt: string
  attemptedAt: string
  status: BillingHistoryStatus
  failureReason: string | null
}

/** 관리자 구독 현황 — 회원 혜택 요약 없이 운영에 필요한 상태 정보만 노출한다. */
export interface AdminMembership {
  id: number
  userId: number
  plan: MembershipPlan
  status: MembershipStatus
  price: number
  startAt: string
  nextBillingAt: string
  canceledAt: string | null
  billingFailureCount: number
  gracePeriodEndsAt: string | null
}

export interface MembershipPolicy {
  monthlyPrice: number
  pointEarnMultiplierBp: number
  freeShippingEnabled: boolean
  maxRetryCount: number
  graceDays: number
}

export interface MembershipBillingRunResult {
  renewed: number
  failed: number
  expired: number
}

// ----- 위시리스트(찜) + 가격 인하 알림 -----
export interface WishlistResponse {
  id: number
  productId: number
  productName: string
  productStatus: ProductStatus
  baselinePrice: number
  currentPrice: number
  priceDropAmount: number
  priceDropRate: number // 정수 %(내림)
  isPriceDropped: boolean
  createdAt: string
}

// ----- 로열티 등급 -----
export type LoyaltyTier = 'BRONZE' | 'SILVER' | 'GOLD' | 'VIP'

export interface MyLoyaltyTierResponse {
  tier: LoyaltyTier
  netPurchaseAmount12m: number
  nextTier: LoyaltyTier | null
  amountToNextTier: number | null
  calculatedAt: string | null
}

/** 관리자 등급 조회 응답 — 다음 등급 정보는 없다(마이페이지 응답과 다른 shape). */
export interface AdminLoyaltyTierResponse {
  userId: number
  tier: LoyaltyTier
  netPurchaseAmount12m: number
  calculatedAt: string | null
}

export interface LoyaltyTierBatchResult {
  upgradedCount: number
  downgradedCount: number
  unchangedCount: number
  erroredCount: number
}

// ----- 장바구니 이탈 리마인드(관리자 수동 트리거) -----
export interface CartReminderBatchResult {
  remindedCount: number
  erroredCount: number
}

// ----- 범용 인앱 알림함 -----
export type NotificationType =
  | 'RESTOCK'
  | 'MEMBERSHIP'
  | 'DELIVERY_SUBSCRIPTION'
  | 'GIFT'
  | 'PRICE_DROP'
  | 'CART_REMINDER'
  | 'LOW_STOCK'
  | string

export interface AppNotification {
  id: number
  type: NotificationType
  title: string
  body: string
  linkUrl: string | null
  isRead: boolean
  createdAt: string
}

// ----- 정기배송 구독(회원, 별도 빌링키 — 멤버십과 다른 테이블) -----
export type DeliverySubscriptionStatus = 'ACTIVE' | 'PAUSED' | 'CANCELED'

/** 회차 처리 결과. `PAUSED_PRODUCT_UNAVAILABLE`는 상품 판매중지로 구독 자체가 자동 정지된 경우. */
export type DeliverySubscriptionHistoryResult =
  | 'ORDER_CREATED'
  | 'SKIPPED_OUT_OF_STOCK'
  | 'SKIPPED_BY_USER'
  | 'PAYMENT_FAILED'
  | 'PAUSED_PRODUCT_UNAVAILABLE'

export interface DeliverySubscriptionBillingKey {
  cardLast4: string
  registeredAt: string
}

export interface DeliverySubscription {
  id: number
  optionId: number
  productName: string
  optionName: string
  quantity: number
  cycleDays: number
  status: DeliverySubscriptionStatus
  nextOrderAt: string
  skipRequested: boolean
  consecutiveFailureCount: number
  canceledAt: string | null
}

export interface DeliverySubscriptionHistory {
  id: number
  attemptedAt: string
  result: DeliverySubscriptionHistoryResult
  orderId: number | null
  detail: string | null
}

/** 관리자 현황 — 회원 식별자를 포함해 운영에 필요한 정보만 노출(상품명 등 비정규화 없음). */
export interface AdminDeliverySubscription {
  id: number
  userId: number
  optionId: number
  quantity: number
  cycleDays: number
  status: DeliverySubscriptionStatus
  nextOrderAt: string
  skipRequested: boolean
  consecutiveFailureCount: number
  canceledAt: string | null
}

export interface DeliverySubscriptionPolicy {
  maxConsecutiveFailures: number
  skipDeadlineDays: number
}

export interface DeliverySubscriptionBillingRunResult {
  orderCreated: number
  skippedOutOfStock: number
  skippedByUser: number
  paymentFailed: number
  autoPaused: number
  errored: number
}

// ----- 선물하기 -----
/** 전이: PENDING → CLAIMED(수령자 수락) / EXPIRED(기한 경과 자동취소) / CANCELED(구매자 취소). 모두 종단 상태. */
export type GiftClaimStatus = 'PENDING' | 'CLAIMED' | 'EXPIRED' | 'CANCELED'

/** 선물 링크 상태 응답 — 구매자의 "보낸 선물" 조회, 수령자의 수락 결과, 관리자 모니터링에 공용으로 쓰인다. */
export interface GiftClaim {
  orderId: number
  orderNumber: string
  token: string
  status: GiftClaimStatus
  expiresAt: string
  claimedAt: string | null
}

export interface GiftPreviewItem {
  productName: string
  optionName: string
  quantity: number
}

/** 선물 미리보기(`GET /api/gift/{token}`, 비회원 접근 가능). 가격 정보는 노출하지 않는다. */
export interface GiftPreview {
  orderNumber: string
  senderName: string
  giftMessage: string | null
  status: GiftClaimStatus
  expiresAt: string
  items: GiftPreviewItem[]
}

export interface GiftPolicy {
  expiryDays: number
}

export interface GiftExpiryBatchResult {
  expiredCount: number
  erroredCount: number
}

// ----- 관리자 회원/감사 로그/포인트 정책 -----
export type UserStatus = 'ACTIVE' | 'LOCKED' | 'DORMANT' | 'WITHDRAWN'

export interface AdminUser {
  id: number
  email: string
  name: string
  status: UserStatus
  roles: string[]
  createdAt: string
}

export interface AuditLog {
  id: number
  userId: number | null
  method: string
  uri: string
  ip: string | null
  statusCode: number
  durationMs: number
  payload: string | null
  createdAt: string
}

export interface PointPolicy {
  earnRateBp: number
  expiryDays: number
}
