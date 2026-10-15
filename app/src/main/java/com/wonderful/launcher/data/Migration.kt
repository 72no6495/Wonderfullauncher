// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (c) 2026 72no6495

package com.wonderful.launcher.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * 保存データ・バックアップのバージョン移行。
 *
 * 古いバージョンで作ったバックアップを読み込んでも同じように使えるよう、
 * 読み込むたびに [migrate] を通す。Android に依存しない純粋な Kotlin なので単体テストできる。
 */
object Migration {

    /** 現在の保存形式バージョン */
    const val CURRENT_VERSION = 6

    fun migrate(input: LauncherData): LauncherData {
        var d = input
        if (d.dataVersion < 2) d = v1ToV2(d)
        if (d.dataVersion < 3) d = v2ToV3(d)
        if (d.dataVersion < 4) d = v3ToV4(d)
        if (d.dataVersion < 5) d = v4ToV5(d)
        if (d.dataVersion < 6) d = v5ToV6(d)
        return sanitize(d.copy(dataVersion = maxOf(d.dataVersion, CURRENT_VERSION)))
    }

    /**
     * v1 → v2
     * - 右下ボタンを「設定」→「ウィジェット」へ（設定は長押しで開ける）
     * - 時計タップの ON/OFF を、ジェスチャー割り当てに置き換え
     */
    private fun v1ToV2(d: LauncherData): LauncherData {
        var s = d.settings
        if (s.cornerBottomRight == CornerAction.SETTINGS) {
            s = s.copy(cornerBottomRight = CornerAction.WIDGETS)
        }
        s = s.copy(clockTapAction = if (s.clockTapOpensAlarm) GestureAction.ALARM else GestureAction.NONE)
        return d.copy(settings = s, dataVersion = 2)
    }

    /**
     * v2 → v3
     * - 「タップでバウンド」の ON/OFF を、タップエフェクトの種類に置き換え
     */
    private fun v2ToV3(d: LauncherData): LauncherData {
        val s = d.settings
        val effect = if (s.tapBounce) s.tapEffect else TapEffect.NONE
        return d.copy(settings = s.copy(tapEffect = effect), dataVersion = 3)
    }

    /**
     * v3 → v4
     * - 迷路とグリッチの設定を、壁紙ごとの詳細設定へ移動
     */
    private fun v3ToV4(d: LauncherData): LauncherData {
        val s = d.settings
        return d.copy(
            settings = s.copy(
                maze = s.maze.copy(cellDp = s.mazeCellDp),
                glitch = s.glitch.copy(intensity = s.glitchIntensity),
            ),
            dataVersion = 4,
        )
    }

    /**
     * v4 → v5
     * - 「時計を表示」の ON/OFF を、時計の位置に表示するものの選択に置き換え
     */
    private fun v4ToV5(d: LauncherData): LauncherData {
        val s = d.settings
        return d.copy(settings = s.copy(topSlot = if (s.showClock) TopSlot.CLOCK else TopSlot.NONE), dataVersion = 5)
    }

    /**
     * v5 → v6
     * - 「フレームレート制限なし」は高リフレッシュレートの端末で 120〜144fps になり発熱するため 60fps に
     *   （設定画面からいつでも「制限なし」に戻せる）
     */
    private fun v5ToV6(d: LauncherData): LauncherData {
        val s = d.settings
        return d.copy(settings = if (s.fpsLimit == 0) s.copy(fpsLimit = 60) else s, dataVersion = 6)
    }

