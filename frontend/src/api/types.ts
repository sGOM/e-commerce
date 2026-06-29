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

export interface ProductSummary {
  id: number
  name: string
  basePrice: number
  status: ProductStatus
  sellerId: number
  storeName: string
  categoryId: number | null
  categoryName: string | null
}

export interface ProductOption {
  id: number
  name: string
  sku: string
  price: number
  availableStock: number
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
}

export interface Cart {
  items: CartItem[]
  totalQuantity: number
  totalPrice: number
}

export type OrderStatus = 'CREATED' | 'PAID' | 'CANCELED'
export type SubOrderStatus =
  | 'CREATED'
  | 'PAID'
  | 'PREPARING'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'CANCELED'

export interface OrderItem {
  optionId: number
  productName: string
  optionName: string
  unitPrice: number
  quantity: number
  lineTotal: number
}

export interface SubOrder {
  subOrderId: number
  sellerId: number
  storeName: string
  status: SubOrderStatus
  subtotal: number
  items: OrderItem[]
}

export interface ShippingAddress {
  receiverName: string
  receiverPhone: string
  zipcode: string
  address1: string
  address2: string | null
}

export interface Order {
  orderId: number
  orderNumber: string
  status: OrderStatus
  totalAmount: number
  discountAmount: number
  pointUsed: number
  payableAmount: number
  ordererName: string
  shippingAddress: ShippingAddress
  createdAt: string
  subOrders: SubOrder[]
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

export type PointTransactionType =
  | 'EARN'
  | 'USE'
  | 'CANCEL_USE'
  | 'CANCEL_EARN'
  | 'EXPIRE'

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
