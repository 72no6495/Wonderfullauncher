package com.wonderful.launcher.ui.sphere

import android.app.ActivityOptions
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.wonderful.launcher.data.AppInfo
import com.wonderful.launcher.data.LabelMode
import com.wonderful.launcher.data.TapEffect
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import com.wonderful.launcher.data.LauncherSettings
import com.wonderful.launcher.ui.theme.RetroPalette
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.math.min

/**
 * アプリアイコンを球状に並べて描画し、ドラッグで回転・タップで起動する。
 */
@Composable
fun SphereView(
    apps: List<AppInfo>,
    icons: Map<String, ImageBitmap>,
    settings: LauncherSettings,
    palette: RetroPalette,
    paused: Boolean,
    spinRequests: Int,
    resetRequests: Int,
    badges: Map<String, Int>,
    onLaunch: (AppInfo, Rect, Bundle?) -> Unit,
    onLongPressApp: (AppInfo) -> Unit,
    onLongPressEmpty: () -> Unit,
    onDoubleTapEmpty: (() -> Unit)?,
    onSizeCommitted: (Float) -> Unit,
    onTwoFingerSwipe: (up: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val engine = remember { SphereEngine() }
    val fx = remember { SphereFx() }
    val frame = remember { mutableIntStateOf(0) }
    val view = LocalView.current

    val currentSettings by rememberUpdatedState(settings)
    val currentPaused by rememberUpdatedState(paused)
    val launchCb by rememberUpdatedState(onLaunch)
    val longPressAppCb by rememberUpdatedState(onLongPressApp)
    val longPressEmptyCb by rememberUpdatedState(onLongPressEmpty)
    val doubleTapCb by rememberUpdatedState(onDoubleTapEmpty)
    val sizeCommitCb by rememberUpdatedState(onSizeCommitted)
    val twoFingerCb by rememberUpdatedState(onTwoFingerSwipe)

    // アプリ構成やレイアウトが変わったら再配置
    val keys = remember(apps) { apps.map { it.key } }
    val appByKey = remember(apps) { apps.associateBy { it.key } }
    val currentAppByKey by rememberUpdatedState(appByKey)
    LaunchedEffect(keys, settings.sphereLayout) {
        engine.setItems(keys, settings.sphereLayout, animate = settings.entranceAnim)
        frame.intValue++
    }
    LaunchedEffect(spinRequests) {
        if (spinRequests > 0) engine.spin(5f)
    }
    LaunchedEffect(resetRequests) {
        if (resetRequests > 0) engine.reset()
    }
    // ピンチで決めたサイズが設定に反映されたら上書きを解除
    LaunchedEffect(settings.sphereSize) {
        val o = engine.sizeOverride
        if (o != null && kotlin.math.abs(o - settings.sphereSize) < 0.001f) engine.sizeOverride = null
    }

    // フレームループ
    LaunchedEffect(engine) {
        var last = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now ->
                val dt = (now - last) / 1_000_000_000f
                last = now
                if (currentPaused) return@withFrameNanos
                val moved = engine.step(dt, currentSettings, currentSettings.entranceAnim)
                fx.advance(dt)
                if (moved || fx.needsFrames(currentSettings)) frame.intValue++
            }
        }
    }

    // 描画用の使い回しオブジェクト
    val glow = remember { makeGlowBitmap() }
    val rainbowFilters = remember { List(36) { ColorFilter.tint(Color.hsv(it * 10f, 0.85f, 1f)) } }
    val glowFilters = remember(palette) {
        listOf(ColorFilter.tint(palette.pink), ColorFilter.tint(palette.cyan), ColorFilter.tint(palette.purple))
    }
    val labelPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER } }
    val initialPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD } }
    val labelCache = remember { HashMap<String, String>() }
    val badgePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; color = android.graphics.Color.WHITE
        }
    }
    val point = remember { FloatArray(3) }
    val point2 = remember { FloatArray(3) }
    val canvasPos = remember { FloatArray(2) }

    fun geometry(w: Float, h: Float): Triple<Float, Float, Float> {
        val s = engine.sizeOverride ?: currentSettings.sphereSize
        val radius = min(w, h) / 2f * 0.78f * s
        return Triple(w / 2f, h / 2f, radius)
    }

    fun launchAt(i: Int) {
        val app = engine.keys.getOrNull(i)?.let { currentAppByKey[it] } ?: return
        val sz = engine.size[i]
        val r = Rect(
            (engine.px[i] - sz / 2).toInt(), (engine.py[i] - sz / 2).toInt(),
            (engine.px[i] + sz / 2).toInt(), (engine.py[i] + sz / 2).toInt(),
        )
        // Canvas 内座標 → ComposeView 内座標 → 画面座標
        r.offset(canvasPos[0].toInt(), canvasPos[1].toInt())
        val inView = Rect(r)
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        r.offset(loc[0], loc[1])
        val opts = if (currentSettings.launchZoom) {
            ActivityOptions.makeClipRevealAnimation(view, inView.left, inView.top, inView.width(), inView.height()).toBundle()
        } else null
        launchCb(app, r, opts)
    }

    /**
     * アイコンを奥から順に描く。back = true なら奥側だけ、false なら手前側だけ、null なら全部。
     */
    fun DrawScope.drawIcons(back: Boolean?, iconPx: Float) {
        val s = currentSettings
        val front = engine.frontIndex()
        val ringStroke = Stroke(1.5.dp.toPx())
        labelPaint.textSize = s.labelSizeSp.sp.toPx()
        labelPaint.typeface = Typeface.MONOSPACE
        labelPaint.color = palette.text.toArgb()
        labelPaint.setShadowLayer(labelPaint.textSize * 0.6f, 0f, 0f, palette.pink.toArgb())
        val trails = fx.trailCount(s)
        if (fx.x.size != engine.count) return

        for (k in 0 until engine.count) {
            val i = engine.order[k]
            val t = engine.depth[i]
            if (back == true && t >= DOF_SPLIT) continue
            if (back == false && t < DOF_SPLIT) continue
            val app = appByKey[engine.keys[i]] ?: continue
            val x = fx.x[i]
            val y = fx.y[i] + fx.floatY[i]
            val sz = engine.size[i]
            val a = engine.alpha[i]
            val half = sz / 2f
            val img = icons[app.key]

            // 残像
            if (trails > 0 && img != null && t > 0.4f) {
                for (tk in trails downTo 1) {
                    val f = 1f - tk / (trails + 1f)
                    val ts = sz * (0.75f + 0.25f * f)
                    drawImage(
                        img, IntOffset.Zero, IntSize(img.width, img.height),
                        IntOffset((fx.trailX(i, tk) - ts / 2).toInt(), (fx.trailY(i, tk) - ts / 2).toInt()),
                        IntSize(ts.toInt(), ts.toInt()),
                        alpha = a * 0.35f * f,
                    )
                }
            }

            // グロー
            if (s.iconGlow && t > 0.45f) {
                val g = half * 1.9f
                val filter = if (s.rainbowGlow) {
                    rainbowFilters[(((fx.time * 90f + i * 23f) % 360f) / 10f).toInt().coerceIn(0, rainbowFilters.lastIndex)]
                } else glowFilters[i % glowFilters.size]
                drawImage(
                    glow,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(glow.width, glow.height),
                    dstOffset = IntOffset((x - g).toInt(), (y - g).toInt()),
                    dstSize = IntSize((g * 2).toInt(), (g * 2).toInt()),
                    alpha = (s.glowIntensity * (t - 0.45f) / 0.55f * a).coerceIn(0f, 1f),
                    colorFilter = filter,
                )
            }

            // タップ時の動き
            var sx = 1f
            var sy = 1f
            var rot = 0f
            if (i == engine.pulseIndex && engine.pulseTime < 1f) {
                val pt = engine.pulseTime
                when (s.tapEffect) {
                    TapEffect.JELLY -> {
                        val j = 0.45f * exp(-3.2f * pt) * sin(pt * 22f)
                        val pop = 1f + 0.18f * sin(PI.toFloat() * pt)
                        sx = (1f + j) * pop; sy = (1f - j * 0.8f) * pop
                    }
                    TapEffect.BOUNCE -> {
                        val amp = if (engine.pulseStrong) 0.8f else 0.3f
                        sx = 1f + amp * sin(PI.toFloat() * pt); sy = sx
                    }
                    TapEffect.SPIN -> {
                        rot = 360f * (1f - (1f - pt) * (1f - pt))
                        sx = 1f + 0.25f * sin(PI.toFloat() * pt); sy = sx
                    }
                    TapEffect.NONE -> Unit
                }
            }
            val stretch = fx.stretch[i]
            val center = Offset(x, y)
            withTransform({
                if (rot != 0f) rotate(rot, center)
                if (stretch > 1.002f) {
                    // 動く向きに伸びて、横に縮む（ゴム）
                    val ang = fx.stretchAngle[i]
                    rotate(ang, center)
                    scale(stretch, 1f / stretch, center)
                    rotate(-ang, center)
                }
                if (sx != 1f || sy != 1f) scale(sx, sy, center)
            }) {
                if (img != null) {
                    drawImage(
                        img,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(img.width, img.height),
                        dstOffset = IntOffset((x - half).toInt(), (y - half).toInt()),
                        dstSize = IntSize(sz.toInt(), sz.toInt()),
                        alpha = a,
                        filterQuality = FilterQuality.Low,
                    )
                } else {
                    // 読み込み中は頭文字の丸を表示
                    drawCircle(palette.purple.copy(alpha = a * 0.8f), half, center)
                    drawIntoCanvas { c ->
                        initialPaint.textSize = half
                        initialPaint.color = Color.White.copy(alpha = a).toArgb()
                        c.nativeCanvas.drawText(app.label.take(1), x, y + half * 0.35f, initialPaint)
                    }
                }
                if (s.iconRing && t > 0.3f) {
                    val ringColor = if (i == front && s.focusFrontIcon) palette.yellow else palette.cyan
                    drawCircle(ringColor.copy(alpha = a * (t - 0.3f) * 1.2f), half + ringStroke.width, center, style = ringStroke)
                }
            }

            // 通知バッジ
            val badge = if (s.showBadges && !app.isWorkProfile) badges[app.packageName] ?: 0 else 0
            if (badge > 0 && t > 0.4f) {
                val br = max(7.dp.toPx(), sz * (if (s.badgeShowCount) 0.2f else 0.12f))
                val bc = Offset(x + half * 0.72f, y - half * 0.72f)
                drawCircle(Color.White.copy(alpha = a), br + 1.5.dp.toPx(), bc)
                drawCircle(palette.pink.copy(alpha = a), br, bc)
                if (s.badgeShowCount) {
                    drawIntoCanvas { c ->
                        badgePaint.textSize = br * (if (badge > 9) 0.95f else 1.25f)
                        badgePaint.alpha = (a * 255).toInt()
                        c.nativeCanvas.drawText(
                            if (badge > 99) "99+" else badge.toString(),
                            bc.x, bc.y + badgePaint.textSize * 0.36f, badgePaint,
                        )
                    }
                }
            }

            // アプリ名
            val showLabel = when (s.labelMode) {
                LabelMode.NONE -> s.focusFrontIcon && i == front
                LabelMode.FRONT -> t > 0.78f || (s.focusFrontIcon && i == front)
                LabelMode.ALL -> true
            }
            if (showLabel) {
                val label = labelCache.getOrPut(app.key + "|" + labelPaint.textSize) { ellipsize(app.label, labelPaint, iconPx * 1.9f) }
                labelPaint.alpha = (a * 255).toInt()
                drawIntoCanvas { c ->
                    c.nativeCanvas.drawText(label, x, y + half * sy + labelPaint.textSize * 1.15f, labelPaint)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { val p = it.positionInRoot(); canvasPos[0] = p.x; canvasPos[1] = p.y }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    engine.touchDown()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var dragging = false
                    var pinching = false
                    var longPressed = false
                    var moved = Offset.Zero
                    var now = down.uptimeMillis
                    val slop = viewConfiguration.touchSlop
                    val longTimeout = viewConfiguration.longPressTimeoutMillis
                    var pinchStart = 0f
                    var pinchAccum = 1f
                    var overrideBefore: Float? = null
                    var twoFingerPan = Offset.Zero

                    while (true) {
                        val waitLong = !dragging && !pinching && !longPressed
                        val event = if (waitLong) {
                            val remain = longTimeout - (now - down.uptimeMillis)
                            withTimeoutOrNull(max(1L, remain)) { awaitPointerEvent() }
                        } else awaitPointerEvent()

                        if (event == null) {
                            // 長押し
                            longPressed = true
                            val hit = engine.hitTest(down.position.x, down.position.y)
                            if (currentSettings.haptics) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            if (hit >= 0) {
                                engine.pulse(hit, false)
                                engine.keys.getOrNull(hit)?.let { currentAppByKey[it] }?.let(longPressAppCb)
                            } else longPressEmptyCb()
                            continue
                        }
                        now = event.changes.first().uptimeMillis
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        if (pressed.size >= 2 && !longPressed) {
                            // 2 本指: ピンチ（拡大縮小）か、2 本指スワイプ
                            if (!pinching) {
                                pinching = true
                                overrideBefore = engine.sizeOverride
                                pinchStart = engine.sizeOverride ?: currentSettings.sphereSize
                                pinchAccum = 1f
                                twoFingerPan = Offset.Zero
                            }
                            pinchAccum *= event.calculateZoom()
                            twoFingerPan += event.calculatePan()
                            if (currentSettings.pinchToZoom) {
                                engine.sizeOverride = (pinchStart * pinchAccum).coerceIn(0.4f, 1.5f)
                                frame.intValue++
                            }
                            event.changes.forEach { it.consume() }
                            continue
                        }
                        val ch = pressed.first()
                        if (longPressed || pinching) { ch.consume(); continue }
                        val delta = ch.positionChange()
                        tracker.addPosition(ch.uptimeMillis, ch.position)
                        if (!dragging) {
                            moved += delta
                            if (moved.getDistance() > slop) dragging = true
                        }
                        if (dragging) {
                            val (_, _, radius) = geometry(size.width.toFloat(), size.height.toFloat())
                            val k = currentSettings.dragSensitivity / radius.coerceAtLeast(1f)
                            engine.drag(delta.x * k, delta.y * k)
                            frame.intValue++
                            ch.consume()
                        }
                    }
                    engine.touchUp()

                    when {
                        pinching -> {
                            val swipeDist = 90.dp.toPx()
                            val isSwipe = kotlin.math.abs(pinchAccum - 1f) < 0.15f &&
                                kotlin.math.abs(twoFingerPan.y) > swipeDist &&
                                kotlin.math.abs(twoFingerPan.y) > kotlin.math.abs(twoFingerPan.x) * 1.5f
                            if (isSwipe) {
                                // スワイプだったのでサイズ変更は取り消す
                                engine.sizeOverride = overrideBefore
                                frame.intValue++
                                if (currentSettings.haptics) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                twoFingerCb(twoFingerPan.y < 0)
                            } else if (currentSettings.pinchToZoom) {
                                engine.sizeOverride?.let(sizeCommitCb)
                            }
                        }
                        dragging -> {
                            val v = tracker.calculateVelocity()
                            val (_, _, radius) = geometry(size.width.toFloat(), size.height.toFloat())
                            val k = currentSettings.dragSensitivity / radius.coerceAtLeast(1f)
                            engine.fling(v.x * k, v.y * k)
                        }
                        !longPressed -> {
                            val hit = engine.hitTest(down.position.x, down.position.y)
                            if (hit >= 0) {
                                if (currentSettings.haptics) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                if (currentSettings.tapEffect != TapEffect.NONE) engine.pulse(hit, currentSettings.launchZoom)
                                if (currentSettings.tapParticles && hit < fx.x.size) {
                                    fx.burst(
                                        fx.x[hit], fx.y[hit],
                                        listOf(palette.pink, palette.cyan, palette.yellow, palette.mint),
                                        density,
                                    )
                                    frame.intValue++
                                }
                                launchAt(hit)
                            } else doubleTapCb?.let { cb ->
                                val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                                    awaitFirstDown(requireUnconsumed = false)
                                }
                                if (second != null && (second.position - down.position).getDistance() < slop * 4) {
                                    if (currentSettings.haptics) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    cb()
                                }
                            }
                        }
                    }
                }
            },
    ) {
        // 奥のアイコン（被写界深度ぼかし）
        val dof = currentSettings.dofBlur > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (dof) {
                        val r = currentSettings.dofBlur * 9.dp.toPx()
                        renderEffect = BlurEffect(r, r, TileMode.Decal)
                    }
                },
        ) {
            frame.intValue // 読み取ることでフレームごとに再描画
            val s = currentSettings
            val (cx, cy, radius) = geometry(size.width, size.height)
            val iconPx = s.iconSizeDp.dp.toPx()
            engine.project(cx, cy, radius, iconPx, s)
            fx.prepare(engine, s, iconPx)

            // ワイヤーフレーム
            if (s.wireframe && engine.count > 0) {
                val wireStroke = 1.dp.toPx()
                for ((wi, wire) in engine.wires.withIndex()) {
                    val color = if (wi % 2 == 0) palette.cyan else palette.pink
                    val n = wire.size / 3
                    engine.projectPoint(wire[0], wire[1], wire[2], cx, cy, radius, s, point)
                    for (k in 1 until n) {
                        engine.projectPoint(wire[k * 3], wire[k * 3 + 1], wire[k * 3 + 2], cx, cy, radius, s, point2)
                        val d = (point[2] + point2[2]) * 0.5f
                        val a = s.wireframeOpacity * (0.15f + 0.85f * d * d)
                        drawLine(color.copy(alpha = a), Offset(point[0], point[1]), Offset(point2[0], point2[1]), wireStroke)
                        point[0] = point2[0]; point[1] = point2[1]; point[2] = point2[2]
                    }
                }
            }
            if (dof) drawIcons(back = true, iconPx)
        }
        // 手前のアイコンと火花
        Canvas(Modifier.fillMaxSize()) {
            frame.intValue
            val s = currentSettings
            val iconPx = s.iconSizeDp.dp.toPx()
            drawIcons(back = if (dof) false else null, iconPx)
            if (fx.particlesAlive) {
                val pr = 3.dp.toPx()
                fx.forEachParticle { x, y, life, color ->
                    val a = life.coerceIn(0f, 1f)
                    drawCircle(color.copy(alpha = a), pr * (0.5f + a), Offset(x, y))
                    drawCircle(Color.White.copy(alpha = a * 0.8f), pr * 0.35f, Offset(x, y))
                }
            }
        }
    }
}

private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
    if (paint.measureText(text) <= maxWidth) return text
    val n = paint.breakText(text, true, maxWidth - paint.measureText("…"), null)
    return text.take(n.coerceAtLeast(1)) + "…"
}

/** 白の放射グラデーション（色はカラーフィルタで付ける） */
private fun makeGlowBitmap(): ImageBitmap {
    val size = 128
    val img = ImageBitmap(size, size)
    val canvas = ComposeCanvas(img)
    val paint = androidx.compose.ui.graphics.Paint()
    val brush = Brush.radialGradient(
        0f to Color.White.copy(alpha = 0.9f),
        0.45f to Color.White.copy(alpha = 0.35f),
        1f to Color.Transparent,
        center = Offset(size / 2f, size / 2f),
        radius = size / 2f,
    )
    brush.applyTo(androidx.compose.ui.geometry.Size(size.toFloat(), size.toFloat()), paint, 1f)
    canvas.drawCircle(Offset(size / 2f, size / 2f), size / 2f, paint)
    return img
}

/** この奥行きより奥のアイコンをぼかしレイヤーに描く */
private const val DOF_SPLIT = 0.5f