    /**
     * どのバージョンから来ても壊れた値にならないよう整える。
     * - 範囲外の数値を丸める
     * - 設定画面へたどり着く手段が無くなっていたら長押しに割り当てる
     * - 重複や壊れたウィジェットを取り除く
     */
    fun sanitize(d: LauncherData): LauncherData {
        var s = d.settings
        s = s.copy(
            sphereSize = s.sphereSize.finiteIn(0.4f, 1.5f, 0.85f),
            iconSizeDp = s.iconSizeDp.finiteIn(32f, 88f, 54f),
            perspective = s.perspective.finiteIn(0f, 1f, 0.55f),
            depthFade = s.depthFade.finiteIn(0f, 0.95f, 0.7f),
            depthScale = s.depthScale.finiteIn(0f, 0.9f, 0.55f),
            dragSensitivity = s.dragSensitivity.finiteIn(0.2f, 3f, 1f),
            inertia = s.inertia.finiteIn(0f, 1f, 0.85f),
            autoRotateSpeed = s.autoRotateSpeed.finiteIn(0.02f, 1.5f, 0.25f),
            animSpeed = s.animSpeed.finiteIn(0.25f, 3f, 1f),
            clockScale = s.clockScale.finiteIn(0.5f, 1.6f, 1f),
            cornerSizeDp = s.cornerSizeDp.finiteIn(36f, 80f, 54f),
            panelOpacity = s.panelOpacity.finiteIn(0.4f, 1f, 0.9f),
            sphereMaxApps = s.sphereMaxApps.coerceIn(8, 200),
            searchColumns = s.searchColumns.coerceIn(3, 6),
            folderColumns = s.folderColumns.coerceIn(3, 6),
            recentCount = s.recentCount.coerceIn(5, 40),
            rubber = s.rubber.finiteIn(0f, 1f, 0.45f),
            iconFloat = s.iconFloat.finiteIn(0f, 1f, 0f),
            trailLength = s.trailLength.finiteIn(0f, 1f, 0f),
            dofBlur = s.dofBlur.finiteIn(0f, 1f, 0.35f),
            filmGrain = s.filmGrain.finiteIn(0f, 1f, 0f),
            mazeCellDp = s.mazeCellDp.finiteIn(14f, 60f, 26f),
            glitchIntensity = s.glitchIntensity.finiteIn(0f, 1f, 0.5f),
            rubberBounce = s.rubberBounce.finiteIn(0f, 1f, 0.6f),
            rubberStretch = s.rubberStretch.finiteIn(0f, 1f, 0.5f),
            rubberMaxLag = s.rubberMaxLag.finiteIn(0.1f, 3f, 0.8f),
            glowSize = s.glowSize.finiteIn(0.3f, 3f, 1f),
            trailSpacing = s.trailSpacing.finiteIn(0f, 1f, 0.5f),
            // 重くなりすぎないよう、細かさや数には上限を付ける
            maze = s.maze.copy(cellDp = s.maze.cellDp.finiteIn(12f, 80f, 26f), holdSec = s.maze.holdSec.finiteIn(0f, 30f, 2.6f)),
            life = s.life.copy(
                cellDp = s.life.cellDp.finiteIn(5f, 60f, 12f),
                stepMs = s.life.stepMs.finiteIn(16f, 2000f, 110f),
                density = s.life.density.finiteIn(0.01f, 0.95f, 0.28f),
                maxGenerations = s.life.maxGenerations.coerceIn(50, 100000),
                hudMarginDp = s.life.hudMarginDp.finiteIn(0f, 400f, 130f),
            ),
            cornerFloatAmount = s.cornerFloatAmount.finiteIn(0f, 1f, 0.5f),
            cornerFloatSpeed = s.cornerFloatSpeed.finiteIn(0.2f, 3f, 1f),
            chargeDurationSec = s.chargeDurationSec.finiteIn(1f, 10f, 3.5f),
            fpsLimit = if (s.fpsLimit in listOf(0, 15, 20, 30, 45, 60)) s.fpsLimit else 0,
            idleSleepSec = s.idleSleepSec.finiteIn(0f, 600f, 0f),
            topWidgetHeightDp = s.topWidgetHeightDp.finiteIn(60f, 500f, 150f),
            popupWidgetRows = s.popupWidgetRows.coerceIn(2, 14),
            matrix = s.matrix.copy(charSizeDp = s.matrix.charSizeDp.finiteIn(8f, 48f, 18f)),
            starfield = s.starfield.copy(count = s.starfield.count.coerceIn(10, 1500)),
            underwater = s.underwater.copy(bubbles = s.underwater.bubbles.coerceIn(0, 300), fish = s.underwater.fish.coerceIn(0, 40)),
            lava = s.lava.copy(blobs = s.lava.blobs.coerceIn(1, 20)),
            aurora = s.aurora.copy(ribbons = s.aurora.ribbons.coerceIn(1, 8)),
            vapor = s.vapor.copy(starCount = s.vapor.starCount.coerceIn(0, 500), horizon = s.vapor.horizon.finiteIn(0.3f, 0.85f, 0.62f)),
            pet = s.pet.copy(
                speed = s.pet.speed.finiteIn(0.2f, 3f, 1f),
                scale = s.pet.scale.finiteIn(0.5f, 2f, 1f),
                fps = if (s.pet.fps in listOf(15, 20, 30, 45, 60)) s.pet.fps else 30,
                hungerHours = s.pet.hungerHours.finiteIn(0.5f, 72f, 6f),
                legPairs = s.pet.legPairs.coerceIn(2, 5),
                soundVolume = s.pet.soundVolume.finiteIn(0f, 1f, 0.7f),
            ),
        )
        if (!settingsReachable(s)) s = s.copy(longPressAction = GestureAction.SETTINGS)

        fun fix(w: WidgetItem): WidgetItem {
            val ww = w.w.coerceIn(1, WIDGET_COLUMNS)
            return w.copy(
                w = ww,
                h = w.h.coerceIn(1, 24),
                x = w.x.coerceIn(0, WIDGET_COLUMNS - ww),
                y = w.y.coerceAtLeast(0),
            )
        }
        val widgets = d.widgets.distinctBy { it.id }.map(::fix)
        return d.copy(
            settings = s,
            sphereApps = d.sphereApps.distinct(),
            dockApps = d.dockApps.distinct(),
            widgets = widgets,
            topWidget = d.topWidget?.let(::fix),
            gestureWidgets = d.gestureWidgets
                .filterKeys { k -> GestureSlot.entries.any { it.name == k } }
                .mapValues { fix(it.value) },
            pets = d.pets.filter { it.key.isNotBlank() }.distinctBy { it.key }.take(MAX_PETS).map {
                it.copy(fullness = it.fullness.finiteIn(0f, 1f, 0.8f), affection = it.affection.finiteIn(0f, 1f, 0.2f))
            },
            furniture = d.furniture.distinctBy { it.id }.map {
                it.copy(
                    x = it.x.finiteIn(0f, 1f, 0.5f), y = it.y.finiteIn(0f, 1f, 0.5f),
                    scale = it.scale.finiteIn(0.4f, 3f, 1f),
                    wDp = if (it.wDp > 0f) it.wDp.finiteIn(48f, 2000f, 0f) else 0f,
                    hDp = if (it.hDp > 0f) it.hDp.finiteIn(48f, 2000f, 0f) else 0f,
                    widget = it.widget?.let(::fix),
                    // ウィジェットの家具なのにウィジェットが無い物は捨てる（下で）
                )
            }.filter { it.kind != FurnitureKind.WIDGET || it.widget != null }
                .groupBy { it.room }.values.flatMap { it.take(MAX_FURNITURE) },
        )
    }

