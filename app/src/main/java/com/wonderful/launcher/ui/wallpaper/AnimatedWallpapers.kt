// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (c) 2026 72no6495

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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.wonderful.launcher.data.CellShape
import com.wonderful.launcher.data.ColorMode
import com.wonderful.launcher.data.LauncherSettings
import com.wonderful.launcher.data.LifeColor
import com.wonderful.launcher.data.LifeConfig
import com.wonderful.launcher.data.LifeSeed
import com.wonderful.launcher.data.MazeGenerator
import com.wonderful.launcher.data.MazeSolver
import com.wonderful.launcher.data.ScreenCorner
import com.wonderful.launcher.ui.theme.FrameGate
import com.wonderful.launcher.ui.theme.rememberTicker
import com.wonderful.launcher.ui.theme.LocalFpsLimit
import com.wonderful.launcher.ui.theme.RetroPalette
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
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
fun rememberSceneTime(speed: Float, paused: Boolean): State<Float> =
    rememberTicker(speed, paused, LocalFpsLimit.current)

/** ホーム画面の何もない所をタップした位置（画面座標）。触れる壁紙が受け取る */
object WallpaperTouch {
    val taps = MutableSharedFlow<Offset>(extraBufferCapacity = 8)
}

private fun LauncherSettings.sceneSpeed() = (gridSpeed * animSpeed).coerceAtLeast(0.05f)

private fun modeColor(mode: ColorMode, p: RetroPalette, i: Int, time: Float, paletteColor: Color): Color = when (mode) {
    ColorMode.PALETTE -> paletteColor
    ColorMode.CLASSIC -> Color(0xFF39FF6A)
    ColorMode.RAINBOW -> Color.hsv(((i * 37f + time * 40f) % 360f + 360f) % 360f, 0.75f, 1f)
}

// ═════════════════════════════════════════════
// 水中
// ═════════════════════════════════════════════

private class Bubble(val x: Float, val phase: Float, val speed: Float, val size: Float)
private class Fish(val y: Float, val offset: Float, val speed: Float, val size: Float, val dir: Int, val colorIdx: Int)

