package com.wonderful.launcher.ui.pets

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import com.wonderful.launcher.data.Accessory
import com.wonderful.launcher.data.EyeStyle
import com.wonderful.launcher.data.LegColor
import com.wonderful.launcher.data.LegStyle
import com.wonderful.launcher.data.PetLook
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * 飼っている子の見た目のバリエーション（目・頭の飾り・足）。
 */

private val INK = Color(0xFF212121)

/**
 * アイコンの体に目と飾りを付けて描く（クモの体と、着せ替え画面のプレビューで共通）。
 * [gaze] は見ている方向（長さ 1 くらい）。
 */
fun DrawScope.drawCritterHead(
    cx: Float, cy: Float, size: Float, icon: ImageBitmap?, fallback: Color, look: PetLook,
    blink: Float, sleeping: Boolean, joy: Float, gaze: Offset, t: Float, unit: Float, seed: Int,
    emote: Emote = Emote.NONE,
) {
    if (icon != null) image(icon, cx, cy, size, size, alpha = if (sleeping) 0.88f else 1f)
    else drawRoundRect(fallback, Offset(cx - size / 2, cy - size / 2), Size(size, size), CornerRadius(size * 0.25f))
    val r = size / 1.9f
    // ほっぺ
    if (joy > 0.05f) {
        drawCircle(Color(0xFFFF8A80).copy(alpha = 0.6f * joy), r * 0.18f, Offset(cx - r * 0.62f, cy + r * 0.25f))
        drawCircle(Color(0xFFFF8A80).copy(alpha = 0.6f * joy), r * 0.18f, Offset(cx + r * 0.62f, cy + r * 0.25f))
    }
    // 帽子などは目より奥（耳・ヘッドホン）と手前に分けて描く
    val eyeC = Offset(cx, cy - size * 0.43f)
    val eyes = expressiveEyes(look.eyes, emote)
    val eyeScale = if (emote == Emote.SURPRISED) 1.35f else 1f
    // 帽子は目の一番上（まゆ・まつげ込み）より上にのせる
    val hatY = eyeTop(eyes, eyeC.y, r, eyeScale) - size * 0.03f
    drawAccessory(look.accessory, cx, cy, size, t, unit, seed, behind = true, hatY = hatY)
    drawEyePair(eyes, eyeC, r, gaze, blink, sleeping, joy, t, unit, scale = eyeScale, emote = emote)
    if (!sleeping) drawExpression(emote, eyeC, r, cx, cy, size, t, unit, pair = eyes != EyeStyle.CYCLOPS)
    if (sleeping) drawNoseBubble(Offset(cx + r * 0.55f, cy + r * 0.05f), r, t, unit, seed)
    drawAccessory(look.accessory, cx, cy, size, t, unit, seed, behind = false, hatY = hatY)
}

/** 目（まゆ・まつげも含む）の一番上の y */
fun eyeTop(style: EyeStyle, eyeY: Float, r: Float, scale: Float = 1f): Float {
    val er = r * 0.22f * scale
    return eyeY - er * when (style) {
        EyeStyle.CYCLOPS -> 1.95f
        EyeStyle.SPIDER -> 1.3f
        EyeStyle.SHADES -> 1.0f
        else -> 1.7f // つり目・かなしい眉・キラキラのまつげまで入る高さ
    }
}

/** 気持ちによって目を変える（サングラスやクモの目はそのまま） */
fun expressiveEyes(base: EyeStyle, emote: Emote): EyeStyle {
    // ひとつ目の子はどんな気持ちでもひとつ目のまま（目の中身だけ変わる → cyclopsInner）
    if (base == EyeStyle.SHADES || base == EyeStyle.SPIDER || base == EyeStyle.CYCLOPS) return base
    return when (emote) {
        Emote.LOVE -> EyeStyle.HEART
        Emote.HAPPY, Emote.YUMMY, Emote.MUSIC -> if (base == EyeStyle.CYCLOPS) base else EyeStyle.SMILE
        Emote.SLEEPY -> EyeStyle.SLEEPY
        Emote.SURPRISED -> if (base == EyeStyle.CYCLOPS) base else EyeStyle.GOOGLY
        else -> base
    }
}

