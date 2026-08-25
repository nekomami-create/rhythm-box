package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteRoleTest {

    private val cMajorKey = MusicKey(0, minor = false)
    private val c = Chord(0, ChordQuality.MAJOR)

    @Test
    fun `chord tones are told apart from the rest of the scale`() {
        // C メジャーの構成音は C E G。
        assertEquals(NoteRole.CHORD_TONE, noteRole(60, c, cMajorKey)) // C
        assertEquals(NoteRole.CHORD_TONE, noteRole(64, c, cMajorKey)) // E
        assertEquals(NoteRole.CHORD_TONE, noteRole(67, c, cMajorKey)) // G
        // D と A は調の中だがコードの外。
        assertEquals(NoteRole.SCALE_TONE, noteRole(62, c, cMajorKey))
        assertEquals(NoteRole.SCALE_TONE, noteRole(69, c, cMajorKey))
        // C# は調の外。
        assertEquals(NoteRole.OUTSIDE, noteRole(61, c, cMajorKey))
    }

    @Test
    fun `the octave does not matter`() {
        for (octave in 0..3) {
            assertEquals(NoteRole.CHORD_TONE, noteRole(60 + octave * 12, c, cMajorKey))
        }
    }

    @Test
    fun `chord tones are labelled by their degree`() {
        assertEquals("R", chordDegreeLabel(60, c))
        assertEquals("3", chordDegreeLabel(64, c))
        assertEquals("5", chordDegreeLabel(67, c))
        assertNull(chordDegreeLabel(62, c))
    }

    @Test
    fun `minor and seventh chords get their own labels`() {
        val am7 = Chord(9, ChordQuality.MINOR_SEVENTH)
        assertEquals("R", chordDegreeLabel(69, am7)) // A
        assertEquals("♭3", chordDegreeLabel(72, am7)) // C
        assertEquals("5", chordDegreeLabel(76, am7)) // E
        assertEquals("7", chordDegreeLabel(79, am7)) // G
    }

    @Test
    fun `a minor key uses its own scale`() {
        val aMinor = MusicKey(9, minor = true)
        // A マイナーの音階に C# は無い。
        assertEquals(NoteRole.OUTSIDE, noteRole(61, Chord(9, ChordQuality.MINOR), aMinor))
        assertEquals(NoteRole.SCALE_TONE, noteRole(62, Chord(9, ChordQuality.MINOR), aMinor))
    }
}
