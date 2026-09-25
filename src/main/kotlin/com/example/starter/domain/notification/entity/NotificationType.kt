package com.example.starter.domain.notification.entity

/**
 * 알림 종류. 재입고 외 다른 알림도 담을 수 있는 범용 테이블([Notification])의 구분 값이다.
 * 새 알림 종류가 생기면 이 enum 에만 추가하면 되고, 테이블/조회 API 는 변경하지 않아도 된다.
 */
enum class NotificationType {
    /** 재입고 알림 신청 옵션의 재고가 보충됨 */
    RESTOCK,

    /** 멤버십 정기결제 실패/재시도/만료 등 상태 변경 안내 */
    MEMBERSHIP,

    /** 정기배송 회차 스킵(재고부족/판매중단)/결제실패/자동일시정지 안내 */
    DELIVERY_SUBSCRIPTION,

    /** 선물 수락/만료 취소 등 선물 링크 상태 변경 안내(구매자 대상, `docs/planning/gift-order.md`) */
    GIFT,

    /** 위시리스트에 담은 상품의 정가(Product.basePrice)가 인하됨(`docs/planning/wishlist-price-alert.md`) */
    PRICE_DROP,

    /** 장바구니에 담긴 채 일정 시간 결제하지 않아 발송하는 이탈 리마인드(자동 쿠폰 발급 없음) */
    CART_REMINDER,

    /** 주문 예약으로 옵션 가용재고가 임계치 이하로 내려감(판매자 대상, ROADMAP 4.3) */
    LOW_STOCK,

    /** 상품 비밀 문의 접수(판매자 대상)와 답변 등록(작성자 대상) - docs/planning/product-qna.md AC3 */
    PRODUCT_QNA,
}
