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
    fun `set and clearVoice`() {
        var pattern = Pattern.empty("A").set(1, 0, true).set(1, 8, true).set(2, 2, true)
        assertEquals(3, pattern.hitCount())
        pattern = pattern.clearVoice(1)
        assertEquals(1, pattern.hitCount())
        assertTrue(pattern.isOn(2, 2))
        assertTrue(pattern.cleared().isEmpty())
    }

    @Test
    fun `rows never exceed the step mask`() {
        val pattern = Pattern("A", List(VOICE_COUNT) { -1 }).set(0, 0, true)
        assertEquals(Pattern.STEP_MASK, pattern.rows[0])
    }

    @Test
    fun `default patterns provide a starting groove`() {
        val patterns = Song.defaultPatterns()
        assertEquals(Song.PATTERN_COUNT, patterns.size)
        assertTrue(patterns[0].hitCount() > 0)
        assertTrue(patterns.drop(2).all { it.isEmpty() })
    }
}
