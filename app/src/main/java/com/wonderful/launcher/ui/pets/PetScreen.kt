package com.wonderful.launcher.ui.pets

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wonderful.launcher.data.AppInfo
import com.wonderful.launcher.data.FurnitureKind
import com.wonderful.launcher.data.LauncherData
import com.wonderful.launcher.data.LauncherSettings
import com.wonderful.launcher.data.MAX_FURNITURE
import com.wonderful.launcher.data.MAX_PETS
import com.wonderful.launcher.data.PetConfig
import com.wonderful.launcher.data.PetTheme
import com.wonderful.launcher.ui.LauncherViewModel
import com.wonderful.launcher.ui.settings.ActionSetting
import com.wonderful.launcher.ui.settings.ChoiceSetting
import com.wonderful.launcher.ui.settings.SliderSetting
import com.wonderful.launcher.ui.settings.SwitchSetting
import com.wonderful.launcher.ui.components.RetroButton
import com.wonderful.launcher.ui.components.RetroLabel
import com.wonderful.launcher.ui.components.SectionHeader
import com.wonderful.launcher.ui.theme.FrameGate
import com.wonderful.launcher.ui.theme.LocalPalette
import com.wonderful.launcher.ui.theme.LocalRetroFont
import com.wonderful.launcher.ui.theme.RetroPalette
import com.wonderful.launcher.ui.theme.RetroTheme
import kotlin.random.Random

/*
 * 飼育モード（スマホを横に持った時だけ表示）。
 * 縦に戻すとこの画面ごと消えるので、計算も描画も完全に止まる。
 */

private enum class Tool(val emoji: String, val label: String) {
    PAT("👆", "なでる"),
    FOOD("🍖", "エサ"),
    FURNITURE("🪑", "家具"),
}

private enum class Sheet { NONE, ROSTER, SETTINGS }

/** 飼育モードの UI の色（テーマに合わせる） */
fun petUiPalette(theme: PetTheme, base: RetroPalette): RetroPalette = when (theme) {
    PetTheme.VAPOR -> base
    PetTheme.CASUAL -> base.copy(
        pink = Color(0xFFFF8A80), cyan = Color(0xFF26A69A), purple = Color(0xFFB39DDB),
        yellow = Color(0xFFFFB300), mint = Color(0xFF43A047), panel = Color(0xFFFFFBF5), text = Color(0xFF5D4037),
        titleStart = Color(0xFFFF8A80), titleEnd = Color(0xFFFFB74D),
    )
    PetTheme.AQUARIUM -> base.copy(
        pink = Color(0xFFFF8A65), cyan = Color(0xFF4DD0E1), purple = Color(0xFF4FC3F7),
        yellow = Color(0xFFFFF59D), mint = Color(0xFF80CBC4), panel = Color(0xFF06324A), text = Color(0xFFE0F7FA),
        titleStart = Color(0xFF4DD0E1), titleEnd = Color(0xFF80CBC4),
    )
}

/** テーマの家具の部屋（カジュアルとヴェイパーは同じ部屋、アクアリウムは別） */
fun roomOf(theme: PetTheme): PetTheme = if (theme == PetTheme.AQUARIUM) PetTheme.AQUARIUM else PetTheme.CASUAL

@Composable
fun PetScreen(vm: LauncherViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val icons by vm.icons.collectAsStateWithLifecycle()
    val s = data.settings
    RetroTheme(s) {
        val base = LocalPalette.current
        val font = if (s.pet.theme == PetTheme.VAPOR) LocalRetroFont.current else FontFamily.SansSerif
        CompositionLocalProvider(
            LocalPalette provides petUiPalette(s.pet.theme, base),
            LocalRetroFont provides font,
        ) {
            PetRoom(vm, data, apps, icons, base)
        }
    }
}

