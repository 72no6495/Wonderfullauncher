package com.wonderful.launcher.ui.pets

import com.wonderful.launcher.data.FurnitureKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * 飼育モードの動きの計算（描画とは別。Android に依存しないのでテストできる）
 *
 * - 部屋モード：アイコンにクモの足が生えて歩く。足は 1 本ずつ地面に着いていて、
 *   体が離れすぎると交互に踏み出す（実際のクモと同じ 2 グループの歩き方）。
 * - アクアリウム：アイコンが魚になって泳ぐ。エサは沈んでいく。
 */

enum class Mood { IDLE, WANDER, SEEK_FOOD, EAT, SLEEP, SIT, PLAY, WATCH, HAPPY, CARRIED, CURIOUS }

enum class FxKind { HEART, ZZZ, CRUMB, BUBBLE, NOTE, SPARK, HUNGRY }

class Particle(
    val kind: FxKind,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val life: Float,
    val size: Float,
) {
    var age = 0f
    val t: Float get() = (age / life).coerceIn(0f, 1f)
}

class Food(val id: Int, var x: Float, var y: Float) {
    var amount = 1f
    /** 落ちてくる高さ（部屋）/ 沈んでいる途中（水槽） */
    var drop = 1f
    var vy = 0f
    var landed = false
    var age = 0f
}

class Leg(val side: Int, val index: Int, val group: Int) {
    var footX = 0f
    var footY = 0f
    var fromX = 0f
    var fromY = 0f
    var toX = 0f
    var toY = 0f
    /** 踏み出しの進み具合。1 = 着地している */
    var t = 1f
    val stepping: Boolean get() = t < 1f
    /** 足を持ち上げている高さ（0〜1）。描画で膝を高く見せる */
    val lift: Float get() = if (t < 1f) sin(t * PI.toFloat()) else 0f
}

class Pet(val key: String, var x: Float, var y: Float, val seed: Int) {
    var vx = 0f
    var vy = 0f
    /** 向き（ラジアン） */
    var heading = 0f
    var mood = Mood.IDLE
    var moodTime = 0f
    var moodLength = 1f
    var targetX = x
    var targetY = y
    var targetFurniture: String? = null
    var targetFood: Int? = null
    var fullness = 0.8f
    var affection = 0.2f
    var legs: List<Leg> = emptyList()
    /** 撫でられた・喜んだ時の揺れ（0〜1） */
    var joy = 0f
    var blink = 0f
    var nextBlink = 2f
    /** 魚の向き（-1 = 左, 1 = 右）。なめらかに切り替える */
    var face = 1f
    var tail = 0f
    var walkPhase = 0f
    /** 名前やステータスを出す残り時間 */
    var infoTime = 0f
    var hungryFx = 3f
    /** 持ち上げられている高さ（影の大きさに使う） */
    var lift = 0f
    val speed: Float get() = hypot(vx, vy)
}

class FurnitureBody(val id: String, val kind: FurnitureKind, var x: Float, var y: Float) {
    var vx = 0f
    var vy = 0f
    var spin = 0f
    /** 誰かが使っている */
    var user: String? = null
    var fx = 0f
}

class PetSim(seed: Int = 7) {
    private val rnd = Random(seed)

    // 動ける範囲（px）
    var left = 0f; private set
    var top = 0f; private set
    var right = 1f; private set
    var bottom = 1f; private set

    var aquarium = false
        private set
    /** 体の半径（px） */
    var radius = 40f
    var speedFactor = 1f
    var legPairs = 4
        private set
    /** 満腹 → 空腹までの秒数 */
    var hungerSeconds = 6f * 3600f

    val pets = ArrayList<Pet>()
    val food = ArrayList<Food>()
    val furniture = ArrayList<FurnitureBody>()
    val particles = ArrayList<Particle>()
    private var foodIds = 0
    var time = 0f
        private set

    val floorY: Float get() = bottom - (bottom - top) * 0.06f

    fun setBounds(l: Float, t: Float, r: Float, b: Float) {
        val changed = l != left || t != top || r != right || b != bottom
        left = l; top = t; right = max(r, l + 1f); bottom = max(b, t + 1f)
        if (changed) {
            pets.forEach { clampPet(it); resetLegs(it) }
            furniture.forEach { it.x = it.x.coerceIn(left, right); it.y = it.y.coerceIn(top, bottom) }
        }
    }

    fun setAquarium(on: Boolean) {
        if (on == aquarium) return
        aquarium = on
        food.clear()
        pets.forEach { it.mood = Mood.IDLE; it.moodTime = 0f; it.moodLength = 0.5f; resetLegs(it) }
    }

    fun changeLegPairs(n: Int) {
        if (n == legPairs) return
        legPairs = n
        pets.forEach(::resetLegs)
    }

