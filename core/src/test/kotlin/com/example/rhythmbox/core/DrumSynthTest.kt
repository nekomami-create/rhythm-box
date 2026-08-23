package com.example.rhythmbox.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DrumSynthTest {

    private val sampleRate = 44_100

    @Test
    fun `every voice renders audible, in-range audio`() {
        for (voice in Voice.entries) {
            val buffer = DrumSynth.render(voice, sampleRate)
            assertTrue("${voice.label}: 長さが 0", buffer.isNotEmpty())
            assertTrue("${voice.label}: 長すぎる", buffer.size < sampleRate * 2)
            val peak = buffer.maxOf { abs(it) }
            assertTrue("${voice.label}: 無音 (peak=$peak)", peak > 0.3f)
            assertTrue("${voice.label}: 音割れ (peak=$peak)", peak <= 1.0f)
        }
    }

    @Test
    fun `rendering is deterministic`() {
        for (voice in Voice.entries) {
            assertArrayEquals(
                DrumSynth.render(voice, sampleRate),
                DrumSynth.render(voice, sampleRate),
                0f,
            )
        }
    }

    @Test
    fun `open hat rings longer than closed hat`() {
        val closed = DrumSynth.render(Voice.CLOSED_HAT, sampleRate).size
        val open = DrumSynth.render(Voice.OPEN_HAT, sampleRate).size
        assertTrue("open=$open closed=$closed", open > closed * 3)
    }

    @Test
    fun `renderAll returns one buffer per voice`() {
        assertTrue(DrumSynth.renderAll(sampleRate).size == DRUM_COUNT)
    }
}
