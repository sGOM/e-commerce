package com.example.starter.domain.address.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 회원 배송지 주소록 항목. 주문에는 [com.example.starter.domain.order.entity.ShippingAddress] 로 복사해 쓰므로
 * 이 엔티티의 변경은 과거 주문에 영향이 없다. [userId] 는 순수 id 참조(코드베이스 관례).
 */
@Entity
@Table(name = "user_addresses")
class UserAddress(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(length = 50)
    var label: String?,

    @Column(name = "receiver_name", nullable = false, length = 100)
    var receiverName: String,

    @Column(name = "receiver_phone", nullable = false, length = 30)
    var receiverPhone: String,

    @Column(nullable = false, length = 10)
    var zipcode: String,

    @Column(nullable = false, length = 255)
    var address1: String,

    @Column(length = 255)
    var address2: String?,

    @Column(name = "is_default", nullable = false)
    var isDefault: Boolean = false,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