    /** 四隅ボタンかジェスチャーのどれかで設定を開けるか */
    fun settingsReachable(s: LauncherSettings): Boolean {
        val corners = listOf(s.cornerTopLeft, s.cornerTopRight, s.cornerBottomLeft, s.cornerBottomRight)
        val gestures = listOf(
            s.longPressAction, s.doubleTapAction, s.swipeDownAction, s.swipeUpAction,
            s.swipeLeftAction, s.swipeRightAction, s.twoFingerDownAction, s.twoFingerUpAction,
            s.homeButtonAction, s.clockTapAction,
        )
        return CornerAction.SETTINGS in corners || GestureAction.SETTINGS in gestures
    }

    private fun Float.finiteIn(min: Float, max: Float, fallback: Float): Float =
        if (isNaN() || isInfinite()) fallback else coerceIn(min, max)
}

/** ジェスチャー枠に割り当てられている動作 */
fun LauncherSettings.actionFor(slot: GestureSlot): GestureAction = when (slot) {
    GestureSlot.DOUBLE_TAP -> doubleTapAction
    GestureSlot.LONG_PRESS -> longPressAction
    GestureSlot.SWIPE_DOWN -> swipeDownAction
    GestureSlot.SWIPE_UP -> swipeUpAction
    GestureSlot.SWIPE_LEFT -> swipeLeftAction
    GestureSlot.SWIPE_RIGHT -> swipeRightAction
    GestureSlot.TWO_FINGER_DOWN -> twoFingerDownAction
    GestureSlot.TWO_FINGER_UP -> twoFingerUpAction
    GestureSlot.HOME_BUTTON -> homeButtonAction
    GestureSlot.CLOCK_TAP -> clockTapAction
}