    // ── 飼っている子・家具の同期 ──

    fun syncPets(entries: List<Triple<String, Float, Float>>) {
        val keys = entries.map { it.first }.toSet()
        pets.removeAll { it.key !in keys }
        entries.forEach { (key, full, aff) ->
            val existing = pets.firstOrNull { it.key == key }
            if (existing == null) {
                val p = Pet(key, rnd.range(left + radius, right - radius), rnd.range(top + radius, bottom - radius), rnd.nextInt())
                p.heading = rnd.range(0f, 2 * PI.toFloat())
                p.fullness = full
                p.affection = aff
                p.moodLength = rnd.range(0.2f, 1.5f)
                if (aquarium) p.face = if (rnd.nextBoolean()) 1f else -1f
                resetLegs(p)
                pets += p
            }
        }
    }

    fun syncFurniture(items: List<Triple<String, FurnitureKind, Pair<Float, Float>>>) {
        val ids = items.map { it.first }.toSet()
        furniture.removeAll { it.id !in ids }
        items.forEach { (id, kind, pos) ->
            val px = left + (right - left) * pos.first
            val py = top + (bottom - top) * pos.second
            val f = furniture.firstOrNull { it.id == id }
            if (f == null) furniture += FurnitureBody(id, kind, px, py)
            else if (kind != FurnitureKind.BALL) { f.x = px; f.y = py }
        }
        // 使っていた家具が消えたら行動をやめる
        pets.forEach { p ->
            if (p.targetFurniture != null && furniture.none { it.id == p.targetFurniture }) {
                p.targetFurniture = null
                if (p.mood in listOf(Mood.SLEEP, Mood.SIT, Mood.PLAY, Mood.WATCH)) setMood(p, Mood.IDLE, 0.5f)
            }
        }
    }

    /** 家具の位置を割合で返す（保存用） */
    fun furnitureFraction(f: FurnitureBody): Pair<Float, Float> =
        ((f.x - left) / (right - left)).coerceIn(0f, 1f) to ((f.y - top) / (bottom - top)).coerceIn(0f, 1f)

    // ── 操作 ──

    fun petAt(x: Float, y: Float): Pet? =
        pets.filter { hypot(it.x - x, it.y - y) < radius * 1.25f }.minByOrNull { hypot(it.x - x, it.y - y) }

    fun furnitureAt(x: Float, y: Float): FurnitureBody? =
        furniture.filter { hypot(it.x - x, it.y - y) < furnitureRadius(it.kind) * 1.1f }.minByOrNull { hypot(it.x - x, it.y - y) }

    fun furnitureRadius(kind: FurnitureKind): Float = radius * when (kind) {
        FurnitureKind.BALL -> 0.45f
        FurnitureKind.BED, FurnitureKind.SOFA, FurnitureKind.RUG, FurnitureKind.CASTLE, FurnitureKind.TREE, FurnitureKind.TENT, FurnitureKind.DOGHOUSE -> 1.3f
        FurnitureKind.TV, FurnitureKind.TOWER, FurnitureKind.ROCK, FurnitureKind.CHEST -> 0.95f
        FurnitureKind.BOWL, FurnitureKind.SHELL -> 0.6f
        else -> 0.8f
    }

    /** 撫でる */
    fun pat(p: Pet) {
        p.joy = 1f
        p.infoTime = 3f
        p.affection = (p.affection + 0.03f).coerceAtMost(1f)
        if (p.mood != Mood.CARRIED && p.mood != Mood.EAT) setMood(p, Mood.HAPPY, 1.4f)
        repeat(3) {
            particles += Particle(FxKind.HEART, p.x + rnd.range(-radius, radius) * 0.6f, p.y - radius * 0.8f, rnd.range(-25f, 25f), rnd.range(-90f, -50f), rnd.range(1f, 1.6f), rnd.range(0.7f, 1.1f))
        }
        trimParticles()
    }

    /** 床をタップ：近くの子が見に来る（なついているほど来る） */
    fun call(x: Float, y: Float) {
        pets.forEach { p ->
            if (p.mood in listOf(Mood.EAT, Mood.CARRIED, Mood.SEEK_FOOD)) return@forEach
            val d = hypot(p.x - x, p.y - y)
            val chance = 0.25f + p.affection * 0.7f
            if (d < (right - left) * 0.6f && rnd.nextFloat() < chance) {
                setMood(p, Mood.CURIOUS, 4f)
                p.targetX = x + rnd.range(-radius, radius) * 1.5f
                p.targetY = y + rnd.range(-radius, radius) * 1.5f
                clampTarget(p)
            }
        }
        particles += Particle(FxKind.SPARK, x, y, 0f, 0f, 0.5f, 1f)
    }

