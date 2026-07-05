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
import java.time.Instant

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

    // 마지막 담기/수량변경/삭제 시각. [BaseTimeEntity.updatedAt] 은 자식 컬렉션(items) 변경만으로는
    // 갱신되지 않아(부모 엔티티 자체 필드가 dirty 되지 않음) 장바구니 이탈 판단(§ 카트 리마인드
    // 배치)을 위해 별도 필드로 명시 관리한다 — [CartService] 가 항목 변경 메서드 호출 시 [touch] 한다.
    @Column(name = "last_activity_at", nullable = false)
    var lastActivityAt: Instant = Instant.now()
        protected set

    // 마지막 이탈 리마인드 발송 시각. null 이면 아직 리마인드 대상이 된 적 없음. 중복 발송 방지에
    // 쓰인다 — [lastReminderAt] 이 [lastActivityAt] 이후면(=리마인드 이후 새 활동 없음) 이미 이번
    // 이탈 구간에 대해 발송을 마친 것으로 보고 재발송하지 않는다(`CartReminderBatchService`).
    @Column(name = "last_reminder_at")
    var lastReminderAt: Instant? = null
        protected set

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

    /** 장바구니 활동 시각 갱신(담기/수량변경/삭제/병합 시 호출). */
    fun touch() {
        lastActivityAt = Instant.now()
    }

    /** 이탈 리마인드 발송 완료 처리. */
    fun markReminded(at: Instant) {
        lastReminderAt = at
    }
}