/** ひとつ目の子の目の中身（ハート・にっこり・ねむそう…） */
fun cyclopsInner(emote: Emote): EyeStyle = when (emote) {
    Emote.LOVE -> EyeStyle.HEART
    Emote.HAPPY, Emote.YUMMY, Emote.MUSIC -> EyeStyle.SMILE
    Emote.SLEEPY -> EyeStyle.SLEEPY
    else -> EyeStyle.GOOGLY
}

/** 表情の足し算（おこ眉・なみだ・汗） */
fun DrawScope.drawExpression(emote: Emote, eyeC: Offset, r: Float, cx: Float, cy: Float, size: Float, t: Float, unit: Float, pair: Boolean) {
    val er = r * 0.22f
    val sides = if (pair) intArrayOf(-1, 1) else intArrayOf(1)
    when (emote) {
        Emote.ANGRY -> sides.forEach { side ->
            val ec = Offset(eyeC.x + (if (pair) side * r * 0.3f else 0f), eyeC.y)
            drawLine(INK, Offset(ec.x + side * er * 1.1f, ec.y - er * 1.6f), Offset(ec.x - side * er * 0.9f, ec.y - er * 0.95f), unit * 3f, StrokeCap.Round)
        }
        Emote.SAD, Emote.HUNGRY -> sides.forEach { side ->
            val ec = Offset(eyeC.x + (if (pair) side * r * 0.3f else 0f), eyeC.y)
            drawLine(INK, Offset(ec.x + side * er * 1.1f, ec.y - er * 1.0f), Offset(ec.x - side * er * 0.8f, ec.y - er * 1.6f), unit * 2.5f, StrokeCap.Round)
            if (emote == Emote.SAD) {
                // なみだ
                val k = (t * 1.4f + side * 0.37f).let { it - kotlin.math.floor(it) }
                val d = Offset(ec.x + side * er * 0.5f, ec.y + er * 0.9f + k * r * 0.9f)
                drawPath(drop(d, er * 0.38f), Color(0xFF64B5F6).copy(alpha = 1f - k))
            }
        }
        Emote.SWEAT -> {
            val k = (t * 0.8f).let { it - kotlin.math.floor(it) }
            val d = Offset(cx + size * 0.42f, cy - size * 0.38f + k * size * 0.25f)
            drawPath(drop(d, size * 0.07f), Color(0xFF81D4FA).copy(alpha = 1f - k * 0.6f))
            drawPath(drop(d, size * 0.07f), Color(0xFF0277BD).copy(alpha = 0.6f), style = Stroke(unit))
        }
        else -> Unit
    }
}

/** しずく形 */
private fun drop(c: Offset, s: Float): Path = Path().apply {
    moveTo(c.x, c.y - s * 1.6f)
    cubicTo(c.x + s * 0.2f, c.y - s * 0.8f, c.x + s, c.y - s * 0.3f, c.x + s, c.y + s * 0.2f)
    cubicTo(c.x + s, c.y + s * 0.8f, c.x - s, c.y + s * 0.8f, c.x - s, c.y + s * 0.2f)
    cubicTo(c.x - s, c.y - s * 0.3f, c.x - s * 0.2f, c.y - s * 0.8f, c.x, c.y - s * 1.6f)
    close()
}

/** 寝ている時の鼻ちょうちん */
fun DrawScope.drawNoseBubble(c: Offset, r: Float, t: Float, unit: Float, seed: Int) {
    val k = 0.5f + 0.5f * sin(t * 1.6f + seed % 7)
    val br = r * (0.1f + 0.22f * k)
    val o = Offset(c.x + br * 0.6f, c.y)
    drawCircle(Color(0xFFB3E5FC).copy(alpha = 0.55f), br, o)
    drawCircle(Color.White.copy(alpha = 0.8f), br, o, style = Stroke(unit * 1.2f))
    drawCircle(Color.White.copy(alpha = 0.9f), br * 0.25f, Offset(o.x - br * 0.35f, o.y - br * 0.35f))
}

// ─────────────────────────────────────────────
// 目
// ─────────────────────────────────────────────