    fun dropFood(x: Float, y: Float) {
        if (food.size >= 24) food.removeAt(0)
        val f = Food(foodIds++, x.coerceIn(left, right), y.coerceIn(top, if (aquarium) floorY else bottom))
        if (aquarium) { f.drop = 0f; f.vy = 20f } else f.drop = 1f
        food += f
        // おなかが空いている子から順に向かう
        pets.filter { it.fullness < 0.97f && it.mood !in listOf(Mood.CARRIED, Mood.EAT) }
            .sortedBy { it.fullness }.take(3).forEach { goToFood(it, f) }
    }

    fun pickUp(p: Pet) {
        setMood(p, Mood.CARRIED, 999f)
        p.targetFurniture?.let { id -> furniture.firstOrNull { it.id == id }?.let { if (it.user == p.key) it.user = null } }
        p.targetFurniture = null
        p.vx = 0f; p.vy = 0f
    }

    fun carry(p: Pet, x: Float, y: Float, dt: Float) {
        val nx = x.coerceIn(left, right)
        val ny = y.coerceIn(top, bottom)
        if (dt > 0f) { p.vx = (nx - p.x) / dt; p.vy = (ny - p.y) / dt }
        p.x = nx; p.y = ny
    }

    fun release(p: Pet) {
        p.vx *= 0.3f; p.vy *= 0.3f
        clampPet(p)
        resetLegs(p)
        setMood(p, Mood.HAPPY, 0.8f)
        p.joy = 0.6f
    }

    // ── 1 フレーム進める ──

    fun step(dtIn: Float) {
        val dt = dtIn.coerceIn(0f, 0.05f)
        if (dt <= 0f) return
        time += dt
        stepFood(dt)
        stepFurniture(dt)
        pets.forEach { stepPet(it, dt) }
        separate()
        stepParticles(dt)
    }

    private fun stepFood(dt: Float) {
        food.forEach { f ->
            f.age += dt
            if (aquarium) {
                if (!f.landed) {
                    f.vy = min(f.vy + 30f * dt, 55f)
                    f.y += f.vy * dt
                    f.x += sin(f.age * 2.3f + f.id) * 12f * dt
                    if (f.y >= floorY) { f.y = floorY; f.landed = true }
                }
            } else {
                f.drop = max(0f, f.drop - dt * 4f)
                if (f.drop == 0f && !f.landed) {
                    f.landed = true
                    repeat(3) { particles += Particle(FxKind.CRUMB, f.x, f.y, rnd.range(-50f, 50f), rnd.range(-60f, -20f), 0.5f, 0.6f) }
                }
            }
        }
        food.removeAll { it.amount <= 0f || it.age > 300f }
    }

    private fun stepFurniture(dt: Float) {
        furniture.forEach { f ->
            f.fx += dt
            if (f.kind == FurnitureKind.BALL) {
                f.x += f.vx * dt
                f.y += f.vy * dt
                val r = furnitureRadius(f.kind)
                if (f.x < left + r) { f.x = left + r; f.vx = abs(f.vx) * 0.8f }
                if (f.x > right - r) { f.x = right - r; f.vx = -abs(f.vx) * 0.8f }
                if (f.y < top + r) { f.y = top + r; f.vy = abs(f.vy) * 0.8f }
                if (f.y > bottom - r) { f.y = bottom - r; f.vy = -abs(f.vy) * 0.8f }
                val drag = if (aquarium) 1.6f else 1.1f
                f.vx -= f.vx * min(1f, drag * dt)
                f.vy -= f.vy * min(1f, drag * dt)
                if (aquarium) f.vy += 18f * dt // ゆっくり沈む
                f.spin += f.vx * dt / max(r, 1f)
            }
            if (f.kind == FurnitureKind.CHEST && aquarium && rnd.nextFloat() < dt * 0.8f) {
                particles += Particle(FxKind.BUBBLE, f.x + rnd.range(-8f, 8f), f.y - radius * 0.5f, rnd.range(-6f, 6f), rnd.range(-70f, -45f), rnd.range(2f, 3.5f), rnd.range(0.4f, 1f))
            }
        }
    }

    private fun stepParticles(dt: Float) {
        particles.forEach { q ->
            q.age += dt
            q.x += q.vx * dt
            q.y += q.vy * dt
            when (q.kind) {
                FxKind.CRUMB -> q.vy += 260f * dt
                FxKind.BUBBLE -> q.vx += sin(q.age * 5f + q.size * 10f) * 30f * dt
                else -> { q.vx *= 1f - min(1f, dt * 1.5f) }
            }
        }
        particles.removeAll { it.age >= it.life || (aquarium && it.kind == FxKind.BUBBLE && it.y < top) }
    }

    private fun trimParticles() {
        while (particles.size > 160) particles.removeAt(0)
    }