fun LauncherSettings.withAction(slot: GestureSlot, a: GestureAction): LauncherSettings = when (slot) {
    GestureSlot.DOUBLE_TAP -> copy(doubleTapAction = a)
    GestureSlot.LONG_PRESS -> copy(longPressAction = a)
    GestureSlot.SWIPE_DOWN -> copy(swipeDownAction = a)
    GestureSlot.SWIPE_UP -> copy(swipeUpAction = a)
    GestureSlot.SWIPE_LEFT -> copy(swipeLeftAction = a)
    GestureSlot.SWIPE_RIGHT -> copy(swipeRightAction = a)
    GestureSlot.TWO_FINGER_DOWN -> copy(twoFingerDownAction = a)
    GestureSlot.TWO_FINGER_UP -> copy(twoFingerUpAction = a)
    GestureSlot.HOME_BUTTON -> copy(homeButtonAction = a)
    GestureSlot.CLOCK_TAP -> copy(clockTapAction = a)
}

/** 空いている場所を上から探す（ウィジェットの自動配置） */
fun findFreeSpot(widgets: List<WidgetItem>, w: Int, h: Int, columns: Int = WIDGET_COLUMNS): Pair<Int, Int> {
    val ww = w.coerceIn(1, columns)
    var y = 0
    while (true) {
        for (x in 0..(columns - ww)) {
            val overlaps = widgets.any { o -> x < o.x + o.w && o.x < x + ww && y < o.y + o.h && o.y < y + h }
            if (!overlaps) return x to y
        }
        y++
    }
}

/** 保存ファイルとバックアップの JSON 形式 */
object BackupCodec {
    val json = Json {
        ignoreUnknownKeys = true   // 新しいバージョンのバックアップの知らない項目は無視
        encodeDefaults = true
        coerceInputValues = true   // 知らない enum 値や null はデフォルト値に
        isLenient = true
        allowSpecialFloatingPointValues = true
        prettyPrint = false
    }

    fun encode(d: LauncherData): String = json.encodeToString(LauncherData.serializer(), d)

    /**
     * JSON を読み込んで最新形式に移行する。
     * 一部の項目が壊れていても、その項目だけを捨てて残りは復元する。
     */
    fun decode(text: String): LauncherData {
        val root = json.parseToJsonElement(text).jsonObject
        val raw = runCatching { json.decodeFromJsonElement(LauncherData.serializer(), root) }
            .getOrElse { json.decodeFromJsonElement(LauncherData.serializer(), repair(root)) }
        return Migration.migrate(raw)
    }

