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
    const val CURRENT_VERSION = 2

    fun migrate(input: LauncherData): LauncherData {
        var d = input
        if (d.dataVersion < 2) d = v1ToV2(d)
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
        )
        if (!settingsReachable(s)) s = s.copy(longPressAction = GestureAction.SETTINGS)

        val widgets = d.widgets
            .distinctBy { it.id }
            .map { w ->
                val ww = w.w.coerceIn(1, WIDGET_COLUMNS)
                w.copy(
                    w = ww,
                    h = w.h.coerceIn(1, 24),
                    x = w.x.coerceIn(0, WIDGET_COLUMNS - ww),
                    y = w.y.coerceAtLeast(0),
                )
            }
        return d.copy(
            settings = s,
            sphereApps = d.sphereApps.distinct(),
            dockApps = d.dockApps.distinct(),
            widgets = widgets,
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
                    // 設定は 1 項目ずつ確かめる
                    out[key] = JsonObject(value.filter { (k, v) -> readable(LauncherSettings.serializer(), JsonObject(mapOf(k to v))) })
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
