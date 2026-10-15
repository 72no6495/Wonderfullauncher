// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (c) 2026 72no6495

package com.wonderful.launcher.ui.pets

import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * 飼育モードの効果音をその場で作る（音声ファイルを持たないのでアプリが軽い）。
 * Android に依存しないのでテストできる。
 */
object PetSynth {
    const val RATE = 22050
    private const val TWO_PI = (2 * PI).toFloat()

    /** 効果音 1 つぶんの波形（-1〜1） */
    fun render(sfx: Sfx): FloatArray {
        val raw = when (sfx) {
            Sfx.PAT -> mix(sweep(0.13f, 520f, 800f, decay = 2f), gain(0.25f, sweep(0.13f, 1040f, 1600f, decay = 3f)))
            Sfx.HAPPY -> concat(sweep(0.08f, 700f, 1150f, decay = 1.5f), silence(0.02f), sweep(0.1f, 900f, 1500f, decay = 1.5f))
            Sfx.LOVE -> concat(*floatArrayOf(523f, 659f, 784f, 1047f).map { tone(0.075f, it, wave = ::tri, decay = 2.5f) }.toTypedArray())
            Sfx.EAT -> mix(concat(crunch(0.04f), silence(0.03f), crunch(0.035f), silence(0.04f), crunch(0.045f)), gain(0.4f, sweep(0.17f, 160f, 110f, decay = 4f)))
            Sfx.YUMMY -> concat(sweep(0.09f, 600f, 900f, decay = 1f), sweep(0.14f, 900f, 700f, decay = 2f, vibrato = 9f))
            Sfx.DROP -> concat(plink(0.16f, 1500f), silence(0.03f), gain(0.5f, plink(0.12f, 1850f)))
            Sfx.LAUNCH -> mix(whoosh(0.36f), gain(0.35f, sweep(0.36f, 300f, 1400f, decay = 0f, bell = true)))
            Sfx.PICKUP -> sweep(0.22f, 220f, 680f, decay = 1f, vibrato = 18f)
            Sfx.PLOP -> sweep(0.1f, 380f, 110f, decay = 3f)
            Sfx.PLACE -> mix(sweep(0.1f, 150f, 85f, decay = 4f), gain(0.5f, noise(0.012f, 0.9f, decay = 6f)))
            Sfx.REMOVE -> mix(noise(0.24f, 0.25f, decay = 0f, bell = true), gain(0.35f, sweep(0.24f, 600f, 200f, decay = 2f)))
            Sfx.KICK -> sweep(0.07f, 430f, 300f, wave = ::soft, decay = 4f)
            Sfx.BUBBLE -> sweep(0.07f, 260f, 950f, decay = 0f, bell = true)
            Sfx.BLIP -> tone(0.055f, 620f, wave = ::soft, decay = 2.5f, attack = 0.004f)
            Sfx.YAWN -> mix(sweep(0.62f, 520f, 230f, decay = 0f, bell = true, vibrato = 6f), gain(0.15f, noise(0.62f, 0.15f, decay = 0f, bell = true)))
            Sfx.SNORE -> mix(amNoise(0.55f), gain(0.4f, sweep(0.55f, 95f, 80f, decay = 0f, bell = true)))
            Sfx.SURPRISE -> sweep(0.1f, 420f, 1300f, wave = ::tri, decay = 1f)
            Sfx.ANGRY -> growl(0.26f)
            Sfx.SAD -> sweep(0.38f, 660f, 430f, decay = 0.5f, vibrato = 7f)
            Sfx.HUNGRY -> concat(sweep(0.14f, 900f, 620f, decay = 0.3f), sweep(0.16f, 620f, 760f, decay = 1.5f))
        }
        return normalize(raw)
    }

    // ── 部品 ──

    private fun n(dur: Float) = max(1, (dur * RATE).toInt())

    private fun sine(ph: Float) = sin(ph * TWO_PI)
    private fun tri(ph: Float): Float { val f = ph - kotlin.math.floor(ph); return 4f * abs(f - 0.5f) - 1f }
    /** 角の取れた矩形波（ピコピコ音） */
    private fun soft(ph: Float) = tanh(3f * sin(ph * TWO_PI))

    /** 音量の形：立ち上がり → 減衰（decay）/ ベル型（bell） */
    private fun env(t: Float, dur: Float, decay: Float, attack: Float, bell: Boolean): Float {
        val a = if (attack > 0f) min(1f, t / attack) else 1f
        val k = (t / dur).coerceIn(0f, 1f)
        val d = when {
            bell -> sin(k * PI.toFloat())
            decay > 0f -> Math.pow((1f - k).toDouble(), decay.toDouble()).toFloat()
            else -> 1f - k * 0.2f
        }
        // 最後はプツッと切れないように
        val tail = min(1f, (dur - t) / 0.004f).coerceAtLeast(0f)
        return a * d * tail
    }

