package com.wonderful.launcher.system

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import com.wonderful.launcher.data.CylinderConfig
import com.wonderful.launcher.data.CylinderItem
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** シリンダーの穴 1 つ分の中身 */
class CylinderEntry(val item: CylinderItem, val label: String, val short: String, var icon: Bitmap? = null)

/**
 * ホログラムのリボルバーのシリンダー。画面の右端に中心があり、左半分だけが見える。
 * 上下にドラッグすると回り、左端（9 時の位置）に来た穴が選ばれる。穴をタップすると起動。
 * 下から上へ素早くはじくと引っ込む。
 *
 * 軽くするため、動いている間（出る・引っ込む・回転・ゆらぎ ON）だけフレームを回し、
 * 止まっている時は一切描き直さない。
 */
@SuppressLint("ViewConstructor")
class CylinderView(
    context: Context,
    private val cfg: CylinderConfig,
    private val entries: List<CylinderEntry>,
    /** 円の半径と、ウィンドウの中での中心（右端・縦の位置） */
    private val radius: Float,
    private val centerY: Float,
    private val onPick: (CylinderEntry) -> Unit,
    private val onClosed: () -> Unit,
) : View(context) {

    private val density = resources.displayMetrics.density
    private val color = cfg.color.argb.toInt()
    private val n = max(6, entries.size)
    private val step = (2 * PI / n).toFloat()
    private val chamberOrbit = radius * 0.62f
    private val chamberR = min(radius * 0.25f, chamberOrbit * sin(PI / n).toFloat() * 0.84f)

    // ── 状態 ──
    /** 回転角。0 で 0 番目の穴が左端 */
    private var angle = 0f
    private var angVel = 0f
    private var snapping = false
    /** 出てくる（1 へ）・引っ込む（0 へ）の進み具合 */
    private var open = 0f
    private var closing = false
    private var selected = 0
    private var time = 0f

    // ── タッチ ──
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var tracker: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var prevTouchAngle = 0f
    private var prevTouchTime = 0L
    private val labelRect = RectF()

    // ── 描画用（毎フレーム作らない） ──
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bodyShader = RadialGradient(
        0f, 0f, radius,
        intArrayOf(withAlpha(color, 18), withAlpha(color, 30), withAlpha(color, 70)),
        floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP,
    )
    private val star = Path()
    private val scan = FloatArray(((radius * 2 / (4 * density)).toInt() + 2) * 4)
    private val iconSrc = Rect()
    private val iconDst = RectF()

    // ─────────────────────────────────────────────
    // アニメーション
    // ─────────────────────────────────────────────

    private var running = false
    private var lastFrame = 0L
    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(now: Long) {
            if (!running) return
            val dt = if (lastFrame == 0L) 0.016f else ((now - lastFrame) / 1e9f).coerceIn(0f, 0.05f)
            lastFrame = now
            if (step(dt)) {
                invalidate()
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                invalidate()
                running = false
                lastFrame = 0L
            }
        }
    }

    private fun startFrames() {
        if (running) return
        running = true
        lastFrame = 0L
        Choreographer.getInstance().postFrameCallback(frame)
    }

    /** 1 フレーム進める。まだ動き続けるなら true */
    private fun step(dt: Float): Boolean {
        time += dt
        var busy = false
        if (closing) {
            open = max(0f, open - dt / 0.18f)
            if (open <= 0f) { onClosed(); return false }
            busy = true
        } else if (open < 1f) {
            open = min(1f, open + dt / 0.34f)
            busy = true
        }
        if (!dragging) {
            if (abs(angVel) > 1.2f) {
                angle += angVel * dt
                angVel *= exp(-3.2f * dt)
                busy = true
            } else {
                angVel = 0f
                // 一番近い穴が左端にぴったり来るように
                val target = (angle / step).roundToInt() * step
                val d = target - angle
                if (abs(d) > 0.0005f) {
                    angle += d * min(1f, dt * 14f)
                    busy = true
                } else angle = target
            }
        }
        updateSelected()
        // ホログラムのゆらぎ（OFF の時は止まっている間に描き直さない）
        if (cfg.hologramMotion) busy = true
        return busy
    }

    private fun updateSelected() {
        val i = Math.floorMod(-(angle / step).roundToInt(), n)
        if (i != selected) {
            selected = i
            if (cfg.haptics && open >= 1f) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    fun close() {
        if (closing) return
        closing = true
        startFrames()
    }

    // ─────────────────────────────────────────────
    // 描画
    // ─────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        // にゅっと出る：右から滑り込み、少しはみ出してから戻る（オーバーシュート）
        val e = easeOutBack(open)
        val cx = width + (1f - e) * radius * 1.1f
        val cy = centerY
        // ホログラムのちらつき
        val flicker = if (cfg.hologramMotion) 0.9f + 0.1f * sin(time * 23f) * sin(time * 7.3f) else 1f
        val alphaAll = (min(1f, open * 1.6f) * flicker).coerceIn(0f, 1f)
        val drawAngle = angle - (1f - e) * 1.4f
        val scale = 0.7f + 0.3f * e

        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(scale, scale)

        // 本体
        fill.shader = bodyShader
        fill.alpha = (255 * alphaAll).toInt()
        canvas.drawCircle(0f, 0f, radius, fill)
        fill.shader = null
        glowCircle(canvas, 0f, 0f, radius, color, alphaAll, 1.6f * density)
        // 色ずれ（ホログラムっぽさ）
        stroke.color = withAlpha(0xFFFF5FD2.toInt(), (70 * alphaAll).toInt())
        stroke.strokeWidth = 1.2f * density
        canvas.drawCircle(-1.6f * density, 0f, radius, stroke)
        stroke.color = withAlpha(color, (90 * alphaAll).toInt())
        canvas.drawCircle(0f, 0f, radius * 0.86f, stroke)

        // 縁の溝（フルート）：穴と穴の間
        for (i in 0 until n) {
            val a = drawAngle + PI.toFloat() + (i + 0.5f) * step
            val x = cos(a) * radius * 0.99f
            val y = sin(a) * radius * 0.99f
            if (x > radius * 0.25f) continue
            fill.color = Color.argb((110 * alphaAll).toInt(), 0, 0, 0)
            canvas.drawCircle(x, y, radius * 0.075f, fill)
            stroke.color = withAlpha(color, (150 * alphaAll).toInt())
            stroke.strokeWidth = 1.2f * density
            canvas.drawCircle(x, y, radius * 0.075f, stroke)
        }

        // 穴（チャンバー）
        text.textSize = chamberR * 0.5f
        for (i in 0 until n) {
            val a = drawAngle + PI.toFloat() + i * step
            val x = cos(a) * chamberOrbit
            val y = sin(a) * chamberOrbit
            if (x > chamberR) continue // 右半分（画面の外）は描かない
            val sel = i == selected && !closing
            val r = if (sel) chamberR * 1.08f else chamberR
            fill.color = Color.argb((120 * alphaAll).toInt(), 0, 0, 0)
            canvas.drawCircle(x, y, r, fill)
            val entry = entries.getOrNull(i)
            val icon = entry?.icon
            if (icon != null) {
                val s = r * 0.78f
                iconSrc.set(0, 0, icon.width, icon.height)
                iconDst.set(x - s, y - s, x + s, y + s)
                iconPaint.alpha = (235 * alphaAll).toInt()
                canvas.drawBitmap(icon, iconSrc, iconDst, iconPaint)
            } else if (entry != null) {
                text.color = withAlpha(color, (240 * alphaAll).toInt())
                text.textSize = r * (if (entry.short.length > 3) 0.38f else 0.5f)
                canvas.drawText(entry.short, x, y + text.textSize * 0.35f, text)
            }
            if (sel) glowCircle(canvas, x, y, r, Color.WHITE, alphaAll, 2.2f * density)
            else {
                stroke.color = withAlpha(color, (210 * alphaAll).toInt())
                stroke.strokeWidth = 1.6f * density
                canvas.drawCircle(x, y, r, stroke)
            }
        }

        // 真ん中のエジェクター（星形）と軸
        val starR = radius * 0.2f
        star.reset()
        val points = n
        for (k in 0 until points * 2) {
            val rr = if (k % 2 == 0) starR else starR * 0.55f
            val a = drawAngle + PI.toFloat() + k * PI.toFloat() / points
            val x = cos(a) * rr
            val y = sin(a) * rr
            if (k == 0) star.moveTo(x, y) else star.lineTo(x, y)
        }
        star.close()
        fill.color = withAlpha(color, (40 * alphaAll).toInt())
        canvas.drawPath(star, fill)
        stroke.color = withAlpha(color, (200 * alphaAll).toInt())
        stroke.strokeWidth = 1.4f * density
        canvas.drawPath(star, stroke)
        fill.color = withAlpha(color, (150 * alphaAll).toInt())
        canvas.drawCircle(0f, 0f, radius * 0.06f, fill)

        // 走査線（円の範囲だけ）
        val gap = 4f * density
        val off = if (cfg.hologramMotion) (time * 18f * density) % gap else 0f
        var k = 0
        var y = -radius + off
        while (y < radius && k + 4 <= scan.size) {
            val half = kotlin.math.sqrt(max(0f, radius * radius - y * y))
            scan[k] = -half; scan[k + 1] = y; scan[k + 2] = 0f; scan[k + 3] = y
            k += 4
            y += gap
        }
        stroke.color = withAlpha(color, (26 * alphaAll).toInt())
        stroke.strokeWidth = 1f * density
        canvas.drawLines(scan, 0, k, stroke)
        canvas.restore()

        // 選んでいるもの名前（シリンダーの左）
        val entry = entries.getOrNull(selected)
        if (cfg.showLabel && entry != null && !closing && open > 0.6f) {
            val la = ((open - 0.6f) / 0.4f).coerceIn(0f, 1f) * flicker
            text.textAlign = Paint.Align.RIGHT
            text.textSize = 15f * density
            text.color = withAlpha(color, (255 * la).toInt())
            text.setShadowLayer(6f * density, 0f, 0f, withAlpha(color, (200 * la).toInt()))
            val tx = cx - radius * scale - 14f * density
            val label = entry.label
            canvas.drawText(label, tx, cy + text.textSize * 0.35f, text)
            text.clearShadowLayer()
            val w = text.measureText(label)
            labelRect.set(tx - w - 8f * density, cy - 20f * density, tx + 8f * density, cy + 20f * density)
            stroke.color = withAlpha(color, (160 * la).toInt())
            stroke.strokeWidth = 1f * density
            canvas.drawLine(tx + 4f * density, cy, cx - (chamberOrbit + chamberR * 1.08f) * scale, cy, stroke)
            text.textAlign = Paint.Align.CENTER
        } else labelRect.setEmpty()
    }

    /** 光って見える円（太く薄い線を重ねる） */
    private fun glowCircle(canvas: Canvas, x: Float, y: Float, r: Float, c: Int, a: Float, w: Float) {
        stroke.color = withAlpha(c, (30 * a).toInt()); stroke.strokeWidth = w * 5f
        canvas.drawCircle(x, y, r, stroke)
        stroke.color = withAlpha(c, (70 * a).toInt()); stroke.strokeWidth = w * 2.5f
        canvas.drawCircle(x, y, r, stroke)
        stroke.color = withAlpha(c, (240 * a).toInt()); stroke.strokeWidth = w
        canvas.drawCircle(x, y, r, stroke)
    }

    // ─────────────────────────────────────────────
    // タッチ
    // ─────────────────────────────────────────────

    /** 何かされるたびに呼ばれる（自動で引っ込むまでの時間を延ばす） */
    var onInteract: () -> Unit = {}

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (closing) return true
        onInteract()
        val cx = width.toFloat()
        val cy = centerY
        when (ev.actionMasked) {
            MotionEvent.ACTION_OUTSIDE -> {
                if (cfg.closeOnOutside) close()
                return true
            }
            MotionEvent.ACTION_DOWN -> {
                tracker?.recycle()
                tracker = VelocityTracker.obtain().also { it.addMovement(ev) }
                downX = ev.x; downY = ev.y
                dragging = false
                angVel = 0f
                prevTouchAngle = atan2(ev.y - cy, ev.x - cx)
                prevTouchTime = SystemClock.uptimeMillis()
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(ev)
                if (!dragging && hypot(ev.x - downX, ev.y - downY) > slop) {
                    dragging = true
                    prevTouchAngle = atan2(ev.y - cy, ev.x - cx)
                }
                if (dragging) {
                    val a = atan2(ev.y - cy, ev.x - cx)
                    var d = a - prevTouchAngle
                    if (d > PI) d -= (2 * PI).toFloat()
                    if (d < -PI) d += (2 * PI).toFloat()
                    val now = SystemClock.uptimeMillis()
                    val dt = max(1L, now - prevTouchTime) / 1000f
                    angle += d
                    angVel = angVel * 0.6f + (d / dt) * 0.4f
                    prevTouchAngle = a
                    prevTouchTime = now
                    updateSelected()
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                tracker?.addMovement(ev)
                tracker?.computeCurrentVelocity(1000)
                val vx = tracker?.xVelocity ?: 0f
                val vy = tracker?.yVelocity ?: 0f
                tracker?.recycle(); tracker = null
                if (!dragging) {
                    tap(ev.x, ev.y, cx, cy)
                } else {
                    dragging = false
                    // 下から上へ素早くはじいたら引っ込む
                    if (vy < -1400f * density && abs(vy) > abs(vx) * 1.2f && downY - ev.y > 36f * density) {
                        close()
                        return true
                    }
                    // 指を離して少し経っていたら勢いは無し
                    if (SystemClock.uptimeMillis() - prevTouchTime > 80) angVel = 0f
                    angVel = angVel.coerceIn(-18f, 18f)
                    startFrames()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                tracker?.recycle(); tracker = null
                dragging = false
                startFrames()
            }
        }
        return true
    }

    private fun tap(x: Float, y: Float, cx: Float, cy: Float) {
        if (!labelRect.isEmpty && labelRect.contains(x, y)) {
            entries.getOrNull(selected)?.let(onPick)
            return
        }
        val px = x - cx
        val py = y - cy
        for (i in 0 until n) {
            val a = angle + PI.toFloat() + i * step
            val hx = cos(a) * chamberOrbit
            val hy = sin(a) * chamberOrbit
            if (hypot(px - hx, py - hy) < chamberR * 1.15f) {
                val entry = entries.getOrNull(i) ?: return
                if (cfg.haptics) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onPick(entry)
                return
            }
        }
        if (hypot(px, py) > radius * 1.02f && cfg.closeOnOutside) close()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 画面に出たら、出てくるアニメーションから始める
        // （以前はコンストラクタの途中で呼んでいて、フレーム処理がまだ作られておらず落ちていた）
        startFrames()
    }

    override fun onDetachedFromWindow() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frame)
        tracker?.recycle(); tracker = null
        super.onDetachedFromWindow()
    }

    companion object {
        private fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

        private fun easeOutBack(t: Float): Float {
            val c1 = 1.9f
            val c3 = c1 + 1f
            val u = t - 1f
            return 1f + c3 * u * u * u + c1 * u * u
        }
    }
}
