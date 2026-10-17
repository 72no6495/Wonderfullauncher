package com.wonderful.launcher.ui.wallpaper

import com.wonderful.launcher.data.LifeConfig
import com.wonderful.launcher.data.LifeRule
import com.wonderful.launcher.data.LifeSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LifeSimTest {
    private fun sim(seed: LifeSeed, cols: Int = 90, rows: Int = 40, wrap: Boolean = true): Pair<LifeSim, LifeConfig> {
        val cfg = LifeConfig(seed = seed, wrap = wrap)
        val s = LifeSim(cols, rows)
        s.setRule(LifeRule.CONWAY.birth, LifeRule.CONWAY.survive)
        s.seed(cfg)
        return s to cfg
    }

    @Test
    fun rPentominoKeepsEvolving() {
        // 以前は人口が少ないので毎世代作り直され、固まって見えていた
        val (s, cfg) = sim(LifeSeed.R_PENTOMINO)
        assertEquals(5, s.population)
        var maxPop = 0
        repeat(300) { s.step(cfg); maxPop = maxOf(maxPop, s.population) }
        assertEquals("reseeded too early", 300, s.generation)
        assertTrue("did not grow: $maxPop", maxPop > 60)
    }

    @Test
    fun acornSeedGrows() {
        val (s, cfg) = sim(LifeSeed.ACORN, 160, 120, wrap = false)
        assertEquals(7, s.population)
        repeat(400) { s.step(cfg) }
        assertEquals(400, s.generation)
        assertTrue("acorn did not grow: ${s.population}", s.population > 100)
    }

    @Test
    fun stableBoardIsDetectedAndReseeded() {
        // ブロック（止まった形）だけの盤面は止まったと判断して作り直す
        val cfg = LifeConfig(seed = LifeSeed.R_PENTOMINO, wrap = false)
        val s = LifeSim(40, 40)
        s.setRule(LifeRule.CONWAY.birth, LifeRule.CONWAY.survive)
        s.seed(cfg)
        // 盤面を消してブロックを 1 つ置く
        val empty = LifeConfig(seed = LifeSeed.R_PENTOMINO, wrap = false, autoReseed = false)
        repeat(1200) { s.step(empty) }
        repeat(40) { s.step(cfg) }
        assertTrue("should reseed when stable, gen=${s.generation}", s.generation < 1240)
    }

    @Test
    fun hotspotFollowsChangeAndGoesAwayWhenStill() {
        val (s, cfg) = sim(LifeSeed.R_PENTOMINO, 240, 240, wrap = false)
        repeat(40) { s.step(cfg) }
        val h = s.hotspot(false)
        assertNotNull(h)
        // 変化は中央付近から始まる
        assertTrue(kotlin.math.abs(h!!.first - 120f) < 40f && kotlin.math.abs(h.second - 120f) < 40f)
        // 何も無い盤面には追う所がない
        val empty = LifeSim(64, 64)
        empty.setRule(LifeRule.CONWAY.birth, LifeRule.CONWAY.survive)
        val still = LifeConfig(seed = LifeSeed.GUN, autoReseed = false)
        repeat(10) { empty.step(still) }
        assertNull(empty.hotspot(true))
    }
}
