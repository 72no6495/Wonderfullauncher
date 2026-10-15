package com.wonderful.launcher.ui.pets

import com.wonderful.launcher.data.FurnitureKind
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 飼育モードの動き：長時間動かしても壊れない（NaN・はみ出し・足のちぎれ）ことを確認 */
class PetSimTest {

    private fun sim(aquarium: Boolean): PetSim = PetSim(3).apply {
        radius = 40f
        setBounds(0f, 0f, 2000f, 1000f)
        setAquarium(aquarium)
        syncPets((0 until 10).map { Triple("app$it", if (it % 2 == 0) 0.2f else 0.9f, 0.5f) })
        syncFurniture(
            listOf(FurnitureKind.BED, FurnitureKind.BALL, FurnitureKind.TV, FurnitureKind.BOWL, FurnitureKind.SOFA, FurnitureKind.CASTLE, FurnitureKind.CHEST, FurnitureKind.TREE, FurnitureKind.TENT, FurnitureKind.STUMP, FurnitureKind.ROCK)
                .mapIndexed { i, k -> Triple("f$i", k, (0.05f + i * 0.08f) to 0.5f) },
        )
    }

    private fun check(s: PetSim) {
        s.pets.forEach { p ->
            assertTrue("NaN position ${p.key}", p.x.isFinite() && p.y.isFinite() && p.heading.isFinite())
            assertTrue("out of bounds ${p.x},${p.y}", p.x in -1f..2001f && p.y in -1f..1001f)
            assertTrue(p.fullness in 0f..1f && p.affection in 0f..1f)
            if (!s.aquarium) p.legs.forEach { l ->
                assertTrue(l.footX.isFinite() && l.footY.isFinite())
                // 足先が体から離れすぎない
                val d = dist(l.footX, l.footY, p.x, p.y)
                assertTrue("leg too far: $d", d < s.radius * 6f)
                val (kx, ky) = s.knee(p, l)
                assertTrue(kx.isFinite() && ky.isFinite())
            }
        }
        assertTrue(s.particles.size <= 400)
        assertTrue(s.sounds.size <= 12)
        s.pets.forEach { p ->
            assertTrue(p.energy in 0f..1f && p.gazeX.isFinite() && p.gazeY.isFinite() && p.hop in 0f..1f)
            // 交流の相手はお互いを指している
            p.partner?.let { q -> if (p.mood in PetSim.SOCIAL && q.mood in PetSim.SOCIAL) assertTrue(q.partner === p) }
        }
    }

    @Test
    fun foodIsAlwaysFinished() {
        for (aq in listOf(false, true)) {
            val s = sim(aq)
            // お腹いっぱいでも残さない
            s.pets.forEach { it.fullness = 1f }
            repeat(10) { i -> s.dropFood(200f + i * 150f, 300f + (i % 3) * 200f) }
            var t = 0
            while (s.food.isNotEmpty() && t < 30 * 120) { s.step(1f / 30f); s.sounds.clear(); t++ }
            assertTrue("leftover food (aquarium=$aq): ${s.food.size}", s.food.isEmpty())
        }
    }

    @Test
    fun petsInteractSleepAndEmote() {
        val s = sim(false)
        s.awakeSeconds = 20f
        val seen = HashSet<Mood>()
        val emotes = HashSet<Emote>()
        val idles = HashSet<IdleAnim>()
        val sfx = HashSet<Sfx>()
        repeat(30 * 300) {
            s.step(1f / 30f)
            s.pets.forEach { p -> seen += p.mood; emotes += p.emote; idles += p.idleAnim }
            s.sounds.forEach { sfx += it.sfx }
            s.sounds.clear()
        }
        assertTrue("social: $seen", seen.any { it in setOf(Mood.CHAT, Mood.CHASE, Mood.DANCE, Mood.HUG, Mood.GREET) })
        assertTrue("sleep: $seen", Mood.SLEEP in seen)
        assertTrue("emotes: $emotes", emotes.size >= 5)
        assertTrue("idles: $idles", idles.size >= 5)
        assertTrue("sounds: $sfx", sfx.size >= 4)
        assertTrue(s.friendship.isNotEmpty())
    }

    @Test fun roomLongRun() = run(false)

    @Test fun aquariumLongRun() = run(true)

    private fun run(aquarium: Boolean) {
        val s = sim(aquarium)
        val r = Random(1)
        repeat(30000) { i ->
            s.step(if (i % 500 == 0) 0.2f else 1f / 30f)
            s.sounds.clear()
            when {
                i % 400 == 0 -> s.dropFood(r.nextFloat() * 2000f, r.nextFloat() * 1000f)
                i % 333 == 0 -> s.pets.randomOrNull(r)?.let(s::pat)
                i % 777 == 0 -> s.call(r.nextFloat() * 2000f, r.nextFloat() * 1000f)
                i % 1500 == 0 -> s.pets.firstOrNull()?.let { p ->
                    s.pickUp(p)
                    repeat(20) { k -> s.carry(p, 100f + k * 40f, 300f, 1f / 30f); s.step(1f / 30f) }
                    s.release(p)
                }
                i % 5000 == 0 -> s.setBounds(0f, 0f, 1500f + r.nextFloat() * 500f, 800f + r.nextFloat() * 200f)
            }
            if (i % 100 == 0) check(s)
        }
        check(s)
        // 何体かはちゃんと動いた・食べた
        assertTrue(s.pets.any { it.fullness > 0.5f })
    }

    @Test fun petsGetFedWhenFoodDropped() {
        val s = sim(false)
        s.pets.forEach { it.fullness = 0.1f }
        repeat(6) { s.dropFood(1000f, 500f) }
        repeat(30 * 40) { s.step(1f / 30f) }
        assertTrue("someone ate", s.pets.any { it.fullness > 0.3f })
    }

    @Test fun walkingMovesLegs() {
        val s = PetSim(5).apply { radius = 40f; setBounds(0f, 0f, 2000f, 1000f); syncPets(listOf(Triple("a", 1f, 0.5f))) }
        val p = s.pets[0]
        val before = p.legs.map { it.footX to it.footY }
        var steps = 0
        repeat(30 * 20) {
            s.step(1f / 30f)
            steps += p.legs.count { it.stepping }
        }
        val after = p.legs.map { it.footX to it.footY }
        assertTrue("legs stepped", steps > 0 && before != after)
    }
}
