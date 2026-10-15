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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import com.wonderful.launcher.data.PetLook
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 * 「体内」テーマ：アプリが T2 ファージかマクロファージの姿になって、大腸菌を食べる。
 */

private fun fr(x: Float) = x - floor(x)

// ─────────────────────────────────────────────
// 背景：血管の中
// ─────────────────────────────────────────────

fun DrawScope.drawBodyBackground(t: Float, unit: Float) {
    val w = size.width
    val h = size.height
    drawRect(Brush.radialGradient(listOf(Color(0xFFB23A48), Color(0xFF7A1C2C), Color(0xFF3E0A16)), Offset(w * 0.5f, h * 0.5f), max(w, h) * 0.7f))
    // 血の流れ（ゆっくり右へ）
    for (k in 0 until 6) {
        val y = h * (0.12f + k * 0.16f) + sin(t * 0.4f + k) * unit * 10f
        val x0 = fr(t * 0.03f + k * 0.27f) * (w + unit * 400f) - unit * 400f
        drawRoundRect(Color.White.copy(alpha = 0.04f), Offset(x0, y), Size(unit * 380f, unit * 18f), CornerRadius(unit * 9f))
    }
    // 奥を流れる赤血球
    for (i in 0 until 10) {
        val sp = 0.025f + (i % 4) * 0.008f
        val x = fr(t * sp + i * 0.137f) * (w + unit * 120f) - unit * 60f
        val y = h * fr(i * 0.618f + 0.1f) + sin(t * 0.7f + i) * unit * 12f
        val rr = unit * (14f + (i % 3) * 5f)
        val squash = 0.55f + 0.45f * abs(sin(t * 0.5f + i))
        rotate((t * 20f + i * 40f) % 360f, Offset(x, y)) {
            drawOval(Color(0xFFD32F2F).copy(alpha = 0.35f), Offset(x - rr, y - rr * squash), Size(rr * 2, rr * 2 * squash))
            drawOval(Color(0xFF8E1B1B).copy(alpha = 0.3f), Offset(x - rr * 0.45f, y - rr * 0.45f * squash), Size(rr * 0.9f, rr * 0.9f * squash))
        }
    }
    // 血小板・小さな粒
    for (i in 0 until 26) {
        val x = fr(t * (0.02f + (i % 5) * 0.006f) + i * 0.381966f) * w
        val y = h * fr(i * 0.754877f + 0.33f) + sin(t + i) * unit * 6f
        drawCircle(Color(0xFFFFCDD2).copy(alpha = 0.25f), unit * (1.5f + i % 3), Offset(x, y))
    }
    // 上と下の壁の柔毛（ゆらゆら）
    for (edge in 0..1) {
        val baseY = if (edge == 0) 0f else h
        val dir = if (edge == 0) 1f else -1f
        var x = -unit * 10f
        var i = 0
        while (x < w + unit * 20f) {
            val len = unit * (26f + (i * 37 % 5) * 6f)
            val sway = sin(t * 1.1f + i * 0.7f) * unit * 6f
            val path = Path().apply {
                moveTo(x - unit * 9f, baseY)
                quadraticTo(x - unit * 10f + sway * 0.5f, baseY + dir * len * 0.6f, x + sway, baseY + dir * len)
                quadraticTo(x + unit * 10f + sway * 0.5f, baseY + dir * len * 0.6f, x + unit * 9f, baseY)
                close()
            }
            drawPath(path, Color(0xFFE57373))
            drawPath(path, Color(0xFFFFCDD2).copy(alpha = 0.45f), style = Stroke(unit * 1.2f))
            x += unit * 20f
            i++
        }
        drawRect(Color(0xFFC62828), Offset(0f, if (edge == 0) 0f else h - unit * 6f), Size(w, unit * 6f))
    }
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x66200008)), Offset(w / 2, h / 2), max(w, h) * 0.75f))
}

// ─────────────────────────────────────────────
// 大腸菌（エサ）
// ─────────────────────────────────────────────

