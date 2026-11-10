package com.wonderful.launcher.ui.components

import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.wonderful.launcher.data.AppInfo
import com.wonderful.launcher.data.AppOrder
import com.wonderful.launcher.ui.theme.LocalPalette
import kotlin.math.abs

/**
 * アプリを並べたグリッド。タップで起動、長押しして指を離すとメニュー、
 * 長押ししたまま動かすとドラッグで並べ替え（離した所で [onReorder] に新しい並びを渡す）。
 */
@Composable
fun ReorderableAppGrid(
    apps: List<AppInfo>,
    icons: Map<String, ImageBitmap>,
    columns: Int,
    showLabels: Boolean,
    onTap: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onReorder: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    badge: (AppInfo) -> String? = { null },
    dimmed: (AppInfo) -> Boolean = { false },
    state: LazyGridState = rememberLazyGridState(),
) {
    val haptics = LocalHapticFeedback.current
    // ドラッグ中だけ画面の中で並びを入れ替える。離したら保存
    var local by remember { mutableStateOf(apps) }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var pressedKey by remember { mutableStateOf<String?>(null) }
    // ドラッグ中の指の位置（グリッドの中の座標）
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(apps) { if (dragKey == null) local = apps }
    val current by rememberUpdatedState(local)
    val latestApps by rememberUpdatedState(apps)
    val tap by rememberUpdatedState(onTap)
    val long by rememberUpdatedState(onLongPress)
    val reorder by rememberUpdatedState(onReorder)

    fun keyAt(pos: Offset): String? = state.layoutInfo.visibleItemsInfo.firstOrNull {
        pos.x >= it.offset.x && pos.x < it.offset.x + it.size.width &&
            pos.y >= it.offset.y && pos.y < it.offset.y + it.size.height
    }?.key as? String

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val key = keyAt(down.position) ?: return@awaitEachGesture
                pressedKey = key
                // 長押しになるまでに離したらタップ、スクロールされたら何もしない
                var tapped = false
                val longPressed = try {
                    withTimeout(viewConfiguration.longPressTimeoutMillis) {
                        tapped = waitForUpOrCancellation() != null
                    }
                    false
                } catch (_: PointerEventTimeoutCancellationException) {
                    true
                }
                if (!longPressed) {
                    pressedKey = null
                    if (tapped) current.firstOrNull { it.key == key }?.let(tap)
                    return@awaitEachGesture
                }
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                dragKey = key
                dragPos = down.position
                var moved = 0f
                // ドラッグ中は先に受け取って消費し、グリッドがスクロールしないようにする
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) { ch.consume(); break }
                    val d = ch.position - ch.previousPosition
                    moved += abs(d.x) + abs(d.y)
                    dragPos = ch.position
                    ch.consume()
                    // 上下の端に近づいたら自動でスクロール
                    val edge = 48.dp.toPx()
                    val h = size.height
                    if (ch.position.y < edge) state.dispatchRawDelta(-(edge - ch.position.y) * 0.25f)
                    else if (ch.position.y > h - edge) state.dispatchRawDelta((ch.position.y - (h - edge)) * 0.25f)
                    // 指の下のアプリと入れ替える
                    val over = keyAt(ch.position)
                    if (over != null && over != key) {
                        val from = current.indexOfFirst { it.key == key }
                        val to = current.indexOfFirst { it.key == over }
                        if (from >= 0 && to >= 0) local = AppOrder.move(current, from, to)
                    }
                }
                val app = current.firstOrNull { it.key == key }
                val changed = current.map { it.key } != latestApps.map { it.key }
                dragKey = null
                pressedKey = null
                if (moved < viewConfiguration.touchSlop && !changed) {
                    app?.let(long)
                } else if (changed) {
                    reorder(current.map { it.key })
                }
            }
        },
    ) {
        items(local, key = { it.key }) { app ->
            val dragging = app.key == dragKey
            AppTile(
                app, icons[app.key],
                onClick = {},
                clickable = false,
                showLabel = showLabels,
                badge = badge(app),
                badgeColor = LocalPalette.current.pink,
                dimmed = dimmed(app),
                modifier = Modifier
                    .animateItem(placementSpec = if (dragging) null else spring(stiffness = 500f, visibilityThreshold = IntOffset.VisibilityThreshold))
                    .zIndex(if (dragging) 1f else 0f)
                    .scale(if (pressedKey == app.key && !dragging) 0.9f else 1f)
                    .graphicsLayer {
                        if (dragging) {
                            // 指に付いてくる（今いるマスの中心からのずれ）
                            val info = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == app.key }
                            if (info != null) {
                                translationX = dragPos.x - (info.offset.x + info.size.width / 2f)
                                translationY = dragPos.y - (info.offset.y + info.size.height / 2f)
                            }
                            scaleX = 1.15f; scaleY = 1.15f
                            alpha = 0.92f
                        }
                    },
            )
        }
    }
}
