// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (c) 2026 72no6495

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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.wonderful.launcher.data.ChargeStyle
import com.wonderful.launcher.data.LauncherSettings
import com.wonderful.launcher.ui.theme.RetroPalette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 充電の開始/終了イベント。
 * 電流・電圧・温度は取れた時だけ入る（端末によっては取れない）。
 */
data class ChargeEvent(
    val plugged: Boolean,
    val level: Int,
    val currentMa: Int? = null,
    val voltage: Float? = null,
    val tempC: Float? = null,
    val id: Long = System.nanoTime(),
)

fun batteryLevel(context: Context): Int =
    context.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        ?.takeIf { it in 0..100 } ?: 0

/** 今の電池の状態でイベントを作る */
fun chargeEvent(context: Context, plugged: Boolean): ChargeEvent {
    val bm = context.getSystemService(BatteryManager::class.java)
    // µA で返る（符号は端末によって違う）。0 や異常値は「取れなかった」扱い
    val currentMa = runCatching { bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }.getOrNull()
        ?.let { abs(it) / 1000 }?.takeIf { it in 1..20000 }
    val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val voltage = sticky?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 }?.let { it / 1000f }
    val temp = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.let { it / 10f }
    return ChargeEvent(plugged, batteryLevel(context), currentMa, voltage, temp)
}

/** 電源の抜き差しを監視する（ホーム画面が表示されている間） */
@Composable
fun ChargeListener(enabled: Boolean, onEvent: (ChargeEvent) -> Unit) {
    if (!enabled) return
    val context = LocalContext.current
    val callback by rememberUpdatedState(onEvent)
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                callback(chargeEvent(c, plugged = i.action == Intent.ACTION_POWER_CONNECTED))
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

// ── イージング ──
private fun seg(x: Float, a: Float, b: Float) = ((x - a) / (b - a)).coerceIn(0f, 1f)
private fun easeOutCubic(x: Float) = 1f - (1f - x) * (1f - x) * (1f - x)
private fun easeOutBack(x: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val y = x - 1f
    return 1f + c3 * y * y * y + c1 * y * y
}

/**
 * 充電アニメーション。タップで閉じる。
 *
 * 流れ: 衝撃波とフラッシュ → 本体が組み上がる → 残量までカウントアップ → 余韻 → フェードアウト
 */
@Composable
fun ChargingOverlay(event: ChargeEvent?, s: LauncherSettings, p: RetroPalette, onDone: () -> Unit) {
    val view = LocalView.current
    val last = remember { arrayOfNulls<ChargeEvent>(1) }
    if (event != null) last[0] = event
    AnimatedVisibility(
        visible = event != null,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(450)),
    ) {
        val ev = last[0] ?: return@AnimatedVisibility
        val progress = remember(ev.id) { Animatable(0f) }
        val time = rememberSceneTime(1f, paused = false)
        LaunchedEffect(ev.id) {
            if (s.chargeHaptic) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            progress.animateTo(1f, tween((s.chargeDurationSec * 1000).toInt(), easing = LinearEasing))
            onDone()
        }
        val textPaint = remember {
            Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textAlign = Paint.Align.CENTER }
        }
        val dust = remember(ev.id) { val r = Random(ev.id); List(46) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) } }
        Canvas(
            Modifier
                .fillMaxSize()
                .clickable(remember { MutableInteractionSource() }, null, onClick = onDone),
        ) {
            val t = time.value
            val prog = progress.value
            // 数字は 15%〜65% の間でカウントアップ
            val countF = ev.level * easeOutCubic(seg(prog, 0.15f, 0.65f))
            val count = countF.toInt()
            val fade = 1f - seg(prog, 0.86f, 1f)
            val accent = levelColor(ev, count, p)
            drawBackdrop(t, prog, p, accent, fade, dust)
            when (s.chargeStyle) {
                ChargeStyle.BATTERY -> drawBattery(ev, countF, t, prog, p, accent, fade)
                ChargeStyle.RING -> drawRing(ev, countF, t, prog, p, accent, fade)
                ChargeStyle.LIGHTNING -> drawLightning(ev, t, prog, p, accent, fade)
                ChargeStyle.WAVE -> drawWave(countF, t, prog, p, accent, fade, dust)
            }
            drawLabels(ev, count, t, prog, p, accent, fade, textPaint, s.chargeStyle)
        }
    }
}

