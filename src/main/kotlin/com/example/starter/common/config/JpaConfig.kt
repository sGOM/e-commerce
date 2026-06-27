package com.example.starter.common.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

/**
 * JPA Auditing 활성화 — [com.example.starter.common.entity.BaseTimeEntity] 의
 * createdAt/updatedAt 자동 채움.
 */
@Configuration
@EnableJpaAuditing
class JpaConfig
