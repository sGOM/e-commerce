package com.example.starter.domain.user.repository

import com.example.starter.domain.user.entity.Role
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface RoleRepository : JpaRepository<Role, Long> {

    @EntityGraph(attributePaths = ["permissions"])
    fun findByName(name: String): Role?
}
