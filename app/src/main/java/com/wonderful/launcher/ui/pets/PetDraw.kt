package com.wonderful.launcher.ui.pets

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.wonderful.launcher.data.FurnitureKind
import com.wonderful.launcher.data.PetTheme
import com.wonderful.launcher.ui.theme.RetroPalette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * 飼育モードの描画。テーマ（カジュアル / ヴェイパー / アクアリウム）ごとに見た目を変える。
 */

/** テーマごとの色 */
class PetColors(
    val leg: Color,
    val legGlow: Color?,
    val joint: Color,
    val shadow: Color,
    val nameBg: Color,
    val nameText: Color,
    val accent: Color,
    val accent2: Color,
)

fun petColors(theme: PetTheme, p: RetroPalette): PetColors = when (theme) {
    PetTheme.CASUAL -> PetColors(
        leg = Color(0xFF5D4037), legGlow = null, joint = Color(0xFF3E2723), shadow = Color(0x33000000),
        nameBg = Color(0xE6FFFFFF), nameText = Color(0xFF5D4037), accent = Color(0xFFFF8A80), accent2 = Color(0xFF4DB6AC),
    )
    PetTheme.VAPOR -> PetColors(
        leg = p.cyan, legGlow = p.pink, joint = Color.White, shadow = Color(0x55000000),
        nameBg = Color(0xCC1B0B33), nameText = p.cyan, accent = p.pink, accent2 = p.cyan,
    )
    PetTheme.AQUARIUM -> PetColors(
        leg = Color.White, legGlow = null, joint = Color.White, shadow = Color(0x33001A2A),
        nameBg = Color(0x99003A55), nameText = Color(0xFFE0F7FA), accent = Color(0xFFFF8A65), accent2 = Color(0xFF4DD0E1),
    )
}

/** 絵文字を画像にして使い回す（毎フレーム文字を描くより軽い） */
class EmojiCache {
    private val map = HashMap<String, ImageBitmap>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    fun get(emoji: String, px: Int): ImageBitmap {
        val size = px.coerceIn(8, 512)
        val key = "$emoji@$size"
        return map.getOrPut(key) {
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(bmp)
            paint.textSize = size * 0.82f
            val fm = paint.fontMetrics
            c.drawText(emoji, size / 2f, size / 2f - (fm.ascent + fm.descent) / 2f, paint)
            bmp.asImageBitmap()
        }
    }

    fun clear() = map.clear()
}

/** アイコンの平均色（魚のヒレの色に使う） */
fun iconTint(img: ImageBitmap?): Color {
    if (img == null) return Color(0xFFFFB74D)
    return runCatching {
        val b = img.asAndroidBitmap()
        var r = 0L; var g = 0L; var bl = 0L; var n = 0L
        val steps = 8
        for (i in 0 until steps) for (j in 0 until steps) {
            val px = b.getPixel((b.width * (i + 0.5f) / steps).toInt(), (b.height * (j + 0.5f) / steps).toInt())
            if ((px ushr 24) < 128) continue
            r += (px shr 16) and 0xFF; g += (px shr 8) and 0xFF; bl += px and 0xFF; n++
        }
        if (n == 0L) Color(0xFFFFB74D)
        else lerp(Color(r / n / 255f, g / n / 255f, bl / n / 255f), Color.White, 0.25f)
    }.getOrDefault(Color(0xFFFFB74D))
}

private fun DrawScope.image(img: ImageBitmap, cx: Float, cy: Float, w: Float, h: Float, alpha: Float = 1f) {
    if (w < 1f || h < 1f) return
    drawImage(
        img,
        dstOffset = IntOffset((cx - w / 2).roundToInt(), (cy - h / 2).roundToInt()),
        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
        alpha = alpha.coerceIn(0f, 1f),
    )
}

// ─────────────────────────────────────────────
// 背景
// ─────────────────────────────────────────────