@Composable
fun UnderwaterScene(s: LauncherSettings, p: RetroPalette, paused: Boolean, modifier: Modifier = Modifier) {
    val cfg = s.underwater
    val t by rememberSceneTime(s.sceneSpeed(), paused)
    val bubbles = remember(cfg.bubbles) {
        val rnd = Random(11)
        List(cfg.bubbles) { Bubble(rnd.nextFloat(), rnd.nextFloat(), 0.04f + rnd.nextFloat() * 0.1f, 2f + rnd.nextFloat() * 8f) }
    }
    val fish = remember(cfg.fish) {
        val rnd = Random(12)
        List(cfg.fish) { Fish(0.2f + rnd.nextFloat() * 0.6f, rnd.nextFloat() * 1.4f, 0.025f + rnd.nextFloat() * 0.04f, 14f + rnd.nextFloat() * 22f, if (it % 2 == 0) 1 else -1, it) }
    }
    val plankton = remember { val rnd = Random(13); List(80) { Triple(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat()) } }
    val weeds = remember { val rnd = Random(14); List(11) { Triple(rnd.nextFloat(), 0.1f + rnd.nextFloat() * 0.2f, rnd.nextFloat()) } }

    Canvas(modifier.fillMaxSize()) {
        val time = t
        val w = size.width
        val h = size.height
        val deep = cfg.depth.coerceIn(0f, 1f)
        drawRect(
            Brush.verticalGradient(
                0f to lerp(lerp(Color(0xFF29C3D6), p.cyan, 0.3f), Color(0xFF0A3A55), deep * 0.6f),
                0.3f to lerp(Color(0xFF0A6C8A), Color(0xFF041A30), deep * 0.6f),
                0.65f to lerp(Color(0xFF052A47), Color(0xFF020A16), deep),
                1f to Color(0xFF010812),
            ),
        )
        if (cfg.lightRays) for (i in 0 until 6) {
            val cx = w * (i + 0.5f) / 6f + sin(time * 0.25f + i * 1.7f) * w * 0.08f
            val topW = w * 0.05f
            val botW = w * 0.22f
            val a = (0.05f + 0.04f * sin(time * 0.8f + i * 2.1f)) * (1f - deep * 0.6f)
            val path = Path().apply {
                moveTo(cx - topW, 0f); lineTo(cx + topW, 0f)
                lineTo(cx + botW + w * 0.1f, h * 0.85f); lineTo(cx - botW + w * 0.1f, h * 0.85f); close()
            }
            drawPath(path, Brush.verticalGradient(listOf(Color.White.copy(alpha = a), Color.Transparent), 0f, h * 0.85f))
        }
        if (cfg.caustics) for (k in 0 until 16) {
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
        if (cfg.plankton) plankton.forEach { (px, py, pz) ->
            val x = (px * w + time * 8f * (pz + 0.2f)) % w
            val y = (py * h + sin(time * 0.6f + pz * 9f) * 14f + h) % h
            drawCircle(Color.White, 0.8f + pz * 1.6f, Offset(x, y), alpha = 0.25f + pz * 0.3f)
        }
        fish.forEach { f ->
            val prog = ((f.offset + time * f.speed) % 1.4f) - 0.2f
            val x = if (f.dir > 0) prog * w else w - prog * w
            val y = f.y * h + sin(time * 1.3f + f.offset * 7f) * 22f
            val sz = f.size * density / 2.5f
            val color = (if (f.colorIdx % 3 == 0) p.pink else if (f.colorIdx % 3 == 1) p.yellow else p.mint).copy(alpha = 0.55f)
            val tail = sin(time * 9f + f.offset * 5f) * sz * 0.3f
            val body = Path().apply { addOval(androidx.compose.ui.geometry.Rect(x - sz, y - sz * 0.45f, x + sz, y + sz * 0.45f)) }
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
        if (cfg.seaweed) weeds.forEach { (wx, wh, wp) ->
            val baseX = wx * w
            val height = wh * h
            val path = Path()
            val segs = 12
            for (k in 0..segs) {
                val f = k / segs.toFloat()
                val sway = sin(time * 1.1f + wp * 6f + f * 3f) * f * 26f
                if (k == 0) path.moveTo(baseX + sway, h - f * height) else path.lineTo(baseX + sway, h - f * height)
            }
            drawPath(path, lerp(Color(0xFF0E5E4E), p.mint, 0.25f).copy(alpha = 0.8f), style = Stroke(7f + wp * 5f, cap = StrokeCap.Round))
        }
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
 * 迷路を少しずつ作り（穴掘り法 / プリム法）、少しずつ解く（幅優先 / 深さ優先 / A* / 右手法）。
 */
class MazeSim(
    val cols: Int,
    val rows: Int,
    seed: Int,
    private val generator: MazeGenerator = MazeGenerator.BACKTRACK,
    private val solver: MazeSolver = MazeSolver.BFS,
) {
    enum class Phase { GENERATE, SOLVE, TRACE, HOLD }

    private val n = cols * rows
    private val rnd = Random(seed)
    val right = BooleanArray(n)   // 右側に壁があるか
    val down = BooleanArray(n)    // 下側に壁があるか
    val visited = BooleanArray(n)
    /** 生成中の強調表示（穴掘りの経路 / プリムの候補） */
    val onStack = BooleanArray(n)
    private val stack = IntArray(n)
    var sp = 0; private set
    private var frontier = IntArray(n)
    private var fSize = 0
    var head = -1; private set
    /** 探索した順番（-1 = 未探索） */
    val dist = IntArray(n)
    private var order = 0
    private val prev = IntArray(n)
    private val queue = IntArray(n * 4)
    private var qh = 0
    private var qt = 0
    private val g = IntArray(n)
    var maxDist = 1; private set
    var path = IntArray(0); private set
    var traceLen = 0; private set
    var phase = Phase.GENERATE; private set
    private var budget = 0f
    private var hold = 0f
    // 右手法
    private var walker = 0
    private var facing = 0 // 0=右 1=下 2=左 3=上
    private val walkStack = IntArray(n * 4)
    private var wsp = 0
    // init より前に宣言する（reset() の中で使うため）
    private val tmp = IntArray(4)

    init { reset() }

    fun reset() {
        right.fill(true); down.fill(true); visited.fill(false); onStack.fill(false); dist.fill(-1); prev.fill(-1)
        sp = 0; fSize = 0; qh = 0; qt = 0; maxDist = 1; path = IntArray(0); traceLen = 0; hold = 0f; budget = 0f; order = 0
        val start = if (generator == MazeGenerator.PRIM) rnd.nextInt(n) else 0
        visited[start] = true
        head = start
        if (generator == MazeGenerator.PRIM) addFrontier(start) else { onStack[start] = true; stack[sp++] = start }
        phase = Phase.GENERATE
    }

    /** dt 秒ぶん進める。何か変わったら true */
    fun update(dt: Float, genSpeed: Float, solveSpeed: Float, holdSec: Float): Boolean {
        val rate = when (phase) {
            Phase.GENERATE -> n / 4.5f * genSpeed
            Phase.SOLVE -> n / 3.5f * solveSpeed
            Phase.TRACE -> max(1, path.size) / 1.6f * solveSpeed
            Phase.HOLD -> 0f
        }
        if (phase == Phase.HOLD) {
            hold += dt
            if (hold > holdSec) { reset(); return true }
            return false
        }
        budget += dt * rate
        var changed = false
        var guard = 0
        while (budget >= 1f && guard++ < 20000) {
            budget -= 1f
            changed = true
            when (phase) {
                Phase.GENERATE -> if (generator == MazeGenerator.PRIM) stepPrim() else stepBacktrack()
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

    private fun neighbors(cur: Int, out: IntArray, unvisitedOnly: Boolean): Int {
        val c = cur % cols
        val r = cur / cols
        var k = 0
        if (c > 0 && (!unvisitedOnly || !visited[cur - 1])) out[k++] = cur - 1
        if (c < cols - 1 && (!unvisitedOnly || !visited[cur + 1])) out[k++] = cur + 1
        if (r > 0 && (!unvisitedOnly || !visited[cur - cols])) out[k++] = cur - cols
        if (r < rows - 1 && (!unvisitedOnly || !visited[cur + cols])) out[k++] = cur + cols
        return k
    }

    private fun carve(a: Int, b: Int) {
        when (b) {
            a - 1 -> right[a - 1] = false
            a + 1 -> right[a] = false
            a - cols -> down[a - cols] = false
            else -> down[a] = false
        }
    }

    private fun stepBacktrack() {
        if (sp == 0) { startSolve(); return }
        val cur = stack[sp - 1]
        head = cur
        val k = neighbors(cur, tmp, unvisitedOnly = true)
        if (k == 0) { onStack[cur] = false; sp--; return }
        val next = tmp[rnd.nextInt(k)]
        carve(cur, next)
        visited[next] = true
        onStack[next] = true
        stack[sp++] = next
    }

    private fun addFrontier(cell: Int) {
        val k = neighbors(cell, tmp, unvisitedOnly = true)
        for (j in 0 until k) {
            val nb = tmp[j]
            if (!onStack[nb]) { onStack[nb] = true; frontier[fSize++] = nb }
        }
    }

    private fun stepPrim() {
        if (fSize == 0) { startSolve(); return }
        val idx = rnd.nextInt(fSize)
        val cell = frontier[idx]
        frontier[idx] = frontier[--fSize]
        onStack[cell] = false
        // 迷路に入っている隣とつなぐ
        val all = IntArray(4)
        val k = neighbors(cell, all, unvisitedOnly = false)
        val inMaze = (0 until k).map { all[it] }.filter { visited[it] }
        if (inMaze.isNotEmpty()) carve(cell, inMaze[rnd.nextInt(inMaze.size)])
        visited[cell] = true
        head = cell
        addFrontier(cell)
    }

    private fun startSolve() {
        phase = Phase.SOLVE
        onStack.fill(false)
        head = -1
        dist[0] = order++
        g.fill(Int.MAX_VALUE); g[0] = 0
        queue[qt++] = 0
        walker = 0; facing = 0; wsp = 0; walkStack[wsp++] = 0
    }

    private fun open(a: Int, b: Int): Boolean = when (b) {
        a + 1 -> a % cols < cols - 1 && !right[a]
        a - 1 -> a % cols > 0 && !right[a - 1]
        a + cols -> a / cols < rows - 1 && !down[a]
        a - cols -> a / cols > 0 && !down[a - cols]
        else -> false
    }

    private fun finish(goal: Int) {
        val list = ArrayList<Int>()
        var x = goal
        var guard = 0
        while (x != -1 && guard++ < n) { list += x; x = prev[x] }
        path = list.reversed().toIntArray()
        phase = Phase.TRACE
    }

    private fun stepSolve() {
        val goal = n - 1
        when (solver) {
            MazeSolver.RIGHT_HAND -> {
                if (walker == goal) {
                    path = walkStack.copyOf(wsp)
                    phase = Phase.TRACE
                    return
                }
                // 右手を壁に付けたまま歩く：右 → まっすぐ → 左 → 後ろ の順に試す
                val dirs = intArrayOf((facing + 1) % 4, facing, (facing + 3) % 4, (facing + 2) % 4)
                for (d in dirs) {
                    val nb = when (d) { 0 -> walker + 1; 1 -> walker + cols; 2 -> walker - 1; else -> walker - cols }
                    if (open(walker, nb)) {
                        facing = d
                        walker = nb
                        if (dist[nb] < 0) dist[nb] = order++
                        maxDist = max(maxDist, order)
                        // 来た道を戻ったらループを消す（最後に残るのが答え）
                        var found = -1
                        for (j in wsp - 1 downTo 0) if (walkStack[j] == nb) { found = j; break }
                        if (found >= 0) wsp = found + 1 else walkStack[wsp++] = nb
                        head = nb
                        return
                    }
                }
            }
            MazeSolver.ASTAR -> {
                if (qh >= qt) { phase = Phase.HOLD; return }
                // 未処理の中から f = g + h が最小のものを選ぶ
                var best = qh
                var bestF = Int.MAX_VALUE
                for (j in qh until qt) {
                    val c = queue[j]
                    val h = (cols - 1 - c % cols) + (rows - 1 - c / cols)
                    val f = g[c] + h
                    if (f < bestF) { bestF = f; best = j }
                }
                val cur = queue[best]
                queue[best] = queue[qh]; queue[qh] = cur; qh++
                expand(cur, goal)
            }
            MazeSolver.DFS -> {
                if (qt <= qh) { phase = Phase.HOLD; return }
                val cur = queue[--qt]
                expand(cur, goal)
            }
            MazeSolver.BFS -> {
                if (qh >= qt) { phase = Phase.HOLD; return }
                expand(queue[qh++], goal)
            }
        }
    }

    private fun expand(cur: Int, goal: Int) {
        head = cur
        if (cur == goal) { finish(goal); return }
        val k = neighbors(cur, tmp, unvisitedOnly = false)
        for (j in 0 until k) {
            val nb = tmp[j]
            if (!open(cur, nb) || dist[nb] >= 0) continue
            dist[nb] = order++
            maxDist = max(maxDist, order)
            prev[nb] = cur
            g[nb] = g[cur] + 1
            if (qt < queue.size) queue[qt++] = nb
        }
    }
}

@Composable
fun MazeScene(s: LauncherSettings, p: RetroPalette, paused: Boolean, modifier: Modifier = Modifier) {
    val cfg = s.maze
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val cellPx = with(density) { cfg.cellDp.dp.toPx() }
        val margin = cellPx * 0.6f
        val cols = ((constraints.maxWidth - margin * 2) / cellPx).toInt().coerceAtLeast(4)
        val rows = ((constraints.maxHeight - margin * 2) / cellPx).toInt().coerceAtLeast(4)
        val sim = remember(cols, rows, cfg.generator, cfg.solver) { MazeSim(cols, rows, Random.nextInt(), cfg.generator, cfg.solver) }
        val tick = remember { mutableIntStateOf(0) }
        val speed by rememberUpdatedState(s.sceneSpeed())
        val config by rememberUpdatedState(cfg)
        val isPaused by rememberUpdatedState(paused)
        val fps by rememberUpdatedState(LocalFpsLimit.current)
        LaunchedEffect(sim) {
            val gate = FrameGate()
            var last = withFrameNanos { it }
            while (isActive) {
                if (isPaused) {
                    snapshotFlow { isPaused }.first { !it }
                    last = withFrameNanos { it }
                    continue
                }
                if (sim.phase == MazeSim.Phase.HOLD) {
                    // 解き終わって止まっている間は、画面を更新せずに待つ
                    delay(200)
                    if (sim.update(0.2f * speed, config.genSpeed, config.solveSpeed, config.holdSec)) tick.intValue++
                    last = withFrameNanos { it }
                    continue
                }
                withFrameNanos { now ->
                    val dt = ((now - last) / 1e9f).coerceIn(0f, 0.1f)
                    last = now
                    if (gate.add(dt, fps) &&
                        sim.update(gate.consume().coerceAtMost(0.1f) * speed, config.genSpeed, config.solveSpeed, config.holdSec)
                    ) tick.intValue++
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            tick.intValue
            drawRect(Brush.verticalGradient(listOf(p.skyTop, Color(0xFF05010D))))
            val ox = (size.width - cols * cellPx) / 2f
            val oy = (size.height - rows * cellPx) / 2f
            val cell = Size(cellPx, cellPx)
            val generating = sim.phase == MazeSim.Phase.GENERATE
            for (i in 0 until cols * rows) {
                val x = ox + (i % cols) * cellPx
                val y = oy + (i / cols) * cellPx
                when {
                    generating && sim.onStack[i] -> drawRect(p.pink.copy(alpha = 0.32f), Offset(x, y), cell)
                    generating && sim.visited[i] -> drawRect(p.purple.copy(alpha = 0.18f), Offset(x, y), cell)
                    !generating && cfg.showSearch && sim.dist[i] >= 0 -> {
                        val f = sim.dist[i] / sim.maxDist.toFloat()
                        drawRect(lerp(p.cyan, p.purple, f).copy(alpha = 0.22f), Offset(x, y), cell)
                    }
                }
            }
            if (sim.head >= 0 && sim.phase != MazeSim.Phase.HOLD) {
                val h = sim.head
                drawRect(p.yellow.copy(alpha = 0.9f), Offset(ox + (h % cols) * cellPx, oy + (h / cols) * cellPx), cell)
            }
            val walls = Path()
            for (i in 0 until cols * rows) {
                val c = i % cols
                val r = i / cols
                val x = ox + c * cellPx
                val y = oy + r * cellPx
                if (c < cols - 1 && sim.right[i]) { walls.moveTo(x + cellPx, y); walls.lineTo(x + cellPx, y + cellPx) }
                if (r < rows - 1 && sim.down[i]) { walls.moveTo(x, y + cellPx); walls.lineTo(x + cellPx, y + cellPx) }
            }
            val ww = cfg.wallWidth.coerceIn(0.3f, 3f)
            if (cfg.wallGlow) drawPath(walls, p.cyan.copy(alpha = 0.25f), style = Stroke(cellPx * 0.28f * ww, cap = StrokeCap.Round))
            drawPath(walls, p.cyan, style = Stroke(cellPx * 0.09f * ww, cap = StrokeCap.Round))
            drawRect(p.cyan, Offset(ox, oy), Size(cols * cellPx, rows * cellPx), style = Stroke(cellPx * 0.12f * ww))
            drawCircle(p.mint, cellPx * 0.3f, Offset(ox + cellPx / 2, oy + cellPx / 2))
            drawCircle(p.pink, cellPx * 0.3f, Offset(ox + (cols - 0.5f) * cellPx, oy + (rows - 0.5f) * cellPx))
            if (sim.traceLen > 1) {
                val route = Path()
                for (k in 0 until min(sim.traceLen, sim.path.size)) {
                    val i = sim.path[k]
                    val cx = ox + (i % cols + 0.5f) * cellPx
                    val cy = oy + (i / cols + 0.5f) * cellPx
                    if (k == 0) route.moveTo(cx, cy) else route.lineTo(cx, cy)
                }
                val glowA = if (sim.phase == MazeSim.Phase.HOLD) 0.5f + 0.3f * sin(tick.intValue * 0.2f) else 0.35f
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
    val cfg = s.glitch
    val t by rememberSceneTime(s.sceneSpeed(), paused)
    val textPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textAlign = Paint.Align.CENTER }
    }
    Canvas(modifier.fillMaxSize()) {
        val time = t
        val intensity = cfg.intensity
        val w = size.width
        val h = size.height

        fun DrawScope.base(tint: Color? = null) {
            drawRect(Brush.verticalGradient(listOf(p.skyTop, p.skyMid, Color(0xFF080010))))
            if (cfg.shapes) {
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
            }
            var gy = h * 0.62f
            while (gy < h) {
                drawLine((tint ?: p.purple).copy(alpha = 0.35f), Offset(0f, gy), Offset(w, gy), 1.5f)
                gy += 38f
            }
            if (cfg.showText) drawIntoCanvas { cv ->
                val text = cfg.text.ifBlank { "ｗｏｎｄｅｒｆｕｌ" }
                textPaint.textSize = (w * 0.11f * min(1f, 9f / max(1, text.length))).coerceAtLeast(w * 0.05f)
                textPaint.color = (tint ?: Color.White).toArgb()
                cv.nativeCanvas.drawText(text, w / 2f, h * 0.44f, textPaint)
                textPaint.textSize = w * 0.045f
                textPaint.color = (tint ?: p.cyan).toArgb()
                cv.nativeCanvas.drawText("NO SIGNAL // 0x" + (time * 997).toInt().toString(16).uppercase(), w / 2f, h * 0.52f, textPaint)
            }
        }

        base()
        val frame = floor(time * (3f + cfg.frequency * 14f)).toInt()
        val rng = Random(frame)
        val active = rng.nextFloat() < 0.1f + 0.6f * cfg.frequency
        if (active) {
            repeat(2 + (rng.nextFloat() * 9 * intensity).toInt()) {
                val y = rng.nextFloat() * h
                val bh = 6f + rng.nextFloat() * h * 0.08f
                val dx = (rng.nextFloat() - 0.5f) * 160f * intensity
                clipRect(0f, y, w, y + bh) {
                    translate(dx, 0f) { base(if (rng.nextBoolean()) p.cyan else p.pink) }
                }
            }
            repeat((3 + 10 * intensity).toInt()) {
                val c = when (rng.nextInt(3)) { 0 -> p.pink; 1 -> p.cyan; else -> p.mint }
                drawRect(
                    c.copy(alpha = 0.25f + rng.nextFloat() * 0.4f),
                    Offset(rng.nextFloat() * w, rng.nextFloat() * h),
                    Size(rng.nextFloat() * w * 0.4f, 2f + rng.nextFloat() * 18f),
                    blendMode = BlendMode.Screen,
                )
            }
            if (cfg.invertFlash && rng.nextFloat() < 0.08f * intensity) drawRect(Color.White, blendMode = BlendMode.Difference)
        }
        val ny = (time * 240f) % (h + 80f) - 40f
        drawRect(Color.White.copy(alpha = 0.06f + 0.05f * intensity), Offset(0f, ny), Size(w, 30f))
    }
}

// ═════════════════════════════════════════════
// デジタル雨
// ═════════════════════════════════════════════

@Composable
fun MatrixScene(s: LauncherSettings, p: RetroPalette, paused: Boolean, modifier: Modifier = Modifier) {
    val cfg = s.matrix
    val t by rememberSceneTime(s.sceneSpeed(), paused)
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER } }
    val glyphs = remember(cfg.charset) { cfg.charset.chars.toCharArray() }
    val buf = remember { CharArray(1) }
    Canvas(modifier.fillMaxSize()) {
        val time = t
        drawRect(Color(0xFF020805))
        val charH = cfg.charSizeDp.dp.toPx()
        val cols = (size.width / (charH * 0.8f)).toInt().coerceAtLeast(4)
        val colW = size.width / cols
        val rows = (size.height / charH).toInt() + 2
        paint.textSize = charH * 0.85f
        drawIntoCanvas { cv ->
            for (c in 0 until cols) {
                val r = Random(c * 7919)
                if (r.nextFloat() > cfg.density) continue
                val speed = 6f + r.nextFloat() * 10f
                val len = ((8 + r.nextInt(18)) * cfg.trail).toInt().coerceAtLeast(2)
                val head = ((time * speed + r.nextFloat() * 60f) % (rows + len))
                val headRow = head.toInt()
                val base = modeColor(cfg.color, p, c, time, lerp(p.mint, p.cyan, (c % 5) / 5f))
                for (j in 0 until len) {
                    val row = headRow - j
                    if (row < 0 || row >= rows) continue
                    val flick = floor(time * 4f + c * 0.37f + row * 0.11f).toInt()
                    val gi = abs((c * 31 + row * 17 + flick * 13) % glyphs.size)
                    buf[0] = glyphs[gi]
                    val a = 1f - j / len.toFloat()
                    paint.color = (if (j == 0) Color.White else base).copy(alpha = a).toArgb()
                    cv.nativeCanvas.drawText(buf, 0, 1, c * colW + colW / 2, row * charH, paint)
                }
            }
        }
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))))
    }
}

// ═════════════════════════════════════════════
// ワープ
// ═════════════════════════════════════════════

private class Stars(val count: Int) {
    val x = FloatArray(count)
    val y = FloatArray(count)
    val z = FloatArray(count)
    val rnd = Random(5)
    var lastT = -1f

    init { for (i in 0 until count) { x[i] = rnd.nextFloat() * 2 - 1; y[i] = rnd.nextFloat() * 2 - 1; z[i] = rnd.nextFloat() } }
}

@Composable
fun StarfieldScene(s: LauncherSettings, p: RetroPalette, paused: Boolean, modifier: Modifier = Modifier) {
    val cfg = s.starfield
    val t by rememberSceneTime(s.sceneSpeed(), paused)
    val st = remember(cfg.count) { Stars(cfg.count) }
    Canvas(modifier.fillMaxSize()) {
        val time = t
        val dt = if (st.lastT < 0) 0f else (time - st.lastT).coerceIn(0f, 0.1f)
        st.lastT = time
        val w = size.width
        val h = size.height
        val c = Offset(w / 2, h / 2)
        drawRect(Color.Black)
        if (cfg.centerGlow) drawRect(Brush.radialGradient(listOf(p.skyMid.copy(alpha = 0.6f), p.skyTop, Color.Black), c, size.maxDimension * 0.7f))
        val scale = size.minDimension * 0.6f
        val v = 0.45f * cfg.warp
        for (i in 0 until st.count) {
            var z = st.z[i] - dt * v
            if (z <= 0.02f) {
                st.x[i] = st.rnd.nextFloat() * 2 - 1; st.y[i] = st.rnd.nextFloat() * 2 - 1
                st.z[i] = 1f
                continue
            }
            st.z[i] = z
            val x1 = c.x + st.x[i] / z * scale
            val y1 = c.y + st.y[i] / z * scale
            if (x1 < -50 || x1 > w + 50 || y1 < -50 || y1 > h + 50) { st.z[i] = 0f; continue }
            val zPrev = min(1f, z + (0.035f + dt * v) * cfg.streak)
            val x0 = c.x + st.x[i] / zPrev * scale
            val y0 = c.y + st.y[i] / zPrev * scale
            val pc = when (i % 4) { 0 -> p.pink; 1 -> p.cyan; 2 -> p.yellow; else -> Color.White }
            val color = modeColor(cfg.color, p, i, time, pc)
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
    val cfg = s.lava
    val t by rememberSceneTime(s.sceneSpeed(), paused)
    Canvas(modifier.fillMaxSize()) {
        val time = t * 0.6f
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0B0016), p.skyTop, Color(0xFF14001F))))
        val colors = listOf(p.pink, p.purple, p.cyan, p.sunBottom, p.yellow, p.mint)
        for (i in 0 until cfg.blobs) {
            val col = colors[i % colors.size]
            val cx = w * (0.5f + 0.38f * sin(time * (0.21f + i * 0.037f) + i * 1.9f))
            val cy = h * (0.5f + 0.42f * sin(time * (0.17f + i * 0.029f) + i * 0.7f))
            val r = w * (0.28f + 0.08f * sin(time * 0.5f + i)) * (1f + 0.12f * sin(time * 1.3f + i * 2f)) * cfg.size
            drawCircle(
                Brush.radialGradient(listOf(col.copy(alpha = 0.85f), col.copy(alpha = 0.35f), Color.Transparent), Offset(cx, cy), r),
                r, Offset(cx, cy), blendMode = BlendMode.Plus,
            )
        }
        if (cfg.glossy) drawRect(
            Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.06f), Color.Transparent), w * 0.12f, w * 0.3f),
        )
    }
}

