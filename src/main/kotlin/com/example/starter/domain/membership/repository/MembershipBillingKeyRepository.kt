package com.example.starter.domain.membership.repository

import com.example.starter.domain.membership.entity.MembershipBillingKey
import org.springframework.data.jpa.repository.JpaRepository

interface MembershipBillingKeyRepository : JpaRepository<MembershipBillingKey, Long> {

    fun findByUserId(userId: Long): MembershipBillingKey?
}