fun DrawScope.drawPetBackground(theme: PetTheme, p: RetroPalette, sim: PetSim, t: Float, unit: Float) {
    val w = size.width
    val h = size.height
    when (theme) {
        PetTheme.CASUAL -> {
            // 木の床
            drawRect(Color(0xFFF6E3C6))
            val plank = unit * 34f
            var row = 0
            var y = 0f
            while (y < h) {
                drawLine(Color(0xFFE6C9A0), Offset(0f, y), Offset(w, y), unit * 1.2f)
                val seam = unit * 190f
                var x = ((row * 137) % 190) * unit - seam
                while (x < w) {
                    drawLine(Color(0xFFE6C9A0), Offset(x, y), Offset(x, y + plank), unit * 1.2f)
                    x += seam
                }
                // 木目
                drawLine(Color(0x14A0703C), Offset(0f, y + plank * 0.45f), Offset(w, y + plank * 0.55f), unit * 0.8f)
                y += plank
                row++
            }
            // 窓からの光
            rotate(-18f, Offset(w * 0.2f, h * 0.1f)) {
                for (i in 0 until 3) {
                    drawRect(
                        Color.White.copy(alpha = 0.16f),
                        topLeft = Offset(w * 0.05f + i * unit * 70f, -h * 0.2f),
                        size = Size(unit * 52f, h * 0.9f),
                    )
                }
            }
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x33A1887F)), Offset(w / 2, h / 2), max(w, h) * 0.75f))
        }
        PetTheme.VAPOR -> {
            drawRect(Brush.verticalGradient(listOf(p.skyTop, p.ground)))
            // 夕日の光
            val sun = Offset(w * 0.85f, h * 0.15f)
            drawCircle(Brush.radialGradient(listOf(p.sunBottom.copy(alpha = 0.35f), Color.Transparent), sun, h * 0.6f), h * 0.6f, sun)
            // 流れるネオングリッド
            val cell = unit * 56f
            val off = (t * unit * 8f) % cell
            var x = -cell + off
            while (x < w + cell) { drawLine(p.grid.copy(alpha = 0.22f), Offset(x, 0f), Offset(x, h), unit * 1.2f); x += cell }
            var y = -cell + off
            while (y < h + cell) { drawLine(p.cyan.copy(alpha = 0.16f), Offset(0f, y), Offset(w, y), unit * 1.2f); y += cell }
            // 走査線
            var sy = 0f
            while (sy < h) { drawLine(Color.Black.copy(alpha = 0.12f), Offset(0f, sy), Offset(w, sy), 1f); sy += unit * 4f }
        }
        PetTheme.AQUARIUM -> {
            val floor = sim.floorY
            drawRect(Brush.verticalGradient(listOf(Color(0xFF1386A8), Color(0xFF0A4F70), Color(0xFF032A40)), 0f, floor))
            // 水面から差し込む光
            for (i in 0 until 6) {
                val x0 = w * (0.08f + i * 0.17f) + sin(t * 0.3f + i) * w * 0.03f
                val path = Path().apply {
                    moveTo(x0, 0f)
                    lineTo(x0 + unit * 40f, 0f)
                    lineTo(x0 + unit * 40f + w * 0.12f, floor)
                    lineTo(x0 + w * 0.06f, floor)
                    close()
                }
                drawPath(path, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.1f + 0.04f * sin(t * 0.7f + i * 2f)), Color.Transparent), 0f, floor))
            }
            // 水面のゆらめき
            for (k in 0 until 3) {
                val path = Path()
                var x = 0f
                val yy = unit * (6f + k * 7f)
                path.moveTo(0f, yy)
                while (x <= w) {
                    path.lineTo(x, yy + sin(x / (unit * 30f) + t * (1.2f + k * 0.4f)) * unit * 3f)
                    x += unit * 12f
                }
                drawPath(path, Color.White.copy(alpha = 0.12f - k * 0.03f), style = Stroke(unit * 1.5f))
            }
            // 奥の水草のシルエット
            for (i in 0 until 9) {
                val bx = w * (i + 0.3f) / 9f
                val hh = h * (0.18f + (i * 37 % 10) / 50f)
                val path = Path()
                path.moveTo(bx, floor)
                val sway = sin(t * 0.8f + i) * unit * 14f
                path.quadraticTo(bx + sway, floor - hh * 0.5f, bx + sway * 1.6f, floor - hh)
                drawPath(path, Color(0xFF02344A).copy(alpha = 0.6f), style = Stroke(unit * 9f, cap = StrokeCap.Round))
            }
            // 砂
            val sand = Path().apply {
                moveTo(0f, floor)
                var x = 0f
                while (x <= w) { lineTo(x, floor - unit * 6f + sin(x / (unit * 80f)) * unit * 6f); x += unit * 20f }
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(sand, Brush.verticalGradient(listOf(Color(0xFFEAD7A4), Color(0xFFC9AE77)), floor - unit * 12f, h))
            for (i in 0 until 40) {
                val px = (i * 0.618034f % 1f) * w
                val py = floor + ((i * 0.381966f) % 1f) * (h - floor) * 0.9f + unit * 3f
                drawCircle(Color(0xFFB39461).copy(alpha = 0.7f), unit * (1.5f + (i % 3)), Offset(px, py))
            }
            // いつも上っていく泡
            for (i in 0 until 14) {
                val bx = (i * 0.137f + 0.05f) % 1f * w
                val speed = 0.05f + (i % 5) * 0.012f
                val by = floor - ((t * speed + i * 0.31f) % 1f) * floor
                drawCircle(Color.White.copy(alpha = 0.35f), unit * (2f + i % 3), Offset(bx + sin(t * 2f + i) * unit * 4f, by), style = Stroke(unit))
            }
        }
    }
}