    private fun setMood(p: Pet, m: Mood, length: Float) {
        if (p.mood != m) {
            // 使っていた家具を空ける
            if (p.mood in listOf(Mood.SLEEP, Mood.SIT, Mood.WATCH) && m !in listOf(Mood.SLEEP, Mood.SIT, Mood.WATCH)) {
                furniture.firstOrNull { it.user == p.key }?.user = null
            }
        }
        p.mood = m
        p.moodTime = 0f
        p.moodLength = length
    }

    private fun goToFood(p: Pet, f: Food) {
        setMood(p, Mood.SEEK_FOOD, 12f)
        p.targetFood = f.id
        p.targetFurniture = null
    }

    private fun stepPet(p: Pet, dt: Float) {
        p.moodTime += dt
        p.joy = max(0f, p.joy - dt * 0.7f)
        p.infoTime = max(0f, p.infoTime - dt)
        p.fullness = max(0f, p.fullness - dt / hungerSeconds)
        p.tail += dt * (2f + p.speed / max(radius, 1f) * 3f)
        p.lift += ((if (p.mood == Mood.CARRIED) 1f else 0f) - p.lift) * min(1f, dt * 10f)
        // まばたき
        p.nextBlink -= dt
        if (p.nextBlink <= 0f) { p.blink = 1f; p.nextBlink = rnd.range(1.5f, 5f) }
        p.blink = max(0f, p.blink - dt * 7f)

        // おなかが空いたら知らせる
        if (p.fullness < 0.3f && p.mood != Mood.SLEEP) {
            p.hungryFx -= dt
            if (p.hungryFx <= 0f) {
                p.hungryFx = rnd.range(5f, 9f)
                particles += Particle(FxKind.HUNGRY, p.x, p.y - radius * 1.2f, 0f, -12f, 2.2f, 1f)
            }
        }

        if (p.mood == Mood.CARRIED) {
            dangleLegs(p, dt)
            return
        }

        think(p, dt)
        move(p, dt)
        if (!aquarium) updateLegs(p, dt)
        if (aquarium) {
            val want = if (abs(p.vx) > 4f) (if (p.vx > 0f) 1f else -1f) else p.face
            p.face += (want - p.face) * min(1f, dt * 5f)
            // 口からぷくぷく
            if (rnd.nextFloat() < dt * 0.35f) {
                particles += Particle(FxKind.BUBBLE, p.x + p.face * radius * 0.9f, p.y - radius * 0.1f, rnd.range(-5f, 5f), rnd.range(-60f, -40f), rnd.range(2f, 3f), rnd.range(0.3f, 0.6f))
                trimParticles()
            }
        }
    }

