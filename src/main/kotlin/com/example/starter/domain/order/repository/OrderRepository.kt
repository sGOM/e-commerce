package com.example.starter.domain.order.repository

import com.example.starter.domain.order.entity.Order
import com.linecorp.kotlinjdsl.support.spring.data.jpa.repository.KotlinJdslJpqlExecutor
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface OrderRepository : JpaRepository<Order, Long>, KotlinJdslJpqlExecutor {

    /** 내 주문 목록(헤더만). 상세 컬렉션은 상세 조회에서 트랜잭션 내 지연 로딩한다. */
    fun findByUserId(userId: Long, pageable: Pageable): Page<Order>

    fun findByIdAndUserId(id: Long, userId: Long): Optional<Order>

    /** 게스트 주문 조회(주문번호 + 연락처 검증과 함께 사용) */
    fun findByOrderNumber(orderNumber: String): Optional<Order>
}