// ═════════════════════════════════════════════
// オーロラ
// ═════════════════════════════════════════════

@Composable
fun AuroraScene(s: LauncherSettings, p: RetroPalette, paused: Boolean, modifier: Modifier = Modifier) {
    val cfg = s.aurora
    val t by rememberSceneTime(s.sceneSpeed(), paused)
    val stars = remember { val r = Random(3); List(120) { Triple(r.nextFloat(), r.nextFloat() * 0.7f, r.nextFloat()) } }
    Canvas(modifier.fillMaxSize()) {
        val time = t
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF01040F), Color(0xFF071A33), p.skyTop)))
        if (cfg.stars) stars.forEach { (x, y, k) ->
            val a = 0.3f + 0.7f * ((sin(time * 1.5f + k * 20f) + 1f) / 2f)
            drawCircle(Color.White, 0.8f + k * 1.4f, Offset(x * w, y * h), alpha = a * 0.8f)
        }
        val colors = listOf(p.mint, p.cyan, p.pink, p.purple, p.yellow)
        for (k in 0 until cfg.ribbons) {
            val col = colors[k % colors.size]
            val top = FloatArray(41)
            val bottom = FloatArray(41)
            for (i in 0..40) {
                val fx = i / 40f
                top[i] = h * (0.12f + 0.06f * k) + sin(fx * 6f + time * 0.5f + k * 1.3f) * h * 0.05f +
                    sin(fx * 13f - time * 0.8f + k) * h * 0.02f
                bottom[i] = top[i] + h * (0.16f + 0.07f * sin(fx * 4f + time * 0.6f + k * 2f))
            }
            val path = Path()
            for (i in 0..40) { val x = w * i / 40f; if (i == 0) path.moveTo(x, top[i]) else path.lineTo(x, top[i]) }
            for (i in 40 downTo 0) path.lineTo(w * i / 40f, bottom[i])
            path.close()
            val a = (0.5f * cfg.brightness).coerceIn(0f, 1f)
            drawPath(
                path,
                Brush.verticalGradient(listOf(Color.Transparent, col.copy(alpha = a), col.copy(alpha = a * 0.3f), Color.Transparent), top.min(), bottom.max()),
                blendMode = BlendMode.Plus,
            )
        }
        if (cfg.mountains) {
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
}

