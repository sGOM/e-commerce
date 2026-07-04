package com.example.starter.domain.notification.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 인앱 알림함(최소 채널). 재입고 알림 전용이 아니라, 향후 주문상태변경/쿠폰 등 다른 알림도 재사용할 수
 * 있도록 [type]/[title]/[body]/[linkUrl] 로 범용화했다(`docs/planning/restock-alert.md` 오픈 이슈 2).
 *
 * [userId] 는 다른 애그리거트 참조는 연관관계 대신 순수 id 로 두는 이 코드베이스 관례를 따른다
 * ([com.example.starter.domain.review.entity.Review.userId] 참고).
 *
 * 이메일/푸시 등 외부 채널은 아직 인프라가 없어 out of scope 다(README 공통 오픈 이슈).
 * 채널을 늘릴 때는 이 엔티티를 건드리지 않고, [com.example.starter.domain.notification.NotificationService]
 * 안에 채널별 발송기를 추가하는 방식으로 확장한다.
 */
@Entity
@Table(name = "notifications")
class Notification(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val type: NotificationType,

    @Column(nullable = false, length = 200)
    val title: String,

    @Column(columnDefinition = "text", nullable = false)
    val body: String,

    @Column(name = "link_url", length = 500)
    val linkUrl: String? = null,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(name = "is_read", nullable = false)
    var isRead: Boolean = false
        protected set

    /** 읽음 처리(AC8). 이미 읽은 알림에 다시 호출해도 멱등. */
    fun markRead() {
        isRead = true
    }
}
