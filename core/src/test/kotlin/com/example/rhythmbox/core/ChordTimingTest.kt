package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** コードの打点を頭から外す動き。 */
class ChordTimingTest {

    private fun chordSteps(pattern: Pattern): List<Int> =
        (0 until STEPS_PER_BAR).filter { pattern.isOn(ROW_CHORD, it) }

    private fun bassSteps(pattern: Pattern): List<Int> =
        (0 until STEPS_PER_BAR).filter { pattern.isOn(ROW_BASS, it) }

    /** [style] を [times] 回作って、コードが頭から外れた回数を数える。 */
    private fun lateCount(style: RhythmStyle, times: Int = 400): Int =
        (0 until times).count { seed ->
            0 !in chordSteps(PatternGenerator.generate(style, Random(seed)))
        }

    @Test
    fun `the chord sometimes comes in after the downbeat`() {
        // ヒップホップはいちばん外れやすい設定にしてある。
        val late = lateCount(RhythmStyle.HIPHOP)
        assertTrue("一度も外れていない", late > 0)
        assertTrue("外れすぎている ($late / 400)", late < 400 * 0.6)
    }

    @Test
    fun `busier genres let go of the downbeat more often`() {
        val hiphop = lateCount(RhythmStyle.HIPHOP)
        val eightBeat = lateCount(RhythmStyle.EIGHT_BEAT)
        assertTrue("ヒップホップ $hiphop <= 8ビート $eightBeat", hiphop > eightBeat)
    }

    @Test
    fun `the chip style always keeps the downbeat`() {
        // 高速アルペジオは打ち直した位置から回りはじめる。
        // そこがずれると、和音が 1 つの音色に聞こえる効き目が薄れる。
        assertEquals(0, lateCount(RhythmStyle.CHIP_DRIVE))
    }

    @Test
    fun `a bar never loses its chord entirely`() {
        for (style in RhythmStyle.entries) {
            for (seed in 0 until 200) {
                val steps = chordSteps(PatternGenerator.generate(style, Random(seed)))
                assertTrue("$style seed=$seed でコードが 1 つも無い", steps.isNotEmpty())
            }
        }
    }

    @Test
    fun `the bass still holds the downbeat when the chord lets go`() {
        // コードが遅れても、1 拍目そのものは鳴っている。
        var checked = 0
        for (style in RhythmStyle.entries) {
            for (seed in 0 until 200) {
                val pattern = PatternGenerator.generate(style, Random(seed))
                if (0 in chordSteps(pattern)) continue
                checked++
                assertTrue("$style seed=$seed でベースまで抜けている", 0 in bassSteps(pattern))
            }
        }
        assertTrue("外れた例が 1 つも作れていない", checked > 0)
    }

    @Test
    fun `a late chord lands either just behind the beat or on the second beat`() {
        val landings = mutableSetOf<Int>()
        for (style in RhythmStyle.entries) {
            for (seed in 0 until 300) {
                val steps = chordSteps(PatternGenerator.generate(style, Random(seed)))
                if (0 in steps) continue
                landings += steps.first()
            }
        }
        assertEquals(setOf(2, 4), landings)
    }

    @Test
    fun `the same seed gives the same pattern`() {
        val first = PatternGenerator.generate(RhythmStyle.HIPHOP, Random(11))
        val second = PatternGenerator.generate(RhythmStyle.HIPHOP, Random(11))
        assertEquals(first.rowAt(ROW_CHORD), second.rowAt(ROW_CHORD))
    }
}
