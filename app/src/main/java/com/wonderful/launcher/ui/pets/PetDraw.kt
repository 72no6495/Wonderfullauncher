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
import androidx.compose.ui.graphics.drawscope.clipPath
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
import com.wonderful.launcher.data.EyeStyle
import com.wonderful.launcher.data.PetLook
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
    PetTheme.MEADOW -> PetColors(
        leg = Color(0xFF4E342E), legGlow = null, joint = Color(0xFF3E2723), shadow = Color(0x33203010),
        nameBg = Color(0xE6FFFFFF), nameText = Color(0xFF33691E), accent = Color(0xFFFF7043), accent2 = Color(0xFF29B6F6),
    )
    PetTheme.BODY -> PetColors(
        leg = Color(0xFFE0F7FA), legGlow = null, joint = Color(0xFF4A0E1C), shadow = Color(0x44200008),
        nameBg = Color(0xCC3A0A14), nameText = Color(0xFFFFE4EC), accent = Color(0xFFFF5C8A), accent2 = Color(0xFF80DEEA),
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

internal fun DrawScope.image(img: ImageBitmap, cx: Float, cy: Float, w: Float, h: Float, alpha: Float = 1f) {
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
        PetTheme.MEADOW -> drawMeadow(t, unit)
        PetTheme.BODY -> drawBodyBackground(t, unit)
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
// 野原
// ─────────────────────────────────────────────

private fun frac(x: Float): Float = x - kotlin.math.floor(x)

private fun DrawScope.drawMeadow(t: Float, unit: Float) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFFA5D66B), Color(0xFF86C251), Color(0xFF74B243))))
    // 芝刈りのしま模様
    val band = unit * 70f
    rotate(-20f, Offset(w / 2, h / 2)) {
        var x = -w
        var i = 0
        while (x < w * 2) {
            if (i % 2 == 0) drawRect(Color.White.copy(alpha = 0.05f), Offset(x, -h), Size(band, h * 3))
            x += band; i++
        }
    }
    // 土の小道
    val path = Path().apply {
        moveTo(-unit * 20f, h * 0.78f)
        cubicTo(w * 0.25f, h * 0.55f, w * 0.5f, h * 1.0f, w * 0.75f, h * 0.7f)
        cubicTo(w * 0.88f, h * 0.55f, w * 0.95f, h * 0.45f, w + unit * 20f, h * 0.42f)
    }
    drawPath(path, Color(0xFFD7B98E), style = Stroke(unit * 46f, cap = StrokeCap.Round))
    drawPath(path, Color(0xFFE6CFA6), style = Stroke(unit * 34f, cap = StrokeCap.Round))
    // 草の束（風になびく）
    val wind = sin(t * 0.9f) * 0.5f + 0.5f
    for (i in 0 until 90) {
        val gx = frac(i * 0.618034f + 0.11f) * w
        val gy = frac(i * 0.381966f + 0.37f) * h
        val sway = (sin(t * 2.2f + gx * 0.01f) * 0.6f + wind) * unit * 3.5f
        val gh = unit * (7f + (i % 4) * 2f)
        val col = if (i % 3 == 0) Color(0xFF558B2F) else Color(0xFF689F38)
        for (k in -1..1) {
            drawLine(col, Offset(gx + k * unit * 2.5f, gy), Offset(gx + k * unit * 4f + sway, gy - gh * (1f - abs(k) * 0.25f)), unit * 1.6f, StrokeCap.Round)
        }
    }
    // 小さな花
    val petals = listOf(Color.White, Color(0xFFFFF176), Color(0xFFF8BBD0), Color(0xFFB39DDB))
    for (i in 0 until 34) {
        val fx = frac(i * 0.754877f + 0.2f) * w
        val fy = frac(i * 0.569840f + 0.6f) * h
        val pr = unit * (2.2f + (i % 3) * 0.6f)
        val nod = sin(t * 1.5f + i) * unit * 1.2f
        val col = petals[i % petals.size]
        for (k in 0 until 5) {
            val a = k * 1.2566f
            drawCircle(col, pr, Offset(fx + nod + cos(a) * pr * 1.2f, fy + sin(a) * pr * 1.2f))
        }
        drawCircle(Color(0xFFFFB300), pr * 0.8f, Offset(fx + nod, fy))
    }
    // ゆっくり流れる雲の影
    for (k in 0 until 3) {
        val cx = frac(t * 0.012f + k * 0.37f) * (w + unit * 600f) - unit * 300f
        val cy = h * (0.2f + k * 0.3f)
        drawOval(Color(0x14000000), Offset(cx - unit * 160f, cy - unit * 60f), Size(unit * 320f, unit * 120f))
        drawOval(Color(0x10000000), Offset(cx - unit * 60f, cy - unit * 90f), Size(unit * 220f, unit * 110f))
    }
    // 日だまり
    drawCircle(Brush.radialGradient(listOf(Color(0x33FFF9C4), Color.Transparent), Offset(w * 0.15f, h * 0.1f), h * 0.8f), h * 0.8f, Offset(w * 0.15f, h * 0.1f))
}

