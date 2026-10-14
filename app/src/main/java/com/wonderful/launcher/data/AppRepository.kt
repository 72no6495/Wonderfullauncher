package com.wonderful.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

/** 起動可能なアプリ 1 件 */
data class AppInfo(
    /** 一意キー: "package/class" (+ 仕事用プロファイルなら "@serial") */
    val key: String,
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val user: UserHandle,
    val installTime: Long,
    val isWorkProfile: Boolean,
)

/**
 * LauncherApps を使ってアプリ一覧とアイコンを管理する。
 * インストール/アンインストールは自動で検知して一覧を更新する。
 */
class AppRepository(private val context: Context, private val scope: CoroutineScope) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _icons = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    val icons: StateFlow<Map<String, ImageBitmap>> = _icons.asStateFlow()

    private val activityInfos = ConcurrentHashMap<String, LauncherActivityInfo>()
    private val rawIcons = ConcurrentHashMap<String, Drawable>()
    private var currentShape: IconShape = IconShape.CIRCLE
    /** 今 _icons に入っているアイコンの形状 */
    @Volatile private var renderedShape: IconShape? = null
    private var iconJob: Job? = null

    private val iconPx = (context.resources.displayMetrics.density * 72).toInt().coerceIn(96, 192)

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh()
        override fun onPackageAdded(packageName: String, user: UserHandle) = invalidate(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = invalidate(packageName)
        override fun onPackagesAvailable(p: Array<out String>, user: UserHandle, r: Boolean) = refresh()
        override fun onPackagesUnavailable(p: Array<out String>, user: UserHandle, r: Boolean) = refresh()
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun dispose() {
        launcherApps.unregisterCallback(callback)
    }

    /** アップデートでアイコンが変わった可能性があるので作り直す */
    private fun invalidate(pkg: String) {
        rawIcons.keys.removeAll { it.startsWith("$pkg/") }
        _icons.value = _icons.value.filterKeys { !it.startsWith("$pkg/") }
        refresh()
    }

    fun refresh() {
        scope.launch(Dispatchers.IO) {
            val myUser = Process.myUserHandle()
            val collator = Collator.getInstance(Locale.getDefault())
            val list = mutableListOf<AppInfo>()
            val seen = HashSet<String>()
            for (user in userManager.userProfiles) {
                val activities = runCatching { launcherApps.getActivityList(null, user) }
                    .getOrDefault(emptyList())
                for (info in activities) {
                    if (info.componentName.packageName == context.packageName) continue
                    val work = user != myUser
                    val key = info.componentName.flattenToShortString() +
                        if (work) "@" + userManager.getSerialNumberForUser(user) else ""
                    if (!seen.add(key)) continue
                    activityInfos[key] = info
                    list += AppInfo(
                        key = key,
                        label = info.label?.toString()?.trim().orEmpty().ifEmpty { info.componentName.packageName },
                        packageName = info.componentName.packageName,
                        component = info.componentName,
                        user = user,
                        installTime = info.firstInstallTime,
                        isWorkProfile = work,
                    )
                }
            }
            list.sortWith { a, b -> collator.compare(a.label, b.label) }
            activityInfos.keys.retainAll(seen)
            rawIcons.keys.retainAll(seen)
            _apps.value = list
            renderIcons(currentShape, onlyMissing = true)
        }
    }

    /** アイコン形状を変える（全アイコンを描き直す） */
    fun setShape(shape: IconShape) {
        if (shape == currentShape && _icons.value.isNotEmpty()) return
        currentShape = shape
        renderIcons(shape, onlyMissing = false)
    }

    private fun renderIcons(shape: IconShape, onlyMissing: Boolean) {
        iconJob?.cancel()
        iconJob = scope.launch(Dispatchers.Default) {
            // 形状が変わった（または途中で中断された）なら全部描き直す
            val full = !onlyMissing || renderedShape != shape
            val existing = _icons.value
            val targets = if (full) _apps.value else _apps.value.filter { it.key !in existing }
            val density = context.resources.displayMetrics.densityDpi
            // 古いアイコンを残したまま少しずつ差し替える（ちらつき防止）
            val alive = _apps.value.mapTo(HashSet()) { it.key }
            val result = HashMap(existing.filterKeys { it in alive })
            targets.chunked(16).forEach { chunk ->
                val rendered = chunk.map { app ->
                    async {
                        val drawable = rawIcons.getOrPut(app.key) {
                            val info = activityInfos[app.key] ?: return@async null
                            runCatching { info.getBadgedIcon(density) }.getOrNull()
                                ?: return@async null
                        }
                        app.key to renderShaped(drawable, shape, iconPx).asImageBitmap()
                    }
                }.awaitAll().filterNotNull()
                result.putAll(rendered)
                _icons.value = HashMap(result)
            }
            _icons.value = result
            renderedShape = shape
        }
    }

    fun iconFor(key: String): ImageBitmap? = _icons.value[key]

    fun findApp(key: String): AppInfo? = _apps.value.firstOrNull { it.key == key }

    fun findByPackage(pkg: String): AppInfo? = _apps.value.firstOrNull { it.packageName == pkg && !it.isWorkProfile }

    companion object {

        /** アイコンを指定形状のビットマップにする */
        fun renderShaped(d: Drawable, shape: IconShape, size: Int): Bitmap {
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            if (shape == IconShape.ORIGINAL) {
                d.setBounds(0, 0, size, size)
                d.draw(canvas)
                return bmp
            }
            val path = shapePath(shape, size.toFloat())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            // マスクを描いてから SRC_IN で中身を合成する（アンチエイリアスの効いた切り抜き）
            canvas.drawPath(path, paint)
            val layer = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val lc = Canvas(layer)
            if (d is AdaptiveIconDrawable) {
                // アダプティブアイコンは前景/背景をはみ出し気味に描いて全面を使う
                val inset = (size * 0.25f).toInt()
                d.background?.let { it.setBounds(-inset, -inset, size + inset, size + inset); it.draw(lc) }
                d.foreground?.let { it.setBounds(-inset, -inset, size + inset, size + inset); it.draw(lc) }
            } else {
                // 旧形式のアイコンは暗い背景の上に少し小さめに置く
                lc.drawColor(0xFF221133.toInt())
                val pad = (size * 0.14f).toInt()
                d.setBounds(pad, pad, size - pad, size - pad)
                d.draw(lc)
            }
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(layer, 0f, 0f, paint)
            layer.recycle()
            return bmp
        }

        fun shapePath(shape: IconShape, size: Float): Path {
            val p = Path()
            when (shape) {
                IconShape.CIRCLE, IconShape.ORIGINAL -> p.addCircle(size / 2, size / 2, size / 2, Path.Direction.CW)
                IconShape.ROUNDED -> p.addRoundRect(RectF(0f, 0f, size, size), size * 0.22f, size * 0.22f, Path.Direction.CW)
                IconShape.SQUIRCLE -> {
                    // 超楕円 |x|^4 + |y|^4 = 1
                    val r = size / 2
                    val steps = 72
                    for (i in 0..steps) {
                        val t = (i.toDouble() / steps) * 2 * Math.PI
                        val c = cos(t); val s = sin(t)
                        val x = r + r * Math.signum(c) * Math.pow(Math.abs(c), 0.5)
                        val y = r + r * Math.signum(s) * Math.pow(Math.abs(s), 0.5)
                        if (i == 0) p.moveTo(x.toFloat(), y.toFloat()) else p.lineTo(x.toFloat(), y.toFloat())
                    }
                    p.close()
                }
                IconShape.HEXAGON -> {
                    val r = size / 2
                    for (i in 0 until 6) {
                        val a = Math.toRadians(60.0 * i - 90)
                        val x = r + r * cos(a).toFloat()
                        val y = r + r * sin(a).toFloat()
                        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                    }
                    p.close()
                }
            }
            return p
        }
    }
}