/** 残量で色を変える（少ない=ピンク / 半分=黄 / 多い=テーマのミント）。外した時はピンク */
private fun levelColor(ev: ChargeEvent, count: Int, p: RetroPalette): Color = when {
    !ev.plugged -> p.pink
    count < 20 -> p.pink
    count < 50 -> p.yellow
    else -> p.mint
}

// ─────────────────────────────────────────────
// 共通の背景：暗幕・中央の光・流れる床グリッド・浮遊する粒・衝撃波
// ─────────────────────────────────────────────

private fun DrawScope.drawBackdrop(
    t: Float, prog: Float, p: RetroPalette, accent: Color, fade: Float, dust: List<Triple<Float, Float, Float>>,
) {
    val w = size.width
    val h = size.height
    drawRect(Color.Black.copy(alpha = 0.84f * fade))
    val c = Offset(w / 2, h * 0.4f)
    drawCircle(
        Brush.radialGradient(listOf(accent.copy(alpha = 0.28f * fade), p.purple.copy(alpha = 0.12f * fade), Color.Transparent), c, size.maxDimension * 0.55f),
        size.maxDimension * 0.55f, c,
    )
    // 下の方にパースの効いたグリッド（奥へ流れる）
    val horizon = h * 0.78f
    val gridA = 0.35f * fade * seg(prog, 0.05f, 0.3f)
    if (gridA > 0f) {
        for (i in -10..10) {
            drawLine(p.purple.copy(alpha = gridA), Offset(w / 2, horizon), Offset(w / 2 + i * w * 0.18f, h), 1.5f)
        }
        for (k in 0 until 8) {
            val z = (k + (t * 0.8f) % 1f) / 8f
            val y = horizon + (h - horizon) * z * z
            drawLine(p.purple.copy(alpha = gridA * z), Offset(0f, y), Offset(w, y), 1.5f)
        }
    }
    // 浮遊する粒
    dust.forEach { (x, y, k) ->
        val yy = ((y - t * (0.02f + k * 0.05f)) % 1f + 1f) % 1f
        val a = (0.15f + 0.5f * k) * fade * (0.5f + 0.5f * sin(t * 3f + k * 20f))
        drawCircle(if (k > 0.6f) accent else Color.White, 1f + k * 2.5f, Offset(x * w, yy * h), alpha = a)
    }
    // つないだ瞬間のフラッシュと衝撃波
    val flash = 1f - seg(prog, 0f, 0.07f)
    if (flash > 0f) drawRect(Color.White.copy(alpha = 0.22f * flash), blendMode = BlendMode.Plus)
    for (k in 0 until 3) {
        val e = easeOutCubic(seg(prog, 0.02f + k * 0.06f, 0.4f + k * 0.06f))
        if (e <= 0f || e >= 1f) continue
        val r = size.maxDimension * 0.65f * e
        drawCircle(accent.copy(alpha = (1f - e) * 0.55f), r, c, style = Stroke(10f * (1f - e) + 2f))
    }
}

private fun boltPath(cx: Float, cy: Float, h: Float): Path = Path().apply {
    val w = h * 0.55f
    moveTo(cx + w * 0.15f, cy - h / 2)
    lineTo(cx - w * 0.45f, cy + h * 0.06f)
    lineTo(cx - w * 0.02f, cy + h * 0.06f)
    lineTo(cx - w * 0.18f, cy + h / 2)
    lineTo(cx + w * 0.45f, cy - h * 0.1f)
    lineTo(cx + w * 0.02f, cy - h * 0.1f)
    close()
}

/** ネオンの稲妻マーク（光の層を重ねる） */
private fun DrawScope.drawBolt(cx: Float, cy: Float, h: Float, p: RetroPalette, accent: Color, alpha: Float) {
    if (alpha <= 0f) return
    drawPath(boltPath(cx, cy, h * 1.18f), accent.copy(alpha = 0.22f * alpha))
    drawPath(boltPath(cx, cy, h), Brush.verticalGradient(listOf(Color.White, p.yellow), cy - h / 2, cy + h / 2), alpha = alpha)
    drawPath(boltPath(cx, cy, h), Color.White.copy(alpha = 0.9f * alpha), style = Stroke(2.5f))
}

