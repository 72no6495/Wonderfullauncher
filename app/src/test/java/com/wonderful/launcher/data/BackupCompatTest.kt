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
    fun v3BackupMovesWallpaperSettings() {
        // backup_v3.json は v3（動く背景・エフェクト追加版）のコードで書き出したもの
        val d = BackupCodec.decode(fixture("backup_v3.json"))
        assertEquals(Migration.CURRENT_VERSION, d.dataVersion)
        val s = d.settings
        assertEquals(WallpaperType.MAZE, s.wallpaperType)
        // v3 の迷路・グリッチ設定は、壁紙ごとの詳細設定へ移る
        assertEquals(40f, s.maze.cellDp, 0.001f)
        assertEquals(0.9f, s.glitch.intensity, 0.001f)
        // エフェクト設定はそのまま
        assertEquals(0.95f, s.rubber, 0.001f)
        assertEquals(TapEffect.SPIN, s.tapEffect)
        assertEquals(0.7f, s.trailLength, 0.001f)
        assertTrue(s.rainbowGlow)
        assertTrue(s.vhsTracking)
        assertEquals(PalettePreset.SYNTHWAVE, s.palette)
        // 新しい詳細設定は初期値
        assertEquals(LifeConfig(), s.life)
        assertEquals(LauncherSettings().rubberBounce, s.rubberBounce, 0.0001f)
        // データ
        assertEquals("TOOLS", d.folders.single().name)
        assertEquals(WidgetKind.CLOCK, d.widgets.single().kind)
        assertEquals(listOf("com.a/.Main", "com.b/.Main"), d.sphereApps)
    }

    @Test
    fun brokenNestedConfigFallsBackToDefaults() {
        // 壁紙の詳細設定の 1 つが壊れていても、他の設定は残る
        val text = """{"settings":{"palette":"MIAMI","life":{"rule":"CONWAY","cellDp":"big"},
            "maze":{"cellDp":30.0,"solver":"DFS"}},"dataVersion":4}"""
        val d = BackupCodec.decode(text)
        assertEquals(PalettePreset.MIAMI, d.settings.palette)
        assertEquals(LifeConfig(), d.settings.life)
        assertEquals(MazeSolver.DFS, d.settings.maze.solver)
        assertEquals(30f, d.settings.maze.cellDp, 0.001f)
    }

    @Test
    fun extremeValuesAreClamped() {
        val text = """{"settings":{"life":{"cellDp":0.5,"stepMs":1.0},"starfield":{"count":999999}},"dataVersion":4}"""
        val s = BackupCodec.decode(text).settings
        assertTrue(s.life.cellDp >= 5f)
        assertTrue(s.life.stepMs >= 16f)
        assertTrue(s.starfield.count <= 1500)
    }

    @Test
    fun v4BackupKeepsWallpaperDetails() {
        // backup_v4.json は v4（壁紙ごとの詳細設定追加版）のコードで書き出したもの
        val d = BackupCodec.decode(fixture("backup_v4.json"))
        val s = d.settings
        assertEquals(WallpaperType.LIFE, s.wallpaperType)
        assertEquals(LifeRule.HIGHLIFE, s.life.rule)
        assertEquals(CellShape.ROUND, s.life.shape)
        assertEquals(LifeColor.RAINBOW, s.life.color)
        assertEquals(9f, s.life.cellDp, 0.001f)
        assertEquals(MazeSolver.ASTAR, s.maze.solver)
        assertEquals(33f, s.maze.cellDp, 0.001f)
        assertEquals(0.8f, s.rubber, 0.001f)
        assertEquals(TrailStyle.LINE, s.trailStyle)
        assertEquals(WidgetKind.BATTERY, d.widgets.single().kind)
        // その後に増えた項目は初期値（人口グラフは時計と重ならない左下へ）
        assertEquals(ScreenCorner.BOTTOM_LEFT, s.life.hudCorner)
        assertFalse(s.liteMode)
        // 「制限なし」だったフレームレートは発熱対策で 60fps に（v6）
        assertEquals(60, s.fpsLimit)
        assertTrue(s.chargeAnim)
        assertEquals(LauncherSettings().cornerFloatAmount, s.cornerFloatAmount, 0.0001f)
    }

    @Test
    fun clockSwitchMigratesToTopSlot() {
        // v4 までの「時計を表示」は、時計の位置に表示するものの選択へ
        assertEquals(TopSlot.CLOCK, BackupCodec.decode(fixture("backup_v4.json")).settings.topSlot)
        val off = BackupCodec.decode("""{"settings":{"showClock":false},"dataVersion":4}""")
        assertEquals(TopSlot.NONE, off.settings.topSlot)
        assertEquals(TopSlot.CLOCK, BackupCodec.decode("{}").settings.topSlot)
    }

    @Test
    fun slotWidgetsRoundTripAndAreSanitized() {
        val d = LauncherData(
            topWidget = WidgetItem("t", WidgetKind.SYSMON, w = 8, h = 4),
            gestureWidgets = mapOf(
                "SWIPE_LEFT" to WidgetItem("g", WidgetKind.MEMORY, w = 99, h = 3),
                "NO_SUCH_GESTURE" to WidgetItem("x", WidgetKind.CPU),
            ),
            settings = LauncherSettings(topSlot = TopSlot.CLOCK_AND_WIDGET),
            dataVersion = Migration.CURRENT_VERSION,
        )
        val back = BackupCodec.decode(BackupCodec.encode(d))
        assertEquals(TopSlot.CLOCK_AND_WIDGET, back.settings.topSlot)
        assertEquals(WidgetKind.SYSMON, back.topWidget?.kind)
        assertEquals(setOf("SWIPE_LEFT"), back.gestureWidgets.keys)
        assertEquals(WIDGET_COLUMNS, back.gestureWidgets["SWIPE_LEFT"]?.w)
        assertEquals(2, back.allWidgets().size)
    }

    @Test
    fun unlimitedFpsMigratesTo60ButExplicitChoiceIsKept() {
        val unlimited = BackupCodec.decode("""{"settings":{"fpsLimit":0},"dataVersion":5}""")
        assertEquals(60, unlimited.settings.fpsLimit)
        val chosen = BackupCodec.decode("""{"settings":{"fpsLimit":30},"dataVersion":5}""")
        assertEquals(30, chosen.settings.fpsLimit)
        // v6 以降で「制限なし」を選び直した場合はそのまま
        val again = BackupCodec.decode("""{"settings":{"fpsLimit":0},"dataVersion":6}""")
        assertEquals(0, again.settings.fpsLimit)
    }

    @Test
    fun liteModeOnlyAffectsRendering() {
        val s = LauncherSettings(liteMode = true, dofBlur = 0.8f, trailLength = 0.5f, fpsLimit = 0)
        val e = s.effective()
        assertEquals(0f, e.dofBlur, 0f)
        assertEquals(0f, e.trailLength, 0f)
        assertEquals(30, e.fpsLimit)
        // 保存される設定は変わらない
        assertEquals(0.8f, BackupCodec.decode(BackupCodec.encode(LauncherData(settings = s))).settings.dofBlur, 0.001f)
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
    fun oldBackupsHaveNoPetsAndDefaultPetSettings() {
        for (v in 1..4) {
            val d = BackupCodec.decode(fixture("backup_v$v.json"))
            assertTrue(d.pets.isEmpty())
            assertTrue(d.furniture.isEmpty())
            assertEquals(PetConfig(), d.settings.pet)
        }
    }

    @Test
    fun petsAndFurnitureSurviveRoundTripAndAreSanitized() {
        val pets = (0 until 14).map { PetEntry("p$it/.A", fullness = if (it == 0) Float.NaN else 2f, affection = -1f) } + PetEntry("p1/.A")
        val furniture = listOf(
            FurnitureItem("f1", FurnitureKind.BED, 1.5f, -0.2f, PetTheme.CASUAL),
            FurnitureItem("f1", FurnitureKind.BALL, 0.5f, 0.5f, PetTheme.CASUAL),
            FurnitureItem("f2", FurnitureKind.CASTLE, 0.3f, 0.9f, PetTheme.AQUARIUM),
        )
        val s = LauncherSettings(pet = PetConfig(theme = PetTheme.AQUARIUM, speed = 99f, fps = 7, legPairs = 9))
        val d = BackupCodec.decode(BackupCodec.encode(LauncherData(settings = s, pets = pets, furniture = furniture)))
        assertEquals(MAX_PETS, d.pets.size)
        assertEquals(d.pets.map { it.key }.distinct(), d.pets.map { it.key })
        assertTrue(d.pets.all { it.fullness in 0f..1f && it.affection in 0f..1f })
        assertEquals(listOf("f1", "f2"), d.furniture.map { it.id })
        assertEquals(1f, d.furniture[0].x, 0.001f)
        assertEquals(0f, d.furniture[0].y, 0.001f)
        assertEquals(PetTheme.AQUARIUM, d.settings.pet.theme)
        assertEquals(3f, d.settings.pet.speed, 0.001f)
        assertEquals(30, d.settings.pet.fps)
        assertEquals(5, d.settings.pet.legPairs)
    }

    @Test
    fun damagedPetEntryDoesNotBreakOthers() {
        val text = """
            {"pets":[{"key":"a/.A","fullness":0.5},{"fullness":"bad"},{"key":"b/.B"}],
             "furniture":[{"id":"x","kind":"NOT_A_KIND"},{"id":"y","kind":"SOFA","x":0.2,"y":0.3}],
             "settings":{"pet":{"theme":"AQUARIUM","speed":"fast"}}}
        """.trimIndent()
        val d = BackupCodec.decode(text)
        assertEquals(listOf("a/.A", "b/.B"), d.pets.map { it.key })
        assertTrue(d.furniture.any { it.id == "y" && it.kind == FurnitureKind.SOFA })
        assertEquals(PetTheme.AQUARIUM, d.settings.pet.theme)
        assertEquals(1f, d.settings.pet.speed, 0.001f)
    }

    @Test
    fun freeSpotAvoidsOverlap() {
        val ws = listOf(WidgetItem("a", x = 0, y = 0, w = 8, h = 2), WidgetItem("b", x = 0, y = 2, w = 4, h = 2))
        assertEquals(4 to 2, findFreeSpot(ws, 4, 2))
        assertEquals(0 to 4, findFreeSpot(ws, 6, 1))
    }
}
