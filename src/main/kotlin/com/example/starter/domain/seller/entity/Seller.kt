package com.example.starter.domain.seller.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 입점 판매자(상점). 마켓플레이스의 각 판매 주체.
 *
 * [userId] 는 `ROLE_SELLER` 를 가진 [com.example.starter.domain.user.entity.User] 와 1:1 로 연결된다.
 * 입점 신청 시 [SellerStatus.PENDING] 으로 생성되고, 관리자 승인으로 [SellerStatus.ACTIVE] 가 된다.
 */
@Entity
@Table(name = "sellers")
class Seller(
    @Column(name = "user_id", nullable = false, unique = true)
    val userId: Long,

    @Column(name = "store_name", nullable = false, length = 100)
    var storeName: String,

    @Column(length = 500)
    var description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: SellerStatus = SellerStatus.PENDING,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
