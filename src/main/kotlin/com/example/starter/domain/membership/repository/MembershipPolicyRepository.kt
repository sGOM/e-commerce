package com.example.starter.domain.membership.repository

import com.example.starter.domain.membership.entity.MembershipPolicy
import org.springframework.data.jpa.repository.JpaRepository

interface MembershipPolicyRepository : JpaRepository<MembershipPolicy, Long> {

    fun findFirstByOrderByIdAsc(): MembershipPolicy?
}
