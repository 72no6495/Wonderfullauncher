package com.wonderful.launcher.ui.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.wonderful.launcher.data.ChargeStyle
import com.wonderful.launcher.data.LauncherSettings
import com.wonderful.launcher.ui.theme.RetroPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** 充電の開始/終了イベント */
data class ChargeEvent(val plugged: Boolean, val level: Int, val id: Long = System.nanoTime())

fun batteryLevel(context: Context): Int =
    context.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        ?.takeIf { it in 0..100 } ?: 0

/** 電源の抜き差しを監視する（ホーム画面が表示されている間） */
@Composable
fun ChargeListener(enabled: Boolean, onEvent: (ChargeEvent) -> Unit) {
    if (!enabled) return
    val context = LocalContext.current
    val callback by rememberUpdatedState(onEvent)
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val plugged = i.action == Intent.ACTION_POWER_CONNECTED
                callback(ChargeEvent(plugged, batteryLevel(c)))
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
}

/**
 * 充電アニメーション。タップで閉じる。
 */
@Composable
fun ChargingOverlay(event: ChargeEvent?, s: LauncherSettings, p: RetroPalette, onDone: () -> Unit) {
    val view = LocalView.current
    val last = remember { arrayOfNulls<ChargeEvent>(1) }
    if (event != null) last[0] = event
    AnimatedVisibility(
        visible = event != null,
        enter = fadeIn(tween(250)) + scaleIn(initialScale = 1.08f),
        exit = fadeOut(tween(400)),
    ) {
        val ev = last[0] ?: return@AnimatedVisibility
        val progress = remember(ev.id) { Animatable(0f) }
        val t by rememberSceneTime(1f, paused = false)
        LaunchedEffect(ev.id) {
            if (s.chargeHaptic) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            progress.animateTo(1f, tween((s.chargeDurationSec * 1000).toInt(), easing = LinearEasing))
            onDone()
        }
        val textPaint = remember {
            Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textAlign = Paint.Align.CENTER }
        }
        val sparks = remember(ev.id) { val r = Random(ev.id); List(40) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) } }
        Canvas(
            Modifier
                .fillMaxSize()
                .clickable(remember { MutableInteractionSource() }, null, onClick = onDone),
        ) {
            val prog = progress.value
            // 出だしで数字がカウントアップし、最後に少し暗くなって消える
            val count = (ev.level * min(1f, prog * 2.2f)).toInt()
            val fade = if (prog > 0.85f) 1f - (prog - 0.85f) / 0.15f else 1f
            drawRect(Color.Black.copy(alpha = 0.78f * fade))
            val accent = if (ev.plugged) p.mint else p.pink
            when (s.chargeStyle) {
                ChargeStyle.BATTERY -> drawBattery(ev, count, t, prog, p, accent, fade)
                ChargeStyle.RING -> drawRing(count, t, prog, p, accent, fade)
                ChargeStyle.LIGHTNING -> drawLightning(ev, t, prog, p, accent, fade)
                ChargeStyle.WAVE -> drawWave(count, t, p, accent, fade, sparks)
            }
            drawLabels(ev, count, p, accent, fade, textPaint, s.chargeStyle)
        }
    }
}

private fun DrawScope.boltPath(cx: Float, cy: Float, h: Float): Path = Path().apply {
    val w = h * 0.55f
    moveTo(cx + w * 0.15f, cy - h / 2)
    lineTo(cx - w * 0.45f, cy + h * 0.06f)
    lineTo(cx - w * 0.02f, cy + h * 0.06f)
    lineTo(cx - w * 0.18f, cy + h / 2)
    lineTo(cx + w * 0.45f, cy - h * 0.1f)
    lineTo(cx + w * 0.02f, cy - h * 0.1f)
    close()
}