// ═════════════════════════════════════════════
// ライフゲーム
// ═════════════════════════════════════════════

/**
 * セル・オートマトン。ルール（B/S）を変えるとコンウェイ以外の世界も作れる。
 */
class LifeSim(val cols: Int, val rows: Int) {
    var cells = BooleanArray(cols * rows); private set
    private var next = BooleanArray(cols * rows)
    val glow = FloatArray(cols * rows)
    val age = IntArray(cols * rows)
    val neighbors = IntArray(cols * rows)
    private var acc = 0f
    private var injectAcc = 0f
    var generation = 0; private set
    var population = 0; private set
    private var stale = 0
    private var lastPop = -1
    var flash = 0f; private set
    /** 人口の推移（HUD のグラフ用） */
    val history = IntArray(120)
    var historyLen = 0; private set
    private var birth = BooleanArray(9)
    private var survive = BooleanArray(9)
    private val rnd = Random(System.nanoTime())
    /** タップで置いた場所の波紋 */
    var rippleX = 0f; var rippleY = 0f; var ripple = 0f

    fun setRule(b: String, s: String) {
        birth = BooleanArray(9) { b.contains(('0' + it)) }
        survive = BooleanArray(9) { s.contains(('0' + it)) }
    }

    fun seed(cfg: LifeConfig) {
        cells.fill(false); age.fill(0)
        val d = cfg.density
        when (cfg.seed) {
            LifeSeed.RANDOM -> for (i in cells.indices) cells[i] = rnd.nextFloat() < d
            LifeSeed.SYMMETRIC -> for (r in 0 until rows) for (c in 0 until (cols + 1) / 2) {
                val v = rnd.nextFloat() < d
                cells[r * cols + c] = v
                cells[r * cols + (cols - 1 - c)] = v
            }
            LifeSeed.SOUP -> {
                val cw = cols / 3; val ch = rows / 4
                for (r in rows / 2 - ch / 2 until rows / 2 + ch / 2) for (c in cols / 2 - cw / 2 until cols / 2 + cw / 2) {
                    if (r in 0 until rows && c in 0 until cols) cells[r * cols + c] = rnd.nextFloat() < 0.5f
                }
            }
            LifeSeed.GLIDERS -> repeat((cols * rows / 120).coerceAtLeast(3)) {
                stamp(GLIDER, rnd.nextInt(cols), rnd.nextInt(rows), rnd.nextInt(4), cfg.wrap)
            }
            LifeSeed.GUN -> stamp(GOSPER_GUN, 1, rows / 4, 0, cfg.wrap)
            LifeSeed.R_PENTOMINO -> stamp(R_PENTOMINO, cols / 2, rows / 2, 0, cfg.wrap)
        }
        generation = 0; stale = 0; flash = 1f; historyLen = 0
    }