    /** 読めない項目・要素を取り除く */
    private fun repair(root: JsonObject): JsonObject {
        val out = LinkedHashMap<String, JsonElement>()
        for ((key, value) in root) {
            when {
                key == "settings" && value is JsonObject -> {
                    // 設定は 1 項目ずつ確かめる。入れ子の設定（壁紙・飼育モードなど）は中の項目ごとに
                    val ser = LauncherSettings.serializer()
                    out[key] = JsonObject(value.mapNotNull { (k, v) ->
                        when {
                            readable(ser, JsonObject(mapOf(k to v))) -> k to v
                            v is JsonObject -> {
                                val kept = JsonObject(v.filter { (ik, iv) -> readable(ser, JsonObject(mapOf(k to JsonObject(mapOf(ik to iv))))) })
                                if (readable(ser, JsonObject(mapOf(k to kept)))) k to kept else null
                            }
                            else -> null
                        }
                    }.toMap())
                }
                value is JsonArray && !readable(LauncherData.serializer(), JsonObject(mapOf(key to value))) -> {
                    // リスト（フォルダやウィジェット）は壊れた要素だけ捨てる
                    out[key] = JsonArray(value.filter { readable(LauncherData.serializer(), JsonObject(mapOf(key to JsonArray(listOf(it))))) })
                }
                readable(LauncherData.serializer(), JsonObject(mapOf(key to value))) -> out[key] = value
            }
        }
        return JsonObject(out)
    }

    private fun <T> readable(serializer: kotlinx.serialization.KSerializer<T>, element: JsonElement): Boolean =
        runCatching { json.decodeFromJsonElement(serializer, element) }.isSuccess
}

/**
 * 実際の描画に使う設定。軽量モードの時は重い演出をまとめて OFF にする
 * （保存されている設定そのものは変えないので、軽量モードを切ると元に戻る）。
 */
fun LauncherSettings.effective(): LauncherSettings = if (!liteMode) this else copy(
    dofBlur = 0f,
    panelBlur = false,
    wallpaperBlur = 0f,
    trailLength = 0f,
    tapParticles = false,
    rainbowGlow = false,
    glowPulse = false,
    iconFloat = 0f,
    filmGrain = 0f,
    vhsTracking = false,
    glitchOverlay = false,
    cornerFloat = false,
    fpsLimit = if (fpsLimit == 0 || fpsLimit > 30) 30 else fpsLimit,
)

/** ウィジェット画面・時計の位置・ジェスチャー用のすべてのウィジェット */
fun LauncherData.allWidgets(): List<WidgetItem> = widgets + listOfNotNull(topWidget) + gestureWidgets.values + furniture.mapNotNull { it.widget }

/** すべてのウィジェットに同じ変換をかける */
fun LauncherData.mapAllWidgets(f: (WidgetItem) -> WidgetItem): LauncherData = copy(
    widgets = widgets.map(f),
    topWidget = topWidget?.let(f),
    gestureWidgets = gestureWidgets.mapValues { f(it.value) },
    furniture = furniture.map { item -> item.widget?.let { item.copy(widget = f(it)) } ?: item },
)

/** 時計の位置に時計を出すか */
val LauncherSettings.showsClock: Boolean get() = topSlot == TopSlot.CLOCK || topSlot == TopSlot.CLOCK_AND_WIDGET

/** 時計の位置にウィジェットを出すか */
val LauncherSettings.showsTopWidget: Boolean get() = topSlot == TopSlot.WIDGET || topSlot == TopSlot.CLOCK_AND_WIDGET

/** ウィジェットを置く先 */
sealed interface WidgetTarget {
    /** ウィジェット画面 */
    data object Board : WidgetTarget
    /** 時計の位置 */
    data object Top : WidgetTarget
    /** ジェスチャーで呼び出す */
    data class Gesture(val slot: String) : WidgetTarget
    /** 既存のウィジェットを作り直す（復元後の再設定） */
    data class Replace(val itemId: String) : WidgetTarget
    /** 飼育モードの家具として置く（位置は部屋に対する割合） */
    data class Furniture(val room: PetTheme, val x: Float, val y: Float) : WidgetTarget
}
