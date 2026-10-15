// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (c) 2026 72no6495

package com.wonderful.launcher.ui.sphere

import com.wonderful.launcher.data.LauncherSettings
import com.wonderful.launcher.data.SphereLayout
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * スフィアの 3D 計算を担当する（描画とは独立）。
 *
 * - 各アイコンは「レイアウト空間」の座標を持ち、回転行列 [m] をかけて画面に投影する
 * - ドラッグで回転、指を離すと慣性で回り続け、しばらく触らないと自動回転に戻る
 * - アプリが増減すると、各アイコンが新しい位置へなめらかに移動する
 */
class SphereEngine {

    /** 回転行列（行優先 3x3） */
    private val m = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    private val tmp = FloatArray(9)
    private val rot = FloatArray(9)

    /** 角速度 [rad/s]。velY は横ドラッグ（Y 軸回り）、velX は縦ドラッグ（X 軸回り） */
    private var velY = 0f
    private var velX = 0f
    var touching = false
        private set
    private var idleTime = 10f
    private var autoFactor = 0f
    private var autoAngle = 0f
    private var resetting = false

    /** ピンチ中のサイズ（確定前） */
    var sizeOverride: Float? = null

    // ── アイテム ──
    var keys: List<String> = emptyList()
        private set
    private var target = FloatArray(0)
    private val current = HashMap<String, FloatArray>()
    var layout: SphereLayout = SphereLayout.SPHERE
        private set

    // ── 投影結果（描画とタップ判定で使う） ──
    var count = 0
        private set
    var px = FloatArray(0); private set
    var py = FloatArray(0); private set
    var size = FloatArray(0); private set
    var depth = FloatArray(0); private set   // 0=奥 1=手前
    var alpha = FloatArray(0); private set
    /** 奥から手前の順に並んだインデックス */
    var order = IntArray(0); private set

    /** タップ時のバウンド演出 */
    var pulseIndex = -1; private set
    var pulseTime = 1f; private set
    var pulseStrong = false; private set

    /** 直前の step で何か動いていたか */
    var isAnimating = false
        private set

    /** ワイヤーフレーム（レイアウト空間の折れ線群） */
    var wires: List<FloatArray> = emptyList(); private set

    fun setItems(newKeys: List<String>, newLayout: SphereLayout, animate: Boolean) {
        val layoutChanged = newLayout != layout || wires.isEmpty()
        keys = newKeys
        layout = newLayout
        val n = newKeys.size
        target = FloatArray(n * 3)
        for (i in 0 until n) layoutPoint(newLayout, i, n, target, i * 3)
        current.keys.retainAll(newKeys.toSet())
        newKeys.forEachIndexed { i, k ->
            if (k !in current) {
                // 新しいアイコンは中心から飛び出してくる
                current[k] = if (animate) floatArrayOf(0f, 0f, 0f)
                else floatArrayOf(target[i * 3], target[i * 3 + 1], target[i * 3 + 2])
            }
        }
        if (px.size != n) {
            px = FloatArray(n); py = FloatArray(n); size = FloatArray(n)
            depth = FloatArray(n); alpha = FloatArray(n); order = IntArray(n) { it }
        }
        count = n
        pulseIndex = -1
        if (layoutChanged) {
            wires = buildWires(newLayout, n)
            identity()
            // リングや銀河は少し傾けて立体感を出す
            if (newLayout == SphereLayout.RING || newLayout == SphereLayout.GALAXY) rotateWorld(0f, 0.5f)
        }
    }

    /**
     * 何もしていない時に、あと何秒で自動回転が始まるか。
     * 自動回転しない設定なら null（触られるまで眠ってよい）。
     */
    fun secondsUntilAutoRotate(s: LauncherSettings): Float? =
        if (!s.autoRotate || touching) null else (s.autoRotateResumeSec - idleTime).coerceAtLeast(0f)

    /** 眠っていた時間を待ち時間に足す（フレームを回さずに自動回転の再開を待つため） */
    fun addIdle(seconds: Float) {
        if (!touching) idleTime += seconds
    }

    fun touchDown() {
        touching = true
        velX = 0f; velY = 0f
        idleTime = 0f
        resetting = false
    }

    fun touchUp() {
        touching = false
        idleTime = 0f
    }

    /** ドラッグ量（ラジアン）だけ回転 */
    fun drag(angleY: Float, angleX: Float) {
        rotateWorld(angleY, angleX)
    }