/** ギザギザの電撃（中点変位法） */
private fun jagged(from: Offset, to: Offset, rnd: Random, rough: Float, depth: Int): List<Offset> {
    if (depth == 0) return listOf(from, to)
    val mid = Offset((from.x + to.x) / 2, (from.y + to.y) / 2)
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = kotlin.math.sqrt(dx * dx + dy * dy)
    val off = (rnd.nextFloat() - 0.5f) * len * rough
    val m = Offset(mid.x - dy / len * off, mid.y + dx / len * off)
    return jagged(from, m, rnd, rough, depth - 1).dropLast(1) + jagged(m, to, rnd, rough, depth - 1)
}

private fun DrawScope.drawZap(points: List<Offset>, color: Color, alpha: Float, width: Float) {
    if (points.size < 2) return
    val path = Path().apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) } }
    drawPath(path, color.copy(alpha = 0.25f * alpha), style = Stroke(width * 5f, cap = StrokeCap.Round))
    drawPath(path, color.copy(alpha = 0.7f * alpha), style = Stroke(width * 2f, cap = StrokeCap.Round))
    drawPath(path, Color.White.copy(alpha = alpha), style = Stroke(width, cap = StrokeCap.Round))
}

// ─────────────────────────────────────────────
// ネオン電池
// ─────────────────────────────────────────────

