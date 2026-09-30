package com.example.starter.domain.notification.push

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository

/** 브라우저 웹 푸시 구독(ROADMAP 6.3). [p256dh]·[auth] 는 메시지 암호화 키(RFC 8291). */
@Entity
@Table(name = "push_subscriptions")
class PushSubscription(
    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(nullable = false, length = 1000, unique = true)
    val endpoint: String,

    @Column(nullable = false, length = 100)
    var p256dh: String,

    @Column(nullable = false, length = 50)
    var auth: String,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    /** 같은 브라우저(endpoint)를 다시 구독하면 소유 회원과 키를 최신으로 바꾼다. */
    fun renew(userId: Long, p256dh: String, auth: String) {
        this.userId = userId
        this.p256dh = p256dh
        this.auth = auth
    }
}

interface PushSubscriptionRepository : JpaRepository<PushSubscription, Long> {
    fun findByEndpoint(endpoint: String): PushSubscription?
    fun findByUserId(userId: Long): List<PushSubscription>
}
