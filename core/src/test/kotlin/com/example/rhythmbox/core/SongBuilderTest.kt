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
    fun `melodies and other patterns are left alone`() {
        val written = base()
            .withPattern(0, base().pattern(0).withLead(0, 72).withLead(4, 74))
            .withPattern(5, Pattern.of("F", "x...x...x...x..."))
        val song = SongBuilder.build(written, Genre.DANCE, key, Random(1))
        assertEquals(72, song.pattern(0).leadAt(0))
        assertEquals(74, song.pattern(0).leadAt(4))
        assertEquals(written.pattern(5), song.pattern(5))
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
