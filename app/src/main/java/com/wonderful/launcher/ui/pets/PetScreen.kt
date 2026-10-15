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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.wonderful.launcher.data.Accessory
import com.wonderful.launcher.data.MicrobeStyle
import com.wonderful.launcher.data.WidgetTarget
import com.wonderful.launcher.ui.panels.AddSystemWidget
import com.wonderful.launcher.ui.panels.WidgetChooser
import com.wonderful.launcher.ui.panels.WidgetContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import com.wonderful.launcher.data.AppInfo
import com.wonderful.launcher.data.EyeStyle
import com.wonderful.launcher.data.LegColor
import com.wonderful.launcher.data.LegStyle
import com.wonderful.launcher.data.availableIn
import com.wonderful.launcher.data.lookFor
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
import com.wonderful.launcher.ui.components.RetroChip
import com.wonderful.launcher.ui.components.RetroLabel
import com.wonderful.launcher.ui.components.SectionHeader
import com.wonderful.launcher.ui.theme.FrameGate
import com.wonderful.launcher.ui.theme.LocalPalette
import com.wonderful.launcher.ui.theme.LocalRetroFont
import com.wonderful.launcher.ui.theme.RetroPalette
import com.wonderful.launcher.ui.theme.RetroTheme
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    PetTheme.MEADOW -> base.copy(
        pink = Color(0xFFFF7043), cyan = Color(0xFF43A047), purple = Color(0xFF8D6E63),
        yellow = Color(0xFFF9A825), mint = Color(0xFF2E7D32), panel = Color(0xFFF9FFF0), text = Color(0xFF33691E),
        titleStart = Color(0xFF7CB342), titleEnd = Color(0xFFFFCA28),
    )
    PetTheme.BODY -> base.copy(
        pink = Color(0xFFFF5C8A), cyan = Color(0xFF80DEEA), purple = Color(0xFFCE93D8),
        yellow = Color(0xFFFFE082), mint = Color(0xFFA5D6A7), panel = Color(0xFF2A0A12), text = Color(0xFFFFE4EC),
        titleStart = Color(0xFFFF5C8A), titleEnd = Color(0xFFCE93D8),
    )
    PetTheme.AQUARIUM -> base.copy(
        pink = Color(0xFFFF8A65), cyan = Color(0xFF4DD0E1), purple = Color(0xFF4FC3F7),
        yellow = Color(0xFFFFF59D), mint = Color(0xFF80CBC4), panel = Color(0xFF06324A), text = Color(0xFFE0F7FA),
        titleStart = Color(0xFF4DD0E1), titleEnd = Color(0xFF80CBC4),
    )
}

/** テーマの家具の部屋（カジュアルとヴェイパーは同じ部屋、アクアリウムは別） */
fun roomOf(theme: PetTheme): PetTheme = when (theme) {
    PetTheme.AQUARIUM -> PetTheme.AQUARIUM
    PetTheme.MEADOW -> PetTheme.MEADOW
    PetTheme.BODY -> PetTheme.BODY
    PetTheme.CASUAL, PetTheme.VAPOR -> PetTheme.CASUAL
}

@Composable
fun PetScreen(vm: LauncherViewModel, onAddSystemWidget: AddSystemWidget) {
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
            PetRoom(vm, data, apps, icons, base, onAddSystemWidget)
        }
    }
}

