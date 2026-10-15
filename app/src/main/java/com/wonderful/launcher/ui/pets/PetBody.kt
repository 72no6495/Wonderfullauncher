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

fun DrawScope.drawBodyBackground(sim: PetSim, t: Float, unit: Float) {
    val w = size.width
    val h = size.height
    // 粘膜の地の色（収縮しているところは濃く）
    drawRect(Brush.radialGradient(listOf(Color(0xFFD9707F), Color(0xFFB04556), Color(0xFF6E1E2E)), Offset(w * 0.5f, h * 0.5f), max(w, h) * 0.75f))
    // ぜん動の波：各位置の「縮み具合」と、波に押されてずれる量
    val lambda = max(1f, sim.right - sim.left) / 1.6f
    fun squeeze(x: Float) = sim.gutWave(x)
    fun shift(x: Float) = -sin(2f * PI.toFloat() * ((x - sim.left) / lambda - sim.time * 0.22f) + PI.toFloat() / 2f) * unit * 14f

    // 収縮の帯（暗く陰になる）
    var x = 0f
    val step = unit * 12f
    while (x < w) {
        val q = squeeze(x)
        if (q > 0.05f) drawRect(Color(0xFF4A0E1C).copy(alpha = 0.28f * q), Offset(x, 0f), Size(step + 1f, h))
        x += step
    }

    // 輪状ヒダ（腸の内側をぐるっと一周する大きなひだ）。
    // 横から見ると、上下の壁から壁へ三日月形にかかる。ぜん動の波で寄り集まり、縮んだ所では太く高くなる
    val folds = 7
    for (i in 0 until folds) {
        val bx = w * (i + 0.5f) / folds
        val q = squeeze(bx)
        val fx = bx + shift(bx) * 2.4f
        // 流れる向きに少しふくらむ三日月
        val bulge = unit * (26f + 18f * q) * (if (i % 2 == 0) 1f else 0.8f)
        val thick = unit * (20f + 16f * q)
        val crest = Path()
        val shadow = Path()
        val light = Path()
        var y = -unit * 10f
        var first = true
        while (y <= h + unit * 10f) {
            val v = (y - h / 2) / (h / 2)
            val xx = fx + bulge * (1f - v * v) + sin(y / (unit * 45f) + i * 1.3f + t * 0.5f) * unit * 4f
            if (first) {
                crest.moveTo(xx, y); shadow.moveTo(xx - thick * 0.45f, y); light.moveTo(xx + thick * 0.25f, y)
                first = false
            } else {
                crest.lineTo(xx, y); shadow.lineTo(xx - thick * 0.45f, y); light.lineTo(xx + thick * 0.25f, y)
            }
            y += unit * 10f
        }
        // 影 → ひだの本体 → 光の当たる稜線、で立体的に
        drawPath(shadow, Color(0xFF4A0E1C).copy(alpha = 0.45f + 0.2f * q), style = Stroke(thick * 0.9f, cap = StrokeCap.Round))
        drawPath(crest, lerp(Color(0xFFE88C9A), Color(0xFFC24A60), q * 0.7f), style = Stroke(thick, cap = StrokeCap.Round))
        drawPath(crest, Color(0xFFD06A7C).copy(alpha = 0.6f), style = Stroke(thick * 0.55f, cap = StrokeCap.Round))
        drawPath(light, Color(0xFFFFD3DA).copy(alpha = 0.55f), style = Stroke(unit * 3f, cap = StrokeCap.Round))
        // ひだの表面の細かいしわ
        drawPath(crest, Color(0xFF8E2236).copy(alpha = 0.25f), style = Stroke(unit * 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(unit * 6f, unit * 9f), t * unit * 4f)))
    }

    // 内容物・粘液の粒（波のところで速く流れる）
    for (i in 0 until 24) {
        val baseX = fr(t * (0.018f + (i % 5) * 0.004f) + i * 0.381966f) * (w + unit * 60f) - unit * 30f
        val px = baseX + squeeze(baseX) * unit * 30f
        val py = h * fr(i * 0.754877f + 0.33f) + sin(t + i) * unit * 6f
        drawCircle(Color(0xFFFFF3C4).copy(alpha = 0.22f), unit * (2f + i % 3), Offset(px, py))
    }

    // 上下の腸壁：縮んだところでぐっと迫り出し、長い絨毛がなびく
    for (edge in 0..1) {
        val dir = if (edge == 0) 1f else -1f
        val baseY = if (edge == 0) 0f else h
        val wall = Path()
        wall.moveTo(-unit * 10f, baseY)
        var wx = -unit * 10f
        while (wx <= w + unit * 10f) {
            val d = unit * (14f + 34f * squeeze(wx)) + sin(wx / (unit * 40f) + t) * unit * 3f
            wall.lineTo(wx, baseY + dir * d)
            wx += unit * 10f
        }
        wall.lineTo(w + unit * 10f, baseY)
        wall.close()
        drawPath(wall, Color(0xFF8E2236))
        drawPath(wall, Color(0xFFFFB3C0).copy(alpha = 0.35f), style = Stroke(unit * 1.5f))
        var fx = -unit * 10f
        var i = 0
        while (fx < w + unit * 20f) {
            val q = squeeze(fx)
            val rootY = baseY + dir * (unit * (14f + 34f * q))
            val len = unit * (22f + (i * 37 % 5) * 5f) * (1f - 0.35f * q)
            // 波の進む方向へなびく
            val lean = (shift(fx + unit * 20f) - shift(fx)) * 1.6f + sin(t * 1.8f + i * 0.6f) * unit * 4f
            val path = Path().apply {
                moveTo(fx - unit * 7f, rootY)
                quadraticTo(fx - unit * 8f + lean * 0.4f, rootY + dir * len * 0.6f, fx + lean, rootY + dir * len)
                quadraticTo(fx + unit * 8f + lean * 0.4f, rootY + dir * len * 0.6f, fx + unit * 7f, rootY)
                close()
            }
            drawPath(path, lerp(Color(0xFFEF8A9A), Color(0xFFC2425A), q))
            drawPath(path, Color(0xFFFFD6DD).copy(alpha = 0.4f), style = Stroke(unit * 1f))
            fx += unit * 16f
            i++
        }
    }
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x55200008)), Offset(w / 2, h / 2), max(w, h) * 0.75f))
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

