import type {
  BillingHistoryStatus,
  CollectionStatus,
  DeliverySlotType,
  DeliverySubscriptionHistoryResult,
  DeliverySubscriptionStatus,
  FlashSalePhase,
  GiftClaimStatus,
  MembershipStatus,
  OrderStatus,
  ProductStatus,
  RestockAlertStatus,
  ReviewStatus,
  SubOrderStatus,
} from './api/types'

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

export const reviewStatusLabel: Record<ReviewStatus, string> = {
  VISIBLE: '노출중',
  REPORTED: '신고검토',
  HIDDEN: '숨김',
}

export const restockAlertStatusLabel: Record<RestockAlertStatus, string> = {
  PENDING: '알림 대기중',
  NOTIFIED: '재입고 알림 완료',
  CANCELED: '취소됨',
}

export const collectionStatusLabel: Record<CollectionStatus, string> = {
  DRAFT: '작성중',
  PUBLISHED: '노출중',
  ENDED: '종료',
}

export const deliverySlotTypeLabel: Record<DeliverySlotType, string> = {
  DAWN: '새벽배송',
  DAYTIME: '주간배송',
}

// "한도 소진"과 "시간 만료"는 고객에게 동일하게 "타임딜 종료"로 표기(재고 품절과는 별개 개념).
export const flashSalePhaseLabel: Record<FlashSalePhase, string> = {
  SCHEDULED: '오픈 예정',
  ONGOING: '진행중',
  ENDED: '타임딜 종료',
  CANCELED: '취소됨',
}

export const membershipStatusLabel: Record<MembershipStatus, string> = {
  ACTIVE: '구독중',
  PAST_DUE: '결제 재시도중',
  CANCELED: '해지 예약(혜택 유지중)',
  EXPIRED: '만료됨',
}

export const billingHistoryStatusLabel: Record<BillingHistoryStatus, string> = {
  SUCCESS: '결제 성공',
  FAILED: '결제 실패',
}

export const deliverySubscriptionStatusLabel: Record<DeliverySubscriptionStatus, string> = {
  ACTIVE: '진행중',
  PAUSED: '일시정지',
  CANCELED: '해지됨',
}

export const deliverySubscriptionHistoryResultLabel: Record<DeliverySubscriptionHistoryResult, string> = {
  ORDER_CREATED: '주문 생성됨',
  SKIPPED_OUT_OF_STOCK: '재고 부족으로 건너뜀',
  SKIPPED_BY_USER: '회원 요청으로 건너뜀',
  PAYMENT_FAILED: '결제 실패로 건너뜀',
  PAUSED_PRODUCT_UNAVAILABLE: '상품 판매중지로 정지됨',
}

export const giftClaimStatusLabel: Record<GiftClaimStatus, string> = {
  PENDING: '수락 대기중',
  CLAIMED: '수락 완료',
  EXPIRED: '기한 만료(자동 취소)',
  CANCELED: '취소됨',
}
