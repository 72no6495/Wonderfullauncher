package com.wonderful.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 古いバージョンのバックアップを読み込めることを保証するテスト。
 *
 * backup_v1.json は最初のリリース（v1.0.0）のコードで実際に書き出したもの。
 * 保存形式を変えたら、このテストが通ることを必ず確認する。
 */
class BackupCompatTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource(name)) { "missing $name" }.readText()

    @Test
    fun v1BackupKeepsUserData() {
        val d = BackupCodec.decode(fixture("backup_v1.json"))

        // 利用者のデータはそのまま残る
        assertEquals(listOf("com.a/.Main", "com.b/.Main"), d.sphereApps)
        assertTrue(d.sphereInitialized)
        assertEquals(1, d.folders.size)
        assertEquals("GAMES", d.folders[0].name)
        assertEquals(2, d.folders[0].colorIndex)
        assertEquals(listOf("com.c/.Main"), d.folders[0].apps)
        assertEquals(listOf("com.d/.Main"), d.dockApps)
        assertEquals(setOf("com.e/.Main"), d.hiddenApps)
        assertEquals(7, d.launchCounts["com.a/.Main"])
        assertEquals(1700000000000, d.recent.single().time)

        // 設定もそのまま
        val s = d.settings
        assertEquals(SphereLayout.GALAXY, s.sphereLayout)
        assertEquals(1.2f, s.sphereSize, 0.0001f)
        assertEquals(PalettePreset.OUTRUN, s.palette)
        assertEquals(CornerAction.SEARCH, s.cornerTopLeft)
        assertEquals(GestureAction.SHUFFLE_SPHERE, s.doubleTapAction)
        assertEquals(WallpaperType.IMAGE, s.wallpaperType)
        assertEquals(0xFF123456, s.solidColor)
    }

    @Test
    fun v1BackupIsMigrated() {
        val d = BackupCodec.decode(fixture("backup_v1.json"))
        assertEquals(Migration.CURRENT_VERSION, d.dataVersion)
        // 右下の「設定」はウィジェット画面へ
        assertEquals(CornerAction.WIDGETS, d.settings.cornerBottomRight)
        // v1 で「時計タップでアラーム」を OFF にしていたら何もしない
        assertEquals(GestureAction.NONE, d.settings.clockTapAction)
        // 新しい項目はデフォルト値
        assertTrue(d.widgets.isEmpty())
        assertEquals(GestureAction.NOTIFICATIONS, d.settings.swipeDownAction)
        // 設定画面へは必ずたどり着ける（このバックアップは長押し=最近 だったので長押しに設定が入る）
        assertTrue(Migration.settingsReachable(d.settings))
        assertEquals(GestureAction.SETTINGS, d.settings.longPressAction)
    }

    @Test
    fun v1TapBounceDefaultKeepsAnEffect() {
        // v1 では「タップでバウンド」が ON（初期値）だったので、タップの動きは残る
        val d = BackupCodec.decode(fixture("backup_v1.json"))
        assertEquals(TapEffect.JELLY, d.settings.tapEffect)
    }

    @Test
    fun v2BackupKeepsWidgetsAndGestures() {
        // backup_v2.json は v2（ウィジェット・ジェスチャー追加版）のコードで書き出したもの
        val d = BackupCodec.decode(fixture("backup_v2.json"))
        assertEquals(Migration.CURRENT_VERSION, d.dataVersion)
        assertEquals(PalettePreset.SEAPUNK, d.settings.palette)
        assertEquals(GestureAction.LAUNCH_APP, d.settings.swipeLeftAction)
        assertEquals("com.cam/.Main", d.settings.gestureApps["SWIPE_LEFT"])
        assertEquals(GestureAction.LOCK_SCREEN, d.settings.twoFingerUpAction)
        assertEquals(GestureAction.CAMERA, d.settings.clockTapAction)
        assertFalse(d.settings.showBadges)
        assertEquals(2, d.widgets.size)
        assertEquals("買い物リスト", d.widgets.first { it.id == "w1" }.text)
        val sys = d.widgets.first { it.id == "w2" }
        assertEquals(WidgetKind.SYSTEM, sys.kind)
        assertEquals("com.w/.Provider", sys.provider)
        assertEquals(4, sys.x)
        // v2 で「タップでバウンド」を OFF にしていたら、タップエフェクトもなし
        assertEquals(TapEffect.NONE, d.settings.tapEffect)
        // v3 で増えたエフェクト設定は初期値
        assertEquals(LauncherSettings().rubber, d.settings.rubber, 0.0001f)
    }

    @Test
    fun currentFormatRoundTrips() {
        val original = BackupCodec.decode(fixture("backup_v1.json")).copy(
            widgets = listOf(WidgetItem("w1", WidgetKind.NOTE, x = 2, y = 3, w = 4, h = 2, text = "メモ")),
        )
        val again = BackupCodec.decode(BackupCodec.encode(original))
        assertEquals(original, again)
    }

    @Test
    fun unknownValuesFromNewerVersionsAreTolerated() {
        // 未来のバージョンで増えた項目や enum 値があっても読み込める
        val text = """
            {"settings":{"sphereLayout":"HYPERCUBE","cornerTopLeft":"TELEPORT","sphereSize":"NaN",
             "futureOption":true},"sphereApps":["x/.A","x/.A"],"brandNewField":[1,2,3],"dataVersion":99}
        """.trimIndent()
        val d = BackupCodec.decode(text)
        assertEquals(SphereLayout.SPHERE, d.settings.sphereLayout)
        assertEquals(CornerAction.RECENT, d.settings.cornerTopLeft)
        assertEquals(listOf("x/.A"), d.sphereApps)
        assertFalse(d.settings.sphereSize.isNaN())
    }

    @Test
    fun damagedBackupKeepsReadableParts() {
        // 1 つのフォルダや設定値が壊れていても、他は復元される
        val text = """
            {"settings":{"palette":"MIAMI","iconSizeDp":{"oops":1}},
             "folders":[{"id":"ok","name":"GOOD","apps":["a/.A"]},{"name":"no id"}],
             "dockApps":"not a list","sphereApps":["a/.A"]}
        """.trimIndent()
        val d = BackupCodec.decode(text)
        assertEquals(PalettePreset.MIAMI, d.settings.palette)
        assertEquals(54f, d.settings.iconSizeDp, 0.001f)
        assertEquals(listOf("GOOD"), d.folders.map { it.name })
        assertTrue(d.dockApps.isEmpty())
        assertEquals(listOf("a/.A"), d.sphereApps)
    }

    @Test
    fun emptyBackupGivesDefaults() {
        val d = BackupCodec.decode("{}")
        assertEquals(LauncherSettings().copy(clockTapAction = GestureAction.ALARM), d.settings)
        assertEquals(CornerAction.WIDGETS, d.settings.cornerBottomRight)
    }

    @Test
    fun freeSpotAvoidsOverlap() {
        val ws = listOf(WidgetItem("a", x = 0, y = 0, w = 8, h = 2), WidgetItem("b", x = 0, y = 2, w = 4, h = 2))
        assertEquals(4 to 2, findFreeSpot(ws, 4, 2))
        assertEquals(0 to 4, findFreeSpot(ws, 6, 1))
    }
}
