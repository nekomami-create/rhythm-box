package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPlanTest {

    private val c = Chord(0, ChordQuality.MAJOR)
    private val am = Chord(9, ChordQuality.MINOR)
    private val f = Chord(5, ChordQuality.MAJOR)

    private val song = Song("s", "test", bpm = 120).copy(
        arrangement = listOf(
            ArrangementStep(0, 2, listOf(c, am)),
            ArrangementStep(3, 1, listOf(f)),
            ArrangementStep(1, 3, listOf(c)),
        ),
    )

    @Test
    fun `arrangement expands repeats into bars`() {
        val plan = PlaybackPlan.arrangement(song)
        assertEquals(listOf(0, 0, 3, 1, 1, 1), plan.bars.map { it.patternIndex })
        assertEquals(6, plan.barCount)
        assertEquals(song.pattern(3), plan.patternAt(2))
    }

    @Test
    fun `each bar carries its own chord`() {
        val plan = PlaybackPlan.arrangement(song)
        assertEquals(listOf(c, am, f, c, c, c), plan.bars.map { it.chord })
        assertEquals(am, plan.chordAt(1))
        // コードが足りないブロックは最後のコードが続く
        assertEquals(c, plan.chordAt(5))
    }

    @Test
    fun `single pattern plan repeats one bar with the pattern's own chord`() {
        val tuned = song.withPatternChord(2, am)
        val plan = PlaybackPlan.single(tuned, 2)
        assertEquals(listOf(2), plan.bars.map { it.patternIndex })
        assertEquals(am, plan.chordAt(0))
        assertEquals(song.pattern(2), plan.patternAt(0))
    }

    @Test
    fun `empty arrangement produces an empty plan`() {
        val plan = PlaybackPlan.arrangement(song.copy(arrangement = emptyList()))
        assertTrue(plan.isEmpty)
    }

    @Test
    fun `out of range values are clamped`() {
        val weird = song.copy(
            arrangement = listOf(ArrangementStep(99, 1), ArrangementStep(0, 999)),
        )
        val plan = PlaybackPlan.arrangement(weird)
        assertEquals(Song.PATTERN_COUNT - 1, plan.bars.first().patternIndex)
        assertEquals(1 + PlaybackPlan.MAX_REPEAT, plan.barCount)
        assertEquals(song.pattern(0), plan.patternAt(Int.MAX_VALUE))
        // コード指定が無いブロックはパターンの試聴コードを使う
        assertEquals(song.patternChord(0), plan.chordAt(1))
    }

    @Test
    fun `arrangement chord slots follow the repeat count`() {
        val step = ArrangementStep(0, 3, listOf(c)).withChordSlots(c)
        assertEquals(listOf(c, c, c), step.chords)
        assertEquals(listOf(c, am, c), step.withChord(1, am, c).chords)
        // 範囲外の指定は無視する
        assertEquals(step, step.withChord(9, am, c))
    }

    @Test
    fun `song reports total length`() {
        assertEquals(6, song.totalBars())
        // 120 BPM の 1 小節 = 2 秒
        assertEquals(12.0, song.totalSeconds(), 1e-9)
        assertEquals("0:12", formatDuration(song.totalSeconds()))
    }

    @Test
    fun `a chain plays the patterns one bar each, in order`() {
        val tuned = song.withPatternChord(0, c).withPatternChord(1, am).withPatternChord(4, f)
        val plan = PlaybackPlan.chain(tuned, listOf(0, 1, 4))
        assertEquals(listOf(0, 1, 4), plan.bars.map { it.patternIndex })
        assertEquals(listOf(c, am, f), plan.bars.map { it.chord })
        assertEquals(3, plan.barCount)
    }

    @Test
    fun `a chain ignores patterns that do not exist`() {
        val plan = PlaybackPlan.chain(song, listOf(0, 99, 2, -1))
        assertEquals(listOf(0, 2), plan.bars.map { it.patternIndex })
        assertTrue(PlaybackPlan.chain(song, emptyList()).isEmpty)
    }

    @Test
    fun `a plan can be repeated for export`() {
        val plan = PlaybackPlan.chain(song, listOf(0, 1)).repeated(3)
        assertEquals(6, plan.barCount)
        assertEquals(listOf(0, 1, 0, 1, 0, 1), plan.bars.map { it.patternIndex })
        assertEquals(2, PlaybackPlan.chain(song, listOf(0, 1)).repeated(0).barCount)
    }

    @Test
    fun `each bar of a block knows which melody to play`() {
        val plan = PlaybackPlan.arrangement(song)
        // 1 ブロック目は 2 小節なので、旋律は 1 小節目 / 2 小節目
        assertEquals(listOf(0, 1, 0, 0, 1, 2), plan.bars.map { it.leadBar })
        assertEquals(1, plan.leadBarAt(1))
    }

    @Test
    fun `previewing a pattern plays all of its melody bars`() {
        val tuned = song.withPattern(0, song.pattern(0).withLeadBarCount(4))
        val plan = PlaybackPlan.single(tuned, 0)
        assertEquals(4, plan.barCount)
        assertEquals(listOf(0, 1, 2, 3), plan.bars.map { it.leadBar })
        // 旋律が 1 小節ぶんだけのパターンは、これまでどおり 1 小節ループ
        assertEquals(1, PlaybackPlan.single(song, 2).barCount)
    }

    @Test
    fun `previewing a pattern uses the chords it has in the song`() {
        // 曲構成で使われているパターンは、その響きのまま試聴できる。
        val tuned = song.withPattern(0, song.pattern(0).withLeadBarCount(2))
        val plan = PlaybackPlan.single(tuned, 0)
        assertEquals(listOf(c, am), plan.bars.map { it.chord })

        // 曲構成に無いパターンは、そのパターンの試聴コードを使う
        val orphan = song.withPatternChord(6, f).withPattern(6, song.pattern(6).withLeadBarCount(2))
        assertEquals(listOf(f, f), PlaybackPlan.single(orphan, 6).bars.map { it.chord })
    }
}
