package com.wonderful.launcher.data

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.wonderful.launcher.system.BadgeListenerService
import com.wonderful.launcher.system.LauncherAccessibilityService

/** 設定画面の「権限」タブに並べる特別な権限 */
enum class SpecialPermission(val title: String, val unlocks: String) {
    DEFAULT_HOME(
        "デフォルトのホームアプリ",
        "ホームボタンでこのランチャーが開く・ホームボタンジェスチャー",
    ),
    NOTIFICATIONS(
        "通知へのアクセス",
        "アプリアイコンに通知の件数バッジを表示（通知の内容は読みません）",
    ),
    ACCESSIBILITY(
        "ユーザー補助（ジェスチャー）",
        "ジェスチャーで画面ロック・スクリーンショット・電源メニュー・タスク一覧",
    ),
    USAGE(
        "使用状況へのアクセス",
        "「最近」パネルに端末全体の使用履歴を表示",
    ),
    DND(
        "おやすみモードへのアクセス",
        "ジェスチャーでおやすみモードを切り替え",
    ),
    ;

    fun isGranted(context: Context): Boolean = when (this) {
        DEFAULT_HOME -> SystemActions.isDefaultHome(context)
        NOTIFICATIONS -> NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        ACCESSIBILITY -> LauncherAccessibilityService.isRunning || accessibilityEnabledInSettings(context)
        USAGE -> SystemActions.hasUsageAccess(context)
        DND -> context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
    }

    /** 許可するための設定画面 */
    fun requestIntent(context: Context): Intent = when (this) {
        DEFAULT_HOME -> SystemActions.requestDefaultHome(context)
        NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(context, BadgeListenerService::class.java).flattenToString(),
                )
        } else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        USAGE -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        DND -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun request(context: Context) {
        if (!SystemActions.safeStart(context, requestIntent(context), toast = false)) {
            // 機種によって詳細画面が無いので一般の設定画面へ
            SystemActions.safeStart(
                context,
                Intent(
                    if (this == NOTIFICATIONS) Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                    else Settings.ACTION_SETTINGS,
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    companion object {
        private fun accessibilityEnabledInSettings(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val me = ComponentName(context, LauncherAccessibilityService::class.java)
            return enabled.split(':').any {
                ComponentName.unflattenFromString(it) == me
            }
        }
    }
}