private fun DrawScope.drawBattery(ev: ChargeEvent, countF: Float, t: Float, prog: Float, p: RetroPalette, accent: Color, fade: Float) {
    val w = size.width
    val h = size.height
    val bw = w * 0.34f
    val bh = min(bw * 1.75f, h * 0.38f)
    val cx = w / 2
    val cy = h * 0.38f
    val left = cx - bw / 2
    val top = cy - bh / 2
    val bottom = top + bh
    val radius = bw * 0.14f
    val stroke = w * 0.009f

    // 1) ケーブルとエネルギーの流れ（下から本体へ）
    if (ev.plugged) {
        val cableA = fade * seg(prog, 0.05f, 0.2f)
        val cable = Path().apply {
            moveTo(cx, h * 1.02f)
            cubicTo(cx - w * 0.12f, h * 0.86f, cx + w * 0.12f, bottom + h * 0.12f, cx, bottom + stroke * 6f)
        }
        drawPath(cable, Color(0xFF07020F).copy(alpha = cableA), style = Stroke(stroke * 5f, cap = StrokeCap.Round))
        drawPath(cable, accent.copy(alpha = 0.3f * cableA), style = Stroke(stroke * 3f, cap = StrokeCap.Round))
        drawPath(
            cable, Color.White.copy(alpha = 0.8f * cableA),
            style = Stroke(stroke * 0.9f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 26f), -t * 160f)),
        )
        // ケーブルに沿って上る光の粒
        val pm = PathMeasure().apply { setPath(cable, false) }
        for (k in 0 until 14) {
            val f = ((t * 0.55f + k / 14f) % 1f)
            val pos = pm.getPosition(pm.length * f)
            drawCircle(accent.copy(alpha = 0.35f * cableA), stroke * 2.2f, pos)
            drawCircle(Color.White.copy(alpha = cableA), stroke * 0.8f, pos)
        }
        // 端子の上の「プラグ」
        drawRoundRect(accent.copy(alpha = cableA), Offset(cx - bw * 0.12f, bottom + stroke * 2f), Size(bw * 0.24f, stroke * 5f), CornerRadius(stroke))
    }

    // 2) 本体の輪郭が描かれていく
    val outline = Path().apply { addRoundRect(RoundRect(left, top, left + bw, bottom, CornerRadius(radius))) }
    val drawIn = easeOutCubic(seg(prog, 0.03f, 0.24f))
    val shown = Path()
    PathMeasure().apply {
        setPath(outline, false)
        getSegment(0f, length * drawIn, shown, true)
    }
    drawPath(Path().apply { addRoundRect(RoundRect(left, top, left + bw, bottom, CornerRadius(radius))) },
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.07f * fade * drawIn), Color.White.copy(alpha = 0.02f * fade * drawIn)), top, bottom))
    drawPath(shown, accent.copy(alpha = 0.22f * fade), style = Stroke(stroke * 5f))
    drawPath(shown, Brush.verticalGradient(listOf(p.cyan, accent, p.pink), top, bottom), alpha = fade, style = Stroke(stroke))
    // 端子
    val capA = fade * seg(prog, 0.18f, 0.26f)
    drawRoundRect(accent.copy(alpha = capA), Offset(cx - bw * 0.17f, top - bh * 0.065f), Size(bw * 0.34f, bh * 0.05f), CornerRadius(stroke * 1.5f))

    // 3) セル（下から 1 マスずつポンと点灯）
    val cells = 10
    val inset = stroke * 3f
    val innerL = left + inset
    val innerW = bw - inset * 2
    val innerTop = top + inset
    val innerH = bh - inset * 2
    val gap = innerH * 0.025f
    val cellH = (innerH - gap * (cells - 1)) / cells
    val filled = countF / 10f
    for (i in 0 until cells) {
        val local = (filled - i).coerceIn(0f, 1f)
        if (local <= 0f) {
            // まだのマスはうっすら
            val y = innerTop + innerH - (i + 1) * cellH - i * gap
            drawRoundRect(Color.White.copy(alpha = 0.04f * fade * drawIn), Offset(innerL, y), Size(innerW, cellH), CornerRadius(stroke))
            continue
        }
        val pop = easeOutBack(local)
        val y = innerTop + innerH - (i + 1) * cellH - i * gap
        val color = lerp(p.pink, if (i < 5) p.yellow else accent, (i / (cells - 1f)).coerceIn(0f, 1f))
        val top1 = i == floor(filled).toInt().coerceAtMost(cells - 1)
        val pulse = if (top1) 0.65f + 0.35f * sin(t * 7f) else 1f
        scale(pop, pop, Offset(innerL + innerW / 2, y + cellH / 2)) {
            drawRoundRect(color.copy(alpha = 0.3f * fade * pulse), Offset(innerL - stroke, y - stroke), Size(innerW + stroke * 2, cellH + stroke * 2), CornerRadius(stroke * 2))
            drawRoundRect(
                Brush.horizontalGradient(listOf(color, lerp(color, Color.White, 0.35f), color), innerL, innerL + innerW),
                Offset(innerL, y), Size(innerW, cellH), CornerRadius(stroke), alpha = fade * pulse,
            )
        }
    }

    // 4) スキャンする光の帯とガラスの反射
    clipRect(left, top, left + bw, bottom) {
        val sy = bottom - ((t * 0.55f) % 1f) * (bh + bh * 0.3f)
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.22f * fade), Color.Transparent), sy - bh * 0.08f, sy + bh * 0.08f),
            Offset(left, sy - bh * 0.08f), Size(bw, bh * 0.16f),
        )
        drawRect(
            Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.14f * fade), Color.Transparent), left + bw * 0.08f, left + bw * 0.3f),
            Offset(left, top), Size(bw, bh),
        )
    }

    // 5) 周りに走る電撃（充電中だけ）
    if (ev.plugged && prog > 0.2f) {
        val rnd = Random(floor(t * 9f).toInt())
        repeat(2) {
            if (rnd.nextFloat() < 0.55f) {
                val side = rnd.nextInt(4)
                val along = rnd.nextFloat()
                val from = when (side) {
                    0 -> Offset(left, top + bh * along)
                    1 -> Offset(left + bw, top + bh * along)
                    2 -> Offset(left + bw * along, top)
                    else -> Offset(left + bw * along, bottom)
                }
                val outDir = when (side) { 0 -> Offset(-1f, 0f); 1 -> Offset(1f, 0f); 2 -> Offset(0f, -1f); else -> Offset(0f, 1f) }
                val len = bw * (0.25f + rnd.nextFloat() * 0.35f)
                val to = Offset(from.x + outDir.x * len + (rnd.nextFloat() - 0.5f) * len * 0.6f, from.y + outDir.y * len + (rnd.nextFloat() - 0.5f) * len * 0.6f)
                drawZap(jagged(from, to, rnd, 0.6f, 4), accent, fade * (0.6f + 0.4f * rnd.nextFloat()), stroke * 0.6f)
            }
        }
    }

    // 6) 稲妻マーク
    val boltIn = easeOutBack(seg(prog, 0.22f, 0.38f))
    val breathe = 1f + 0.05f * sin(t * 4f)
    drawBolt(cx, cy, bh * 0.42f * boltIn * breathe, p, accent, fade * seg(prog, 0.22f, 0.3f) * if (ev.plugged) 1f else 0.5f)
}

