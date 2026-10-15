// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (c) 2026 72no6495

package com.wonderful.launcher.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.UserManager

/**
 * Android のホーム画面ウィジェットを置くための窓口。
 * ウィジェットの ID の確保・解放、表示用 View の作成を担当する。
 */
class WidgetManager(context: Context) {

    private val appContext = context.applicationContext
    val host = AppWidgetHost(appContext, HOST_ID)
    val manager: AppWidgetManager = AppWidgetManager.getInstance(appContext)
    private val userManager = appContext.getSystemService(UserManager::class.java)

    fun startListening() {
        runCatching { host.startListening() }
    }

    fun stopListening() {
        runCatching { host.stopListening() }
    }

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(id: Int) {
        if (id >= 0) runCatching { host.deleteAppWidgetId(id) }
    }

    fun info(id: Int): AppWidgetProviderInfo? =
        if (id < 0) null else runCatching { manager.getAppWidgetInfo(id) }.getOrNull()

    /** 許可済みなら黙ってバインド。未許可なら false（確認画面を出す必要あり） */
    fun bindIfAllowed(id: Int, info: AppWidgetProviderInfo): Boolean =
        runCatching { manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null) }.getOrDefault(false)

    /** インストールされているウィジェット（仕事用プロファイル含む） */
    fun providers(): List<AppWidgetProviderInfo> =
        userManager.userProfiles.flatMap { user ->
            runCatching { manager.getInstalledProvidersForProfile(user) }.getOrDefault(emptyList())
        }

    fun findProvider(flat: String): AppWidgetProviderInfo? {
        val cn = ComponentName.unflattenFromString(flat) ?: return null
        return providers().firstOrNull { it.provider == cn }
    }

    fun createView(context: Context, id: Int, info: AppWidgetProviderInfo): AppWidgetHostView =
        host.createView(context, id, info)

    /** 保存データに無いのに確保されたままの ID を解放する（起動時の掃除） */
    fun releaseOrphans(keep: Set<Int>) {
        runCatching {
            host.appWidgetIds.filter { it !in keep }.forEach { deleteId(it) }
        }
    }

    companion object {
        const val HOST_ID = 0x5E7A
    }
}
