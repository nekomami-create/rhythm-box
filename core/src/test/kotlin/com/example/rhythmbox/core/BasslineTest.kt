package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** ベースの動き方。 */
class BasslineTest {

    private val c = Chord(0, ChordQuality.MAJOR)
    private val g = Chord(7, ChordQuality.MAJOR)
    private val f = Chord(5, ChordQuality.MAJOR)

    private fun note(
        chord: Chord = c,
        next: Chord = g,
        hitIndex: Int = 0,
        last: Boolean = false,
        style: BassStyle,
    ) = Bassline.noteAt(chord, next, hitIndex, last, style)

    @Test
    fun `the plain style never leaves the root`() {
        listOf(0, 1, 2, 3).forEach { index ->
            assertEquals(c.bassMidi(), note(hitIndex = index, style = BassStyle.ROOT))
        }
        assertEquals(c.bassMidi(), note(hitIndex = 3, last = true, style = BassStyle.ROOT))
    }

    @Test
    fun `the downbeat is always the root`() {
        // 頭が 5 度だと、何のコードなのかが分からなくなる。
        BassStyle.entries.forEach { style ->
            assertEquals(style.label, c.bassMidi(), note(hitIndex = 0, style = style))
        }
    }

    @Test
    fun `the fifth style alternates root and fifth`() {
        assertEquals(c.bassMidi(), note(hitIndex = 0, style = BassStyle.FIFTH))
        assertEquals(g.bassMidi(), note(hitIndex = 1, style = BassStyle.FIFTH)) // C の 5 度は G
        assertEquals(c.bassMidi(), note(hitIndex = 2, style = BassStyle.FIFTH))
        assertEquals(g.bassMidi(), note(hitIndex = 3, style = BassStyle.FIFTH))
    }

    @Test
    fun `the fifth stays inside the bass octave`() {
        // 上に取ると調によっては和音の下側とぶつかる。同じオクターブに折り返す。
        for (root in 0 until 12) {
            val chord = Chord(root, ChordQuality.MAJOR)
            val fifth = Bassline.noteAt(chord, chord, 1, false, BassStyle.FIFTH)
            assertTrue("ルート $root の 5 度が $fifth", fifth in Chord.BASS_BASE_MIDI until Chord.BASS_BASE_MIDI + 12)
            assertEquals("ルート $root", (root + 7).mod(12), fifth.mod(12))
        }
    }

    @Test
    fun `walking leads into the next chord from a semitone below`() {
        // G へ向かうので F#。ここだけ次の小節を見て決まる。
        val approach = note(chord = c, next = g, hitIndex = 3, last = true, style = BassStyle.WALK)
        assertEquals((7 - 1).mod(12), approach.mod(12))
        assertNotEquals(c.bassMidi(), approach)
    }

    @Test
    fun `only the last hit of the bar leads into the next chord`() {
        // 途中で入ると、ただ調の外の音が挟まっただけになる。
        val middle = note(chord = c, next = g, hitIndex = 3, last = false, style = BassStyle.WALK)
        assertEquals(g.bassMidi(), middle) // 5 度のまま
    }

    @Test
    fun `no lead-in when the chord does not change`() {
        // 行き先が同じ音では「近づいた」ことにならず、半音下がって戻るだけになる。
        val same = note(chord = c, next = c, hitIndex = 1, last = true, style = BassStyle.WALK)
        assertEquals(g.bassMidi(), same)
    }

    @Test
    fun `a slash chord is followed, both as the note and as the target`() {
        // C/E ならベースは E を弾く。次が F/A なら、そこへ向かう。
        val onE = Chord(0, ChordQuality.MAJOR, bass = 4)
        assertEquals(4, note(chord = onE, hitIndex = 0, style = BassStyle.WALK).mod(12))
        val toA = Chord(5, ChordQuality.MAJOR, bass = 9)
        val approach = note(chord = c, next = toA, hitIndex = 1, last = true, style = BassStyle.WALK)
        assertEquals((9 - 1).mod(12), approach.mod(12))
    }

    @Test
    fun `every note stays in the bass range`() {
        for (root in 0 until 12) {
            for (target in 0 until 12) {
                BassStyle.entries.forEach { style ->
                    for (index in 0..3) {
                        listOf(false, true).forEach { last ->
                            val midi = Bassline.noteAt(
                                Chord(root), Chord(target), index, last, style,
                            )
                            assertTrue(
                                "$style root=$root next=$target で $midi",
                                midi in Chord.BASS_BASE_MIDI until Chord.BASS_BASE_MIDI + 12,
                            )
                        }
                    }
                }
            }
        }
    }

    // --- 打点の数え方 ---------------------------------------------------------

    @Test
    fun `the hit index counts the earlier hits in the row`() {
        val pattern = Pattern.of("A", "................", "................", "x.x...x.........")
        assertEquals(0, pattern.hitIndex(2, 0))
        assertEquals(1, pattern.hitIndex(2, 2))
        assertEquals(2, pattern.hitIndex(2, 6))
        assertEquals(3, pattern.hitIndex(2, 15))
    }

    // --- 曲としての繋がり ------------------------------------------------------

    @Test
    fun `a new song plays the bass the way it always did`() {
        assertEquals(BassStyle.ROOT, Song.newSong("s", "test", 0L).bassStyle)
    }

    @Test
    fun `the last bar leads back to the first`() {
        // 曲はループするので、最後の次は先頭。
        val song = Song("s", "test").copy(
            arrangement = listOf(ArrangementStep(0, 2, listOf(c, f))),
        )
        val plan = PlaybackPlan.arrangement(song)
        assertEquals(f, plan.nextChordAt(0))
        assertEquals(c, plan.nextChordAt(1))
    }
}