@Composable
private fun PetRoom(
    vm: LauncherViewModel, data: LauncherData, apps: List<AppInfo>, icons: Map<String, ImageBitmap>, base: RetroPalette,
    onAddSystemWidget: AddSystemWidget,
) {
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
    /** 家具モードで選んでいる家具（大きさ変更・片付けのメニュー） */
    var selectedId by remember { mutableStateOf<String?>(null) }
    /** 家具として置くウィジェットを選んでいる時の置き場所 */
    var widgetTarget by remember { mutableStateOf<WidgetTarget?>(null) }
    val scope = rememberCoroutineScope()
    // 写真：置く場所 or 差し替える家具を覚えておいてから、写真を選ぶ
    var pendingPhoto by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var replacePhotoId by remember { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val pos = pendingPhoto
        val replace = replacePhotoId
        pendingPhoto = null
        replacePhotoId = null
        if (uri != null) scope.launch {
            val name = withContext(Dispatchers.IO) { PetPhotos.import(context, uri) }
            when {
                name == null -> Toast.makeText(context, "写真を読み込めませんでした", Toast.LENGTH_SHORT).show()
                replace != null -> vm.setFurniturePhoto(replace, name)
                pos != null -> if (!vm.addFurniture(FurnitureKind.PHOTO, pos.first, pos.second, roomOf(data.settings.pet.theme), photo = name)) {
                    PetPhotos.delete(context, name)
                    Toast.makeText(context, "家具は $MAX_FURNITURE 個までです", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    fun pickPhoto() = photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    var sized by remember { mutableStateOf(false) }
    val room = roomOf(theme)
    val colors = remember(theme, base) { petColors(theme, base) }
    val appMap = remember(apps) { apps.associateBy { it.key } }
    val pets = data.pets.filter { appMap.containsKey(it.key) }
    val roomFurniture = data.furniture.filter { it.room == room }
    val looks = remember(data.pets, cfg) { data.pets.associate { it.key to cfg.lookFor(it, it.key) } }

    fun haptic(kind: Int = HapticFeedbackConstants.CLOCK_TICK) {
        if (cfg.haptic) view.performHapticFeedback(kind)
    }

    // 設定を計算に反映
    val radiusPx = with(density) { 30.dp.toPx() } * cfg.scale
    SideEffect {
        sim.radius = radiusPx
        sim.speedFactor = cfg.speed
        sim.hungerSeconds = cfg.hungerHours * 3600f
        if (theme != PetTheme.BODY) sim.changeLegPairs(cfg.legPairs)
        sim.setAquarium(theme == PetTheme.AQUARIUM)
        sim.setBodyMode(theme == PetTheme.BODY)
        sim.legless = theme == PetTheme.BODY && cfg.microbe == MicrobeStyle.MACROPHAGE
        sim.autoFood = theme == PetTheme.BODY && cfg.autoBacteria
        // T2 ファージの尾繊維は 6 本
        if (theme == PetTheme.BODY && cfg.microbe == MicrobeStyle.PHAGE) sim.changeLegPairs(3)
        sim.socialEnabled = cfg.social
    }

    // 効果音（飼育モードの間だけ用意する）
    val sound = remember { PetSound(context) }
    LaunchedEffect(sound) { withContext(Dispatchers.IO) { sound.load() } }
    DisposableEffect(sound) { onDispose { sound.release() } }
    val cfgNow by rememberUpdatedState(cfg)
    fun playSounds(events: List<SoundEvent>) {
        val c = cfgNow
        if (c.sound) sound.play(events, sim.right, c.soundVolume, c.soundRespectSilent)
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
    LaunchedEffect(roomFurniture, sized, unit) {
        if (sized) sim.syncFurnitureSpecs(roomFurniture.map {
            val (wPx, hPx) = widgetSizePx(it, unit)
            FurnitureSpec(it.id, it.kind, it.x, it.y, it.scale, wPx, hPx, it.photo)
        })
    }
    // 写真を読み込む（裏で、まだ読んでいない物だけ）
    val photos = remember { mutableStateMapOf<String, ImageBitmap>() }
    val photoNames = roomFurniture.mapNotNull { it.photo.takeIf { n -> n.isNotEmpty() } }.toSet()
    LaunchedEffect(photoNames) {
        photos.keys.filter { it !in photoNames }.forEach { photos.remove(it) }
        photoNames.filter { it !in photos }.forEach { name ->
            withContext(Dispatchers.IO) { PetPhotos.load(context, name) }?.let { photos[name] = it.asImageBitmap() }
        }
    }
    val resizing = remember { mutableStateMapOf<String, Pair<Float, Float>>() }
    // 片付けた家具が選ばれたままにならないように
    if (selectedId != null && roomFurniture.none { it.id == selectedId }) selectedId = null

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
                    if (sim.sounds.isNotEmpty()) {
                        playSounds(sim.sounds.toList())
                        sim.sounds.clear()
                    }
                    frame++
                }
                last = now
            }
        }
    }

    BackHandler(enabled = sheet != Sheet.NONE || tool != Tool.PAT || widgetTarget != null) {
        when {
            widgetTarget != null -> widgetTarget = null
            sheet != Sheet.NONE -> sheet = Sheet.NONE
            selectedId != null -> selectedId = null
            else -> { tool = Tool.PAT; placeKind = null }
        }
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
                                    playSounds(listOf(SoundEvent(Sfx.LAUNCH, pet.x, pet.voice, 1f)))
                                    appMapNow[pet.key]?.let { vm.launch(it) }
                                } else if (fur != null && toolNow == Tool.FURNITURE) {
                                    if (hapticNow) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    sim.sfx(Sfx.REMOVE, fur.x)
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
                                        sim.sfx(Sfx.KICK, fur.x)
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
                                        // 置いてある家具をタップ → 選ぶ（大きさ変更・片付け）
                                        fur != null -> { selectedId = if (selectedId == fur.id) null else fur.id; if (hapticNow) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                                        selectedId != null -> selectedId = null
                                        k == null -> Toast.makeText(context, "下から家具を選んでください", Toast.LENGTH_SHORT).show()
                                        furnitureCount >= MAX_FURNITURE -> Toast.makeText(context, "家具は $MAX_FURNITURE 個までです", Toast.LENGTH_SHORT).show()
                                        k == FurnitureKind.PHOTO -> { pendingPhoto = fraction(start.x, start.y); pickPhoto() }
                                        k == FurnitureKind.WIDGET -> {
                                            val (fx, fy) = fraction(start.x, start.y)
                                            widgetTarget = WidgetTarget.Furniture(roomNow, fx, fy)
                                        }
                                        else -> {
                                            val (fx, fy) = fraction(start.x, start.y)
                                            vm.addFurniture(k, fx, fy, roomNow)
                                            sim.sfx(Sfx.PLACE, start.x, 0.9f + Random.nextFloat() * 0.2f)
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
                                        sim.sfx(Sfx.PLACE, movingFur.x, 1.2f, 0.6f)
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
                sim.furniture.forEach { drawFurniture(it, sim, theme, colors, base, emoji, t, unit, it.id == selectedId, photos) }
                sim.food.forEach { drawFood(it, sim, unit) }
                sim.pets.forEach { p ->
                    val icon = icons[p.key]
                    val fin = tints.getOrPut(p.key) { iconTint(icon) }
                    drawFish(p, sim, icon, fin, looks[p.key] ?: cfg.lookFor(null, p.key), t, unit)
                }
            } else {
                // ラグは床に敷くので先に。他は奥（上）から順に描く
                sim.furniture.filter { it.kind == FurnitureKind.RUG }.forEach { drawFurniture(it, sim, theme, colors, base, emoji, t, unit, it.id == selectedId, photos) }
                val items = ArrayList<Pair<Float, () -> Unit>>()
                sim.furniture.filter { it.kind != FurnitureKind.RUG }.forEach { f -> items += f.y to { drawFurniture(f, sim, theme, colors, base, emoji, t, unit, f.id == selectedId, photos) } }
                sim.food.forEach { f -> items += f.y to { if (theme == PetTheme.BODY) drawEColi(f, sim, t, unit) else drawFood(f, sim, unit) } }
                sim.pets.forEach { p ->
                    val icon = icons[p.key]
                    val tint = tints.getOrPut(p.key) { iconTint(icon) }
                    val look = looks[p.key] ?: cfg.lookFor(null, p.key)
                    // 家具の上に乗っている子は家具より手前、テントや小屋の中の子は奥に描く
                    val onF = sim.furniture.firstOrNull { it.id == p.targetFurniture }
                    val key = when {
                        onF == null || p.mood !in setOf(Mood.USE, Mood.SIT, Mood.SLEEP) -> p.y + p.lift * sim.radius * 3f
                        onF.kind == FurnitureKind.TENT || onF.kind == FurnitureKind.DOGHOUSE -> onF.y - 1f
                        onF.kind in ON_TOP -> onF.y + 1f
                        else -> p.y + p.lift * sim.radius * 3f
                    }
                    items += key to {
                        when {
                            theme != PetTheme.BODY -> drawSpider(p, sim, icon, tint, look, colors, theme, t, unit)
                            cfg.microbe == MicrobeStyle.PHAGE -> drawPhage(p, sim, icon, tint, look, colors, t, unit)
                            else -> drawMacrophage(p, sim, icon, tint, look, colors, t, unit)
                        }
                    }
                }
                items.sortBy { it.first }
                items.forEach { it.second() }
                if (theme == PetTheme.MEADOW) drawMeadowOverlay(t, unit)
            }
            if (showFurnitureSel) {
                sim.furniture.filter { it.kind != FurnitureKind.WIDGET && it.id != selectedId }.forEach { f ->
                    drawCircle(colors.accent.copy(alpha = 0.6f), sim.furnitureRadius(f) * 1.25f, Offset(f.x, f.y), style = androidx.compose.ui.graphics.drawscope.Stroke(unit * 1.5f))
                }
            }
            drawParticles(sim, colors, emoji, unit, labelPaint)
            sim.pets.forEach { p ->
                drawPetLabel(p, sim, appMap[p.key]?.label ?: "", cfg.showNames, colors, labelPaint, emoji, unit)
            }
            if (cfg.emotes) sim.pets.forEach { drawEmoteBalloon(it, sim, emoji, colors, unit) }
        }

        // ── 家具として置いたウィジェット（本物のウィジェットを上に重ねる） ──
        // 角をドラッグして大きさを変えている途中の大きさ（px）
        roomFurniture.filter { it.kind == FurnitureKind.WIDGET && it.widget != null }.forEach { item ->
            key(item.id) {
                val w = item.widget!!
                val (wPx, hPx) = resizing[item.id] ?: widgetSizePx(item, unit)
                val wDp = (wPx / unit).toInt()
                val hDp = (hPx / unit).toInt()
                val shape = RoundedCornerShape(if (theme == PetTheme.VAPOR) 6.dp else 16.dp)
                val editing = tool == Tool.FURNITURE
                Box(
                    Modifier
                        .offset {
                            val b = sim.furniture.firstOrNull { it.id == item.id }
                            if (frame < 0 || b == null) IntOffset.Zero
                            else IntOffset((b.x - wPx / 2).roundToInt(), (b.y - hPx / 2).roundToInt())
                        }
                        .size(wDp.dp, hDp.dp)
                        .clip(shape)
                        .background(
                            when (theme) {
                                PetTheme.VAPOR -> ui.panel.copy(alpha = 0.85f)
                                PetTheme.AQUARIUM -> Color(0x9906324A)
                                else -> Color.White.copy(alpha = 0.85f)
                            },
                        )
                        .border(if (item.id == selectedId) 3.dp else 1.5.dp, if (item.id == selectedId) ui.pink else ui.cyan.copy(alpha = 0.6f), shape),
                ) {
                    WidgetContent(w, vm, wDp, hDp, editing = editing, onAddSystem = onAddSystemWidget)
                    if (editing) {
                        // 家具モードの間は、ウィジェットを触らずに動かす・選ぶ・片付ける
                        Box(
                            Modifier
                                .matchParentSize()
                                .background(if (item.id == selectedId) ui.pink.copy(alpha = 0.12f) else Color.Transparent)
                                .pointerInput(item.id) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown()
                                        val kind = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                            var r = 1
                                            while (true) {
                                                val ev = awaitPointerEvent()
                                                val c = ev.changes.firstOrNull { it.id == down.id }
                                                if (c == null || !c.pressed) { r = 1; break }
                                                if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) { r = 2; break }
                                            }
                                            r
                                        } ?: 0
                                        val body = sim.furniture.firstOrNull { it.id == item.id }
                                        when (kind) {
                                            0 -> {
                                                if (hapticNow) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                                body?.let { sim.sfx(Sfx.REMOVE, it.x) }
                                                vm.removeFurniture(item.id)
                                                waitForUpOrCancellation()
                                            }
                                            1 -> selectedId = if (selectedId == item.id) null else item.id
                                            else -> if (body != null) {
                                                while (true) {
                                                    val ev = awaitPointerEvent()
                                                    val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                                                    if (!c.pressed) break
                                                    val d = c.positionChange()
                                                    body.x = (body.x + d.x).coerceIn(sim.left, sim.right)
                                                    body.y = (body.y + d.y).coerceIn(sim.top, sim.bottom)
                                                    c.consume()
                                                }
                                                val fx = ((body.x - sim.left) / (sim.right - sim.left)).coerceIn(0f, 1f)
                                                val fy = ((body.y - sim.top) / (sim.bottom - sim.top)).coerceIn(0f, 1f)
                                                vm.moveFurniture(item.id, fx, fy)
                                                sim.sfx(Sfx.PLACE, body.x, 1.2f, 0.6f)
                                            }
                                        }
                                    }
                                },
                        )
                    }
                }
                // 選んでいる時は右下の角をドラッグして、幅と高さを自由に変えられる
                if (editing && item.id == selectedId) {
                    val handle = 30.dp
                    Box(
                        Modifier
                            .offset {
                                val b = sim.furniture.firstOrNull { it.id == item.id }
                                if (frame < 0 || b == null) IntOffset.Zero
                                else IntOffset((b.x + wPx / 2 - handle.toPx() * 0.6f).roundToInt(), (b.y + hPx / 2 - handle.toPx() * 0.6f).roundToInt())
                            }
                            .size(handle)
                            .clip(RoundedCornerShape(50))
                            .background(ui.pink)
                            .border(2.dp, Color.White, RoundedCornerShape(50))
                            .pointerInput(item.id) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    var cur = resizing[item.id] ?: widgetSizePx(item, unit)
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!c.pressed) break
                                        val d = c.positionChange()
                                        // 真ん中を中心に広がるので、角を指に合わせるには 2 倍
                                        val minPx = 48f * unit
                                        cur = (cur.first + d.x * 2f).coerceIn(minPx, sim.right - sim.left) to (cur.second + d.y * 2f).coerceIn(minPx, sim.bottom - sim.top)
                                        resizing[item.id] = cur
                                        sim.furniture.firstOrNull { it.id == item.id }?.let { it.solidW = cur.first; it.solidH = cur.second }
                                        c.consume()
                                    }
                                    vm.setFurnitureSize(item.id, cur.first / unit, cur.second / unit)
                                    sim.sfx(Sfx.PLACE, sim.furniture.firstOrNull { it.id == item.id }?.x ?: 0f, 1.3f, 0.5f)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) { RetroLabel("⤡", size = 14.sp, color = Color.White, weight = FontWeight.Bold) }
                }
            }
        }
        // 保存された大きさが反映されたら、途中の大きさは捨てる
        LaunchedEffect(roomFurniture) { resizing.clear() }

        // ── 選んだ家具のメニュー ──
        val selected = roomFurniture.firstOrNull { it.id == selectedId }
        AnimatedVisibility(
            tool == Tool.FURNITURE && selected != null,
            Modifier.align(Alignment.BottomStart).windowInsetsPadding(insets).padding(start = 12.dp, bottom = 96.dp),
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut(),
        ) {
            val shownSel = remember { arrayOfNulls<com.wonderful.launcher.data.FurnitureItem>(1) }
            if (selected != null) shownSel[0] = selected
            val f = shownSel[0] ?: return@AnimatedVisibility
            Card(theme) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RetroLabel("${f.kind.emoji} ${f.kind.label}", size = 13.sp, weight = FontWeight.Bold, color = ui.text)
                    RetroLabel("${(f.scale * 100).roundToInt()}%", size = 12.sp, color = ui.yellow)
                    RetroButton("－", { vm.setFurnitureScale(f.id, f.scale - 0.2f) }, color = ui.cyan)
                    RetroButton("＋", { vm.setFurnitureScale(f.id, f.scale + 0.2f) }, color = ui.cyan)
                    if (f.kind == FurnitureKind.PHOTO) RetroButton("写真を変える", { replacePhotoId = f.id; pickPhoto() }, color = ui.mint)
                    if (f.kind == FurnitureKind.WIDGET && (f.wDp > 0f || f.scale != 1f)) RetroButton("元の大きさ", { vm.resetFurnitureSize(f.id) }, color = ui.yellow)
                    if (f.kind == FurnitureKind.WIDGET) RetroButton("選び直す", {
                        vm.removeFurniture(f.id)
                        widgetTarget = WidgetTarget.Furniture(room, f.x, f.y)
                    }, color = ui.mint)
                    RetroButton("片付ける", {
                        sim.furniture.firstOrNull { it.id == f.id }?.let { sim.sfx(Sfx.REMOVE, it.x) }
                        vm.removeFurniture(f.id)
                        selectedId = null
                    }, color = ui.pink)
                }
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
                val (em, lb) = if (t == Tool.FOOD && theme == PetTheme.BODY) "🦠" to "大腸菌" else t.emoji to t.label
                ToolButton(theme, em, lb, tool == t) {
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
                    when (theme) {
                        PetTheme.AQUARIUM -> "🐠 アプリ水族館"
                        PetTheme.MEADOW -> "🌼 アプリ牧場"
                        PetTheme.BODY -> if (cfg.microbe == MicrobeStyle.PHAGE) "🦠 アプリ体内（T2ファージ）" else "🦠 アプリ体内（マクロファージ）"
                        else -> "🕷 アプリ飼育"
                    } + "  ${pets.size}/$MAX_PETS",
                    size = 13.sp, weight = FontWeight.Bold, color = ui.text,
                )
            }
            val hint = when (tool) {
                Tool.PAT -> "タップ・なぞってなでる / 長押しで起動 / ドラッグで運ぶ"
                Tool.FOOD -> if (theme == PetTheme.BODY) "タップした所に大腸菌を放す（逃げ回るので追いかけて食べます）" else "タップした所にエサ（なぞるとばらまく）"
                Tool.FURNITURE -> "下から選んでタップで置く / 家具をタップで大きさ変更（ウィジェットは角の⤡で自由に） / ドラッグで移動 / 長押しで片付け"
            }
            if (cfg.showHints) Pill(theme, alpha = 0.75f) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RetroLabel(hint, size = 11.sp, color = ui.text)
                    Spacer(Modifier.width(6.dp))
                    // ✕ で操作説明を消す（設定 → 飼育 でまた出せる）
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                vm.updateSettings { it.copy(pet = it.pet.copy(showHints = false)) }
                                Toast.makeText(context, "操作説明を消しました（設定 → 飼育 でまた表示できます）", Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center,
                    ) { RetroLabel("✕", size = 12.sp, color = ui.text, weight = FontWeight.Bold) }
                }
            }
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
                        .filter { it.availableIn(room) }
                        .forEach { k ->
                            val sel = placeKind == k
                            Column(
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (sel) ui.pink.copy(alpha = 0.35f) else Color.Transparent)
                                    .border(if (sel) 2.dp else 0.dp, if (sel) ui.pink else Color.Transparent, RoundedCornerShape(12.dp))
                                    .clickable { placeKind = if (sel) null else k; selectedId = null; haptic() }
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
                    RetroLabel(when (theme) { PetTheme.AQUARIUM -> "🐠"; PetTheme.MEADOW -> "🌼"; PetTheme.BODY -> "🦠"; else -> "🕷" }, size = 40.sp)
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

        // ── 家具にするウィジェットを選ぶ ──
        val wt = widgetTarget
        if (wt != null) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(remember { MutableInteractionSource() }, null) { widgetTarget = null })
            WidgetChooser(
                title = "家具として置くウィジェット",
                target = wt,
                vm = vm,
                onAddSystem = onAddSystemWidget,
                onClose = { widgetTarget = null },
                modifier = Modifier.align(Alignment.Center).windowInsetsPadding(insets).padding(12.dp).widthIn(max = 640.dp).fillMaxWidth(0.85f).fillMaxHeight(),
            )
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
    var tab by remember { mutableIntStateOf(0) }
    val chosen = data.pets.map { it.key }.toSet()
    SheetHeader(if (tab == 0) "仲間を選ぶ  ${chosen.size}/$MAX_PETS" else "着せ替え", onClose)
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RetroChip("🐾 仲間を選ぶ", tab == 0, { tab = 0 })
        RetroChip("👒 着せ替え", tab == 1, { tab = 1 })
    }
    if (tab == 1) {
        DressUp(vm, data, apps, icons)
        return
    }
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
// 着せ替え（1 体ずつ）
// ─────────────────────────────────────────────

