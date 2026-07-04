package com.example.starter.domain.delivery.repository

import com.example.starter.domain.delivery.entity.DeliveryRegion
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface DeliveryRegionRepository : JpaRepository<DeliveryRegion, Long> {

    fun existsByPostalCodePrefix(postalCodePrefix: String): Boolean

    fun findAllByOrderByPostalCodePrefixAsc(): List<DeliveryRegion>

    /** [postalCode] 가 새벽배송 가능 화이트리스트에 있는 접두사로 시작하는지(AC5). */
    @Query(
        """
        SELECT COUNT(r) > 0 FROM DeliveryRegion r
         WHERE r.dawnDeliveryAvailable = true
           AND :postalCode LIKE CONCAT(r.postalCodePrefix, '%')
        """,
    )
    fun existsDawnAvailable(@Param("postalCode") postalCode: String): Boolean
}
