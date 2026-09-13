package com.example.starter.domain.notification.repository

import com.example.starter.domain.notification.entity.Notification
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface NotificationRepository : JpaRepository<Notification, Long> {

    fun findByUserIdOrderByIdDesc(userId: Long, pageable: Pageable): Page<Notification>

    fun findByUserIdAndIsReadFalseOrderByIdDesc(userId: Long, pageable: Pageable): Page<Notification>

    fun countByUserIdAndIsReadFalse(userId: Long): Long

    /** 본인 알림만 읽음 처리 — 소유권 검증. 남의 알림은 404 로 존재를 숨긴다(리뷰/재입고 신청 관례와 동일). */
    fun findByIdAndUserId(id: Long, userId: Long): Optional<Notification>
}