// ─────────────────────────────────────────────
// 家具
// ─────────────────────────────────────────────

fun DrawScope.drawFurniture(f: FurnitureBody, sim: PetSim, theme: PetTheme, colors: PetColors, p: RetroPalette, emoji: EmojiCache, t: Float, unit: Float, selected: Boolean) {
    val r = sim.furnitureRadius(f.kind)
    val c = Offset(f.x, f.y)
    if (selected) {
        drawCircle(colors.accent.copy(alpha = 0.25f + 0.15f * sin(t * 6f)), r * 1.35f, c)
        drawCircle(colors.accent, r * 1.35f, c, style = Stroke(unit * 2f))
    }
    when (theme) {
        PetTheme.VAPOR -> drawCircle(Brush.radialGradient(listOf(p.cyan.copy(alpha = 0.35f), Color.Transparent), c, r * 1.4f), r * 1.4f, c)
        PetTheme.CASUAL -> if (f.kind != FurnitureKind.RUG) drawOval(colors.shadow, Offset(f.x - r * 0.9f, f.y + r * 0.45f), Size(r * 1.8f, r * 0.55f))
        PetTheme.AQUARIUM -> drawOval(Color(0x33002233), Offset(f.x - r * 0.9f, f.y + r * 0.5f), Size(r * 1.8f, r * 0.4f))
    }
    when (f.kind) {
        FurnitureKind.RUG -> {
            val w = r * 2.6f
            val h = r * 1.7f
            val base = if (theme == PetTheme.VAPOR) p.purple else Color(0xFFFFCDD2)
            val stripe = if (theme == PetTheme.VAPOR) p.pink else Color(0xFFFF8A80)
            drawRoundRect(base.copy(alpha = 0.9f), Offset(f.x - w / 2, f.y - h / 2), Size(w, h), CornerRadius(h / 2))
            drawRoundRect(stripe, Offset(f.x - w / 2 + unit * 6f, f.y - h / 2 + unit * 6f), Size(w - unit * 12f, h - unit * 12f), CornerRadius(h / 2), style = Stroke(unit * 3f))
            drawRoundRect(Color.White.copy(alpha = 0.5f), Offset(f.x - w / 2 + unit * 14f, f.y - h / 2 + unit * 14f), Size(w - unit * 28f, h - unit * 28f), CornerRadius(h / 2), style = Stroke(unit * 1.5f))
        }
        FurnitureKind.BOWL -> {
            val w = r * 1.9f
            drawOval(Color(0xFF8D6E63), Offset(f.x - w / 2, f.y - r * 0.3f), Size(w, r * 0.9f))
            drawOval(if (theme == PetTheme.VAPOR) p.pink else Color(0xFF64B5F6), Offset(f.x - w / 2, f.y - r * 0.45f), Size(w, r * 0.8f))
            for (i in 0 until 7) {
                val a = i * 0.9f
                drawCircle(Color(0xFFA0522D), r * 0.16f, Offset(f.x + cos(a) * r * 0.4f * (i % 3) / 2f, f.y - r * 0.1f + sin(a) * r * 0.12f))
            }
        }
        FurnitureKind.SEAWEED -> {
            for (k in 0 until 3) {
                val path = Path()
                val bx = f.x + (k - 1) * r * 0.35f
                path.moveTo(bx, f.y + r * 0.6f)
                val sway = sin(t * 1.3f + k * 1.7f + f.fx * 0.1f) * r * 0.35f
                path.cubicTo(bx + sway, f.y, bx - sway, f.y - r * 0.8f, bx + sway * 1.3f, f.y - r * (1.4f + k * 0.15f))
                drawPath(path, Color(0xFF2E7D32), style = Stroke(r * 0.26f, cap = StrokeCap.Round))
                drawPath(path, Color(0xFF66BB6A), style = Stroke(r * 0.12f, cap = StrokeCap.Round))
            }
        }
        FurnitureKind.BALL -> {
            val img = emoji.get(f.kind.emoji, (r * 2.2f).toInt())
            rotate(f.spin * 57.3f, c) { image(img, f.x, f.y, r * 2.2f, r * 2.2f) }
        }
        else -> {
            val s = r * 2.3f
            val bob = if (theme == PetTheme.AQUARIUM && f.kind == FurnitureKind.CHEST) sin(t * 2f) * unit else 0f
            image(emoji.get(f.kind.emoji, s.toInt()), f.x, f.y + bob, s, s)
            if (f.kind == FurnitureKind.TV) {
                // 画面のチラつき
                val glow = 0.25f + 0.15f * sin(t * 13f) * sin(t * 3.1f)
                drawCircle(Brush.radialGradient(listOf(colors.accent2.copy(alpha = glow), Color.Transparent), Offset(f.x, f.y + r), r * 2.2f), r * 2.2f, Offset(f.x, f.y + r))
            }
            if (f.kind == FurnitureKind.LAMP) {
                drawCircle(Brush.radialGradient(listOf(Color(0x66FFF59D), Color.Transparent), c, r * 3f), r * 3f, c)
            }
        }
    }
}

