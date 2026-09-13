package com.example.starter.domain.promotion.entity

/**
 * 기획전/컬렉션 노출 상태(기획서 AC2).
 *
 * - [DRAFT]: 작성 중, 고객에게 노출되지 않는다.
 * - [PUBLISHED]: 노출 대상. 실제 고객 노출 여부는 [Collection.isActiveAt] 로 기간까지 함께 판단한다
 *   (`endAt` 경과 시 상태값을 배치로 강제 전이하지 않고, 조회 시점 필터로만 처리 — 오픈 이슈 #1,
 *   구현 단순성을 택함. 운영 대시보드에서 "기간 만료"를 구분해 보여주고 싶다면 상태와 별개로
 *   `endAt < now` 를 프론트에서 함께 표시한다).
 * - [ENDED]: 관리자가 명시적으로 종료 처리한 상태.
 */
enum class CollectionStatus {
    DRAFT,
    PUBLISHED,
    ENDED,
}