@Composable
private fun PetRoom(vm: LauncherViewModel, data: LauncherData, apps: List<AppInfo>, icons: Map<String, ImageBitmap>, base: RetroPalette) {
    val cfg = data.settings.pet
    val theme = cfg.theme
    val ui = LocalPalette.current
    val density = LocalDensity.current
    val unit = density.density
    val view = LocalView.current
    val context = LocalContext.current
    val layoutDir = LocalLayoutDirection.current
    val sim = remember { PetSim(Random.nextInt()) }
    val emoji = remember { EmojiCache() }
    val labelPaint = remember(unit) { LabelPaint(unit) }
    val tints = remember { HashMap<String, Color>() }
    var frame by remember { mutableIntStateOf(0) }
    var tool by remember { mutableStateOf(Tool.PAT) }
    var sheet by remember { mutableStateOf(Sheet.NONE) }
    var placeKind by remember { mutableStateOf<FurnitureKind?>(null) }
    var sized by remember { mutableStateOf(false) }
    val room = roomOf(theme)
    val colors = remember(theme, base) { petColors(theme, base) }
    val appMap = remember(apps) { apps.associateBy { it.key } }
    val pets = data.pets.filter { appMap.containsKey(it.key) }
    val roomFurniture = data.furniture.filter { it.room == room }

    fun haptic(kind: Int = HapticFeedbackConstants.CLOCK_TICK) {
        if (cfg.haptic) view.performHapticFeedback(kind)
    }

    // 設定を計算に反映
    val radiusPx = with(density) { 30.dp.toPx() } * cfg.scale
    SideEffect {
        sim.radius = radiusPx
        sim.speedFactor = cfg.speed
        sim.hungerSeconds = cfg.hungerHours * 3600f
        sim.changeLegPairs(cfg.legPairs)
        sim.setAquarium(theme == PetTheme.AQUARIUM)
    }

    // 飼っている子・家具を計算に反映（離れていた間はお腹が減る）
    LaunchedEffect(pets.map { it.key }, sized) {
        if (!sized) return@LaunchedEffect
        val now = System.currentTimeMillis()
        sim.syncPets(pets.map { e ->
            val away = if (e.lastSeen > 0) ((now - e.lastSeen) / 1000f).coerceAtLeast(0f) else 0f
            Triple(e.key, (e.fullness - away / (cfg.hungerHours * 3600f)).coerceIn(0f, 1f), e.affection)
        })
    }
    LaunchedEffect(roomFurniture, sized) {
        if (sized) sim.syncFurniture(roomFurniture.map { Triple(it.id, it.kind, it.x to it.y) })
    }

    // 離れる時（縦持ち・アプリ起動・画面オフ）に保存
    val lifecycleOwner = LocalLifecycleOwner.current
    val save by rememberUpdatedState {
        if (sim.pets.isNotEmpty() || sim.furniture.isNotEmpty()) {
            vm.savePetState(sim.stats(), sim.furniture.filter { it.kind == FurnitureKind.BALL }.associate { it.id to sim.furnitureFraction(it) })
        }
    }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_PAUSE) save() }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            save()
        }
    }

    // フレームのループ（画面が前に出ている間だけ）
    val lifeState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val running = sized && lifeState.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(running, cfg.fps) {
        if (!running) return@LaunchedEffect
        val gate = FrameGate()
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L && gate.add((now - last) / 1e9f, cfg.fps)) {
                    sim.step(gate.consume())
                    frame++
                }
                last = now
            }
        }
    }

    BackHandler(enabled = sheet != Sheet.NONE || tool != Tool.PAT) {
        if (sheet != Sheet.NONE) sheet = Sheet.NONE else { tool = Tool.PAT; placeKind = null }
    }

    val toolNow by rememberUpdatedState(tool)
    val placeNow by rememberUpdatedState(placeKind)
    val appMapNow by rememberUpdatedState(appMap)
    val roomNow by rememberUpdatedState(room)
    val furnitureCount by rememberUpdatedState(roomFurniture.size)
    val hapticNow by rememberUpdatedState(cfg.haptic)

    val insets = WindowInsets.systemBars
    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { sz ->
                    val l = insets.getLeft(density, layoutDir) + 12f * unit
                    val t = insets.getTop(density) + 12f * unit
                    val r = sz.width - insets.getRight(density, layoutDir) - 92f * unit
                    val b = sz.height - insets.getBottom(density) - 8f * unit
                    sim.setBounds(l, t, r, b)
                    sized = true
                }
                .pointerInput(Unit) {
                    fun fraction(x: Float, y: Float) =
                        ((x - sim.left) / (sim.right - sim.left)).coerceIn(0f, 1f) to ((y - sim.top) / (sim.bottom - sim.top)).coerceIn(0f, 1f)

                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val start = down.position
                        val pet = sim.petAt(start.x, start.y)
                        val fur = if (pet == null) sim.furnitureAt(start.x, start.y) else null
                        // 0 = 長押し / 1 = タップ / 2 = ドラッグ
                        val kind = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            var r = 1
                            while (true) {
                                val ev = awaitPointerEvent()
                                val c = ev.changes.firstOrNull { it.id == down.id }
                                if (c == null || !c.pressed) { r = 1; break }
                                if ((c.position - start).getDistance() > viewConfiguration.touchSlop) { r = 2; break }
                            }
                            r
                        } ?: 0
                        when (kind) {
                            0 -> {
                                if (pet != null) {
                                    // 長押しでアプリを起動
                                    if (hapticNow) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    pet.joy = 1f
                                    appMapNow[pet.key]?.let { vm.launch(it) }
                                } else if (fur != null && toolNow == Tool.FURNITURE) {
                                    if (hapticNow) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    vm.removeFurniture(fur.id)
                                }
                                waitForUpOrCancellation()
                            }
                            1 -> when (toolNow) {
                                Tool.PAT -> when {
                                    pet != null -> { sim.pat(pet); if (hapticNow) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                                    fur != null && fur.kind == FurnitureKind.BALL -> {
                                        val dx = fur.x - start.x
                                        val dy = fur.y - start.y
                                        val d = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
                                        fur.vx = dx / d * sim.radius * 8f
                                        fur.vy = dy / d * sim.radius * 8f
                                    }
                                    else -> sim.call(start.x, start.y)
                                }
                                Tool.FOOD -> {
                                    if (pet != null) sim.dropFood(pet.x, pet.y + sim.radius * 0.6f) else sim.dropFood(start.x, start.y)
                                    if (hapticNow) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                                Tool.FURNITURE -> {
                                    val k = placeNow
                                    when {
                                        fur != null -> Toast.makeText(context, "ドラッグで移動・長押しで片付けます", Toast.LENGTH_SHORT).show()
                                        k == null -> Toast.makeText(context, "下から家具を選んでください", Toast.LENGTH_SHORT).show()
                                        furnitureCount >= MAX_FURNITURE -> Toast.makeText(context, "家具は $MAX_FURNITURE 個までです", Toast.LENGTH_SHORT).show()
                                        else -> {
                                            val (fx, fy) = fraction(start.x, start.y)
                                            vm.addFurniture(k, fx, fy, roomNow)
                                            if (hapticNow) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        }
                                    }
                                }
                            }
                            else -> {
                                var lastTime = down.uptimeMillis
                                val movingFur = fur?.takeIf { toolNow == Tool.FURNITURE || it.kind == FurnitureKind.BALL }
                                val grab = movingFur?.let { Offset(it.x - start.x, it.y - start.y) } ?: Offset.Zero
                                if (pet != null) sim.pickUp(pet)
                                val patted = HashMap<String, Long>()
                                var lastFood = start
                                var foodCount = 0
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!c.pressed) break
                                    val dt = ((c.uptimeMillis - lastTime) / 1000f).coerceAtLeast(0.001f)
                                    lastTime = c.uptimeMillis
                                    val pos = c.position
                                    when {
                                        // 持ち上げて運ぶ
                                        pet != null -> sim.carry(pet, pos.x, pos.y, dt)
                                        movingFur != null -> {
                                            val nx = (pos.x + grab.x).coerceIn(sim.left, sim.right)
                                            val ny = (pos.y + grab.y).coerceIn(sim.top, sim.bottom)
                                            if (movingFur.kind == FurnitureKind.BALL && toolNow != Tool.FURNITURE) {
                                                movingFur.vx = (nx - movingFur.x) / dt
                                                movingFur.vy = (ny - movingFur.y) / dt
                                            } else { movingFur.vx = 0f; movingFur.vy = 0f }
                                            movingFur.x = nx
                                            movingFur.y = ny
                                        }
                                        // なでなで（なぞった子をなでる）
                                        toolNow == Tool.PAT -> sim.petAt(pos.x, pos.y)?.let { p ->
                                            val now = c.uptimeMillis
                                            if (now - (patted[p.key] ?: 0L) > 350L) {
                                                patted[p.key] = now
                                                sim.pat(p)
                                                if (hapticNow) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            }
                                        }
                                        // エサをばらまく
                                        toolNow == Tool.FOOD && foodCount < 6 && (pos - lastFood).getDistance() > sim.radius * 1.5f -> {
                                            sim.dropFood(pos.x, pos.y)
                                            lastFood = pos
                                            foodCount++
                                        }
                                    }
                                    c.consume()
                                }
                                if (pet != null) sim.release(pet)
                                if (movingFur != null) {
                                    if (toolNow == Tool.FURNITURE || movingFur.kind != FurnitureKind.BALL) {
                                        val (fx, fy) = fraction(movingFur.x, movingFur.y)
                                        vm.moveFurniture(movingFur.id, fx, fy)
                                    }
                                }
                            }
                        }
                    }
                },
        ) {
            if (frame < 0) return@Canvas // frame を読む＝フレームごとに描き直す
            val t = sim.time
            drawPetBackground(theme, base, sim, t, unit)
            val showFurnitureSel = tool == Tool.FURNITURE
            if (theme == PetTheme.AQUARIUM) {
                sim.furniture.forEach { drawFurniture(it, sim, theme, colors, base, emoji, t, unit, false) }
                sim.food.forEach { drawFood(it, sim, unit) }
                sim.pets.forEach { p ->
                    val icon = icons[p.key]
                    val fin = tints.getOrPut(p.key) { iconTint(icon) }
                    drawFish(p, sim, icon, fin, t, unit)
                }
            } else {
                // ラグは床に敷くので先に。他は奥（上）から順に描く
                sim.furniture.filter { it.kind == FurnitureKind.RUG }.forEach { drawFurniture(it, sim, theme, colors, base, emoji, t, unit, false) }
                val items = ArrayList<Pair<Float, () -> Unit>>()
                sim.furniture.filter { it.kind != FurnitureKind.RUG }.forEach { f -> items += f.y to { drawFurniture(f, sim, theme, colors, base, emoji, t, unit, false) } }
                sim.food.forEach { f -> items += f.y to { drawFood(f, sim, unit) } }
                sim.pets.forEach { p -> items += (p.y + p.lift * sim.radius * 3f) to { drawSpider(p, sim, icons[p.key], colors, theme, t, unit) } }
                items.sortBy { it.first }
                items.forEach { it.second() }
            }
            if (showFurnitureSel) {
                sim.furniture.forEach { f ->
                    drawCircle(colors.accent.copy(alpha = 0.6f), sim.furnitureRadius(f.kind) * 1.25f, Offset(f.x, f.y), style = androidx.compose.ui.graphics.drawscope.Stroke(unit * 1.5f))
                }
            }
            drawParticles(sim, colors, emoji, unit)
            sim.pets.forEach { p ->
                drawPetLabel(p, sim, appMap[p.key]?.label ?: "", cfg.showNames, colors, labelPaint, emoji, unit)
            }
        }

        // ── 右側の道具 ──
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .windowInsetsPadding(insets)
                .padding(end = 10.dp)
                .width(72.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Tool.entries.forEach { t ->
                ToolButton(theme, t.emoji, t.label, tool == t) {
                    tool = t
                    if (t != Tool.FURNITURE) placeKind = null
                    haptic()
                }
            }
            Spacer(Modifier.height(4.dp))
            ToolButton(theme, "🐾", "仲間", sheet == Sheet.ROSTER) { sheet = if (sheet == Sheet.ROSTER) Sheet.NONE else Sheet.ROSTER }
            ToolButton(theme, "⚙", "設定", sheet == Sheet.SETTINGS) { sheet = if (sheet == Sheet.SETTINGS) Sheet.NONE else Sheet.SETTINGS }
        }

        // ── 左上の表示 ──
        Row(
            Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(insets)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Pill(theme) {
                RetroLabel(
                    (if (theme == PetTheme.AQUARIUM) "🐠 アプリ水族館" else "🕷 アプリ飼育") + "  ${pets.size}/$MAX_PETS",
                    size = 13.sp, weight = FontWeight.Bold, color = ui.text,
                )
            }
            val hint = when (tool) {
                Tool.PAT -> "タップ・なぞってなでる / 長押しで起動 / ドラッグで運ぶ"
                Tool.FOOD -> "タップした所にエサ（なぞるとばらまく）"
                Tool.FURNITURE -> "下から選んでタップで置く / ドラッグで移動 / 長押しで片付け"
            }
            Pill(theme, alpha = 0.75f) { RetroLabel(hint, size = 11.sp, color = ui.text) }
        }

        // ── 家具を選ぶ帯 ──
        AnimatedVisibility(
            tool == Tool.FURNITURE,
            Modifier.align(Alignment.BottomStart).windowInsetsPadding(insets).padding(start = 12.dp, end = 96.dp, bottom = 10.dp),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            Card(theme, Modifier.fillMaxWidth()) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RetroLabel("${roomFurniture.size}/$MAX_FURNITURE", size = 12.sp, color = ui.yellow, modifier = Modifier.padding(horizontal = 6.dp))
                    FurnitureKind.entries
                        .filter { if (room == PetTheme.AQUARIUM) it.aquarium || it == FurnitureKind.BALL else !it.aquarium }
                        .forEach { k ->
                            val sel = placeKind == k
                            Column(
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (sel) ui.pink.copy(alpha = 0.35f) else Color.Transparent)
                                    .border(if (sel) 2.dp else 0.dp, if (sel) ui.pink else Color.Transparent, RoundedCornerShape(12.dp))
                                    .clickable { placeKind = if (sel) null else k; haptic() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                RetroLabel(k.emoji, size = 24.sp)
                                RetroLabel(k.label, size = 10.sp, color = ui.text)
                            }
                        }
                }
            }
        }

        // ── まだ誰もいない時 ──
        if (pets.isEmpty() && sheet == Sheet.NONE) {
            Card(theme, Modifier.align(Alignment.Center).widthIn(max = 420.dp)) {
                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    RetroLabel(if (theme == PetTheme.AQUARIUM) "🐠" else "🕷", size = 40.sp)
                    Spacer(Modifier.height(6.dp))
                    RetroLabel("まだアプリを飼っていません", size = 17.sp, weight = FontWeight.Bold, color = ui.text)
                    RetroLabel("好きなアプリを最大 $MAX_PETS 体まで放し飼いにできます", size = 12.sp, color = ui.text.copy(alpha = 0.7f))
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RetroButton("仲間を選ぶ", { sheet = Sheet.ROSTER }, color = ui.cyan)
                        RetroButton("おまかせで6体", { vm.autoPickPets(6) }, color = ui.pink, filled = true)
                    }
                }
            }
        }

        // ── シート ──
        AnimatedVisibility(sheet != Sheet.NONE, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(remember { MutableInteractionSource() }, null) { sheet = Sheet.NONE },
            )
        }
        AnimatedVisibility(
            sheet != Sheet.NONE,
            Modifier.align(Alignment.Center),
            enter = scaleIn(initialScale = 0.9f) + fadeIn(),
            exit = scaleOut(targetScale = 0.9f) + fadeOut(),
        ) {
            val shown = remember { arrayOf(Sheet.ROSTER) }
            if (sheet != Sheet.NONE) shown[0] = sheet
            Card(
                theme,
                Modifier
                    .windowInsetsPadding(insets)
                    .padding(vertical = 12.dp)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(0.82f)
                    .fillMaxHeight(),
            ) {
                when (shown[0]) {
                    Sheet.ROSTER -> RosterSheet(vm, data, apps, icons) { sheet = Sheet.NONE }
                    else -> {
                        SheetHeader("飼育モードの設定") { sheet = Sheet.NONE }
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)) {
                            PetSettingsContent(data.settings, vm, inPetMode = true)
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// 仲間を選ぶ
// ─────────────────────────────────────────────

@Composable
private fun ColumnScope.RosterSheet(vm: LauncherViewModel, data: LauncherData, apps: List<AppInfo>, icons: Map<String, ImageBitmap>, onClose: () -> Unit) {
    val ui = LocalPalette.current
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val chosen = data.pets.map { it.key }.toSet()
    SheetHeader("仲間を選ぶ  ${chosen.size}/$MAX_PETS", onClose)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(50))
                .background(ui.text.copy(alpha = 0.08f))
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            if (query.isEmpty()) RetroLabel("🔍 アプリを探す", size = 14.sp, color = ui.text.copy(alpha = 0.45f))
            BasicTextField(
                query, { query = it }, singleLine = true,
                textStyle = TextStyle(color = ui.text, fontSize = 14.sp, fontFamily = LocalRetroFont.current),
                cursorBrush = SolidColor(ui.pink),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        RetroButton("おまかせ", { vm.autoPickPets(6) }, color = ui.pink)
        if (chosen.isNotEmpty()) RetroButton("全部外す", { chosen.forEach { vm.togglePet(it) } }, color = ui.purple)
    }
    val q = query.trim().lowercase()
    val list = apps
        .filter { it.key !in data.hiddenApps && (q.isEmpty() || it.label.lowercase().contains(q)) }
        .sortedWith(compareByDescending<AppInfo> { it.key in chosen }.thenBy { it.label.lowercase() })
    LazyVerticalGrid(
        GridCells.Adaptive(84.dp),
        Modifier.fillMaxWidth().weight(1f),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(list, key = { it.key }) { app ->
            val on = app.key in chosen
            Column(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) ui.mint.copy(alpha = 0.22f) else Color.Transparent)
                    .border(if (on) 2.dp else 0.dp, if (on) ui.mint else Color.Transparent, RoundedCornerShape(14.dp))
                    .clickable {
                        if (!vm.togglePet(app.key)) Toast.makeText(context, "飼えるのは最大 $MAX_PETS 体までです", Toast.LENGTH_SHORT).show()
                    }
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    val icon = icons[app.key]
                    if (icon != null) Image(icon, app.label, Modifier.size(48.dp))
                    else Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(ui.purple))
                    if (on) RetroLabel("✔", size = 14.sp, color = Color.White, weight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(50)).background(ui.mint).padding(horizontal = 5.dp))
                }
                Spacer(Modifier.height(4.dp))
                RetroLabel(app.label, size = 11.sp, color = ui.text, align = TextAlign.Center)
            }
        }
    }
}

