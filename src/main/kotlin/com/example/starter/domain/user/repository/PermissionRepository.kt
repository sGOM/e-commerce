package com.example.starter.domain.user.repository

import com.example.starter.domain.user.entity.Permission
import org.springframework.data.jpa.repository.JpaRepository

interface PermissionRepository : JpaRepository<Permission, Long> {

    fun findByName(name: String): Permission?
}
