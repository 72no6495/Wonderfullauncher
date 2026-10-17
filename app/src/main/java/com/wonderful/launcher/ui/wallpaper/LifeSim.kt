package com.wonderful.launcher.ui.wallpaper

import com.wonderful.launcher.data.LifeConfig
import com.wonderful.launcher.data.LifeSeed
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

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
    /** 盤面の指紋（1 つ前・2 つ前）。同じなら止まっている（周期 1・2 の振動子も含む） */
    private var hash1 = 0L
    private var hash2 = 0L
    /** 8×8 マスごとの「どれだけ変化しているか」（追尾カメラ用） */
    val block = 8
    val blocksX = (cols + block - 1) / block
    val blocksY = (rows + block - 1) / block
    val heat = FloatArray(blocksX * blocksY)
    private val changes = IntArray(blocksX * blocksY)
    /** 直前の世代で変わったセルの数 */
    var changed = 0; private set
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
            LifeSeed.R_PENTOMINO -> stamp(R_PENTOMINO, cols / 2 - 1, rows / 2 - 1, 0, cfg.wrap)
            LifeSeed.ACORN -> stamp(ACORN, cols / 2 - 3, rows / 2 - 1, 0, cfg.wrap)
        }
        generation = 0; stale = 0; flash = 1f; historyLen = 0
        lastPop = -1; hash1 = 0L; hash2 = 0L
        heat.fill(0f)
        population = cells.count { it }
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
        var hash = 1469598103934665603L
        var nChanged = 0
        changes.fill(0)
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
            if (alive) { pop++; hash = (hash xor i.toLong()) * 1099511628211L }
            if (alive != cells[i]) { nChanged++; changes[(r / block) * blocksX + c / block]++ }
        }
        changed = nChanged
        for (b in heat.indices) heat[b] = heat[b] * 0.82f + changes[b]
        val tmp = cells; cells = next; next = tmp
        generation++
        population = pop
        if (historyLen < history.size) history[historyLen++] = pop
        else { System.arraycopy(history, 1, history, 0, history.size - 1); history[history.size - 1] = pop }
        // 止まった（何も変わらない・2 世代ごとのくり返しだけ）かどうかは盤面そのもので判定する。
        // 以前は人口の増減で見ていたので、ゆっくり育つ R ペントミノなどを「止まった」と誤判定していた
        stale = if (nChanged == 0 || hash == hash1 || hash == hash2) stale + 1 else 0
        hash2 = hash1; hash1 = hash
        lastPop = pop
        // 小さな模様から始める初期配置は、人口が少なくても作り直さない（作り直すと固まって見える）
        val pattern = cfg.seed == LifeSeed.R_PENTOMINO || cfg.seed == LifeSeed.ACORN || cfg.seed == LifeSeed.GUN
        val limit = if (pattern) max(cfg.maxGenerations, 6000) else cfg.maxGenerations
        val tooFew = if (pattern) pop == 0 else pop < cols * rows / 400 + 1
        if (cfg.autoReseed && (stale > 30 || generation > limit || tooFew)) seed(cfg)
    }

    /**
     * 一番変化している所（セル座標の中心）。周りの 3×3 ブロックも足して、ちらつかないようにする。
     * 変化がほとんど無ければ null（＝落ち着いた）
     */
    fun hotspot(wrap: Boolean): Pair<Float, Float>? {
        var best = 0f
        var bx = 0
        var by = 0
        for (y in 0 until blocksY) for (x in 0 until blocksX) {
            var sum = 0f
            for (dy in -1..1) for (dx in -1..1) {
                var xx = x + dx
                var yy = y + dy
                if (wrap) { xx = (xx + blocksX) % blocksX; yy = (yy + blocksY) % blocksY }
                else if (xx !in 0 until blocksX || yy !in 0 until blocksY) continue
                sum += heat[yy * blocksX + xx] * (if (dx == 0 && dy == 0) 2f else 1f)
            }
            if (sum > best) { best = sum; bx = x; by = y }
        }
        if (best < 6f) return null
        return (bx * block + block / 2f) to (by * block + block / 2f)
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
