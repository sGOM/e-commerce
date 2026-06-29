import type { OrderStatus, ProductStatus, SubOrderStatus } from './api/types'

export const productStatusLabel: Record<ProductStatus, string> = {
  DRAFT: '준비중',
  ON_SALE: '판매중',
  SOLD_OUT: '품절',
  HIDDEN: '숨김',
}

export const orderStatusLabel: Record<OrderStatus, string> = {
  CREATED: '결제대기',
  PAID: '결제완료',
  CANCELED: '취소',
}

export const subOrderStatusLabel: Record<SubOrderStatus, string> = {
  CREATED: '결제대기',
  PAID: '결제완료',
  PREPARING: '상품준비중',
  SHIPPED: '배송중',
  DELIVERED: '배송완료',
  CANCELED: '취소',
}