    /** パターンを置く（rot: 0〜3 で回転） */
    fun stamp(pattern: List<String>, cx: Int, cy: Int, rot: Int, wrap: Boolean) {
        for ((dy, line) in pattern.withIndex()) for ((dx, ch) in line.withIndex()) {
            if (ch != 'O') continue
            val (ox, oy) = when (rot % 4) { 0 -> dx to dy; 1 -> -dy to dx; 2 -> -dx to -dy; else -> dy to -dx }
            var c = cx + ox
            var r = cy + oy
            if (wrap) { c = ((c % cols) + cols) % cols; r = ((r % rows) + rows) % rows }
            if (c in 0 until cols && r in 0 until rows) { cells[r * cols + c] = true; glow[r * cols + c] = 1f }
        }
    }

    /** タップした場所にランダムな模様を置く */
    fun touch(c: Int, r: Int, wrap: Boolean) {
        val patterns = listOf(GLIDER, R_PENTOMINO, ACORN, LWSS, BLOCK_BURST)
        stamp(patterns[rnd.nextInt(patterns.size)], c, r, rnd.nextInt(4), wrap)
        ripple = 1f
    }

    fun update(dt: Float, cfg: LifeConfig): Boolean {
        flash = max(0f, flash - dt * 2f)
        ripple = max(0f, ripple - dt * 1.5f)
        // 消えたセルの残光（afterglow が大きいほど長く残る）
        val decay = min(1f, dt * (12f - 11f * cfg.afterglow.coerceIn(0f, 1f)))
        for (i in glow.indices) glow[i] = if (cells[i]) 1f else glow[i] * (1f - decay)
        if (cfg.injectGliders) {
            injectAcc += dt
            if (injectAcc > 3f) { injectAcc = 0f; stamp(GLIDER, rnd.nextInt(cols), rnd.nextInt(rows), rnd.nextInt(4), cfg.wrap) }
        }
        acc += dt
        if (acc < cfg.stepMs / 1000f) return true
        acc = 0f
        step(cfg)
        return true
    }