// ─────────────────────────────────────────────
// リング
// ─────────────────────────────────────────────

private fun DrawScope.drawRing(ev: ChargeEvent, countF: Float, t: Float, prog: Float, p: RetroPalette, accent: Color, fade: Float) {
    val c = Offset(size.width / 2, size.height * 0.4f)
    val r = size.minDimension * 0.32f
    val sw = r * 0.09f
    val appear = easeOutBack(seg(prog, 0.03f, 0.25f))
    scale(appear, appear, c) {
        // 外側の目盛り（残量ぶん点灯）
        val ticks = 60
        val lit = (countF / 100f * ticks).toInt()
        for (i in 0 until ticks) {
            val a = -PI.toFloat() / 2 + i * 2 * PI.toFloat() / ticks
            val on = i < lit
            val r1 = r * 1.22f
            val r2 = r * (if (i % 5 == 0) 1.34f else 1.29f)
            drawLine(
                (if (on) accent else Color.White).copy(alpha = (if (on) 0.95f else 0.12f) * fade),
                Offset(c.x + cos(a) * r1, c.y + sin(a) * r1), Offset(c.x + cos(a) * r2, c.y + sin(a) * r2),
                if (i % 5 == 0) 4f else 2.5f, StrokeCap.Round,
            )
        }
        // 下地と進捗の弧
        drawCircle(Color.White.copy(alpha = 0.06f * fade), r, c, style = Stroke(sw))
        val sweep = 360f * countF / 100f
        drawArc(
            accent.copy(alpha = 0.3f * fade), -90f, sweep, false,
            Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(sw * 2.4f, cap = StrokeCap.Round),
        )
        drawArc(
            Brush.sweepGradient(listOf(p.pink, p.cyan, accent, p.pink), c), -90f, sweep, false,
            Offset(c.x - r, c.y - r), Size(r * 2, r * 2), alpha = fade, style = Stroke(sw, cap = StrokeCap.Round),
        )
        // 弧の先端の光
        val endA = Math.toRadians((sweep - 90f).toDouble()).toFloat()
        val head = Offset(c.x + cos(endA) * r, c.y + sin(endA) * r)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = fade), accent.copy(alpha = 0.6f * fade), Color.Transparent), head, sw * 2.2f), sw * 2.2f, head)
        // 内側で逆回りする点線の輪
        rotate(-t * 40f, c) {
            drawCircle(p.purple.copy(alpha = 0.6f * fade), r * 0.78f, c, style = Stroke(2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 16f))))
        }
        rotate(t * 25f, c) {
            drawCircle(p.cyan.copy(alpha = 0.35f * fade), r * 0.7f, c, style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 22f))))
        }
        // 尾を引いて回る彗星
        for (k in 0 until 3) {
            for (j in 0 until 9) {
                val a = t * (1.6f + k * 0.4f) + k * 2.1f - j * 0.07f
                val rr = r * (1.08f + 0.04f * k)
                val f = 1f - j / 9f
                drawCircle((if (k == 1) p.pink else p.cyan).copy(alpha = 0.8f * f * fade), sw * 0.32f * f + 1f, Offset(c.x + cos(a) * rr, c.y + sin(a) * rr))
            }
        }
        drawBolt(c.x, c.y - r * 0.42f, r * 0.26f, p, accent, fade * seg(prog, 0.2f, 0.32f))
    }
}

// ─────────────────────────────────────────────
// 稲妻
// ─────────────────────────────────────────────