fun DrawScope.drawEColi(f: Food, sim: PetSim, t: Float, unit: Float) {
    val r = sim.radius
    // 食べられるほど縮む（ファージに感染された時は少しふくらんでから破裂）
    val k = f.amount.coerceIn(0f, 1f)
    val swell = if (f.captured && !sim.legless) 1f + 0.25f * (1f - k) else 0.35f + 0.65f * k
    val len = r * 0.95f * swell
    val wid = r * 0.42f * swell
    val deg = f.heading * 180f / PI.toFloat()
    val speed = hypot(f.vx, f.vy) / max(r, 1f)
    drawOval(Color(0x22000000), Offset(f.x - len * 0.5f, f.y + wid * 0.4f), Size(len, wid * 0.6f))
    rotate(deg, Offset(f.x, f.y)) {
        // べん毛（後ろへ波打つ）
        for (i in 0 until 5) {
            val sy = f.y + (i - 2) * wid * 0.22f
            val path = Path()
            path.moveTo(f.x - len * 0.42f, sy)
            var s = 0f
            while (s <= 1f) {
                val px = f.x - len * 0.42f - s * len * 0.9f
                val py = sy + sin(s * 9f - t * (8f + speed * 10f) + i) * wid * 0.28f * s + (i - 2) * s * wid * 0.25f
                path.lineTo(px, py)
                s += 0.1f
            }
            drawPath(path, Color(0xFFDCEDC8).copy(alpha = 0.7f * k + 0.2f), style = Stroke(unit * 1.1f, cap = StrokeCap.Round))
        }
        val tl = Offset(f.x - len / 2, f.y - wid / 2)
        val danger = if (f.captured && !sim.legless) 1f - k else 0f
        val body = lerp(Color(0xFF8BC34A), Color(0xFFFF7043), danger)
        drawRoundRect(Brush.verticalGradient(listOf(lerp(body, Color.White, 0.3f), body, lerp(body, Color.Black, 0.25f)), tl.y, tl.y + wid), tl, Size(len, wid), CornerRadius(wid / 2))
        drawRoundRect(lerp(body, Color.Black, 0.45f), tl, Size(len, wid), CornerRadius(wid / 2), style = Stroke(unit * 1.4f))
        // 線毛
        for (i in 0 until 8) {
            val px = tl.x + len * (0.15f + i * 0.1f)
            drawLine(lerp(body, Color.Black, 0.3f), Offset(px, tl.y), Offset(px + unit, tl.y - unit * 3f), unit * 0.8f)
            drawLine(lerp(body, Color.Black, 0.3f), Offset(px, tl.y + wid), Offset(px + unit, tl.y + wid + unit * 3f), unit * 0.8f)
        }
        // 中の DNA（くねくね）
        val dna = Path()
        var s = 0f
        dna.moveTo(f.x - len * 0.3f, f.y)
        while (s <= 1f) { dna.lineTo(f.x - len * 0.3f + s * len * 0.6f, f.y + sin(s * 14f + t * 2f) * wid * 0.15f); s += 0.08f }
        drawPath(dna, Color(0xFF33691E).copy(alpha = 0.6f), style = Stroke(unit * 1.2f))
        // 小さな目（逃げる時はびっくり顔）
        val eyeR = wid * 0.16f
        for (side in intArrayOf(-1, 1)) {
            val ec = Offset(f.x + len * 0.3f, f.y + side * wid * 0.18f)
            drawCircle(Color.White, eyeR * (if (speed > 0.6f) 1.3f else 1f), ec)
            drawCircle(Color(0xFF212121), eyeR * 0.55f, ec + Offset(eyeR * 0.2f, 0f))
        }
    }
}

// ─────────────────────────────────────────────
// T2 ファージ
// ─────────────────────────────────────────────

