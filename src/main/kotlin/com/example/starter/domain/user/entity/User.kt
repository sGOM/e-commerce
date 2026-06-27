package com.example.starter.domain.user.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table

/**
 * 사용자 계정.
 *
 * [password] 는 nullable — 소셜 로그인으로만 가입한 계정은 비밀번호가 없다.
 * 자체 로그인과 소셜 로그인은 [email] 기준으로 동일 계정에 연동된다
 * (소셜 연동 정보는 [com.example.starter.domain.user.entity.OAuthAccount] 에 별도 저장 — Phase 3).
 */
@Entity
@Table(name = "users")
class User(
    @Column(nullable = false, unique = true, length = 320)
    var email: String,

    @Column(name = "password_hash", length = 100)
    var password: String? = null,

    @Column(nullable = false, length = 50)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: UserStatus = UserStatus.ACTIVE,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_roles",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "role_id")],
    )
    val roles: MutableSet<Role> = mutableSetOf()

    /** 소셜 전용 계정 여부 (비밀번호 미설정) */
    val isSocialOnly: Boolean
        get() = password == null

    fun grantRole(role: Role) {
        roles.add(role)
    }

    fun revokeRole(role: Role) {
        roles.removeIf { it.name == role.name }
    }

    /** 모든 권한 문자열 (역할명 + 역할이 가진 모든 권한명) — Security authority 로 사용 */
    fun authorities(): Set<String> =
        roles.flatMap { role -> listOf(role.name) + role.permissions.map { it.name } }.toSet()
}
