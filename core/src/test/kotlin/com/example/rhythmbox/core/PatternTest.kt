package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternTest {

    @Test
    fun `of parses step strings`() {
        val pattern = Pattern.of("A", "x...x...x...x...", "....x.......x...")
        assertTrue(pattern.isOn(0, 0))
        assertTrue(pattern.isOn(0, 4))
        assertFalse(pattern.isOn(0, 1))
        assertTrue(pattern.isOn(1, 4))
        assertFalse(pattern.isOn(2, 0))
        assertEquals(4 + 2, pattern.hitCount())
    }

    @Test
    fun `toggle flips a single step only`() {
        val base = Pattern.empty("A")
        val toggled = base.toggle(3, 7)
        assertTrue(toggled.isOn(3, 7))
        assertEquals(1, toggled.hitCount())
        assertEquals(0, toggled.toggle(3, 7).hitCount())
        assertEquals(0, base.hitCount()) // 元のインスタンスは変わらない
    }

    @Test
    fun `set and clearRow`() {
        var pattern = Pattern.empty("A").set(1, 0, true).set(1, 8, true).set(2, 2, true)
        assertEquals(3, pattern.hitCount())
        pattern = pattern.clearRow(1)
        assertEquals(1, pattern.hitCount())
        assertTrue(pattern.isOn(2, 2))
        assertTrue(pattern.cleared().isEmpty())
    }

    @Test
    fun `rows never exceed the step mask`() {
        val pattern = Pattern("A", List(STEP_ROW_COUNT) { -1 }).set(0, 0, true)
        assertEquals(Pattern.STEP_MASK, pattern.rows[0])
    }

    @Test
    fun `chord and bass live in their own rows`() {
        val pattern = Pattern.empty("A").set(ROW_CHORD, 0, true).set(ROW_BASS, 4, true)
        assertTrue(pattern.isOn(ROW_CHORD, 0))
        assertFalse(pattern.isOn(ROW_BASS, 0))
        assertTrue(pattern.isOn(ROW_BASS, 4))
        // ドラムの行には触れていない
        assertTrue((0 until DRUM_COUNT).all { pattern.rowAt(it) == 0 })
    }

    @Test
    fun `nextHit finds the following step or the end of the bar`() {
        val pattern = Pattern.of("A", "x...x......x....")
        assertEquals(4, pattern.nextHit(0, 0))
        assertEquals(11, pattern.nextHit(0, 4))
        assertEquals(STEPS_PER_BAR, pattern.nextHit(0, 11))
        assertEquals(STEPS_PER_BAR, pattern.nextHit(1, 0)) // 何も無い行
    }

    @Test
    fun `lead holds one note per step`() {
        var pattern = Pattern.empty("A").withLead(0, 60).withLead(6, 67)
        assertEquals(60, pattern.leadAt(0))
        assertEquals(Pattern.REST, pattern.leadAt(1))
        assertEquals(6, pattern.nextLead(0))
        assertEquals(STEPS_PER_BAR, pattern.nextLead(6))
        assertEquals(2, pattern.hitCount())

        pattern = pattern.withLead(0, Pattern.REST)
        assertEquals(Pattern.REST, pattern.leadAt(0))
        assertTrue(pattern.clearLead().isEmpty())
    }

    @Test
    fun `old patterns without the melodic rows still work`() {
        // 音程を足す前の保存データ（ドラム 8 行のみ、lead 無し）を読んだ状態
        val legacy = Pattern("A", List(DRUM_COUNT) { if (it == 0) 0b1 else 0 }, emptyList())
        assertTrue(legacy.isOn(0, 0))
        assertFalse(legacy.isOn(ROW_CHORD, 0))
        assertEquals(Pattern.REST, legacy.leadAt(3))

        val fixed = legacy.normalized()
        assertEquals(STEP_ROW_COUNT, fixed.rows.size)
        assertEquals(STEPS_PER_BAR, fixed.lead.size)
        assertTrue(fixed.isOn(0, 0))
    }

    @Test
    fun `default patterns provide a starting groove`() {
        val patterns = Song.defaultPatterns()
        assertEquals(Song.PATTERN_COUNT, patterns.size)
        assertTrue(patterns[0].hitCount() > 0)
        assertTrue(patterns[0].isOn(ROW_CHORD, 0))
        assertTrue(patterns[0].isOn(ROW_BASS, 0))
        assertTrue(patterns.drop(2).all { it.isEmpty() })
    }
}
