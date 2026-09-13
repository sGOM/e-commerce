package com.example.starter.domain.address.repository

import com.example.starter.domain.address.entity.UserAddress
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserAddressRepository : JpaRepository<UserAddress, Long> {

    /** 본인 배송지만 — 남의 id 는 없는 것으로 취급(소유권 격리). */
    fun findByIdAndUserId(id: Long, userId: Long): UserAddress?

    fun countByUserId(userId: Long): Long

    /** 기본 배송지 먼저, 그다음 최근 등록순. */
    fun findByUserIdOrderByIsDefaultDescIdDesc(userId: Long): List<UserAddress>

    fun findFirstByUserIdOrderByIdDesc(userId: Long): UserAddress?

    /**
     * 기본 배송지 해제. 새 기본을 지정하기 전에 DB 에 먼저 반영해야 부분 유니크 인덱스
     * `uq_user_addresses_default` 에 걸리지 않는다. 벌크 업데이트라 영속성 컨텍스트를 비워
     * 메모리의 오래된 isDefault 값이 남지 않게 한다 — 호출 후 엔티티는 다시 조회해야 한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE UserAddress a SET a.isDefault = false WHERE a.userId = :userId AND a.isDefault = true")
    fun clearDefault(@Param("userId") userId: Long): Int
}