private fun DrawScope.drawLightning(ev: ChargeEvent, t: Float, prog: Float, p: RetroPalette, accent: Color, fade: Float) {
    val w = size.width
    val h = size.height
    val c = Offset(w / 2, h * 0.4f)
    // 最初の 45% は枝分かれした雷が何本も落ちる
    if (prog < 0.45f) {
        val frame = floor(t * 10f).toInt()
        val rnd = Random(frame)
        if (rnd.nextFloat() < 0.6f) {
            repeat(1 + rnd.nextInt(2)) {
                val start = Offset(c.x + (rnd.nextFloat() - 0.5f) * w * 0.9f, 0f)
                val main = jagged(start, c, rnd, 0.35f, 6)
                drawZap(main, accent, fade, 3f)
                // 枝
                repeat(2) {
                    val from = main[main.size / 3 + rnd.nextInt(main.size / 3)]
                    val to = Offset(from.x + (rnd.nextFloat() - 0.5f) * w * 0.4f, from.y + h * (0.05f + rnd.nextFloat() * 0.12f))
                    drawZap(jagged(from, to, rnd, 0.5f, 4), accent, fade * 0.7f, 2f)
                }
            }
            drawRect(Color.White.copy(alpha = 0.1f * fade), blendMode = BlendMode.Plus)
        }
    }
    // 回転する光線
    val rays = 24
    for (k in 0 until rays) {
        val a = k * (2 * PI.toFloat() / rays) + t * 0.35f
        val len = size.minDimension * (0.3f + 0.12f * sin(t * 4f + k * 1.7f))
        drawLine(
            Brush.linearGradient(listOf(accent.copy(alpha = 0.5f * fade), Color.Transparent), c, Offset(c.x + cos(a) * len, c.y + sin(a) * len)),
            c, Offset(c.x + cos(a) * len, c.y + sin(a) * len), 3f,
        )
    }
    // エネルギーの輪
    val ringE = easeOutCubic(seg(prog, 0.2f, 0.55f))
    drawCircle(accent.copy(alpha = 0.6f * fade), size.minDimension * 0.22f * ringE, c, style = Stroke(4f))
    drawCircle(p.cyan.copy(alpha = 0.3f * fade), size.minDimension * 0.25f * ringE, c, style = Stroke(2f))
    val pop = easeOutBack(seg(prog, 0.18f, 0.36f))
    drawBolt(c.x, c.y, size.minDimension * 0.34f * pop * (1f + 0.04f * sin(t * 6f)), p, accent, fade * seg(prog, 0.18f, 0.24f))
}

// ─────────────────────────────────────────────
// 液体
// ─────────────────────────────────────────────

private fun DrawScope.drawWave(countF: Float, t: Float, prog: Float, p: RetroPalette, accent: Color, fade: Float, dust: List<Triple<Float, Float, Float>>) {
    val w = size.width
    val h = size.height
    val surface = h - h * 0.92f * (countF / 100f) * easeOutCubic(seg(prog, 0.02f, 0.3f)).coerceAtLeast(countF / 100f)
    val layers = listOf(p.purple to 0.6f, p.cyan to 0.45f, accent to 0.55f)
    layers.forEachIndexed { li, (col, a) ->
        val path = Path()
        path.moveTo(0f, h)
        var x = 0f
        val amp = h * (0.018f - li * 0.004f)
        while (x <= w + 12f) {
            val y = surface + li * h * 0.012f + sin(x * (0.009f + li * 0.003f) + t * (1.8f + li * 0.7f) + li * 2f) * amp
            path.lineTo(x, y)
            x += 12f
        }
        path.lineTo(w, h)
        path.close()
        drawPath(path, Brush.verticalGradient(listOf(col.copy(alpha = a), p.skyTop.copy(alpha = 0.9f)), surface, h), alpha = fade)
    }
    // 泡立つ水面のライン
    val foam = Path()
    var x = 0f
    while (x <= w + 12f) {
        val y = surface + 2f * h * 0.012f + sin(x * 0.015f + t * 3.2f + 4f) * h * 0.01f
        if (x == 0f) foam.moveTo(x, y) else foam.lineTo(x, y)
        x += 12f
    }
    drawPath(foam, Color.White.copy(alpha = 0.6f * fade), style = Stroke(3f, cap = StrokeCap.Round))
    // 液体の中のゆらめき
    clipRect(0f, surface, w, h) {
        for (k in 0 until 7) {
            val yy = surface + (h - surface) * (k + 0.5f) / 7f
            val path = Path()
            var xx = 0f
            while (xx <= w + 20f) {
                val y = yy + sin(xx * 0.02f + t * 1.4f + k) * 10f
                if (xx == 0f) path.moveTo(xx, y) else path.lineTo(xx, y)
                xx += 20f
            }
            drawPath(path, Color.White.copy(alpha = 0.05f * fade), style = Stroke(2f))
        }
        // 泡
        dust.forEach { (sx, sp, ss) ->
            val y = h - ((t * (0.08f + ss * 0.15f) + sp) % 1f) * (h - surface)
            val r = 2f + ss * 7f
            drawCircle(Color.White.copy(alpha = 0.45f * fade), r, Offset(sx * w + sin(t * 2f + sp * 9f) * 8f, y), style = Stroke(1.5f))
        }
    }
}