fun DrawScope.drawEyePair(style: EyeStyle, c: Offset, r: Float, gaze: Offset, blink: Float, sleeping: Boolean, joy: Float, t: Float, unit: Float, scale: Float = 1f, emote: Emote = Emote.NONE) {
    val er = r * 0.22f * scale
    when (style) {
        EyeStyle.CYCLOPS -> drawEye(cyclopsInner(emote), Offset(c.x, c.y - er * 0.2f), er * 1.7f, 1, gaze, blink, sleeping, joy, t, unit)
        EyeStyle.SPIDER -> drawSpiderEyes(c, r, gaze, blink, sleeping, unit)
        EyeStyle.SHADES -> drawShades(c, r, er, t, unit, pair = true)
        else -> for (side in intArrayOf(-1, 1)) drawEye(style, Offset(c.x + side * r * 0.3f, c.y), er, side, gaze, blink, sleeping, joy, t, unit)
    }
}

/** 目 1 つ（魚は 1 つだけ描く）。[side] は左右（つり目の向き） */
fun DrawScope.drawEye(style: EyeStyle, c: Offset, er: Float, side: Int, gaze: Offset, blink: Float, sleeping: Boolean, joy: Float, t: Float, unit: Float) {
    val sw = unit * 1.5f
    val look = gaze * (er * 0.32f)
    if (sleeping && style != EyeStyle.SHADES && style != EyeStyle.BUTTON) {
        drawArc(INK, 10f, 160f, false, Offset(c.x - er, c.y - er * 0.6f), Size(er * 2, er * 1.2f), style = Stroke(unit * 2f, cap = StrokeCap.Round))
        return
    }
    val open = (1f - blink).coerceIn(0.08f, 1f)
    fun white(w: Float = er, h: Float = er) {
        drawOval(Color.White, Offset(c.x - w, c.y - h * open), Size(w * 2, h * 2 * open))
        drawOval(INK, Offset(c.x - w, c.y - h * open), Size(w * 2, h * 2 * open), style = Stroke(sw))
    }
    when (style) {
        EyeStyle.GOOGLY, EyeStyle.CYCLOPS, EyeStyle.MIXED -> {
            white()
            if (open > 0.3f) {
                drawCircle(INK, er * 0.5f, c + look)
                drawCircle(Color.White, er * 0.17f, c + look + Offset(-er * 0.15f, -er * 0.15f))
            }
        }
        EyeStyle.DOTS -> {
            drawOval(INK, Offset(c.x - er * 0.42f, c.y - er * 0.55f * open + look.y * 0.3f), Size(er * 0.84f, er * 1.1f * open))
            if (open > 0.3f) drawCircle(Color.White, er * 0.15f, Offset(c.x - er * 0.12f, c.y - er * 0.22f))
        }
        EyeStyle.ANIME -> {
            val h = er * 1.3f
            drawOval(Color.White, Offset(c.x - er, c.y - h * open), Size(er * 2, h * 2 * open))
            if (open > 0.3f) {
                val ic = c + look * 0.6f
                drawOval(
                    Brush.verticalGradient(listOf(Color(0xFF81D4FA), Color(0xFF1565C0)), ic.y - h * 0.8f, ic.y + h * 0.8f),
                    Offset(ic.x - er * 0.78f, ic.y - h * 0.8f * open), Size(er * 1.56f, h * 1.6f * open),
                )
                drawOval(INK, Offset(ic.x - er * 0.38f, ic.y - h * 0.42f * open), Size(er * 0.76f, h * 0.84f * open))
                drawCircle(Color.White, er * 0.3f, Offset(ic.x - er * 0.3f, ic.y - h * 0.38f))
                drawCircle(Color.White, er * 0.13f, Offset(ic.x + er * 0.28f, ic.y + h * 0.3f))
            }
            // まつげ
            drawArc(INK, 195f, 150f, false, Offset(c.x - er * 1.05f, c.y - h * open * 1.05f), Size(er * 2.1f, h * 2.1f * open), style = Stroke(unit * 3f, cap = StrokeCap.Round))
            drawLine(INK, Offset(c.x + side * er * 0.95f, c.y - h * 0.55f * open), Offset(c.x + side * er * 1.35f, c.y - h * 0.85f * open), unit * 2f, StrokeCap.Round)
            if (joy > 0.2f) drawStar(Offset(c.x + side * er * 1.4f, c.y - h * 1.2f), er * 0.35f * joy, Color(0xFFFFF59D), null, unit)
        }
        EyeStyle.SMILE -> drawArc(INK, 200f, 140f, false, Offset(c.x - er, c.y - er * 0.35f), Size(er * 2, er * 1.6f), style = Stroke(unit * 2.4f, cap = StrokeCap.Round))
        EyeStyle.SLEEPY -> {
            white()
            if (open > 0.3f) drawCircle(INK, er * 0.45f, Offset(c.x + look.x, c.y + er * 0.35f))
            // まぶた
            drawArc(Color(0xFF90A4AE), 180f, 180f, true, Offset(c.x - er, c.y - er * open), Size(er * 2, er * 2 * open))
            drawLine(INK, Offset(c.x - er, c.y), Offset(c.x + er, c.y), sw * 1.3f, StrokeCap.Round)
        }
        EyeStyle.ANGRY -> {
            white()
            if (open > 0.3f) drawCircle(INK, er * 0.45f, c + look + Offset(0f, er * 0.15f))
            // まゆ（内側が下がる）
            drawLine(INK, Offset(c.x + side * er * 1.1f, c.y - er * 1.55f), Offset(c.x - side * er * 0.9f, c.y - er * 0.95f), unit * 3f, StrokeCap.Round)
        }
        EyeStyle.HEART -> {
            white()
            if (open > 0.3f) {
                val s = er * (0.7f + 0.15f * sin(t * 8f) * (0.3f + joy))
                drawPath(heart(c + look * 0.6f, s), Color(0xFFFF4081))
            }
        }
        EyeStyle.STAR -> {
            white()
            if (open > 0.3f) drawStar(c + look * 0.6f, er * 0.62f, Color(0xFFFFC107), INK, unit, rot = t * 30f)
        }
        EyeStyle.BUTTON -> {
            drawCircle(Color(0xFFE57373), er * 0.95f, c)
            drawCircle(Color(0xFFB71C1C), er * 0.95f, c, style = Stroke(sw))
            drawCircle(Color(0xFFB71C1C), er * 0.62f, c, style = Stroke(sw * 0.8f))
            val hole = er * 0.12f
            for ((dx, dy) in listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)) drawCircle(Color(0xFF7F0000), hole, Offset(c.x + dx * er * 0.25f, c.y + dy * er * 0.25f))
            drawLine(Color(0xFF4E342E), Offset(c.x - er * 0.25f, c.y - er * 0.25f), Offset(c.x + er * 0.25f, c.y + er * 0.25f), sw)
            drawLine(Color(0xFF4E342E), Offset(c.x + er * 0.25f, c.y - er * 0.25f), Offset(c.x - er * 0.25f, c.y + er * 0.25f), sw)
        }
        EyeStyle.SPIDER -> drawSpiderEyes(c, er / 0.22f * 0.6f, gaze, blink, false, unit)
        EyeStyle.SHADES -> drawShades(c, er / 0.22f, er, t, unit, pair = false)
    }
}

