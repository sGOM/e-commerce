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

    // 사용자 (USER)
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER-001", "사용자를 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "USER-002", "이미 사용 중인 이메일입니다."),
    USER_INACTIVE(HttpStatus.FORBIDDEN, "USER-003", "비활성화된 계정입니다."),

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

    // 장바구니 (CART)
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "CART-001", "장바구니 항목을 찾을 수 없습니다."),

    // 주문 (ORDER)
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER-001", "주문을 찾을 수 없습니다."),
    EMPTY_ORDER(HttpStatus.BAD_REQUEST, "ORDER-002", "주문할 상품이 없습니다."),
    ORDER_NOT_CANCELABLE(HttpStatus.CONFLICT, "ORDER-003", "현재 상태에서는 주문을 취소할 수 없습니다."),
    ORDER_NOT_PAYABLE(HttpStatus.CONFLICT, "ORDER-004", "결제할 수 없는 주문 상태입니다."),
    SUB_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER-005", "하위 주문을 찾을 수 없습니다."),
    SUB_ORDER_NOT_SHIPPABLE(HttpStatus.CONFLICT, "ORDER-006", "결제 완료/상품준비 상태에서만 발송할 수 있습니다."),

    // 결제 (PAYMENT)
    PAYMENT_FAILED(HttpStatus.PAYMENT_REQUIRED, "PAYMENT-001", "결제가 거절되었습니다."),

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
}
