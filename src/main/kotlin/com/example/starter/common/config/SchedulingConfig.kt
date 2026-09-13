package com.example.starter.common.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * 스케줄링 활성화. 실제 주기 작업 빈은 각 도메인에서 `@ConditionalOnProperty` 로 선택 등록한다
 * (예: 정산 스케줄러). 등록된 `@Scheduled` 빈이 없으면 아무 작업도 돌지 않는다.
 */
@Configuration
@EnableScheduling
class SchedulingConfig