private fun DrawScope.drawSpiderEyes(c: Offset, r: Float, gaze: Offset, blink: Float, sleeping: Boolean, unit: Float) {
    val open = if (sleeping) 0.15f else (1f - blink).coerceIn(0.15f, 1f)
    val eyes = listOf(
        Triple(-0.13f, 0f, 0.13f), Triple(0.13f, 0f, 0.13f),
        Triple(-0.36f, -0.06f, 0.09f), Triple(0.36f, -0.06f, 0.09f),
        Triple(-0.24f, -0.2f, 0.065f), Triple(0.24f, -0.2f, 0.065f),
        Triple(-0.46f, 0.1f, 0.06f), Triple(0.46f, 0.1f, 0.06f),
    )
    eyes.forEach { (dx, dy, s) ->
        val e = Offset(c.x + dx * r, c.y + dy * r)
        val rr = s * r
        drawOval(INK, Offset(e.x - rr, e.y - rr * open), Size(rr * 2, rr * 2 * open))
        if (open > 0.4f) {
            drawCircle(Color(0xFFB71C1C).copy(alpha = 0.5f), rr * 0.7f, e + gaze * (rr * 0.2f))
            drawCircle(Color.White, rr * 0.3f, Offset(e.x - rr * 0.3f, e.y - rr * 0.35f))
        }
    }
}

