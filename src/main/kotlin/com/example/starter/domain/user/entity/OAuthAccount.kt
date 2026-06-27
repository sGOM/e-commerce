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
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 소셜 계정 연동 정보. 한 [User] 가 여러 제공자 계정을 연동할 수 있다.
 * (provider, providerId) 조합이 유일하다.
 */
@Entity
@Table(
    name = "oauth_accounts",
    uniqueConstraints = [UniqueConstraint(name = "uk_oauth_provider_provider_id", columnNames = ["provider", "provider_id"])],
)
class OAuthAccount(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var provider: OAuthProvider,

    @Column(name = "provider_id", nullable = false, length = 255)
    var providerId: String,

    @Column(length = 320)
    var email: String? = null,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
