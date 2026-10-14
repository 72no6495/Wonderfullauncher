package com.wonderful.launcher.ui.wallpaper

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.wonderful.launcher.data.LauncherSettings
import com.wonderful.launcher.ui.theme.RetroPalette
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 背景アニメーション用の経過時間（秒）。速度設定を掛け、パネル表示中などは止める。
 */
@Composable
fun rememberSceneTime(speed: Float, paused: Boolean): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    val currentSpeed by rememberUpdatedState(speed)
    val currentPaused by rememberUpdatedState(paused)
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
                last = now
                if (!currentPaused) time.floatValue += dt * currentSpeed
            }
        }
    }
    return time
}

private fun LauncherSettings.sceneSpeed() = (gridSpeed * animSpeed).coerceAtLeast(0.05f)

// ═════════════════════════════════════════════
// 水中
// ═════════════════════════════════════════════

private class Bubble(val x: Float, val phase: Float, val speed: Float, val size: Float)
private class Fish(val y: Float, val offset: Float, val speed: Float, val size: Float, val dir: Int, val colorIdx: Int)

@Composable
fun UnderwaterScene(s: LauncherSettings, p: RetroPalette, paused: Boolean, modifier: Modifier = Modifier) {
    val t by rememberSceneTime(s.sceneSpeed(), paused)
    val rnd = remember { Random(11) }
    val bubbles = remember { List(46) { Bubble(rnd.nextFloat(), rnd.nextFloat(), 0.04f + rnd.nextFloat() * 0.1f, 2f + rnd.nextFloat() * 8f) } }
    val fish = remember {
        List(6) { Fish(0.25f + rnd.nextFloat() * 0.5f, rnd.nextFloat() * 1.4f, 0.025f + rnd.nextFloat() * 0.04f, 14f + rnd.nextFloat() * 22f, if (it % 2 == 0) 1 else -1, it) }
    }
    val plankton = remember { List(80) { Triple(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat()) } }
    val weeds = remember { List(11) { Triple(rnd.nextFloat(), 0.1f + rnd.nextFloat() * 0.2f, rnd.nextFloat()) } }

    Canvas(modifier.fillMaxSize()) {
        val time = t
        val w = size.width
        val h = size.height
        // 水の色（上ほど明るい）
        drawRect(
            Brush.verticalGradient(
                0f to lerp(Color(0xFF29C3D6), p.cyan, 0.3f),
                0.3f to Color(0xFF0A6C8A),
                0.65f to Color(0xFF052A47),
                1f to Color(0xFF010812),
            ),
        )
        // 光の筋
        for (i in 0 until 6) {
            val cx = w * (i + 0.5f) / 6f + sin(time * 0.25f + i * 1.7f) * w * 0.08f
            val topW = w * 0.05f
            val botW = w * 0.22f
            val a = 0.05f + 0.04f * sin(time * 0.8f + i * 2.1f)
            val path = Path().apply {
                moveTo(cx - topW, 0f); lineTo(cx + topW, 0f)
                lineTo(cx + botW + w * 0.1f, h * 0.85f); lineTo(cx - botW + w * 0.1f, h * 0.85f); close()
            }
            drawPath(path, Brush.verticalGradient(listOf(Color.White.copy(alpha = a), Color.Transparent), 0f, h * 0.85f))
        }
        // コースティクス（水面のゆらゆら模様）
        for (k in 0 until 16) {
            val y0 = h * 0.01f + k * h * 0.024f
            val path = Path()
            var x = 0f
            var first = true
            while (x <= w + 24f) {
                val y = y0 + sin(x * 0.021f + time * 1.6f + k) * 7f + sin(x * 0.037f - time * 1.15f + k * 0.5f) * 5f
                if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                x += 22f
            }
            drawPath(path, Color.White.copy(alpha = 0.10f - k * 0.005f), style = Stroke(2.2f))
        }
        // 浮遊する粒
        plankton.forEach { (px, py, pz) ->
            val x = (px * w + time * 8f * (pz + 0.2f)) % w
            val y = (py * h + sin(time * 0.6f + pz * 9f) * 14f + h) % h
            drawCircle(Color.White, 0.8f + pz * 1.6f, Offset(x, y), alpha = 0.25f + pz * 0.3f)
        }
        // 魚のシルエット
        fish.forEach { f ->
            val prog = ((f.offset + time * f.speed) % 1.4f) - 0.2f
            val x = if (f.dir > 0) prog * w else w - prog * w
            val y = f.y * h + sin(time * 1.3f + f.offset * 7f) * 22f
            val sz = f.size * density / 2.5f
            val color = (if (f.colorIdx % 3 == 0) p.pink else if (f.colorIdx % 3 == 1) p.yellow else p.mint).copy(alpha = 0.55f)
            val tail = sin(time * 9f + f.offset * 5f) * sz * 0.3f
            val body = Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(x - sz, y - sz * 0.45f, x + sz, y + sz * 0.45f))
            }
            drawPath(body, color)
            val tx = x - f.dir * sz
            val tailPath = Path().apply {
                moveTo(tx, y)
                lineTo(tx - f.dir * sz * 0.8f, y - sz * 0.5f + tail)
                lineTo(tx - f.dir * sz * 0.8f, y + sz * 0.5f + tail)
                close()
            }
            drawPath(tailPath, color)
            drawCircle(Color.Black.copy(alpha = 0.6f), sz * 0.1f, Offset(x + f.dir * sz * 0.55f, y - sz * 0.1f))
        }
        // 海藻
        weeds.forEach { (wx, wh, wp) ->
            val baseX = wx * w
            val height = wh * h
            val path = Path()
            val segs = 12
            for (k in 0..segs) {
                val f = k / segs.toFloat()
                val sway = sin(time * 1.1f + wp * 6f + f * 3f) * f * 26f
                val px = baseX + sway
                val py = h - f * height
                if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, lerp(Color(0xFF0E5E4E), p.mint, 0.25f).copy(alpha = 0.8f), style = Stroke(7f + wp * 5f, cap = StrokeCap.Round))
        }
        // 泡
        bubbles.forEach { b ->
            val prog = (b.phase + time * b.speed) % 1.1f
            val y = h - prog * h * 1.05f
            val x = b.x * w + sin(time * 2.2f + b.phase * 12f) * 9f
            val r = b.size * density / 2.5f
            drawCircle(Color.White.copy(alpha = 0.55f), r, Offset(x, y), style = Stroke(1.6f))
            drawCircle(Color.White.copy(alpha = 0.6f), r * 0.28f, Offset(x - r * 0.35f, y - r * 0.35f))
        }
    }
}

