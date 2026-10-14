package com.wonderful.launcher.ui.panels

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.BatteryManager
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.wonderful.launcher.data.LauncherData
import com.wonderful.launcher.data.WIDGET_COLUMNS
import com.wonderful.launcher.data.WidgetItem
import com.wonderful.launcher.data.WidgetKind
import com.wonderful.launcher.ui.LauncherViewModel
import com.wonderful.launcher.ui.components.EmptyMessage
import com.wonderful.launcher.ui.components.Glyph
import com.wonderful.launcher.ui.components.NeonText
import com.wonderful.launcher.ui.components.RetroButton
import com.wonderful.launcher.ui.components.RetroGlyph
import com.wonderful.launcher.ui.components.RetroLabel
import com.wonderful.launcher.ui.components.RetroTextField
import com.wonderful.launcher.ui.components.RetroWindow
import com.wonderful.launcher.ui.theme.LocalPalette
import com.wonderful.launcher.ui.theme.LocalRetroFont
import com.wonderful.launcher.ui.theme.LocalSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Android ウィジェットを追加する（バインド許可・設定画面は Activity 側で処理） */
typealias AddSystemWidget = (info: AppWidgetProviderInfo, w: Int, h: Int, replaceItemId: String?) -> Unit

/**
 * ウィジェット画面。グリッドに沿って自由に配置でき、縦にスクロールできる。
 * 「編集」中はドラッグで移動、右下の ◢ でサイズ変更、✕ で削除。
 */
