// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (c) 2026 72no6495

package com.wonderful.launcher.ui.panels

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wonderful.launcher.data.SysStats
import com.wonderful.launcher.data.SystemMonitor
import com.wonderful.launcher.data.WidgetItem
import com.wonderful.launcher.ui.LauncherViewModel
import com.wonderful.launcher.ui.components.NeonText
import com.wonderful.launcher.ui.components.RetroLabel
import com.wonderful.launcher.ui.theme.LocalPalette
import kotlinx.coroutines.launch

@Composable
private fun rememberStats(): SysStats {
    val context = LocalContext.current
    val flow = remember { SystemMonitor.stats(context) }
    val s by flow.collectAsStateWithLifecycle()
    return s
}

@Composable
private fun MonitorCard(title: String, accent: Color, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.7f))))
            .padding(10.dp),
    ) {
        RetroLabel(title, size = 10.sp, color = accent, modifier = Modifier.align(Alignment.TopStart))
        Box(Modifier.fillMaxSize().padding(top = 14.dp), content = content)
    }
}

/** 折れ線グラフ（0〜1 に正規化した値） */
@Composable
private fun Sparkline(values: List<Float>, color: Color, modifier: Modifier, maxValue: Float? = null) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val mx = (maxValue ?: values.maxOrNull() ?: 1f).coerceAtLeast(0.0001f)
        val path = Path()
        val fill = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1).coerceAtLeast(1)
            val y = size.height - size.height * (v / mx).coerceIn(0f, 1f)
            if (i == 0) { path.moveTo(x, y); fill.moveTo(x, size.height); fill.lineTo(x, y) } else { path.lineTo(x, y); fill.lineTo(x, y) }
        }
        fill.lineTo(size.width, size.height); fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** 10 区切りのレトロなメーター */
@Composable
private fun SegmentBar(ratio: Float, color: Color, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(12.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 0 until 10) {
            val on = ratio > i / 10f
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(2.dp))
                    .background(if (on) color else color.copy(alpha = 0.15f)),
            )
        }
    }
}

private fun loadColor(r: Float, cool: Color, warm: Color, hot: Color) = when {
    r < 0.6f -> cool
    r < 0.85f -> warm
    else -> hot
}

@Composable
fun MemoryWidget(item: WidgetItem) {
    val p = LocalPalette.current
    val st = rememberStats()
    val color = loadColor(st.memUsedRatio, p.cyan, p.yellow, p.pink)
    MonitorCard("// MEMORY", color) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.Bottom) {
                NeonText("${(st.memUsedRatio * 100).toInt()}%", size = 22.sp, glow = color)
                Spacer(Modifier.width(6.dp))
                RetroLabel(
                    "${SystemMonitor.formatBytes(st.memTotal - st.memAvail)} / ${SystemMonitor.formatBytes(st.memTotal)}",
                    size = 10.sp, color = p.text.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.height(4.dp))
            SegmentBar(st.memUsedRatio, color)
            if (item.h >= 3) {
                Spacer(Modifier.height(6.dp))
                Sparkline(st.memHistory, color, Modifier.fillMaxWidth().weight(1f), maxValue = 1f)
            }
            if (st.lowMemory) RetroLabel("⚠ メモリ不足", size = 10.sp, color = p.pink)
        }
    }
}

@Composable
fun CpuWidget(item: WidgetItem) {
    val p = LocalPalette.current
    val st = rememberStats()
    val load = st.cpuLoad
    val color = loadColor(load ?: 0f, p.mint, p.yellow, p.pink)
    MonitorCard("// CPU", color) {
        Column(Modifier.fillMaxSize()) {
            if (load == null) {
                RetroLabel("この端末では CPU の情報を読めません", size = 11.sp, maxLines = 3)
                return@Column
            }
            Row(verticalAlignment = Alignment.Bottom) {
                NeonText("${(load * 100).toInt()}%", size = 22.sp, glow = color)
                Spacer(Modifier.width(6.dp))
                RetroLabel("${st.cpuFreqs.size}コア · 周波数", size = 10.sp, color = p.text.copy(alpha = 0.7f))
            }
            Spacer(Modifier.height(4.dp))
            // コアごとの周波数バー
            Row(Modifier.fillMaxWidth().height(if (item.h >= 3) 34.dp else 18.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                st.cpuFreqs.forEach { (cur, max) ->
                    val r = if (max > 0) (cur.toFloat() / max).coerceIn(0f, 1f) else 0f
                    Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(color.copy(alpha = 0.15f))) {
                        Box(
                            Modifier.fillMaxWidth().fillMaxHeight(r.coerceAtLeast(0.02f)).align(Alignment.BottomCenter)
                                .background(loadColor(r, p.mint, p.yellow, p.pink)),
                        )
                    }
                }
            }
            if (item.h >= 3) {
                Spacer(Modifier.height(6.dp))
                Sparkline(st.cpuHistory, color, Modifier.fillMaxWidth().weight(1f), maxValue = 1f)
            }
        }
    }
}

