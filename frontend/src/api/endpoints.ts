import { api } from './client'
import type {
  Cart,
  Category,
  Coupon,
  IssuedCoupon,
  Order,
  OrderStatus,
  OrderSummary,
  PageResponse,
  Payment,
  PointSummary,
  ProductDetail,
  ProductSummary,
  Seller,
  SellerStatus,
  SellerSubOrder,
  Settlement,
  SubOrderStatus,
  User,
} from './types'

// ----- 인증 -----
export const authApi = {
  me: () => api.get<User>('/api/auth/me'),
  login: (email: string, password: string) =>
    api.post<User>('/api/auth/login', { email, password }),
  signup: (email: string, password: string, name: string) =>
    api.post<User>('/api/auth/signup', { email, password, name }),
  logout: () => api.post<void>('/api/auth/logout'),
}

// ----- 내 쿠폰/포인트 -----
export const meApi = {
  coupons: () => api.get<IssuedCoupon[]>('/api/me/coupons'),
  points: () => api.get<PointSummary>('/api/me/points'),
}

// ----- 상품 -----
export const productApi = {
  search: (params: { keyword?: string; sellerId?: number; page?: number; size?: number }) => {
    const q = new URLSearchParams()
    if (params.keyword) q.set('keyword', params.keyword)
    if (params.sellerId != null) q.set('sellerId', String(params.sellerId))
    q.set('page', String(params.page ?? 0))
    q.set('size', String(params.size ?? 20))
    return api.get<PageResponse<ProductSummary>>(`/api/products?${q.toString()}`)
  },
  detail: (id: number) => api.get<ProductDetail>(`/api/products/${id}`),
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
  options: CreateOptionBody[]
}

// ----- 판매자 백오피스 -----
export const sellerApi = {
  apply: (storeName: string, description?: string) =>
    api.post<Seller>('/api/seller/apply', { storeName, description }),
  myStore: () => api.get<Seller>('/api/seller/store'),
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

export interface CreateOrderBody {
  ordererName: string
  ordererPhone: string
  ordererEmail: string
  shippingAddress: ShippingAddressBody
  issuedCouponId?: number | null
  usePoint?: number
}

export interface GuestOrderBody {
  ordererName: string
  ordererPhone: string
  ordererEmail: string
  shippingAddress: ShippingAddressBody
  items: GuestCartLine[]
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
}