// ─────────────────────────────────────────────
// 腸の中にある物（家具）
// ─────────────────────────────────────────────

/** 悪性腫瘍：ぼこぼこした塊。脈打ち、上ではねられるとぶよっとつぶれる（トランポリン） */
fun DrawScope.drawTumor(f: FurnitureBody, r: Float, t: Float, unit: Float) {
    val pulse = 1f + 0.05f * sin(t * 3.2f + f.fx)
    val hit = (f.active / 0.5f).coerceIn(0f, 1f)
    val c = Offset(f.x, f.y)
    val base = r * 1.05f * pulse
    // はねられた時のつぶれ（下を支点に）
    val squash = hit * hit
    val bottom = Offset(c.x, c.y + base)
    withTransform({ scale(1f + 0.18f * squash, 1f - 0.28f * squash, bottom) }) {
        drawTumorBody(c, base, t, unit, squash)
    }
}

private fun DrawScope.drawTumorBody(c: Offset, base: Float, t: Float, unit: Float, hit: Float) {
    // 栄養を取る血管
    for (i in 0 until 6) {
        val a = i * 1.047f + 0.3f
        val path = Path().apply {
            moveTo(c.x, c.y)
            quadraticTo(c.x + cos(a + 0.4f) * base * 1.2f, c.y + sin(a + 0.4f) * base * 1.2f, c.x + cos(a) * base * 1.9f, c.y + sin(a) * base * 1.9f)
        }
        drawPath(path, Color(0xFF7B1F2E).copy(alpha = 0.7f), style = Stroke(unit * 2.2f, cap = StrokeCap.Round))
    }
    // こぶの集まり
    val lumps = listOf(0f to 0f to 1f, 0.55f to -0.3f to 0.62f, -0.5f to -0.35f to 0.58f, 0.35f to 0.5f to 0.6f, -0.45f to 0.4f to 0.55f, 0.05f to -0.65f to 0.5f, 0.75f to 0.2f to 0.45f)
    lumps.forEachIndexed { i, (pos, s) ->
        val wob = 1f + 0.06f * sin(t * 2.3f + i * 1.9f)
        val o = Offset(c.x + pos.first * base, c.y + pos.second * base)
        val rr = base * 0.62f * s * wob
        drawCircle(Brush.radialGradient(listOf(Color(0xFFB0577A), Color(0xFF6A1B4D), Color(0xFF3E0F2B)), o - Offset(rr * 0.3f, rr * 0.3f), rr * 1.3f), rr, o)
        drawCircle(Color.White.copy(alpha = 0.18f), rr * 0.25f, o - Offset(rr * 0.35f, rr * 0.4f))
    }
    // 不気味な目（攻撃されるとひるむ）
    for (side in intArrayOf(-1, 1)) {
        val e = Offset(c.x + side * base * 0.22f, c.y - base * 0.1f)
        drawOval(Color(0xFFFFEB3B), Offset(e.x - base * 0.1f, e.y - base * 0.06f * (1f - hit * 0.8f)), Size(base * 0.2f, base * 0.12f * (1f - hit * 0.8f)))
        drawCircle(Color.Black, base * 0.035f, e)
    }
}

