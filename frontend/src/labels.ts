import type {
  BillingHistoryStatus,
  CollectionStatus,
  DeliverySlotType,
  DeliverySubscriptionHistoryResult,
  DeliverySubscriptionStatus,
  FlashSalePhase,
  GiftClaimStatus,
  LoyaltyTier,
  MembershipStatus,
  NotificationType,
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

export const loyaltyTierLabel: Record<LoyaltyTier, string> = {
  BRONZE: '브론즈',
  SILVER: '실버',
  GOLD: '골드',
  VIP: 'VIP',
}

/** 등급별 시각 차별화(뱃지/카드 강조색). Badge의 variant 대신 커스텀 클래스를 덧입힌다. */
export const loyaltyTierBadgeClass: Record<LoyaltyTier, string> = {
  BRONZE: 'border-transparent bg-amber-700/15 text-amber-800 dark:text-amber-400',
  SILVER: 'border-transparent bg-slate-400/20 text-slate-600 dark:text-slate-300',
  GOLD: 'border-transparent bg-yellow-400/20 text-yellow-700 dark:text-yellow-400',
  VIP: 'border-transparent bg-violet-500/20 text-violet-700 dark:text-violet-400',
}

// 알림함(NotificationsPage/NotificationBell)은 제목/본문을 서버가 그대로 내려주므로 타입별 렌더는
// 아이콘 정도만 덧붙인다. 등록되지 않은(미래) 타입은 undefined 로 떨어져 기본 아이콘(🔔)을 쓴다.
export const notificationTypeIcon: Partial<Record<NotificationType, string>> = {
  RESTOCK: '📦',
  MEMBERSHIP: '💳',
  DELIVERY_SUBSCRIPTION: '🔁',
  GIFT: '🎁',
  PRICE_DROP: '💰',
  CART_REMINDER: '🛒',
}
