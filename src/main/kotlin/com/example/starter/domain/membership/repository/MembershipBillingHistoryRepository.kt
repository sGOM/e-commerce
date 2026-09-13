package com.example.starter.domain.membership.repository

import com.example.starter.domain.membership.entity.MembershipBillingHistory
import org.springframework.data.jpa.repository.JpaRepository

interface MembershipBillingHistoryRepository : JpaRepository<MembershipBillingHistory, Long> {

    fun findByMembershipIdOrderByIdDesc(membershipId: Long): List<MembershipBillingHistory>
}
