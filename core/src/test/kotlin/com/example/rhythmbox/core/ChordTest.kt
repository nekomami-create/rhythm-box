package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordTest {

    @Test
    fun `names read the way they are written on a lead sheet`() {
        assertEquals("C", Chord(0, ChordQuality.MAJOR).name)
        assertEquals("Am", Chord(9, ChordQuality.MINOR).name)
        assertEquals("G7", Chord(7, ChordQuality.SEVENTH).name)
        assertEquals("FM7", Chord(5, ChordQuality.MAJOR_SEVENTH).name)
        assertEquals("D#sus4", Chord(3, ChordQuality.SUS4).name)
    }

    @Test
    fun `voicing spells the chord tones`() {
        assertEquals(listOf(60, 64, 67), Chord(0, ChordQuality.MAJOR).voicing())
        assertEquals(listOf(62, 65, 69), Chord(2, ChordQuality.MINOR).voicing())
        assertEquals(listOf(60, 64, 67, 70), Chord(0, ChordQuality.SEVENTH).voicing())
    }

    @Test
    fun `high roots drop an octave so chords do not jump around`() {
        // F# 以上は 1 オクターブ下げる。C4 を挟んだ狭い範囲に収まる。
        val g = Chord(7, ChordQuality.MAJOR).voicing()
        assertEquals(listOf(55, 59, 62), g)
        val all = (0..11).flatMap { Chord(it, ChordQuality.MAJOR).voicing() }
        assertTrue("最低音 ${all.min()}", all.min() >= 54)
        assertTrue("最高音 ${all.max()}", all.max() <= 72)
    }

    @Test
    fun `bass plays the root two octaves down`() {
        assertEquals(36, Chord(0, ChordQuality.MAJOR).bassMidi()) // C2
        assertEquals(45, Chord(9, ChordQuality.MINOR).bassMidi()) // A2
    }

    @Test
    fun `chords can be parsed back from their names`() {
        for (root in 0..11) {
            for (quality in ChordQuality.entries) {
                val chord = Chord(root, quality)
                assertEquals(chord, Chord.of(chord.name))
            }
        }
        assertNull(Chord.of("H"))
        assertNull(Chord.of("Cxyz"))
    }

    @Test
    fun `midi names are readable`() {
        assertEquals("C4", midiName(60))
        assertEquals("A4", midiName(69))
        assertEquals("C2", midiName(36))
    }
}
