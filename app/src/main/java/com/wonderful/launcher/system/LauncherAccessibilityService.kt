package com.wonderful.launcher.system

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * 画面ロック・スクリーンショット・電源メニューなど、
 * 普通のアプリには許されていない操作をジェスチャーから行うためのユーザー補助サービス。
 * 画面の内容は一切読み取らない（イベントも受け取らない設定）。
 */
class LauncherAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: LauncherAccessibilityService? = null

        val isRunning: Boolean get() = instance != null

        /** GLOBAL_ACTION_* を実行。サービスが無効なら false */
        fun perform(action: Int): Boolean = instance?.performGlobalAction(action) ?: false
    }
}
