package com.wonderful.launcher.data

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.WallpaperManager
import android.app.role.RoleManager
import android.app.usage.UsageStatsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.AlarmClock
import android.provider.Settings
import android.content.pm.LauncherApps
import android.widget.Toast
import java.io.File
import kotlin.math.max

/** OS へのお願いごと（起動・アンインストール・通知パネル等）をまとめたもの */
object SystemActions {

    fun launch(context: Context, app: AppInfo, bounds: Rect? = null, opts: Bundle? = null): Boolean {
        val la = context.getSystemService(LauncherApps::class.java)
        return runCatching {
            la.startMainActivity(app.component, app.user, bounds, opts)
            true
        }.getOrElse {
            Toast.makeText(context, "起動できませんでした: ${app.label}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun openAppInfo(context: Context, app: AppInfo) {
        val la = context.getSystemService(LauncherApps::class.java)
        runCatching { la.startAppDetailsActivity(app.component, app.user, null, null) }
    }

    fun uninstall(context: Context, app: AppInfo) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        safeStart(context, intent)
    }

    fun webSearch(context: Context, query: String) {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", query)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!safeStart(context, intent, toast = false)) {
            safeStart(
                context,
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun openAlarms(context: Context) {
        safeStart(context, Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** 通知パネルを下ろす（非公開 API なので一部機種では動かない） */
    @SuppressLint("WrongConstant")
    fun expandNotifications(context: Context) {
        runCatching {
            val sbs = context.getSystemService("statusbar")
            val clazz = Class.forName("android.app.StatusBarManager")
            clazz.getMethod("expandNotificationsPanel").invoke(sbs)
        }.onFailure {
            Toast.makeText(context, "この端末では通知パネルを開けません", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestDefaultHome(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                return rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        }
        return Intent(Settings.ACTION_HOME_SETTINGS)
    }

    fun isDefaultHome(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val res = context.packageManager.resolveActivity(intent, 0)
        return res?.activityInfo?.packageName == context.packageName
    }

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings(context: Context) {
        safeStart(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** システムの使用履歴から最近使ったパッケージ（新しい順） */
    fun systemRecentPackages(context: Context, limit: Int): List<Pair<String, Long>> {
        if (!hasUsageAccess(context)) return emptyList()
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val end = System.currentTimeMillis()
        val start = end - 3L * 24 * 60 * 60 * 1000
        val stats = runCatching { usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end) }
            .getOrNull().orEmpty()
        return stats.asSequence()
            .filter { it.lastTimeUsed > 0 && it.packageName != context.packageName }
            .groupBy { it.packageName }
            .map { (pkg, list) -> pkg to list.maxOf { it.lastTimeUsed } }
            .sortedByDescending { it.second }
            .take(limit)
    }

    // ── 壁紙 ──

    fun wallpaperFile(context: Context) = File(context.filesDir, "wallpaper.jpg")

    /** 選んだ画像を画面サイズ程度に縮小してアプリ内に保存する */
    fun importWallpaper(context: Context, uri: Uri): Boolean = runCatching {
        val dm = context.resources.displayMetrics
        val target = max(dm.widthPixels, dm.heightPixels)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return false
        wallpaperFile(context).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bmp.recycle()
        true
    }.getOrDefault(false)

    fun loadWallpaper(context: Context): Bitmap? = runCatching {
        val f = wallpaperFile(context)
        if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }.getOrNull()

    /** 選んだ画像を OS の壁紙（ロック画面含む）にも設定する */
    fun applyAsSystemWallpaper(context: Context): Boolean = runCatching {
        val bmp = loadWallpaper(context) ?: return false
        WallpaperManager.getInstance(context).setBitmap(bmp)
        true
    }.getOrDefault(false)

    fun safeStart(context: Context, intent: Intent, toast: Boolean = true): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        if (toast) Toast.makeText(context, "対応するアプリがありません", Toast.LENGTH_SHORT).show()
        false
    } catch (e: SecurityException) {
        if (toast) Toast.makeText(context, "権限がありません", Toast.LENGTH_SHORT).show()
        false
    }
}