    fun step(cfg: LifeConfig) {
        var pop = 0
        for (r in 0 until rows) for (c in 0 until cols) {
            var nbs = 0
            for (dr in -1..1) for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                var rr = r + dr
                var cc = c + dc
                if (cfg.wrap) { rr = (rr + rows) % rows; cc = (cc + cols) % cols }
                else if (rr !in 0 until rows || cc !in 0 until cols) continue
                if (cells[rr * cols + cc]) nbs++
            }
            val i = r * cols + c
            val alive = if (cells[i]) survive[nbs] else birth[nbs]
            next[i] = alive
            neighbors[i] = nbs
            age[i] = if (alive) age[i] + 1 else 0
            if (alive) pop++
        }
        val tmp = cells; cells = next; next = tmp
        generation++
        population = pop
        if (historyLen < history.size) history[historyLen++] = pop
        else { System.arraycopy(history, 1, history, 0, history.size - 1); history[history.size - 1] = pop }
        stale = if (abs(pop - lastPop) <= 2) stale + 1 else 0
        lastPop = pop
        if (cfg.autoReseed && (stale > 25 || generation > cfg.maxGenerations || pop < cols * rows / 400 + 1)) seed(cfg)
    }

    companion object {
        val GLIDER = listOf(".O.", "..O", "OOO")
        val R_PENTOMINO = listOf(".OO", "OO.", ".O.")
        val ACORN = listOf(".O.....", "...O...", "OO..OOO")
        val LWSS = listOf(".O..O", "O....", "O...O", "OOOO.")
        val BLOCK_BURST = listOf("OO.OO", "O...O", ".....", "O...O", "OO.OO")
        val GOSPER_GUN = listOf(
            "........................O...........",
            "......................O.O...........",
            "............OO......OO............OO",
            "...........O...O....OO............OO",
            "OO........O.....O...OO..............",
            "OO........O...O.OO....O.O...........",
            "..........O.....O.......O...........",
            "...........O...O....................",
            "............OO......................",
        )
    }
}

