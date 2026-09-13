package com.example.starter.domain.order.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/**
 * 배송지(주문 임베디드 값 객체). 수령인은 주문자와 다를 수 있어 별도로 보관한다.
 *
 * DB 컬럼은 nullable 이다 — [com.example.starter.domain.order.entity.Order.shippingAddress] 자체가
 * 없을 수 있어서다(선물 주문은 수령자가 배송지를 입력하기 전까지 배송지가 없다,
 * `docs/planning/gift-order.md` §9 오픈이슈 #1, V22 마이그레이션). 다만 이 값 객체가 존재한다면
 * (= [Order.shippingAddress] 가 null 이 아니라면) 필드는 항상 전부 채워져 있다(부분 null 없음).
 */
@Embeddable
class ShippingAddress(
    @Column(name = "receiver_name", length = 100)
    val receiverName: String,

    @Column(name = "receiver_phone", length = 30)
    val receiverPhone: String,

    @Column(name = "zipcode", length = 10)
    val zipcode: String,

    @Column(name = "address1", length = 255)
    val address1: String,

    @Column(name = "address2", length = 255)
    val address2: String? = null,
)
