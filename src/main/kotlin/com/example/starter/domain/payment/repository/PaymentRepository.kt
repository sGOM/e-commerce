package com.example.starter.domain.payment.repository

import com.example.starter.domain.payment.entity.Payment
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface PaymentRepository : JpaRepository<Payment, Long> {

    fun findByOrderId(orderId: Long): Optional<Payment>
}
