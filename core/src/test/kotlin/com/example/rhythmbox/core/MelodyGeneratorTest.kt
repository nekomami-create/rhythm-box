package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class MelodyGeneratorTest {

    private val cMajor = MusicKey(0, minor = false)
    private val c = Chord(0, ChordQuality.MAJOR)
    private val am = Chord(9, ChordQuality.MINOR)

    private fun notesOf(lead: List<Int>) = lead.filter { it != Pattern.REST }

    @Test
    fun `melodies stay inside the piano roll`() {
        repeat(30) { seed ->
            val lead = MelodyGenerator.generate(c, cMajor, Random(seed))
            assertEquals(STEPS_PER_BAR, lead.size)
            notesOf(lead).forEach {
                assertTrue("$it が音域の外", it in MelodyGenerator.LOWEST_MIDI..MelodyGenerator.HIGHEST_MIDI)
            }
        }
    }

    @Test
    fun `every note belongs to the key`() {
        val scale = setOf(0, 2, 4, 5, 7, 9, 11) // C メジャー
        repeat(30) { seed ->
            val lead = MelodyGenerator.generate(c, cMajor, Random(seed))
            notesOf(lead).forEach {
                assertTrue("${midiName(it)} は調の外", it.mod(12) in scale)
            }
        }
    }

    @Test
    fun `notes on the beat land on chord tones`() {
        repeat(30) { seed ->
            val chord = if (seed % 2 == 0) c else am
            val tones = chord.voicing().map { it.mod(12) }.toSet()
            val lead = MelodyGenerator.generate(chord, cMajor, Random(seed))
            lead.forEachIndexed { step, midi ->
                if (midi != Pattern.REST && step % 4 == 0) {
                    assertTrue(
                        "${midiName(midi)} は ${chord.name} の構成音ではない",
                        midi.mod(12) in tones,
                    )
                }
            }
        }
    }

    @Test
    fun `the last note resolves onto a chord tone`() {
        repeat(30) { seed ->
            val lead = MelodyGenerator.generate(am, cMajor, Random(seed))
            val last = notesOf(lead).last()
            assertTrue(midiName(last), last.mod(12) in am.voicing().map { it.mod(12) }.toSet())
        }
    }

    @Test
    fun `the line moves in singable steps`() {
        repeat(30) { seed ->
            val notes = notesOf(MelodyGenerator.generate(c, cMajor, Random(seed)))
            notes.zipWithNext().forEach { (from, to) ->
                assertTrue("$from → $to は跳びすぎ", abs(to - from) <= 12)
            }
        }
    }

    @Test
    fun `there is always something to hear, but not a wall of notes`() {
        repeat(30) { seed ->
            val count = notesOf(MelodyGenerator.generate(c, cMajor, Random(seed))).size
            assertTrue("音が少なすぎる: $count", count >= 2)
            assertTrue("音が多すぎる: $count", count <= STEPS_PER_BAR)
        }
    }

    @Test
    fun `the same seed always gives the same melody`() {
        assertEquals(
            MelodyGenerator.generate(c, cMajor, Random(7)),
            MelodyGenerator.generate(c, cMajor, Random(7)),
        )
    }

    @Test
    fun `different seeds give different melodies`() {
        val melodies = (0 until 12).map { MelodyGenerator.generate(c, cMajor, Random(it)) }
        assertTrue(melodies.distinct().size >= 8)
    }

    @Test
    fun `a new bar continues from where the last one ended`() {
        repeat(20) { seed ->
            val first = MelodyGenerator.generate(c, cMajor, Random(seed))
            val tail = notesOf(first).last()
            val second = MelodyGenerator.generate(am, cMajor, Random(seed + 100), previous = first)
            val head = notesOf(second).first()
            assertTrue("$tail → $head で飛びすぎ", abs(head - tail) <= 12)
        }
    }

    @Test
    fun `minor keys use the minor scale`() {
        val aMinor = MusicKey(9, minor = true)
        val scale = setOf(9, 11, 0, 2, 4, 5, 7) // A ナチュラルマイナー
        repeat(20) { seed ->
            val lead = MelodyGenerator.generate(am, aMinor, Random(seed))
            notesOf(lead).forEach { assertTrue(midiName(it), it.mod(12) in scale) }
        }
    }
}
