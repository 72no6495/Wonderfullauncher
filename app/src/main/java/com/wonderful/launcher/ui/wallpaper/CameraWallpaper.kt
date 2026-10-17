package com.wonderful.launcher.ui.wallpaper

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wonderful.launcher.data.CameraEffect
import com.wonderful.launcher.data.CameraLens
import com.wonderful.launcher.data.LauncherSettings
import com.wonderful.launcher.ui.components.RetroLabel
import com.wonderful.launcher.ui.theme.RetroPalette

fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/**
 * カメラで見ている景色をそのまま壁紙にする。
 * 映像は画面に映すだけで、保存も送信もしない。止まっている間（パネル表示中・アイドル中）はカメラを切る
 */
@Composable
fun CameraScene(s: LauncherSettings, p: RetroPalette, paused: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val c = s.camera
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    // 設定画面で許可した後に戻ってきた時に気付けるよう、復帰のたびに確かめ直す
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) granted = hasCameraPermission(context) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (!granted) {
            RetroLabel(
                "カメラの使用が許可されていません\n設定 → 壁紙 で「カメラを許可」を押してください",
                Modifier.align(Alignment.Center).padding(24.dp), size = 13.sp, color = p.text.copy(alpha = 0.7f), maxLines = 3,
            )
            return@Box
        }
        val view = remember {
            PreviewView(context).apply {
                // TextureView で描くと、色の効果（カラーフィルター）をかけられる
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        }
        AndroidView({ view }, Modifier.fillMaxSize(), update = { applyCameraEffect(it, c.effect) })
        DisposableEffect(c.lens, paused, owner) {
            var provider: ProcessCameraProvider? = null
            var disposed = false
            if (!paused) {
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    if (disposed) return@addListener
                    runCatching {
                        val pr = future.get()
                        provider = pr
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                        val selector = if (c.lens == CameraLens.FRONT) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                        pr.unbindAll()
                        pr.bindToLifecycle(owner, selector, preview)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
            onDispose {
                disposed = true
                runCatching { provider?.unbindAll() }
            }
        }
        if (c.scanlines) {
            Box(
                Modifier.fillMaxSize().graphicsLayer().drawWithCache {
                    val step = 4.dp.toPx()
                    onDrawBehind {
                        var y = 0f
                        val col = Color.Black.copy(alpha = 0.35f)
                        while (y < size.height) {
                            drawLine(col, Offset(0f, y), Offset(size.width, y), step * 0.4f)
                            y += step
                        }
                    }
                },
            )
        }
    }
}

/** 映像に色の効果をかける（ハードウェアレイヤーのカラーフィルターなので軽い） */
private fun applyCameraEffect(view: PreviewView, effect: CameraEffect) {
    val m = cameraMatrix(effect)
    if (m == null) {
        view.setLayerType(View.LAYER_TYPE_NONE, null)
    } else {
        view.setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply { colorFilter = ColorMatrixColorFilter(m) })
    }
}

private fun cameraMatrix(effect: CameraEffect): ColorMatrix? = when (effect) {
    CameraEffect.NORMAL -> null
    CameraEffect.MONO -> ColorMatrix().apply { setSaturation(0f) }
    CameraEffect.SEPIA -> ColorMatrix().apply {
        setSaturation(0f)
        postConcat(ColorMatrix().apply { setScale(1.07f, 0.88f, 0.66f, 1f) })
    }
    CameraEffect.VAPOR -> ColorMatrix(
        floatArrayOf(
            0.9f, 0.1f, 0.3f, 0f, 20f,
            0.1f, 0.5f, 0.3f, 0f, 0f,
            0.4f, 0.2f, 0.9f, 0f, 40f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    CameraEffect.NIGHT -> ColorMatrix().apply {
        setSaturation(0f)
        postConcat(
            ColorMatrix(
                floatArrayOf(
                    0.25f, 0f, 0f, 0f, 0f,
                    0f, 1.5f, 0f, 0f, 20f,
                    0f, 0f, 0.3f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }
    CameraEffect.INVERT -> ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
}
