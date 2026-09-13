package com.example.starter.domain.flashsale.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 타임딜(한정특가). 옵션 단위로 한정 시간·한정 수량 특가를 운영한다(`docs/planning/flash-sale.md`).
 *
 * [productOptionId]/[sellerId] 는 catalog/seller 애그리거트를 참조만 하는 비정규화 id 다(연관관계
 * 대신 순수 id — 이 코드베이스 관례, [com.example.starter.domain.promotion.entity.Collection] 참고).
 * [sellerId] 는 셀러 본인 타임딜 조회·정산 조회 편의를 위해 옵션 생성 시점의 판매자로 스냅샷한다.
 *
 * [originalPrice] 는 등록 시점의 정가(기본가+옵션추가금) 스냅샷이며, 등록 이후 상품가가 바뀌어도
 * 이 값은 변하지 않는다(할인율 표기의 기준을 고정하기 위함). 실제 주문 시 청구가는 [salePrice] 다.
 */
@Entity
@Table(name = "flash_sales")
class FlashSale(
    @Column(name = "product_option_id", nullable = false)
    val productOptionId: Long,

    @Column(name = "seller_id", nullable = false)
    val sellerId: Long,

    @Column(name = "original_price", nullable = false)
    val originalPrice: Long,

    @Column(name = "sale_price", nullable = false)
    val salePrice: Long,

    @Column(name = "limit_quantity", nullable = false)
    val limitQuantity: Int,

    @Column(name = "start_at", nullable = false)
    val startAt: Instant,

    @Column(name = "end_at", nullable = false)
    val endAt: Instant,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    // 한도 원자적 UPDATE(FlashSaleRepository.reserve/release) 로만 변경된다. JPA 변경감지로 직접
    // 갱신하지 않는다(재고 reserved 와 동일한 이유 — 동시성 하에서 단일 UPDATE 문만 신뢰).
    @Column(name = "sold_quantity", nullable = false)
    var soldQuantity: Int = 0

    // 관례상 setter 비공개는 allOpen(프록시 상속) 과 충돌해 컴파일 에러가 나므로 public var 로 두되
    // 호출측은 cancel() 을 통해서만 접근한다(Review.status, Collection.status 참고).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: FlashSaleStatus = FlashSaleStatus.ACTIVE

    /** 관리자 강제 종료(AC — 강제 종료 액션). 이미 종료된 딜은 호출측에서 걸러야 한다. */
    fun cancel() {
        status = FlashSaleStatus.CANCELED
    }

    /**
     * [at] 시점의 실시간 진행 단계(AC3/AC5). 한도 소진과 시간 만료는 모두 [FlashSalePhase.ENDED] 로
     * 통합 표기한다(품절과 구분되는 "딜 종료" 배지, 기획서 §4).
     */
    fun phaseAt(at: Instant): FlashSalePhase = when {
        status == FlashSaleStatus.CANCELED -> FlashSalePhase.CANCELED
        at.isBefore(startAt) -> FlashSalePhase.SCHEDULED
        !at.isBefore(endAt) || soldQuantity >= limitQuantity -> FlashSalePhase.ENDED
        else -> FlashSalePhase.ONGOING
    }
}