    fun fling(angVelY: Float, angVelX: Float) {
        velY = angVelY.coerceIn(-25f, 25f)
        velX = angVelX.coerceIn(-25f, 25f)
    }

    fun spin(amount: Float) {
        velY += amount
    }

    fun reset() {
        resetting = true
        velX = 0f; velY = 0f
    }

    fun pulse(index: Int, strong: Boolean) {
        pulseIndex = index
        pulseTime = 0f
        pulseStrong = strong
    }

    /**
     * 1 フレーム進める。何か動いたら true を返す（再描画が必要）。
     */
    fun step(dt: Float, s: LauncherSettings, entrance: Boolean): Boolean {
        var moved = false
        val d = dt.coerceIn(0f, 0.05f)

        // 慣性
        if (!touching && (abs(velX) > 0.002f || abs(velY) > 0.002f)) {
            rotateWorld(velY * d, velX * d)
            val friction = 0.80f + 0.185f * s.inertia.coerceIn(0f, 1f)
            val k = friction.pow(d * 60f)
            velX *= k; velY *= k
            moved = true
            idleTime = 0f
        } else if (!touching) {
            velX = 0f; velY = 0f
            idleTime += d
        }

        // 回転リセット（単位行列へ近づける）
        if (resetting) {
            val k = 1f - exp(-d * 8f)
            for (i in 0 until 9) {
                val id = if (i % 4 == 0) 1f else 0f
                m[i] += (id - m[i]) * k
            }
            orthonormalize()
            if (abs(m[0] - 1f) < 0.0005f && abs(m[4] - 1f) < 0.0005f && abs(m[8] - 1f) < 0.0005f) {
                identity(); resetting = false
            }
            moved = true
        }

        // 自動回転（しばらく触らないとゆっくり再開）
        val wantAuto = s.autoRotate && !touching && !resetting && idleTime > s.autoRotateResumeSec
        autoFactor = if (wantAuto) (autoFactor + d * 0.8f).coerceAtMost(1f) else 0f
        if (autoFactor > 0f) {
            val sign = if (s.reverseAutoRotate) -1f else 1f
            val angle = sign * s.autoRotateSpeed * autoFactor * d * s.animSpeed
            autoAngle += angle
            rotateTilted(angle, Math.toRadians(s.autoRotateTilt.toDouble()).toFloat())
            moved = true
        }

        // アイコン位置を目標へなめらかに移動
        val lerp = if (entrance) 1f - exp(-d * 5f * s.animSpeed) else 1f
        for (i in 0 until count) {
            val c = current[keys[i]] ?: continue
            val tx = target[i * 3]; val ty = target[i * 3 + 1]; val tz = target[i * 3 + 2]
            if (abs(c[0] - tx) + abs(c[1] - ty) + abs(c[2] - tz) > 0.0005f) {
                c[0] += (tx - c[0]) * lerp
                c[1] += (ty - c[1]) * lerp
                c[2] += (tz - c[2]) * lerp
                moved = true
            }
        }

        if (pulseTime < 1f) {
            pulseTime = (pulseTime + d / 0.6f * s.animSpeed).coerceAtMost(1f)
            moved = true
        }
        isAnimating = moved
        return moved
    }

    /** 画面座標へ投影する */
    fun project(cx: Float, cy: Float, radius: Float, iconPx: Float, s: LauncherSettings) {
        val camDist = 1.6f + (1f - s.perspective.coerceIn(0f, 1f)) * 8f
        for (i in 0 until count) {
            val c = current[keys[i]] ?: continue
            val x = c[0]; val y = c[1]; val z = c[2]
            val rx = m[0] * x + m[1] * y + m[2] * z
            val ry = m[3] * x + m[4] * y + m[5] * z
            val rz = m[6] * x + m[7] * y + m[8] * z
            val persp = camDist / (camDist - rz)
            val t = ((rz + 1f) * 0.5f).coerceIn(0f, 1f)
            px[i] = cx + rx * radius * persp
            py[i] = cy - ry * radius * persp
            size[i] = iconPx * persp * (1f - s.depthScale * (1f - t))
            depth[i] = t
            alpha[i] = (1f - s.depthFade * (1f - t)).coerceIn(0.05f, 1f)
        }
        // 挿入ソート（前フレームとほぼ同順なので速い）
        if (order.size != count) order = IntArray(count) { it }
        for (i in 1 until count) {
            val v = order[i]
            var j = i - 1
            while (j >= 0 && depth[order[j]] > depth[v]) {
                order[j + 1] = order[j]; j--
            }
            order[j + 1] = v
        }
    }

