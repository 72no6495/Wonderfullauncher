package com.wonderful.launcher.ui.wallpaper

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.wonderful.launcher.data.LauncherSettings
import com.wonderful.launcher.data.MirrorConfig
import com.wonderful.launcher.ui.theme.RetroPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.sin

/**
 * 合わせ鏡の仕組み（ビデオフィードバック）：
 * ホーム画面（壁紙＋アイコン）を少し遅れて縮小写真に撮り、それを次の壁紙として真ん中に小さく描く。
 * その写真の中にも 1 つ前の写真が写っているので、画面が奥へ奥へと無限に続いて見える。
 * 撮るのは「遅れ」ごとに 1 回・縮小サイズだけなので、毎フレーム何十枚も描くより軽い。
 */
object MirrorFeed {
    /** 最後に撮ったホーム画面 */
    var frame by mutableStateOf<ImageBitmap?>(null)
    /** 撮った回数（揺れや色の変化に使う。時計を回さないで済む） */
    var count = 0
}

/**
 * ホーム画面の後ろ側（壁紙＋ホーム）を [MirrorFeed] に撮り続ける Modifier。
 * 合わせ鏡でない時は何もしない（撮った画像も捨てる）
 */
@Composable
fun rememberMirrorCapture(enabled: Boolean, paused: Boolean, cfg: MirrorConfig): Modifier {
    val layer = rememberGraphicsLayer()
    // 描画に「撮って」と頼むための合図
    val request = remember { mutableIntStateOf(0) }
    val pending = remember { booleanArrayOf(false) }
    val config by rememberUpdatedState(cfg)
    LaunchedEffect(enabled, paused) {
        if (!enabled) {
            MirrorFeed.frame = null
            return@LaunchedEffect
        }
        if (paused) return@LaunchedEffect
        while (isActive) {
            delay((config.delaySec.coerceIn(0.04f, 2f) * 1000).toLong())
            pending[0] = true
            request.intValue++
            // 次の描画で記録されるのを待ってから画像にする
            withFrameNanos { }
            withFrameNanos { }
            if (!pending[0]) {
                runCatching { layer.toImageBitmap() }.getOrNull()?.let {
                    MirrorFeed.count++
                    MirrorFeed.frame = it
                }
            }
        }
    }
    if (!enabled) return Modifier
    return Modifier.drawWithContent {
        request.intValue
        if (pending[0] && size.width >= 1f && size.height >= 1f) {
            pending[0] = false
            val q = config.quality.scale
            val w = (size.width * q).toInt().coerceAtLeast(1)
            val h = (size.height * q).toInt().coerceAtLeast(1)
            val sx = w / size.width
            val sy = h / size.height
            // 縮小して記録（画素数が減るので撮るのも描くのも軽い）
            layer.record(IntSize(w, h)) {
                scale(sx, sy, Offset.Zero) {
                    this@drawWithContent.drawContent()
                }
            }
        }
        drawContent()
    }
}

/** 合わせ鏡の壁紙：1 つ前に撮ったホーム画面を、縮めて・回して・暗くして真ん中に描く */
@Composable
fun MirrorScene(s: LauncherSettings, p: RetroPalette, modifier: Modifier = Modifier) {
    val c = s.mirror
    Canvas(modifier) {
        val img = MirrorFeed.frame
        val n = MirrorFeed.count
        // 一番奥（まだ何も写っていない所）
        drawRect(Brush.radialGradient(listOf(p.skyMid.copy(alpha = 0.6f), Color(0xFF05000C)), center, size.maxDimension * 0.7f))
        if (img == null) return@Canvas
        val k = c.shrink.coerceIn(0.5f, 0.97f)
        val phase = n * 0.25f * s.gridSpeed
        val wob = c.wobble.coerceIn(0f, 1f)
        val dx = if (wob > 0f) sin(phase) * size.width * 0.04f * wob else 0f
        val dy = if (wob > 0f) sin(phase * 0.7f + 1f) * size.height * 0.03f * wob else 0f
        val rot = c.rotation + if (wob > 0f) sin(phase * 0.5f) * 4f * wob else 0f
        val w = size.width * k
        val h = size.height * k
        val topLeft = Offset((size.width - w) / 2 + dx, (size.height - h) / 2 + dy)
        withTransform({
            rotate(rot, Offset(center.x + dx, center.y + dy))
            if (c.flip) scale(-1f, 1f, Offset(center.x + dx, center.y + dy))
        }) {
            drawImage(
                img,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(img.width, img.height),
                dstOffset = IntOffset(topLeft.x.toInt(), topLeft.y.toInt()),
                dstSize = IntSize(w.toInt(), h.toInt()),
                filterQuality = FilterQuality.Low,
            )
            val sz = Size(w, h)
            // 奥へ行くほど暗く（毎回重なるので、奥ほど何段も暗くなる）
            if (c.darken > 0f) drawRect(Color.Black.copy(alpha = c.darken.coerceIn(0f, 0.6f)), topLeft, sz)
            // 奥へ行くほど色が変わる（撮るたびに色相をずらして重ねる）
            if (c.rainbow) drawRect(Color.hsv((n * 23f) % 360f, 0.9f, 1f).copy(alpha = 0.16f), topLeft, sz)
            if (c.frame) {
                val sw = (size.minDimension * 0.008f).coerceAtLeast(2f)
                drawRect(p.cyan.copy(alpha = 0.85f), topLeft, sz, style = Stroke(sw))
                drawRect(p.pink.copy(alpha = 0.5f), Offset(topLeft.x - sw * 1.5f, topLeft.y - sw * 1.5f), Size(w + sw * 3, h + sw * 3), style = Stroke(sw * 0.6f))
            }
        }
    }
}