// ─────────────────────────────────────────────
// 設定（飼育モードの中と、通常の設定画面の両方で使う）
// ─────────────────────────────────────────────

@Composable
fun PetSettingsContent(s: LauncherSettings, vm: LauncherViewModel, inPetMode: Boolean) {
    val cfg = s.pet
    val ui = LocalPalette.current
    val context = LocalContext.current
    fun u(f: (PetConfig) -> PetConfig) = vm.updateSettings { it.copy(pet = f(it.pet)) }
    SectionHeader("基本")
    SwitchSetting(
        "横持ちで飼育モード", cfg.enabled,
        "スマホを横に持つと飼育モード、縦に戻すと通常のホーム。縦持ちの間はこの機能は完全に止まります",
    ) { v -> u { it.copy(enabled = v) } }
    SwitchSetting("回転ロック中でも横向きにする", cfg.ignoreRotationLock, "OFF の時は、画面の自動回転がオンの時だけ切り替わります") { v -> u { it.copy(ignoreRotationLock = v) } }
    ChoiceSetting("テーマ", PetTheme.entries, cfg.theme, { it.label }, "アクアリウムではアプリが魚になって泳ぎます") { v -> u { it.copy(theme = v) } }
    SectionHeader("動き・見た目")
    SliderSetting("動く速さ", cfg.speed, 0.3f..2.5f, format = { "%.1f倍".format(it) }) { v -> u { it.copy(speed = v) } }
    SliderSetting("大きさ", cfg.scale, 0.6f..1.8f, format = { "%.1f倍".format(it) }) { v -> u { it.copy(scale = v) } }
    ChoiceSetting("足の数", listOf(2, 3, 4, 5), cfg.legPairs, { "${it * 2}本" }, "部屋のテーマの時") { v -> u { it.copy(legPairs = v) } }
    SwitchSetting("名前を表示", cfg.showNames) { v -> u { it.copy(showNames = v) } }
    SwitchSetting("振動", cfg.haptic, "なでた時・長押しした時") { v -> u { it.copy(haptic = v) } }
    SectionHeader("お世話")
    SliderSetting("お腹が空くまでの時間", cfg.hungerHours, 0.5f..48f, "お腹いっぱいからペコペコになるまで。アプリを閉じている間も減ります", format = { "%.1f時間".format(it) }) { v -> u { it.copy(hungerHours = v) } }
    SectionHeader("軽量化")
    ChoiceSetting("最大フレームレート", listOf(15, 20, 30, 45, 60), cfg.fps, { "${it}fps" }, "低いほど電池にやさしい") { v -> u { it.copy(fps = v) } }
    SectionHeader("片付け")
    ActionSetting("この部屋の家具を全部片付ける", "CLEAR", color = ui.pink) {
        vm.clearFurniture(roomOf(cfg.theme))
        Toast.makeText(context, "片付けました", Toast.LENGTH_SHORT).show()
    }
    if (!inPetMode) {
        Spacer(Modifier.height(8.dp))
        RetroLabel("飼っているアプリ: ${vm.data.value.pets.size}/$MAX_PETS（飼育モードの「🐾 仲間」か、アプリの長押しメニューで選べます）", size = 12.sp, color = ui.text.copy(alpha = 0.75f), maxLines = 3)
    }
}