    /** 次に何をするか決める */
    private fun think(p: Pet, dt: Float) {
        when (p.mood) {
            Mood.SEEK_FOOD -> {
                val f = food.firstOrNull { it.id == p.targetFood }
                if (f == null || f.amount <= 0f) { pickNext(p); return }
                p.targetX = f.x; p.targetY = if (aquarium) f.y - radius * 0.3f else f.y
                val ready = aquarium || f.landed
                if (ready && hypot(p.x - f.x, p.y - p.targetY) < radius * 0.8f) setMood(p, Mood.EAT, 2.5f)
                else if (p.moodTime > p.moodLength) pickNext(p)
            }
            Mood.EAT -> {
                val f = food.firstOrNull { it.id == p.targetFood }
                val bowl = furniture.firstOrNull { it.id == p.targetFurniture && it.kind == FurnitureKind.BOWL }
                if (f == null && bowl == null) { pickNext(p); return }
                val gain = dt * 0.16f
                if (f != null) {
                    if (f.amount <= 0f) { pickNext(p); return }
                    f.amount -= dt * 0.45f
                } else if (bowl != null && hypot(p.x - bowl.x, p.y - bowl.y) > radius * 1.6f) { pickNext(p); return }
                p.fullness = (p.fullness + gain).coerceAtMost(1f)
                if (rnd.nextFloat() < dt * 6f) {
                    particles += Particle(if (aquarium) FxKind.BUBBLE else FxKind.CRUMB, p.x + rnd.range(-10f, 10f), p.y + radius * 0.2f, rnd.range(-40f, 40f), rnd.range(-70f, -30f), 0.6f, 0.5f)
                }
                if (p.fullness >= 1f || p.moodTime > 6f) {
                    p.affection = (p.affection + 0.01f).coerceAtMost(1f)
                    p.joy = 0.7f
                    particles += Particle(FxKind.NOTE, p.x, p.y - radius, 10f, -40f, 1.4f, 1f)
                    pickNext(p)
                }
            }
            Mood.SLEEP -> {
                if (rnd.nextFloat() < dt * 0.9f) particles += Particle(FxKind.ZZZ, p.x + radius * 0.4f, p.y - radius * 0.8f, 14f, -26f, 2.2f, rnd.range(0.7f, 1.1f))
                if (p.moodTime > p.moodLength) pickNext(p)
            }
            Mood.WATCH -> {
                if (rnd.nextFloat() < dt * 0.3f) particles += Particle(FxKind.NOTE, p.x, p.y - radius, 8f, -30f, 1.4f, 0.8f)
                if (p.moodTime > p.moodLength) pickNext(p)
            }
            Mood.PLAY -> {
                val ball = furniture.firstOrNull { it.id == p.targetFurniture }
                if (ball == null || p.moodTime > p.moodLength) { pickNext(p); return }
                p.targetX = ball.x; p.targetY = ball.y
                val d = hypot(p.x - ball.x, p.y - ball.y)
                if (d < radius + furnitureRadius(ball.kind)) {
                    // 蹴る
                    val a = atan2(ball.y - p.y, ball.x - p.x) + rnd.range(-0.4f, 0.4f)
                    val power = radius * rnd.range(5f, 9f)
                    ball.vx = cos(a) * power
                    ball.vy = sin(a) * power
                    p.joy = 0.5f
                    if (rnd.nextFloat() < 0.4f) particles += Particle(FxKind.NOTE, p.x, p.y - radius, 0f, -40f, 1f, 0.8f)
                }
            }
            Mood.SIT, Mood.HAPPY, Mood.IDLE -> if (p.moodTime > p.moodLength) pickNext(p)
            Mood.WANDER, Mood.CURIOUS -> {
                val fid = p.targetFurniture
                val f = fid?.let { id -> furniture.firstOrNull { it.id == id } }
                // ボールは転がるので追いかける
                if (f != null && f.kind == FurnitureKind.BALL) { p.targetX = f.x; p.targetY = f.y }
                val arrived = hypot(p.targetX - p.x, p.targetY - p.y) < radius * (if (f != null) 0.6f else 0.9f) ||
                    (f?.kind == FurnitureKind.BALL && hypot(f.x - p.x, f.y - p.y) < radius * 1.5f)
                when {
                    // 家具に着いたら使う
                    arrived && p.mood == Mood.WANDER && f != null -> useFurniture(p, f)
                    arrived || p.moodTime > p.moodLength ->
                        if (p.mood == Mood.CURIOUS) { setMood(p, Mood.IDLE, rnd.range(1f, 2.5f)); p.joy = 0.3f } else pickNext(p)
                }
            }
            Mood.CARRIED -> Unit
        }
    }

    private fun pickNext(p: Pet) {
        p.targetFood = null
        p.targetFurniture?.let { id -> furniture.firstOrNull { it.id == id && it.user == p.key }?.user = null }
        p.targetFurniture = null
        // おなかが空いていたらエサかエサ皿へ
        if (p.fullness < 0.6f) {
            val f = food.filter { it.amount > 0f }.minByOrNull { hypot(it.x - p.x, it.y - p.y) }
            if (f != null) { goToFood(p, f); return }
            val bowl = furniture.filter { it.kind == FurnitureKind.BOWL }.minByOrNull { hypot(it.x - p.x, it.y - p.y) }
            if (bowl != null && p.fullness < 0.45f) {
                setMood(p, Mood.WANDER, 12f)
                p.targetFurniture = bowl.id
                p.targetX = bowl.x; p.targetY = bowl.y + radius * 0.9f
                clampTarget(p)
                return
            }
        }
        val r = rnd.nextFloat()
        val usable = furniture.filter { it.user == null && it.kind.usable(aquarium) }
        when {
            r < 0.3f && usable.isNotEmpty() -> {
                val f = usable.random(rnd)
                setMood(p, Mood.WANDER, 14f)
                p.targetFurniture = f.id
                val spot = spotFor(p, f)
                p.targetX = spot.first; p.targetY = spot.second
                clampTarget(p)
            }
            r < 0.42f && pets.size > 1 -> {
                // 他の子に会いに行く
                val o = pets.filter { it !== p }.random(rnd)
                setMood(p, Mood.WANDER, 8f)
                val a = rnd.range(0f, 2 * PI.toFloat())
                p.targetX = o.x + cos(a) * radius * 2.2f
                p.targetY = o.y + sin(a) * radius * 2.2f
                clampTarget(p)
            }
            r < 0.75f -> setMood(p, Mood.IDLE, rnd.range(2f, 6f))
            else -> {
                // 近場をぶらぶら
                setMood(p, Mood.WANDER, 8f)
                val reach = (right - left) * 0.3f
                p.targetX = p.x + rnd.range(-reach, reach)
                p.targetY = p.y + rnd.range(-reach, reach) * 0.6f
                clampTarget(p)
            }
        }
    }

