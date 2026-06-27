package com.example.starter.domain.user.repository

import com.example.starter.domain.user.entity.OAuthAccount
import com.example.starter.domain.user.entity.OAuthProvider
import org.springframework.data.jpa.repository.JpaRepository

interface OAuthAccountRepository : JpaRepository<OAuthAccount, Long> {

    fun findByProviderAndProviderId(provider: OAuthProvider, providerId: String): OAuthAccount?
}