private fun DrawScope.drawBattery(ev: ChargeEvent, count: Int, t: Float, prog: Float, p: RetroPalette, accent: Color, fade: Float) {
    val w = size.width
    val h = size.height
    val bw = w * 0.42f
    val bh = h * 0.32f
    val left = (w - bw) / 2
    val top = h * 0.26f
    val stroke = w * 0.012f
    // 外枠（ネオン）
    drawRoundRect(accent.copy(alpha = 0.25f * fade), Offset(left, top), Size(bw, bh), CornerRadius(bw * 0.12f), style = Stroke(stroke * 4))
    drawRoundRect(accent.copy(alpha = fade), Offset(left, top), Size(bw, bh), CornerRadius(bw * 0.12f), style = Stroke(stroke))
    drawRoundRect(accent.copy(alpha = fade), Offset(left + bw * 0.32f, top - bh * 0.06f), Size(bw * 0.36f, bh * 0.06f), CornerRadius(stroke))
    // 中身（波打つ液体）
    val inset = stroke * 2.2f
    val innerTop = top + inset
    val innerH = bh - inset * 2
    val level = count / 100f
    val surface = innerTop + innerH * (1f - level)
    clipRect(left + inset, innerTop, left + bw - inset, top + bh - inset) {
        val path = Path()
        path.moveTo(left, top + bh)
        var x = left
        while (x <= left + bw) {
            path.lineTo(x, surface + sin(x * 0.04f + t * 5f) * bh * 0.02f)
            x += 8f
        }
        path.lineTo(left + bw, top + bh)
        path.close()
        drawPath(path, Brush.verticalGradient(listOf(p.cyan, accent, p.purple), surface, top + bh), alpha = 0.9f * fade)
        // 泡
        for (k in 0 until 10) {
            val bx = left + bw * ((k * 0.137f + 0.05f) % 1f)
            val by = top + bh - ((t * 0.35f + k * 0.1f) % 1f) * (top + bh - surface)
            drawCircle(Color.White.copy(alpha = 0.5f * fade), bw * 0.015f + k % 3 * 2f, Offset(bx, by))
        }
    }
    // 稲妻マーク（ポンと出てくる）
    val pop = (prog * 4f).coerceIn(0f, 1f)
    val scale = 1f + 0.3f * sin(PI.toFloat() * pop)
    drawPath(boltPath(w / 2, top + bh / 2, bh * 0.55f * scale), Color.White.copy(alpha = 0.9f * fade * pop))
    if (ev.plugged) drawPath(boltPath(w / 2, top + bh / 2, bh * 0.75f * scale), p.yellow.copy(alpha = 0.25f * fade * pop))
}

private fun DrawScope.drawRing(count: Int, t: Float, prog: Float, p: RetroPalette, accent: Color, fade: Float) {
    val c = Offset(size.width / 2, size.height * 0.42f)
    val r = size.minDimension * 0.3f
    val sw = r * 0.12f
    drawCircle(accent.copy(alpha = 0.15f * fade), r, c, style = Stroke(sw))
    drawArc(
        Brush.sweepGradient(listOf(p.pink, p.cyan, p.mint, p.pink), c),
        startAngle = -90f, sweepAngle = 360f * count / 100f, useCenter = false,
        topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round), alpha = fade,
    )
    // 回る光の粒
    for (k in 0 until 10) {
        val a = t * 2.2f + k * (2 * PI / 10).toFloat()
        val rr = r * (1.25f + 0.08f * sin(t * 3f + k))
        drawCircle((if (k % 2 == 0) p.cyan else p.pink).copy(alpha = fade), sw * 0.18f, Offset(c.x + cos(a) * rr, c.y + sin(a) * rr))
    }
    val pulse = 0.5f + 0.5f * sin(t * 4f)
    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f * pulse * fade), Color.Transparent), c, r * 0.9f), r * 0.9f, c)
    drawPath(boltPath(c.x, c.y - r * 0.1f, r * 0.55f), Color.White.copy(alpha = 0.9f * fade * (prog * 4f).coerceAtMost(1f)))
}

