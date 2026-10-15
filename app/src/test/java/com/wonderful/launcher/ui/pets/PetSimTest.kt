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
            listOf(FurnitureKind.BED, FurnitureKind.BALL, FurnitureKind.TV, FurnitureKind.BOWL, FurnitureKind.SOFA, FurnitureKind.CASTLE, FurnitureKind.CHEST, FurnitureKind.TREE, FurnitureKind.TENT, FurnitureKind.STUMP, FurnitureKind.ROCK, FurnitureKind.PLANT, FurnitureKind.LAMP, FurnitureKind.MUSHROOM, FurnitureKind.TUMOR, FurnitureKind.FOOD_LUMP, FurnitureKind.BATTERY, FurnitureKind.COIN, FurnitureKind.CAPSULE)
                .mapIndexed { i, k -> Triple("f$i", k, (0.04f + i * 0.045f) to (0.3f + (i % 3) * 0.2f)) },
        )
    }

    private fun check(s: PetSim) {
        s.pets.forEach { p ->
            assertTrue("NaN position ${p.key}", p.x.isFinite() && p.y.isFinite() && p.heading.isFinite())
            assertTrue("out of bounds ${p.x},${p.y}", p.x in -1f..2001f && p.y in -1f..1001f)
            assertTrue(p.fullness in 0f..1f && p.affection in 0f..1f)
            if (!s.aquarium && !s.legless) p.legs.forEach { l ->
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

    @Test
    fun feedingDoesNotJamEveryone() {
        for (aq in listOf(false, true)) {
            val s = sim(aq)
            s.pets.forEach { it.fullness = 0.1f }
            // 同じ場所に何度もエサを置く（みんなが群がる状況）
            repeat(8) { s.dropFood(1000f, 500f) }
            var t = 0
            var maxPerFood = 0
            while (s.food.isNotEmpty() && t < 30 * 120) {
                s.step(1f / 30f); s.sounds.clear(); t++
                s.food.forEach { f -> maxPerFood = maxOf(maxPerFood, s.pets.count { it.targetFood == f.id && it.mood == Mood.EAT }) }
            }
            assertTrue("food left (aquarium=$aq): ${s.food.size}", s.food.isEmpty())
            assertTrue("too many eaters: $maxPerFood", maxPerFood <= PetSim.MAX_EATERS)
            // 食べ終わったら散らばって動き出す
            repeat(30 * 10) { s.step(1f / 30f); s.sounds.clear() }
            // エサはもう無いので、エサに向かったまま止まっている子はいない（エサ皿で食べるのは OK）
            assertTrue(s.pets.none { it.mood == Mood.SEEK_FOOD || (it.mood == Mood.EAT && it.targetFurniture == null) })
            val moved = s.pets.map { it.x to it.y }
            repeat(30 * 20) { s.step(1f / 30f); s.sounds.clear() }
            assertTrue("pets stay frozen", s.pets.zip(moved).count { (p, m) -> dist(p.x, p.y, m.first, m.second) > s.radius } >= 3)
        }
    }

    @Test
    fun petsDoNotWalkIntoSolidFurniture() {
        val s = sim(false)
        s.syncFurnitureSpecs(listOf(FurnitureSpec("w", FurnitureKind.WIDGET, 0.5f, 0.5f, solidW = 600f, solidH = 300f)))
        repeat(30 * 120) {
            s.step(1f / 30f); s.sounds.clear()
            s.pets.filter { it.mood != Mood.CARRIED }.forEach { p ->
                assertTrue("inside widget: ${p.x},${p.y}", !(kotlin.math.abs(p.x - 1000f) < 300f - 1f && kotlin.math.abs(p.y - 500f) < 150f - 1f))
            }
        }
    }

    @Test
    fun bodyModeBacteriaSwimAndGetEaten() {
        for (legless in listOf(false, true)) {
            val s = sim(false)
            s.setBodyMode(true)
            s.legless = legless
            s.autoFood = true
            s.pets.forEach { it.fullness = 0.2f }
            repeat(6) { i -> s.dropFood(300f + i * 250f, 500f) }
            val start = s.food.map { it.x to it.y }
            repeat(30) { s.step(1f / 30f) }
            // 泳いで動く
            assertTrue(s.food.zip(start).any { (f, p0) -> dist(f.x, f.y, p0.first, p0.second) > 5f })
            var t = 0
            var bursts = 0
            while (t < 30 * 150) {
                s.step(1f / 30f); s.sounds.clear(); t++
                bursts += s.particles.count { (it.kind == FxKind.PHAGELET || it.kind == FxKind.BUBBLE) && it.age == 0f }
                s.food.forEach { f -> assertTrue(f.x.isFinite() && f.y.isFinite() && f.x in -1f..2001f && f.y in -1f..1001f) }
                check(s)
            }
            // 自然にわいた分も含めて、どんどん食べられる
            assertTrue("bacteria left: ${s.food.size}", s.food.size <= 3)
            assertTrue(s.pets.any { it.fullness > 0.5f })
            if (legless) assertTrue(s.pets.all { p -> p.legs.all { !it.stepping } })
        }
    }

    @Test
    fun petsDoNotAllCrowdOneFurniture() {
        val s = PetSim(11).apply {
            radius = 40f; setBounds(0f, 0f, 2000f, 1000f)
            syncPets((0 until 10).map { Triple("p$it", 1f, 0.5f) })
            syncFurniture(listOf(Triple("tv", FurnitureKind.TV, 0.5f to 0.2f), Triple("ph", FurnitureKind.PHOTO, 0.2f to 0.5f), Triple("so", FurnitureKind.SOFA, 0.8f to 0.7f)))
        }
        var worst = 0
        var crowdedFrames = 0
        repeat(30 * 300) { i ->
            s.step(1f / 30f); s.sounds.clear()
            s.furniture.forEach { f ->
                val n = s.pets.count { it.targetFurniture == f.id }
                worst = maxOf(worst, n - s.capacity(f.kind))
                // 家具のまわり 3 匹分に、定員より大勢が固まっていないか
                if (s.pets.count { dist(it.x, it.y, f.x, f.y) < s.radius * 3.5f } > s.capacity(f.kind) + 3) crowdedFrames++
            }
        }
        assertTrue("over capacity by $worst", worst <= 0)
        assertTrue("crowded frames: $crowdedFrames", crowdedFrames < 30 * 20)
    }

    @Test
    fun petsPlayWithFurnitureAndGutMoves() {
        val s = sim(false)
        s.setBodyMode(true)
        val used = HashSet<FurnitureKind>()
        val moods = HashSet<Mood>()
        repeat(30 * 400) {
            s.step(1f / 30f); s.sounds.clear()
            s.pets.forEach { p -> moods += p.mood; if (p.mood == Mood.USE) s.furniture.firstOrNull { it.id == p.targetFurniture }?.let { used += it.kind } }
            s.furniture.forEach { f -> assertTrue(f.state in 0f..1f && f.active >= 0f && f.x.isFinite()) }
        }
        assertTrue("USE never happened", Mood.USE in moods)
        assertTrue("few furniture interactions: $used", used.size >= 4)
        // ぜん動の波は 0〜1 で、場所と時間で変わる
        val w = (0..20).map { s.gutWave(it * 100f) }
        assertTrue(w.all { it in 0f..1f } && w.max() - w.min() > 0.5f)
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
