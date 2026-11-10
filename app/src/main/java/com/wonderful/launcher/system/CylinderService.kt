package com.wonderful.launcher.system

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.wonderful.launcher.MainActivity
import com.wonderful.launcher.R
import com.wonderful.launcher.data.AppOrder
import com.wonderful.launcher.data.CylinderAction
import com.wonderful.launcher.data.CylinderConfig
import com.wonderful.launcher.data.CylinderItem
import com.wonderful.launcher.data.LauncherStore
import com.wonderful.launcher.data.SystemActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min

/**
 * シリンダーメニュー。ほかのアプリを使っている時、画面の右上の端を上から下へスワイプすると出てくる。
 *
 * 軽くするための工夫：
 * - 出ていない時は、右上の細い透明な「反応する所」しかない（何も描かない・何も動かない）
 * - シリンダーのウィンドウは出す時に作り、引っ込んだら捨てる
 * - アイコンは出す時に読み、引っ込んだら捨てる（「アイコンを覚えておく」が ON の時だけ残す）
 */
class CylinderService : Service() {

    private lateinit var wm: WindowManager
    private val store by lazy { LauncherStore.get(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    private var trigger: TriggerView? = null
    private var triggerKey: Triple<Float, Float, Float>? = null
    private var cylinder: CylinderView? = null
    private var cfg = CylinderConfig()
    private var items: List<CylinderItem> = emptyList()
    private val iconCache = HashMap<String, Bitmap>()
    private var foreground = false

    private val autoHide = Runnable { cylinder?.close() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        scope.launch {
            combine(
                store.data.map { it.settings.cylinder to it.cylinderItems }.distinctUntilChanged(),
                launcherVisible,
            ) { (c, list), home -> Triple(c, list, home) }
                .collect { (c, list, home) -> apply(c, list, home) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        cfg = store.data.value.settings.cylinder
        updateForeground()
        if (intent?.action == ACTION_SHOW) handler.post { show() }
        return START_STICKY
    }

    private fun apply(c: CylinderConfig, list: List<CylinderItem>, home: Boolean) {
        val itemsChanged = list != items
        cfg = c
        items = list
        if (!c.enabled || !Settings.canDrawOverlays(this)) {
            removeTrigger()
            removeCylinder()
            stopSelf()
            return
        }
        if (itemsChanged) iconCache.clear()
        updateForeground()
        // ランチャーを見ている時やシリンダーが出ている時は、反応する所は要らない
        if (home || cylinder != null) removeTrigger() else addTrigger()
    }

    // ─────────────────────────────────────────────
    // 常駐（通知）
    // ─────────────────────────────────────────────

    private fun updateForeground() {
        if (cfg.keepAlive && !foreground) {
            val nm = getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "シリンダーメニュー", NotificationManager.IMPORTANCE_MIN).apply {
                        setShowBadge(false)
                        description = "シリンダーメニューを待機させておくための通知です"
                    },
                )
            }
            val n: Notification = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("シリンダーメニュー待機中")
                .setContentText("右上の端を下にスワイプで表示")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .setSilent(true)
                .build()
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(NOTIFICATION_ID, n)
                }
                foreground = true
            }
        } else if (!cfg.keepAlive && foreground) {
            @Suppress("DEPRECATION")
            stopForeground(true)
            foreground = false
        }
    }

    // ─────────────────────────────────────────────
    // 反応する所（右上の端の細い透明な帯）
    // ─────────────────────────────────────────────

    private fun dp(v: Float) = (v * resources.displayMetrics.density)

    private fun overlayType() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun addTrigger() {
        val key = Triple(cfg.triggerWidthDp, cfg.triggerTopDp, cfg.triggerLengthDp)
        val existing = trigger
        if (existing != null && triggerKey == key && existing.showHint == cfg.showTrigger) return
        removeTrigger()
        val v = TriggerView(this, cfg.showTrigger, cfg.color.argb.toInt()) { show() }
        val lp = WindowManager.LayoutParams(
            dp(cfg.triggerWidthDp).toInt(), dp(cfg.triggerLengthDp).toInt(),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            y = dp(cfg.triggerTopDp).toInt()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            title = "CylinderTrigger"
        }
        if (runCatching { wm.addView(v, lp) }.isSuccess) {
            trigger = v
            triggerKey = key
        }
    }

    private fun removeTrigger() {
        trigger?.let { runCatching { wm.removeView(it) } }
        trigger = null
    }

    // ─────────────────────────────────────────────
    // シリンダー
    // ─────────────────────────────────────────────

    private fun show() {
        // 万一出せなくても、ランチャーごと落ちないようにする
        try {
            showCylinder()
        } catch (t: Throwable) {
            android.util.Log.e("CylinderService", "シリンダーを出せませんでした", t)
            removeCylinder()
        }
    }

    private fun showCylinder() {
        if (cylinder != null || !Settings.canDrawOverlays(this)) return
        val entries = items.mapNotNull(::entryFor)
        val bounds = screenSize()
        val radius = bounds.second / 2f * cfg.size.coerceIn(0.35f, 0.95f)
        val centerY = bounds.second * cfg.centerY.coerceIn(0.2f, 0.8f)
        val pad = dp(12f)
        val labelSpace = if (cfg.showLabel) dp(170f) else 0f
        val w = min(bounds.first.toFloat(), radius + pad + labelSpace).toInt()
        val h = (radius * 2 + pad * 2).toInt()
        val top = (centerY - radius - pad).toInt()
        val v = CylinderView(
            this, cfg, entries, radius, radius + pad,
            onPick = { e ->
                runCatching { run(e.item) }.onFailure { android.util.Log.e("CylinderService", "起動できませんでした", it) }
                removeCylinder()
            },
            onClosed = { removeCylinder() },
        )
        v.onInteract = { restartAutoHide() }
        val lp = WindowManager.LayoutParams(
            w, h, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            y = top
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            title = "CylinderMenu"
        }
        if (runCatching { wm.addView(v, lp) }.isFailure) return
        cylinder = v
        removeTrigger()
        if (cfg.haptics) v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        restartAutoHide()
        loadIcons(entries, v)
    }

    private fun restartAutoHide() {
        handler.removeCallbacks(autoHide)
        handler.postDelayed(autoHide, (cfg.autoHideSec.coerceIn(1f, 60f) * 1000).toLong())
    }

    private fun removeCylinder() {
        handler.removeCallbacks(autoHide)
        val v = cylinder ?: return
        cylinder = null
        runCatching { wm.removeView(v) }
        if (!cfg.cacheIcons) iconCache.clear()
        // また右上の端を待つ
        if (cfg.enabled && !launcherVisible.value) addTrigger()
    }

    private fun screenSize(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val m = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
            m.widthPixels to m.heightPixels
        }

    private fun entryFor(item: CylinderItem): CylinderEntry? {
        item.action?.let { return CylinderEntry(item, it.label, it.short) }
        val key = item.app ?: return null
        val info = resolve(key) ?: return null
        return CylinderEntry(item, info.label?.toString().orEmpty(), info.label?.toString()?.take(2).orEmpty(), iconCache[key])
    }

    /** アイコンは裏で読み、読めたものから表示する */
    private fun loadIcons(entries: List<CylinderEntry>, view: CylinderView) {
        val need = entries.filter { it.icon == null && it.item.app != null }
        if (need.isEmpty()) return
        scope.launch {
            val size = dp(64f).toInt()
            val density = resources.displayMetrics.densityDpi
            val loaded = withContext(Dispatchers.Default) {
                need.mapNotNull { e ->
                    val key = e.item.app ?: return@mapNotNull null
                    val d = runCatching { resolve(key)?.getBadgedIcon(density) }.getOrNull() ?: return@mapNotNull null
                    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    d.setBounds(0, 0, size, size)
                    d.draw(Canvas(bmp))
                    key to bmp
                }.toMap()
            }
            iconCache.putAll(loaded)
            if (cylinder !== view) return@launch
            need.forEach { e -> e.icon = loaded[e.item.app] }
            view.invalidate()
        }
    }

    private fun resolve(key: String): LauncherActivityInfo? = runCatching {
        val la = getSystemService(LauncherApps::class.java)
        val at = key.lastIndexOf('@')
        val base = if (at > 0) key.substring(0, at) else key
        val user: UserHandle = if (at > 0) {
            getSystemService(UserManager::class.java).getUserForSerialNumber(key.substring(at + 1).toLong()) ?: return null
        } else Process.myUserHandle()
        val cn = ComponentName.unflattenFromString(base) ?: return null
        la.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(cn), user)
    }.getOrNull()

    /** 選んだものを実行する */
    private fun run(item: CylinderItem) {
        val key = item.app
        if (key != null) {
            val info = resolve(key) ?: return
            val ok = runCatching {
                getSystemService(LauncherApps::class.java).startMainActivity(info.componentName, info.user, null, null)
            }.isSuccess
            if (ok) {
                val now = System.currentTimeMillis()
                store.update { AppOrder.recordLaunch(it, key, now, java.time.LocalDate.now().toEpochDay()) }
            }
            return
        }
        when (item.action ?: return) {
            CylinderAction.HOME -> openLauncher(null)
            CylinderAction.SEARCH -> openLauncher("SEARCH")
            CylinderAction.RECENT -> openLauncher("RECENT")
            CylinderAction.FOLDERS -> openLauncher("FOLDERS")
            CylinderAction.WIDGETS -> openLauncher("WIDGETS")
            CylinderAction.SETTINGS -> openLauncher("SETTINGS")
            CylinderAction.NOTIFICATIONS -> SystemActions.expandNotifications(this)
            CylinderAction.QUICK_SETTINGS -> SystemActions.expandQuickSettings(this)
            CylinderAction.CAMERA -> SystemActions.openCamera(this)
            CylinderAction.ASSISTANT -> SystemActions.openAssistant(this)
            CylinderAction.FLASHLIGHT -> SystemActions.toggleFlashlight(this)
            CylinderAction.DND -> SystemActions.toggleDnd(this)
            CylinderAction.ALARM -> SystemActions.openAlarms(this)
            CylinderAction.LOCK_SCREEN -> SystemActions.lockScreen(this)
            CylinderAction.SCREENSHOT -> {
                // シリンダーが写らないよう、引っ込んでから撮る
                handler.postDelayed({ SystemActions.screenshot(this) }, 350)
            }
            CylinderAction.SYSTEM_RECENTS -> SystemActions.globalAction(this, AccessibilityService.GLOBAL_ACTION_RECENTS)
            CylinderAction.POWER_MENU -> SystemActions.globalAction(this, AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
        }
    }

    private fun openLauncher(panel: String?) {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .apply { if (panel != null) putExtra(MainActivity.EXTRA_PANEL, panel) }
        SystemActions.safeStart(this, intent)
    }

    override fun onDestroy() {
        removeTrigger()
        removeCylinder()
        iconCache.clear()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "cylinder"
        private const val NOTIFICATION_ID = 7301
        const val ACTION_SHOW = "com.wonderful.launcher.CYLINDER_SHOW"

        /** ランチャー（ホーム画面）が見えているか。見えている間は右上の端に反応しない */
        val launcherVisible = MutableStateFlow(false)

        private fun wanted(context: Context): Boolean {
            val c = LauncherStore.get(context).data.value.settings.cylinder
            return c.enabled && Settings.canDrawOverlays(context)
        }

        /** 設定に合わせて動かす / 止める（ホーム画面が出た時や設定を変えた時に呼ぶ） */
        fun sync(context: Context) {
            val intent = Intent(context, CylinderService::class.java)
            if (wanted(context)) start(context, intent) else context.stopService(intent)
        }

        /** 試しに出す */
        fun showNow(context: Context) {
            if (!wanted(context)) return
            start(context, Intent(context, CylinderService::class.java).setAction(ACTION_SHOW))
        }

        private fun start(context: Context, intent: Intent) {
            val keep = LauncherStore.get(context).data.value.settings.cylinder.keepAlive
            runCatching {
                if (keep) ContextCompat.startForegroundService(context, intent) else context.startService(intent)
            }
        }
    }
}

/**
 * 右上の端の細い帯。上から下へのスワイプだけを見る。何も描かない（位置合わせ用の表示が ON の時だけ線を描く）
 */
@SuppressLint("ViewConstructor")
class TriggerView(context: Context, val showHint: Boolean, color: Int, private val onSwipeDown: () -> Unit) : View(context) {
    private val density = resources.displayMetrics.density
    private var startX = 0f
    private var startY = 0f
    private var fired = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = (color and 0x00FFFFFF) or (0x88 shl 24) }

    init {
        setWillNotDraw(!showHint)
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        val w = 3f * density
        canvas.drawRoundRect(width - w, 0f, width.toFloat(), height.toFloat(), w, w, paint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { startX = ev.rawX; startY = ev.rawY; fired = false }
            MotionEvent.ACTION_MOVE -> {
                val dy = ev.rawY - startY
                val dx = ev.rawX - startX
                if (!fired && dy > 24f * density && dy > abs(dx)) {
                    fired = true
                    onSwipeDown()
                }
            }
        }
        return true
    }
}