private fun DrawScope.drawLightning(ev: ChargeEvent, t: Float, prog: Float, p: RetroPalette, accent: Color, fade: Float) {
    val w = size.width
    val h = size.height
    val c = Offset(w / 2, h * 0.42f)
    // 最初の 40% は稲妻が何本も落ちる
    if (prog < 0.4f) {
        val strike = Random((t * 12).toInt())
        if (strike.nextFloat() < 0.6f) {
            repeat(1 + strike.nextInt(3)) {
                val path = Path()
                var x = c.x + (strike.nextFloat() - 0.5f) * w * 0.8f
                var y = 0f
                path.moveTo(x, y)
                while (y < c.y) {
                    y += h * 0.04f
                    x += (strike.nextFloat() - 0.5f) * w * 0.12f
                    x += (c.x - x) * 0.12f
                    path.lineTo(x, y)
                }
                drawPath(path, accent.copy(alpha = 0.35f * fade), style = Stroke(14f, cap = StrokeCap.Round))
                drawPath(path, Color.White.copy(alpha = fade), style = Stroke(4f, cap = StrokeCap.Round))
            }
            drawRect(Color.White.copy(alpha = 0.12f * fade), blendMode = BlendMode.Plus)
        }
    }
    // 放射状の光
    for (k in 0 until 16) {
        val a = k * (2 * PI / 16).toFloat() + t * 0.4f
        val len = size.minDimension * (0.35f + 0.08f * sin(t * 5f + k))
        drawLine(accent.copy(alpha = 0.3f * fade), c, Offset(c.x + cos(a) * len, c.y + sin(a) * len), 3f)
    }
    val pop = ((prog - 0.15f) * 4f).coerceIn(0f, 1f)
    drawPath(boltPath(c.x, c.y, size.minDimension * 0.35f * (0.6f + 0.4f * pop)), p.yellow.copy(alpha = fade * pop))
    if (ev.plugged) drawPath(boltPath(c.x, c.y, size.minDimension * 0.45f), p.yellow.copy(alpha = 0.2f * fade * pop))
}

private fun DrawScope.drawWave(count: Int, t: Float, p: RetroPalette, accent: Color, fade: Float, sparks: List<Triple<Float, Float, Float>>) {
    val w = size.width
    val h = size.height
    val surface = h * (1f - count / 100f * 0.9f)
    for (layer in 0 until 2) {
        val path = Path()
        path.moveTo(0f, h)
        var x = 0f
        while (x <= w) {
            val y = surface + sin(x * 0.012f + t * (2.4f + layer) + layer * 2f) * h * 0.02f
            path.lineTo(x, y)
            x += 10f
        }
        path.lineTo(w, h)
        path.close()
        val col = if (layer == 0) accent else p.cyan
        drawPath(path, Brush.verticalGradient(listOf(col.copy(alpha = 0.55f), p.purple.copy(alpha = 0.25f)), surface, h), alpha = fade)
    }
    sparks.forEach { (sx, sp, ss) ->
        val y = h - ((t * (0.1f + ss * 0.2f) + sp) % 1f) * (h - surface)
        drawCircle(Color.White.copy(alpha = 0.45f * fade), 2f + ss * 6f, Offset(sx * w, y))
    }
}

private fun DrawScope.drawLabels(ev: ChargeEvent, count: Int, p: RetroPalette, accent: Color, fade: Float, paint: Paint, style: ChargeStyle) {
    val w = size.width
    val h = size.height
    val baseY = when (style) {
        ChargeStyle.BATTERY -> h * 0.72f
        ChargeStyle.WAVE -> h * 0.4f
        else -> h * 0.76f
    }
    drawIntoCanvas { cv ->
        paint.textSize = w * 0.2f
        // 色収差っぽく 3 回描く
        paint.color = p.pink.copy(alpha = 0.6f * fade).toArgb()
        cv.nativeCanvas.drawText("$count%", w / 2 - 6f, baseY, paint)
        paint.color = p.cyan.copy(alpha = 0.6f * fade).toArgb()
        cv.nativeCanvas.drawText("$count%", w / 2 + 6f, baseY, paint)
        paint.color = Color.White.copy(alpha = fade).toArgb()
        cv.nativeCanvas.drawText("$count%", w / 2, baseY, paint)
        paint.textSize = w * 0.055f
        paint.color = accent.copy(alpha = fade).toArgb()
        cv.nativeCanvas.drawText(if (ev.plugged) "⚡ ＣＨＡＲＧＩＮＧ ⚡" else "ＤＩＳＣＯＮＮＥＣＴＥＤ", w / 2, baseY + w * 0.11f, paint)
    }
}