@Composable
private fun ColumnScope.DressUp(vm: LauncherViewModel, data: LauncherData, apps: List<AppInfo>, icons: Map<String, ImageBitmap>) {
    val ui = LocalPalette.current
    val cfg = data.settings.pet
    val unit = LocalDensity.current.density
    if (data.pets.isEmpty()) {
        RetroLabel("まだ誰もいません。「仲間を選ぶ」から選んでください", size = 13.sp, color = ui.text, modifier = Modifier.padding(20.dp), maxLines = 2)
        return
    }
    val colors = petColors(cfg.theme, ui)
    LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        items(data.pets, key = { it.key }) { e ->
            val app = apps.firstOrNull { it.key == e.key }
            val look = cfg.lookFor(e, e.key)
            val icon = icons[e.key]
            val tint = remember(icon) { iconTint(icon) }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(96.dp)) {
                    val hs = size.width * 0.42f
                    val cx = size.width / 2
                    val cy = size.height * 0.58f
                    val r = hs / 1.9f
                    if (cfg.theme != PetTheme.AQUARIUM) for (i in 0 until 4) for (side in intArrayOf(-1, 1)) {
                        val k = i - 1.5f
                        drawLeg(
                            look.legStyle,
                            Offset(cx + side * hs * 0.3f, cy + k * hs * 0.15f),
                            Offset(cx + side * hs * 0.66f, cy + k * hs * 0.22f - hs * 0.2f),
                            Offset(cx + side * hs * 0.86f, cy + k * hs * 0.3f + hs * 0.25f),
                            r, legColor(look, colors.leg, tint, i * 2 + side, 0f), colors.legGlow, unit,
                        )
                    }
                    drawCritterHead(cx, cy, hs, icon, ui.pink, look, 0f, false, 0f, Offset(0.3f, 0.6f), 0f, unit, e.key.hashCode())
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    RetroLabel(app?.label ?: e.key, size = 15.sp, weight = FontWeight.Bold, color = ui.text)
                    LookChips("目", listOf<EyeStyle?>(null) + EyeStyle.entries.filter { it != EyeStyle.MIXED }, e.eyes, { it?.label ?: "おまかせ" }) { v ->
                        vm.setPetLook(e.key) { it.copy(eyes = v) }
                    }
                    LookChips("飾り", listOf<Accessory?>(null) + Accessory.entries.filter { it != Accessory.MIXED }, e.accessory, { it?.label ?: "おまかせ" }) { v ->
                        vm.setPetLook(e.key) { it.copy(accessory = v) }
                    }
                    LookChips(if (cfg.theme == PetTheme.AQUARIUM) "ヒレ" else "足", listOf<LegStyle?>(null) + LegStyle.entries.filter { it != LegStyle.MIXED }, e.legStyle, { it?.label ?: "おまかせ" }) { v ->
                        vm.setPetLook(e.key) { it.copy(legStyle = v) }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> LookChips(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val ui = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RetroLabel(title, size = 11.sp, color = ui.text.copy(alpha = 0.7f), modifier = Modifier.width(30.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { o -> RetroChip(label(o), o == selected, { onSelect(o) }) }
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
    ChoiceSetting("テーマ", PetTheme.entries, cfg.theme, { it.label }, "アクアリウムではアプリが魚に、体内では微生物になって大腸菌を食べます") { v -> u { it.copy(theme = v) } }
    ChoiceSetting("体内での姿", MicrobeStyle.entries, cfg.microbe, { it.label }, "T2ファージは尾繊維で歩いて大腸菌に感染、マクロファージは仮足で包み込んで食べます") { v -> u { it.copy(microbe = v) } }
    SwitchSetting("体内で大腸菌が自然にわく", cfg.autoBacteria) { v -> u { it.copy(autoBacteria = v) } }
    SectionHeader("動き・見た目")
    SliderSetting("動く速さ", cfg.speed, 0.3f..2.5f, format = { "%.1f倍".format(it) }) { v -> u { it.copy(speed = v) } }
    SliderSetting("大きさ", cfg.scale, 0.6f..1.8f, format = { "%.1f倍".format(it) }) { v -> u { it.copy(scale = v) } }
    ChoiceSetting("足の数", listOf(2, 3, 4, 5), cfg.legPairs, { "${it * 2}本" }, "部屋のテーマの時") { v -> u { it.copy(legPairs = v) } }
    SectionHeader("見た目（みんな共通。1 体ずつ変えるには 🐾 仲間 →「着せ替え」）")
    ChoiceSetting("目", EyeStyle.entries, cfg.eyes, { it.label }, "「バラバラ」はアプリごとに違う目になります") { v -> u { it.copy(eyes = v) } }
    ChoiceSetting("頭の飾り", Accessory.entries, cfg.accessory, { it.label }) { v -> u { it.copy(accessory = v) } }
    ChoiceSetting("足", LegStyle.entries, cfg.legStyle, { it.label }, "アクアリウムではヒレの形が変わります") { v -> u { it.copy(legStyle = v) } }
    ChoiceSetting("足の色", LegColor.entries, cfg.legColor, { it.label }) { v -> u { it.copy(legColor = v) } }
    SwitchSetting("名前を表示", cfg.showNames) { v -> u { it.copy(showNames = v) } }
    SwitchSetting("操作説明を表示", cfg.showHints, "飼育モードの左上に出る、操作のヒントの文章") { v -> u { it.copy(showHints = v) } }
    SwitchSetting("振動", cfg.haptic, "なでた時・長押しした時") { v -> u { it.copy(haptic = v) } }
    SectionHeader("音")
    SwitchSetting("効果音", cfg.sound, "なでた時・ごはん・おしゃべり・いびき など") { v -> u { it.copy(sound = v) } }
    SliderSetting("音量", cfg.soundVolume, 0f..1f, format = { "${(it * 100).toInt()}%" }) { v -> u { it.copy(soundVolume = v) } }
    SwitchSetting("マナーモード中は鳴らさない", cfg.soundRespectSilent) { v -> u { it.copy(soundRespectSilent = v) } }
    SectionHeader("ふるまい")
    SwitchSetting("仲間どうしで遊ぶ", cfg.social, "おしゃべり・おにごっこ・ダンス・ハグ・あいさつ") { v -> u { it.copy(social = v) } }
    SwitchSetting("気持ちを吹き出しで出す", cfg.emotes, "うれしい・好き・おこ・びっくり・ねむい など") { v -> u { it.copy(emotes = v) } }
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
    PetTheme.CASUAL, PetTheme.MEADOW -> RoundedCornerShape(22.dp)
    PetTheme.VAPOR -> RoundedCornerShape(6.dp)
    PetTheme.AQUARIUM, PetTheme.BODY -> RoundedCornerShape(18.dp)
}

@Composable
private fun Card(theme: PetTheme, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val ui = LocalPalette.current
    val shape = cardShape(theme)
    val (bg, border, elevation) = when (theme) {
        PetTheme.CASUAL -> Triple(Color(0xFFFFFBF5), Color(0xFFFFCCBC), 8.dp)
        PetTheme.MEADOW -> Triple(Color(0xFFF9FFF0), Color(0xFFC5E1A5), 8.dp)
        PetTheme.VAPOR -> Triple(ui.panel.copy(alpha = 0.94f), ui.pink, 0.dp)
        PetTheme.AQUARIUM -> Triple(Color(0xE606324A), Color(0x88B2EBF2), 0.dp)
        PetTheme.BODY -> Triple(Color(0xE62A0A12), Color(0x88FF8A80), 0.dp)
    }
    Column(
        modifier
            .shadow(elevation, shape)
            .clip(shape)
            .background(bg)
            .border(if (theme == PetTheme.CASUAL || theme == PetTheme.MEADOW) 2.dp else 1.5.dp, border, shape),
        content = content,
    )
}

@Composable
private fun Pill(theme: PetTheme, alpha: Float = 1f, content: @Composable () -> Unit) {
    val ui = LocalPalette.current
    val bg = when (theme) {
        PetTheme.CASUAL, PetTheme.MEADOW -> Color.White
        PetTheme.VAPOR -> ui.panel
        PetTheme.AQUARIUM -> Color(0xFF06324A)
        PetTheme.BODY -> Color(0xFF2A0A12)
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
        PetTheme.CASUAL, PetTheme.MEADOW -> RoundedCornerShape(20.dp)
        PetTheme.VAPOR -> RoundedCornerShape(4.dp)
        PetTheme.AQUARIUM, PetTheme.BODY -> RoundedCornerShape(50)
    }
    val bg = when (theme) {
        PetTheme.CASUAL -> if (selected) Color(0xFFFFE0B2) else Color.White
        PetTheme.MEADOW -> if (selected) Color(0xFFDCEDC8) else Color.White
        PetTheme.VAPOR -> if (selected) ui.pink.copy(alpha = 0.45f) else ui.panel.copy(alpha = 0.85f)
        PetTheme.AQUARIUM -> if (selected) Color(0xCC26C6DA) else Color(0x9906324A)
        PetTheme.BODY -> if (selected) Color(0xCCFF5C8A) else Color(0x992A0A12)
    }
    val border = if (selected) ui.pink else when (theme) {
        PetTheme.CASUAL -> Color(0xFFFFCCBC)
        PetTheme.MEADOW -> Color(0xFFC5E1A5)
        PetTheme.VAPOR -> ui.cyan.copy(alpha = 0.6f)
        PetTheme.AQUARIUM -> Color(0x88B2EBF2)
        PetTheme.BODY -> Color(0x88FF8A80)
    }
    Column(
        Modifier
            .size(size)
            .shadow(if (theme == PetTheme.CASUAL || theme == PetTheme.MEADOW) 4.dp else 0.dp, shape)
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

/** 家具にしたウィジェットの大きさ（px）。ウィジェット画面の 1 マス = 40dp を基準に、家具の大きさをかける */
fun widgetSizePx(item: com.wonderful.launcher.data.FurnitureItem, density: Float): Pair<Float, Float> {
    val w = item.widget ?: return 0f to 0f
    // 自由に大きさを変えていたらその大きさ
    if (item.wDp > 0f && item.hDp > 0f) return (item.wDp * density * item.scale) to (item.hDp * density * item.scale)
    val cell = 40f * density * item.scale
    return (w.w * cell) to (w.h * cell)
}

/** 上に乗って遊ぶ家具 */
private val ON_TOP = setOf(
    FurnitureKind.BED, FurnitureKind.SOFA, FurnitureKind.RUG, FurnitureKind.TOWER, FurnitureKind.STUMP,
    FurnitureKind.MUSHROOM, FurnitureKind.ROCK, FurnitureKind.BLOODCELL, FurnitureKind.CELL, FurnitureKind.TUMOR,
)