@Composable
fun TemperatureWidget(item: WidgetItem) {
    val p = LocalPalette.current
    val st = rememberStats()
    val main = st.cpuTemp ?: st.batteryTemp
    val color = when {
        main == null -> p.cyan
        main < 38f -> p.cyan
        main < 45f -> p.yellow
        else -> p.pink
    }
    MonitorCard("// TEMP", color) {
        Column(Modifier.fillMaxSize()) {
            NeonText(main?.let { "%.1f℃".format(it) } ?: "--℃", size = 22.sp, glow = color)
            RetroLabel(
                buildString {
                    st.batteryTemp?.let { append("電池 %.1f℃".format(it)) }
                    st.cpuTemp?.let { append("  CPU %.1f℃".format(it)) }
                    if (st.thermalStatus != null) append("  状態: ${SystemMonitor.thermalLabel(st.thermalStatus)}")
                },
                size = 10.sp, color = p.text.copy(alpha = 0.75f), maxLines = 2,
            )
            if (item.h >= 3) {
                Spacer(Modifier.height(6.dp))
                Sparkline(st.tempHistory.filter { it > 0f }, color, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
fun StorageWidget() {
    val p = LocalPalette.current
    val st = rememberStats()
    val color = loadColor(st.storageUsedRatio, p.purple, p.yellow, p.pink)
    MonitorCard("// STORAGE", color) {
        Column(Modifier.fillMaxSize()) {
            NeonText("${(st.storageUsedRatio * 100).toInt()}%", size = 22.sp, glow = color)
            RetroLabel(
                "空き ${SystemMonitor.formatBytes(st.storageFree)} / ${SystemMonitor.formatBytes(st.storageTotal)}",
                size = 10.sp, color = p.text.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(4.dp))
            SegmentBar(st.storageUsedRatio, color)
        }
    }
}

@Composable
fun NetworkWidget(item: WidgetItem) {
    val p = LocalPalette.current
    val st = rememberStats()
    MonitorCard("// NETWORK", p.cyan) {
        Column(Modifier.fillMaxSize()) {
            Row {
                NeonText("↓ ${SystemMonitor.formatBytes(st.rxPerSec)}/s", size = 15.sp, glow = p.cyan)
            }
            Row {
                NeonText("↑ ${SystemMonitor.formatBytes(st.txPerSec)}/s", size = 15.sp, glow = p.pink, color = p.pink)
            }
            if (item.h >= 3) {
                Spacer(Modifier.height(6.dp))
                Sparkline(st.netHistory, p.cyan, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

/** 4 項目をまとめて表示 */
@Composable
fun SysMonWidget(item: WidgetItem) {
    val p = LocalPalette.current
    val st = rememberStats()
    MonitorCard("// SYSTEM MONITOR", p.mint) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MiniRow("RAM", st.memUsedRatio, "${(st.memUsedRatio * 100).toInt()}%", p.cyan)
            st.cpuLoad?.let { MiniRow("CPU", it, "${(it * 100).toInt()}%", p.mint) }
            val temp = st.cpuTemp ?: st.batteryTemp
            if (temp != null) MiniRow("TMP", ((temp - 20f) / 30f).coerceIn(0f, 1f), "%.0f℃".format(temp), p.yellow)
            MiniRow("ROM", st.storageUsedRatio, "${(st.storageUsedRatio * 100).toInt()}%", p.purple)
            if (item.h >= 4) MiniRow("NET", 0f, "↓${SystemMonitor.formatBytes(st.rxPerSec)}/s ↑${SystemMonitor.formatBytes(st.txPerSec)}/s", p.pink)
        }
    }
}

@Composable
private fun MiniRow(label: String, ratio: Float, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RetroLabel(label, size = 10.sp, color = color, modifier = Modifier.width(30.dp))
        if (label != "NET") {
            Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.15f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(ratio.coerceIn(0.02f, 1f)).background(color))
            }
            Spacer(Modifier.width(6.dp))
            RetroLabel(value, size = 10.sp, modifier = Modifier.width(44.dp))
        } else {
            RetroLabel(value, size = 10.sp, modifier = Modifier.weight(1f))
        }
    }
}

/** メモリ解放ボタン */
@Composable
fun BoostWidget(vm: LauncherViewModel) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val st = rememberStats()
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val sweep = remember { Animatable(0f) }
    // 解放中だけ回す（待機中はフレームを要求しない）
    val spin = remember { Animatable(0f) }
    LaunchedEffect(running) {
        while (running) {
            spin.snapTo(0f)
            spin.animateTo(360f, tween(900, easing = LinearEasing))
        }
    }
    MonitorCard("// BOOST", p.pink) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .border(2.dp, Brush.sweepGradient(listOf(p.pink, p.cyan, p.pink)), CircleShape)
                    .clickable(enabled = !running) {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        running = true
                        result = null
                        scope.launch {
                            sweep.snapTo(0f)
                            launch { sweep.animateTo(1f, tween(1400)) }
                            val freed = SystemMonitor.boost(context, vm.apps.value.map { it.packageName })
                            result = if (freed > 1L shl 20) "${SystemMonitor.formatBytes(freed)} 解放しました"
                            else "これ以上は解放できません"
                            running = false
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = if (running) spin.value else 0f }) {
                    drawArc(
                        p.cyan, -90f, 360f * (if (running) 0.3f else 1f - st.memUsedRatio), false,
                        Offset(6.dp.toPx(), 6.dp.toPx()), Size(size.width - 12.dp.toPx(), size.height - 12.dp.toPx()),
                        style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                NeonText(if (running) "..." else "GO", size = 16.sp, glow = p.pink)
            }
            Spacer(Modifier.height(6.dp))
            RetroLabel(
                result ?: "空き ${SystemMonitor.formatBytes(st.memAvail)}",
                size = 11.sp, color = if (result != null) p.mint else p.text.copy(alpha = 0.7f), maxLines = 2,
            )
        }
    }
}
