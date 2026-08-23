package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPlanTest {

    private val song = Song("s", "test", bpm = 120).copy(
        arrangement = listOf(ArrangementStep(0, 2), ArrangementStep(3, 1), ArrangementStep(1, 3)),
    )

    @Test
    fun `arrangement expands repeats into bars`() {
        val plan = PlaybackPlan.arrangement(song)
        assertEquals(listOf(0, 0, 3, 1, 1, 1), plan.bars)
        assertEquals(6, plan.barCount)
        assertEquals(song.pattern(3), plan.patternAt(2))
    }

    @Test
    fun `single pattern plan repeats one bar`() {
        val plan = PlaybackPlan.single(song, 2)
        assertEquals(listOf(2), plan.bars)
        assertEquals(song.pattern(2), plan.patternAt(0))
    }

    @Test
    fun `empty arrangement produces an empty plan`() {
        val plan = PlaybackPlan.arrangement(song.copy(arrangement = emptyList()))
        assertTrue(plan.isEmpty)
    }

    @Test
    fun `out of range values are clamped`() {
        val weird = song.copy(arrangement = listOf(ArrangementStep(99, 1), ArrangementStep(0, 999)))
        val plan = PlaybackPlan.arrangement(weird)
        assertEquals(Song.PATTERN_COUNT - 1, plan.bars.first())
        assertEquals(1 + PlaybackPlan.MAX_REPEAT, plan.barCount)
        assertEquals(song.pattern(0), plan.patternAt(Int.MAX_VALUE))
    }

    @Test
    fun `song reports total length`() {
        assertEquals(6, song.totalBars())
        // 120 BPM の 1 小節 = 2 秒
        assertEquals(12.0, song.totalSeconds(), 1e-9)
    }
}
