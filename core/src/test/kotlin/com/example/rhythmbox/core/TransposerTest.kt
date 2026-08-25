package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransposerTest {

    private fun songWithLead(vararg notes: Int): Song {
        val lead = MutableList(STEPS_PER_BAR) { Pattern.REST }
        notes.forEachIndexed { index, midi -> lead[index] = midi }
        return Song("s", "test")
            .withPattern(0, Pattern.empty("A").withLeads(listOf(lead)))
            .withPatternChord(0, Chord(0, ChordQuality.MAJOR))
    }

    @Test
    fun `moving up two semitones moves the chords and the melody together`() {
        val song = songWithLead(60, 64, 67)
        val moved = Transposer.transpose(song, 2)

        assertEquals(Chord(2, ChordQuality.MAJOR), moved.patternChord(0))
        assertEquals(62, moved.pattern(0).leadAt(0, 0))
        assertEquals(66, moved.pattern(0).leadAt(0, 1))
        assertEquals(69, moved.pattern(0).leadAt(0, 2))
    }

    @Test
    fun `the chord type never changes`() {
        val song = Song("s", "test").withPatternChord(0, Chord(9, ChordQuality.MINOR_SEVENTH))
        val moved = Transposer.transpose(song, 5)

        assertEquals(ChordQuality.MINOR_SEVENTH, moved.patternChord(0).quality)
        assertEquals(2, moved.patternChord(0).root)
    }

    @Test
    fun `the arrangement moves too`() {
        val song = Song("s", "test").copy(
            arrangement = listOf(
                ArrangementStep(0, 2, listOf(Chord(0, ChordQuality.MAJOR), Chord(7, ChordQuality.MAJOR))),
            ),
        )
        val moved = Transposer.transpose(song, 3)

        assertEquals(listOf(Chord(3, ChordQuality.MAJOR), Chord(10, ChordQuality.MAJOR)), moved.arrangement[0].chords)
    }

    @Test
    fun `a melody that would run off the top is folded back into range`() {
        // C6 は書ける一番上。ここから 5 半音上げるとはみ出す。
        val song = songWithLead(84, 83)
        val moved = Transposer.transpose(song, 5)

        val notes = moved.pattern(0).leadBars.flatten().filter { Pattern.isNote(it) }
        assertTrue("$notes", notes.all { it in 60..84 })
        // 折り返してもコードは指定どおり動いている。
        assertEquals(5, moved.patternChord(0).root)
        // 音どうしの間隔（旋律の形）は変わらない。
        assertEquals(84 - 83, notes[0] - notes[1])
    }

    @Test
    fun `a melody that would fall off the bottom is folded back too`() {
        val song = songWithLead(60, 62)
        val moved = Transposer.transpose(song, -5)

        val notes = moved.pattern(0).leadBars.flatten().filter { Pattern.isNote(it) }
        assertTrue("$notes", notes.all { it in 60..84 })
        assertEquals(62 - 60, notes[1] - notes[0])
    }

    @Test
    fun `moving by nothing changes nothing`() {
        val song = songWithLead(60, 64)
        assertEquals(song, Transposer.transpose(song, 0))
    }

    @Test
    fun `ties survive the move`() {
        val lead = MutableList(STEPS_PER_BAR) { Pattern.REST }
        lead[0] = 72
        lead[1] = Pattern.TIE
        lead[2] = Pattern.TIE
        val song = Song("s", "test").withPattern(0, Pattern.empty("A").withLeads(listOf(lead)))
        val moved = Transposer.transpose(song, 4)

        assertEquals(76, moved.pattern(0).leadAt(0, 0))
        assertEquals(Pattern.TIE, moved.pattern(0).leadAt(0, 1))
        assertEquals(2, moved.pattern(0).tieRun(0, 0))
    }
}