fun DrawScope.drawPhage(p: Pet, sim: PetSim, icon: ImageBitmap?, tint: Color, look: PetLook, colors: PetColors, t: Float, unit: Float) {
    val r = sim.radius
    val air = (p.lift * 0.8f + p.hop * 0.55f) * r
    val sleeping = p.mood == Mood.SLEEP
    // 感染中は尾が縮んで頭が下がる（ドクン、ドクン）
    val injecting = p.mood == Mood.EAT
    val contract = if (injecting) 0.45f + 0.15f * sin(t * 10f) else 0f
    val tailLen = r * 0.55f * (1f - contract)
    val baseY = p.y + r * 0.45f
    val tailTop = baseY - tailLen
    val headH = r * 1.75f * (1f + 0.04f * p.stretch)
    val headW = r * 1.4f
    val headCy = tailTop - r * 0.08f - headH / 2 + (if (sleeping) r * 0.1f else 0f)

    // 影
    drawOval(colors.shadow.copy(alpha = colors.shadow.alpha * (1f - (p.lift + p.hop) * 0.3f).coerceAtLeast(0.2f)), Offset(p.x - r * 1.1f, p.y + r * 0.45f), Size(r * 2.2f, r * 0.55f))

    translate(0f, -air) {
        // 尾繊維（足）：基盤から足先へ、膝は上向きに折れる
        val l1 = r * 0.95f
        val l2 = r * 1.05f
        p.legs.forEachIndexed { i, l ->
            val col = legColor(look, Color(0xFFE0F7FA), tint, i, t)
            val hip = Offset(p.x + l.side * r * 0.18f, baseY + r * 0.05f)
            val foot = Offset(l.footX, l.footY + r * 0.35f)
            val dx = foot.x - hip.x
            val dy = foot.y - hip.y
            val d = hypot(dx, dy).coerceIn(abs(l1 - l2) + 0.01f, l1 + l2 - 0.01f)
            val a = acos(((l1 * l1 + d * d - l2 * l2) / (2 * l1 * d)).coerceIn(-1f, 1f))
            val base = atan2(dy, dx)
            // 2 つの解のうち、膝が上（y が小さい）になる方
            val k1 = Offset(hip.x + cos(base + a) * l1, hip.y + sin(base + a) * l1)
            val k2 = Offset(hip.x + cos(base - a) * l1, hip.y + sin(base - a) * l1)
            val knee = (if (k1.y < k2.y) k1 else k2) - Offset(0f, l.lift * r * 0.2f)
            drawLine(col.copy(alpha = 0.35f), hip, knee, r * 0.12f, StrokeCap.Round)
            drawLine(col, hip, knee, r * 0.055f, StrokeCap.Round)
            drawLine(col, knee, foot, r * 0.045f, StrokeCap.Round)
            drawCircle(col, r * 0.05f, knee)
            drawCircle(lerp(col, Color.Black, 0.3f), r * 0.04f, foot)
        }
        // 基盤（六角形の板）
        val plate = hexagon(Offset(p.x, baseY), r * 0.32f, r * 0.11f)
        drawPath(plate, Color(0xFF80CBC4))
        drawPath(plate, Color(0xFF004D40), style = Stroke(unit * 1.4f))
        // 尾（しましまの鞘）
        val sheathW = r * 0.3f * (1f + contract * 0.6f)
        drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF4DB6AC), Color(0xFFB2DFDB), Color(0xFF4DB6AC)), p.x - sheathW / 2, p.x + sheathW / 2), Offset(p.x - sheathW / 2, tailTop), Size(sheathW, tailLen), CornerRadius(unit * 2f))
        val rings = 6
        for (i in 1 until rings) {
            val y = tailTop + tailLen * i / rings
            drawLine(Color(0xFF00695C), Offset(p.x - sheathW / 2, y), Offset(p.x + sheathW / 2, y), unit * 1.2f)
        }
        // 襟
        drawRoundRect(Color(0xFF26A69A), Offset(p.x - r * 0.24f, tailTop - r * 0.1f), Size(r * 0.48f, r * 0.12f), CornerRadius(unit * 2f))

        // 頭（細長い正二十面体）の中にアプリのアイコン
        val hc = Offset(p.x, headCy)
        val joy = p.joy
        val sx = 1f + 0.1f * joy * sin(t * 22f)
        val sy = 1f - 0.1f * joy * sin(t * 22f)
        val tilt = if (p.emote == Emote.CONFUSED) 12f else if (p.emote == Emote.ANGRY) sin(t * 40f) * 4f else (p.vx / (r * 3f)).coerceIn(-1f, 1f) * 8f
        withTransform({
            rotate(tilt, Offset(p.x, baseY))
            scale(sx, sy, Offset(p.x, tailTop))
        }) {
            val head = elongatedHex(hc, headW, headH)
            drawPath(head, Brush.linearGradient(listOf(Color(0xFFB39DDB), Color(0xFF7E57C2), Color(0xFF4527A0)), Offset(hc.x - headW / 2, hc.y - headH / 2), Offset(hc.x + headW / 2, hc.y + headH / 2)))
            clipPath(head) {
                if (icon != null) image(icon, hc.x, hc.y + headH * 0.06f, headW * 0.92f, headW * 0.92f, alpha = if (sleeping) 0.8f else 0.95f)
                // 面の線
                val pts = hexPoints(hc, headW, headH)
                pts.forEach { pt -> drawLine(Color.White.copy(alpha = 0.25f), hc, pt, unit * 1f) }
                drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.3f), Color.Transparent), Offset(hc.x - headW / 2, hc.y - headH / 2), hc), Offset(hc.x - headW / 2, hc.y - headH / 2), Size(headW, headH))
            }
            drawPath(head, Color(0xFF311B92), style = Stroke(unit * 2f))
            // 顔と帽子
            val er = headW / 1.9f
            val eyeC = Offset(hc.x, hc.y - headH * 0.12f)
            val eyes = expressiveEyes(look.eyes, p.emote)
            val eyeScale = if (p.emote == Emote.SURPRISED) 1.35f else 1f
            drawEyePair(eyes, eyeC, er, Offset(p.gazeX, p.gazeY), p.blink, sleeping, joy, t, unit, eyeScale, p.emote)
            if (!sleeping) drawExpression(p.emote, eyeC, er, hc.x, hc.y, headW, t, unit, pair = eyes != com.wonderful.launcher.data.EyeStyle.CYCLOPS)
            else drawNoseBubble(Offset(hc.x + headW * 0.3f, hc.y + headH * 0.05f), er, t, unit, p.seed)
            val hatY = min(eyeTop(eyes, eyeC.y, er, eyeScale), hc.y - headH / 2) - headW * 0.03f
            drawAccessory(look.accessory, hc.x, hc.y, headW, t, unit, p.seed, behind = true, hatY = hatY)
            drawAccessory(look.accessory, hc.x, hc.y, headW, t, unit, p.seed, behind = false, hatY = hatY)
        }

        // DNA を注入している線
        if (injecting) {
            val f = sim.food.firstOrNull { it.id == p.targetFood }
            if (f != null) {
                val from = Offset(p.x, baseY + r * 0.1f)
                val to = Offset(f.x, f.y + air)
                drawLine(Color(0xFFFFEB3B).copy(alpha = 0.7f), from, to, unit * 2.5f, StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(unit * 5f, unit * 4f), -t * 60f))
            }
        }
    }
}