    private fun FurnitureKind.usable(aq: Boolean): Boolean = when (this) {
        FurnitureKind.BED, FurnitureKind.SOFA, FurnitureKind.RUG, FurnitureKind.TOWER, FurnitureKind.TV, FurnitureKind.BALL -> true
        FurnitureKind.CASTLE, FurnitureKind.CHEST, FurnitureKind.CORAL, FurnitureKind.SEAWEED, FurnitureKind.SHELL -> aq
        FurnitureKind.ROCK -> true
        FurnitureKind.PLANT, FurnitureKind.PALM, FurnitureKind.BUST, FurnitureKind.LAMP -> true
        FurnitureKind.TREE, FurnitureKind.STUMP, FurnitureKind.MUSHROOM, FurnitureKind.TENT, FurnitureKind.SUNFLOWER, FurnitureKind.DOGHOUSE -> true
        FurnitureKind.BOWL -> false
    }

    /** 家具のどこに行くか */
    private fun spotFor(p: Pet, f: FurnitureBody): Pair<Float, Float> = when (f.kind) {
        FurnitureKind.TV -> f.x to f.y + radius * 2.4f
        // 木陰・テントや小屋の入り口
        FurnitureKind.TREE -> (f.x + (if (p.x < f.x) -1 else 1) * radius * 0.8f) to f.y + radius * 1.3f
        FurnitureKind.TENT, FurnitureKind.DOGHOUSE -> f.x to f.y + radius * 0.9f
        FurnitureKind.MUSHROOM, FurnitureKind.SUNFLOWER -> (f.x + (if (p.x < f.x) -1 else 1) * radius * 1.4f) to f.y + radius * 0.3f
        FurnitureKind.BALL -> f.x to f.y
        FurnitureKind.PLANT, FurnitureKind.PALM, FurnitureKind.BUST, FurnitureKind.LAMP, FurnitureKind.CORAL, FurnitureKind.SEAWEED ->
            (f.x + (if (p.x < f.x) -1 else 1) * radius * 1.6f) to f.y + radius * 0.4f
        FurnitureKind.CASTLE, FurnitureKind.ROCK, FurnitureKind.CHEST, FurnitureKind.SHELL ->
            if (aquarium) f.x to f.y - radius * 1.3f else f.x to f.y
        else -> f.x to f.y
    }

    private fun useFurniture(p: Pet, f: FurnitureBody) {
        when (f.kind) {
            FurnitureKind.BOWL -> { setMood(p, Mood.EAT, 6f); p.targetFurniture = f.id; p.targetFood = null }
            FurnitureKind.BED, FurnitureKind.RUG -> { setMood(p, Mood.SLEEP, rnd.range(6f, 14f)); f.user = p.key; p.targetFurniture = f.id }
            FurnitureKind.SOFA, FurnitureKind.TOWER -> { setMood(p, Mood.SIT, rnd.range(4f, 9f)); f.user = p.key; p.targetFurniture = f.id }
            FurnitureKind.TV -> { setMood(p, Mood.WATCH, rnd.range(5f, 10f)); p.targetFurniture = f.id; p.heading = -PI.toFloat() / 2 }
            FurnitureKind.BALL -> { setMood(p, Mood.PLAY, rnd.range(4f, 8f)); p.targetFurniture = f.id }
            FurnitureKind.TENT, FurnitureKind.DOGHOUSE -> { setMood(p, Mood.SLEEP, rnd.range(6f, 14f)); f.user = p.key; p.targetFurniture = f.id }
            FurnitureKind.TREE, FurnitureKind.STUMP -> { setMood(p, Mood.SIT, rnd.range(4f, 10f)); p.targetFurniture = f.id }
            FurnitureKind.ROCK -> if (aquarium) { setMood(p, Mood.SLEEP, rnd.range(4f, 9f)); p.targetFurniture = f.id } else { setMood(p, Mood.SIT, rnd.range(3f, 7f)); p.targetFurniture = f.id }
            FurnitureKind.CASTLE, FurnitureKind.CHEST, FurnitureKind.SHELL ->
                if (aquarium) { setMood(p, Mood.SLEEP, rnd.range(4f, 9f)); p.targetFurniture = f.id } else setMood(p, Mood.IDLE, 2f)
            else -> { setMood(p, Mood.IDLE, rnd.range(1.5f, 3f)); p.joy = 0.2f }
        }
    }

    private fun clampTarget(p: Pet) {
        p.targetX = p.targetX.coerceIn(left + radius * 0.6f, right - radius * 0.6f)
        p.targetY = p.targetY.coerceIn(top + radius * 0.6f, (if (aquarium) floorY else bottom) - radius * 0.6f)
    }

    private fun clampPet(p: Pet) {
        p.x = p.x.coerceIn(left + radius * 0.5f, max(left + radius * 0.5f, right - radius * 0.5f))
        p.y = p.y.coerceIn(top + radius * 0.5f, max(top + radius * 0.5f, (if (aquarium) floorY else bottom) - radius * 0.5f))
    }

