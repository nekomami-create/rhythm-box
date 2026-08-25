package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PadRecorderTest {

    @Test
    fun `a hit just after the beat lands on that beat`() {
        assertEquals(4, PadRecorder.quantise(4, 0.0))
        assertEquals(4, PadRecorder.quantise(4, 0.2))
        assertEquals(4, PadRecorder.quantise(4, 0.49))
    }

    @Test
    fun `a hit closer to the next step lands on the next step`() {
        assertEquals(5, PadRecorder.quantise(4, 0.5))
        assertEquals(5, PadRecorder.quantise(4, 0.8))
        assertEquals(6, PadRecorder.quantise(4, 1.6))
    }

    @Test
    fun `a hit at the end of the bar wraps to the top`() {
        // 小節の最後のステップの直後に叩いたら、次の小節の頭ではなく
        // 同じ小節の頭に置く（1 小節をループしているため）。
        assertEquals(0, PadRecorder.quantise(15, 0.6))
        assertEquals(1, PadRecorder.quantise(15, 1.5))
    }

    @Test
    fun `the frame clock is turned into a step`() {
        val framesPerStep = 1000.0
        // ステップ 8 が 80000 フレーム目に鳴った。
        assertEquals(8, PadRecorder.stepAt(8, 80_000, 80_100, framesPerStep))
        assertEquals(9, PadRecorder.stepAt(8, 80_000, 80_700, framesPerStep))
        // 少し早く叩いた（前のステップの余韻の中）ぶんも、いちばん近いところへ。
        assertEquals(8, PadRecorder.stepAt(8, 80_000, 79_900, framesPerStep))
    }

    @Test
    fun `recording keeps what was already there`() {
        val pattern = Pattern.of("A", "x...............")
        val after = PadRecorder.record(pattern, 0, 8, Pattern.Level.NORMAL)

        assertTrue(after.isOn(0, 0))
        assertTrue(after.isOn(0, 8))
    }

    @Test
    fun `recording can put an accent down`() {
        val after = PadRecorder.record(Pattern.empty("A"), 2, 4, Pattern.Level.ACCENT)

        assertTrue(after.isOn(2, 4))
        assertEquals(Pattern.Level.ACCENT, after.levelAt(2, 4))
    }
}
