package com.example.starter.domain.seller.repository

import com.example.starter.domain.seller.entity.Seller
import com.example.starter.domain.seller.entity.SellerStatus
import com.linecorp.kotlinjdsl.support.spring.data.jpa.repository.KotlinJdslJpqlExecutor
import org.springframework.data.jpa.repository.JpaRepository

interface SellerRepository : JpaRepository<Seller, Long>, KotlinJdslJpqlExecutor {

    fun findByUserId(userId: Long): Seller?

    fun existsByUserId(userId: Long): Boolean

    fun findByStatusOrderByIdDesc(status: SellerStatus): List<Seller>
}
