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

enum class Mood {
    IDLE, WANDER, SEEK_FOOD, EAT, SLEEP, SIT, PLAY, WATCH, HAPPY, CARRIED, CURIOUS,
    // 仲間との交流
    APPROACH, AWAIT, CHAT, CHASE, FLEE, DANCE, HUG, GREET,
}

/** 交流の種類 */
enum class Social { CHAT, CHASE, DANCE, HUG, GREET }

/** 頭の上に出る気持ち（吹き出し）と、目の表情 */
enum class Emote(val emoji: String) {
    NONE(""), HAPPY("😊"), LOVE("💕"), ANGRY("💢"), SAD("💧"), SURPRISED("❗"), CONFUSED("❓"),
    SWEAT("💦"), IDEA("💡"), SLEEPY("💤"), MUSIC("🎵"), YUMMY("😋"), HUNGRY("🍖"),
}

/** その場でのちょっとした仕草 */
enum class IdleAnim { NONE, LOOK, STRETCH, HOP, SPIN, SCRATCH, TAP, YAWN, WIGGLE }

/** 効果音の種類 */
enum class Sfx { PAT, EAT, DROP, LAUNCH, PICKUP, PLOP, PLACE, REMOVE, KICK, BUBBLE, BLIP, YAWN, SNORE, LOVE, SURPRISE, ANGRY, SAD, HAPPY, HUNGRY, YUMMY }

/** 鳴らしてほしい音（画面側でまとめて鳴らす）。[pitch] は 1 = そのまま */
class SoundEvent(val sfx: Sfx, val x: Float, val pitch: Float, val volume: Float)

enum class FxKind { HEART, ZZZ, CRUMB, BUBBLE, NOTE, SPARK, HUNGRY, SPEECH, PHAGELET }

class Particle(
    val kind: FxKind,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val life: Float,
    val size: Float,
    /** 吹き出しの中身（SPEECH） */
    val text: String = "",
) {
    var age = 0f
    val t: Float get() = (age / life).coerceIn(0f, 1f)
}

class Food(val id: Int, var x: Float, var y: Float) {
    /** 体内モードの大腸菌：泳ぐ速さ・向き・つかまっているか */
    var vx = 0f
    var heading = 0f
    var captured = false
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

    /** 元気（0 になると眠くなる） */
    var energy = 1f
    /** 一緒に遊んでいる相手 */
    var partner: Pet? = null
    var social = Social.CHAT
    /** 吹き出しの気持ち */
    var emote = Emote.NONE
    var emoteTime = 0f
    var emoteLength = 0f
    /** その場の仕草 */
    var idleAnim = IdleAnim.NONE
    var idleT = 0f
    var idleLength = 0f
    var nextIdle = 1f
    /** 見ている方向（描画用、長さ 1 くらい） */
    var gazeX = 1f
    var gazeY = 0f
    /** ぴょんと跳ねている高さ（0〜1）、伸び（-1〜1）、足をたたむ具合（0〜1） */
    var hop = 0f
    var stretch = 0f
    var tuck = 0f
    /** 鳴き声の高さ（アプリごとに違う） */
    val voice: Float = 0.8f + ((seed ushr 3) and 0xFF) / 255f * 0.5f
    var lastSfx = -10f
    /** 交流で使う：踊りの角度・言い合いになったか */
    var danceAngle = 0f
    var argued = false
    var talkTimer = 0f
    var lastKick = -10f
    /** エサのどちら側から食べるか（-1 / 1）と、近づけずに止まっていないかの見張り */
    var foodSide = 1f
    var bestDist = Float.MAX_VALUE
    var stuckT = 0f
}

/** 家具の置き方（画面から渡す）。位置は割合、solidW/H は通り抜けられない大きさ（px, 0 = 通れる） */
data class FurnitureSpec(
    val id: String, val kind: FurnitureKind, val x: Float, val y: Float,
    val scale: Float = 1f, val solidW: Float = 0f, val solidH: Float = 0f, val photo: String = "",
)

class FurnitureBody(val id: String, val kind: FurnitureKind, var x: Float, var y: Float) {
    var scale = 1f
    /** 写真のファイル名 */
    var photo = ""
    var solidW = 0f
    var solidH = 0f
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
    /** 鳴らす音。画面側が読んだら clear する */
    val sounds = ArrayList<SoundEvent>()
    /** 仲良し度（"a|b" → 0〜1） */
    val friendship = HashMap<String, Float>()
    /** 仲間どうしで遊ぶか */
    var socialEnabled = true
    /** 何秒起きていると眠くなるか（目安） */
    var awakeSeconds = 150f
    private var foodIds = 0
    private var foodCheck = 0f
    /** 体内モード（エサが大腸菌になって泳ぐ） */
    var bodyMode = false
        private set
    /** 足が無い（マクロファージ） */
    var legless = false
    /** 体内モードで大腸菌が自然にわく */
    var autoFood = false
    private var spawnTimer = 8f
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

