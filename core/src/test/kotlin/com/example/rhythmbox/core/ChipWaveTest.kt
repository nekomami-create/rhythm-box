package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * チップ音源のために直接作るようにした波形の確認。
 *
 * 加算合成のままではデューティ比が出せない（どの比を指定しても 50% になる）ので、
 * ここでは「実際に指定どおりの幅になっているか」を波形そのもので測る。
 */
class ChipWaveTest {

    private val sampleRate = 44_100

    private fun phaseStepFor(midi: Int) = ToneSynth.frequency(midi) / sampleRate

    /** 1 周期ぶんを細かく刻んで、波形が上を向いている時間の割合を出す。 */
    private fun positiveRatio(duty: Float, phaseStep: Double, points: Int = 20_000): Double {
        var above = 0
        for (i in 0 until points) {
            if (ToneSynth.pulse(i.toDouble() / points, duty, phaseStep) > 0f) above++
        }
        return above.toDouble() / points
    }

    private fun renderPulse(midi: Int, duty: Float, frames: Int): FloatArray {
        val step = phaseStepFor(midi)
        return FloatArray(frames) { ToneSynth.pulse(it * step, duty, step) }
    }

    /** 段差を丸めない素朴な矩形波（比較用）。 */
    private fun renderNaive(midi: Int, duty: Float, frames: Int): FloatArray {
        val step = phaseStepFor(midi)
        return FloatArray(frames) { if ((it * step).mod(1.0) < duty) 1f else -1f }
    }

    /** 折り返しの無い理想の矩形波（ナイキストより下の奇数倍音だけを足す）。 */
    private fun renderIdeal(midi: Int, frames: Int): FloatArray {
        val frequency = ToneSynth.frequency(midi)
        val out = FloatArray(frames)
        var harmonic = 1
        while (frequency * harmonic < sampleRate / 2.0) {
            val gain = 4.0 / (PI * harmonic)
            for (i in 0 until frames) {
                out[i] += (gain * sin(2 * PI * frequency * harmonic * i / sampleRate)).toFloat()
            }
            harmonic += 2
        }
        return out
    }

    private fun magnitudeAt(buffer: FloatArray, frequency: Double): Double {
        var real = 0.0
        var imaginary = 0.0
        for (i in buffer.indices) {
            val angle = 2 * PI * frequency * i / sampleRate
            real += buffer[i] * cos(angle)
            imaginary += buffer[i] * sin(angle)
        }
        return hypot(real, imaginary) / buffer.size
    }

    /** 2 つの波形のずれ（実効値）。位相が合っているもの同士で使う。 */
    private fun difference(a: FloatArray, b: FloatArray): Double {
        var sum = 0.0
        for (i in a.indices) {
            val d = a[i] - b[i]
            sum += d * d
        }
        return sqrt(sum / a.size)
    }

    @Test
    fun `a pulse actually comes out at the width it was asked for`() {
        // 加算合成ではここが全部 50% になっていた。直接作れば指定どおりになる。
        val step = phaseStepFor(48) // C3。低い音なら段差を丸めるぶんの影響はごく小さい
        assertEquals(0.125, positiveRatio(0.125f, step), 0.01)
        assertEquals(0.25, positiveRatio(0.25f, step), 0.01)
        assertEquals(0.50, positiveRatio(0.50f, step), 0.01)
    }

    @Test
    fun `the widths are audibly different from each other`() {
        // 幅が違えば倍音の出方も違う。25% は 2 倍音を持つが、50% は持たない。
        val frames = 8_192
        val midi = 57 // A3 = 220Hz
        val second = ToneSynth.frequency(midi) * 2
        val thin = magnitudeAt(renderPulse(midi, 0.25f, frames), second)
        val square = magnitudeAt(renderPulse(midi, 0.5f, frames), second)

        assertTrue("25% は偶数倍音を持つ ($thin)", thin > 0.15)
        assertTrue("50% は偶数倍音がほぼ無い ($square)", square < 0.02)
    }

    @Test
    fun `smoothing the edges keeps high notes from turning metallic`() {
        // 高い音ほど段差が粗くなり、丸めないと実機に無い成分（折り返し）が乗る。
        val midi = 96 // C7 = 2093Hz
        val frames = 8_192
        val ideal = renderIdeal(midi, frames)
        val smoothed = difference(renderPulse(midi, 0.5f, frames), ideal)
        val naive = difference(renderNaive(midi, 0.5f, frames), ideal)

        assertTrue("丸めたほうが理想に近い（丸め $smoothed / 素朴 $naive）", smoothed < naive)
    }

