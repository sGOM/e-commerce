import { api } from './client'
import type {
  Cart,
  IssuedCoupon,
  Order,
  OrderSummary,
  PageResponse,
  Payment,
  PointSummary,
  ProductDetail,
  ProductSummary,
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
  search: (params: { keyword?: string; page?: number; size?: number }) => {
    const q = new URLSearchParams()
    if (params.keyword) q.set('keyword', params.keyword)
    q.set('page', String(params.page ?? 0))
    q.set('size', String(params.size ?? 20))
    return api.get<PageResponse<ProductSummary>>(`/api/products?${q.toString()}`)
  },
  detail: (id: number) => api.get<ProductDetail>(`/api/products/${id}`),
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