// ═════════════════════════════════════════════
// 迷路（自動生成 → 自動で解く）
// ═════════════════════════════════════════════

/**
 * 穴掘り法（再帰的バックトラック）で迷路を作り、幅優先探索で解く様子を少しずつ進める。
 */
class MazeSim(val cols: Int, val rows: Int, seed: Int) {
    enum class Phase { GENERATE, SOLVE, TRACE, HOLD }

    private val n = cols * rows
    private val rnd = Random(seed)
    val right = BooleanArray(n)   // 右側に壁があるか
    val down = BooleanArray(n)    // 下側に壁があるか
    val visited = BooleanArray(n)
    val onStack = BooleanArray(n)
    private val stack = IntArray(n)
    var sp = 0; private set
    val dist = IntArray(n)
    private val prev = IntArray(n)
    private val queue = IntArray(n)
    private var qh = 0
    private var qt = 0
    var maxDist = 1; private set
    var path = IntArray(0); private set
    var traceLen = 0; private set
    var phase = Phase.GENERATE; private set
    private var budget = 0f
    private var hold = 0f
    val head get() = if (sp > 0) stack[sp - 1] else -1

    init { reset() }

    fun reset() {
        right.fill(true); down.fill(true); visited.fill(false); onStack.fill(false); dist.fill(-1); prev.fill(-1)
        sp = 0; qh = 0; qt = 0; maxDist = 1; path = IntArray(0); traceLen = 0; hold = 0f; budget = 0f
        val start = 0
        visited[start] = true; onStack[start] = true; stack[sp++] = start
        phase = Phase.GENERATE
    }

