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
                val song = SongBuilder.build(base(), genre, key, Random(seed))
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
                val song = SongBuilder.build(base(), genre, key, Random(seed))
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
            val song = SongBuilder.build(base(), Genre.JPOP, key, Random(seed))
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
        val song = SongBuilder.build(base(), Genre.ROCK, key, Random(3))
        val bars = PlaybackPlan.arrangement(song).bars.map { it.chord }
        assertEquals(bars[0], song.patternChord(0))
        assertEquals(bars[4], song.patternChord(1))
    }

    @Test
    fun `the two halves are not identical`() {
        // 前半と後半が毎回同じだと、8 小節にする意味がない。
        val different = (0 until 20).count { seed ->
            val song = SongBuilder.build(base(), Genre.JPOP, key, Random(seed))
            song.pattern(0).rows != song.pattern(1).rows
        }
        assertTrue("20 回中 $different 回しか違わない", different >= 18)
    }

    @Test
    fun `each bar of a block gets its own melody`() {
        // ドラムは 4 小節同じでも、旋律は小節ごとに変える必要がある。
        repeat(10) { seed ->
            val song = SongBuilder.build(base(), Genre.JPOP, key, Random(seed))
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
            val song = SongBuilder.build(base(), Genre.JPOP, key, Random(seed))
            val block = song.arrangement.first()
            val pattern = song.pattern(block.patternIndex)
            block.chords.forEachIndexed { bar, chord ->
                val tones = chord.voicing().map { it.mod(12) }.toSet()
                for (step in 0 until STEPS_PER_BAR step 4) {
                    val midi = pattern.leadAt(bar, step)
                    if (midi != Pattern.REST) {
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
        val song = SongBuilder.build(written, Genre.DANCE, key, Random(1), withMelody = false)
        assertEquals(72, song.pattern(0).leadAt(0, 0))
        assertEquals(74, song.pattern(0).leadAt(0, 4))
        assertEquals(written.pattern(5), song.pattern(5))

        // 旋律も作る場合でも、使っていないパターンには触らない。
        val withMelody = SongBuilder.build(written, Genre.DANCE, key, Random(1))
        assertEquals(written.pattern(5), withMelody.pattern(5))
    }

    @Test
    fun `the same seed builds the same song`() {
        assertEquals(
            SongBuilder.build(base(), Genre.CITY_POP, key, Random(9)),
            SongBuilder.build(base(), Genre.CITY_POP, key, Random(9)),
        )
        assertNotEquals(
            SongBuilder.build(base(), Genre.CITY_POP, key, Random(9)),
            SongBuilder.build(base(), Genre.CITY_POP, key, Random(10)),
        )
    }
}
