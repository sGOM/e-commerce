package com.example.starter.domain.order.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/**
 * 배송지(주문 임베디드 값 객체). 수령인은 주문자와 다를 수 있어 별도로 보관한다.
 */
@Embeddable
class ShippingAddress(
    @Column(name = "receiver_name", nullable = false, length = 100)
    val receiverName: String,

    @Column(name = "receiver_phone", nullable = false, length = 30)
    val receiverPhone: String,

    @Column(name = "zipcode", nullable = false, length = 10)
    val zipcode: String,

    @Column(name = "address1", nullable = false, length = 255)
    val address1: String,

    @Column(name = "address2", length = 255)
    val address2: String? = null,
)
