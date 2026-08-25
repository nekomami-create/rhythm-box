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

    @Test
    fun `every lead voice really sounds different`() {
        // 倍音の番号だけでは足りない（スクエアとトライアングルは同じ奇数倍音で、
        // 違うのはその大きさと包絡線）。音色まるごとで見分ける。
        val timbres = ToneSynth.LeadVoice.entries.map { ToneSynth.timbre(Instrument.LEAD, it) }
        assertEquals(timbres.size, timbres.toSet().size)
        // 基音は必ず入っている。
        assertTrue(timbres.all { timbre -> timbre.partials.any { it.harmonic == 1 } })
    }

    @Test
    fun `no lead voice runs off the ends of the envelope`() {
        for (voice in ToneSynth.LeadVoice.entries) {
            val timbre = ToneSynth.timbre(Instrument.LEAD, voice)
            assertTrue("$voice", timbre.sustain in 0f..0.95f)
            assertTrue("$voice", timbre.attack > 0.0 && timbre.decay > 0.0)
            assertTrue("$voice", timbre.partials.isNotEmpty())
        }
    }

    @Test
    fun `plucked voices die away and held voices do not`() {
        val pluck = ToneSynth.timbre(Instrument.LEAD, ToneSynth.LeadVoice.PLUCK)
        val organ = ToneSynth.timbre(Instrument.LEAD, ToneSynth.LeadVoice.ORGAN)
        assertTrue("pluck=${pluck.sustain} organ=${organ.sustain}", pluck.sustain < organ.sustain)
        assertTrue("pluck=${pluck.decay} organ=${organ.decay}", pluck.decay < organ.decay)
        // 息で吹く音は立ち上がりが遅い。
        val flute = ToneSynth.timbre(Instrument.LEAD, ToneSynth.LeadVoice.FLUTE)
        assertTrue(flute.attack > pluck.attack)
    }

    @Test
    fun `the lead voice does not change the other parts`() {
        val chord = ToneSynth.timbre(Instrument.CHORD)
        val bass = ToneSynth.timbre(Instrument.BASS)
        for (voice in ToneSynth.LeadVoice.entries) {
            assertEquals(chord, ToneSynth.timbre(Instrument.CHORD, voice))
            assertEquals(bass, ToneSynth.timbre(Instrument.BASS, voice))
        }
    }

    @Test
    fun `the bell rings longer than the others`() {
        val bell = ToneSynth.timbre(Instrument.LEAD, ToneSynth.LeadVoice.BELL)
        val square = ToneSynth.timbre(Instrument.LEAD, ToneSynth.LeadVoice.SQUARE)
        assertTrue("bell=${bell.decay} square=${square.decay}", bell.decay > square.decay)
    }
}