@Composable
fun LifeScene(s: LauncherSettings, p: RetroPalette, paused: Boolean, modifier: Modifier = Modifier) {
    val cfg = s.life
    val density = LocalDensity.current
    val hudPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE } }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val cellPx = with(density) { cfg.cellDp.dp.toPx() }
        val cols = (constraints.maxWidth / cellPx).toInt().coerceAtLeast(8)
        val rows = (constraints.maxHeight / cellPx).toInt().coerceAtLeast(8)
        val sim = remember(cols, rows) { LifeSim(cols, rows) }
        val tick = remember { mutableIntStateOf(0) }
        val speed by rememberUpdatedState(s.sceneSpeed())
        val config by rememberUpdatedState(cfg)
        val isPaused by rememberUpdatedState(paused)
        val fps by rememberUpdatedState(LocalFpsLimit.current)
        // ルールや初期配置を変えたら作り直す
        LaunchedEffect(sim, cfg.rule) { sim.setRule(cfg.rule.birth, cfg.rule.survive) }
        LaunchedEffect(sim, cfg.seed, cfg.density) { sim.seed(cfg) }
        LaunchedEffect(sim) {
            val gate = FrameGate()
            var last = withFrameNanos { it }
            while (isActive) {
                if (isPaused) {
                    snapshotFlow { isPaused }.first { !it }
                    last = withFrameNanos { it }
                    continue
                }
                withFrameNanos { now ->
                    val dt = ((now - last) / 1e9f).coerceIn(0f, 0.1f)
                    last = now
                    // セルの世代は 0.1 秒ごとくらいなので、描き直しは最大 30fps で十分
                    if (gate.add(dt, if (fps == 0) 30 else min(fps, 30)) && sim.update(gate.consume().coerceAtMost(0.1f) * speed, config)) tick.intValue++
                }
            }
        }
        // タップした所にセルを置く
        LaunchedEffect(sim) {
            WallpaperTouch.taps.collect { pos ->
                if (!config.touch) return@collect
                sim.touch((pos.x / cellPx).toInt(), (pos.y / cellPx).toInt(), config.wrap)
                sim.rippleX = pos.x; sim.rippleY = pos.y
                tick.intValue++
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            tick.intValue
            drawRect(Brush.verticalGradient(listOf(Color(0xFF07000F), p.skyTop)))
            if (cfg.gridLines) {
                val gc = p.purple.copy(alpha = 0.18f)
                for (c in 0..cols) drawLine(gc, Offset(c * cellPx, 0f), Offset(c * cellPx, rows * cellPx), 1f)
                for (r in 0..rows) drawLine(gc, Offset(0f, r * cellPx), Offset(cols * cellPx, r * cellPx), 1f)
            }
            val gap = if (cfg.shape == CellShape.PIXEL) 0f else cellPx * cfg.cellGap.coerceIn(0f, 0.6f)
            val cs = cellPx - gap
            val cell = Size(cs, cs)
            val time = sim.generation * 0.05f
            for (i in 0 until cols * rows) {
                val g = sim.glow[i]
                if (g < 0.03f) continue
                val c = i % cols
                val r = i / cols
                val x = c * cellPx + gap / 2
                val y = r * cellPx + gap / 2
                val alive = sim.cells[i]
                val color = if (!alive) p.purple else when (cfg.color) {
                    LifeColor.AGE -> when {
                        sim.age[i] < 2 -> p.yellow
                        sim.age[i] < 8 -> p.pink
                        else -> p.cyan
                    }
                    LifeColor.RAINBOW -> Color.hsv(((c * 6f + r * 3f + time * 60f) % 360f + 360f) % 360f, 0.75f, 1f)
                    LifeColor.NEIGHBORS -> when (sim.neighbors[i]) {
                        0, 1 -> p.purple; 2 -> p.cyan; 3 -> p.mint; 4 -> p.yellow; else -> p.pink
                    }
                    LifeColor.HEAT -> {
                        val a = (sim.age[i] / 30f).coerceIn(0f, 1f)
                        if (a < 0.5f) lerp(Color.White, p.yellow, a * 2f) else lerp(p.yellow, p.pink, (a - 0.5f) * 2f)
                    }
                    LifeColor.MONO -> p.cyan
                }
                val alpha = 0.9f * g
                val center = Offset(x + cs / 2, y + cs / 2)
                if (cfg.glow && alive) drawCircle(color.copy(alpha = 0.18f), cellPx * 0.95f, center)
                when (cfg.shape) {
                    CellShape.SQUARE -> drawRoundRect(color.copy(alpha = alpha), Offset(x, y), cell, CornerRadius(cs * 0.18f))
                    CellShape.PIXEL -> drawRect(color.copy(alpha = alpha), Offset(x, y), cell)
                    CellShape.ROUND -> drawCircle(color.copy(alpha = alpha), cs / 2, center)
                    CellShape.DIAMOND -> {
                        val d = Path().apply {
                            moveTo(center.x, y); lineTo(x + cs, center.y); lineTo(center.x, y + cs); lineTo(x, center.y); close()
                        }
                        drawPath(d, color.copy(alpha = alpha))
                    }
                }
            }
            if (sim.flash > 0f) drawRect(p.pink.copy(alpha = sim.flash * 0.25f))
            if (sim.ripple > 0f) {
                val rr = (1f - sim.ripple) * cellPx * 12f
                drawCircle(p.yellow.copy(alpha = sim.ripple), rr, Offset(sim.rippleX, sim.rippleY), style = Stroke(3f))
            }
            if (cfg.hud) drawLifeHud(sim, cfg, p, hudPaint)
        }
    }
}

