package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SongBuilderTest {

    private val key = MusicKey(0, minor = false)

    private fun base() = Song.newSong("s", "テスト", 0L)

    @Test
    fun `builds eight bars as two blocks of four`() {
        for (genre in Genre.entries) {
            repeat(10) { seed ->
                val song = SongBuilder.build(base(), genre, key, random = Random(seed))
                assertEquals(8, song.totalBars())
                assertEquals(2, song.arrangement.size)
                assertTrue(song.arrangement.all { it.repeat == 4 })
                assertEquals(0, song.arrangement[0].patternIndex)
                assertEquals(1, song.arrangement[1].patternIndex)
                assertTrue("${genre.label} のテンポ", song.bpm in genre.bpmRange)
            }
        }
    }

    @Test
    fun `every bar gets a chord from one of the genre's progressions`() {
        for (genre in Genre.entries) {
            repeat(10) { seed ->
                val song = SongBuilder.build(base(), genre, key, random = Random(seed))
                val bars = PlaybackPlan.arrangement(song).bars.map { it.chord }
                assertEquals(8, bars.size)
                // どれかの型を 8 小節に敷いたものと一致する
                assertTrue(
                    "${genre.label} $bars",
                    genre.progressions.any { it.fill(key, 8) == bars },
                )
            }
        }
    }

    @Test
    fun `both patterns get something to play`() {
        repeat(10) { seed ->
            val song = SongBuilder.build(base(), Genre.JPOP, key, random = Random(seed))
            listOf(0, 1).forEach { index ->
                val pattern = song.pattern(index)
                assertTrue("パターン ${pattern.name} が空", pattern.hitCount() > 0)
                assertTrue(pattern.isOn(Voice.KICK.ordinal, 0))
                assertTrue(pattern.isOn(ROW_CHORD, 0))
                assertTrue(pattern.isOn(ROW_BASS, 0))
            }
        }
    }

    @Test
    fun `each pattern previews with the chord its block starts on`() {
        val song = SongBuilder.build(base(), Genre.ROCK, key, random = Random(3))
        val bars = PlaybackPlan.arrangement(song).bars.map { it.chord }
        assertEquals(bars[0], song.patternChord(0))
        assertEquals(bars[4], song.patternChord(1))
    }

    @Test
    fun `the two halves are not identical`() {
        // 前半と後半が毎回同じだと、8 小節にする意味がない。
        val different = (0 until 20).count { seed ->
            val song = SongBuilder.build(base(), Genre.JPOP, key, random = Random(seed))
            song.pattern(0).rows != song.pattern(1).rows
        }
        assertTrue("20 回中 $different 回しか違わない", different >= 18)
    }

    @Test
    fun `each bar of a block gets its own melody`() {
        // ドラムは 4 小節同じでも、旋律は小節ごとに変える必要がある。
        repeat(10) { seed ->
            val song = SongBuilder.build(base(), Genre.JPOP, key, random = Random(seed))
            listOf(SongBuilder.FIRST_PATTERN, SongBuilder.SECOND_PATTERN).forEach { index ->
                val pattern = song.pattern(index)
                assertEquals(SongBuilder.BLOCK, pattern.leadBarCount)
                assertTrue("旋律が入っていない", pattern.leadNoteCount() > 0)
                // 4 小節が全部同じだと、繰り返しても意味がない。
                assertTrue("4 小節とも同じ旋律", pattern.leadBars.distinct().size > 1)
            }
        }
    }

    @Test
    fun `each melody lands on the chord of its own bar`() {
        repeat(10) { seed ->
            val song = SongBuilder.build(base(), Genre.JPOP, key, random = Random(seed))
            val block = song.arrangement.first()
            val pattern = song.pattern(block.patternIndex)
            block.chords.forEachIndexed { bar, chord ->
                val tones = chord.voicing().map { it.mod(12) }.toSet()
                for (step in 0 until STEPS_PER_BAR step 4) {
                    val midi = pattern.leadAt(bar, step)
                    if (Pattern.isNote(midi)) {
                        assertTrue(
                            "${bar + 1} 小節目 ${chord.name} に ${midiName(midi)}",
                            midi.mod(12) in tones,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `melodies can be left alone, and other patterns always are`() {
        val written = base()
            .withPattern(0, base().pattern(0).withLead(0, 0, 72).withLead(0, 4, 74))
            .withPattern(5, Pattern.of("F", "x...x...x...x..."))
        val song = SongBuilder.build(written, Genre.DANCE, key, random = Random(1), withMelody = false)
        assertEquals(72, song.pattern(0).leadAt(0, 0))
        assertEquals(74, song.pattern(0).leadAt(0, 4))
        assertEquals(written.pattern(5), song.pattern(5))

        // 旋律も作る場合でも、使っていないパターンには触らない。
        val withMelody = SongBuilder.build(written, Genre.DANCE, key, random = Random(1))
        assertEquals(written.pattern(5), withMelody.pattern(5))
    }

    @Test
    fun `the same seed builds the same song`() {
        assertEquals(
            SongBuilder.build(base(), Genre.CITY_POP, key, random = Random(9)),
            SongBuilder.build(base(), Genre.CITY_POP, key, random = Random(9)),
        )
        assertNotEquals(
            SongBuilder.build(base(), Genre.CITY_POP, key, random = Random(9)),
            SongBuilder.build(base(), Genre.CITY_POP, key, random = Random(10)),
        )
    }

    // --- 小節数を選べる（オート作曲） -------------------------------------

    @Test
    fun `the length can be chosen in blocks of four`() {
        assertEquals(16, SongBuilder.BAR_CHOICES.size)
        assertEquals(4, SongBuilder.BAR_CHOICES.first())
        assertEquals(64, SongBuilder.BAR_CHOICES.last())
        assertTrue(SongBuilder.BAR_CHOICES.all { it % SongBuilder.BLOCK == 0 })
    }

    @Test
    fun `it builds exactly the number of bars asked for`() {
        for (bars in SongBuilder.BAR_CHOICES) {
            val song = SongBuilder.build(base(), Genre.JPOP, key, bars, Random(bars))
            assertEquals("$bars 小節", bars, song.totalBars())
            assertEquals(bars / SongBuilder.BLOCK, song.arrangement.size)
            assertTrue(song.arrangement.all { it.repeat == SongBuilder.BLOCK })
        }
    }

    @Test
    fun `odd lengths are rounded into shape`() {
        assertEquals(4, SongBuilder.normalizeBars(0))
        assertEquals(4, SongBuilder.normalizeBars(3))
        assertEquals(4, SongBuilder.normalizeBars(7))
        assertEquals(8, SongBuilder.normalizeBars(11))
        assertEquals(64, SongBuilder.normalizeBars(999))
    }

    @Test
    fun `blocks reuse a handful of patterns instead of eating every slot`() {
        assertEquals(listOf(0), SongBuilder.patternLayout(4))
        assertEquals(listOf(0, 1), SongBuilder.patternLayout(8))
        assertEquals(listOf(0, 1, 2), SongBuilder.patternLayout(12))
        assertEquals(listOf(0, 1, 2, 3), SongBuilder.patternLayout(16))
        // 16 小節を超えたら、また A から使い回す（曲としての繰り返しになる）
        assertEquals(listOf(0, 1, 2, 3, 0, 1, 2, 3), SongBuilder.patternLayout(32))

        val song = SongBuilder.build(base(), Genre.ROCK, key, 64, Random(1))
        val used = song.arrangement.map { it.patternIndex }.distinct()
        assertEquals(SongBuilder.MAX_PATTERNS, used.size)
        // E 以降は手で書く用に空けておく
        assertTrue(
            "使っていないパターンが書き換わっている",
            (SongBuilder.MAX_PATTERNS until Song.PATTERN_COUNT).all { base().pattern(it) == song.pattern(it) },
        )
    }

    @Test
    fun `a repeated pattern still fits the chords wherever it comes back`() {
        // 同じパターンが後半にもう一度出てくるとき、そのブロックのコードが
        // 最初のブロックと同じでないと、作った旋律が合わなくなる。
        for (genre in Genre.entries) {
            for (bars in listOf(16, 32, 64)) {
                val song = SongBuilder.build(base(), genre, key, bars, Random(bars))
                val byPattern = song.arrangement.groupBy { it.patternIndex }
                byPattern.forEach { (index, blocks) ->
                    val first = blocks.first().chords
                    assertTrue(
                        "${genre.label} $bars 小節: パターン $index のコードがブロックごとに違う",
                        blocks.all { it.chords == first },
                    )
                }
            }
        }
    }

    @Test
    fun `long songs still get a melody for every bar of every pattern`() {
        val song = SongBuilder.build(base(), Genre.JPOP, key, 32, Random(5))
        song.arrangement.map { it.patternIndex }.distinct().forEach { index ->
            val pattern = song.pattern(index)
            assertEquals(SongBuilder.BLOCK, pattern.leadBarCount)
            assertTrue(pattern.leadNoteCount() > 0)
        }
    }
}
