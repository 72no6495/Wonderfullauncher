// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (c) 2026 72no6495

package com.wonderful.launcher.ui.pets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PetSynthTest {
    @Test
    fun everySoundRendersCleanly() {
        Sfx.entries.forEach { sfx ->
            val a = PetSynth.render(sfx)
            assertTrue("$sfx empty", a.size > PetSynth.RATE / 100)
            assertTrue("$sfx too long", a.size < PetSynth.RATE)
            assertTrue("$sfx not finite", a.all { it.isFinite() })
            val peak = a.maxOf { abs(it) }
            assertTrue("$sfx peak $peak", peak in 0.3f..1f)
            // 終わりはプツッと鳴らない
            assertTrue("$sfx click at end", abs(a.last()) < 0.05f)
            val wav = PetSynth.wav(a)
            assertEquals(44 + a.size * 2, wav.size)
            assertEquals("RIFF", String(wav, 0, 4))
        }
    }
}
