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

    @Test
    fun `block style keeps playing the whole chord`() {
        val voicing = listOf(60, 64, 67)
        repeat(5) { index ->
            assertEquals(voicing, ChordStyle.BLOCK.notesAt(voicing, index))
        }
    }

    @Test
    fun `up and down walk through the chord one note at a time`() {
        val voicing = listOf(60, 64, 67)
        assertEquals(listOf(60), ChordStyle.UP.notesAt(voicing, 0))
        assertEquals(listOf(64), ChordStyle.UP.notesAt(voicing, 1))
        assertEquals(listOf(67), ChordStyle.UP.notesAt(voicing, 2))
        assertEquals(listOf(60), ChordStyle.UP.notesAt(voicing, 3))

        assertEquals(listOf(67), ChordStyle.DOWN.notesAt(voicing, 0))
        assertEquals(listOf(60), ChordStyle.DOWN.notesAt(voicing, 2))
    }

    @Test
    fun `up and down turns around without repeating the ends`() {
        val voicing = listOf(60, 64, 67)
        val played = (0 until 8).map { ChordStyle.UP_DOWN.notesAt(voicing, it).single() }
        assertEquals(listOf(60, 64, 67, 64, 60, 64, 67, 64), played)
    }

    @Test
    fun `an arpeggio of one note does not get stuck`() {
        val voicing = listOf(60)
        repeat(5) { index ->
            assertEquals(listOf(60), ChordStyle.UP_DOWN.notesAt(voicing, index))
        }
        assertEquals(emptyList<Int>(), ChordStyle.UP.notesAt(emptyList(), 0))
    }

    @Test
    fun `the added chord types sound the notes their name says`() {
        assertEquals(listOf(0, 2, 7), ChordQuality.SUS2.intervals)
        assertEquals(listOf(0, 4, 7, 9), ChordQuality.SIXTH.intervals)
        assertEquals(listOf(0, 3, 6, 10), ChordQuality.HALF_DIMINISHED.intervals)
        // 9th は 1 オクターブ上の 2 度。詰めると濁るので上に置く。
        assertTrue(ChordQuality.ADD_NINTH.intervals.last() > 12)
        assertEquals(5, ChordQuality.MAJOR_NINTH.intervals.size)
    }

    @Test
    fun `every chord type starts on its root and has no repeated notes`() {
        for (quality in ChordQuality.entries) {
            assertEquals("$quality", 0, quality.intervals.first())
            assertEquals("$quality", quality.intervals, quality.intervals.sorted())
            val classes = quality.intervals.map { it.mod(12) }
            assertEquals("$quality", classes.size, classes.toSet().size)
        }
    }

    @Test
    fun `every chord type has its own name`() {
        val suffixes = ChordQuality.entries.map { it.suffix }
        assertEquals(suffixes.size, suffixes.toSet().size)
    }

    @Test
    fun `an on-chord keeps the harmony but moves the bass`() {
        val plain = Chord(0, ChordQuality.MAJOR)
        val onE = Chord(0, ChordQuality.MAJOR, bass = 4)

        assertEquals("C/E", onE.name)
        // 上に乗る和音は変わらない。
        assertEquals(plain.voicing(), onE.voicing())
        // 弾くベースの音だけが変わる。
        assertEquals(plain.bassMidi() + 4, onE.bassMidi())
    }

    @Test
    fun `an on-chord survives being written down and read back`() {
        assertEquals(Chord(0, ChordQuality.MAJOR, bass = 4), Chord.of("C/E"))
        assertEquals(Chord(5, ChordQuality.MAJOR_SEVENTH, bass = 0), Chord.of("FM7/C"))
        assertEquals(Chord(2, ChordQuality.MINOR), Chord.of("Dm"))
        assertNull(Chord.of("C/H"))
    }

    @Test
    fun `moving the key moves the bass of an on-chord too`() {
        val moved = Chord(0, ChordQuality.MAJOR, bass = 4).transposed(2)
        assertEquals(Chord(2, ChordQuality.MAJOR, bass = 6), moved)
        assertEquals("D/F#", moved.name)
    }
}