    /** dt 秒ぶん進める。何か変わったら true */
    fun update(dt: Float): Boolean {
        val rate = when (phase) {
            Phase.GENERATE -> n / 4.5f   // 約 4.5 秒で完成
            Phase.SOLVE -> n / 3.5f
            Phase.TRACE -> max(1, path.size) / 1.6f
            Phase.HOLD -> 0f
        }
        if (phase == Phase.HOLD) {
            hold += dt
            if (hold > 2.6f) { reset(); return true }
            return false
        }
        budget += dt * rate
        var changed = false
        while (budget >= 1f) {
            budget -= 1f
            changed = true
            when (phase) {
                Phase.GENERATE -> stepGenerate()
                Phase.SOLVE -> stepSolve()
                Phase.TRACE -> {
                    traceLen++
                    if (traceLen >= path.size) { phase = Phase.HOLD; budget = 0f }
                }
                Phase.HOLD -> budget = 0f
            }
        }
        return changed
    }

    private fun stepGenerate() {
        if (sp == 0) {
            // 完成 → 左上から右下へ解き始める
            phase = Phase.SOLVE
            dist[0] = 0; queue[qt++] = 0
            return
        }
        val cur = stack[sp - 1]
        val c = cur % cols
        val r = cur / cols
        val options = IntArray(4)
        var k = 0
        if (c > 0 && !visited[cur - 1]) options[k++] = cur - 1
        if (c < cols - 1 && !visited[cur + 1]) options[k++] = cur + 1
        if (r > 0 && !visited[cur - cols]) options[k++] = cur - cols
        if (r < rows - 1 && !visited[cur + cols]) options[k++] = cur + cols
        if (k == 0) {
            onStack[cur] = false
            sp--
            return
        }
        val next = options[rnd.nextInt(k)]
        when (next) {
            cur - 1 -> right[cur - 1] = false
            cur + 1 -> right[cur] = false
            cur - cols -> down[cur - cols] = false
            else -> down[cur] = false
        }
        visited[next] = true
        onStack[next] = true
        stack[sp++] = next
    }

    private fun stepSolve() {
        if (qh >= qt) { phase = Phase.HOLD; return }
        val cur = queue[qh++]
        val goal = n - 1
        if (cur == goal) {
            val list = ArrayList<Int>()
            var x = goal
            while (x != -1) { list += x; x = prev[x] }
            path = list.reversed().toIntArray()
            phase = Phase.TRACE
            return
        }
        val c = cur % cols
        val r = cur / cols
        fun visit(nb: Int) {
            if (dist[nb] >= 0) return
            dist[nb] = dist[cur] + 1
            maxDist = max(maxDist, dist[nb])
            prev[nb] = cur
            queue[qt++] = nb
        }
        if (c < cols - 1 && !right[cur]) visit(cur + 1)
        if (c > 0 && !right[cur - 1]) visit(cur - 1)
        if (r < rows - 1 && !down[cur]) visit(cur + cols)
        if (r > 0 && !down[cur - cols]) visit(cur - cols)
    }
}