/** 食べ物（消化中のかたまり）：食べられると小さくなる */
fun DrawScope.drawFoodLump(f: FurnitureBody, r: Float, t: Float, unit: Float) {
    val k = 0.3f + 0.7f * f.state
    val c = Offset(f.x, f.y)
    val base = r * 0.95f * k
    drawOval(Color(0x33000000), Offset(c.x - base, c.y + base * 0.5f), Size(base * 2f, base * 0.6f))
    val blobs = listOf(-0.3f to 0f, 0.3f to 0.05f, 0f to -0.25f, -0.1f to 0.25f, 0.35f to -0.25f)
    blobs.forEachIndexed { i, (dx, dy) ->
        val wob = 1f + 0.04f * sin(t * 1.7f + i)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFE6C27A), Color(0xFFB5833E)), Offset(c.x + dx * base, c.y + dy * base), base * 0.7f), base * 0.58f * wob, Offset(c.x + dx * base, c.y + dy * base))
    }
    // 中に見える食べ物のかけら（コーン・葉っぱ・ごはん）
    val bits = listOf(Color(0xFFFFEB3B), Color(0xFF66BB6A), Color.White, Color(0xFFFF7043), Color(0xFFFFEB3B), Color.White)
    bits.forEachIndexed { i, col ->
        val a = i * 1.1f + 0.4f
        val o = Offset(c.x + cos(a) * base * 0.45f, c.y + sin(a) * base * 0.35f)
        drawOval(col, Offset(o.x - base * 0.09f, o.y - base * 0.06f), Size(base * 0.18f, base * 0.12f))
    }
    drawCircle(Color.White.copy(alpha = 0.2f), base * 0.25f, Offset(c.x - base * 0.35f, c.y - base * 0.35f))
}

