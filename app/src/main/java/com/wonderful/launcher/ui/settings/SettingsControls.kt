// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (c) 2026 72no6495

package com.wonderful.launcher.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wonderful.launcher.ui.components.RetroButton
import com.wonderful.launcher.ui.components.RetroChip
import com.wonderful.launcher.ui.components.RetroLabel
import com.wonderful.launcher.ui.theme.LocalPalette

@Composable
private fun SettingRow(
    title: String,
    description: String?,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    val p = LocalPalette.current
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            RetroLabel(title, size = 14.sp, color = p.text, maxLines = 2)
            if (description != null) {
                RetroLabel(description, size = 11.sp, color = p.text.copy(alpha = 0.55f), maxLines = 3)
            }
        }
        Spacer(Modifier.width(10.dp))
        trailing()
    }
}

/** ネオンのトグルスイッチ */
@Composable
fun RetroSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    val x by animateDpAsState(if (checked) 22.dp else 0.dp, spring(dampingRatio = 0.5f, stiffness = 700f), label = "sw")
    val track by animateColorAsState(if (checked) p.pink.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.5f), label = "track")
    val knob by animateColorAsState(if (checked) p.yellow else p.text.copy(alpha = 0.6f), label = "knob")
    Box(
        Modifier
            .width(50.dp).height(28.dp)
            .clip(RoundedCornerShape(50))
            .background(track)
            .border(1.5.dp, if (checked) p.pink else p.cyan.copy(alpha = 0.5f), RoundedCornerShape(50))
            .clickable { onChange(!checked) }
            .padding(3.dp),
    ) {
        Box(Modifier.offset(x = x).size(22.dp).clip(CircleShape).background(knob))
    }
}

@Composable
fun SwitchSetting(title: String, value: Boolean, description: String? = null, onChange: (Boolean) -> Unit) {
    SettingRow(title, description, Modifier.clickable { onChange(!value) }) { RetroSwitch(value, onChange) }
}

@Composable
fun SliderSetting(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    description: String? = null,
    steps: Int = 0,
    format: (Float) -> String = { "%.2f".format(it) },
    onChange: (Float) -> Unit,
) {
    val p = LocalPalette.current
    // ドラッグ中はローカル値で動かし、離した時にも確定
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                RetroLabel(title, size = 14.sp)
                if (description != null) RetroLabel(description, size = 11.sp, color = p.text.copy(alpha = 0.55f), maxLines = 3)
            }
            RetroLabel(format(local), size = 13.sp, color = p.yellow)
        }
        Slider(
            value = local,
            onValueChange = { local = it; onChange(it) },
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = p.pink,
                activeTrackColor = p.cyan,
                inactiveTrackColor = p.purple.copy(alpha = 0.35f),
                activeTickColor = p.yellow,
                inactiveTickColor = p.purple,
            ),
        )
    }
}

@Composable
fun <T> ChoiceSetting(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    description: String? = null,
    onSelect: (T) -> Unit,
) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        RetroLabel(title, size = 14.sp)
        if (description != null) RetroLabel(description, size = 11.sp, color = p.text.copy(alpha = 0.55f), maxLines = 3)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { o -> RetroChip(label(o), o == selected, { onSelect(o) }) }
        }
    }
}

@Composable
fun ActionSetting(
    title: String,
    button: String,
    description: String? = null,
    color: Color = LocalPalette.current.cyan,
    onClick: () -> Unit,
) {
    SettingRow(title, description) { RetroButton(button, onClick, color = color) }
}

@Composable
fun InfoSetting(title: String, value: String) {
    val p = LocalPalette.current
    SettingRow(title, null) { RetroLabel(value, color = p.yellow, size = 13.sp) }
}

/** プリセット色＋色相/明るさスライダーで色を選ぶ */
@Composable
fun ColorSetting(title: String, value: Long, onChange: (Long) -> Unit) {
    val p = LocalPalette.current
    val presets = listOf(
        0xFF1A0033, 0xFF000000, 0xFF0D0221, 0xFF2B1055, 0xFF001F3F, 0xFF3D0C3C, 0xFF1B1B2F, 0xFF4B0A6B,
    )
    val hsv = remember(value) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(value.toInt(), it) }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RetroLabel(title, size = 14.sp, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(Color(value))
                    .border(2.dp, p.cyan, RoundedCornerShape(6.dp)),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { c ->
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(Color(c))
                        .border(if (c == value) 3.dp else 1.dp, if (c == value) p.yellow else p.cyan.copy(alpha = 0.5f), CircleShape)
                        .clickable { onChange(c) },
                )
            }
        }
        // 色相バー
        Box(
            Modifier.fillMaxWidth().padding(top = 8.dp).height(8.dp).clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) })),
        )
        SliderSetting("色相", hsv[0], 0f..359f, format = { "${it.toInt()}°" }) {
            onChange(hsvToLong(it, hsv[1], hsv[2]))
        }
        SliderSetting("彩度", hsv[1], 0f..1f, format = { "${(it * 100).toInt()}%" }) {
            onChange(hsvToLong(hsv[0], it, hsv[2]))
        }
        SliderSetting("明るさ", hsv[2], 0f..1f, format = { "${(it * 100).toInt()}%" }) {
            onChange(hsvToLong(hsv[0], hsv[1], it))
        }
    }
}

private fun hsvToLong(h: Float, s: Float, v: Float): Long =
    Color.hsv(h.coerceIn(0f, 359.9f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)).toArgb().toLong() and 0xFFFFFFFFL