@Composable
fun WidgetPanel(
    data: LauncherData,
    vm: LauncherViewModel,
    onAddSystem: AddSystemWidget,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    var editing by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    RetroWindow(
        title = if (picking) "ADD_WIDGET.EXE" else if (editing) "WIDGETS.EXE [EDIT]" else "WIDGETS.EXE",
        onClose = { if (picking) picking = false else onClose() },
        modifier = modifier,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val cellPx = constraints.maxWidth / WIDGET_COLUMNS.toFloat()
            AnimatedContent(
                targetState = picking,
                transitionSpec = {
                    (slideInVertically(tween(320)) { it / 3 } + fadeIn(tween(220))) togetherWith
                        (slideOutVertically(tween(260)) { -it / 4 } + fadeOut(tween(180)))
                },
                label = "widgets",
            ) { isPicking ->
                if (isPicking) {
                    WidgetPicker(
                        vm = vm,
                        cellPx = cellPx,
                        onPickBuiltin = { kind, w, h ->
                            vm.addWidget(kind, w, h)
                            picking = false
                        },
                        onPickSystem = { info, w, h ->
                            onAddSystem(info, w, h, null)
                            picking = false
                        },
                    )
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RetroButton("+ 追加", { picking = true }, color = p.mint)
                            RetroButton(if (editing) "完了" else "配置を編集", { editing = !editing }, color = p.yellow, filled = editing)
                            Spacer(Modifier.weight(1f))
                            RetroLabel("${data.widgets.size} 個", size = 11.sp, color = p.cyan)
                        }
                        WidgetBoard(data, vm, cellPx, editing, onAddSystem)
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetBoard(
    data: LauncherData,
    vm: LauncherViewModel,
    cellPx: Float,
    editing: Boolean,
    onAddSystem: AddSystemWidget,
) {
    val p = LocalPalette.current
    val s = LocalSettings.current
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    if (data.widgets.isEmpty()) {
        EmptyMessage("ウィジェットがありません\n「+ 追加」から置いてみよう")
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val visibleRows = (constraints.maxHeight / cellPx).toInt() + 1
        val usedRows = data.widgets.maxOfOrNull { it.y + it.h } ?: 0
        // 編集中は下に余白を作って、下へも置けるようにする
        val rows = maxOf(visibleRows, usedRows + if (editing) 6 else 1)
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { (rows * cellPx).toDp() })
                    .drawBehind {
                        if (editing && s.widgetGrid) {
                            // グリッドの点
                            for (gx in 0..WIDGET_COLUMNS) for (gy in 0..rows) {
                                drawCircle(p.cyan.copy(alpha = 0.35f), 1.6f, Offset(gx * cellPx, gy * cellPx))
                            }
                        }
                    },
            ) {
                data.widgets.forEach { item ->
                    key(item.id) {
                        WidgetCell(item, cellPx, editing, vm, onAddSystem)
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetCell(
    item: WidgetItem,
    cellPx: Float,
    editing: Boolean,
    vm: LauncherViewModel,
    onAddSystem: AddSystemWidget,
) {
    val p = LocalPalette.current
    val s = LocalSettings.current
    val density = LocalDensity.current
    val current by rememberUpdatedState(item)
    var drag by remember { mutableStateOf(Offset.Zero) }
    var resize by remember { mutableStateOf(Offset.Zero) }
    var active by remember { mutableStateOf(false) }
    val lift by animateFloatAsState(if (active) 1.04f else 1f, spring(dampingRatio = 0.5f), label = "lift")

    val wPx = (item.w * cellPx + resize.x).coerceAtLeast(cellPx)
    val hPx = (item.h * cellPx + resize.y).coerceAtLeast(cellPx)
    val shape = RoundedCornerShape(14.dp)

    Box(
        Modifier
            .offset { IntOffset((item.x * cellPx + drag.x).roundToInt(), (item.y * cellPx + drag.y).roundToInt()) }
            .size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })
            .zIndex(if (active) 1f else 0f)
            .graphicsLayer { scaleX = lift; scaleY = lift }
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .let { if (s.widgetFrame) it.border(1.5.dp, Brush.linearGradient(listOf(p.pink, p.cyan)), shape) else it },
        ) {
            val wDp = with(density) { (item.w * cellPx).toDp().value.toInt() }
            val hDp = with(density) { (item.h * cellPx).toDp().value.toInt() }
            when (item.kind) {
                WidgetKind.SYSTEM -> SystemWidget(item, vm, wDp, hDp, onAddSystem)
                WidgetKind.CLOCK -> ClockWidget(item)
                WidgetKind.BATTERY -> BatteryWidget()
                WidgetKind.NOTE -> NoteWidget(item, enabled = !editing) { text -> vm.updateWidget(item.id) { it.copy(text = text) } }
                WidgetKind.MEMORY -> MemoryWidget(item)
                WidgetKind.CPU -> CpuWidget(item)
                WidgetKind.TEMPERATURE -> TemperatureWidget(item)
                WidgetKind.STORAGE -> StorageWidget()
                WidgetKind.NETWORK -> NetworkWidget(item)
                WidgetKind.BOOST -> BoostWidget(vm)
                WidgetKind.SYSMON -> SysMonWidget(item)
            }
        }

        if (editing) {
            // 移動用のつまみ（全面）
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(p.pink.copy(alpha = if (active) 0.25f else 0.1f))
                    .pointerInput(item.id, cellPx) {
                        detectDragGestures(
                            onDragStart = { active = true },
                            onDragEnd = {
                                val cur = current
                                val nx = ((cur.x * cellPx + drag.x) / cellPx).roundToInt().coerceIn(0, WIDGET_COLUMNS - cur.w)
                                val ny = ((cur.y * cellPx + drag.y) / cellPx).roundToInt().coerceAtLeast(0)
                                vm.updateWidget(cur.id) { w -> w.copy(x = nx, y = ny) }
                                drag = Offset.Zero; active = false
                            },
                            onDragCancel = { drag = Offset.Zero; active = false },
                        ) { change, amount ->
                            change.consume()
                            drag += amount
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                RetroLabel("${item.w}×${item.h}", color = Color.White, size = 12.sp)
            }
            // 削除
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(p.pink)
                    .clickable { vm.removeWidget(item.id) },
                contentAlignment = Alignment.Center,
            ) { RetroLabel("✕", color = Color.White, size = 13.sp) }
            // サイズ変更
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(30.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp))
                    .background(p.yellow)
                    .pointerInput(item.id, cellPx) {
                        detectDragGestures(
                            onDragStart = { active = true },
                            onDragEnd = {
                                val cur = current
                                val nw = ((cur.w * cellPx + resize.x) / cellPx).roundToInt().coerceIn(1, WIDGET_COLUMNS - cur.x)
                                val nh = ((cur.h * cellPx + resize.y) / cellPx).roundToInt().coerceIn(1, 24)
                                vm.updateWidget(cur.id) { w -> w.copy(w = nw, h = nh) }
                                resize = Offset.Zero; active = false
                            },
                            onDragCancel = { resize = Offset.Zero; active = false },
                        ) { change, amount ->
                            change.consume()
                            resize += amount
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { RetroLabel("◢", color = Color.Black, size = 14.sp) }
        }
    }
}

// ─────────────────────────────────────────────
// Android ウィジェット
// ─────────────────────────────────────────────

@Composable
private fun SystemWidget(item: WidgetItem, vm: LauncherViewModel, wDp: Int, hDp: Int, onAddSystem: AddSystemWidget) {
    val p = LocalPalette.current
    val info = remember(item.appWidgetId) { vm.widgets.info(item.appWidgetId) }
    if (info == null) {
        // バックアップから復元した・アプリが消えた等で表示できない
        val provider = remember(item.provider) { vm.widgets.findProvider(item.provider) }
        Column(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RetroLabel("NO SIGNAL", color = p.pink, size = 13.sp)
            if (provider != null) {
                Spacer(Modifier.height(6.dp))
                RetroButton("再設定", { onAddSystem(provider, item.w, item.h, item.id) }, color = p.yellow)
            } else {
                RetroLabel("アプリがありません", size = 10.sp, color = p.text.copy(alpha = 0.6f))
            }
        }
        return
    }
    key(item.appWidgetId) {
        AndroidView(
            factory = { ctx ->
                vm.widgets.createView(ctx, item.appWidgetId, info).apply { setPadding(0, 0, 0, 0) }
            },
            update = { v ->
                @Suppress("DEPRECATION")
                v.updateAppWidgetSize(null, wDp, hDp, wDp, hDp)
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// ─────────────────────────────────────────────
// 内蔵ウィジェット
// ─────────────────────────────────────────────

@Composable
private fun RetroCard(content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(p.purple.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.65f))))
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun ClockWidget(item: WidgetItem) {
    val p = LocalPalette.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000 - now % 1000)
        }
    }
    RetroCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            NeonText(
                SimpleDateFormat(if (item.w >= 6) "HH:mm:ss" else "HH:mm", Locale.getDefault()).format(Date(now)),
                size = (14 + item.h * 9).coerceAtMost(64).sp, color = Color.White, glow = p.pink,
            )
            if (item.h >= 2) {
                RetroLabel(
                    SimpleDateFormat("yyyy.MM.dd (E)", Locale.JAPAN).format(Date(now)),
                    color = p.cyan, size = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun BatteryWidget() {
    val p = LocalPalette.current
    val context = LocalContext.current
    var level by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (l >= 0 && sc > 0) level = l * 100 / sc
                val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    val color = when {
        charging -> p.mint
        level in 0..20 -> p.pink
        else -> p.cyan
    }
    RetroCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            NeonText(
                if (level < 0) "--%" else "$level%" + if (charging) " ⚡" else "",
                size = 22.sp, color = Color.White, glow = color,
            )
            Spacer(Modifier.height(6.dp))
            // 10 区切りのレトロなメーター
            Row(Modifier.fillMaxWidth().height(14.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (i in 0 until 10) {
                    val on = level >= 0 && level > i * 10
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(2.dp))
                            .background(if (on) color else color.copy(alpha = 0.15f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteWidget(item: WidgetItem, enabled: Boolean, onChange: (String) -> Unit) {
    val p = LocalPalette.current
    var text by remember(item.id) { mutableStateOf(item.text) }
    // 打つたびに保存（ディスクへの書き込みはまとめて行われる）
    LaunchedEffect(text) {
        if (text != item.text) {
            delay(300)
            onChange(text)
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF7AE).copy(alpha = 0.92f))
            .drawBehind {
                // 罫線
                var y = 26.dp.toPx()
                while (y < size.height) {
                    drawLine(p.cyan.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), 1.5f)
                    y += 22.dp.toPx()
                }
            }
            .padding(10.dp),
    ) {
        if (text.isEmpty()) RetroLabel("メモを入力…", color = Color.Black.copy(alpha = 0.35f), size = 14.sp)
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            enabled = enabled,
            textStyle = TextStyle(color = Color(0xFF2A0B4A), fontSize = 14.sp, fontFamily = LocalRetroFont.current, lineHeight = 22.sp),
            cursorBrush = SolidColor(p.pink),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// ─────────────────────────────────────────────
// 追加画面
// ─────────────────────────────────────────────

private data class BuiltinSpec(val label: String, val glyph: Glyph, val kind: WidgetKind, val w: Int, val h: Int)

private data class ProviderEntry(val info: AppWidgetProviderInfo, val label: String, val appLabel: String)

@Composable
private fun WidgetPicker(
    vm: LauncherViewModel,
    cellPx: Float,
    onPickBuiltin: (WidgetKind, Int, Int) -> Unit,
    onPickSystem: (AppWidgetProviderInfo, Int, Int) -> Unit,
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val providers by produceState<List<ProviderEntry>?>(null) {
        value = withContext(Dispatchers.Default) {
            val pm = context.packageManager
            vm.widgets.providers().map { info ->
                val appLabel = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
                }.getOrDefault(info.provider.packageName)
                ProviderEntry(info, runCatching { info.loadLabel(pm) }.getOrNull() ?: appLabel, appLabel)
            }.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
        }
    }
    val q = normalizeForSearch(query.trim())
    val filtered = providers?.filter {
        q.isEmpty() || normalizeForSearch(it.label).contains(q) || normalizeForSearch(it.appLabel).contains(q)
    }

    Column(Modifier.fillMaxSize()) {
        RetroTextField(query, { query = it }, "ウィジェットを検索…", Modifier.fillMaxWidth().padding(10.dp))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (q.isEmpty()) {
                item { RetroLabel("// 内蔵ウィジェット", color = p.cyan, size = 12.sp) }
                // (表示名, アイコン, 種類, 幅, 高さ)
                val builtins = listOf(
                    BuiltinSpec("レトロ時計", Glyph.RECENT, WidgetKind.CLOCK, 8, 3),
                    BuiltinSpec("バッテリー", Glyph.STAR, WidgetKind.BATTERY, 4, 2),
                    BuiltinSpec("メモ", Glyph.EDIT, WidgetKind.NOTE, 4, 4),
                    BuiltinSpec("システム\nモニター", Glyph.WIDGETS, WidgetKind.SYSMON, 8, 4),
                    BuiltinSpec("メモリ", Glyph.WIDGETS, WidgetKind.MEMORY, 4, 3),
                    BuiltinSpec("CPU", Glyph.SETTINGS, WidgetKind.CPU, 4, 3),
                    BuiltinSpec("温度", Glyph.STAR, WidgetKind.TEMPERATURE, 4, 3),
                    BuiltinSpec("ストレージ", Glyph.FOLDER, WidgetKind.STORAGE, 4, 2),
                    BuiltinSpec("通信速度", Glyph.SEARCH, WidgetKind.NETWORK, 4, 3),
                    BuiltinSpec("メモリ解放", Glyph.PLUS, WidgetKind.BOOST, 4, 3),
                )
                builtins.chunked(3).forEach { row ->
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { b ->
                                BuiltinTile(b.label, b.glyph, Modifier.weight(1f)) { onPickBuiltin(b.kind, b.w, b.h) }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                item { RetroLabel("// アプリのウィジェット", color = p.cyan, size = 12.sp) }
            }
            when {
                filtered == null -> item { EmptyMessage("LOADING…") }
                filtered.isEmpty() -> item { EmptyMessage("見つかりません") }
                else -> items(filtered, key = { it.info.provider.flattenToString() + it.info.profile.hashCode() }) { e ->
                    val (w, h) = cellsFor(e.info, cellPx, context)
                    ProviderRow(e, w, h) { onPickSystem(e.info, w, h) }
                }
            }
        }
    }
}

/** ウィジェットの推奨サイズを、このランチャーのグリッドのマス数に変換 */
private fun cellsFor(info: AppWidgetProviderInfo, cellPx: Float, context: Context): Pair<Int, Int> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
        // 一般的なランチャーの 1 マス ≒ このグリッドの 2 マス
        return (info.targetCellWidth * 2).coerceIn(1, WIDGET_COLUMNS) to (info.targetCellHeight * 2).coerceIn(1, 24)
    }
    val minPx = context.resources.displayMetrics.density * 40
    val w = ceil(maxOf(info.minWidth.toFloat(), minPx) / cellPx).toInt().coerceIn(2, WIDGET_COLUMNS)
    val h = ceil(maxOf(info.minHeight.toFloat(), minPx) / cellPx).toInt().coerceIn(1, 24)
    return w to h
}

@Composable
private fun BuiltinTile(label: String, glyph: Glyph, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .clip(shape)
            .background(p.purple.copy(alpha = 0.25f))
            .border(1.5.dp, p.pink, shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RetroGlyph(glyph, p.yellow, Modifier.size(30.dp))
        Spacer(Modifier.height(6.dp))
        RetroLabel(label, size = 12.sp, maxLines = 2, align = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun ProviderRow(e: ProviderEntry, w: Int, h: Int, onClick: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val preview by produceState<ImageBitmap?>(null, e.info) {
        value = withContext(Dispatchers.IO) {
            val dpi = context.resources.displayMetrics.densityDpi
            val d = runCatching { e.info.loadPreviewImage(context, dpi) }.getOrNull()
                ?: runCatching { e.info.loadIcon(context, dpi) }.getOrNull()
            d?.let {
                val iw = it.intrinsicWidth.takeIf { v -> v > 0 } ?: 128
                val ih = it.intrinsicHeight.takeIf { v -> v > 0 } ?: 128
                val scale = minOf(1f, 360f / maxOf(iw, ih))
                val bmp = Bitmap.createBitmap((iw * scale).toInt().coerceAtLeast(1), (ih * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                it.setBounds(0, 0, bmp.width, bmp.height)
                it.draw(Canvas(bmp))
                bmp.asImageBitmap()
            }
        }
    }
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.35f))
            .border(1.dp, p.cyan.copy(alpha = 0.5f), shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            preview?.let { Image(it, null, Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            RetroLabel(e.label, size = 14.sp, maxLines = 2)
            RetroLabel(e.appLabel, size = 11.sp, color = p.cyan)
        }
        RetroLabel("${w}×${h}", size = 12.sp, color = p.yellow)
    }
}