/** 野原を飛ぶチョウ（飼っている子の手前に描く） */
fun DrawScope.drawMeadowOverlay(t: Float, unit: Float) {
    val w = size.width
    val h = size.height
    val wings = listOf(Color(0xFFFFEB3B), Color(0xFFFFFFFF), Color(0xFF81D4FA), Color(0xFFFF8A65), Color(0xFFCE93D8))
    for (i in 0 until 5) {
        val sp = 0.05f + i * 0.013f
        val x = w * (0.5f + 0.42f * sin(t * sp * 2.1f + i * 1.7f) + 0.06f * sin(t * 0.9f + i))
        val y = h * (0.45f + 0.35f * sin(t * sp * 3.3f + i * 2.3f) + 0.05f * sin(t * 1.7f + i * 3f))
        val flap = abs(sin(t * 14f + i * 1.3f))
        val s = unit * 7f
        val dx = cos(t * sp * 2.1f + i * 1.7f)
        rotate(if (dx > 0f) 15f else -15f, Offset(x, y)) {
            for (side in intArrayOf(-1, 1)) {
                val wx = side * s * (0.3f + 0.9f * flap)
                drawOval(wings[i], Offset(x + (if (side < 0) wx else 0f), y - s), Size(abs(wx), s * 1.1f))
                drawOval(wings[i].copy(alpha = 0.85f), Offset(x + (if (side < 0) wx * 0.8f else 0f), y), Size(abs(wx) * 0.8f, s * 0.8f))
            }
            drawLine(Color(0xFF3E2723), Offset(x, y - s * 0.9f), Offset(x, y + s * 0.8f), unit * 1.5f, StrokeCap.Round)
        }
        // 影
        drawOval(Color(0x18000000), Offset(x - s * 0.6f, y + unit * 24f), Size(s * 1.2f, s * 0.4f))
    }
}

// ─────────────────────────────────────────────
// 家具
// ─────────────────────────────────────────────