// ─────────────────────────────────────────────
// 文字（残量・状態・電流などの実測値）
// ─────────────────────────────────────────────

private fun DrawScope.drawLabels(
    ev: ChargeEvent, count: Int, t: Float, prog: Float, p: RetroPalette, accent: Color, fade: Float, paint: Paint, style: ChargeStyle,
) {
    val w = size.width
    val h = size.height
    val textIn = seg(prog, 0.12f, 0.24f)
    if (textIn <= 0f) return
    val baseY = when (style) {
        ChargeStyle.RING -> h * 0.4f + w * 0.07f
        ChargeStyle.WAVE -> h * 0.36f
        ChargeStyle.BATTERY -> h * 0.38f + min(w * 0.34f * 1.75f, h * 0.38f) / 2 + w * 0.22f
        ChargeStyle.LIGHTNING -> h * 0.66f
    }
    val a = fade * textIn
    // 出始めは少しガタつく（グリッチ）
    val jitter = if (textIn < 1f) (Random(floor(t * 30f).toInt()).nextFloat() - 0.5f) * 20f * (1f - textIn) else 0f
    drawIntoCanvas { cv ->
        val big = if (style == ChargeStyle.RING) w * 0.17f else w * 0.2f
        paint.textSize = big
        paint.letterSpacing = 0.02f
        paint.clearShadowLayer()
        paint.color = p.pink.copy(alpha = 0.55f * a).toArgb()
        cv.nativeCanvas.drawText("$count%", w / 2 - 6f + jitter, baseY, paint)
        paint.color = p.cyan.copy(alpha = 0.55f * a).toArgb()
        cv.nativeCanvas.drawText("$count%", w / 2 + 6f - jitter, baseY, paint)
        paint.setShadowLayer(big * 0.18f, 0f, 0f, accent.copy(alpha = a).toArgb())
        paint.color = Color.White.copy(alpha = a).toArgb()
        cv.nativeCanvas.drawText("$count%", w / 2 + jitter, baseY, paint)
        paint.clearShadowLayer()

        paint.textSize = w * 0.05f
        paint.letterSpacing = 0.25f
        paint.color = accent.copy(alpha = a * (0.75f + 0.25f * sin(t * 5f))).toArgb()
        val status = when {
            !ev.plugged -> "DISCONNECTED"
            (ev.currentMa ?: 0) >= 2000 -> "⚡ FAST CHARGING ⚡"
            else -> "⚡ CHARGING ⚡"
        }
        cv.nativeCanvas.drawText(status, w / 2, baseY + w * 0.1f, paint)

        // 実測値（取れた物だけ）
        val stats = listOfNotNull(
            ev.currentMa?.let { "${it}mA" },
            ev.voltage?.let { "%.2fV".format(it) },
            ev.tempC?.let { "%.1f℃".format(it) },
        )
        if (stats.isNotEmpty()) {
            paint.textSize = w * 0.035f
            paint.letterSpacing = 0.1f
            paint.color = Color.White.copy(alpha = 0.7f * a * seg(prog, 0.3f, 0.4f)).toArgb()
            cv.nativeCanvas.drawText(stats.joinToString("  ·  "), w / 2, baseY + w * 0.165f, paint)
        }
        paint.letterSpacing = 0f
    }
}