    @Test
    fun `the chip triangle really is a sixteen step staircase`() {
        val levels = (0 until 4_000).map { ToneSynth.chipTriangle(it / 4_000.0) }.distinct()
        assertEquals("段の数", ToneSynth.TRIANGLE_LEVELS, levels.size)
        assertEquals("いちばん上", 1f, levels.max(), 1e-6f)
        assertEquals("いちばん下", -1f, levels.min(), 1e-6f)
        // 段が等間隔に並んでいること（実機の 4bit と同じ刻み）。
        val sorted = levels.sorted()
        val gaps = sorted.zipWithNext { a, b -> b - a }
        assertTrue("段の高さが揃っている", gaps.all { abs(it - gaps.first()) < 1e-5f })
    }

    @Test
    fun `the triangle is symmetric around its turning point`() {
        for (i in 0 until 500) {
            val t = i / 1_000.0
            assertEquals(ToneSynth.chipTriangle(t), ToneSynth.chipTriangle(1.0 - t - 1e-9), 1e-5f)
        }
    }

    private fun lfsrPeriod(shortPeriod: Boolean): Int {
        var state = ToneSynth.LFSR_SEED
        var steps = 0
        do {
            state = ToneSynth.nextLfsr(state, shortPeriod)
            steps++
        } while (state != ToneSynth.LFSR_SEED && steps <= 40_000)
        return steps
    }

    @Test
    fun `the noise repeats on the two periods the hardware uses`() {
        // 短周期は 93 段で 1 周するので、繰り返しが耳に付いて音程として聞こえる。
        assertEquals(93, lfsrPeriod(shortPeriod = true))
        // 長周期は 32767 段。こちらは雑音として聞こえる。
        assertEquals(32_767, lfsrPeriod(shortPeriod = false))
    }

    @Test
    fun `the noise only ever swings between two values`() {
        var state = ToneSynth.LFSR_SEED
        val seen = mutableSetOf<Float>()
        repeat(5_000) {
            state = ToneSynth.nextLfsr(state, shortPeriod = false)
            seen += ToneSynth.lfsrOutput(state)
        }
        assertEquals(setOf(-1f, 1f), seen)
    }

    @Test
    fun `the voices that were already there still use the old method`() {
        // チップ音色を足したせいで、今までの音が変わっていないこと。
        // チップ側を並べると音色を足すたびに直すことになるので、
        // 守りたいほう（前からあった 10 音色）を名指しで押さえる。
        val original = listOf(
            ToneSynth.LeadVoice.SQUARE,
            ToneSynth.LeadVoice.SAW,
            ToneSynth.LeadVoice.SOFT,
            ToneSynth.LeadVoice.BELL,
            ToneSynth.LeadVoice.TRIANGLE,
            ToneSynth.LeadVoice.PLUCK,
            ToneSynth.LeadVoice.ORGAN,
            ToneSynth.LeadVoice.BRASS,
            ToneSynth.LeadVoice.FLUTE,
            ToneSynth.LeadVoice.GLASS,
        )
        for (voice in original) {
            val timbre = ToneSynth.timbre(Instrument.LEAD, voice)
            assertEquals("${voice.label} は加算合成のまま", ToneSynth.Waveform.Additive, timbre.wave)
            assertTrue("${voice.label} は何も動かさないまま", !timbre.modulation.active)
        }
        // コードとベースも、この段階ではまだ触っていない。
        assertEquals(ToneSynth.Waveform.Additive, ToneSynth.timbre(Instrument.CHORD).wave)
        assertEquals(ToneSynth.Waveform.Additive, ToneSynth.timbre(Instrument.BASS).wave)
    }

    @Test
    fun `every chip voice is wired to a waveform of its own`() {
        val waves = listOf(
            ToneSynth.LeadVoice.PULSE_12 to ToneSynth.Waveform.Pulse(0.125f),
            ToneSynth.LeadVoice.PULSE_25 to ToneSynth.Waveform.Pulse(0.25f),
            ToneSynth.LeadVoice.PULSE_50 to ToneSynth.Waveform.Pulse(0.5f),
            ToneSynth.LeadVoice.CHIP_TRIANGLE to ToneSynth.Waveform.ChipTriangle,
            ToneSynth.LeadVoice.PULSE_SWEEP to ToneSynth.Waveform.Pulse(0.125f),
            ToneSynth.LeadVoice.CHIP_NOISE to ToneSynth.Waveform.Noise(shortPeriod = true),
        )
        for ((voice, wave) in waves) {
            assertEquals(voice.label, wave, ToneSynth.timbre(Instrument.LEAD, voice).wave)
        }
    }
}
