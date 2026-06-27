package com.example.starter.domain.user.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 세분화된 권한 단위. 예: `USER_READ`, `USER_WRITE`, `AUDIT_READ`.
 * Spring Security 의 authority 로 그대로 사용된다.
 */
@Entity
@Table(name = "permissions")
class Permission(
    @Column(nullable = false, unique = true, length = 100)
    var name: String,

    @Column(length = 255)
    var description: String? = null,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
