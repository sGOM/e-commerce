package com.example.starter.domain.catalog.repository

import com.example.starter.domain.catalog.entity.Inventory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface InventoryRepository : JpaRepository<Inventory, Long> {

    /**
     * 재고 예약(차감). 단일 원자적 UPDATE 로 동시성 하에서도 초과 판매를 막는다.
     * `quantity - reserved >= qty` 조건을 만족할 때만 reserved 를 늘리며, 영향 행이 0이면 재고 부족이다.
     *
     * JPQL 벌크 UPDATE 는 영속성 컨텍스트를 우회하므로 호출 전 변경을 flush 한다. 1차 캐시는 비우지 않는데,
     * 주문 흐름에서 예약 이후 같은 트랜잭션에서 [Inventory] 를 재조회하지 않으며, clear 가 cart/seller 등
     * 영속 엔티티까지 detach 시키는 부작용이 더 크기 때문이다.
     */
    @Modifying(flushAutomatically = true)
    @Query(
        """
        UPDATE Inventory i
           SET i.reserved = i.reserved + :qty
         WHERE i.option.id = :optionId
           AND i.quantity - i.reserved >= :qty
        """,
    )
    fun reserve(@Param("optionId") optionId: Long, @Param("qty") qty: Int): Int

    /**
     * 재고 예약 복원(취소/결제 실패). reserved 를 되돌린다. 음수 방지를 위해 reserved >= qty 일 때만 적용.
     */
    @Modifying(flushAutomatically = true)
    @Query(
        """
        UPDATE Inventory i
           SET i.reserved = i.reserved - :qty
         WHERE i.option.id = :optionId
           AND i.reserved >= :qty
        """,
    )
    fun release(@Param("optionId") optionId: Long, @Param("qty") qty: Int): Int
}
