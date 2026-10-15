// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (c) 2026 72no6495

package com.wonderful.launcher.ui.pets

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.SystemClock
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 飼育モードの効果音を鳴らす。音は最初に 1 回だけ作ってキャッシュに保存し、SoundPool で鳴らす。
 * 飼育モードの画面がある間だけ存在する（縦に戻すと解放）。
 */
class PetSound(context: Context) {
    private val app = context.applicationContext
    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids = ConcurrentHashMap<Sfx, Int>()
    private val ready: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    @Volatile private var released = false
    private var silentCheckedAt = 0L
    private var silent = false

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) ready += id }
    }

    /** 音を用意する（重いので裏で呼ぶ） */
    fun load() {
        val dir = File(app.cacheDir, "pet_sfx_v$VERSION").apply { mkdirs() }
        Sfx.entries.forEach { sfx ->
            if (released) return
            val f = File(dir, "${sfx.name.lowercase()}.wav")
            runCatching {
                if (!f.exists() || f.length() < 64) f.writeBytes(PetSynth.wav(PetSynth.render(sfx)))
                ids[sfx] = pool.load(f.path, 1)
            }
        }
    }

    /** マナーモード・サイレント中か（毎回聞くと重いので 2 秒ごと） */
    private fun isSilent(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - silentCheckedAt > 2000L) {
            silentCheckedAt = now
            silent = runCatching { app.getSystemService(AudioManager::class.java).ringerMode != AudioManager.RINGER_MODE_NORMAL }.getOrDefault(false)
        }
        return silent
    }

    /**
     * まとめて鳴らす。[pan] は画面の左右（x 座標）で音を左右に振るための幅。
     */
    fun play(events: List<SoundEvent>, width: Float, volume: Float, respectSilent: Boolean) {
        if (released || events.isEmpty() || volume <= 0f) return
        if (respectSilent && isSilent()) return
        // 同じ音が同時にたくさん鳴らないように
        val played = HashSet<Sfx>()
        events.forEach { e ->
            if (e.sfx in played && e.sfx != Sfx.BLIP) return@forEach
            played += e.sfx
            val id = ids[e.sfx] ?: return@forEach
            if (id !in ready) return@forEach
            val pan = if (width > 0f) (e.x / width * 2f - 1f).coerceIn(-1f, 1f) else 0f
            val v = (volume * e.volume * BASE[e.sfx]!!).coerceIn(0f, 1f)
            val l = v * (1f - pan * 0.6f).coerceAtMost(1f)
            val r = v * (1f + pan * 0.6f).coerceAtMost(1f)
            runCatching { pool.play(id, l, r, 1, 0, e.pitch.coerceIn(0.5f, 2f)) }
        }
    }

    fun release() {
        released = true
        runCatching { pool.release() }
    }

    companion object {
        private const val VERSION = 1
        /** 音ごとの基本の大きさ（うるさくなりすぎないように） */
        private val BASE: Map<Sfx, Float> = Sfx.entries.associateWith {
            when (it) {
                Sfx.BLIP -> 0.45f
                Sfx.SNORE -> 0.35f
                Sfx.EAT -> 0.6f
                Sfx.KICK, Sfx.BUBBLE -> 0.5f
                Sfx.LAUNCH, Sfx.PICKUP, Sfx.PLACE, Sfx.REMOVE, Sfx.DROP -> 0.8f
                else -> 0.65f
            }
        }
    }
}
