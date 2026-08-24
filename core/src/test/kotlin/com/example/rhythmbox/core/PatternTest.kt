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
        var pattern = Pattern.empty("A").withLead(0, 0, 60).withLead(0, 6, 67)
        assertEquals(60, pattern.leadAt(0, 0))
        assertEquals(Pattern.REST, pattern.leadAt(0, 1))
        assertEquals(6, pattern.nextLead(0, 0))
        assertEquals(STEPS_PER_BAR, pattern.nextLead(0, 6))
        assertEquals(2, pattern.hitCount())

        pattern = pattern.withLead(0, 0, Pattern.REST)
        assertEquals(Pattern.REST, pattern.leadAt(0, 0))
        assertTrue(pattern.clearAllLeads().isEmpty())
    }

    @Test
    fun `old patterns without the melodic rows still work`() {
        // 音程を足す前の保存データ（ドラム 8 行のみ、lead 無し）を読んだ状態
        val legacy = Pattern("A", List(DRUM_COUNT) { if (it == 0) 0b1 else 0 }, emptyList())
        assertTrue(legacy.isOn(0, 0))
        assertFalse(legacy.isOn(ROW_CHORD, 0))
        assertEquals(Pattern.REST, legacy.leadAt(0, 3))

        val fixed = legacy.normalized()
        assertEquals(STEP_ROW_COUNT, fixed.rows.size)
        assertEquals(STEPS_PER_BAR, fixed.leadBars.first().size)
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

    @Test
    fun `a pattern can hold a different melody for each repetition`() {
        // ドラムは同じでも、繰り返しごとに旋律を変えられる。
        var pattern = Pattern.empty("A").withLeadBarCount(4)
        assertEquals(4, pattern.leadBarCount)

        pattern = pattern.withLead(0, 0, 60).withLead(1, 0, 62).withLead(3, 8, 67)
        assertEquals(60, pattern.leadAt(0, 0))
        assertEquals(62, pattern.leadAt(1, 0))
        assertEquals(Pattern.REST, pattern.leadAt(2, 0))
        assertEquals(67, pattern.leadAt(3, 8))
        assertEquals(3, pattern.leadNoteCount())

        // 範囲を超えた繰り返しは折り返す（4 小節ぶんなら 5 回目は 1 回目と同じ）。
        assertEquals(60, pattern.leadAt(4, 0))
        assertEquals(62, pattern.leadAt(5, 0))
    }

    @Test
    fun `clearing one bar leaves the others`() {
        val pattern = Pattern.empty("A")
            .withLeadBarCount(2)
            .withLead(0, 0, 60)
            .withLead(1, 0, 62)
        val cleared = pattern.clearLead(0)
        assertEquals(Pattern.REST, cleared.leadAt(0, 0))
        assertEquals(62, cleared.leadAt(1, 0))
        assertEquals(2, cleared.leadBarCount)

        val all = pattern.clearAllLeads()
        assertEquals(1, all.leadBarCount)
        assertEquals(0, all.leadNoteCount())
    }

    @Test
    fun `changing the number of bars keeps what was written`() {
        val pattern = Pattern.empty("A").withLeadBarCount(4).withLead(1, 4, 64)
        assertEquals(64, pattern.withLeadBarCount(4).leadAt(1, 4))
        // 減らしてから戻すと、消えたぶんは空に戻る
        assertEquals(64, pattern.withLeadBarCount(2).leadAt(1, 4))
        assertEquals(1, pattern.withLeadBarCount(1).leadBarCount)
        // 上限を超えては増やせない
        assertEquals(Pattern.MAX_LEAD_BARS, pattern.withLeadBarCount(99).leadBarCount)
    }

    @Test
    fun `songs saved with a single melody still play it`() {
        // 旋律を小節ごとに持てるようにする前の保存データ。
        val legacy = Pattern(
            name = "A",
            rows = List(STEP_ROW_COUNT) { 0 },
            lead = List(STEPS_PER_BAR) { if (it == 0) 72 else Pattern.REST },
        )
        assertEquals(1, legacy.leadBarCount)
        assertEquals(72, legacy.leadAt(0, 0))

        val fixed = legacy.normalized()
        assertEquals(72, fixed.leadAt(0, 0))
        assertTrue("新しい形に移してある", fixed.lead.isEmpty())
        assertEquals(1, fixed.leads.size)
    }

    @Test
    fun `a note can be stretched over several steps`() {
        val pattern = Pattern.empty("A").withLead(0, 0, 72).withLeadTie(0, 0, 6)

        assertEquals(72, pattern.leadAt(0, 0))
        for (step in 1..6) assertEquals("step=$step", Pattern.TIE, pattern.leadAt(0, step))
        assertEquals(Pattern.REST, pattern.leadAt(0, 7))
        assertEquals(6, pattern.tieRun(0, 0))
        // 伸ばした先でも「鳴っているのは C5」と分かる。
        for (step in 0..6) assertEquals("step=$step", 72, pattern.soundingLead(0, step))
        assertEquals(Pattern.REST, pattern.soundingLead(0, 7))
        // 伸ばしても音の数は 1 つのまま。
        assertEquals(1, pattern.leadNoteCount())
    }

    @Test
    fun `stretching to the same step again puts the note back`() {
        val stretched = Pattern.empty("A").withLead(0, 4, 60).withLeadTie(0, 4, 10)
        val back = stretched.withLeadTie(0, 4, 10)

        assertEquals(0, back.tieRun(0, 4))
        assertEquals(60, back.leadAt(0, 4))
        for (step in 5..10) assertEquals("step=$step", Pattern.REST, back.leadAt(0, step))
    }

    @Test
    fun `stretching to an earlier step shortens the note`() {
        val pattern = Pattern.empty("A").withLead(0, 0, 60).withLeadTie(0, 0, 12).withLeadTie(0, 0, 5)

        assertEquals(5, pattern.tieRun(0, 0))
        assertEquals(Pattern.REST, pattern.leadAt(0, 6))
    }

    @Test
    fun `deleting a note also takes away what it was holding`() {
        val pattern = Pattern.empty("A").withLead(0, 2, 64).withLeadTie(0, 2, 9)
        val cleared = pattern.withLead(0, 2, Pattern.REST)

        for (step in 0 until STEPS_PER_BAR) {
            assertEquals("step=$step", Pattern.REST, cleared.leadAt(0, step))
        }
    }

    @Test
    fun `a bar can start held over from the bar before`() {
        val first = MutableList(STEPS_PER_BAR) { Pattern.REST }
        first[14] = 67
        first[15] = Pattern.TIE
        val second = MutableList(STEPS_PER_BAR) { Pattern.REST }
        second[0] = Pattern.TIE
        second[1] = Pattern.TIE
        second[4] = 60
        val pattern = Pattern.empty("A").withLeads(listOf(first, second))

        assertEquals(67, pattern.soundingLead(1, 0))
        assertEquals(67, pattern.soundingLead(1, 1))
        assertEquals(Pattern.REST, pattern.soundingLead(1, 2))
        // 次の音を探すときは、タイを音として数えない。
        assertEquals(4, pattern.nextLead(1, 0))
    }
}