    /** 回転後の点を投影（ワイヤーフレーム用）。out = [sx, sy, depth] */
    fun projectPoint(x: Float, y: Float, z: Float, cx: Float, cy: Float, radius: Float, s: LauncherSettings, out: FloatArray) {
        val camDist = 1.6f + (1f - s.perspective.coerceIn(0f, 1f)) * 8f
        val rx = m[0] * x + m[1] * y + m[2] * z
        val ry = m[3] * x + m[4] * y + m[5] * z
        val rz = m[6] * x + m[7] * y + m[8] * z
        val persp = camDist / (camDist - rz)
        out[0] = cx + rx * radius * persp
        out[1] = cy - ry * radius * persp
        out[2] = ((rz + 1f) * 0.5f).coerceIn(0f, 1f)
    }

    /** 手前のアイコンから順にタップ判定 */
    fun hitTest(x: Float, y: Float): Int {
        for (k in count - 1 downTo 0) {
            val i = order.getOrNull(k) ?: continue
            if (depth[i] < 0.35f) continue // 奥側は押せない
            val r = size[i] * 0.55f
            val dx = x - px[i]; val dy = y - py[i]
            if (dx * dx + dy * dy <= r * r) return i
        }
        return -1
    }

    fun frontIndex(): Int = if (count == 0) -1 else order[count - 1]

    // ── 行列演算 ──

    private fun identity() {
        m.fill(0f); m[0] = 1f; m[4] = 1f; m[8] = 1f
    }

    /** ワールド座標で Y 軸回りに a、X 軸回りに b 回転する（M = Rx(b)·Ry(a)·M） */
    private fun rotateWorld(a: Float, b: Float) {
        val ca = cos(a); val sa = sin(a)
        val cb = cos(b); val sb = sin(b)
        // R = Rx(b) * Ry(a)
        rot[0] = ca; rot[1] = 0f; rot[2] = sa
        rot[3] = sb * sa; rot[4] = cb; rot[5] = -sb * ca
        rot[6] = -cb * sa; rot[7] = sb; rot[8] = cb * ca
        mulLeft(rot)
    }

    /** 傾いた軸のまわりに回転（自動回転用） */
    private fun rotateTilted(angle: Float, tilt: Float) {
        // 軸 = (sin(tilt), cos(tilt), 0) を Rodrigues の公式で
        val ax = sin(tilt); val ay = cos(tilt); val az = 0f
        val c = cos(angle); val s = sin(angle); val t = 1f - c
        rot[0] = t * ax * ax + c; rot[1] = t * ax * ay - s * az; rot[2] = t * ax * az + s * ay
        rot[3] = t * ax * ay + s * az; rot[4] = t * ay * ay + c; rot[5] = t * ay * az - s * ax
        rot[6] = t * ax * az - s * ay; rot[7] = t * ay * az + s * ax; rot[8] = t * az * az + c
        mulLeft(rot)
    }

    private fun mulLeft(r: FloatArray) {
        for (row in 0 until 3) for (col in 0 until 3) {
            tmp[row * 3 + col] = r[row * 3] * m[col] + r[row * 3 + 1] * m[3 + col] + r[row * 3 + 2] * m[6 + col]
        }
        System.arraycopy(tmp, 0, m, 0, 9)
        orthonormalize()
    }

    /** 誤差の蓄積で行列が歪まないように直交化 */
    private fun orthonormalize() {
        var l = sqrt(m[0] * m[0] + m[1] * m[1] + m[2] * m[2])
        m[0] /= l; m[1] /= l; m[2] /= l
        val d = m[0] * m[3] + m[1] * m[4] + m[2] * m[5]
        m[3] -= d * m[0]; m[4] -= d * m[1]; m[5] -= d * m[2]
        l = sqrt(m[3] * m[3] + m[4] * m[4] + m[5] * m[5])
        m[3] /= l; m[4] /= l; m[5] /= l
        m[6] = m[1] * m[5] - m[2] * m[4]
        m[7] = m[2] * m[3] - m[0] * m[5]
        m[8] = m[0] * m[4] - m[1] * m[3]
    }