// ─────────────────────────────────────────────
// テーマに合わせた部品
// ─────────────────────────────────────────────

private fun cardShape(theme: PetTheme): Shape = when (theme) {
    PetTheme.CASUAL -> RoundedCornerShape(22.dp)
    PetTheme.VAPOR -> RoundedCornerShape(6.dp)
    PetTheme.AQUARIUM -> RoundedCornerShape(18.dp)
}

@Composable
private fun Card(theme: PetTheme, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val ui = LocalPalette.current
    val shape = cardShape(theme)
    val (bg, border, elevation) = when (theme) {
        PetTheme.CASUAL -> Triple(Color(0xFFFFFBF5), Color(0xFFFFCCBC), 8.dp)
        PetTheme.VAPOR -> Triple(ui.panel.copy(alpha = 0.94f), ui.pink, 0.dp)
        PetTheme.AQUARIUM -> Triple(Color(0xE606324A), Color(0x88B2EBF2), 0.dp)
    }
    Column(
        modifier
            .shadow(elevation, shape)
            .clip(shape)
            .background(bg)
            .border(if (theme == PetTheme.CASUAL) 2.dp else 1.5.dp, border, shape),
        content = content,
    )
}

@Composable
private fun Pill(theme: PetTheme, alpha: Float = 1f, content: @Composable () -> Unit) {
    val ui = LocalPalette.current
    val bg = when (theme) {
        PetTheme.CASUAL -> Color.White
        PetTheme.VAPOR -> ui.panel
        PetTheme.AQUARIUM -> Color(0xFF06324A)
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg.copy(alpha = 0.88f * alpha))
            .border(1.dp, ui.pink.copy(alpha = 0.6f * alpha), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) { content() }
}

