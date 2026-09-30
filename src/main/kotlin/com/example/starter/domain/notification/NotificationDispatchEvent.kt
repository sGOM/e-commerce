package com.example.starter.domain.notification

/**
 * 인앱 밖(메일·웹 푸시) 채널로도 나가야 하는 알림이 저장됐을 때 발행된다. 채널별 리스너가 커밋 후 비동기로 보낸다
 * ([com.example.starter.domain.notification.email.NotificationEmailListener], [com.example.starter.domain.notification.push.NotificationPushListener]).
 */
data class NotificationDispatchEvent(val userId: Long, val title: String, val body: String, val linkUrl: String?)
