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

    // --- 音色が「その楽器らしい」周波数になっているか ---------------------

    /** [from]〜[to] Hz に含まれるエネルギーの割合（0.0〜1.0）。 */
    private fun bandRatio(samples: FloatArray, from: Double, to: Double): Double {
        val start = (sampleRate * 0.01).toInt()
        val window = minOf(8192, samples.size - start)
        var inBand = 0.0
        var total = 0.0
        var frequency = 20.0
        while (frequency < 14_000.0) {
            var real = 0.0
            var imaginary = 0.0
            for (i in 0 until window) {
                val angle = 2 * Math.PI * frequency * i / sampleRate
                real += samples[start + i] * kotlin.math.cos(angle)
                imaginary += samples[start + i] * kotlin.math.sin(angle)
            }
            val power = (real * real + imaginary * imaginary) / (window.toDouble() * window)
            total += power
            if (frequency in from..to) inBand += power
            frequency += 20.0
        }
        return if (total <= 0.0) 0.0 else inBand / total
    }

    @Test
    fun `hi-hats are bright, not a mid-range buzz`() {
        // 発振器の基音が残っていると「シャッ」ではなく「ブーッ」になる。
        for (voice in listOf(Voice.CLOSED_HAT, Voice.OPEN_HAT)) {
            val samples = DrumSynth.render(voice, sampleRate)
            val high = bandRatio(samples, 4_000.0, 14_000.0)
            val mid = bandRatio(samples, 200.0, 1_000.0)
            assertTrue("${voice.label}: 4kHz 以上が %.0f%% しかない".format(high * 100), high > 0.7)
            assertTrue("${voice.label}: 中域が %.0f%% も残っている".format(mid * 100), mid < 0.1)
        }
    }

    @Test
    fun `the cowbell sits in the middle instead of piercing`() {
        val samples = DrumSynth.render(Voice.COWBELL, sampleRate)
        val body = bandRatio(samples, 200.0, 4_000.0)
        val harsh = bandRatio(samples, 4_000.0, 14_000.0)
        assertTrue("胴鳴りが %.0f%% しかない".format(body * 100), body > 0.85)
        assertTrue("高域が %.0f%% も出ている".format(harsh * 100), harsh < 0.15)
    }

    @Test
    fun `the kick is low and the snare has both body and noise`() {
        val kick = DrumSynth.render(Voice.KICK, sampleRate)
        assertTrue("キックの低域が足りない", bandRatio(kick, 20.0, 200.0) > 0.6)

        val snare = DrumSynth.render(Voice.SNARE, sampleRate)
        assertTrue("胴鳴りが無い", bandRatio(snare, 150.0, 500.0) > 0.1)
        assertTrue("スナッピーが無い", bandRatio(snare, 1_000.0, 14_000.0) > 0.1)
    }
}
