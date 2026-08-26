package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordPadsTest {

    @Test
    fun `the first seven pads are the chords of the key, in order`() {
        val key = MusicKey(0, Scale.MAJOR)
        val pads = ChordPads.forKey(key)

        assertEquals(ChordPads.COUNT, pads.size)
        assertEquals(key.diatonicChords(), pads.take(7))
        assertEquals(listOf("C", "Dm", "Em", "F", "G", "Am", "Bdim"), pads.take(7).map { it.name })
    }

    @Test
    fun `the rest are the same chords made thicker`() {
        val pads = ChordPads.forKey(MusicKey(0, Scale.MAJOR)).drop(7)
        // V7 は終止感の芯なので必ず入れる。
        assertTrue("$pads", pads.any { it.name == "G7" })
        assertTrue("$pads", pads.any { it.name == "FM7" })
        assertTrue("$pads", pads.any { it.name == "Dm7" })
    }

    @Test
    fun `a minor key keeps the character of each degree`() {
        val pads = ChordPads.forKey(MusicKey(9, Scale.NATURAL_MINOR))
        // 三和音がマイナーなら 7th もマイナー 7th。長三和音に化けない。
        assertTrue("$pads", pads.any { it.name == "Am7" })
        assertTrue("$pads", pads.none { it.name == "AM7" })
    }

    @Test
    fun `every pad stays inside the key`() {
        for (scale in Scale.entries) {
            val key = MusicKey(0, scale)
            val allowed = key.diatonicChords().map { it.root }.toSet()
            ChordPads.forKey(key).forEach {
                assertTrue("$scale ${it.name}", it.root in allowed)
            }
        }
    }

    @Test
    fun `pads follow the key until you change one`() {
        val key = MusicKey(0, Scale.MAJOR)
        // 何も決めていなければ調から作る。
        assertEquals(ChordPads.forKey(key), ChordPads.resolve(emptyList(), key))

        // 1 つでも決めたら、そこは尊重して残りは既定で埋める。
        val mine = List(ChordPads.COUNT) { ChordPads.forKey(key)[it] }
            .toMutableList()
            .also { it[3] = Chord(7, ChordQuality.SEVENTH) }
        val resolved = ChordPads.resolve(mine, key)
        assertEquals("G7", resolved[3].name)
        assertEquals(ChordPads.forKey(key)[0], resolved[0])
    }

    @Test
    fun `a short saved list is filled up to twelve`() {
        val key = MusicKey(0, Scale.MAJOR)
        val resolved = ChordPads.resolve(listOf(Chord(5, ChordQuality.MAJOR)), key)
        assertEquals(ChordPads.COUNT, resolved.size)
        assertEquals("F", resolved.first().name)
    }
}
