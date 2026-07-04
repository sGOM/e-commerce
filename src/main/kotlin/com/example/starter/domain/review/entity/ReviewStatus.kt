package com.example.starter.domain.review.entity

/**
 * 리뷰 노출 상태.
 *
 * - [VISIBLE]: 정상 노출.
 * - [REPORTED]: 신고 누적 임계치 도달 — 오탐 방지를 위해 자동 숨김하지 않고 노출은 유지한 채
 *   관리자 검토 큐에만 올린다(기획서 AC12).
 * - [HIDDEN]: 관리자가 검토 후 숨김 처리. 목록/평균 평점 계산에서 제외되며, 작성자 본인에게만
 *   "숨김 처리됨" 안내와 함께 보인다(AC14).
 */
enum class ReviewStatus {
    VISIBLE,
    REPORTED,
    HIDDEN,
    ;

    /** 고객 대상 목록/평점 집계에 포함할지 — HIDDEN 만 제외한다(REPORTED 는 노출 유지). */
    val isPubliclyVisible: Boolean
        get() = this != HIDDEN
}
