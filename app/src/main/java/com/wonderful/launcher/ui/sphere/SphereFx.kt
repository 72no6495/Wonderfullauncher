// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (c) 2026 72no6495

package com.wonderful.launcher.ui.sphere

import androidx.compose.ui.graphics.Color
import com.wonderful.launcher.data.LauncherSettings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * スフィアの見た目のエフェクト（描画位置だけを変え、球体の計算には影響しない）。
 *
 * - ゴム: 各アイコンがバネで本来の位置を追いかけ、速さに応じて伸び縮みする
 * - 浮遊: ふわふわ上下に揺れる
 * - 残像: 過去の位置にうっすら残る
 * - 火花: タップした所から粒が飛び散る
 */
class SphereFx {
    var time = 0f
        private set
    private var pendingDt = 0f

    // 表示位置（バネ）
    var x = FloatArray(0); private set
    var y = FloatArray(0); private set
    private var vx = FloatArray(0)
    private var vy = FloatArray(0)
    /** 伸び率（1 = 伸びなし）と伸びる向き（度） */
    var stretch = FloatArray(0); private set
    var stretchAngle = FloatArray(0); private set
    /** 伸びる向きと垂直な方向の縮み率 */
    var squash = FloatArray(0); private set
    var floatY = FloatArray(0); private set
    private var lastTX = FloatArray(0)
    private var lastTY = FloatArray(0)
    private var trailClock = 0f

    // 残像
    private var trailX = FloatArray(0)
    private var trailY = FloatArray(0)
    private var trailHead = 0
    private var trailFilled = 0
    private var settle = 0

    // 火花
    private val cap = 240
    private val pX = FloatArray(cap)
    private val pY = FloatArray(cap)
    private val pVX = FloatArray(cap)
    private val pVY = FloatArray(cap)
    private val pLife = FloatArray(cap)
    private val pColor = arrayOfNulls<Color>(cap)
    private var pNext = 0
    var particlesAlive = false; private set

    /** 最後の prepare で何か動いていたか（フレームを回し続ける必要があるか） */
    var moving = false; private set

    fun advance(dt: Float) {
        val d = dt.coerceIn(0f, 0.05f)
        time += d
        pendingDt += d
    }

    fun needsFrames(s: LauncherSettings): Boolean =
        moving || particlesAlive || s.iconFloat > 0f || s.rainbowGlow || s.glowPulse || settle > 0

    fun reset() {
        x = FloatArray(0)
    }

    private fun ensure(n: Int) {
        if (x.size == n) return
        x = FloatArray(n) { Float.NaN }; y = FloatArray(n); vx = FloatArray(n); vy = FloatArray(n)
        stretch = FloatArray(n) { 1f }; stretchAngle = FloatArray(n); floatY = FloatArray(n)
        squash = FloatArray(n) { 1f }; lastTX = FloatArray(n); lastTY = FloatArray(n)
        trailX = FloatArray(n * TRAIL_MAX); trailY = FloatArray(n * TRAIL_MAX)
        trailHead = 0; trailFilled = 0
    }