private fun hexPoints(c: Offset, w: Float, h: Float): List<Offset> = listOf(
    Offset(c.x, c.y - h / 2),
    Offset(c.x + w / 2, c.y - h / 4),
    Offset(c.x + w / 2, c.y + h / 4),
    Offset(c.x, c.y + h / 2),
    Offset(c.x - w / 2, c.y + h / 4),
    Offset(c.x - w / 2, c.y - h / 4),
)

private fun elongatedHex(c: Offset, w: Float, h: Float): Path = Path().apply {
    val pts = hexPoints(c, w, h)
    moveTo(pts[0].x, pts[0].y)
    pts.drop(1).forEach { lineTo(it.x, it.y) }
    close()
}

/** 横長の六角形（基盤） */
private fun hexagon(c: Offset, hw: Float, hh: Float): Path = Path().apply {
    moveTo(c.x - hw, c.y)
    lineTo(c.x - hw * 0.6f, c.y - hh)
    lineTo(c.x + hw * 0.6f, c.y - hh)
    lineTo(c.x + hw, c.y)
    lineTo(c.x + hw * 0.6f, c.y + hh)
    lineTo(c.x - hw * 0.6f, c.y + hh)
    close()
}

// ─────────────────────────────────────────────
// マクロファージ
// ─────────────────────────────────────────────