// ─────────────────────────────────────────────
// エサ
// ─────────────────────────────────────────────

fun DrawScope.drawFood(f: Food, sim: PetSim, unit: Float) {
    val r = sim.radius
    val s = (0.4f + 0.6f * f.amount)
    if (sim.aquarium) {
        for (i in 0 until 3) {
            val a = f.age * 1.5f + i * 2.1f
            val o = Offset(f.x + cos(i * 2.1f) * r * 0.15f, f.y + sin(i * 2.1f) * r * 0.1f)
            rotate(a * 57f, o) {
                drawRect(listOf(Color(0xFFFF7043), Color(0xFFFFCA28), Color(0xFFEF5350))[i], Offset(o.x - r * 0.08f * s, o.y - r * 0.05f * s), Size(r * 0.16f * s, r * 0.1f * s))
            }
        }
    } else {
        val dropY = -f.drop * r * 3f
        // 影（落ちてくる途中は小さい）
        drawOval(Color(0x33000000), Offset(f.x - r * 0.3f * (1f - f.drop * 0.5f), f.y + r * 0.08f), Size(r * 0.6f * (1f - f.drop * 0.5f), r * 0.18f))
        val pts = listOf(-0.14f to 0f, 0.12f to -0.04f, 0f to -0.13f, 0.02f to 0.08f, -0.06f to -0.06f)
        pts.take((2 + 3 * f.amount).toInt().coerceIn(1, 5)).forEach { (dx, dy) ->
            val o = Offset(f.x + dx * r, f.y + dy * r + dropY)
            drawCircle(Color(0xFF8D5524), r * 0.11f, o)
            drawCircle(Color(0xFFC68642), r * 0.05f, Offset(o.x - r * 0.03f, o.y - r * 0.03f))
        }
    }
}