    companion object {
        private val GOLDEN = (PI * (3.0 - sqrt(5.0))).toFloat()

        /** i 番目のアイコンのレイアウト空間座標 */
        fun layoutPoint(layout: SphereLayout, i: Int, n: Int, out: FloatArray, o: Int) {
            when (layout) {
                SphereLayout.SPHERE -> {
                    // フィボナッチ球: 均等に散らばる
                    val y = if (n == 1) 0f else 1f - 2f * (i + 0.5f) / n
                    val r = sqrt((1f - y * y).coerceAtLeast(0f))
                    val th = GOLDEN * i
                    out[o] = cos(th) * r; out[o + 1] = y; out[o + 2] = sin(th) * r
                }
                SphereLayout.RING -> {
                    // トーラス（ドーナツ）状
                    val u = 2f * PI.toFloat() * i / n.coerceAtLeast(1)
                    val v = 2f * PI.toFloat() * ((i * 0.618034f) % 1f)
                    val minor = 0.28f
                    val k = 1f / (1f + minor)
                    out[o] = (1f + minor * cos(v)) * cos(u) * k
                    out[o + 1] = minor * sin(v) * k
                    out[o + 2] = (1f + minor * cos(v)) * sin(u) * k
                }
                SphereLayout.HELIX -> {
                    val perTurn = 9f
                    val a = 2f * PI.toFloat() * i / perTurn
                    val y = if (n == 1) 0f else 1.1f - 2.2f * i / (n - 1)
                    out[o] = cos(a) * 0.72f; out[o + 1] = y; out[o + 2] = sin(a) * 0.72f
                }
                SphereLayout.GALAXY -> {
                    val arms = 3
                    val arm = i % arms
                    val t = (i / arms).toFloat() / (n / arms).coerceAtLeast(1)
                    val r = 0.18f + 0.85f * sqrt(t)
                    val a = arm * 2f * PI.toFloat() / arms + t * 3.6f
                    val jitter = (((i * 7919) % 100) / 100f - 0.5f) * 0.14f
                    out[o] = cos(a) * r; out[o + 1] = jitter; out[o + 2] = sin(a) * r
                }
            }
        }

        fun buildWires(layout: SphereLayout, n: Int): List<FloatArray> {
            val wires = mutableListOf<FloatArray>()
            val seg = 48
            fun circle(fn: (Float) -> Triple<Float, Float, Float>) {
                val arr = FloatArray((seg + 1) * 3)
                for (k in 0..seg) {
                    val (x, y, z) = fn(2f * PI.toFloat() * k / seg)
                    arr[k * 3] = x; arr[k * 3 + 1] = y; arr[k * 3 + 2] = z
                }
                wires += arr
            }
            when (layout) {
                SphereLayout.SPHERE -> {
                    for (lat in listOf(-60, -30, 0, 30, 60)) {
                        val rad = Math.toRadians(lat.toDouble()).toFloat()
                        val y = sin(rad); val r = cos(rad)
                        circle { a -> Triple(cos(a) * r, y, sin(a) * r) }
                    }
                    for (lon in 0 until 180 step 30) {
                        val rad = Math.toRadians(lon.toDouble()).toFloat()
                        circle { a -> Triple(cos(a) * cos(rad), sin(a), cos(a) * sin(rad)) }
                    }
                }
                SphereLayout.RING -> {
                    val k = 1f / 1.28f
                    for (rr in listOf(0.72f, 1f, 1.28f)) circle { a -> Triple(cos(a) * rr * k, 0f, sin(a) * rr * k) }
                }
                SphereLayout.HELIX -> {
                    val pts = (n.coerceAtLeast(2)) * 4
                    val arr = FloatArray(pts * 3)
                    for (k in 0 until pts) {
                        val i = k / 4f
                        val a = 2f * PI.toFloat() * i / 9f
                        val y = 1.1f - 2.2f * i / (n.coerceAtLeast(2) - 1)
                        arr[k * 3] = cos(a) * 0.72f; arr[k * 3 + 1] = y; arr[k * 3 + 2] = sin(a) * 0.72f
                    }
                    wires += arr
                }
                SphereLayout.GALAXY -> {
                    for (rr in listOf(0.35f, 0.7f, 1.05f)) circle { a -> Triple(cos(a) * rr, 0f, sin(a) * rr) }
                }
            }
            return wires
        }
    }
}
