package com.example.starter.domain.delivery.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 새벽배송 가능 지역 화이트리스트(MVP — 우편번호 접두사 단순 매핑, `docs/planning/delivery-slot.md` §4).
 * 정교한 권역 폴리곤 관리는 범위 밖. [postalCodePrefix] 로 시작하는 우편번호는 이 행의 설정을 따른다.
 */
@Entity
@Table(name = "delivery_regions")
class DeliveryRegion(
    @Column(name = "postal_code_prefix", nullable = false, unique = true, length = 10)
    val postalCodePrefix: String,

    @Column(name = "dawn_delivery_available", nullable = false)
    var dawnDeliveryAvailable: Boolean = true,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
