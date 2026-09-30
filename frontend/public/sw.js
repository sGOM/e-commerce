// 웹 푸시 서비스워커(ROADMAP 6.3). 서버(NotificationPushListener)가 보낸 { title, body, url } 을 알림으로 띄우고,
// 알림을 누르면 해당 화면을 연다(이미 열린 탭이 있으면 그 탭으로 이동).
self.addEventListener('push', (event) => {
  const data = event.data ? event.data.json() : {}
  event.waitUntil(
    self.registration.showNotification(data.title || '알림', {
      body: data.body || '',
      icon: '/favicon.svg',
      data: { url: data.url || '/notifications' },
    }),
  )
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const url = new URL(event.notification.data.url, self.location.origin).href
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windows) => {
      const tab = windows.find((w) => new URL(w.url).origin === self.location.origin)
      return tab ? tab.focus().then(() => tab.navigate(url)) : self.clients.openWindow(url)
    }),
  )
})
