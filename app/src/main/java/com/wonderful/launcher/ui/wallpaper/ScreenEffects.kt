package com.wonderful.launcher.ui.wallpaper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.wonderful.launcher.data.LauncherSettings
import com.wonderful.launcher.ui.theme.RetroPalette
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * 画面全体にかけるエフェクト（フィルムグレイン・VHS のトラッキングノイズ・ランダムグリッチ）。
 * 描画するだけでタッチは下に素通りする。
 */
@Composable
fun ScreenEffects(s: LauncherSettings, p: RetroPalette, paused: Boolean) {
    val anyOn = s.filmGrain > 0f || s.vhsTracking || s.glitchOverlay
    if (!anyOn) return
    val t by rememberSceneTime(s.animSpeed, paused)
    val grain = remember { List(3) { makeNoise(it) } }
    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        val time = t
        val w = size.width
        val h = size.height

        if (s.filmGrain > 0f) {
            // 3 枚のノイズ画像を毎フレームずらして敷き詰める
            val img = grain[(time * 24).toInt() % grain.size]
            val r = Random((time * 24).toInt())
            val ox = -r.nextInt(img.width)
            val oy = -r.nextInt(img.height)
            var y = oy
            while (y < h) {
                var x = ox
                while (x < w) {
                    drawImage(
                        img, IntOffset.Zero, IntSize(img.width, img.height),
                        IntOffset(x, y), IntSize(img.width, img.height),
                        alpha = s.filmGrain * 0.35f, blendMode = BlendMode.Overlay,
                    )
                    x += img.width
                }
                y += img.height
            }
        }

        if (s.vhsTracking) {
            // ゆっくり下へ流れるトラッキングの帯
            val bandY = ((time * 0.12f) % 1.3f - 0.15f) * h
            val bandH = h * 0.05f
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.04f), Color.Transparent),
                    bandY, bandY + bandH,
                ),
                Offset(0f, bandY), Size(w, bandH),
            )
            val r = Random((time * 30).toInt())
            repeat(14) {
                val ly = bandY + r.nextFloat() * bandH
                val lx = r.nextFloat() * w
                drawLine(Color.White.copy(alpha = 0.25f), Offset(lx, ly), Offset(lx + r.nextFloat() * w * 0.3f, ly), 1.5f)
            }
            // 画面下の VHS ノイズ
            drawRect(Color.White.copy(alpha = 0.03f + 0.02f * sin(time * 40f)), Offset(0f, h - 6f), Size(w, 6f))
        }

        if (s.glitchOverlay) {
            val frame = floor(time * 7f).toInt()
            val r = Random(frame * 31)
            if (r.nextFloat() < 0.22f) {
                repeat(2 + r.nextInt(5)) {
                    val c = if (r.nextBoolean()) p.pink else p.cyan
                    drawRect(
                        c.copy(alpha = 0.18f + r.nextFloat() * 0.25f),
                        Offset(r.nextFloat() * w * 0.6f, r.nextFloat() * h),
                        Size(w * (0.2f + r.nextFloat() * 0.6f), 2f + r.nextFloat() * 14f),
                        blendMode = BlendMode.Screen,
                    )
                }
            }
        }
    }
}

private fun makeNoise(seed: Int): ImageBitmap {
    val size = 192
    val r = Random(seed)
    val px = IntArray(size * size) {
        val v = r.nextInt(256)
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    return android.graphics.Bitmap.createBitmap(px, size, size, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}