    private fun move(p: Pet, dt: Float) {
        val moving = p.mood in listOf(Mood.WANDER, Mood.SEEK_FOOD, Mood.PLAY, Mood.CURIOUS) ||
            (p.mood == Mood.EAT && p.targetFurniture != null && hypot(p.x - p.targetX, p.y - p.targetY) > radius * 0.6f)
        val maxSpeed = radius * (if (aquarium) 2.2f else 2.6f) * speedFactor *
            (if (p.mood == Mood.SEEK_FOOD || p.mood == Mood.PLAY) 1.5f else 1f) * (0.6f + 0.4f * p.fullness.coerceAtLeast(0.3f))
        var ax = 0f
        var ay = 0f
        if (moving) {
            val dx = p.targetX - p.x
            val dy = p.targetY - p.y
            val d = hypot(dx, dy)
            if (d > 1f) {
                // 着く直前はゆっくり
                val want = maxSpeed * min(1f, d / (radius * 1.2f))
                ax = (dx / d * want - p.vx) * 4f
                ay = (dy / d * want - p.vy) * 4f
            }
        } else {
            ax = -p.vx * 6f
            ay = -p.vy * 6f
        }
        if (aquarium) {
            // ふわふわ上下する
            ay += sin(time * 1.7f + p.seed % 10) * radius * 0.6f
        }
        p.vx += ax * dt
        p.vy += ay * dt
        val sp = p.speed
        val cap = maxSpeed * 1.2f
        if (sp > cap) { p.vx *= cap / sp; p.vy *= cap / sp }
        p.x += p.vx * dt
        p.y += p.vy * dt
        clampPet(p)

        // 向き：進んでいる方へゆっくり回る
        if (p.speed > radius * 0.15f) {
            val want = atan2(p.vy, p.vx)
            p.heading += angleDiff(p.heading, want) * min(1f, dt * 6f)
        } else if (p.mood == Mood.HAPPY) {
            p.heading += sin(time * 9f) * dt * 2f
        }
        p.walkPhase += p.speed * dt / max(radius, 1f)
    }

    /** 重なった子どうしを押し離す */
    private fun separate() {
        for (i in pets.indices) for (j in i + 1 until pets.size) {
            val a = pets[i]
            val b = pets[j]
            if (a.mood == Mood.CARRIED || b.mood == Mood.CARRIED) continue
            val dx = b.x - a.x
            val dy = b.y - a.y
            val d = hypot(dx, dy)
            val minD = radius * 1.7f
            if (d in 0.001f..minD) {
                val push = (minD - d) * 0.5f
                val nx = dx / d
                val ny = dy / d
                if (a.mood !in listOf(Mood.SLEEP, Mood.SIT)) { a.x -= nx * push; a.y -= ny * push }
                if (b.mood !in listOf(Mood.SLEEP, Mood.SIT)) { b.x += nx * push; b.y += ny * push }
                clampPet(a); clampPet(b)
            }
        }
    }

    // ── クモの足 ──

    /** 足の付け根と、休んでいる時の足先の位置 */
    fun legAngle(leg: Leg): Float {
        val n = legPairs
        val f = if (n == 1) 0.5f else leg.index / (n - 1f)
        val deg = 38f + f * 104f // 前 38° 〜 後ろ 142°
        return leg.side * deg * PI.toFloat() / 180f
    }

    fun hip(p: Pet, leg: Leg): Pair<Float, Float> {
        val a = p.heading + legAngle(leg)
        return (p.x + cos(a) * radius * 0.55f) to (p.y + sin(a) * radius * 0.55f)
    }

    private fun restFoot(p: Pet, leg: Leg, lead: Float): Pair<Float, Float> {
        val a = p.heading + legAngle(leg) * 0.92f
        val reach = radius * 1.65f
        return (p.x + cos(a) * reach + p.vx * lead) to (p.y + sin(a) * reach + p.vy * lead)
    }

    fun resetLegs(p: Pet) {
        val legs = ArrayList<Leg>()
        for (i in 0 until legPairs) for (side in intArrayOf(-1, 1)) {
            val group = (i + if (side > 0) 1 else 0) % 2
            legs += Leg(side, i, group)
        }
        p.legs = legs
        legs.forEach { l ->
            val (fx, fy) = restFoot(p, l, 0f)
            l.footX = fx; l.footY = fy; l.fromX = fx; l.fromY = fy; l.toX = fx; l.toY = fy; l.t = 1f
        }
    }