// ─────────────────────────────────────────────
// クモ足のアプリ
// ─────────────────────────────────────────────

fun DrawScope.drawSpider(p: Pet, sim: PetSim, icon: ImageBitmap?, colors: PetColors, theme: PetTheme, t: Float, unit: Float) {
    val r = sim.radius
    val lift = p.lift * r * 0.8f
    val walking = p.speed > r * 0.2f
    val bob = if (walking) sin(p.walkPhase * PI.toFloat() * 2f) * r * 0.05f else sin(t * 2.5f + p.seed) * r * 0.02f
    // 影
    val sh = 1f + p.lift * 0.3f
    drawOval(colors.shadow.copy(alpha = colors.shadow.alpha * (1f - p.lift * 0.5f)), Offset(p.x - r * 1.05f * sh, p.y + r * 0.45f), Size(r * 2.1f * sh, r * 0.6f))

    translate(0f, -lift) {
        // 足（体より先に描く）
        val thigh = r * 0.15f
        val shin = r * 0.1f
        p.legs.forEach { l ->
            val (hx, hy) = sim.hip(p, l)
            val (kx, ky) = sim.knee(p, l)
            val hip = Offset(hx, hy)
            val knee = Offset(kx, ky - l.lift * r * 0.25f)
            val foot = Offset(l.footX, l.footY)
            colors.legGlow?.let { g ->
                drawLine(g.copy(alpha = 0.35f), hip, knee, thigh * 2.2f, StrokeCap.Round)
                drawLine(g.copy(alpha = 0.35f), knee, foot, shin * 2.2f, StrokeCap.Round)
            }
            drawLine(colors.leg, hip, knee, thigh, StrokeCap.Round)
            drawLine(colors.leg, knee, foot, shin, StrokeCap.Round)
            // 毛（カジュアルだけ、ちょっとだけ）
            if (theme == PetTheme.CASUAL) {
                val mx = (hip.x + knee.x) / 2
                val my = (hip.y + knee.y) / 2
                drawLine(colors.leg, Offset(mx, my), Offset(mx + (knee.y - hip.y) * 0.12f, my - (knee.x - hip.x) * 0.12f), unit * 1.2f, StrokeCap.Round)
            }
            drawCircle(colors.joint, thigh * 0.55f, knee)
            drawCircle(colors.joint, shin * 0.6f, foot)
        }

        // 体（アプリのアイコン）
        val joy = p.joy
        val sx = 1f + 0.12f * joy * sin(t * 22f)
        val sleeping = p.mood == Mood.SLEEP
        val sy = (1f - 0.12f * joy * sin(t * 22f)) * (if (sleeping) 0.93f else 1f) * (1f + 0.02f * sin(t * 3f + p.seed))
        val lean = (p.vx / (r * 3f)).coerceIn(-1f, 1f) * 10f
        val cy = p.y + bob
        val size = r * 1.9f
        withTransform({
            rotate(lean, Offset(p.x, cy + size / 2))
            scale(sx, sy, Offset(p.x, cy + size / 2))
        }) {
            if (theme == PetTheme.VAPOR) drawRoundRect(colors.accent.copy(alpha = 0.5f), Offset(p.x - size * 0.56f, cy - size * 0.56f), Size(size * 1.12f, size * 1.12f), CornerRadius(size * 0.3f), style = Stroke(unit * 3f))
            if (icon != null) image(icon, p.x, cy, size, size, alpha = if (sleeping) 0.85f else 1f)
            else drawRoundRect(colors.accent, Offset(p.x - size / 2, cy - size / 2), Size(size, size), CornerRadius(size * 0.25f))
            // ほっぺ
            if (joy > 0.05f) {
                drawCircle(Color(0xFFFF8A80).copy(alpha = 0.6f * joy), r * 0.18f, Offset(p.x - r * 0.62f, cy + r * 0.25f))
                drawCircle(Color(0xFFFF8A80).copy(alpha = 0.6f * joy), r * 0.18f, Offset(p.x + r * 0.62f, cy + r * 0.25f))
            }
            // 目（アイコンの上からのぞく）
            val eyeY = cy - size * 0.43f
            val look = Offset(cos(p.heading), sin(p.heading)) * (r * 0.07f)
            for (side in intArrayOf(-1, 1)) {
                val ec = Offset(p.x + side * r * 0.3f, eyeY)
                val er = r * 0.22f
                if (sleeping) {
                    drawArc(colors.joint, 10f, 160f, false, Offset(ec.x - er, ec.y - er * 0.6f), Size(er * 2, er * 1.2f), style = Stroke(unit * 2f, cap = StrokeCap.Round))
                } else {
                    val open = (1f - p.blink).coerceIn(0.08f, 1f)
                    drawOval(Color.White, Offset(ec.x - er, ec.y - er * open), Size(er * 2, er * 2 * open))
                    drawOval(colors.joint, Offset(ec.x - er, ec.y - er * open), Size(er * 2, er * 2 * open), style = Stroke(unit * 1.5f))
                    if (open > 0.3f) {
                        drawCircle(Color(0xFF212121), er * 0.5f, ec + look)
                        drawCircle(Color.White, er * 0.17f, ec + look + Offset(-er * 0.15f, -er * 0.15f))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// 魚になったアプリ
// ─────────────────────────────────────────────

fun DrawScope.drawFish(p: Pet, sim: PetSim, icon: ImageBitmap?, fin: Color, t: Float, unit: Float) {
    val r = sim.radius
    val dir = if (p.face >= 0f) 1f else -1f
    val width = abs(p.face).coerceIn(0.2f, 1f)
    val joy = p.joy
    val cy = p.y + sin(p.tail * 0.7f) * r * 0.05f
    val wag = sin(p.tail * 7f) * (0.25f + 0.25f * (p.speed / (r * 2f)).coerceIn(0f, 1f) + joy * 0.4f)
    val finDark = lerp(fin, Color.Black, 0.3f)

    // 尾びれ
    val tailBase = Offset(p.x - dir * r * 0.8f * width, cy)
    val tailLen = r * 0.9f
    val a = wag
    val tipUp = Offset(tailBase.x - dir * tailLen * cos(a - 0.5f), cy - tailLen * sin(0.6f + a * dir * 0.5f))
    val tipDown = Offset(tailBase.x - dir * tailLen * cos(a + 0.5f), cy + tailLen * sin(0.6f - a * dir * 0.5f))
    val tail = Path().apply {
        moveTo(tailBase.x, tailBase.y)
        quadraticTo(tailBase.x - dir * tailLen * 0.5f, cy - r * 0.2f, tipUp.x, tipUp.y)
        quadraticTo(tailBase.x - dir * tailLen * 0.55f, cy, tipDown.x, tipDown.y)
        quadraticTo(tailBase.x - dir * tailLen * 0.5f, cy + r * 0.2f, tailBase.x, tailBase.y)
        close()
    }
    drawPath(tail, fin.copy(alpha = 0.9f))
    drawPath(tail, finDark, style = Stroke(unit * 1.5f))

    // 背びれ
    val dorsal = Path().apply {
        moveTo(p.x - dir * r * 0.45f * width, cy - r * 0.78f)
        quadraticTo(p.x - dir * r * 0.1f * width, cy - r * (1.35f + 0.08f * sin(p.tail * 5f)), p.x + dir * r * 0.4f * width, cy - r * 0.8f)
        close()
    }
    drawPath(dorsal, fin.copy(alpha = 0.85f))
    drawPath(dorsal, finDark, style = Stroke(unit * 1.2f))

    // 体
    val size = r * 1.75f
    if (icon != null) image(icon, p.x, cy, size * width, size)
    else drawRoundRect(fin, Offset(p.x - size * width / 2, cy - size / 2), Size(size * width, size), CornerRadius(size * 0.3f))
    // うろこっぽい光
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Transparent), cy - size / 2, cy),
        Offset(p.x - size * width / 2, cy - size / 2), Size(size * width, size / 2), CornerRadius(size * 0.3f),
    )

    // 胸びれ（パタパタ）
    val pec = Path().apply {
        val bx = p.x + dir * r * 0.1f * width
        val by = cy + r * 0.55f
        moveTo(bx, by)
        quadraticTo(bx - dir * r * 0.25f, by + r * (0.45f + 0.15f * sin(p.tail * 9f)), bx - dir * r * 0.55f, by + r * 0.15f)
        close()
    }
    drawPath(pec, fin.copy(alpha = 0.8f))

    // 目
    if (width > 0.5f) {
        val ec = Offset(p.x + dir * r * 0.55f * width, cy - r * 0.38f)
        val er = r * 0.2f
        if (p.mood == Mood.SLEEP) {
            drawLine(Color(0xFF212121), Offset(ec.x - er * 0.8f, ec.y), Offset(ec.x + er * 0.8f, ec.y), unit * 2f, StrokeCap.Round)
        } else {
            val open = (1f - p.blink).coerceIn(0.1f, 1f)
            drawOval(Color.White, Offset(ec.x - er, ec.y - er * open), Size(er * 2, er * 2 * open))
            drawCircle(Color(0xFF212121), er * 0.55f, Offset(ec.x + dir * er * 0.25f, ec.y))
            drawCircle(Color.White, er * 0.18f, Offset(ec.x + dir * er * 0.1f, ec.y - er * 0.2f))
        }
        if (joy > 0.05f) drawCircle(Color(0xFFFF8A80).copy(alpha = 0.5f * joy), r * 0.16f, Offset(ec.x - dir * r * 0.1f, cy + r * 0.1f))
    }
}

// ─────────────────────────────────────────────
// 演出の粒
// ─────────────────────────────────────────────

private fun heartPath(c: Offset, s: Float): Path = Path().apply {
    moveTo(c.x, c.y + s * 0.35f)
    cubicTo(c.x - s * 0.9f, c.y - s * 0.2f, c.x - s * 0.45f, c.y - s * 0.85f, c.x, c.y - s * 0.35f)
    cubicTo(c.x + s * 0.45f, c.y - s * 0.85f, c.x + s * 0.9f, c.y - s * 0.2f, c.x, c.y + s * 0.35f)
    close()
}

fun DrawScope.drawParticles(sim: PetSim, colors: PetColors, emoji: EmojiCache, unit: Float) {
    val r = sim.radius
    sim.particles.forEach { q ->
        val fade = 1f - q.t
        when (q.kind) {
            FxKind.HEART -> {
                val s = r * 0.35f * q.size * (0.6f + 0.4f * min(1f, q.t * 5f))
                drawPath(heartPath(Offset(q.x, q.y), s), Color(0xFFFF5C8A).copy(alpha = fade))
                drawPath(heartPath(Offset(q.x, q.y), s), Color.White.copy(alpha = fade * 0.8f), style = Stroke(unit * 1.2f))
            }
            FxKind.ZZZ -> image(emoji.get("💤", (r * 0.7f).toInt()), q.x, q.y, r * 0.7f * q.size, r * 0.7f * q.size, alpha = fade)
            FxKind.NOTE -> image(emoji.get("🎵", (r * 0.6f).toInt()), q.x, q.y, r * 0.6f * q.size, r * 0.6f * q.size, alpha = fade)
            FxKind.HUNGRY -> {
                val c = Offset(q.x, q.y)
                val a = min(1f, (1f - q.t) * 3f) * min(1f, q.t * 6f)
                drawCircle(Color.White.copy(alpha = 0.92f * a), r * 0.42f, c)
                drawCircle(colors.accent.copy(alpha = a), r * 0.42f, c, style = Stroke(unit * 1.5f))
                drawCircle(Color.White.copy(alpha = 0.92f * a), r * 0.1f, Offset(c.x - r * 0.3f, c.y + r * 0.45f))
                image(emoji.get("🍖", (r * 0.55f).toInt()), c.x, c.y, r * 0.55f, r * 0.55f, alpha = a)
            }
            FxKind.BUBBLE -> {
                val br = r * 0.18f * q.size
                drawCircle(Color.White.copy(alpha = 0.55f * fade), br, Offset(q.x, q.y), style = Stroke(unit * 1.2f))
                drawCircle(Color.White.copy(alpha = 0.5f * fade), br * 0.3f, Offset(q.x - br * 0.35f, q.y - br * 0.35f))
            }
            FxKind.CRUMB -> drawCircle(Color(0xFFA0522D).copy(alpha = fade), r * 0.06f * q.size, Offset(q.x, q.y))
            FxKind.SPARK -> drawCircle(colors.accent.copy(alpha = fade * 0.8f), r * (0.2f + q.t * 1.2f), Offset(q.x, q.y), style = Stroke(unit * 2f * fade + 0.5f))
        }
    }
}

// ─────────────────────────────────────────────
// 名前と状態
// ─────────────────────────────────────────────

class LabelPaint(unit: Float) {
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = unit * 11f
    }
}

fun DrawScope.drawPetLabel(p: Pet, sim: PetSim, name: String, showName: Boolean, colors: PetColors, paint: LabelPaint, emoji: EmojiCache, unit: Float) {
    val r = sim.radius
    val lift = p.lift * r * 0.8f
    if (showName || p.infoTime > 0f) {
        val label = if (name.length > 10) name.take(9) + "…" else name
        val tw = paint.text.measureText(label)
        val y = p.y + r * (if (sim.aquarium) 1.15f else 1.25f) - lift
        drawRoundRect(colors.nameBg, Offset(p.x - tw / 2 - unit * 6f, y - unit * 2f), Size(tw + unit * 12f, unit * 17f), CornerRadius(unit * 9f))
        drawIntoCanvas { c ->
            paint.text.color = colors.nameText.toArgb()
            c.nativeCanvas.drawText(label, p.x, y + unit * 11f, paint.text)
        }
    }
    if (p.infoTime > 0f) {
        // 満腹度となつき度
        val a = min(1f, p.infoTime * 2f)
        val w = r * 2.2f
        val top = p.y - r * 2.05f - lift
        drawRoundRect(colors.nameBg.copy(alpha = colors.nameBg.alpha * a), Offset(p.x - w / 2 - unit * 4f, top - unit * 4f), Size(w + unit * 8f, unit * 30f), CornerRadius(unit * 8f))
        val icon = unit * 11f
        listOf(Triple("🍖", p.fullness, Color(0xFFFFA726)), Triple("💗", p.affection, Color(0xFFFF5C8A))).forEachIndexed { i, (e, v, col) ->
            val y = top + i * unit * 12f + unit * 1f
            image(emoji.get(e, icon.toInt()), p.x - w / 2 + icon / 2, y + icon / 2, icon, icon, alpha = a)
            val bx = p.x - w / 2 + icon + unit * 3f
            val bw = w - icon - unit * 3f
            drawRoundRect(Color.Black.copy(alpha = 0.15f * a), Offset(bx, y + unit * 3f), Size(bw, unit * 5f), CornerRadius(unit * 3f))
            drawRoundRect(col.copy(alpha = a), Offset(bx, y + unit * 3f), Size(bw * v.coerceIn(0f, 1f), unit * 5f), CornerRadius(unit * 3f))
        }
    }
}