fun DrawScope.drawFurniture(
    f: FurnitureBody, sim: PetSim, theme: PetTheme, colors: PetColors, p: RetroPalette, emoji: EmojiCache, t: Float, unit: Float, selected: Boolean,
    photos: Map<String, ImageBitmap> = emptyMap(),
) {
    val r = sim.furnitureRadius(f)
    if (f.kind == FurnitureKind.WIDGET) {
        // ウィジェット本体は上に重ねて表示するので、ここでは床の影と光だけ
        val w = f.solidW
        val h = f.solidH
        drawRoundRect(colors.shadow, Offset(f.x - w / 2 + unit * 4f, f.y - h / 2 + unit * 8f), Size(w, h), CornerRadius(unit * 16f))
        if (theme == PetTheme.VAPOR) drawRoundRect(colors.accent.copy(alpha = 0.35f), Offset(f.x - w / 2 - unit * 4f, f.y - h / 2 - unit * 4f), Size(w + unit * 8f, h + unit * 8f), CornerRadius(unit * 18f), style = Stroke(unit * 4f))
        if (selected) drawRoundRect(colors.accent, Offset(f.x - w / 2 - unit * 6f, f.y - h / 2 - unit * 6f), Size(w + unit * 12f, h + unit * 12f), CornerRadius(unit * 20f), style = Stroke(unit * 3f))
        return
    }
    if (f.kind == FurnitureKind.PHOTO) {
        drawPhotoFrame(f, r, photos[f.photo], theme, colors, p, emoji, t, unit, selected)
        return
    }
    val c = Offset(f.x, f.y)
    if (selected) {
        drawCircle(colors.accent.copy(alpha = 0.25f + 0.15f * sin(t * 6f)), r * 1.35f, c)
        drawCircle(colors.accent, r * 1.35f, c, style = Stroke(unit * 2f))
    }
    when (theme) {
        PetTheme.VAPOR -> drawCircle(Brush.radialGradient(listOf(p.cyan.copy(alpha = 0.35f), Color.Transparent), c, r * 1.4f), r * 1.4f, c)
        PetTheme.CASUAL, PetTheme.MEADOW -> if (f.kind != FurnitureKind.RUG) drawOval(colors.shadow, Offset(f.x - r * 0.9f, f.y + r * 0.45f), Size(r * 1.8f, r * 0.55f))
        PetTheme.AQUARIUM -> drawOval(Color(0x33002233), Offset(f.x - r * 0.9f, f.y + r * 0.5f), Size(r * 1.8f, r * 0.4f))
        PetTheme.BODY -> drawCircle(Brush.radialGradient(listOf(Color(0x55FFCDD2), Color.Transparent), c, r * 1.5f), r * 1.5f, c)
    }
    when (f.kind) {
        FurnitureKind.RUG -> if (theme == PetTheme.MEADOW) {
            // ピクニックシート（赤白チェック）
            val w = r * 2.6f
            val h = r * 1.9f
            val tl = Offset(f.x - w / 2, f.y - h / 2)
            drawRect(Color.White, tl, Size(w, h))
            val n = 6
            for (i in 0 until n) {
                drawRect(Color(0x88E53935), Offset(tl.x + i * w / n, tl.y), Size(w / n / 2, h))
                drawRect(Color(0x88E53935), Offset(tl.x, tl.y + i * h / n), Size(w, h / n / 2))
            }
            drawRect(Color(0xFFC62828), tl, Size(w, h), style = Stroke(unit * 2f))
        } else {
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
        FurnitureKind.BLOODCELL -> drawBloodCell(Offset(f.x, f.y + sin(t * 1.5f + f.fx) * unit * 2f), r, unit)
        FurnitureKind.BALL -> {
            val img = emoji.get(f.kind.emoji, (r * 2.2f).toInt())
            rotate(f.spin * 57.3f, c) { image(img, f.x, f.y, r * 2.2f, r * 2.2f) }
        }
        else -> {
            val s = r * (if (f.kind == FurnitureKind.TREE) 3.2f else 2.3f)
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

/** 写真立て（テーマごとに額の見た目を変える） */
private fun DrawScope.drawPhotoFrame(
    f: FurnitureBody, r: Float, img: ImageBitmap?, theme: PetTheme, colors: PetColors, p: RetroPalette,
    emoji: EmojiCache, t: Float, unit: Float, selected: Boolean,
) {
    val aspect = if (img != null && img.height > 0) img.width.toFloat() / img.height else 1f
    val long = r * 2.4f
    val w = if (aspect >= 1f) long else long * aspect
    val h = if (aspect >= 1f) long / aspect else long
    val tl = Offset(f.x - w / 2, f.y - h / 2)
    val tilt = when (theme) { PetTheme.MEADOW -> -4f; PetTheme.CASUAL -> 2f; else -> 0f }
    rotate(tilt, Offset(f.x, f.y)) {
        val pad = when (theme) {
            PetTheme.MEADOW -> r * 0.09f
            PetTheme.CASUAL -> r * 0.14f
            else -> r * 0.07f
        }
        val bottomPad = if (theme == PetTheme.MEADOW) r * 0.32f else pad
        val outer = Offset(tl.x - pad, tl.y - pad)
        val outerSize = Size(w + pad * 2, h + pad + bottomPad)
        // 影
        drawRoundRect(Color.Black.copy(alpha = 0.22f), outer + Offset(unit * 3f, unit * 6f), outerSize, CornerRadius(unit * 6f))
        when (theme) {
            PetTheme.CASUAL -> {
                drawRoundRect(Brush.linearGradient(listOf(Color(0xFFA1887F), Color(0xFF6D4C41)), outer, outer + Offset(outerSize.width, outerSize.height)), outer, outerSize, CornerRadius(unit * 4f))
                drawRect(Color(0xFFEFEBE9), Offset(tl.x - pad * 0.3f, tl.y - pad * 0.3f), Size(w + pad * 0.6f, h + pad * 0.6f))
            }
            PetTheme.VAPOR -> {
                drawRoundRect(colors.accent.copy(alpha = 0.3f), outer - Offset(unit * 4f, unit * 4f), Size(outerSize.width + unit * 8f, outerSize.height + unit * 8f), CornerRadius(unit * 10f), style = Stroke(unit * 6f))
                drawRoundRect(Color(0xFF1B0B33), outer, outerSize, CornerRadius(unit * 6f))
            }
            PetTheme.MEADOW -> drawRect(Color(0xFFFFFDF7), outer, outerSize)
            PetTheme.AQUARIUM -> drawRoundRect(Color(0x66B2EBF2), outer, outerSize, CornerRadius(r * 0.3f))
            // 体内：細胞膜のようなぷるぷるの枠
            PetTheme.BODY -> drawRoundRect(Color(0xCCF8BBD0), outer, outerSize, CornerRadius(r * 0.35f))
        }
        val clip = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(tl.x, tl.y, tl.x + w, tl.y + h, CornerRadius(if (theme == PetTheme.AQUARIUM) r * 0.25f else unit * 2f)))
        }
        clipPath(clip) {
            if (img != null) image(img, f.x, f.y, w, h)
            else {
                drawRect(Color(0xFFCFD8DC), tl, Size(w, h))
                image(emoji.get("🖼", (r * 1.2f).toInt()), f.x, f.y, r * 1.2f, r * 1.2f)
            }
            // ガラスの反射
            drawRect(
                Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.18f), Color.Transparent), tl, tl + Offset(w, h)),
                tl, Size(w, h),
            )
        }
        when (theme) {
            PetTheme.VAPOR -> drawRoundRect(
                Brush.linearGradient(listOf(p.pink, p.cyan, p.purple), outer, outer + Offset(outerSize.width, outerSize.height)),
                outer, outerSize, CornerRadius(unit * 6f), style = Stroke(unit * 2.5f),
            )
            PetTheme.MEADOW -> {
                // マスキングテープ
                rotate(-8f, Offset(f.x, outer.y)) {
                    drawRect(Color(0x99FFE082), Offset(f.x - r * 0.35f, outer.y - r * 0.12f), Size(r * 0.7f, r * 0.24f))
                }
            }
            PetTheme.AQUARIUM -> {
                drawRoundRect(Color.White.copy(alpha = 0.7f), outer, outerSize, CornerRadius(r * 0.3f), style = Stroke(unit * 2f))
                drawCircle(Color.White.copy(alpha = 0.5f), r * 0.08f, Offset(outer.x + r * 0.2f, outer.y + r * 0.2f), style = Stroke(unit))
            }
            PetTheme.BODY -> drawRoundRect(Color(0xFFAD1457), outer, outerSize, CornerRadius(r * 0.35f), style = Stroke(unit * 2f))
            PetTheme.CASUAL -> Unit
        }
        if (selected) drawRoundRect(colors.accent, outer - Offset(unit * 6f, unit * 6f), Size(outerSize.width + unit * 12f, outerSize.height + unit * 12f), CornerRadius(unit * 10f), style = Stroke(unit * 3f))
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

fun DrawScope.drawSpider(p: Pet, sim: PetSim, icon: ImageBitmap?, tint: Color, look: PetLook, colors: PetColors, theme: PetTheme, t: Float, unit: Float) {
    val r = sim.radius
    // 持ち上げ・ジャンプの高さ
    val air = p.lift * 0.8f + p.hop * 0.55f
    val lift = air * r
    val walking = p.speed > r * 0.2f
    val bob = if (walking) sin(p.walkPhase * PI.toFloat() * 2f) * r * 0.05f
    else if (p.mood == Mood.SLEEP) sin(t * 1.3f + p.seed) * r * 0.03f
    else sin(t * 2.5f + p.seed) * r * 0.02f
    // 影
    val sh = 1f + air * 0.3f
    drawOval(colors.shadow.copy(alpha = colors.shadow.alpha * (1f - air * 0.5f).coerceAtLeast(0.2f)), Offset(p.x - r * 1.05f * sh, p.y + r * 0.45f), Size(r * 2.1f * sh, r * 0.6f))

    translate(0f, -lift) {
        // 足（体より先に描く）
        p.legs.forEachIndexed { i, l ->
            val (hx, hy) = sim.hip(p, l)
            val (kx, ky) = sim.knee(p, l)
            drawLeg(
                look.legStyle, Offset(hx, hy), Offset(kx, ky - l.lift * r * 0.25f), Offset(l.footX, l.footY), r,
                legColor(look, colors.leg, tint, i, t), colors.legGlow, unit,
            )
        }

        // 体（アプリのアイコン）＋目＋飾り
        val joy = p.joy
        val sleeping = p.mood == Mood.SLEEP
        // 伸び・うれしい揺れ・寝息
        val sx = (1f + 0.12f * joy * sin(t * 22f)) * (1f - 0.08f * p.stretch) * (1f + 0.04f * p.tuck)
        val sy = (1f - 0.12f * joy * sin(t * 22f)) * (1f + 0.2f * p.stretch) * (if (sleeping) 0.92f + 0.03f * sin(t * 1.3f + p.seed) else 1f + 0.02f * sin(t * 3f + p.seed))
        val tilt = when (p.emote) {
            Emote.CONFUSED -> 14f * sin(min(1f, p.emoteTime * 4f) * PI.toFloat() / 2f)
            Emote.ANGRY -> sin(t * 40f) * 4f
            else -> 0f
        }
        val lean = (p.vx / (r * 3f)).coerceIn(-1f, 1f) * 10f + tilt
        val cy = p.y + bob + p.tuck * r * 0.12f
        val size = r * 1.9f
        withTransform({
            rotate(lean, Offset(p.x, cy + size / 2))
            scale(sx, sy, Offset(p.x, cy + size / 2))
        }) {
            if (theme == PetTheme.VAPOR) drawRoundRect(colors.accent.copy(alpha = 0.5f), Offset(p.x - size * 0.56f, cy - size * 0.56f), Size(size * 1.12f, size * 1.12f), CornerRadius(size * 0.3f), style = Stroke(unit * 3f))
            drawCritterHead(p.x, cy, size, icon, colors.accent, look, p.blink, sleeping, joy, Offset(p.gazeX, p.gazeY), t, unit, p.seed, p.emote)
        }
    }
}

// ─────────────────────────────────────────────
// 魚になったアプリ
// ─────────────────────────────────────────────

fun DrawScope.drawFish(p: Pet, sim: PetSim, icon: ImageBitmap?, fin: Color, look: PetLook, t: Float, unit: Float) {
    val r = sim.radius
    val dir = if (p.face >= 0f) 1f else -1f
    val width = abs(p.face).coerceIn(0.2f, 1f)
    val joy = p.joy
    val cy = p.y + sin(p.tail * 0.7f) * r * 0.05f
    val wag = sin(p.tail * 7f) * (0.25f + 0.25f * (p.speed / (r * 2f)).coerceIn(0f, 1f) + joy * 0.4f)
    val finCol = if (look.legColor == com.wonderful.launcher.data.LegColor.RAINBOW) Color.hsv((t * 50f + p.seed % 360 + 360f) % 360f, 0.6f, 1f) else fin
    val finDark = lerp(finCol, Color.Black, 0.3f)
    // ヒレの形も足の見た目に合わせて少し変える
    val finScale = when (look.legStyle) {
        com.wonderful.launcher.data.LegStyle.STUBBY -> 0.75f
        com.wonderful.launcher.data.LegStyle.HAIRY -> 1.25f
        else -> 1f
    }

    // 尾びれ
    val tailBase = Offset(p.x - dir * r * 0.8f * width, cy)
    val tailLen = r * 0.9f * finScale
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
    drawPath(tail, finCol.copy(alpha = 0.9f))
    if (look.legStyle == com.wonderful.launcher.data.LegStyle.CANDY) {
        drawPath(tail, Color.White.copy(alpha = 0.7f), style = Stroke(unit * 3f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(unit * 4f, unit * 4f))))
    }
    drawPath(tail, finDark, style = Stroke(unit * 1.5f))

    // 背びれ（メカはギザギザ）
    val dorsal = Path().apply {
        moveTo(p.x - dir * r * 0.45f * width, cy - r * 0.78f)
        if (look.legStyle == com.wonderful.launcher.data.LegStyle.ROBOT) {
            for (k in 1..4) {
                val fx = p.x - dir * r * (0.45f - k * 0.22f) * width
                lineTo(fx - dir * r * 0.11f * width, cy - r * (1.15f + 0.05f * k))
                lineTo(fx, cy - r * 0.8f)
            }
        } else {
            quadraticTo(p.x - dir * r * 0.1f * width, cy - r * (1.35f + 0.08f * sin(p.tail * 5f)) * finScale.coerceAtMost(1.1f), p.x + dir * r * 0.4f * width, cy - r * 0.8f)
        }
        close()
    }
    drawPath(dorsal, (if (look.legStyle == com.wonderful.launcher.data.LegStyle.ROBOT) Color(0xFF90A4AE) else finCol).copy(alpha = 0.85f))
    drawPath(dorsal, finDark, style = Stroke(unit * 1.2f))

    // 体
    val size = r * 1.75f
    if (icon != null) image(icon, p.x, cy, size * width, size)
    else drawRoundRect(finCol, Offset(p.x - size * width / 2, cy - size / 2), Size(size * width, size), CornerRadius(size * 0.3f))
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
        quadraticTo(bx - dir * r * 0.25f, by + r * (0.45f + 0.15f * sin(p.tail * 9f)) * finScale, bx - dir * r * 0.55f, by + r * 0.15f)
        close()
    }
    drawPath(pec, finCol.copy(alpha = 0.8f))

    // 頭の飾り
    if (width > 0.5f) {
        drawAccessory(look.accessory, p.x + dir * r * 0.1f, cy, size * width.coerceAtLeast(0.7f), t, unit, p.seed, behind = true)
        drawAccessory(look.accessory, p.x + dir * r * 0.1f, cy, size * width.coerceAtLeast(0.7f), t, unit, p.seed, behind = false)
    }

    // 目（横から見ているので 1 つ）
    if (width > 0.5f) {
        val ec = Offset(p.x + dir * r * 0.55f * width, cy - r * 0.38f)
        val eye = expressiveEyes(look.eyes, p.emote).let { if (it == EyeStyle.SMILE || it == EyeStyle.HEART || it == EyeStyle.SLEEPY) it else look.eyes }
        drawEye(eye, ec, r * 0.2f * (if (p.emote == Emote.SURPRISED) 1.35f else 1f), dir.toInt(), Offset(dir * 0.8f, p.gazeY * 0.5f), p.blink, p.mood == Mood.SLEEP, joy, t, unit)
        if (p.mood != Mood.SLEEP) drawExpression(p.emote, ec, r, p.x, cy, size, t, unit, pair = false)
        else drawNoseBubble(Offset(p.x + dir * r * 0.95f, cy + r * 0.05f), r, t, unit, p.seed)
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

fun DrawScope.drawParticles(sim: PetSim, colors: PetColors, emoji: EmojiCache, unit: Float, paint: LabelPaint) {
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
            FxKind.PHAGELET -> drawPhagelet(q, r, unit)
            FxKind.SPEECH -> {
                // おしゃべりの吹き出し
                val pop = min(1f, q.t * 8f)
                val a = min(1f, (1f - q.t) * 4f)
                paint.speech.textSize = r * 0.42f * (0.7f + 0.3f * pop)
                val tw = paint.speech.measureText(q.text)
                val w = tw + r * 0.4f
                val h = r * 0.62f * (0.7f + 0.3f * pop)
                val tl = Offset(q.x - w / 2, q.y - h / 2)
                drawRoundRect(Color.White.copy(alpha = 0.95f * a), tl, Size(w, h), CornerRadius(h / 2))
                drawRoundRect(colors.accent.copy(alpha = a), tl, Size(w, h), CornerRadius(h / 2), style = Stroke(unit * 1.2f))
                val tail = Path().apply { moveTo(q.x - r * 0.1f, q.y + h / 2 - 1f); lineTo(q.x + r * 0.05f, q.y + h / 2 + r * 0.18f); lineTo(q.x + r * 0.12f, q.y + h / 2 - 1f); close() }
                drawPath(tail, Color.White.copy(alpha = 0.95f * a))
                drawIntoCanvas { c ->
                    paint.speech.color = Color(0xFF424242).copy(alpha = a).toArgb()
                    c.nativeCanvas.drawText(q.text, q.x, q.y + paint.speech.textSize * 0.35f, paint.speech)
                }
            }
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
    val speech = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
}

/** 頭の上の気持ちの吹き出し（ポンと出て、ふわふわして、消える） */
fun DrawScope.drawEmoteBalloon(p: Pet, sim: PetSim, emoji: EmojiCache, colors: PetColors, unit: Float) {
    if (p.emote == Emote.NONE || p.emote.emoji.isEmpty()) return
    val r = sim.radius
    val k = p.emoteTime
    val out = (p.emoteLength - k).coerceAtLeast(0f)
    val pop = if (k < 0.25f) {
        val x = k / 0.25f
        val c1 = 1.70158f; val c3 = c1 + 1f; val y = x - 1f
        1f + c3 * y * y * y + c1 * y * y
    } else 1f
    val alpha = min(1f, out / 0.25f)
    val air = (if (sim.aquarium) 0f else p.lift * 0.8f + p.hop * 0.55f) * r
    val c = Offset(p.x + r * 0.75f, p.y - r * 1.55f - air + sin(k * 4f) * r * 0.05f)
    val br = r * 0.42f * pop
    if (br <= 0f) return
    drawCircle(Color.Black.copy(alpha = 0.12f * alpha), br, c + Offset(unit, unit * 1.5f))
    drawCircle(Color.White.copy(alpha = 0.95f * alpha), br, c)
    drawCircle(colors.accent.copy(alpha = 0.8f * alpha), br, c, style = Stroke(unit * 1.2f))
    drawCircle(Color.White.copy(alpha = 0.95f * alpha), br * 0.22f, Offset(c.x - br * 0.75f, c.y + br * 0.85f))
    drawCircle(Color.White.copy(alpha = 0.95f * alpha), br * 0.12f, Offset(c.x - br * 1.05f, c.y + br * 1.25f))
    val s = br * 1.25f
    image(emoji.get(p.emote.emoji, (r * 0.6f).toInt()), c.x, c.y, s, s, alpha)
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