@Composable
fun MazeScene(s: LauncherSettings, p: RetroPalette, paused: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val cellPx = with(density) { s.mazeCellDp.dp.toPx() }
        val margin = cellPx * 0.6f
        val cols = ((constraints.maxWidth - margin * 2) / cellPx).toInt().coerceAtLeast(4)
        val rows = ((constraints.maxHeight - margin * 2) / cellPx).toInt().coerceAtLeast(4)
        val sim = remember(cols, rows) { MazeSim(cols, rows, Random.nextInt()) }
        val tick = remember { mutableIntStateOf(0) }
        val speed by rememberUpdatedState(s.sceneSpeed())
        val isPaused by rememberUpdatedState(paused)
        LaunchedEffect(sim) {
            var last = withFrameNanos { it }
            while (isActive) {
                withFrameNanos { now ->
                    val dt = ((now - last) / 1e9f).coerceIn(0f, 0.1f)
                    last = now
                    if (!isPaused && sim.update(dt * speed)) tick.intValue++
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            tick.intValue
            drawRect(Brush.verticalGradient(listOf(p.skyTop, Color(0xFF05010D))))
            val ox = (size.width - cols * cellPx) / 2f
            val oy = (size.height - rows * cellPx) / 2f
            val cell = Size(cellPx, cellPx)
            for (i in 0 until cols * rows) {
                val x = ox + (i % cols) * cellPx
                val y = oy + (i / cols) * cellPx
                when {
                    sim.phase == MazeSim.Phase.GENERATE && sim.onStack[i] ->
                        drawRect(p.pink.copy(alpha = 0.32f), Offset(x, y), cell)
                    sim.phase == MazeSim.Phase.GENERATE && sim.visited[i] ->
                        drawRect(p.purple.copy(alpha = 0.18f), Offset(x, y), cell)
                    sim.phase != MazeSim.Phase.GENERATE && sim.dist[i] >= 0 -> {
                        val f = sim.dist[i] / sim.maxDist.toFloat()
                        drawRect(lerp(p.cyan, p.purple, f).copy(alpha = 0.22f), Offset(x, y), cell)
                    }
                }
            }
            // 掘っている先頭
            if (sim.phase == MazeSim.Phase.GENERATE && sim.head >= 0) {
                val h = sim.head
                drawRect(p.yellow.copy(alpha = 0.9f), Offset(ox + (h % cols) * cellPx, oy + (h / cols) * cellPx), cell)
            }
            // 壁
            val walls = Path()
            for (i in 0 until cols * rows) {
                val c = i % cols
                val r = i / cols
                val x = ox + c * cellPx
                val y = oy + r * cellPx
                if (c < cols - 1 && sim.right[i]) { walls.moveTo(x + cellPx, y); walls.lineTo(x + cellPx, y + cellPx) }
                if (r < rows - 1 && sim.down[i]) { walls.moveTo(x, y + cellPx); walls.lineTo(x + cellPx, y + cellPx) }
            }
            drawPath(walls, p.cyan.copy(alpha = 0.25f), style = Stroke(cellPx * 0.28f, cap = StrokeCap.Round))
            drawPath(walls, p.cyan, style = Stroke(cellPx * 0.09f, cap = StrokeCap.Round))
            drawRect(p.cyan, Offset(ox, oy), Size(cols * cellPx, rows * cellPx), style = Stroke(cellPx * 0.12f))
            // スタートとゴール
            drawCircle(p.mint, cellPx * 0.3f, Offset(ox + cellPx / 2, oy + cellPx / 2))
            drawCircle(p.pink, cellPx * 0.3f, Offset(ox + (cols - 0.5f) * cellPx, oy + (rows - 0.5f) * cellPx))
            // 解答の道筋
            if (sim.traceLen > 1) {
                val route = Path()
                for (k in 0 until min(sim.traceLen, sim.path.size)) {
                    val i = sim.path[k]
                    val cx = ox + (i % cols + 0.5f) * cellPx
                    val cy = oy + (i / cols + 0.5f) * cellPx
                    if (k == 0) route.moveTo(cx, cy) else route.lineTo(cx, cy)
                }
                val glowA = if (sim.phase == MazeSim.Phase.HOLD) 0.5f + 0.3f * sin(sim.traceLen + tick.intValue * 0.2f) else 0.35f
                drawPath(route, p.yellow.copy(alpha = glowA), style = Stroke(cellPx * 0.55f, cap = StrokeCap.Round))
                drawPath(route, p.yellow, style = Stroke(cellPx * 0.2f, cap = StrokeCap.Round))
            }
        }
    }
}

// ═════════════════════════════════════════════
// グリッチ
// ═════════════════════════════════════════════

@Composable
fun GlitchScene(s: LauncherSettings, p: RetroPalette, paused: Boolean, modifier: Modifier = Modifier) {
    val t by rememberSceneTime(s.sceneSpeed(), paused)
    val textPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textAlign = Paint.Align.CENTER }
    }
    Canvas(modifier.fillMaxSize()) {
        val time = t
        val intensity = s.glitchIntensity
        val w = size.width
        val h = size.height

        fun DrawScope.base(tint: Color? = null) {
            drawRect(Brush.verticalGradient(listOf(p.skyTop, p.skyMid, Color(0xFF080010))))
            // 回転するワイヤーフレーム
            val c = Offset(w / 2f, h * 0.42f)
            for (k in 0 until 3) {
                val r = w * (0.22f + k * 0.09f)
                val a0 = time * (0.3f + k * 0.15f) * (if (k % 2 == 0) 1 else -1)
                val path = Path()
                val sides = 3 + k
                for (j in 0..sides) {
                    val a = a0 + j * 2 * PI.toFloat() / sides
                    val pt = Offset(c.x + cos(a) * r, c.y + sin(a) * r)
                    if (j == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                }
                drawPath(path, (tint ?: if (k == 1) p.cyan else p.pink).copy(alpha = 0.6f), style = Stroke(3f))
            }
            // 格子
            var gy = h * 0.62f
            while (gy < h) {
                drawLine((tint ?: p.purple).copy(alpha = 0.35f), Offset(0f, gy), Offset(w, gy), 1.5f)
                gy += 38f
            }
            drawIntoCanvas { cv ->
                textPaint.textSize = w * 0.11f
                textPaint.color = (tint ?: Color.White).toArgb()
                cv.nativeCanvas.drawText("ｗｏｎｄｅｒｆｕｌ", w / 2f, h * 0.44f, textPaint)
                textPaint.textSize = w * 0.045f
                textPaint.color = (tint ?: p.cyan).toArgb()
                cv.nativeCanvas.drawText("NO SIGNAL // 0x" + (time * 997).toInt().toString(16).uppercase(), w / 2f, h * 0.52f, textPaint)
            }
        }

        base()
        // 一定間隔でランダムなグリッチが起きる
        val frame = floor(time * 9f).toInt()
        val rng = Random(frame)
        val active = rng.nextFloat() < 0.15f + 0.55f * intensity
        if (active) {
            // 横にずれる帯
            repeat(2 + (rng.nextFloat() * 9 * intensity).toInt()) {
                val y = rng.nextFloat() * h
                val bh = 6f + rng.nextFloat() * h * 0.08f
                val dx = (rng.nextFloat() - 0.5f) * 160f * intensity
                clipRect(0f, y, w, y + bh) {
                    translate(dx, 0f) { base(if (rng.nextBoolean()) p.cyan else p.pink) }
                }
            }
            // 色ずれブロック
            repeat((3 + 10 * intensity).toInt()) {
                val c = when (rng.nextInt(3)) { 0 -> p.pink; 1 -> p.cyan; else -> p.mint }
                drawRect(
                    c.copy(alpha = 0.25f + rng.nextFloat() * 0.4f),
                    Offset(rng.nextFloat() * w, rng.nextFloat() * h),
                    Size(rng.nextFloat() * w * 0.4f, 2f + rng.nextFloat() * 18f),
                    blendMode = BlendMode.Screen,
                )
            }
            // まれに反転フラッシュ
            if (rng.nextFloat() < 0.08f * intensity) drawRect(Color.White, blendMode = BlendMode.Difference)
        }
        // 常に流れるノイズ線
        val ny = (time * 240f) % (h + 80f) - 40f
        drawRect(Color.White.copy(alpha = 0.06f + 0.05f * intensity), Offset(0f, ny), Size(w, 30f))
    }
}

