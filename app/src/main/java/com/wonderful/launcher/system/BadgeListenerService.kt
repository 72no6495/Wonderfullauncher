package com.wonderful.launcher.system

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 通知の件数をアプリごとに数えて、アイコンにバッジを出すためのサービス。
 * 通知の中身は読まず、パッケージ名と件数だけを使う。
 */
class BadgeListenerService : NotificationListenerService() {

    override fun onListenerConnected() = recount()
    override fun onListenerDisconnected() {
        _counts.value = emptyMap()
    }
    override fun onNotificationPosted(sbn: StatusBarNotification?) = recount()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = recount()

    private fun recount() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        _counts.value = active.asSequence()
            .filter { it.isClearable && !it.isOngoing }
            // まとめ通知（グループの親）は数えない
            .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .groupingBy { it.packageName }
            .eachCount()
    }

    companion object {
        private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())

        /** パッケージ名 → 通知件数 */
        val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()
    }
}
