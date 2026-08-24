package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GenreTest {

    private val cMajor = MusicKey(0, minor = false)

    @Test
    fun `the classic progressions spell out the chords everyone knows`() {
        assertEquals(
            listOf("F", "G", "Em", "Am"),
            ProgressionTemplate.ROYAL_ROAD.chords(cMajor).map { it.name },
        )
        assertEquals(
            listOf("Am", "F", "G", "C"),
            ProgressionTemplate.KOMURO.chords(cMajor).map { it.name },
        )
        assertEquals(
            listOf("C", "G", "Am", "Em", "F", "C", "F", "G"),
            ProgressionTemplate.CANON.chords(cMajor).map { it.name },
        )
        assertEquals(
            listOf("C", "G", "Am", "F"),
            ProgressionTemplate.POP_PUNK.chords(cMajor).map { it.name },
        )
    }

    @Test
    fun `templates can ask for their own chord types`() {
        assertEquals(
            listOf("FM7", "E7", "Am7", "Am7"),
            ProgressionTemplate.CITY.chords(cMajor).map { it.name },
        )
        assertEquals(
            listOf("Dm7", "G7", "CM7", "CM7"),
            ProgressionTemplate.TWO_FIVE_ONE.chords(cMajor).map { it.name },
        )
    }

    @Test
    fun `progressions transpose with the key`() {
        val gMajor = MusicKey(7, minor = false)
        assertEquals(
            listOf("C", "D", "Bm", "Em"),
            ProgressionTemplate.ROYAL_ROAD.chords(gMajor).map { it.name },
        )
        val aMinor = MusicKey(9, minor = true)
        // マイナーキーでも度数どおりに並ぶ（i, III, iv...）
        assertEquals(4, ProgressionTemplate.KOMURO.chords(aMinor).size)
    }

    @Test
    fun `filling repeats or trims the template to the bar count`() {
        val template = ProgressionTemplate.ROYAL_ROAD
        assertEquals(
            listOf("F", "G", "Em", "Am", "F", "G", "Em", "Am"),
            template.fill(cMajor, 8).map { it.name },
        )
        assertEquals(listOf("F", "G", "Em"), template.fill(cMajor, 3).map { it.name })
        assertEquals(8, ProgressionTemplate.CANON.fill(cMajor, 8).size)
        assertTrue(template.fill(cMajor, 0).isEmpty())
    }

    @Test
    fun `every genre is set up with something usable`() {
        for (genre in Genre.entries) {
            assertTrue(genre.label.isNotBlank())
            assertTrue("${genre.label} のテンポ", genre.bpmRange.first >= Song.MIN_BPM)
            assertTrue("${genre.label} のテンポ", genre.bpmRange.last <= Song.MAX_BPM)
            assertTrue("${genre.label} のリズム", genre.rhythms.isNotEmpty())
            assertTrue("${genre.label} の進行", genre.progressions.isNotEmpty())
        }
    }

    @Test
    fun `picking from a genre stays inside its own settings`() {
        for (genre in Genre.entries) {
            repeat(30) { seed ->
                val random = Random(seed)
                assertTrue(genre.pickBpm(random) in genre.bpmRange)
                assertTrue(genre.pickRhythm(random) in genre.rhythms)
                assertTrue(genre.pickProgression(random) in genre.progressions)
            }
        }
    }

    @Test
    fun `the genres really are different from each other`() {
        // テンポ帯が全部同じだと、選ぶ意味がない。
        val ranges = Genre.entries.map { it.bpmRange }
        assertEquals(ranges.size, ranges.distinct().size)
        assertTrue(Genre.BALLAD.bpmRange.last < Genre.ROCK.bpmRange.first)
        assertEquals(MelodyDensity.SPARSE, Genre.BALLAD.melodyDensity)
        assertEquals(MelodyDensity.BUSY, Genre.CITY_POP.melodyDensity)
    }
}