// ═════════════════════════════════════════════
// デジタル雨
// ═════════════════════════════════════════════

private const val RAIN_GLYPHS = "ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜｦﾝ0123456789ABCDEF<>*+:"

@Composable
fun MatrixScene(s: LauncherSettings, p: RetroPalette, paused: Boolean, modifier: Modifier = Modifier) {
    val t by rememberSceneTime(s.sceneSpeed(), paused)
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER } }
    val glyphs = remember { RAIN_GLYPHS.toCharArray() }
    val buf = remember { CharArray(1) }
    Canvas(modifier.fillMaxSize()) {
        val time = t
        drawRect(Color(0xFF020805))
        val charH = 18.dp.toPx()
        val cols = (size.width / (charH * 0.8f)).toInt().coerceAtLeast(8)
        val colW = size.width / cols
        val rows = (size.height / charH).toInt() + 2
        paint.textSize = charH * 0.85f
        drawIntoCanvas { cv ->
            for (c in 0 until cols) {
                val r = Random(c * 7919)
                val speed = 6f + r.nextFloat() * 10f
                val len = 8 + r.nextInt(18)
                val head = ((time * speed + r.nextFloat() * 60f) % (rows + len))
                val headRow = head.toInt()
                for (j in 0 until len) {
                    val row = headRow - j
                    if (row < 0 || row >= rows) continue
                    val flick = floor(time * 4f + c * 0.37f + row * 0.11f).toInt()
                    val gi = abs((c * 31 + row * 17 + flick * 13) % glyphs.size)
                    buf[0] = glyphs[gi]
                    val a = 1f - j / len.toFloat()
                    paint.color = (if (j == 0) Color.White else lerp(p.mint, p.cyan, (c % 5) / 5f)).copy(alpha = a).toArgb()
                    cv.nativeCanvas.drawText(buf, 0, 1, c * colW + colW / 2, row * charH, paint)
                }
            }
        }
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))))
    }
}

