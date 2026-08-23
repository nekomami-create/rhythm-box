package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChordSuggesterTest {

    private val c = Chord(0, ChordQuality.MAJOR)
    private val dm = Chord(2, ChordQuality.MINOR)
    private val em = Chord(4, ChordQuality.MINOR)
    private val f = Chord(5, ChordQuality.MAJOR)
    private val g = Chord(7, ChordQuality.MAJOR)
    private val am = Chord(9, ChordQuality.MINOR)

    @Test
    fun `detects the key of a plain progression`() {
        assertEquals(MusicKey(0, minor = false), ChordSuggester.detectKey(listOf(c, am, f, g)))
        assertEquals(
            MusicKey(5, minor = false),
            ChordSuggester.detectKey(
                listOf(f, Chord(10, ChordQuality.MAJOR), Chord(0, ChordQuality.MAJOR), f),
            ),
        )
    }

    @Test
    fun `detects a minor key when the music sits on the minor tonic`() {
        val key = ChordSuggester.detectKey(listOf(am, f, Chord(0, ChordQuality.MAJOR), g, am))
        assertEquals(MusicKey(9, minor = true), key)
    }

    @Test
    fun `an empty song falls back to C major`() {
        assertEquals(MusicKey(0, minor = false), ChordSuggester.detectKey(emptyList()))
    }

    @Test
    fun `diatonic chords of C major are the familiar seven`() {
        val chords = MusicKey(0, minor = false).diatonicChords()
        assertEquals(listOf("C", "Dm", "Em", "F", "G", "Am", "Bdim"), chords.map { it.name })
        assertEquals(listOf("I", "ii", "iii", "IV", "V", "vi", "vii°"), MusicKey(0, false).degreeLabels())
    }

    @Test
    fun `the dominant resolves to the tonic`() {
        val key = MusicKey(0, minor = false)
        val best = ChordSuggester.suggest(g, key).first()
        assertEquals(c, best.chord)
        assertEquals("I", best.degree)
    }

    @Test
    fun `the tonic suggests the usual places to go`() {
        val suggestions = ChordSuggester.suggest(c, MusicKey(0, minor = false))
        val chords = suggestions.map { it.chord }
        assertTrue("$chords", f in chords)
        assertTrue("$chords", g in chords)
        assertTrue("$chords", am in chords)
        // 今のコードそのものは出さない
        assertFalse(c in chords)
    }

    @Test
    fun `major keys offer the dominant seventh too`() {
        val suggestions = ChordSuggester.suggest(dm, MusicKey(0, minor = false), limit = 8)
        assertTrue(suggestions.any { it.chord == Chord(7, ChordQuality.SEVENTH) && it.degree == "V7" })
    }

    @Test
    fun `minor keys offer the major dominant`() {
        val key = MusicKey(9, minor = true) // Am
        val suggestions = ChordSuggester.suggest(dm, key, limit = 8)
        assertTrue(
            suggestions.map { it.chord }.toString(),
            suggestions.any { it.chord == Chord(4, ChordQuality.MAJOR) }, // E
        )
    }

    @Test
    fun `without a previous chord the common chords come first`() {
        val suggestions = ChordSuggester.suggest(null, MusicKey(0, minor = false), limit = 4)
        assertEquals(c, suggestions.first().chord)
        assertTrue(suggestions.map { it.chord }.containsAll(listOf(c, f, g)))
    }

    @Test
    fun `a chord from outside the key still gets sensible answers`() {
        val outside = Chord(1, ChordQuality.MAJOR) // C# は C メジャーの音階外
        val suggestions = ChordSuggester.suggest(outside, MusicKey(0, minor = false))
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.map { it.chord }.contains(c))
    }

    @Test
    fun `generated progressions stay in key and end on the tonic`() {
        val key = MusicKey(0, minor = false)
        val diatonic = key.diatonicChords() + Chord(7, ChordQuality.SEVENTH)
        repeat(20) { seed ->
            val progression = ChordSuggester.generateProgression(8, key, c, Random(seed))
            assertEquals(8, progression.size)
            assertEquals(c, progression.first())
            assertEquals(c, progression.last())
            assertTrue("$progression", progression.all { it in diatonic })
            // 同じコードが延々と続いたりしない
            assertTrue("$progression", progression.zipWithNext().none { it.first == it.second })
        }
    }

    @Test
    fun `progressions are reproducible from a seed`() {
        val key = MusicKey(9, minor = true)
        val first = ChordSuggester.generateProgression(6, key, random = Random(42))
        val second = ChordSuggester.generateProgression(6, key, random = Random(42))
        assertEquals(first, second)
        assertEquals(6, first.size)
    }
}
