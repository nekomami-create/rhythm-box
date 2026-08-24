package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class ToneSynthTest {

    @Test
    fun `midi notes map to the usual frequencies`() {
        assertEquals(440.0, ToneSynth.frequency(69), 1e-9)
        assertEquals(220.0, ToneSynth.frequency(57), 1e-9)
        assertEquals(261.6255653, ToneSynth.frequency(60), 1e-6)
    }

    @Test
    fun `the sine table matches the real sine closely`() {
        var worst = 0.0
        var phase = 0.0
        while (phase < 3.0) {
            val expected = sin(2 * PI * phase)
            worst = maxOf(worst, abs(expected - ToneSynth.sine(phase)))
            phase += 0.00037
        }
        assertTrue("誤差 $worst", worst < 1e-3)
    }

    @Test
    fun `negative phases wrap correctly`() {
        assertEquals(ToneSynth.sine(0.25), ToneSynth.sine(-1.75), 1e-4f)
    }

    @Test
    fun `every instrument has a usable timbre`() {
        for (instrument in Instrument.entries) {
            val timbre = ToneSynth.timbre(instrument)
            assertTrue(timbre.partials.isNotEmpty())
            assertTrue(timbre.gain > 0f && timbre.gain <= 1f)
            assertTrue(timbre.sustain in 0f..1f)
            assertTrue(timbre.attack > 0 && timbre.release > 0)
            assertTrue(timbre.maxGateSteps in 1..STEPS_PER_BAR)
        }
    }

    @Test
    fun `the middle of the hold knob changes nothing`() = with(ToneSynth) {
        for (instrument in Instrument.entries) {
            val base = timbre(instrument)
            assertEquals(base, base.withHold(ToneSynth.DEFAULT_HOLD))
        }
    }

    @Test
    fun `turning the hold knob up stretches the envelope`() = with(ToneSynth) {
        val base = timbre(Instrument.LEAD)
        val long = base.withHold(1f)
        val short = base.withHold(0f)

        assertTrue(long.decay > base.decay && base.decay > short.decay)
        assertTrue(long.release > base.release && base.release > short.release)
        assertTrue(long.sustain > base.sustain && base.sustain > short.sustain)
        assertTrue(long.maxGateSteps > base.maxGateSteps)
        assertTrue(short.maxGateSteps < base.maxGateSteps)
    }

    @Test
    fun `the hold knob never runs off the ends`() = with(ToneSynth) {
        for (instrument in Instrument.entries) {
            var value = 0f
            while (value <= 1f) {
                val timbre = timbre(instrument).withHold(value)
                assertTrue("$instrument $value", timbre.sustain in 0f..0.95f)
                assertTrue("$instrument $value", timbre.maxGateSteps in 1..STEPS_PER_BAR)
                assertTrue("$instrument $value", timbre.decay > 0.0 && timbre.release > 0.0)
                value += 0.05f
            }
        }
    }
}
