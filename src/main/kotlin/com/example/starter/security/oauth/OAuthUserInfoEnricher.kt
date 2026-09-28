package com.example.starter.security.oauth

import com.example.starter.domain.user.entity.OAuthProvider
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest

/** 사용자 정보 응답만으로 부족한 값을 제공자 API 로 추가 조회한다(제공자당 하나, 필요한 제공자만). */
interface OAuthUserInfoEnricher {
    val provider: OAuthProvider

    fun enrich(info: OAuthUserInfo, userRequest: OAuth2UserRequest): OAuthUserInfo
}