/** 世代数・人口・ルールと人口グラフ（位置は設定で四隅から選べる） */
private fun DrawScope.drawLifeHud(sim: LifeSim, cfg: LifeConfig, p: RetroPalette, paint: Paint) {
    val pad = 8.dp.toPx()
    val gw = 120.dp.toPx()
    val gh = 34.dp.toPx()
    val boxW = gw + pad * 2
    val boxH = gh + 46.dp.toPx()
    val side = 14.dp.toPx()
    val margin = cfg.hudMarginDp.dp.toPx()
    val left = when (cfg.hudCorner) {
        ScreenCorner.TOP_LEFT, ScreenCorner.BOTTOM_LEFT -> side - pad
        else -> size.width - side - boxW + pad
    }
    val top = when (cfg.hudCorner) {
        ScreenCorner.TOP_LEFT, ScreenCorner.TOP_RIGHT -> margin
        else -> size.height - margin - boxH
    }.coerceIn(0f, (size.height - boxH).coerceAtLeast(0f))
    val x0 = left + pad
    val y0 = top + 18.dp.toPx()
    drawRoundRect(Color.Black.copy(alpha = 0.45f), Offset(left, top), Size(boxW, boxH), CornerRadius(8.dp.toPx()))
    drawIntoCanvas { cv ->
        paint.textSize = 11.dp.toPx()
        paint.color = p.cyan.toArgb()
        cv.nativeCanvas.drawText("GEN ${sim.generation}  POP ${sim.population}", x0, y0, paint)
        paint.color = p.pink.toArgb()
        cv.nativeCanvas.drawText("${cfg.rule.label}  B${cfg.rule.birth}/S${cfg.rule.survive}", x0, y0 + 14.dp.toPx(), paint)
    }
    val n = sim.historyLen
    if (n > 1) {
        val maxV = (0 until n).maxOf { sim.history[it] }.coerceAtLeast(1)
        val path = Path()
        val gTop = y0 + 20.dp.toPx()
        for (k in 0 until n) {
            val px = x0 + gw * k / (sim.history.size - 1)
            val py = gTop + gh - gh * sim.history[k] / maxV
            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, p.yellow, style = Stroke(1.5.dp.toPx()))
    }
}