    private fun tone(dur: Float, f: Float, wave: (Float) -> Float = ::sine, decay: Float = 2f, attack: Float = 0.005f): FloatArray =
        sweep(dur, f, f, wave, decay, attack)

    /** 周波数を f0 → f1 に変えながら鳴らす */
    private fun sweep(
        dur: Float, f0: Float, f1: Float, wave: (Float) -> Float = ::sine,
        decay: Float = 2f, attack: Float = 0.005f, bell: Boolean = false, vibrato: Float = 0f,
    ): FloatArray {
        val out = FloatArray(n(dur))
        var ph = 0f
        for (i in out.indices) {
            val t = i.toFloat() / RATE
            val k = t / dur
            // 指数カーブで滑らかに（音程として自然）
            var f = f0 * Math.pow((f1 / f0).toDouble(), k.toDouble()).toFloat()
            if (vibrato > 0f) f *= 1f + 0.06f * sin(t * vibrato * TWO_PI)
            ph += f / RATE
            if (ph > 1f) ph -= 1f
            out[i] = wave(ph) * env(t, dur, decay, attack, bell)
        }
        return out
    }

    private fun noise(dur: Float, smooth: Float, decay: Float = 2f, bell: Boolean = false, seed: Int = 1): FloatArray {
        val r = Random(seed)
        val out = FloatArray(n(dur))
        var lp = 0f
        for (i in out.indices) {
            val t = i.toFloat() / RATE
            lp += (r.nextFloat() * 2f - 1f - lp) * smooth
            out[i] = lp * env(t, dur, decay, 0.002f, bell) * 1.6f
        }
        return out
    }

    private fun crunch(dur: Float) = noise(dur, 0.55f, decay = 3f, seed = (dur * 1000).toInt())

    private fun plink(dur: Float, f: Float): FloatArray {
        val a = tone(dur, f, decay = 5f, attack = 0.002f)
        val b = tone(dur, f * 2.76f, decay = 7f, attack = 0.002f)
        return mix(a, gain(0.3f, b))
    }

    /** 風を切る音（だんだん高くなるノイズ） */
    private fun whoosh(dur: Float): FloatArray {
        val r = Random(7)
        val out = FloatArray(n(dur))
        var lp = 0f
        for (i in out.indices) {
            val t = i.toFloat() / RATE
            val k = t / dur
            lp += (r.nextFloat() * 2f - 1f - lp) * (0.04f + 0.5f * k)
            out[i] = lp * sin(k * PI.toFloat()) * 2f
        }
        return out
    }

    /** いびき（ゆれるノイズ） */
    private fun amNoise(dur: Float): FloatArray {
        val base = noise(dur, 0.08f, decay = 0f, bell = true, seed = 3)
        for (i in base.indices) base[i] *= 0.6f + 0.4f * sin(i.toFloat() / RATE * 32f * TWO_PI)
        return base
    }

    /** うなり声（ガルル） */
    private fun growl(dur: Float): FloatArray {
        val out = sweep(dur, 150f, 120f, ::soft, decay = 0f, bell = true)
        for (i in out.indices) out[i] *= 0.55f + 0.45f * sin(i.toFloat() / RATE * 26f * TWO_PI)
        return out
    }

    private fun silence(dur: Float) = FloatArray(n(dur))

    private fun concat(vararg parts: FloatArray): FloatArray {
        val out = FloatArray(parts.sumOf { it.size })
        var o = 0
        parts.forEach { it.copyInto(out, o); o += it.size }
        return out
    }

    /** 2 つの音を重ねる（長い方に合わせる） */
    private fun mix(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(max(a.size, b.size))
        for (i in out.indices) out[i] = (if (i < a.size) a[i] else 0f) + (if (i < b.size) b[i] else 0f)
        return out
    }

    private fun gain(v: Float, a: FloatArray): FloatArray = FloatArray(a.size) { a[it] * v }

    private fun normalize(a: FloatArray, peak: Float = 0.8f): FloatArray {
        val m = a.maxOfOrNull { abs(it) } ?: 0f
        if (m <= 0f || m.isNaN()) return a
        val g = peak / m
        return FloatArray(a.size) { (a[it] * g).coerceIn(-1f, 1f) }
    }

    /** 16bit モノラルの WAV ファイルの中身 */
    fun wav(samples: FloatArray): ByteArray {
        val data = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, v ->
            val s = (v.coerceIn(-1f, 1f) * 32767f).toInt()
            data[i * 2] = (s and 0xFF).toByte()
            data[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        val out = ByteArrayOutputStream(44 + data.size)
        fun str(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun int(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF); out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF) }
        fun short(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
        str("RIFF"); int(36 + data.size); str("WAVE")
        str("fmt "); int(16); short(1); short(1); int(RATE); int(RATE * 2); short(2); short(16)
        str("data"); int(data.size)
        out.write(data)
        return out.toByteArray()
    }
}
