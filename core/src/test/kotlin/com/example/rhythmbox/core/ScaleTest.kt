package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ScaleTest {

    @Test
    fun `stacking thirds reproduces the major and minor chords we had before`() {
        // 手で並べていた表を一般の手順に置き換えたので、同じ結果になることを確かめる。
        val major = MusicKey(0, Scale.MAJOR).diatonicChords().map { it.name }
        assertEquals(listOf("C", "Dm", "Em", "F", "G", "Am", "Bdim"), major)
        assertEquals(listOf("I", "ii", "iii", "IV", "V", "vi", "vii°"), MusicKey(0, Scale.MAJOR).degreeLabels())

        val minor = MusicKey(9, Scale.NATURAL_MINOR).diatonicChords().map { it.name }
        assertEquals(listOf("Am", "Bdim", "C", "Dm", "Em", "F", "G"), minor)
        assertEquals(
            listOf("i", "ii°", "III", "iv", "v", "VI", "VII"),
            MusicKey(9, Scale.NATURAL_MINOR).degreeLabels(),
        )
    }

    @Test
    fun `a mode gets the chords of its own scale`() {
        // D ドリアンは白鍵だけ。IV が長三和音になるのがドリアンらしさ。
        val dorian = MusicKey(2, Scale.DORIAN)
        assertEquals(listOf("Dm", "Em", "F", "G", "Am", "Bdim", "C"), dorian.diatonicChords().map { it.name })
        assertEquals("IV", dorian.degreeLabels()[3])
    }

    @Test
    fun `the harmonic minor gets its augmented chord`() {
        val chords = MusicKey(9, Scale.HARMONIC_MINOR).diatonicChords().map { it.name }
        // 導音が上がるので III が増三和音、V が長三和音になる。
        assertTrue("$chords", "Caug" in chords)
        assertTrue("$chords", "E" in chords)
    }

    @Test
    fun `five note scales still get ordinary chords`() {
        // ペンタトニックは三度を積めないので、明暗の近い 7 音音階から借りる。
        val pentatonic = MusicKey(0, Scale.MAJOR_PENTATONIC)
        assertEquals(MusicKey(0, Scale.MAJOR).diatonicChords(), pentatonic.diatonicChords())
        // ただし「調の中の音」は 5 音のまま。
        assertEquals(setOf(0, 2, 4, 7, 9), pentatonic.scalePitches())
    }

    @Test
    fun `every scale has a tonic and stays inside an octave`() {
        for (scale in Scale.entries) {
            assertEquals("$scale", 0, scale.intervals.first())
            assertTrue("$scale", scale.intervals.all { it in 0..11 })
            assertEquals("$scale", scale.intervals.size, scale.intervals.toSet().size)
            assertEquals("$scale", scale.intervals, scale.intervals.sorted())
            assertEquals("$scale", 7, scale.chordSource.intervals.size)
        }
    }

    @Test
    fun `melodies follow the chosen scale, landing on chord tones`() {
        // 通り道は音階の音、拍の頭はコードの構成音、という作りになっている。
        // ブルース音階に C7 を当てると、コードの 3 度（E）は音階の外だが正しく出る。
        val blues = MusicKey(0, Scale.BLUES)
        val chord = Chord(0, ChordQuality.SEVENTH)
        val chordTones = chord.voicing().map { it.mod(12) }.toSet()
        repeat(30) { seed ->
            val bar = MelodyGenerator.generate(chord, blues, Random(seed))
            bar.filterIndexed { step, midi -> Pattern.isNote(midi) && step % 4 != 0 }
                .forEach {
                    assertTrue(
                        "${midiName(it)} はブルース音階にもコードにも無い",
                        it.mod(12) in blues.scalePitches() || it.mod(12) in chordTones,
                    )
                }
        }
    }

    @Test
    fun `changing the scale changes which notes a melody may pass through`() {
        val chord = Chord(0, ChordQuality.MAJOR)
        fun passingNotes(scale: Scale): Set<Int> = (0 until 40).flatMap { seed ->
            MelodyGenerator.generate(chord, MusicKey(0, scale), Random(seed))
                .filterIndexed { step, midi -> Pattern.isNote(midi) && step % 4 != 0 }
                .map { it.mod(12) }
        }.toSet()

        // リディアンは 4 度が上がるので F# を通る。メジャーでは通らない。
        assertTrue(6 in passingNotes(Scale.LYDIAN))
        assertTrue(6 !in passingNotes(Scale.MAJOR))
    }

    @Test
    fun `the piano roll colours follow the chosen scale too`() {
        val dorian = MusicKey(2, Scale.DORIAN)
        // B はドリアンの中（ここがマイナーとの違い）。
        assertEquals(NoteRole.SCALE_TONE, noteRole(71, Chord(2, ChordQuality.MINOR), dorian))
        // 同じ主音のナチュラルマイナーなら B♭ が入るので、B は外。
        assertEquals(NoteRole.OUTSIDE, noteRole(71, Chord(2, ChordQuality.MINOR), MusicKey(2, Scale.NATURAL_MINOR)))
    }
}