    fun setBodyMode(on: Boolean) {
        if (on == bodyMode) return
        bodyMode = on
        food.clear()
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

    fun syncFurniture(items: List<Triple<String, FurnitureKind, Pair<Float, Float>>>) =
        syncFurnitureSpecs(items.map { (id, kind, pos) -> FurnitureSpec(id, kind, pos.first, pos.second) })

    fun syncFurnitureSpecs(items: List<FurnitureSpec>) {
        val ids = items.map { it.id }.toSet()
        furniture.removeAll { it.id !in ids }
        items.forEach { spec ->
            val px = left + (right - left) * spec.x
            val py = top + (bottom - top) * spec.y
            val f = furniture.firstOrNull { it.id == spec.id } ?: FurnitureBody(spec.id, spec.kind, px, py).also { furniture += it }
            if (spec.kind != FurnitureKind.BALL) { f.x = px; f.y = py }
            f.scale = spec.scale
            f.photo = spec.photo
            f.solidW = spec.solidW
            f.solidH = spec.solidH
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

    // ── 音と気持ち ──

    /** 音を鳴らす（同じ子が短い間に鳴らしすぎないように） */
    fun sfx(kind: Sfx, x: Float, pitch: Float = 1f, volume: Float = 1f) {
        if (sounds.size < 12) sounds += SoundEvent(kind, x, pitch, volume)
    }

    private fun petSfx(p: Pet, kind: Sfx, volume: Float = 1f, minGap: Float = 0.25f) {
        if (time - p.lastSfx < minGap) return
        p.lastSfx = time
        sfx(kind, p.x, p.voice, volume)
    }

    /** 気持ちを吹き出しで出す（目の表情も変わる） */
    fun emote(p: Pet, e: Emote, length: Float = 1.8f, sound: Boolean = true) {
        p.emote = e
        p.emoteTime = 0f
        p.emoteLength = length
        if (!sound) return
        when (e) {
            Emote.HAPPY -> petSfx(p, Sfx.HAPPY)
            Emote.LOVE -> petSfx(p, Sfx.LOVE)
            Emote.ANGRY -> petSfx(p, Sfx.ANGRY)
            Emote.SAD -> petSfx(p, Sfx.SAD, 0.7f)
            Emote.SURPRISED -> petSfx(p, Sfx.SURPRISE)
            Emote.YUMMY -> petSfx(p, Sfx.YUMMY)
            Emote.HUNGRY -> petSfx(p, Sfx.HUNGRY, 0.6f)
            Emote.SLEEPY -> petSfx(p, Sfx.YAWN, 0.7f)
            Emote.CONFUSED, Emote.SWEAT, Emote.IDEA -> petSfx(p, Sfx.BLIP, 0.6f)
            Emote.MUSIC, Emote.NONE -> Unit
        }
    }

    private fun pairKey(a: Pet, b: Pet) = if (a.key < b.key) "${a.key}|${b.key}" else "${b.key}|${a.key}"
    fun friendshipOf(a: Pet, b: Pet): Float = friendship[pairKey(a, b)] ?: 0.2f
    private fun addFriendship(a: Pet, b: Pet, v: Float) {
        friendship[pairKey(a, b)] = (friendshipOf(a, b) + v).coerceIn(0f, 1f)
    }

    // ── 操作 ──

    fun petAt(x: Float, y: Float): Pet? =
        pets.filter { hypot(it.x - x, it.y - y) < radius * 1.25f }.minByOrNull { hypot(it.x - x, it.y - y) }

    fun furnitureAt(x: Float, y: Float): FurnitureBody? =
        furniture.filter {
            if (it.solidW > 0f) abs(it.x - x) < it.solidW / 2 && abs(it.y - y) < it.solidH / 2
            else hypot(it.x - x, it.y - y) < furnitureRadius(it) * 1.1f
        }.minByOrNull { hypot(it.x - x, it.y - y) }

    /** 家具の大きさ（px）。置いた時の大きさも反映 */
    fun furnitureRadius(f: FurnitureBody): Float = furnitureRadius(f.kind) * f.scale

    fun furnitureRadius(kind: FurnitureKind): Float = radius * when (kind) {
        FurnitureKind.BALL -> 0.45f
        FurnitureKind.BED, FurnitureKind.SOFA, FurnitureKind.RUG, FurnitureKind.CASTLE, FurnitureKind.TREE, FurnitureKind.TENT, FurnitureKind.DOGHOUSE -> 1.3f
        FurnitureKind.TV, FurnitureKind.TOWER, FurnitureKind.ROCK, FurnitureKind.CHEST -> 0.95f
        FurnitureKind.BOWL, FurnitureKind.SHELL -> 0.6f
        FurnitureKind.PHOTO -> 1.2f
        FurnitureKind.WIDGET -> 1.5f
        else -> 0.8f
    }

    /** 撫でる */
    fun pat(p: Pet) {
        p.joy = 1f
        p.infoTime = 3f
        p.affection = (p.affection + 0.03f).coerceAtMost(1f)
        sfx(Sfx.PAT, p.x, p.voice)
        when {
            // 寝ていたらびっくりして起きる
            p.mood == Mood.SLEEP -> { endSocial(p); setMood(p, Mood.HAPPY, 1.6f); emote(p, Emote.SURPRISED, 1.2f); p.hop = 1f }
            p.mood == Mood.CARRIED || p.mood == Mood.EAT -> Unit
            else -> {
                endSocial(p)
                setMood(p, Mood.HAPPY, 1.4f)
                if (p.emote != Emote.LOVE && p.emote != Emote.HAPPY) emote(p, if (p.affection > 0.6f && rnd.nextFloat() < 0.6f) Emote.LOVE else Emote.HAPPY, 1.6f, sound = false)
            }
        }
        repeat(3) {
            particles += Particle(FxKind.HEART, p.x + rnd.range(-radius, radius) * 0.6f, p.y - radius * 0.8f, rnd.range(-25f, 25f), rnd.range(-90f, -50f), rnd.range(1f, 1.6f), rnd.range(0.7f, 1.1f))
        }
        trimParticles()
    }

    /** 床をタップ：近くの子が見に来る（なついているほど来る） */
    fun call(x: Float, y: Float) {
        pets.forEach { p ->
            if (p.mood in BUSY || p.mood == Mood.SLEEP) return@forEach
            val d = hypot(p.x - x, p.y - y)
            val chance = 0.25f + p.affection * 0.7f
            if (d < (right - left) * 0.6f && rnd.nextFloat() < chance) {
                endSocial(p)
                setMood(p, Mood.CURIOUS, 4f)
                p.targetX = x + rnd.range(-radius, radius) * 1.5f
                p.targetY = y + rnd.range(-radius, radius) * 1.5f
                clampTarget(p)
                if (rnd.nextFloat() < 0.4f) emote(p, if (rnd.nextBoolean()) Emote.CONFUSED else Emote.SURPRISED, 1.2f)
            }
        }
        particles += Particle(FxKind.SPARK, x, y, 0f, 0f, 0.5f, 1f)
    }

    fun dropFood(x: Float, y: Float) {
        if (food.size >= 24) food.removeAt(0)
        val f = Food(foodIds++, x.coerceIn(left, right), y.coerceIn(top, if (aquarium) floorY else bottom))
        when {
            bodyMode -> { f.drop = 0f; f.landed = true; f.heading = rnd.range(0f, 2 * PI.toFloat()) }
            aquarium -> { f.drop = 0f; f.vy = 20f }
            else -> f.drop = 1f
        }
        food += f
        sfx(Sfx.DROP, f.x, 0.9f + rnd.nextFloat() * 0.3f)
        // お腹が空いている子・近い子から向かう（お腹いっぱいでも、誰かは必ず食べに来る）
        val free = pets.filter { it.mood !in BUSY && it.mood != Mood.SLEEP && it.mood != Mood.EAT }
        free.sortedBy { it.fullness * 2f + hypot(it.x - f.x, it.y - f.y) / (right - left) }.take(2).forEach { p ->
            endSocial(p)
            goToFood(p, f)
            if (rnd.nextFloat() < 0.5f) emote(p, if (p.fullness < 0.5f) Emote.SURPRISED else Emote.IDEA, 1.1f)
        }
    }

    fun pickUp(p: Pet) {
        endSocial(p)
        setMood(p, Mood.CARRIED, 999f)
        p.targetFurniture?.let { id -> furniture.firstOrNull { it.id == id }?.let { if (it.user == p.key) it.user = null } }
        p.targetFurniture = null
        p.targetFood = null
        p.vx = 0f; p.vy = 0f
        sfx(Sfx.PICKUP, p.x, p.voice)
        emote(p, if (rnd.nextBoolean()) Emote.SURPRISED else Emote.SWEAT, 1.4f, sound = false)
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
        p.hop = 0.6f
        sfx(Sfx.PLOP, p.x, p.voice)
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
            if (bodyMode) {
                stepBacterium(f, dt)
                return@forEach
            }
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
        food.removeAll { it.amount <= 0f }
        // 体内では大腸菌がときどき自然にわく
        if (bodyMode && autoFood) {
            spawnTimer -= dt
            if (spawnTimer <= 0f) {
                spawnTimer = rnd.range(12f, 24f)
                if (food.size < 3) {
                    // 画面の端から泳いでくる
                    val side = rnd.nextInt(4)
                    val x = if (side == 0) left + radius else if (side == 1) right - radius else rnd.range(left, right)
                    val y = if (side == 2) top + radius else if (side == 3) bottom - radius else rnd.range(top, bottom)
                    val f = Food(foodIds++, x, y).apply { drop = 0f; landed = true; heading = rnd.range(0f, 2 * PI.toFloat()) }
                    food += f
                }
            }
        }
        // 誰も向かっていないエサがあったら、手の空いている子を行かせる（残さず食べる）
        foodCheck -= dt
        if (foodCheck <= 0f && food.isNotEmpty() && pets.isNotEmpty()) {
            foodCheck = 0.6f
            food.forEach { f ->
                val claimed = pets.any { it.targetFood == f.id && (it.mood == Mood.SEEK_FOOD || it.mood == Mood.EAT) }
                if (!claimed) {
                    // 交流中や寝ている子より、ひまな子を優先
                    val free = pets.filter { it.mood !in BUSY && it.mood !in SOCIAL && it.mood != Mood.EAT }
                    val pool = free.filter { it.mood != Mood.SLEEP }.ifEmpty { free }
                    pool.minByOrNull { it.fullness * 2f + hypot(it.x - f.x, it.y - f.y) / (right - left) }?.let { p ->
                        if (p.mood == Mood.SLEEP) emote(p, Emote.SURPRISED, 1f)
                        goToFood(p, f)
                    }
                }
            }
        }
    }

    /** 大腸菌：のんびり泳ぎ、近くに来られると逃げる。つかまったら止まる */
    private fun stepBacterium(f: Food, dt: Float) {
        f.captured = pets.any { it.targetFood == f.id && it.mood == Mood.EAT }
        if (f.captured) { f.vx *= 0.8f; f.vy *= 0.8f; return }
        val threat = pets.filter { it.mood != Mood.SLEEP }.minByOrNull { hypot(it.x - f.x, it.y - f.y) }
        val d = threat?.let { hypot(it.x - f.x, it.y - f.y) } ?: Float.MAX_VALUE
        var want = f.heading + sin(f.age * 1.3f + f.id) * 0.8f * dt
        var speed = radius * 0.45f
        if (threat != null && d < radius * 3.2f) {
            // 逃げる（でも捕まえられるくらいの速さ）
            want = atan2(f.y - threat.y, f.x - threat.x)
            speed = radius * 0.85f
        }
        f.heading += angleDiff(f.heading, want) * min(1f, dt * 3f)
        f.vx += (cos(f.heading) * speed - f.vx) * min(1f, dt * 2f)
        f.vy += (sin(f.heading) * speed - f.vy) * min(1f, dt * 2f)
        f.x += f.vx * dt
        f.y += f.vy * dt
        val m = radius * 0.5f
        if (f.x < left + m || f.x > right - m) { f.heading = PI.toFloat() - f.heading; f.x = f.x.coerceIn(left + m, right - m) }
        if (f.y < top + m || f.y > bottom - m) { f.heading = -f.heading; f.y = f.y.coerceIn(top + m, bottom - m) }
        val (ox, oy) = outOfSolids(f.x, f.y, m)
        f.x = ox; f.y = oy
    }

    private fun stepFurniture(dt: Float) {
        furniture.forEach { f ->
            f.fx += dt
            if (f.kind == FurnitureKind.BALL) {
                f.x += f.vx * dt
                f.y += f.vy * dt
                val r = furnitureRadius(f)
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
            if (m != Mood.IDLE) { p.idleAnim = IdleAnim.NONE; p.stretch = 0f }
        }
        p.mood = m
        p.moodTime = 0f
        p.moodLength = length
        p.bestDist = Float.MAX_VALUE
        p.stuckT = 0f
    }

    /** そのエサに向かっている・食べている子の数 */
    private fun claims(f: Food, except: Pet? = null): Int =
        pets.count { it !== except && it.targetFood == f.id && (it.mood == Mood.SEEK_FOOD || it.mood == Mood.EAT) }

    /** 1 つのエサを囲めるのは 2 人まで（全員が群がって動けなくならないように） */
    private fun hasRoom(f: Food, p: Pet? = null) = f.amount > 0f && claims(f, p) < MAX_EATERS

    /** エサのどこに立つか（左右に分かれて食べる） */
    private fun foodSpot(p: Pet, f: Food): Pair<Float, Float> =
        (f.x + p.foodSide * radius * 0.85f) to (if (aquarium) f.y - radius * 0.3f else f.y)

    private fun goToFood(p: Pet, f: Food) {
        setMood(p, Mood.SEEK_FOOD, 20f)
        p.targetFood = f.id
        p.targetFurniture = null
        // もう 1 人いたら反対側へ
        val other = pets.firstOrNull { it !== p && it.targetFood == f.id && (it.mood == Mood.SEEK_FOOD || it.mood == Mood.EAT) }
        p.foodSide = other?.let { -it.foodSide } ?: if (p.x < f.x) -1f else 1f
        p.bestDist = Float.MAX_VALUE
        p.stuckT = 0f
    }

    private fun stepPet(p: Pet, dt: Float) {
        p.moodTime += dt
        p.joy = max(0f, p.joy - dt * 0.7f)
        p.infoTime = max(0f, p.infoTime - dt)
        p.fullness = max(0f, p.fullness - dt / hungerSeconds)
        p.tail += dt * (2f + p.speed / max(radius, 1f) * 3f)
        p.lift += ((if (p.mood == Mood.CARRIED) 1f else 0f) - p.lift) * min(1f, dt * 10f)
        p.hop = max(0f, p.hop - dt * 3f)
        val wantTuck = if (p.mood == Mood.SLEEP) 1f else if (p.mood in listOf(Mood.SIT, Mood.WATCH, Mood.HUG)) 0.45f else 0f
        p.tuck += (wantTuck - p.tuck) * min(1f, dt * 3f)
        // 気持ちの吹き出し
        if (p.emote != Emote.NONE) {
            p.emoteTime += dt
            if (p.emoteTime > p.emoteLength) p.emote = Emote.NONE
        }
        // 元気：動くと減り、寝ると戻る
        if (p.mood == Mood.SLEEP) p.energy = min(1f, p.energy + dt / 25f)
        else p.energy = max(0f, p.energy - dt / awakeSeconds * (if (p.speed > radius * 0.3f) 1.6f else 0.8f))
        // まばたき
        p.nextBlink -= dt
        if (p.nextBlink <= 0f) { p.blink = 1f; p.nextBlink = rnd.range(1.5f, 5f) }
        p.blink = max(0f, p.blink - dt * 7f)

        // おなかが空いたら知らせる
        if (p.fullness < 0.3f && p.mood != Mood.SLEEP) {
            p.hungryFx -= dt
            if (p.hungryFx <= 0f) {
                p.hungryFx = rnd.range(6f, 11f)
                if (rnd.nextBoolean()) particles += Particle(FxKind.HUNGRY, p.x, p.y - radius * 1.2f, 0f, -12f, 2.2f, 1f)
                else emote(p, if (rnd.nextBoolean()) Emote.HUNGRY else Emote.SAD, 2f)
            }
        }

        if (p.mood == Mood.CARRIED) {
            dangleLegs(p, dt)
            return
        }

        think(p, dt)
        if (p.mood == Mood.IDLE) stepIdle(p, dt) else { p.idleAnim = IdleAnim.NONE; p.stretch *= 1f - min(1f, dt * 6f) }
        move(p, dt)
        updateGaze(p, dt)
        if (!aquarium && !legless) updateLegs(p, dt)
        if (aquarium) {
            val want = when {
                p.idleAnim == IdleAnim.SPIN -> if (sin(p.idleT / max(p.idleLength, 0.1f) * PI.toFloat() * 2f) > 0f) 1f else -1f
                abs(p.vx) > 4f -> if (p.vx > 0f) 1f else -1f
                p.partner != null && p.mood in SOCIAL -> if (p.partner!!.x > p.x) 1f else -1f
                else -> p.face
            }
            p.face += (want - p.face) * min(1f, dt * 5f)
            // 口からぷくぷく
            if (rnd.nextFloat() < dt * 0.35f) {
                particles += Particle(FxKind.BUBBLE, p.x + p.face * radius * 0.9f, p.y - radius * 0.1f, rnd.range(-5f, 5f), rnd.range(-60f, -40f), rnd.range(2f, 3f), rnd.range(0.3f, 0.6f))
                trimParticles()
            }
        }
    }

    /** 見ている方向（エサ・相手・きょろきょろ） */
    private fun updateGaze(p: Pet, dt: Float) {
        var gx = cos(p.heading)
        var gy = sin(p.heading)
        val partner = p.partner
        val f = food.firstOrNull { it.id == p.targetFood }
        when {
            p.idleAnim == IdleAnim.LOOK -> { gx = sin(p.idleT * 2.6f); gy = 0.2f * cos(p.idleT * 1.3f) }
            partner != null && p.mood in SOCIAL -> { val d = hypot(partner.x - p.x, partner.y - p.y).coerceAtLeast(1f); gx = (partner.x - p.x) / d; gy = (partner.y - p.y) / d }
            f != null -> { val d = hypot(f.x - p.x, f.y - p.y).coerceAtLeast(1f); gx = (f.x - p.x) / d; gy = (f.y - p.y) / d }
            p.mood == Mood.WATCH -> { gx = 0f; gy = -1f }
        }
        p.gazeX += (gx - p.gazeX) * min(1f, dt * 6f)
        p.gazeY += (gy - p.gazeY) * min(1f, dt * 6f)
    }

    /** その場の仕草（きょろきょろ・伸び・ジャンプ・回る・かく・あくび…） */
    private fun stepIdle(p: Pet, dt: Float) {
        if (p.idleAnim == IdleAnim.NONE) {
            p.nextIdle -= dt
            if (p.nextIdle <= 0f) {
                val choices = mutableListOf(IdleAnim.LOOK, IdleAnim.LOOK, IdleAnim.STRETCH, IdleAnim.HOP, IdleAnim.SPIN, IdleAnim.WIGGLE)
                if (!aquarium) { choices += IdleAnim.SCRATCH; choices += IdleAnim.TAP }
                if (p.energy < 0.6f) { choices += IdleAnim.YAWN; choices += IdleAnim.YAWN }
                p.idleAnim = choices.random(rnd)
                p.idleT = 0f
                p.idleLength = when (p.idleAnim) {
                    IdleAnim.LOOK -> 2.4f
                    IdleAnim.STRETCH -> 1.4f
                    IdleAnim.HOP -> 0.9f
                    IdleAnim.SPIN -> 1.1f
                    IdleAnim.SCRATCH, IdleAnim.TAP -> 1.6f
                    IdleAnim.YAWN -> 1.8f
                    IdleAnim.WIGGLE -> 1f
                    IdleAnim.NONE -> 0f
                }
                when (p.idleAnim) {
                    IdleAnim.YAWN -> emote(p, Emote.SLEEPY, 1.6f)
                    IdleAnim.WIGGLE -> { p.joy = 0.5f; if (rnd.nextFloat() < 0.4f) emote(p, Emote.MUSIC, 1.2f, sound = false) }
                    IdleAnim.SCRATCH -> if (rnd.nextFloat() < 0.3f) emote(p, Emote.SWEAT, 1f, sound = false)
                    else -> Unit
                }
            }
            return
        }
        p.idleT += dt
        val k = (p.idleT / p.idleLength).coerceIn(0f, 1f)
        when (p.idleAnim) {
            IdleAnim.STRETCH -> p.stretch = sin(k * PI.toFloat())
            IdleAnim.YAWN -> p.stretch = 0.6f * sin(k * PI.toFloat())
            IdleAnim.HOP -> {
                val h = abs(sin(k * PI.toFloat() * 2f))
                if (h > p.hop) p.hop = h
                if (aquarium) p.vy -= radius * 6f * dt * cos(k * PI.toFloat() * 2f)
            }
            IdleAnim.SPIN -> if (!aquarium) p.heading += 2f * PI.toFloat() * dt / p.idleLength
            else -> Unit
        }
        if (p.idleT >= p.idleLength) {
            p.idleAnim = IdleAnim.NONE
            p.stretch = 0f
            p.nextIdle = rnd.range(0.8f, 3f)
        }
    }

    /** 次に何をするか決める */
    private fun think(p: Pet, dt: Float) {
        when (p.mood) {
            Mood.SEEK_FOOD -> {
                val f = food.firstOrNull { it.id == p.targetFood }
                if (f == null || f.amount <= 0f) { pickNext(p); return }
                // 先に 2 人が食べ始めていたら譲る
                if (pets.count { it !== p && it.targetFood == f.id && it.mood == Mood.EAT } >= MAX_EATERS) { pickNext(p); return }
                val (sx, sy) = foodSpot(p, f)
                p.targetX = sx; p.targetY = sy
                clampTarget(p)
                val ready = aquarium || f.landed
                val dSpot = hypot(p.x - p.targetX, p.y - p.targetY)
                val dFood = hypot(p.x - f.x, p.y - f.y)
                // 近づけているか見張る（押し合いで止まったら諦める）
                if (dSpot < p.bestDist - radius * 0.15f) { p.bestDist = dSpot; p.stuckT = 0f } else p.stuckT += dt
                when {
                    ready && (dSpot < radius * 0.6f || dFood < radius * 1.3f) -> setMood(p, Mood.EAT, 999f)
                    ready && p.stuckT > 2.5f && dFood < radius * 2.2f -> setMood(p, Mood.EAT, 999f)
                    p.stuckT > 3.5f || p.moodTime > p.moodLength -> { emote(p, Emote.SWEAT, 1.2f, sound = false); pickNext(p, avoidFood = f.id) }
                }
            }
            Mood.EAT -> {
                val f = food.firstOrNull { it.id == p.targetFood }
                val bowl = furniture.firstOrNull { it.id == p.targetFurniture && it.kind == FurnitureKind.BOWL }
                if (f == null && bowl == null) { finishMeal(p); return }
                if (f != null) {
                    // エサは無くなるまで食べる（お腹いっぱいでも残さない）
                    if (f.amount <= 0f) { finishMeal(p); return }
                    if (hypot(p.x - f.x, p.y - (if (aquarium) f.y - radius * 0.3f else f.y)) > radius * 2.4f) { goToFood(p, f); return }
                    val bite = min(f.amount, dt * 0.5f)
                    f.amount -= bite
                    if (f.amount <= 0f && bodyMode) burst(p, f)
                    p.fullness = (p.fullness + bite * 0.35f).coerceAtMost(1f)
                } else if (bowl != null) {
                    if (hypot(p.x - bowl.x, p.y - bowl.y) > radius * 1.6f) { pickNext(p); return }
                    p.fullness = (p.fullness + dt * 0.16f).coerceAtMost(1f)
                    if (p.fullness >= 1f || p.moodTime > 6f) { finishMeal(p); return }
                }
                if (rnd.nextFloat() < dt * 6f) {
                    particles += Particle(if (aquarium) FxKind.BUBBLE else FxKind.CRUMB, p.x + rnd.range(-10f, 10f), p.y + radius * 0.2f, rnd.range(-40f, 40f), rnd.range(-70f, -30f), 0.6f, 0.5f)
                }
                if (rnd.nextFloat() < dt * 3f) petSfx(p, Sfx.EAT, 0.8f, 0.3f)
            }
            Mood.SLEEP -> {
                if (rnd.nextFloat() < dt * 0.9f) particles += Particle(FxKind.ZZZ, p.x + radius * 0.4f, p.y - radius * 0.8f, 14f, -26f, 2.2f, rnd.range(0.7f, 1.1f))
                if (rnd.nextFloat() < dt * 0.25f) petSfx(p, Sfx.SNORE, 0.35f, 2f)
                if (p.moodTime > p.moodLength || p.energy >= 1f) {
                    // 起きたら伸び
                    setMood(p, Mood.IDLE, rnd.range(1.5f, 3f))
                    p.idleAnim = IdleAnim.STRETCH; p.idleT = 0f; p.idleLength = 1.4f
                    emote(p, Emote.SLEEPY, 1.4f)
                }
            }
            Mood.WATCH -> {
                if (rnd.nextFloat() < dt * 0.3f) particles += Particle(FxKind.NOTE, p.x, p.y - radius, 8f, -30f, 1.4f, 0.8f)
                if (rnd.nextFloat() < dt * 0.08f) emote(p, listOf(Emote.HAPPY, Emote.SURPRISED, Emote.SAD, Emote.LOVE).random(rnd), 1.5f)
                if (p.moodTime > p.moodLength) pickNext(p)
            }
            Mood.PLAY -> {
                val ball = furniture.firstOrNull { it.id == p.targetFurniture }
                if (ball == null || p.moodTime > p.moodLength) { pickNext(p); return }
                p.targetX = ball.x; p.targetY = ball.y
                val d = hypot(p.x - ball.x, p.y - ball.y)
                if (d < radius + furnitureRadius(ball) && time - p.lastKick > 0.6f) {
                    p.lastKick = time
                    // 蹴る
                    val a = atan2(ball.y - p.y, ball.x - p.x) + rnd.range(-0.4f, 0.4f)
                    val power = radius * rnd.range(5f, 9f)
                    ball.vx = cos(a) * power
                    ball.vy = sin(a) * power
                    p.joy = 0.5f
                    sfx(Sfx.KICK, ball.x, rnd.range(0.9f, 1.2f))
                    if (rnd.nextFloat() < 0.3f) emote(p, Emote.HAPPY, 1.2f, sound = false)
                }
            }
            Mood.SIT, Mood.HAPPY, Mood.IDLE -> if (p.moodTime > p.moodLength && p.idleAnim == IdleAnim.NONE) pickNext(p)
            Mood.WANDER, Mood.CURIOUS -> {
                val fid = p.targetFurniture
                val f = fid?.let { id -> furniture.firstOrNull { it.id == id } }
                // ボールは転がるので追いかける
                if (f != null && f.kind == FurnitureKind.BALL) { p.targetX = f.x; p.targetY = f.y }
                val arrived = hypot(p.targetX - p.x, p.targetY - p.y) < radius * (if (f != null) 0.6f else 0.9f) ||
                    (f?.kind == FurnitureKind.BALL && hypot(f.x - p.x, f.y - p.y) < radius * 1.5f)
                // 寝床の近くまで来たら（先客がいて近づけなくても）そこで寝る
                val nearBed = f != null && p.energy < 0.3f && f.kind in SLEEP_SPOTS && hypot(p.targetX - p.x, p.targetY - p.y) < radius * 1.8f
                // 混んでいて近づけない時の見張り
                val dTarget = hypot(p.targetX - p.x, p.targetY - p.y)
                if (dTarget < p.bestDist - radius * 0.15f) { p.bestDist = dTarget; p.stuckT = 0f } else p.stuckT += dt
                val stuck = p.stuckT > 2.5f
                when {
                    // 家具に着いたら使う（混んでいたら近くで使う）
                    (arrived || nearBed || (stuck && dTarget < radius * 2.5f)) && p.mood == Mood.WANDER && f != null -> useFurniture(p, f)
                    // 押し合いで進めない → あきらめて別のことをする
                    stuck && p.stuckT > 3.5f -> pickNext(p)
                    // 眠いのに寝床に着けなかったらその場で寝る
                    p.moodTime > p.moodLength && p.energy < 0.3f -> { endWork(p); setMood(p, Mood.SLEEP, rnd.range(12f, 25f)) }
                    arrived || p.moodTime > p.moodLength ->
                        if (p.mood == Mood.CURIOUS) { setMood(p, Mood.IDLE, rnd.range(1f, 2.5f)); p.joy = 0.3f } else pickNext(p)
                }
            }
            Mood.CARRIED -> Unit
            Mood.APPROACH, Mood.AWAIT, Mood.CHAT, Mood.CHASE, Mood.FLEE, Mood.DANCE, Mood.HUG, Mood.GREET -> thinkSocial(p, dt)
        }
    }

    /** 大腸菌を食べ終わった時：ファージは中から子ファージが飛び出し、マクロファージは消化のかけら */
    private fun burst(p: Pet, f: Food) {
        val n = if (legless) 5 else 8
        repeat(n) { i ->
            val a = i * 2f * PI.toFloat() / n + rnd.range(-0.2f, 0.2f)
            val sp = radius * rnd.range(1.2f, 2.2f)
            particles += if (legless) Particle(FxKind.BUBBLE, f.x, f.y, cos(a) * sp * 0.4f, sin(a) * sp * 0.4f - 20f, rnd.range(0.8f, 1.4f), rnd.range(0.4f, 0.8f))
            else Particle(FxKind.PHAGELET, f.x, f.y, cos(a) * sp, sin(a) * sp, rnd.range(0.9f, 1.4f), rnd.range(0.7f, 1f))
        }
        sfx(if (legless) Sfx.BUBBLE else Sfx.REMOVE, f.x, rnd.range(1.1f, 1.4f), 0.7f)
        trimParticles()
    }

    private fun finishMeal(p: Pet) {
        p.affection = (p.affection + 0.01f).coerceAtMost(1f)
        p.joy = 0.7f
        particles += Particle(FxKind.NOTE, p.x, p.y - radius, 10f, -40f, 1.4f, 1f)
        // まだ残っているエサがあれば続けて食べに行く
        val next = food.filter { hasRoom(it, p) }.minByOrNull { hypot(it.x - p.x, it.y - p.y) }
        if (next != null) { goToFood(p, next); return }
        emote(p, Emote.YUMMY, 1.6f)
        pickNext(p)
    }

    // ── 仲間との交流 ──

    private fun startSocial(p: Pet, q: Pet) {
        val f = friendshipOf(p, q)
        val roll = rnd.nextFloat()
        val kind = when {
            roll < 0.32f -> Social.CHAT
            roll < 0.52f -> Social.CHASE
            roll < 0.68f -> Social.DANCE
            roll < 0.84f && f > 0.35f -> Social.HUG
            else -> Social.GREET
        }
        p.partner = q; q.partner = p
        p.social = kind; q.social = kind
        setMood(p, Mood.APPROACH, 9f)
        endWork(q)
        setMood(q, Mood.AWAIT, 9f)
        q.vx *= 0.3f; q.vy *= 0.3f
        emote(p, if (kind == Social.CHASE) Emote.IDEA else Emote.HAPPY, 1.1f, sound = false)
        if (rnd.nextFloat() < 0.5f) emote(q, Emote.SURPRISED, 1f)
    }

    /** 家具やエサの予定を手放す */
    private fun endWork(p: Pet) {
        p.targetFurniture?.let { id -> furniture.firstOrNull { it.id == id && it.user == p.key }?.user = null }
        p.targetFurniture = null
        p.targetFood = null
    }

    /** 交流をやめる（相手も） */
    private fun endSocial(p: Pet) {
        val q = p.partner ?: return
        p.partner = null
        if (q.partner === p) {
            q.partner = null
            if (q.mood in SOCIAL) setMood(q, Mood.IDLE, rnd.range(0.5f, 1.5f))
        }
    }

    private fun beginInteraction(p: Pet, q: Pet) {
        val len = when (p.social) {
            Social.CHAT -> rnd.range(3.5f, 6.5f)
            Social.CHASE -> rnd.range(4f, 7f)
            Social.DANCE -> rnd.range(3f, 5f)
            Social.HUG -> rnd.range(2f, 3.2f)
            Social.GREET -> 1.3f
        }
        val (mp, mq) = when (p.social) {
            Social.CHAT -> Mood.CHAT to Mood.CHAT
            Social.CHASE -> Mood.CHASE to Mood.FLEE
            Social.DANCE -> Mood.DANCE to Mood.DANCE
            Social.HUG -> Mood.HUG to Mood.HUG
            Social.GREET -> Mood.GREET to Mood.GREET
        }
        setMood(p, mp, len); setMood(q, mq, len)
        p.argued = false; q.argued = false
        p.talkTimer = 0.2f; q.talkTimer = 0.6f
        val a = atan2(p.y - q.y, p.x - q.x)
        p.danceAngle = a; q.danceAngle = a + PI.toFloat()
        when (p.social) {
            Social.GREET -> { p.hop = 1f; q.hop = 1f; speech(p, "👋"); emote(q, Emote.HAPPY, 1.2f) }
            Social.HUG -> { emote(p, Emote.LOVE, 2f); emote(q, Emote.LOVE, 2f, sound = false) }
            Social.DANCE -> { emote(p, Emote.MUSIC, 2f, sound = false); emote(q, Emote.MUSIC, 2f, sound = false); sfx(Sfx.HAPPY, p.x, p.voice) }
            Social.CHASE -> { emote(q, Emote.SURPRISED, 1f); speech(p, "!") }
            Social.CHAT -> Unit
        }
    }

    private fun speech(p: Pet, text: String) {
        particles += Particle(FxKind.SPEECH, p.x + rnd.range(-6f, 6f), p.y - radius * 1.5f, rnd.range(-8f, 8f), -16f, 1.3f, 1f, text)
        trimParticles()
    }

    private fun thinkSocial(p: Pet, dt: Float) {
        val q = p.partner
        // 相手がいなくなった・別のことを始めたら終わり
        if (q == null || q.partner !== p || q.mood !in SOCIAL || q !in pets) {
            p.partner = null
            setMood(p, Mood.IDLE, rnd.range(0.5f, 1.5f))
            return
        }
        val d = hypot(q.x - p.x, q.y - p.y)
        when (p.mood) {
            Mood.APPROACH -> {
                val a = atan2(p.y - q.y, p.x - q.x)
                p.targetX = q.x + cos(a) * radius * 2.1f
                p.targetY = q.y + sin(a) * radius * 2.1f
                clampTarget(p)
                if (d < radius * 2.6f) beginInteraction(p, q)
                else if (p.moodTime > p.moodLength) endSocial(p).also { setMood(p, Mood.IDLE, 1f) }
            }
            Mood.AWAIT -> {
                // 近づいてくる相手の方を向いて待つ
                p.heading += angleDiff(p.heading, atan2(q.y - p.y, q.x - p.x)) * min(1f, dt * 4f)
                if (p.moodTime > p.moodLength) { endSocial(p); setMood(p, Mood.IDLE, 1f) }
            }
            Mood.CHAT -> {
                p.heading += angleDiff(p.heading, atan2(q.y - p.y, q.x - p.x)) * min(1f, dt * 4f)
                p.talkTimer -= dt
                if (p.talkTimer <= 0f) {
                    p.talkTimer = rnd.range(0.9f, 1.8f)
                    speech(p, TALK.random(rnd))
                    // ペチャクチャ
                    repeat(1 + rnd.nextInt(3)) { i -> sounds += SoundEvent(Sfx.BLIP, p.x, p.voice * rnd.range(0.85f, 1.25f), 0.55f - i * 0.1f) }
                    p.joy = max(p.joy, 0.3f)
                    val r = rnd.nextFloat()
                    when {
                        r < 0.12f && !q.argued -> { emote(p, Emote.ANGRY, 1.5f); p.argued = true }
                        r < 0.3f -> emote(p, listOf(Emote.HAPPY, Emote.LOVE, Emote.CONFUSED, Emote.SURPRISED, Emote.IDEA).random(rnd), 1.4f, sound = false)
                    }
                }
                if (p.moodTime > p.moodLength) finishSocial(p, q)
            }
            Mood.CHASE -> {
                p.targetX = q.x; p.targetY = q.y
                if (d < radius * 1.5f && p.moodTime > 0.6f) {
                    // タッチ！ 鬼を交代
                    speech(p, "タッチ!")
                    emote(q, Emote.SURPRISED, 1f)
                    sfx(Sfx.KICK, p.x, 1.4f, 0.7f)
                    val remain = p.moodLength - p.moodTime
                    setMood(p, Mood.FLEE, remain); setMood(q, Mood.CHASE, remain)
                    p.hop = 1f
                } else if (p.moodTime > p.moodLength) finishSocial(p, q)
            }
            Mood.FLEE -> {
                // 鬼から離れる方へ（壁際では横へ逃げる）
                val a = atan2(p.y - q.y, p.x - q.x) + sin(time * 1.3f + p.seed) * 0.8f
                p.targetX = p.x + cos(a) * radius * 4f
                p.targetY = p.y + sin(a) * radius * 4f
                clampTarget(p)
                if (p.moodTime > p.moodLength) finishSocial(p, q)
            }
            Mood.DANCE -> {
                // 2 人で輪になってくるくる
                val mx = (p.x + q.x) / 2
                val my = (p.y + q.y) / 2
                p.danceAngle += dt * 2.2f
                p.targetX = mx + cos(p.danceAngle) * radius * 1.4f
                p.targetY = my + sin(p.danceAngle) * radius * 1.4f
                if (rnd.nextFloat() < dt * 2f) particles += Particle(FxKind.NOTE, p.x, p.y - radius, rnd.range(-20f, 20f), -40f, 1.2f, 0.8f)
                if (rnd.nextFloat() < dt * 1.5f) p.hop = 0.7f
                if (p.moodTime > p.moodLength) finishSocial(p, q)
            }
            Mood.HUG -> {
                val a = atan2(p.y - q.y, p.x - q.x)
                p.targetX = q.x + cos(a) * radius * 1.25f
                p.targetY = q.y + sin(a) * radius * 1.25f
                p.joy = max(p.joy, 0.4f)
                if (rnd.nextFloat() < dt * 3f) particles += Particle(FxKind.HEART, (p.x + q.x) / 2 + rnd.range(-10f, 10f), (p.y + q.y) / 2 - radius, rnd.range(-15f, 15f), -50f, 1.2f, 0.8f)
                if (p.moodTime > p.moodLength) finishSocial(p, q)
            }
            Mood.GREET -> {
                p.heading += angleDiff(p.heading, atan2(q.y - p.y, q.x - p.x)) * min(1f, dt * 5f)
                if (p.moodTime > p.moodLength) finishSocial(p, q)
            }
            else -> Unit
        }
    }

    private fun finishSocial(p: Pet, q: Pet) {
        val fought = p.argued || q.argued
        addFriendship(p, q, if (fought) 0.02f else 0.08f)
        p.partner = null; q.partner = null
        if (fought && rnd.nextFloat() < 0.5f) {
            // けんかしたまま… でもすぐ仲直り
            emote(p, Emote.ANGRY, 1.2f); emote(q, Emote.SAD, 1.2f)
        } else {
            emote(p, if (p.social == Social.HUG) Emote.LOVE else Emote.HAPPY, 1.4f)
            emote(q, if (rnd.nextBoolean()) Emote.HAPPY else Emote.MUSIC, 1.4f, sound = false)
        }
        setMood(p, Mood.IDLE, rnd.range(1f, 2.5f))
        setMood(q, Mood.IDLE, rnd.range(1f, 2.5f))
    }

    private fun pickNext(p: Pet, avoidFood: Int? = null) {
        endWork(p)
        endSocial(p)
        // エサが残っていたら食べに行く（誰も食べていないエサを優先。満員のエサには行かない）
        val open = food.filter { it.id != avoidFood && hasRoom(it, p) }
        val leftover = open.filter { claims(it, p) == 0 }.minByOrNull { hypot(it.x - p.x, it.y - p.y) }
            ?: if (p.fullness < 0.6f) open.minByOrNull { hypot(it.x - p.x, it.y - p.y) } else null
        if (leftover != null) { goToFood(p, leftover); return }
        if (p.fullness < 0.45f) {
            val bowl = furniture.filter { it.kind == FurnitureKind.BOWL }.minByOrNull { hypot(it.x - p.x, it.y - p.y) }
            if (bowl != null) {
                setMood(p, Mood.WANDER, 12f)
                p.targetFurniture = bowl.id
                p.targetX = bowl.x; p.targetY = bowl.y + radius * 0.9f
                clampTarget(p)
                return
            }
        }
        // 眠くなったら寝床へ（無ければその場で）
        if (p.energy < 0.22f) {
            val beds = furniture.filter { it.user == null && it.kind in SLEEP_SPOTS && (aquarium == it.kind.aquarium || it.kind == FurnitureKind.ROCK) && visitors(it, p) < capacity(it.kind) }
            val bed = beds.minByOrNull { hypot(it.x - p.x, it.y - p.y) }
            emote(p, Emote.SLEEPY, 1.6f)
            if (bed != null) {
                setMood(p, Mood.WANDER, 14f)
                p.targetFurniture = bed.id
                val spot = spotFor(p, bed)
                p.targetX = spot.first; p.targetY = spot.second
                clampTarget(p)
            } else setMood(p, Mood.SLEEP, rnd.range(12f, 25f))
            return
        }
        val r = rnd.nextFloat()
        // 満員の家具には行かない（みんなが 1 か所に集まって動けなくならないように）
        val usable = furniture.filter { it.user == null && it.kind.usable(aquarium) && visitors(it, p) < capacity(it.kind) }
        // 一緒に遊べる相手（仲良しほど選ばれやすい）
        val mates = if (socialEnabled) pets.filter { it !== p && it.partner == null && it.mood in FREE } else emptyList()
        when {
            r < 0.28f && mates.isNotEmpty() -> {
                val q = mates.maxByOrNull { friendshipOf(p, it) + rnd.nextFloat() * 0.6f - hypot(it.x - p.x, it.y - p.y) / (right - left) }!!
                startSocial(p, q)
            }
            r < 0.5f && usable.isNotEmpty() -> {
                // 空いていて近い家具ほど選ばれやすい
                val f = usable.minByOrNull { visitors(it, p) * 0.6f + hypot(it.x - p.x, it.y - p.y) / (right - left) + rnd.nextFloat() * 0.9f }!!
                setMood(p, Mood.WANDER, 14f)
                p.targetFurniture = f.id
                val spot = spotFor(p, f)
                p.targetX = spot.first; p.targetY = spot.second
                clampTarget(p)
            }
            r < 0.8f -> { setMood(p, Mood.IDLE, rnd.range(2.5f, 6f)); p.nextIdle = rnd.range(0.3f, 1.5f) }
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
        FurnitureKind.PHOTO -> true
        FurnitureKind.BLOODCELL, FurnitureKind.CELL, FurnitureKind.DNA, FurnitureKind.CAPSULE -> true
        FurnitureKind.BOWL, FurnitureKind.WIDGET -> false
    }

    /** 家具のどこに行くか */
    private fun spotFor(p: Pet, f: FurnitureBody): Pair<Float, Float> = when (f.kind) {
        FurnitureKind.TV -> (f.x + rnd.range(-1.6f, 1.6f) * radius) to f.y + radius * 2.4f
        // 写真の前で見とれる
        FurnitureKind.PHOTO -> (f.x + rnd.range(-0.5f, 0.5f) * furnitureRadius(f)) to f.y + furnitureRadius(f) + radius * 1.1f
        // 木陰・テントや小屋の入り口
        FurnitureKind.TREE -> (f.x + (if (p.x < f.x) -1 else 1) * radius * 0.8f) to f.y + radius * 1.3f
        FurnitureKind.TENT, FurnitureKind.DOGHOUSE -> f.x to f.y + radius * 0.9f
        FurnitureKind.MUSHROOM, FurnitureKind.SUNFLOWER -> (f.x + (if (p.x < f.x) -1 else 1) * radius * 1.4f) to f.y + radius * 0.3f
        FurnitureKind.BALL -> f.x to f.y
        FurnitureKind.PLANT, FurnitureKind.PALM, FurnitureKind.BUST, FurnitureKind.LAMP, FurnitureKind.CORAL, FurnitureKind.SEAWEED ->
            (f.x + (if (p.x < f.x) -1 else 1) * radius * 1.6f) to f.y + radius * 0.4f
        FurnitureKind.CASTLE, FurnitureKind.ROCK, FurnitureKind.CHEST, FurnitureKind.SHELL ->
            if (aquarium) f.x to f.y - radius * 1.3f else f.x to f.y
        // 何人か来る家具は少しずらして並ぶ
        else -> (f.x + rnd.range(-0.5f, 0.5f) * radius * (capacity(f.kind) - 1)) to f.y
    }

    /** その家具に向かっている・使っている子の数 */
    private fun visitors(f: FurnitureBody, except: Pet? = null): Int = pets.count { it !== except && it.targetFurniture == f.id }

    /** 同時に使える数 */
    fun capacity(kind: FurnitureKind): Int = when (kind) {
        FurnitureKind.TV, FurnitureKind.RUG -> 3
        FurnitureKind.PHOTO, FurnitureKind.BALL, FurnitureKind.TREE, FurnitureKind.SOFA, FurnitureKind.PALM,
        FurnitureKind.CASTLE, FurnitureKind.CORAL, FurnitureKind.SEAWEED, FurnitureKind.BLOODCELL -> 2
        else -> 1
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
            FurnitureKind.CELL -> { setMood(p, Mood.SLEEP, rnd.range(6f, 14f)); f.user = p.key; p.targetFurniture = f.id }
            FurnitureKind.BLOODCELL -> { setMood(p, Mood.SIT, rnd.range(4f, 9f)); p.targetFurniture = f.id }
            FurnitureKind.DNA, FurnitureKind.CAPSULE -> { setMood(p, Mood.IDLE, rnd.range(1.5f, 3f)); emote(p, if (f.kind == FurnitureKind.DNA) Emote.IDEA else Emote.CONFUSED, 1.5f) }
            FurnitureKind.PHOTO -> {
                setMood(p, Mood.WATCH, rnd.range(3f, 6f)); p.targetFurniture = f.id
                p.heading = atan2(f.y - p.y, f.x - p.x)
                emote(p, listOf(Emote.LOVE, Emote.HAPPY, Emote.SURPRISED, Emote.MUSIC).random(rnd), 1.8f)
            }
            FurnitureKind.ROCK -> if (aquarium) { setMood(p, Mood.SLEEP, rnd.range(6f, 14f)); f.user = p.key; p.targetFurniture = f.id } else { setMood(p, Mood.SIT, rnd.range(3f, 7f)); p.targetFurniture = f.id }
            FurnitureKind.CASTLE, FurnitureKind.CHEST, FurnitureKind.SHELL ->
                if (aquarium) { setMood(p, Mood.SLEEP, rnd.range(6f, 14f)); f.user = p.key; p.targetFurniture = f.id } else setMood(p, Mood.IDLE, 2f)
            else -> { setMood(p, Mood.IDLE, rnd.range(1.5f, 3f)); p.joy = 0.2f }
        }
    }

    private fun clampTarget(p: Pet) {
        val (tx, ty) = outOfSolids(p.targetX, p.targetY, radius * 0.9f)
        p.targetX = tx.coerceIn(left + radius * 0.6f, right - radius * 0.6f)
        p.targetY = ty.coerceIn(top + radius * 0.6f, (if (aquarium) floorY else bottom) - radius * 0.6f)
    }

    /** ウィジェットなど通り抜けられない家具の外へ押し出す */
    private fun outOfSolids(x: Float, y: Float, margin: Float): Pair<Float, Float> {
        var nx = x
        var ny = y
        furniture.forEach { f ->
            if (f.solidW <= 0f || f.solidH <= 0f) return@forEach
            val hw = f.solidW / 2 + margin
            val hh = f.solidH / 2 + margin
            val dx = nx - f.x
            val dy = ny - f.y
            if (abs(dx) < hw && abs(dy) < hh) {
                if (hw - abs(dx) < hh - abs(dy)) nx = f.x + (if (dx >= 0f) hw else -hw)
                else ny = f.y + (if (dy >= 0f) hh else -hh)
            }
        }
        return nx to ny
    }

    private fun clampPet(p: Pet) {
        p.x = p.x.coerceIn(left + radius * 0.5f, max(left + radius * 0.5f, right - radius * 0.5f))
        p.y = p.y.coerceIn(top + radius * 0.5f, max(top + radius * 0.5f, (if (aquarium) floorY else bottom) - radius * 0.5f))
    }

    private fun move(p: Pet, dt: Float) {
        val moving = p.mood in MOVING ||
            (p.mood == Mood.EAT && p.targetFurniture != null && hypot(p.x - p.targetX, p.y - p.targetY) > radius * 0.6f)
        val maxSpeed = radius * (if (aquarium) 2.2f else 2.6f) * speedFactor *
            (if (p.mood in FAST) 1.5f else if (p.mood == Mood.DANCE || p.mood == Mood.HUG) 1.2f else 1f) *
            (0.6f + 0.4f * p.fullness.coerceAtLeast(0.3f)) * (if (p.energy < 0.25f) 0.75f else 1f)
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
        val (ox, oy) = outOfSolids(p.x, p.y, radius * 0.7f)
        if (ox != p.x || oy != p.y) { p.x = ox; p.y = oy; clampPet(p) }

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
            // くっついている 2 人は離さない
            if (a.partner === b && (a.mood == Mood.HUG || a.mood == Mood.DANCE)) continue
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
        // 寝ている時は足をたたむ
        val a = p.heading + legAngle(leg) * (0.92f - 0.25f * p.tuck)
        val reach = radius * (1.65f - 0.7f * p.tuck)
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
        // 仕草：前足で頭をかく / 足をトントン
        if (p.idleAnim == IdleAnim.SCRATCH || p.idleAnim == IdleAnim.TAP) {
            p.legs.firstOrNull { it.index == 0 && it.side == 1 }?.let { l ->
                val (hx, hy) = hip(p, l)
                val k = p.idleT
                if (p.idleAnim == IdleAnim.SCRATCH) {
                    // 頭の上の方へ持ち上げてカキカキ
                    val a = p.heading + legAngle(l) * 0.35f
                    l.footX = hx + cos(a) * radius * 0.9f + sin(k * 28f) * radius * 0.12f
                    l.footY = hy + sin(a) * radius * 0.9f - radius * 0.5f + cos(k * 28f) * radius * 0.08f
                } else {
                    val (rx, ry) = restFoot(p, l, 0f)
                    l.footX = rx
                    l.footY = ry - abs(sin(k * 9f)) * radius * 0.35f
                }
                l.t = 1f
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
        /** 他のことに夢中で呼んでも来ない */
        val BUSY = setOf(Mood.CARRIED, Mood.SEEK_FOOD, Mood.EAT)
        /** 交流中 */
        val SOCIAL = setOf(Mood.APPROACH, Mood.AWAIT, Mood.CHAT, Mood.CHASE, Mood.FLEE, Mood.DANCE, Mood.HUG, Mood.GREET)
        /** 遊びに誘える */
        val FREE = setOf(Mood.IDLE, Mood.WANDER, Mood.CURIOUS, Mood.SIT, Mood.HAPPY)
        /** 目的地へ動く */
        val MOVING = setOf(Mood.WANDER, Mood.SEEK_FOOD, Mood.PLAY, Mood.CURIOUS, Mood.APPROACH, Mood.CHASE, Mood.FLEE, Mood.DANCE, Mood.HUG)
        val FAST = setOf(Mood.SEEK_FOOD, Mood.PLAY, Mood.CHASE, Mood.FLEE)
        /** 1 つのエサを同時に食べられる数 */
        const val MAX_EATERS = 2
        val SLEEP_SPOTS = setOf(FurnitureKind.CELL, FurnitureKind.BED, FurnitureKind.RUG, FurnitureKind.TENT, FurnitureKind.DOGHOUSE, FurnitureKind.CASTLE, FurnitureKind.ROCK, FurnitureKind.CHEST, FurnitureKind.SHELL)
        val TALK = listOf("♪", "!", "?", "…", "♥", "☆", "🍙", "⚽", "🌸", "☀", "🌙", "💤", "🍰", "🎵", "😂", "😳", "✨", "🐟", "👀")

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
