package com.example.starter.domain.user.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table

/**
 * 역할. Spring Security 규칙상 이름은 `ROLE_` 접두사를 가진다 (예: `ROLE_USER`, `ROLE_ADMIN`).
 * 역할은 여러 [Permission] 을 묶는다 (RBAC).
 */
@Entity
@Table(name = "roles")
class Role(
    @Column(nullable = false, unique = true, length = 50)
    var name: String,

    @Column(length = 255)
    var description: String? = null,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @ManyToMany(fetch = FetchType.LAZY, cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @JoinTable(
        name = "role_permissions",
        joinColumns = [JoinColumn(name = "role_id")],
        inverseJoinColumns = [JoinColumn(name = "permission_id")],
    )
    val permissions: MutableSet<Permission> = mutableSetOf()
}