fun DrawScope.drawMacrophage(p: Pet, sim: PetSim, icon: ImageBitmap?, tint: Color, look: PetLook, colors: PetColors, t: Float, unit: Float) {
    val r = sim.radius
    val air = (p.lift * 0.8f + p.hop * 0.45f) * r
    val sleeping = p.mood == Mood.SLEEP
    val big = r * 1.3f * (if (sleeping) 0.95f else 1f) * (1f + 0.06f * p.stretch)
    // 仮足を伸ばす向き：エサ・相手・進む方向
    val f = sim.food.firstOrNull { it.id == p.targetFood }
    val dir = when {
        f != null -> atan2(f.y - p.y, f.x - p.x)
        p.speed > r * 0.2f -> atan2(p.vy, p.vx)
        else -> p.heading
    }
    val reach = when {
        p.mood == Mood.EAT -> 0.75f
        p.mood == Mood.SEEK_FOOD -> 0.6f
        p.speed > r * 0.2f -> 0.35f
        else -> 0.1f
    }
    val n = 32
    val pts = ArrayList<Offset>(n)
    for (i in 0 until n) {
        val th = i * 2f * PI.toFloat() / n
        var rad = big * (1f + 0.09f * sin(3f * th + t * 1.7f + p.seed) + 0.06f * sin(5f * th - t * 1.1f + p.seed * 0.3f))
        val c = cos(th - dir)
        if (c > 0f) rad += big * reach * c.pow(6)
        // 食べている時は大腸菌を包むように左右からも伸ばす
        if (p.mood == Mood.EAT) {
            val c2 = cos(th - dir - 0.7f).coerceAtLeast(0f).pow(8) + cos(th - dir + 0.7f).coerceAtLeast(0f).pow(8)
            rad += big * 0.35f * c2 * (0.8f + 0.2f * sin(t * 6f))
        }
        pts += Offset(p.x + cos(th) * rad, p.y - air + sin(th) * rad * 0.92f)
    }
    val blob = Path().apply {
        // 点の中点をつないでなめらかに
        val m0 = (pts[n - 1] + pts[0]) / 2f
        moveTo(m0.x, m0.y)
        for (i in 0 until n) {
            val a = pts[i]
            val b = pts[(i + 1) % n]
            val m = (a + b) / 2f
            quadraticTo(a.x, a.y, m.x, m.y)
        }
        close()
    }
    drawOval(colors.shadow.copy(alpha = 0.18f), Offset(p.x - big * 1.1f, p.y + big * 0.55f), Size(big * 2.2f, big * 0.5f))
    drawPath(blob, Brush.radialGradient(listOf(Color(0xEEFCE4EC), Color(0xDDF8BBD0), Color(0xCCCE93D8)), Offset(p.x, p.y - air), big * 1.6f))
    // 中の粒（リソソーム）
    for (i in 0 until 14) {
        val a = i * 2.39996f + t * 0.2f
        val d = big * (0.45f + 0.4f * fr(i * 0.618f))
        drawCircle(Color(0xFFAB47BC).copy(alpha = 0.35f), r * (0.05f + (i % 3) * 0.02f), Offset(p.x + cos(a) * d, p.y - air + sin(a) * d * 0.9f))
    }
    drawPath(blob, Color(0xFF8E24AA).copy(alpha = 0.8f), style = Stroke(unit * 2.5f))
    drawPath(blob, Color.White.copy(alpha = 0.35f), style = Stroke(unit * 1f))

    // 核（アプリのアイコン）
    val size = r * 1.35f
    val cy = p.y - air - r * 0.05f
    val joy = p.joy
    withTransform({
        scale(1f + 0.08f * joy * sin(t * 22f), 1f - 0.08f * joy * sin(t * 22f), Offset(p.x, cy))
    }) {
        drawCircle(Color(0xFF7B1FA2).copy(alpha = 0.25f), size * 0.62f, Offset(p.x, cy))
        drawCritterHead(p.x, cy, size, icon, colors.accent, look, p.blink, sleeping, joy, Offset(p.gazeX, p.gazeY), t, unit, p.seed, p.emote)
    }
}

/** 子ファージ（大腸菌から飛び出す小さなファージ） */
fun DrawScope.drawPhagelet(q: Particle, r: Float, unit: Float) {
    val fade = 1f - q.t
    val s = r * 0.22f * q.size
    val c = Offset(q.x, q.y)
    rotate(q.age * 400f + q.size * 90f, c) {
        drawPath(elongatedHex(c, s, s * 1.2f), Color(0xFF9575CD).copy(alpha = fade))
        drawLine(Color(0xFFB2DFDB).copy(alpha = fade), Offset(c.x, c.y + s * 0.6f), Offset(c.x, c.y + s * 1.3f), unit * 1.5f)
        for (side in intArrayOf(-1, 1)) drawLine(Color(0xFFE0F7FA).copy(alpha = fade), Offset(c.x, c.y + s * 1.3f), Offset(c.x + side * s * 0.6f, c.y + s * 1.8f), unit)
    }
}

/** 赤血球の家具 */
fun DrawScope.drawBloodCell(c: Offset, rr: Float, unit: Float) {
    drawCircle(Brush.radialGradient(listOf(Color(0xFFB71C1C), Color(0xFFE53935), Color(0xFFEF5350)), c, rr), rr, c)
    drawCircle(Color(0xFF8E1B1B).copy(alpha = 0.45f), rr * 0.45f, c)
    drawCircle(Color(0xFF7F0000), rr, c, style = Stroke(unit * 1.5f))
    drawArc(Color.White.copy(alpha = 0.35f), 200f, 70f, false, Offset(c.x - rr * 0.8f, c.y - rr * 0.8f), Size(rr * 1.6f, rr * 1.6f), style = Stroke(unit * 2f, cap = StrokeCap.Round))
}
