package com.example.starter.common.exception

import org.springframework.http.HttpStatus

/**
 * 애플리케이션 전역 에러 코드.
 *
 * 코드 체계: `{도메인}-{번호}` (예: AUTH-001). HTTP 상태와 기본 메시지를 한곳에서 관리한다.
 * 새 에러는 도메인별 구간에 추가한다.
 */
enum class ErrorCode(
    val status: HttpStatus,
    val code: String,
    val message: String,
) {
    // 공통 (COMMON)
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-001", "서버 내부 오류가 발생했습니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON-002", "입력값이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON-003", "허용되지 않은 HTTP 메서드입니다."),
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON-004", "요청한 리소스를 찾을 수 없습니다."),
    MESSAGE_NOT_READABLE(HttpStatus.BAD_REQUEST, "COMMON-005", "요청 본문을 해석할 수 없습니다."),

    // 인증/인가 (AUTH)
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "AUTH-001", "인증이 필요합니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH-002", "접근 권한이 없습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH-003", "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_RESET_TOKEN(HttpStatus.BAD_REQUEST, "AUTH-004", "만료되었거나 이미 사용한 재설정 링크입니다."),

    // 사용자 (USER)
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER-001", "사용자를 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "USER-002", "이미 사용 중인 이메일입니다."),
    USER_INACTIVE(HttpStatus.FORBIDDEN, "USER-003", "비활성화된 계정입니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "USER-004", "현재 비밀번호가 일치하지 않습니다."),

    // 소셜 로그인 (OAUTH)
    UNSUPPORTED_OAUTH_PROVIDER(HttpStatus.BAD_REQUEST, "OAUTH-001", "지원하지 않는 소셜 로그인 제공자입니다."),
    OAUTH_EMAIL_NOT_PROVIDED(HttpStatus.BAD_REQUEST, "OAUTH-002", "소셜 계정에서 이메일을 제공받지 못했습니다."),

    // 판매자 (SELLER)
    SELLER_NOT_FOUND(HttpStatus.NOT_FOUND, "SELLER-001", "판매자를 찾을 수 없습니다."),
    ALREADY_SELLER(HttpStatus.CONFLICT, "SELLER-002", "이미 입점 신청한 계정입니다."),

    // 카탈로그/상품 (CATALOG)
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "CATALOG-001", "상품을 찾을 수 없습니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "CATALOG-002", "카테고리를 찾을 수 없습니다."),
    PRODUCT_OPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "CATALOG-003", "상품 옵션을 찾을 수 없습니다."),
    PRODUCT_NOT_PURCHASABLE(HttpStatus.CONFLICT, "CATALOG-004", "현재 구매할 수 없는 상품입니다."),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "CATALOG-005", "재고가 부족합니다."),
    CATEGORY_INVALID_PARENT(HttpStatus.BAD_REQUEST, "CATALOG-006", "자기 자신이나 하위 카테고리를 상위로 지정할 수 없습니다."),
    CATEGORY_IN_USE(HttpStatus.CONFLICT, "CATALOG-007", "하위 카테고리나 상품이 있는 카테고리는 삭제할 수 없습니다."),

    // 장바구니 (CART)
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "CART-001", "장바구니 항목을 찾을 수 없습니다."),

    // 주문 (ORDER)
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER-001", "주문을 찾을 수 없습니다."),
    EMPTY_ORDER(HttpStatus.BAD_REQUEST, "ORDER-002", "주문할 상품이 없습니다."),
    ORDER_NOT_CANCELABLE(HttpStatus.CONFLICT, "ORDER-003", "현재 상태에서는 주문을 취소할 수 없습니다."),
    ORDER_NOT_PAYABLE(HttpStatus.CONFLICT, "ORDER-004", "결제할 수 없는 주문 상태입니다."),
    SUB_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER-005", "하위 주문을 찾을 수 없습니다."),
    SUB_ORDER_NOT_SHIPPABLE(HttpStatus.CONFLICT, "ORDER-006", "결제 완료/상품준비 상태에서만 발송할 수 있습니다."),
    ORDER_ALREADY_CLAIMED(HttpStatus.CONFLICT, "ORDER-007", "이미 회원 계정에 연결된 주문입니다."),
    SUB_ORDER_NOT_DELIVERABLE(HttpStatus.CONFLICT, "ORDER-008", "발송 상태에서만 수령 확인(구매확정)할 수 있습니다."),
    ORDER_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER-009", "주문 항목을 찾을 수 없습니다."),
    ORDER_SHIPPING_ADDRESS_REQUIRED(HttpStatus.BAD_REQUEST, "ORDER-010", "배송지를 입력해 주세요."),

    // 결제 (PAYMENT)
    PAYMENT_FAILED(HttpStatus.PAYMENT_REQUIRED, "PAYMENT-001", "결제가 거절되었습니다."),
    PAYMENT_CANCEL_FAILED(HttpStatus.BAD_GATEWAY, "PAYMENT-002", "PG 결제 취소에 실패했습니다. 잠시 후 다시 시도해 주세요."),

    // 쿠폰 (COUPON)
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "COUPON-001", "쿠폰을 찾을 수 없습니다."),
    COUPON_ALREADY_USED(HttpStatus.CONFLICT, "COUPON-002", "이미 사용한 쿠폰입니다."),
    COUPON_EXPIRED(HttpStatus.CONFLICT, "COUPON-003", "사용 기간이 아닌 쿠폰입니다."),
    COUPON_MIN_ORDER_NOT_MET(HttpStatus.CONFLICT, "COUPON-004", "쿠폰 최소 주문금액을 충족하지 않습니다."),

    // 포인트 (POINT)
    INSUFFICIENT_POINT(HttpStatus.CONFLICT, "POINT-001", "보유 포인트가 부족합니다."),
    POINT_EXCEEDS_PAYABLE(HttpStatus.BAD_REQUEST, "POINT-002", "사용 포인트가 결제금액을 초과합니다."),

    // 정산 (SETTLEMENT)
    SETTLEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "SETTLEMENT-001", "정산 내역을 찾을 수 없습니다."),

    // 리뷰 (REVIEW)
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "REVIEW-001", "리뷰를 찾을 수 없습니다."),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "REVIEW-002", "이미 작성한 리뷰가 있습니다."),
    REVIEW_NOT_ALLOWED(HttpStatus.CONFLICT, "REVIEW-003", "배송완료된 주문 항목만 리뷰를 작성할 수 있습니다."),
    REVIEW_PERIOD_EXPIRED(HttpStatus.CONFLICT, "REVIEW-004", "리뷰 작성 가능 기간이 지났습니다."),
    INVALID_REVIEW_STATUS(HttpStatus.BAD_REQUEST, "REVIEW-005", "허용되지 않는 리뷰 상태 변경입니다."),

    // 재입고 알림 (RESTOCK)
    RESTOCK_ALERT_NOT_FOUND(HttpStatus.NOT_FOUND, "RESTOCK-001", "재입고 알림 신청 내역을 찾을 수 없습니다."),
    RESTOCK_ALERT_ALREADY_EXISTS(HttpStatus.CONFLICT, "RESTOCK-002", "이미 재입고 알림을 신청한 옵션입니다."),
    RESTOCK_ALERT_NOT_ALLOWED(HttpStatus.CONFLICT, "RESTOCK-003", "재고가 있는 옵션은 재입고 알림을 신청할 수 없습니다."),

    // 알림함 (NOTIFICATION)
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION-001", "알림을 찾을 수 없습니다."),

    // 기획전/컬렉션 (COLLECTION)
    COLLECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "COLLECTION-001", "컬렉션을 찾을 수 없습니다."),
    COLLECTION_INVALID_PERIOD(HttpStatus.BAD_REQUEST, "COLLECTION-002", "노출 종료일은 시작일 이후여야 합니다."),
    COLLECTION_DUPLICATE_PRODUCT(HttpStatus.BAD_REQUEST, "COLLECTION-003", "동일한 상품을 중복해서 편성할 수 없습니다."),

    // 타임딜/한정특가 (FLASHSALE)
    FLASH_SALE_NOT_FOUND(HttpStatus.NOT_FOUND, "FLASHSALE-001", "타임딜을 찾을 수 없습니다."),
    FLASH_SALE_INVALID_PRICE(HttpStatus.BAD_REQUEST, "FLASHSALE-002", "특가는 정가보다 낮아야 합니다."),
    FLASH_SALE_INVALID_PERIOD(HttpStatus.BAD_REQUEST, "FLASHSALE-003", "종료 시각은 시작 시각 이후여야 합니다."),
    FLASH_SALE_PERIOD_OVERLAP(HttpStatus.CONFLICT, "FLASHSALE-004", "해당 옵션에 시간이 겹치는 타임딜이 이미 있습니다."),
    FLASH_SALE_ALREADY_ENDED(HttpStatus.CONFLICT, "FLASHSALE-005", "이미 종료된 타임딜입니다."),
    FLASH_SALE_SOLD_OUT(HttpStatus.CONFLICT, "FLASHSALE-006", "타임딜 한정수량이 모두 소진되었습니다."),

    // 배송 슬롯 (DELIVERY)
    DELIVERY_SLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "DELIVERY-001", "배송 슬롯을 찾을 수 없습니다."),
    DELIVERY_SLOT_INVALID_PERIOD(HttpStatus.BAD_REQUEST, "DELIVERY-002", "종료 시각은 시작 시각 이후여야 합니다."),
    DELIVERY_SLOT_INVALID_CUTOFF(HttpStatus.BAD_REQUEST, "DELIVERY-003", "주문 마감시각은 슬롯 시작 시각 이전이어야 합니다."),
    DELIVERY_SLOT_SOLD_OUT(HttpStatus.CONFLICT, "DELIVERY-004", "선택한 배송 슬롯이 마감되었거나 정원이 초과되었습니다."),
    DELIVERY_SLOT_NOT_APPLICABLE(HttpStatus.CONFLICT, "DELIVERY-005", "해당 하위 주문에는 이 배송 슬롯을 적용할 수 없습니다."),
    DELIVERY_REGION_NOT_FOUND(HttpStatus.NOT_FOUND, "DELIVERY-006", "배송 가능 지역 정보를 찾을 수 없습니다."),
    DELIVERY_REGION_ALREADY_EXISTS(HttpStatus.CONFLICT, "DELIVERY-007", "이미 등록된 우편번호 접두사입니다."),

    // 유료 멤버십 (MEMBERSHIP)
    MEMBERSHIP_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBERSHIP-001", "가입된 멤버십이 없습니다."),
    MEMBERSHIP_ALREADY_ACTIVE(HttpStatus.CONFLICT, "MEMBERSHIP-002", "이미 구독 중인 멤버십이 있습니다."),
    MEMBERSHIP_BILLING_KEY_NOT_REGISTERED(HttpStatus.CONFLICT, "MEMBERSHIP-003", "등록된 결제수단이 없습니다. 먼저 카드를 등록해 주세요."),
    MEMBERSHIP_PLAN_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "MEMBERSHIP-004", "현재 판매하지 않는 플랜입니다."),
    MEMBERSHIP_SUBSCRIBE_PAYMENT_FAILED(HttpStatus.PAYMENT_REQUIRED, "MEMBERSHIP-005", "구독 결제가 거절되었습니다."),
    MEMBERSHIP_NOT_CANCELABLE(HttpStatus.CONFLICT, "MEMBERSHIP-006", "해지할 수 있는 상태의 멤버십이 없습니다."),
    MEMBERSHIP_BILLING_KEY_INVALID(HttpStatus.BAD_REQUEST, "MEMBERSHIP-007", "카드 등록에 실패했습니다."),

    // 정기배송 구독 (DELIVERY_SUBSCRIPTION)
    DELIVERY_SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "DSUB-001", "정기배송을 찾을 수 없습니다."),
    DELIVERY_SUBSCRIPTION_BILLING_KEY_NOT_REGISTERED(HttpStatus.CONFLICT, "DSUB-002", "등록된 결제수단이 없습니다. 먼저 카드를 등록해 주세요."),
    DELIVERY_SUBSCRIPTION_BILLING_KEY_INVALID(HttpStatus.BAD_REQUEST, "DSUB-003", "카드 등록에 실패했습니다."),
    DELIVERY_SUBSCRIPTION_NOT_ACTIVE(HttpStatus.CONFLICT, "DSUB-004", "진행 중인 정기배송만 가능한 작업입니다."),
    DELIVERY_SUBSCRIPTION_NOT_PAUSED(HttpStatus.CONFLICT, "DSUB-005", "일시정지된 정기배송만 재개할 수 있습니다."),
    DELIVERY_SUBSCRIPTION_NOT_CANCELABLE(HttpStatus.CONFLICT, "DSUB-006", "이미 해지된 정기배송입니다."),
    DELIVERY_SUBSCRIPTION_SKIP_WINDOW_CLOSED(HttpStatus.CONFLICT, "DSUB-007", "다음 배송일이 임박해 이번 회차는 건너뛸 수 없습니다."),

    // 선물하기 (GIFT)
    GIFT_CLAIM_NOT_FOUND(HttpStatus.NOT_FOUND, "GIFT-001", "선물 링크를 찾을 수 없습니다."),
    GIFT_CLAIM_ALREADY_CLAIMED(HttpStatus.CONFLICT, "GIFT-002", "이미 수락된 선물 링크입니다."),
    GIFT_CLAIM_EXPIRED(HttpStatus.CONFLICT, "GIFT-003", "선물 링크가 만료되었습니다."),
    GIFT_CLAIM_NOT_CLAIMABLE(HttpStatus.CONFLICT, "GIFT-004", "수락할 수 없는 선물 링크입니다."),
    GIFT_DELIVERY_SLOT_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "GIFT-005", "선물 주문은 배송 슬롯을 선택할 수 없습니다."),
    GIFT_NOT_A_GIFT_ORDER(HttpStatus.BAD_REQUEST, "GIFT-006", "선물 주문이 아닙니다."),

    // 위시리스트/가격 인하 알림 (WISHLIST)
    WISHLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "WISHLIST-001", "위시리스트 항목을 찾을 수 없습니다."),
    WISHLIST_ALREADY_EXISTS(HttpStatus.CONFLICT, "WISHLIST-002", "이미 위시리스트에 담은 상품입니다."),
    WISHLIST_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "WISHLIST-003", "위시리스트에 담을 수 있는 최대 개수를 초과했습니다."),

    // 배송지 주소록 (ADDRESS)
    ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "ADDRESS-001", "배송지를 찾을 수 없습니다."),
    ADDRESS_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "ADDRESS-002", "등록할 수 있는 배송지 개수를 초과했습니다."),

    // 파일 업로드 (UPLOAD)
    UNSUPPORTED_IMAGE(HttpStatus.BAD_REQUEST, "UPLOAD-001", "JPEG, PNG, GIF, WEBP 이미지만 업로드할 수 있습니다."),
    UPLOAD_NOT_FOUND(HttpStatus.NOT_FOUND, "UPLOAD-002", "파일을 찾을 수 없습니다."),
}
