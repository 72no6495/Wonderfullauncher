package com.wonderful.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppOrderTest {
    @Test
    fun moveShiftsOthers() {
        val l = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), AppOrder.move(l, 0, 2))
        assertEquals(listOf("d", "a", "b", "c"), AppOrder.move(l, 3, 0))
        assertEquals(l, AppOrder.move(l, 1, 1))
        assertEquals(l, AppOrder.move(l, 5, 0))
    }

    @Test
    fun sphereOrderKeepsUnseenAppsAtTheEnd() {
        // 画面に出ていない x（非表示など）は消さずに後ろへ
        val merged = AppOrder.mergeSphereOrder(listOf("c", "a", "b"), listOf("a", "x", "b", "c"))
        assertEquals(listOf("c", "a", "b", "x"), merged)
    }

    @Test
    fun dailyCountsResetOnNewDay() {
        var counts = AppOrder.bumpDaily(emptyMap(), 0L, 100L, "a")
        counts = AppOrder.bumpDaily(counts, 100L, 100L, "a")
        counts = AppOrder.bumpDaily(counts, 100L, 100L, "b")
        assertEquals(mapOf("a" to 2, "b" to 1), counts)
        assertEquals(mapOf("c" to 1), AppOrder.bumpDaily(counts, 100L, 101L, "c"))
        assertEquals(emptyMap<String, Int>(), AppOrder.todayCounts(counts, 100L, 101L))
    }

    @Test
    fun byCountTiesBreakByRecentUse() {
        val r = AppOrder.byCount(mapOf("a" to 2, "b" to 3, "c" to 2, "d" to 0), mapOf("a" to 10L, "c" to 20L))
        assertEquals(listOf("b" to 3, "c" to 2, "a" to 2), r)
    }

    @Test
    fun oldBackupsGetDefaults() {
        val d = BackupCodec.decode("""{"sphereApps":["a","b"],"settings":{"sortOrder":"NAME"}}""")
        assertEquals(SortOrder.NAME, d.settings.sortOrder)
        assertEquals(RecentTab.RECENT, d.settings.recentTab)
        assertEquals(false, d.sphereCustomized)
        assertEquals(emptyMap<String, Int>(), d.dailyLaunches)
    }

    @Test
    fun recordLaunchUpdatesHistoryAndCounts() {
        var d = LauncherData()
        d = AppOrder.recordLaunch(d, "a", 1L, 10L)
        d = AppOrder.recordLaunch(d, "b", 2L, 10L)
        d = AppOrder.recordLaunch(d, "a", 3L, 10L)
        assertEquals(listOf("a", "b"), d.recent.map { it.key })
        assertEquals(2, d.launchCounts["a"])
        assertEquals(mapOf("a" to 2, "b" to 1), d.dailyLaunches)
    }

    @Test
    fun cylinderDefaultsAndSanitize() {
        val d = BackupCodec.decode("""{"settings":{"cylinder":{"enabled":true,"size":5.0,"triggerWidthDp":-3}}}""")
        assertEquals(true, d.settings.cylinder.enabled)
        assertEquals(0.95f, d.settings.cylinder.size)
        assertEquals(8f, d.settings.cylinder.triggerWidthDp)
        // 古いバックアップには並べるものが無いので初期のショートカットが入る
        assertEquals(6, d.cylinderItems.size)
        val empty = BackupCodec.decode("""{"cylinderItems":[{},{"action":"FLASHLIGHT"}]}""")
        assertEquals(listOf(CylinderItem(action = CylinderAction.FLASHLIGHT)), empty.cylinderItems)
    }
}
