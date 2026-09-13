package com.example.starter.domain.point.repository

import com.example.starter.domain.point.entity.PointAccount
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface PointAccountRepository : JpaRepository<PointAccount, Long> {

    fun findByUserId(userId: Long): Optional<PointAccount>

    @EntityGraph(attributePaths = ["transactions"])
    fun findWithTransactionsByUserId(userId: Long): Optional<PointAccount>

    @EntityGraph(attributePaths = ["lots"])
    fun findWithLotsByUserId(userId: Long): Optional<PointAccount>
}
