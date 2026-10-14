package com.wonderful.launcher.ui.wallpaper

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wonderful.launcher.data.LauncherSettings
import com.wonderful.launcher.data.SystemActions
import com.wonderful.launcher.data.WallpaperType
import com.wonderful.launcher.ui.theme.RetroPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** 設定に応じた壁紙を描く */
@Composable
fun WallpaperLayer(settings: LauncherSettings, palette: RetroPalette, paused: Boolean) {
    Crossfade(targetState = settings.wallpaperType, animationSpec = tween(600), label = "wp") { type ->
        // 専用のレイヤー（動く壁紙を描き直しても他を巻き込まない）
        Box(Modifier.fillMaxSize().graphicsLayer()) {
            val blurMod = if (settings.wallpaperBlur > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Modifier.blur((settings.wallpaperBlur * 24f).dp)
            } else Modifier
            when (type) {
                WallpaperType.VAPOR_GRID -> VaporScene(settings, palette, paused, Modifier.fillMaxSize().then(blurMod))
                WallpaperType.SYSTEM -> Unit // ウィンドウ背景に OS の壁紙が透けて見える
                WallpaperType.IMAGE -> ImageWallpaper(settings.wallpaperImageVersion, Modifier.fillMaxSize().then(blurMod))
                WallpaperType.GRADIENT -> GradientWallpaper(settings, palette, paused, Modifier.fillMaxSize())
                WallpaperType.SOLID -> Box(Modifier.fillMaxSize().background(Color(settings.solidColor)))
                WallpaperType.UNDERWATER -> UnderwaterScene(settings, palette, paused, blurMod)
                WallpaperType.MAZE -> MazeScene(settings, palette, paused, blurMod)
                WallpaperType.GLITCH -> GlitchScene(settings, palette, paused, blurMod)
                WallpaperType.MATRIX -> MatrixScene(settings, palette, paused, blurMod)
                WallpaperType.STARFIELD -> StarfieldScene(settings, palette, paused, blurMod)
                WallpaperType.LAVA -> LavaScene(settings, palette, paused, blurMod)
                WallpaperType.AURORA -> AuroraScene(settings, palette, paused, blurMod)
                WallpaperType.LIFE -> LifeScene(settings, palette, paused, blurMod)
            }
            if (settings.wallpaperDim > 0f) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = settings.wallpaperDim)))
            }
        }
    }
}

@Composable
private fun ImageWallpaper(version: Long, modifier: Modifier) {
    val context = LocalContext.current
    val bmp by produceState<Bitmap?>(null, version) {
        value = withContext(Dispatchers.IO) { SystemActions.loadWallpaper(context) }
    }
    val image = remember(bmp) { bmp?.asImageBitmap() }
    if (image != null) {
        Image(image, null, modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(Color.Black))
    }
}

@Composable
private fun GradientWallpaper(s: LauncherSettings, p: RetroPalette, paused: Boolean, modifier: Modifier) {
    // 回さない設定なら時計そのものを止める（1 枚描いたら描き直さない）
    val time = rememberSceneTime(s.animSpeed, paused || !s.gradientAnimate)
    Canvas(modifier) {
        val t = if (s.gradientAnimate) 0.5f + 0.5f * sin(time.value * 2f * PI.toFloat() / 24f) else 0f
        val a = Math.toRadians((s.gradientAngle + t * 40f).toDouble())
        val r = size.maxDimension
        val c = Offset(size.width / 2, size.height / 2)
        val d = Offset((cos(a) * r).toFloat(), (sin(a) * r).toFloat())
        drawRect(Brush.linearGradient(listOf(p.skyTop, p.skyMid, p.skyBottom, p.cyan.copy(alpha = 0.6f)), c - d, c + d))
    }
}

/**
 * 王道ヴェイパーウェイヴ風景：夕焼け空・縞模様の太陽・山・流れるパースグリッド・瞬く星
 */