// ═════════════════════════════════════════════
// ワープ（星が流れる）
// ═════════════════════════════════════════════

@Composable
fun StarfieldScene(s: LauncherSettings, p: RetroPalette, paused: Boolean, modifier: Modifier = Modifier) {
    val t by rememberSceneTime(s.sceneSpeed(), paused)
    val count = 260
    val sx = remember { FloatArray(count) }
    val sy = remember { FloatArray(count) }
    val sz = remember { FloatArray(count) }
    val rnd = remember { Random(5) }
    val lastT = remember { floatArrayOf(-1f) }
    remember {
        for (i in 0 until count) { sx[i] = rnd.nextFloat() * 2 - 1; sy[i] = rnd.nextFloat() * 2 - 1; sz[i] = rnd.nextFloat() }
        true
    }
    Canvas(modifier.fillMaxSize()) {
        val time = t
        val dt = if (lastT[0] < 0) 0f else (time - lastT[0]).coerceIn(0f, 0.1f)
        lastT[0] = time
        val w = size.width
        val h = size.height
        val c = Offset(w / 2, h / 2)
        drawRect(Brush.radialGradient(listOf(p.skyMid.copy(alpha = 0.6f), p.skyTop, Color.Black), c, size.maxDimension * 0.7f))
        val scale = size.minDimension * 0.6f
        for (i in 0 until count) {
            val oldZ = sz[i]
            var z = oldZ - dt * 0.45f
            if (z <= 0.02f) {
                sx[i] = rnd.nextFloat() * 2 - 1; sy[i] = rnd.nextFloat() * 2 - 1
                z = 1f; sz[i] = z
                continue
            }
            sz[i] = z
            val x1 = c.x + sx[i] / z * scale
            val y1 = c.y + sy[i] / z * scale
            if (x1 < -50 || x1 > w + 50 || y1 < -50 || y1 > h + 50) { sz[i] = 0f; continue }
            val zPrev = min(1f, z + 0.035f + dt * 0.45f)
            val x0 = c.x + sx[i] / zPrev * scale
            val y0 = c.y + sy[i] / zPrev * scale
            val color = when (i % 4) { 0 -> p.pink; 1 -> p.cyan; 2 -> p.yellow; else -> Color.White }
            val a = (1f - z).coerceIn(0f, 1f)
            drawLine(color.copy(alpha = a), Offset(x0, y0), Offset(x1, y1), (1f - z) * 5f + 1f, StrokeCap.Round)
        }
    }
}