/** 電池（飲み込まれた異物）：さびて汁が出ていて、つつくとビリッ */
fun DrawScope.drawBatteryItem(f: FurnitureBody, r: Float, t: Float, unit: Float) {
    val c = Offset(f.x, f.y)
    val len = r * 2.1f
    val wid = r * 0.85f
    val hit = (f.active / 0.5f).coerceIn(0f, 1f)
    // 漏れた液
    drawOval(Color(0x5581C784), Offset(c.x - len * 0.45f, c.y + wid * 0.1f), Size(len * 0.9f, wid * 0.7f))
    rotate(-18f, c) {
        val tl = Offset(c.x - len / 2, c.y - wid / 2)
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF616161), Color(0xFF212121), Color(0xFF424242)), tl.y, tl.y + wid), tl, Size(len * 0.78f, wid), CornerRadius(wid * 0.18f))
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFFCC80), Color(0xFFE65100), Color(0xFFFFB74D)), tl.y, tl.y + wid), Offset(tl.x + len * 0.72f, tl.y), Size(len * 0.24f, wid), CornerRadius(wid * 0.18f))
        drawRoundRect(Color(0xFFBDBDBD), Offset(tl.x + len * 0.96f, c.y - wid * 0.16f), Size(len * 0.06f, wid * 0.32f), CornerRadius(unit * 2f))
        drawLine(Color.White, Offset(tl.x + len * 0.15f, c.y), Offset(tl.x + len * 0.3f, c.y), unit * 2.5f)
        drawLine(Color.White, Offset(tl.x + len * 0.55f, c.y - wid * 0.12f), Offset(tl.x + len * 0.55f, c.y + wid * 0.12f), unit * 2.5f)
        drawLine(Color.White, Offset(tl.x + len * 0.48f, c.y), Offset(tl.x + len * 0.62f, c.y), unit * 2.5f)
        // さび
        for (i in 0 until 5) drawCircle(Color(0xFFD7E7C0).copy(alpha = 0.8f), wid * (0.08f + (i % 2) * 0.04f), Offset(tl.x + len * (0.05f + i * 0.17f), tl.y + wid * (0.15f + (i * 37 % 7) / 10f)))
    }
    if (hit > 0f) drawCircle(Brush.radialGradient(listOf(Color(0x88FFF176), Color.Transparent), c, r * 1.8f), r * 1.8f * hit, c)
}

/** コイン（転がる） */
fun DrawScope.drawCoin(f: FurnitureBody, r: Float, unit: Float) {
    val c = Offset(f.x, f.y)
    val rr = r * 0.9f
    // 転がっている時は縦につぶれて見える
    val sq = 0.55f + 0.45f * abs(cos(f.spin))
    drawOval(Color(0x33000000), Offset(c.x - rr, c.y + rr * 0.6f), Size(rr * 2f, rr * 0.5f))
    drawOval(Brush.radialGradient(listOf(Color(0xFFFFF59D), Color(0xFFFFC107), Color(0xFFB8860B)), c - Offset(rr * 0.3f, rr * 0.3f), rr * 1.4f), Offset(c.x - rr * sq, c.y - rr), Size(rr * 2f * sq, rr * 2f))
    drawOval(Color(0xFF8D6E00), Offset(c.x - rr * sq * 0.72f, c.y - rr * 0.72f), Size(rr * 1.44f * sq, rr * 1.44f), style = Stroke(unit * 1.5f))
    drawOval(Color(0xFF8D6E00), Offset(c.x - rr * sq, c.y - rr), Size(rr * 2f * sq, rr * 2f), style = Stroke(unit * 1.5f))
}

/** ビー玉（転がる） */
fun DrawScope.drawMarble(f: FurnitureBody, r: Float, t: Float, unit: Float) {
    val c = Offset(f.x, f.y)
    val rr = r * 0.75f
    drawOval(Color(0x33000000), Offset(c.x - rr, c.y + rr * 0.6f), Size(rr * 2f, rr * 0.45f))
    drawCircle(Color(0x8880DEEA), rr, c)
    rotate(f.spin * 57.3f, c) {
        val swirl = Path().apply {
            moveTo(c.x - rr * 0.7f, c.y)
            cubicTo(c.x - rr * 0.2f, c.y - rr * 0.8f, c.x + rr * 0.2f, c.y + rr * 0.8f, c.x + rr * 0.7f, c.y)
        }
        drawPath(swirl, Color(0xFFFF4081), style = Stroke(rr * 0.25f, cap = StrokeCap.Round))
        drawPath(swirl, Color(0xFFFFEB3B), style = Stroke(rr * 0.08f, cap = StrokeCap.Round))
    }
    drawCircle(Color.White.copy(alpha = 0.5f), rr, c, style = Stroke(unit * 1.2f))
    drawCircle(Color.White.copy(alpha = 0.8f), rr * 0.22f, Offset(c.x - rr * 0.35f, c.y - rr * 0.4f))
}