@Composable
fun VaporScene(s: LauncherSettings, p: RetroPalette, paused: Boolean, modifier: Modifier = Modifier) {
    // 時間は描画の時にだけ読む（以前は毎フレーム画面全体を作り直していた）
    val time = rememberSceneTime(s.animSpeed, paused)
    val v = s.vapor
    val stars = remember(v.starCount) {
        val r = Random(42)
        List(v.starCount) { Triple(r.nextFloat(), r.nextFloat() * 0.95f, r.nextFloat()) }
    }

    Box(
        modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val horizon = h * v.horizon
            val sky = Brush.verticalGradient(listOf(p.skyTop, p.skyMid, p.skyBottom), 0f, horizon)
            val ground = Brush.verticalGradient(listOf(p.ground, Color.Black), horizon, h)
            val sunR = w * 0.3f * v.sunSize
            val sunC = Offset(w / 2f, horizon - sunR * 0.35f)
            val sunBrush = Brush.verticalGradient(listOf(p.sunTop, p.sunBottom), sunC.y - sunR, sunC.y + sunR)
            val mountains = Path().apply {
                moveTo(0f, horizon)
                val r = Random(7)
                var x = 0f
                lineTo(0f, horizon - h * 0.05f)
                while (x < w) {
                    x += w * (0.06f + r.nextFloat() * 0.08f)
                    lineTo(x, horizon - h * (0.02f + r.nextFloat() * 0.1f))
                }
                lineTo(w, horizon)
                close()
            }
            onDrawBehind {
                // グリッドが流れる位置（2.2 秒で 1 マス）
                val phase = (time.value * s.gridSpeed / 2.2f) % 1f
                drawRect(sky, size = Size(w, horizon))
                if (s.showStars) {
                    stars.forEachIndexed { i, (sx, sy, tw) ->
                        val a = 0.35f + 0.65f * ((sin(time.value * 2f * PI.toFloat() / 6f + tw * 12f) + 1f) / 2f)
                        drawCircle(if (i % 3 == 0) p.cyan else Color.White, 1.2f + tw * 1.6f, Offset(sx * w, sy * horizon * 0.95f), alpha = a * 0.9f)
                    }
                }
                if (s.showSun) {
                    // 太陽のグロー
                    drawCircle(
                        Brush.radialGradient(listOf(p.sunBottom.copy(alpha = 0.45f), Color.Transparent), sunC, sunR * 1.8f),
                        sunR * 1.8f, sunC,
                    )
                    clipRect(0f, 0f, w, horizon) {
                        if (s.sunStripes) {
                            // 下に行くほど太くなる横縞で太陽を切り抜く
                            val top = sunC.y - sunR
                            val bottom = sunC.y + sunR
                            var y = top
                            while (y < bottom) {
                                val t = (y - top) / (bottom - top)
                                val gap = if (t < 0.45f) 0f else 2f + (t - 0.45f) * sunR * 0.12f
                                val solid = sunR * 0.11f
                                clipRect(0f, y, w, y + solid) {
                                    drawCircle(sunBrush, sunR, sunC)
                                }
                                y += solid + gap
                            }
                        } else {
                            drawCircle(sunBrush, sunR, sunC)
                        }
                    }
                }
                if (s.showMountains) {
                    drawPath(mountains, p.mountain)
                    drawPath(mountains, p.cyan.copy(alpha = 0.5f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                }
                // 地面とパースグリッド
                drawRect(ground, Offset(0f, horizon), Size(w, h - horizon))
                val gridColor = p.grid
                val vanish = Offset(w / 2f, horizon)
                val spacing = w * 0.16f / v.gridDensity.coerceAtLeast(0.3f)
                val lines = (w * 3.5f / spacing).toInt()
                for (i in -lines..lines) {
                    val bx = w / 2f + i * spacing
                    if (v.gridGlow) drawLine(gridColor.copy(alpha = 0.18f), vanish, Offset(bx, h), 7f)
                    drawLine(gridColor.copy(alpha = 0.75f), vanish, Offset(bx, h), 2f)
                }
                // 奥から手前へ流れる横線（遠くほど詰まる）
                val rows = (14 * v.gridDensity).toInt().coerceIn(4, 60)
                for (k in 0 until rows) {
                    val z = (k + phase) / rows
                    val y = horizon + (h - horizon) * z * z
                    val a = (0.15f + z * 0.85f).coerceIn(0f, 1f)
                    if (v.gridGlow) drawLine(gridColor.copy(alpha = a * 0.2f), Offset(0f, y), Offset(w, y), 6f + z * 6f)
                    drawLine(gridColor.copy(alpha = a), Offset(0f, y), Offset(w, y), 1.5f + z * 2f)
                }
                // 地平線の発光
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, p.pink.copy(alpha = 0.55f), Color.Transparent), horizon - 18f, horizon + 18f),
                    Offset(0f, horizon - 18f), Size(w, 36f),
                )
            }
        },
    )
}

/** CRT 風の走査線とビネット */
@Composable
fun CrtOverlay(settings: LauncherSettings, modifier: Modifier = Modifier) {
    if (!settings.scanlines && !settings.vignette) return
    Box(
        // 専用のレイヤーで 1 回だけ描く（走査線 800 本を毎フレーム描き直さない）
        modifier.fillMaxSize().graphicsLayer().drawWithCache {
            val step = 3.dp.toPx()
            val vignette = Brush.radialGradient(
                listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                center = Offset(size.width / 2, size.height / 2),
                radius = size.maxDimension * 0.75f,
            )
            onDrawWithContent {
                if (settings.scanlines) {
                    var y = 0f
                    val c = Color.Black.copy(alpha = settings.scanlineIntensity)
                    while (y < size.height) {
                        drawLine(c, Offset(0f, y), Offset(size.width, y), step * 0.45f)
                        y += step
                    }
                }
                if (settings.vignette) drawRect(vignette)
            }
        },
    )
}