// ═════════════════════════════════════════════
// ラバランプ
// ═════════════════════════════════════════════

@Composable
fun LavaScene(s: LauncherSettings, p: RetroPalette, paused: Boolean, modifier: Modifier = Modifier) {
    val t by rememberSceneTime(s.sceneSpeed(), paused)
    Canvas(modifier.fillMaxSize()) {
        val time = t * 0.6f
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0B0016), p.skyTop, Color(0xFF14001F))))
        val colors = listOf(p.pink, p.purple, p.cyan, p.sunBottom, p.yellow, p.mint, p.pink)
        colors.forEachIndexed { i, col ->
            val cx = w * (0.5f + 0.38f * sin(time * (0.21f + i * 0.037f) + i * 1.9f))
            val cy = h * (0.5f + 0.42f * sin(time * (0.17f + i * 0.029f) + i * 0.7f))
            val r = w * (0.28f + 0.08f * sin(time * 0.5f + i)) * (1f + 0.12f * sin(time * 1.3f + i * 2f))
            drawCircle(
                Brush.radialGradient(listOf(col.copy(alpha = 0.85f), col.copy(alpha = 0.35f), Color.Transparent), Offset(cx, cy), r),
                r, Offset(cx, cy), blendMode = BlendMode.Plus,
            )
        }
        // ガラスの反射
        drawRect(
            Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.06f), Color.Transparent), w * 0.12f, w * 0.3f),
        )
    }
}

// ═════════════════════════════════════════════
// オーロラ
// ═════════════════════════════════════════════

@Composable
fun AuroraScene(s: LauncherSettings, p: RetroPalette, paused: Boolean, modifier: Modifier = Modifier) {
    val t by rememberSceneTime(s.sceneSpeed(), paused)
    val stars = remember { val r = Random(3); List(120) { Triple(r.nextFloat(), r.nextFloat() * 0.7f, r.nextFloat()) } }
    Canvas(modifier.fillMaxSize()) {
        val time = t
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF01040F), Color(0xFF071A33), p.skyTop)))
        stars.forEach { (x, y, k) ->
            val a = 0.3f + 0.7f * ((sin(time * 1.5f + k * 20f) + 1f) / 2f)
            drawCircle(Color.White, 0.8f + k * 1.4f, Offset(x * w, y * h), alpha = a * 0.8f)
        }
        val ribbons = listOf(p.mint, p.cyan, p.pink)
        ribbons.forEachIndexed { k, col ->
            val top = FloatArray(41)
            val bottom = FloatArray(41)
            for (i in 0..40) {
                val fx = i / 40f
                top[i] = h * (0.14f + 0.07f * k) + sin(fx * 6f + time * 0.5f + k * 1.3f) * h * 0.05f +
                    sin(fx * 13f - time * 0.8f + k) * h * 0.02f
                bottom[i] = top[i] + h * (0.16f + 0.07f * sin(fx * 4f + time * 0.6f + k * 2f))
            }
            val path = Path()
            for (i in 0..40) { val x = w * i / 40f; if (i == 0) path.moveTo(x, top[i]) else path.lineTo(x, top[i]) }
            for (i in 40 downTo 0) path.lineTo(w * i / 40f, bottom[i])
            path.close()
            val minY = top.min()
            val maxY = bottom.max()
            drawPath(
                path,
                Brush.verticalGradient(listOf(Color.Transparent, col.copy(alpha = 0.5f), col.copy(alpha = 0.15f), Color.Transparent), minY, maxY),
                blendMode = BlendMode.Plus,
            )
        }
        // 山と湖
        val hills = Path().apply {
            moveTo(0f, h * 0.78f)
            var x = 0f
            val r = Random(9)
            while (x < w) { x += w * 0.08f; lineTo(x, h * (0.7f + r.nextFloat() * 0.08f)) }
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(hills, Color(0xFF02060C))
        drawRect(Brush.verticalGradient(listOf(p.cyan.copy(alpha = 0.08f), Color.Transparent), h * 0.82f, h), Offset(0f, h * 0.82f), Size(w, h * 0.18f))
    }
}

