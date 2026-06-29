package com.example.starter.domain.cart.repository

import com.example.starter.domain.cart.entity.Cart
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface CartRepository : JpaRepository<Cart, Long> {

    fun findByUserId(userId: Long): Optional<Cart>

    /** 장바구니 + 항목 + 옵션/상품/재고를 한 번에 로딩 (조회 시 N+1 방지) */
    @EntityGraph(attributePaths = ["items", "items.option", "items.option.product", "items.option.inventory"])
    fun findWithItemsByUserId(userId: Long): Optional<Cart>
}