    private fun updateLegs(p: Pet, dt: Float) {
        if (p.legs.size != legPairs * 2) resetLegs(p)
        val stepDur = (0.16f / speedFactor.coerceAtLeast(0.3f)).coerceIn(0.07f, 0.3f)
        val threshold = radius * 0.55f
        // 進行中の踏み出し（体が動き続けているので着地点も追いかける）
        p.legs.forEach { l ->
            if (l.stepping) {
                val (rx, ry) = restFoot(p, l, stepDur * 1.6f * (1f - l.t))
                l.toX = rx; l.toY = ry
                l.t = min(1f, l.t + dt / stepDur)
                val e = l.t * l.t * (3f - 2f * l.t)
                l.footX = l.fromX + (l.toX - l.fromX) * e
                l.footY = l.fromY + (l.toY - l.fromY) * e
            }
        }
        // 反対のグループが踏み出し中なら待つ（いつも半分の足は地面に着いている）
        val busy = IntArray(2)
        p.legs.forEach { if (it.stepping) busy[it.group]++ }
        for (g in 0..1) {
            if (busy[1 - g] > 0) continue
            val group = p.legs.filter { it.group == g && !it.stepping }
            val far = group.filter { l ->
                val (rx, ry) = restFoot(p, l, 0f)
                hypot(l.footX - rx, l.footY - ry) > threshold
            }
            if (far.isNotEmpty()) {
                group.forEach { l ->
                    val (rx, ry) = restFoot(p, l, stepDur * 1.6f)
                    if (hypot(l.footX - rx, l.footY - ry) > threshold * 0.35f) {
                        l.fromX = l.footX; l.fromY = l.footY
                        l.toX = rx; l.toY = ry
                        l.t = 0f
                    }
                }
                break
            }
        }
        // 押されたり急に動いたりして足が届かなくなったら、グループに関係なくすぐ踏み出す
        val maxReach = radius * 2.3f
        p.legs.forEach { l ->
            val (hx, hy) = hip(p, l)
            val d = hypot(l.footX - hx, l.footY - hy)
            if (d > maxReach * 1.4f) {
                val (rx, ry) = restFoot(p, l, 0f)
                l.footX = rx; l.footY = ry; l.t = 1f
            } else if (d > maxReach && !l.stepping) {
                l.fromX = l.footX; l.fromY = l.footY
                l.t = 0f
            }
        }
    }

    /** 持ち上げられている時は足をバタバタ */
    private fun dangleLegs(p: Pet, dt: Float) {
        if (p.legs.size != legPairs * 2) resetLegs(p)
        p.legs.forEachIndexed { i, l ->
            val a = p.heading + legAngle(l) * 0.8f + sin(time * 14f + i * 1.3f) * 0.35f
            val reach = radius * (1.2f + 0.15f * sin(time * 11f + i))
            val tx = p.x + cos(a) * reach
            val ty = p.y + sin(a) * reach + radius * 0.3f
            l.footX += (tx - l.footX) * min(1f, dt * 18f)
            l.footY += (ty - l.footY) * min(1f, dt * 18f)
            l.t = 1f
        }
        p.heading += angleDiff(p.heading, -PI.toFloat() / 2) * min(1f, dt * 3f)
    }

    /**
     * 2 本の骨で足の膝の位置を出す（逆運動学）。膝は体の外側に曲がる。
     * 戻り値は膝の座標。
     */
    fun knee(p: Pet, leg: Leg): Pair<Float, Float> {
        val (hx, hy) = hip(p, leg)
        val l1 = radius * 0.95f
        val l2 = radius * 1.05f
        val dx = leg.footX - hx
        val dy = leg.footY - hy
        val d = hypot(dx, dy).coerceIn(abs(l1 - l2) + 0.01f, l1 + l2 - 0.01f)
        val base = atan2(dy, dx)
        val cosA = ((l1 * l1 + d * d - l2 * l2) / (2 * l1 * d)).coerceIn(-1f, 1f)
        val a = acos(cosA)
        // 外側（付け根から見て体と反対側）に曲げる
        val bend = -leg.side.toFloat()
        val ka = base + bend * a
        // 持ち上げている足は膝を少し高く（外へ）
        val liftOut = 1f + leg.lift * 0.25f
        return (hx + cos(ka) * l1 * liftOut) to (hy + sin(ka) * l1 * liftOut)
    }

    /** 保存用：今の満腹度となつき度 */
    fun stats(): Map<String, Pair<Float, Float>> = pets.associate { it.key to (it.fullness to it.affection) }

    companion object {
        fun angleDiff(from: Float, to: Float): Float {
            var d = (to - from) % (2 * PI.toFloat())
            if (d > PI) d -= 2 * PI.toFloat()
            if (d < -PI) d += 2 * PI.toFloat()
            return d
        }
    }
}

private fun Random.range(a: Float, b: Float): Float = if (b <= a) a else a + nextFloat() * (b - a)

internal fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float = sqrt((ax - bx) * (ax - bx) + (ay - by) * (ay - by))