private fun DrawScope.drawShades(c: Offset, r: Float, er: Float, t: Float, unit: Float, pair: Boolean) {
    val w = er * 2.4f
    val h = er * 1.7f
    val centers = if (pair) listOf(Offset(c.x - r * 0.3f, c.y), Offset(c.x + r * 0.3f, c.y)) else listOf(c)
    if (pair) drawLine(INK, Offset(centers[0].x + w / 2, c.y - h * 0.15f), Offset(centers[1].x - w / 2, c.y - h * 0.15f), unit * 2.5f)
    centers.forEach { e ->
        val tl = Offset(e.x - w / 2, e.y - h / 2)
        drawRoundRect(Brush.linearGradient(listOf(Color(0xFF311B92), Color(0xFF000000), Color(0xFFAD1457)), tl, tl + Offset(w, h)), tl, Size(w, h), CornerRadius(h * 0.45f))
        drawRoundRect(INK, tl, Size(w, h), CornerRadius(h * 0.45f), style = Stroke(unit * 2f))
        // 反射
        val sweep = ((t * 0.4f) % 1.6f) - 0.3f
        drawLine(Color.White.copy(alpha = 0.55f), Offset(tl.x + w * sweep, tl.y + h * 0.15f), Offset(tl.x + w * (sweep + 0.25f), tl.y + h * 0.85f), unit * 2f, StrokeCap.Round)
    }
}

fun heart(c: Offset, s: Float): Path = Path().apply {
    moveTo(c.x, c.y + s * 0.6f)
    cubicTo(c.x - s * 1.3f, c.y - s * 0.1f, c.x - s * 0.6f, c.y - s * 1.1f, c.x, c.y - s * 0.4f)
    cubicTo(c.x + s * 0.6f, c.y - s * 1.1f, c.x + s * 1.3f, c.y - s * 0.1f, c.x, c.y + s * 0.6f)
    close()
}

fun DrawScope.drawStar(c: Offset, r: Float, fill: Color, outline: Color?, unit: Float, rot: Float = 0f) {
    val path = Path()
    for (i in 0 until 10) {
        val a = (rot - 90f + i * 36f) * PI.toFloat() / 180f
        val rr = if (i % 2 == 0) r else r * 0.45f
        val p = Offset(c.x + cos(a) * rr, c.y + sin(a) * rr)
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, fill)
    if (outline != null) drawPath(path, outline, style = Stroke(unit * 1.2f))
}

// ─────────────────────────────────────────────
// 頭の飾り
// ─────────────────────────────────────────────

