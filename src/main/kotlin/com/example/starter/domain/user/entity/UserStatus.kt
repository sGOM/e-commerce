package com.example.starter.domain.user.entity

/**
 * 사용자 계정 상태.
 */
enum class UserStatus {
    /** 정상 사용 가능 */
    ACTIVE,

    /** 관리자에 의해 잠김 (로그인 차단) */
    LOCKED,

    /** 장기 미접속 휴면 */
    DORMANT,

    /** 탈퇴 (소프트 삭제) */
    WITHDRAWN,
    ;

    /** 로그인 가능 여부 */
    val canLogin: Boolean
        get() = this == ACTIVE
}
