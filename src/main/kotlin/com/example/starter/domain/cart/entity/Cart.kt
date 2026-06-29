package com.example.starter.domain.cart.entity

import com.example.starter.common.entity.BaseTimeEntity
import com.example.starter.domain.catalog.entity.ProductOption
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

/**
 * 회원 장바구니. 회원당 1개([userId] unique). 게스트는 장바구니를 갖지 않는다.
 */
@Entity
@Table(name = "carts")
class Cart(
    @Column(name = "user_id", nullable = false, unique = true)
    val userId: Long,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @OneToMany(mappedBy = "cart", cascade = [CascadeType.ALL], orphanRemoval = true)
    val items: MutableList<CartItem> = mutableListOf()

    /** 이미 담긴 옵션이면 수량을 더하고, 아니면 새 항목을 추가한다. 추가/변경된 항목을 반환. */
    fun addOrIncrease(option: ProductOption, quantity: Int): CartItem {
        val existing = items.firstOrNull { it.option.id == option.id }
        if (existing != null) {
            existing.quantity += quantity
            return existing
        }
        val item = CartItem(cart = this, option = option, quantity = quantity)
        items.add(item)
        return item
    }

    fun removeItem(item: CartItem) {
        items.remove(item)
    }

    /** 모든 항목을 비운다(주문 확정 후). orphanRemoval 로 항목 행이 삭제된다. */
    fun clear() {
        items.clear()
    }
}