fun DrawScope.drawAccessory(a: Accessory, cx: Float, cy: Float, size: Float, t: Float, unit: Float, seed: Int, behind: Boolean, hatY: Float = cy - size * 0.64f) {
    val top = cy - size / 2
    val sw = unit * 1.5f
    when (a) {
        Accessory.NONE, Accessory.MIXED -> Unit
        Accessory.CAT_EARS -> if (behind) for (side in intArrayOf(-1, 1)) {
            val twitch = if ((seed + (t * 0.5f).toInt()) % 7 == 0) sin(t * 30f) * 4f else 0f
            rotate(side * (8f + twitch), Offset(cx + side * size * 0.3f, top + size * 0.05f)) {
                val ear = Path().apply {
                    moveTo(cx + side * size * 0.46f, top + size * 0.12f)
                    lineTo(cx + side * size * 0.36f, top - size * 0.3f)
                    lineTo(cx + side * size * 0.1f, top + size * 0.02f)
                    close()
                }
                drawPath(ear, Color(0xFF424242))
                val inner = Path().apply {
                    moveTo(cx + side * size * 0.4f, top + size * 0.06f)
                    lineTo(cx + side * size * 0.34f, top - size * 0.18f)
                    lineTo(cx + side * size * 0.18f, top + size * 0.02f)
                    close()
                }
                drawPath(inner, Color(0xFFF48FB1))
            }
        }
        Accessory.HEADPHONES -> if (behind) {
            drawArc(Color(0xFF37474F), 195f, 150f, false, Offset(cx - size * 0.56f, top - size * 0.28f), Size(size * 1.12f, size * 0.95f), style = Stroke(size * 0.07f, cap = StrokeCap.Round))
        } else for (side in intArrayOf(-1, 1)) {
            val tl = Offset(cx + side * size * 0.52f - size * 0.09f, cy - size * 0.28f)
            drawRoundRect(Color(0xFFFF4081), tl, Size(size * 0.18f, size * 0.34f), CornerRadius(size * 0.08f))
            drawRoundRect(Color(0xFF37474F), tl, Size(size * 0.18f, size * 0.34f), CornerRadius(size * 0.08f), style = Stroke(sw))
            // 音符がときどき出る
            if (((t * 0.7f + seed % 5) % 3f) < 0.6f && side == 1) {
                val y = tl.y - ((t * 0.7f + seed % 5) % 3f) * size * 0.4f
                drawCircle(Color(0xFF7E57C2), size * 0.05f, Offset(tl.x + size * 0.3f, y))
                drawLine(Color(0xFF7E57C2), Offset(tl.x + size * 0.34f, y), Offset(tl.x + size * 0.34f, y - size * 0.14f), sw)
            }
        }
        Accessory.ANTENNA -> if (behind) for (side in intArrayOf(-1, 1)) {
            val sway = sin(t * 4f + seed + side) * size * 0.05f
            val base = Offset(cx + side * size * 0.16f, top + size * 0.02f)
            val tip = Offset(cx + side * size * 0.3f + sway, top - size * 0.36f)
            val path = Path().apply { moveTo(base.x, base.y); quadraticTo(base.x + side * size * 0.02f, top - size * 0.2f, tip.x, tip.y) }
            drawPath(path, Color(0xFF424242), style = Stroke(unit * 2f, cap = StrokeCap.Round))
            drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFFF4081)), tip - Offset(size * 0.02f, size * 0.02f), size * 0.08f), size * 0.07f, tip)
        }
        Accessory.HALO -> if (behind) {
            val y = hatY - size * 0.14f + sin(t * 2f + seed) * size * 0.03f
            val tl = Offset(cx - size * 0.3f, y - size * 0.08f)
            drawOval(Color(0x55FFF59D), tl - Offset(size * 0.05f, size * 0.04f), Size(size * 0.7f, size * 0.24f), style = Stroke(size * 0.08f))
            drawOval(Color(0xFFFFD54F), tl, Size(size * 0.6f, size * 0.16f), style = Stroke(size * 0.04f))
        }
        Accessory.TOPHAT -> if (!behind) {
            val base = Offset(cx - size * 0.06f, hatY)
            rotate(-8f, base) {
                drawRoundRect(Color(0xFF212121), Offset(base.x - size * 0.28f, base.y - size * 0.05f), Size(size * 0.56f, size * 0.08f), CornerRadius(size * 0.04f))
                drawRect(Color(0xFF212121), Offset(base.x - size * 0.18f, base.y - size * 0.42f), Size(size * 0.36f, size * 0.38f))
                drawRect(Color(0xFFD32F2F), Offset(base.x - size * 0.18f, base.y - size * 0.14f), Size(size * 0.36f, size * 0.08f))
                drawLine(Color.White.copy(alpha = 0.25f), Offset(base.x - size * 0.12f, base.y - size * 0.4f), Offset(base.x - size * 0.12f, base.y - size * 0.16f), sw)
            }
        }
        Accessory.CROWN -> if (!behind) {
            val by = hatY
            val w = size * 0.5f
            val h = size * 0.26f
            val path = Path().apply {
                moveTo(cx - w / 2, by)
                lineTo(cx - w / 2, by - h * 0.7f)
                lineTo(cx - w / 4, by - h * 0.35f)
                lineTo(cx, by - h)
                lineTo(cx + w / 4, by - h * 0.35f)
                lineTo(cx + w / 2, by - h * 0.7f)
                lineTo(cx + w / 2, by)
                close()
            }
            drawPath(path, Brush.verticalGradient(listOf(Color(0xFFFFF176), Color(0xFFFFA000)), by - h, by))
            drawPath(path, Color(0xFFE65100), style = Stroke(sw))
            listOf(Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047)).forEachIndexed { i, col ->
                drawCircle(col, size * 0.035f, Offset(cx + (i - 1) * w * 0.3f, by - h * 0.18f))
            }
            // きらり
            val tw = ((t * 0.6f + seed % 4) % 2f)
            if (tw < 0.4f) drawStar(Offset(cx + w * 0.3f, by - h * 0.9f), size * 0.08f * sin(tw / 0.4f * PI.toFloat()), Color.White, null, unit)
        }
        Accessory.PARTY -> if (!behind) {
            val base = Offset(cx + size * 0.06f, hatY)
            rotate(12f, base) {
                val path = Path().apply {
                    moveTo(base.x - size * 0.19f, base.y)
                    lineTo(base.x, base.y - size * 0.5f)
                    lineTo(base.x + size * 0.19f, base.y)
                    close()
                }
                drawPath(path, Brush.linearGradient(listOf(Color(0xFFAB47BC), Color(0xFF29B6F6)), Offset(base.x - size * 0.2f, base.y), Offset(base.x + size * 0.2f, base.y - size * 0.5f)))
                drawCircle(Color(0xFFFFEB3B), size * 0.035f, Offset(base.x - size * 0.06f, base.y - size * 0.12f))
                drawCircle(Color(0xFFFFEB3B), size * 0.03f, Offset(base.x + size * 0.05f, base.y - size * 0.26f))
                drawCircle(Color(0xFFFF4081), size * 0.06f, Offset(base.x, base.y - size * 0.52f))
            }
        }
        Accessory.FLOWER -> if (!behind) {
            val c = Offset(cx - size * 0.44f, top + size * 0.06f)
            val pr = size * 0.07f
            for (i in 0 until 5) {
                val ang = (i * 72f + t * 10f) * PI.toFloat() / 180f
                drawCircle(Color(0xFFF8BBD0), pr, Offset(c.x + cos(ang) * pr * 1.1f, c.y + sin(ang) * pr * 1.1f))
            }
            drawCircle(Color(0xFFFFD54F), pr * 0.75f, c)
        }
        Accessory.RIBBON -> if (!behind) {
            val c = Offset(cx + size * 0.44f, top + size * 0.06f)
            val s = size * 0.14f * (1f + 0.05f * sin(t * 3f + seed))
            for (side in intArrayOf(-1, 1)) {
                val bow = Path().apply {
                    moveTo(c.x, c.y)
                    lineTo(c.x + side * s * 1.2f, c.y - s * 0.7f)
                    lineTo(c.x + side * s * 1.2f, c.y + s * 0.7f)
                    close()
                }
                drawPath(bow, Color(0xFFFF5C8A))
                drawPath(bow, Color(0xFFC2185B), style = Stroke(sw))
            }
            drawCircle(Color(0xFFC2185B), s * 0.32f, c)
        }
        Accessory.BEANIE -> if (!behind) {
            val by = hatY + size * 0.02f
            val tl = Offset(cx - size * 0.42f, by - size * 0.36f)
            drawArc(Color(0xFF26A69A), 180f, 180f, true, tl, Size(size * 0.84f, size * 0.72f))
            for (i in 1..3) drawLine(Color.White.copy(alpha = 0.5f), Offset(cx - size * 0.42f + i * size * 0.21f, by - size * 0.3f), Offset(cx - size * 0.42f + i * size * 0.21f, by), sw)
            drawRoundRect(Color(0xFF00796B), Offset(cx - size * 0.44f, by - size * 0.06f), Size(size * 0.88f, size * 0.1f), CornerRadius(size * 0.04f))
            drawCircle(Color.White, size * 0.08f, Offset(cx, by - size * 0.38f))
        }
    }
}