@Composable
private fun ToolButton(theme: PetTheme, emoji: String, label: String, selected: Boolean, size: Dp = 64.dp, onClick: () -> Unit) {
    val ui = LocalPalette.current
    val shape = when (theme) {
        PetTheme.CASUAL -> RoundedCornerShape(20.dp)
        PetTheme.VAPOR -> RoundedCornerShape(4.dp)
        PetTheme.AQUARIUM -> RoundedCornerShape(50)
    }
    val bg = when (theme) {
        PetTheme.CASUAL -> if (selected) Color(0xFFFFE0B2) else Color.White
        PetTheme.VAPOR -> if (selected) ui.pink.copy(alpha = 0.45f) else ui.panel.copy(alpha = 0.85f)
        PetTheme.AQUARIUM -> if (selected) Color(0xCC26C6DA) else Color(0x9906324A)
    }
    val border = if (selected) ui.pink else when (theme) {
        PetTheme.CASUAL -> Color(0xFFFFCCBC)
        PetTheme.VAPOR -> ui.cyan.copy(alpha = 0.6f)
        PetTheme.AQUARIUM -> Color(0x88B2EBF2)
    }
    Column(
        Modifier
            .size(size)
            .shadow(if (theme == PetTheme.CASUAL) 4.dp else 0.dp, shape)
            .clip(shape)
            .background(bg)
            .border(if (selected) 2.5.dp else 1.5.dp, border, shape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RetroLabel(emoji, size = 22.sp)
        RetroLabel(label, size = 10.sp, color = ui.text, weight = FontWeight.Bold)
    }
}

@Composable
private fun SheetHeader(title: String, onClose: () -> Unit) {
    val ui = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RetroLabel(title, size = 17.sp, weight = FontWeight.Bold, color = ui.text, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { RetroLabel("✕", size = 18.sp, color = ui.text) }
    }
}