    /** engine.project の後に 1 フレーム 1 回呼ぶ */
    fun prepare(e: SphereEngine, s: LauncherSettings, iconPx: Float) {
        val n = e.count
        ensure(n)
        val dt = pendingDt.coerceIn(0f, 0.05f)
        pendingDt = 0f
        val rub = s.rubber.coerceIn(0f, 1f)
        moving = false

        // バネの硬さ（柔らかいほど遅れてついてくる）と減衰（弾みが大きいほど長く揺れる）
        val k = 650f - 520f * rub
        val zeta = (1f - 0.88f * s.rubberBounce.coerceIn(0f, 1f)).coerceAtLeast(0.08f)
        val c = 2f * zeta * sqrt(k)
        val sub = 4
        val h = dt / sub
        val baseLag = s.rubberMaxLag * iconPx
        val maxStretch = 0.55f * s.rubberStretch.coerceIn(0f, 1f) * rub

        for (i in 0 until n) {
            val tx = e.px[i]
            val ty = e.py[i]
            if (x[i].isNaN() || rub <= 0.001f || dt <= 0f) {
                if (x[i].isNaN() || rub <= 0.001f) { x[i] = tx; y[i] = ty; vx[i] = 0f; vy[i] = 0f }
                lastTX[i] = tx; lastTY[i] = ty
                stretch[i] = 1f; squash[i] = 1f
                floatY[i] = floatOffset(s, i, iconPx)
                continue
            }
            // 本来の位置の移動速度（速く回すほど遅れ幅を小さくして、ぐちゃっとならないようにする）
            val tvx = (tx - lastTX[i]) / dt
            val tvy = (ty - lastTY[i]) / dt
            lastTX[i] = tx; lastTY[i] = ty
            val tSpeed = sqrt(tvx * tvx + tvy * tvy)
            val maxLag = if (s.rubberSpeedLimit) baseLag / (1f + tSpeed / 2500f) else baseLag

            repeat(sub) {
                val ax = k * (tx - x[i]) - c * vx[i]
                val ay = k * (ty - y[i]) - c * vy[i]
                vx[i] += ax * h; vy[i] += ay * h
                x[i] += vx[i] * h; y[i] += vy[i] * h
            }
            // 遅れすぎたら引き戻す（離れていく向きの速度も消す）
            val dx = x[i] - tx
            val dy = y[i] - ty
            val dist = sqrt(dx * dx + dy * dy)
            if (dist > maxLag && dist > 0.001f) {
                val nx = dx / dist
                val ny = dy / dist
                x[i] = tx + nx * maxLag
                y[i] = ty + ny * maxLag
                val away = vx[i] * nx + vy[i] * ny
                if (away > 0f) { vx[i] -= away * nx; vy[i] -= away * ny }
            }
            if (dist > 0.4f || abs(vx[i]) + abs(vy[i]) > 6f) moving = true

            // 伸び縮み: 動く向きに伸び、横は体積を保つ程度に少しだけ縮む（潰れない）
            val speed = sqrt(vx[i] * vx[i] + vy[i] * vy[i])
            val target = 1f + min(speed / 2200f, 1f) * maxStretch
            stretch[i] += (target - stretch[i]) * 0.35f
            squash[i] = (1f / sqrt(stretch[i])).coerceIn(0.8f, 1f)
            if (speed > 25f) stretchAngle[i] = (atan2(vy[i], vx[i]) * 180f / PI).toFloat()
            floatY[i] = floatOffset(s, i, iconPx)
        }

        // 残像: 一定時間ごとに位置を記録（毎フレームだと重なって見えないため）
        if (s.trailLength > 0f && n > 0) {
            trailClock += dt
            val interval = 0.016f + s.trailSpacing.coerceIn(0f, 1f) * 0.09f
            if (trailClock >= interval) {
                trailClock = 0f
                trailHead = (trailHead + 1) % TRAIL_MAX
                for (i in 0 until n) {
                    trailX[i * TRAIL_MAX + trailHead] = x[i]
                    trailY[i * TRAIL_MAX + trailHead] = y[i] + floatY[i]
                }
                trailFilled = min(TRAIL_MAX, trailFilled + 1)
            }
            settle = if (moving || e.isAnimating) 120 else (settle - 1).coerceAtLeast(0)
        } else {
            trailFilled = 0; settle = 0
        }

        // 火花
        particlesAlive = false
        for (j in 0 until cap) {
            if (pLife[j] <= 0f) continue
            pVY[j] += 1400f * dt
            pVX[j] *= 1f - min(1f, dt * 1.5f)
            pX[j] += pVX[j] * dt
            pY[j] += pVY[j] * dt
            pLife[j] -= dt * 1.3f
            if (pLife[j] > 0f) particlesAlive = true
        }
    }

    private fun floatOffset(s: LauncherSettings, i: Int, iconPx: Float) =
        if (s.iconFloat > 0f) sin(time * 1.7f + i * 0.9f) * s.iconFloat * iconPx * 0.14f else 0f

    /** 残像の数 */
    fun trailCount(s: LauncherSettings): Int =
        if (s.trailLength <= 0f) 0 else min(trailFilled - 1, 2 + (s.trailLength * (TRAIL_MAX - 3)).toInt()).coerceAtLeast(0)

    /** k 個前の残像位置 */
    fun trailX(i: Int, k: Int) = trailX[i * TRAIL_MAX + ((trailHead - k + TRAIL_MAX * 4) % TRAIL_MAX)]
    fun trailY(i: Int, k: Int) = trailY[i * TRAIL_MAX + ((trailHead - k + TRAIL_MAX * 4) % TRAIL_MAX)]

    fun burst(cx: Float, cy: Float, colors: List<Color>, scale: Float) {
        val r = Random(System.nanoTime())
        repeat(28) {
            val j = pNext
            pNext = (pNext + 1) % cap
            val a = r.nextFloat() * 2f * PI.toFloat()
            val sp = (300f + r.nextFloat() * 700f) * scale
            pX[j] = cx; pY[j] = cy
            pVX[j] = cos(a) * sp; pVY[j] = sin(a) * sp - 250f * scale
            pLife[j] = 0.7f + r.nextFloat() * 0.5f
            pColor[j] = colors[r.nextInt(colors.size)]
        }
        particlesAlive = true
    }

    /** 飛んでいる粒をすぐに消す */
    fun clearParticles() {
        pLife.fill(0f)
        particlesAlive = false
    }

    inline fun forEachParticle(block: (x: Float, y: Float, life: Float, color: Color) -> Unit) {
        for (j in 0 until particleCap) {
            val life = particleLife(j)
            if (life > 0f) block(particleX(j), particleY(j), life, particleColor(j))
        }
    }

    val particleCap get() = cap
    fun particleLife(j: Int) = pLife[j]
    fun particleX(j: Int) = pX[j]
    fun particleY(j: Int) = pY[j]
    fun particleColor(j: Int) = pColor[j] ?: Color.White

    companion object {
        const val TRAIL_MAX = 16
    }
}
