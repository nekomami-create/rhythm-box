package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PatternGeneratorTest {

    @Test
    fun `every style puts a kick on the downbeat`() {
        for (style in RhythmStyle.entries) {
            repeat(20) { seed ->
                val pattern = PatternGenerator.generate(style, Random(seed))
                assertTrue(
                    "${style.label} (seed=$seed)",
                    pattern.isOn(Voice.KICK.ordinal, 0),
                )
            }
        }
    }

    @Test
    fun `patterns are busy enough to be music but not a wall of noise`() {
        for (style in RhythmStyle.entries) {
            repeat(20) { seed ->
                val hits = PatternGenerator.generate(style, Random(seed)).hitCount()
                assertTrue("${style.label} (seed=$seed) が薄すぎる: $hits", hits >= 8)
                assertTrue("${style.label} (seed=$seed) が詰まりすぎ: $hits", hits <= 70)
            }
        }
    }

    @Test
    fun `the backbeat lands on 2 and 4 for the straight styles`() {
        for (style in listOf(RhythmStyle.EIGHT_BEAT, RhythmStyle.BREAKBEAT, RhythmStyle.HIPHOP)) {
            val pattern = PatternGenerator.generate(style, Random(7))
            assertTrue(style.label, pattern.isOn(Voice.SNARE.ordinal, 4))
            assertTrue(style.label, pattern.isOn(Voice.SNARE.ordinal, 12))
        }
    }

    @Test
    fun `four on the floor really is four on the floor`() {
        val pattern = PatternGenerator.generate(RhythmStyle.FOUR_ON_FLOOR, Random(3))
        listOf(0, 4, 8, 12).forEach { assertTrue("step $it", pattern.isOn(Voice.KICK.ordinal, it)) }
        listOf(4, 12).forEach { assertTrue("clap $it", pattern.isOn(Voice.CLAP.ordinal, it)) }
    }

    @Test
    fun `latin keeps the clave on the rim`() {
        val pattern = PatternGenerator.generate(RhythmStyle.LATIN, Random(11))
        listOf(0, 3, 6, 10, 12).forEach { assertTrue("clave $it", pattern.isOn(Voice.RIM.ordinal, it)) }
    }

    @Test
    fun `chord and bass rows get something to play`() {
        for (style in RhythmStyle.entries) {
            val pattern = PatternGenerator.generate(style, Random(5))
            assertTrue(style.label, pattern.isOn(ROW_CHORD, 0))
            assertTrue(style.label, pattern.isOn(ROW_BASS, 0))
        }
    }

    @Test
    fun `the melody is left alone`() {
        val pattern = PatternGenerator.generate(RhythmStyle.EIGHT_BEAT, Random(1))
        assertTrue(pattern.lead.all { it == Pattern.REST })
    }

    @Test
    fun `the same seed always gives the same pattern`() {
        val first = PatternGenerator.generate(RhythmStyle.BREAKBEAT, Random(99), "A")
        val second = PatternGenerator.generate(RhythmStyle.BREAKBEAT, Random(99), "A")
        assertEquals(first, second)
    }

    @Test
    fun `different seeds give different patterns`() {
        val patterns = (0 until 12).map { PatternGenerator.generate(RhythmStyle.EIGHT_BEAT, Random(it)) }
        assertTrue("同じものばかり出ている", patterns.distinct().size >= 8)
    }

    @Test
    fun `generateAny picks a style at random but still makes a groove`() {
        repeat(10) { seed ->
            val pattern = PatternGenerator.generateAny(Random(seed), "B")
            assertEquals("B", pattern.name)
            assertTrue(pattern.hitCount() >= 8)
            assertEquals(STEP_ROW_COUNT, pattern.rows.size)
        }
    }

    @Test
    fun `the hi-hat keeps a steady subdivision instead of random holes`() {
        // 刻みは「4分 / 8分 / 8分裏 / 16分」のどれかを丸ごと敷いた上に飾りが乗る。
        val grids = listOf(
            listOf(0, 4, 8, 12),
            listOf(0, 2, 4, 6, 8, 10, 12, 14),
            listOf(2, 6, 10, 14),
            (0 until STEPS_PER_BAR).toList(),
        )
        for (style in RhythmStyle.entries) {
            repeat(20) { seed ->
                val pattern = PatternGenerator.generate(style, Random(seed))
                val hits = (0 until STEPS_PER_BAR).filter { pattern.isOn(Voice.CLOSED_HAT.ordinal, it) }
                if (hits.isEmpty()) return@repeat
                assertTrue(
                    "${style.label} (seed=$seed) の刻みが不揃い: $hits",
                    grids.any { grid -> hits.containsAll(grid) },
                )
            }
        }
    }
}