// ─────────────────────────────────────────────
// 足
// ─────────────────────────────────────────────

fun legColor(look: PetLook, themeLeg: Color, tint: Color, index: Int, t: Float): Color = when (look.legColor) {
    LegColor.THEME, LegColor.MIXED -> themeLeg
    LegColor.ICON -> lerp(tint, Color.Black, 0.35f)
    LegColor.RAINBOW -> Color.hsv(((t * 60f + index * 40f) % 360f + 360f) % 360f, 0.7f, 0.95f)
}

/** 足 1 本（付け根 → 膝 → 足先） */
fun DrawScope.drawLeg(style: LegStyle, hip: Offset, knee: Offset, foot: Offset, r: Float, color: Color, glow: Color?, unit: Float) {
    val thick = if (style == LegStyle.STUBBY) 1.9f else if (style == LegStyle.ROBOT) 1.3f else 1f
    val thigh = r * 0.15f * thick
    val shin = r * 0.1f * thick
    glow?.let { g ->
        drawLine(g.copy(alpha = 0.35f), hip, knee, thigh * 2.2f, StrokeCap.Round)
        drawLine(g.copy(alpha = 0.35f), knee, foot, shin * 2.2f, StrokeCap.Round)
    }
    when (style) {
        LegStyle.ROBOT -> {
            val dark = Color(0xFF37474F)
            val metal = lerp(Color(0xFFB0BEC5), color, 0.25f)
            drawLine(dark, hip, knee, thigh * 1.4f, StrokeCap.Butt)
            drawLine(metal, hip, knee, thigh * 0.8f, StrokeCap.Butt)
            drawLine(dark, knee, foot, shin * 1.4f, StrokeCap.Butt)
            drawLine(metal, knee, foot, shin * 0.8f, StrokeCap.Butt)
            drawCircle(dark, thigh * 0.85f, knee)
            drawCircle(Color(0xFFFFCA28), thigh * 0.35f, knee)
            drawCircle(dark, thigh * 0.7f, hip)
            // 爪
            val d = foot - knee
            val len = d.getDistance().coerceAtLeast(1f)
            val n = Offset(-d.y / len, d.x / len) * (shin * 1.4f)
            drawLine(dark, foot, foot + n + d / len * shin, unit * 2f, StrokeCap.Round)
            drawLine(dark, foot, foot - n + d / len * shin, unit * 2f, StrokeCap.Round)
        }
        LegStyle.CANDY -> {
            drawLine(color, hip, knee, thigh, StrokeCap.Round)
            drawLine(color, knee, foot, shin, StrokeCap.Round)
            val dash = PathEffect.dashPathEffect(floatArrayOf(thigh * 0.9f, thigh * 0.9f))
            drawLine(Color.White.copy(alpha = 0.85f), hip, knee, thigh * 0.95f, StrokeCap.Butt, dash)
            drawLine(Color.White.copy(alpha = 0.85f), knee, foot, shin * 0.95f, StrokeCap.Butt, PathEffect.dashPathEffect(floatArrayOf(shin * 1.2f, shin * 1.2f)))
            drawCircle(color, thigh * 0.6f, knee)
            drawCircle(Color.White, shin * 0.7f, foot)
        }
        LegStyle.STUBBY -> {
            drawLine(color, hip, knee, thigh, StrokeCap.Round)
            drawLine(color, knee, foot, shin, StrokeCap.Round)
            drawCircle(lerp(color, Color.Black, 0.3f), shin * 0.9f, foot)
            drawCircle(Color.White.copy(alpha = 0.3f), shin * 0.35f, foot - Offset(shin * 0.3f, shin * 0.3f))
        }
        LegStyle.HAIRY -> {
            drawLine(color, hip, knee, thigh * 1.1f, StrokeCap.Round)
            drawLine(color, knee, foot, shin * 1.1f, StrokeCap.Round)
            for ((a, b) in listOf(hip to knee, knee to foot)) {
                val d = b - a
                for (k in 1..3) {
                    val m = a + d * (k / 4f)
                    val n = Offset(-d.y, d.x) * 0.14f
                    drawLine(color, m - n, m + n * 0.4f + d * 0.08f, unit * 1.2f, StrokeCap.Round)
                    drawLine(color, m + n, m - n * 0.4f + d * 0.08f, unit * 1.2f, StrokeCap.Round)
                }
            }
            drawCircle(color, thigh * 0.6f, knee)
        }
        LegStyle.THIN, LegStyle.MIXED -> {
            drawLine(color, hip, knee, thigh, StrokeCap.Round)
            drawLine(color, knee, foot, shin, StrokeCap.Round)
            drawCircle(lerp(color, Color.Black, 0.3f), thigh * 0.55f, knee)
            drawCircle(lerp(color, Color.Black, 0.3f), shin * 0.6f, foot)
        }
    }
}