// ═════════════════════════════════════════════
// ライフゲーム
// ═════════════════════════════════════════════

class LifeSim(val cols: Int, val rows: Int) {
    var cells = BooleanArray(cols * rows); private set
    private var next = BooleanArray(cols * rows)
    val glow = FloatArray(cols * rows)
    val age = IntArray(cols * rows)
    private var acc = 0f
    private var generation = 0
    private var stale = 0
    private var lastPop = -1
    var flash = 0f; private set

    init { seed() }

    fun seed() {
        val r = Random.Default
        for (i in cells.indices) { cells[i] = r.nextFloat() < 0.28f; age[i] = 0 }
        generation = 0; stale = 0; flash = 1f
    }

    fun update(dt: Float): Boolean {
        flash = max(0f, flash - dt * 2f)
        for (i in glow.indices) glow[i] = if (cells[i]) 1f else glow[i] * (1f - min(1f, dt * 3f))
        acc += dt
        if (acc < 0.11f) return true
        acc = 0f
        var pop = 0
        for (r in 0 until rows) for (c in 0 until cols) {
            var nbs = 0
            for (dr in -1..1) for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val rr = (r + dr + rows) % rows
                val cc = (c + dc + cols) % cols
                if (cells[rr * cols + cc]) nbs++
            }
            val i = r * cols + c
            val alive = if (cells[i]) nbs == 2 || nbs == 3 else nbs == 3
            next[i] = alive
            age[i] = if (alive) age[i] + 1 else 0
            if (alive) pop++
        }
        val tmp = cells; cells = next; next = tmp
        generation++
        stale = if (abs(pop - lastPop) <= 2) stale + 1 else 0
        lastPop = pop
        // 動きが止まった・長く続いた・ほぼ全滅したら植え直す
        if (stale > 25 || generation > 600 || pop < cols * rows / 60) seed()
        return true
    }
}

@Composable
fun LifeScene(s: LauncherSettings, p: RetroPalette, paused: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val cellPx = with(density) { 12.dp.toPx() }
        val cols = (constraints.maxWidth / cellPx).toInt().coerceAtLeast(8)
        val rows = (constraints.maxHeight / cellPx).toInt().coerceAtLeast(8)
        val sim = remember(cols, rows) { LifeSim(cols, rows) }
        val tick = remember { mutableIntStateOf(0) }
        val speed by rememberUpdatedState(s.sceneSpeed())
        val isPaused by rememberUpdatedState(paused)
        LaunchedEffect(sim) {
            var last = withFrameNanos { it }
            while (isActive) {
                withFrameNanos { now ->
                    val dt = ((now - last) / 1e9f).coerceIn(0f, 0.1f)
                    last = now
                    if (!isPaused && sim.update(dt * speed)) tick.intValue++
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            tick.intValue
            drawRect(Brush.verticalGradient(listOf(Color(0xFF07000F), p.skyTop)))
            val cell = Size(cellPx * 0.82f, cellPx * 0.82f)
            for (i in 0 until cols * rows) {
                val g = sim.glow[i]
                if (g < 0.03f) continue
                val x = (i % cols) * cellPx
                val y = (i / cols) * cellPx
                val color = when {
                    !sim.cells[i] -> p.purple
                    sim.age[i] < 2 -> p.yellow
                    sim.age[i] < 8 -> p.pink
                    else -> p.cyan
                }
                drawRect(color.copy(alpha = 0.85f * g), Offset(x, y), cell)
            }
            if (sim.flash > 0f) drawRect(p.pink.copy(alpha = sim.flash * 0.25f))
        }
    }
}
