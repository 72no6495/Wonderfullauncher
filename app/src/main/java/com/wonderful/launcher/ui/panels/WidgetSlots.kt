// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (c) 2026 72no6495

package com.wonderful.launcher.ui.panels

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wonderful.launcher.data.WIDGET_COLUMNS
import com.wonderful.launcher.data.WidgetItem
import com.wonderful.launcher.data.WidgetKind
import com.wonderful.launcher.data.WidgetTarget
import com.wonderful.launcher.ui.LauncherViewModel
import com.wonderful.launcher.ui.components.RetroLabel
import com.wonderful.launcher.ui.components.RetroWindow
import com.wonderful.launcher.ui.theme.LocalPalette
import com.wonderful.launcher.ui.theme.LocalSettings

/** ウィジェットの表示名 */
@Composable
fun rememberWidgetLabel(item: WidgetItem?, vm: LauncherViewModel): String {
    val context = LocalContext.current
    return remember(item?.id, item?.provider) {
        when {
            item == null -> "未選択"
            item.kind != WidgetKind.SYSTEM -> item.kind.label
            else -> vm.widgets.findProvider(item.provider)?.let { info ->
                runCatching { info.loadLabel(context.packageManager) }.getOrNull()
            } ?: "Androidウィジェット"
        }
    }
}

/** 置き場所を指定してウィジェットを選ぶ画面 */
@Composable
fun WidgetChooser(
    title: String,
    target: WidgetTarget,
    vm: LauncherViewModel,
    onAddSystem: AddSystemWidget,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RetroWindow("SELECT_WIDGET.EXE", onClose, modifier) {
        RetroLabel(title, size = 12.sp, color = LocalPalette.current.cyan, modifier = Modifier.padding(start = 12.dp, top = 10.dp))
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val cellPx = constraints.maxWidth / WIDGET_COLUMNS.toFloat()
            WidgetPicker(
                vm = vm,
                cellPx = cellPx,
                onPickBuiltin = { kind, w, h -> vm.placeWidget(target, kind, w, h); onClose() },
                onPickSystem = { info, w, h -> onAddSystem(info, w, h, target); onClose() },
            )
        }
    }
}

/**
 * 決まった大きさの枠にウィジェットを 1 つ表示する（時計の位置・ジェスチャー表示）。
 */
@Composable
fun SlotWidget(
    item: WidgetItem,
    vm: LauncherViewModel,
    height: Dp,
    onAddSystem: AddSystemWidget,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val s = LocalSettings.current
    val density = LocalDensity.current
    val shape = RoundedCornerShape(14.dp)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .let { if (s.widgetFrame) it.border(1.5.dp, Brush.linearGradient(listOf(p.pink, p.cyan)), shape) else it },
    ) {
        val wDp = with(density) { constraints.maxWidth.toDp().value.toInt() }
        WidgetContent(item, vm, wDp, height.value.toInt(), editing = false, onAddSystem = onAddSystem)
    }
}

/** 時計の位置で、まだウィジェットを選んでいない時の枠 */
@Composable
fun EmptySlot(height: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .border(1.5.dp, p.cyan.copy(alpha = 0.6f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        RetroLabel("＋ ここに表示するウィジェットを選ぶ", color = p.cyan, size = 13.sp)
    }
}

/** ジェスチャーで呼び出したウィジェット */
@Composable
fun WidgetPopup(
    item: WidgetItem,
    title: String,
    rows: Int,
    vm: LauncherViewModel,
    onAddSystem: AddSystemWidget,
    onChange: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    RetroWindow(
        title = title,
        onClose = onClose,
        modifier = modifier,
        actions = {
            RetroLabel("変更", size = 12.sp, color = LocalPalette.current.yellow, modifier = Modifier.clickable(onClick = onChange).padding(horizontal = 8.dp))
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(10.dp)) {
            val cell = constraints.maxWidth / WIDGET_COLUMNS.toFloat()
            val h = with(density) { (cell * rows).toDp() }
            SlotWidget(item, vm, h, onAddSystem)
        }
    }
}
