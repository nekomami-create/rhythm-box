package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** コードクルーザーが出す種。 */
class ChordCruiserTest {

    private val cMajor = MusicKey(0, Scale.MAJOR)
    private val aMinor = MusicKey(9, Scale.NATURAL_MINOR)

    @Test
    fun `every seed is exactly as long as asked`() {
        listOf(1, 2, 4, 8).forEach { bars ->
            ChordCruiser.seeds(cMajor, bars, Random(1)).forEach { seed ->
                assertEquals("${seed.name} が $bars 小節ぶんでない", bars, seed.chords.size)
            }
        }
    }

    @Test
    fun `seeds carry a name you can tell apart`() {
        val seeds = ChordCruiser.seeds(cMajor, ChordCruiser.BARS, Random(1))
        assertTrue("種が少なすぎる", seeds.size >= 10)
        assertEquals("名前が重なっている", seeds.size, seeds.map { it.name }.distinct().size)
        assertTrue(seeds.none { it.name.isBlank() })
    }

    @Test
    fun `the templates come out in the song's key`() {
        // C 長調なら C から、A 短調なら A から始まる型がある。
        val major = ChordCruiser.templates(cMajor).first { it.name == "I-V-vi-IV" }
        assertEquals(listOf("C", "G", "Am", "F"), major.chords.map { it.name })

        val minor = ChordCruiser.templates(aMinor).first { it.name == "i-♭VII-♭VI-♭VII" }
        assertEquals(listOf("Am", "G", "F", "G"), minor.chords.map { it.name })
    }

    @Test
    fun `an eight bar template gives its first half when four bars are asked`() {
        val canon = ChordCruiser.templates(cMajor, 4).first { it.name == "カノン進行" }
        val full = ProgressionTemplate.CANON.chords(cMajor)
        assertEquals(full.take(4), canon.chords)
    }

    @Test
    fun `the generated seeds land on the tonic`() {
        // 起承転結の「結」で帰ってくるので、4 小節でも終わりは主和音になる。
        ChordCruiser.generated(cMajor, 4, count = 8, random = Random(2)).forEach { seed ->
            assertEquals("${seed.name}: ${seed.chords.map { it.name }}", 0, seed.chords.last().root)
        }
    }

    @Test
    fun `the same seed number gives the same progression`() {
        assertEquals(
            ChordCruiser.seeds(cMajor, 4, Random(5)).map { it.chords },
            ChordCruiser.seeds(cMajor, 4, Random(5)).map { it.chords },
        )
    }

    @Test
    fun `fitting stretches a short progression by holding the last chord`() {
        val short = listOf(Chord(0), Chord(7))
        assertEquals(
            listOf(Chord(0), Chord(7), Chord(7), Chord(7)),
            ChordCruiser.fit(short, 4),
        )
    }

    @Test
    fun `fitting trims a long progression`() {
        val long = List(8) { Chord(it) }
        assertEquals(List(4) { Chord(it) }, ChordCruiser.fit(long, 4))
    }

    @Test
    fun `fitting an empty progression still gives something playable`() {
        assertEquals(List(4) { Chord() }, ChordCruiser.fit(emptyList(), 4))
        assertEquals(emptyList<Chord>(), ChordCruiser.fit(listOf(Chord(0)), 0))
    }

    @Test
    fun `a cruised progression can be played as a plan of its own`() {
        // 差し込む前に聴けることが肝なので、そのままプランに組めることを押さえる。
        val song = Song.newSong("s", "test", 0L)
        val chords = ChordCruiser.templates(cMajor).first().chords
        val plan = PlaybackPlan(song.patterns, chords.mapIndexed { bar, chord -> Bar(0, chord, bar) })
        assertEquals(4, plan.barCount)
        assertEquals(chords, (0 until plan.barCount).map { plan.chordAt(it) })
        // 声部を解いた結果も足せる（試聴と本番で音が変わらないように）。
        val voicings = Voicing.lead(chords)
        val led = PlaybackPlan(song.patterns, chords.mapIndexed { bar, chord -> Bar(0, chord, bar, voicings[bar]) })
        assertEquals(voicings, (0 until led.barCount).map { led.voicingAt(it) })
    }
}
