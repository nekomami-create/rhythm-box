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

    @Test
    fun `primary is exactly the seven diatonic chords, nothing thickened`() {
        val key = MusicKey(0, Scale.MAJOR)
        assertEquals(key.diatonicChords(), ChordPads.primary(key))
        assertEquals(listOf("C", "Dm", "Em", "F", "G", "Am", "Bdim"), ChordPads.primary(key).map { it.name })
    }

    @Test
    fun `withSevenths colours every degree without changing the root`() {
        val key = MusicKey(0, Scale.MAJOR)
        val sevenths = ChordPads.withSevenths(ChordPads.primary(key))
        assertEquals(
            listOf("CM7", "Dm7", "Em7", "FM7", "G7", "Am7", "Bm7-5"),
            sevenths.map { it.name },
        )
        // 根音は動かない。7th は色付けであって別の和音にすり替わるわけではない。
        assertEquals(ChordPads.primary(key).map { it.root }, sevenths.map { it.root })
    }

    @Test
    fun `withSevenths keeps the dominant a plain seventh, not major`() {
        // V を M7 にすると緊張が消えて、主和音へ落ちる力が無くなる
        // （HelpScreen に書いてある理屈と同じ）。単独モードでも同じ規則にする。
        val key = MusicKey(0, Scale.MAJOR)
        val v = ChordPads.withSevenths(ChordPads.primary(key))[4]
        assertEquals(ChordQuality.SEVENTH, v.quality)
    }

    @Test
    fun `withSevenths respects a minor key's own degrees`() {
        // 自然短音階の v は短三和音のまま（導音を借りていない）ので、
        // ドミナントらしい G7 ではなく Gm7... ではなく素直な GM7 になる。
        // 「5 番目だから 7」ではなく「そこにある三和音の性格を保つ」規則。
        val key = MusicKey(9, Scale.NATURAL_MINOR)
        val sevenths = ChordPads.withSevenths(ChordPads.primary(key))
        assertEquals(
            listOf("Am7", "Bm7-5", "CM7", "Dm7", "Em7", "FM7", "GM7"),
            sevenths.map { it.name },
        )
    }

    @Test
    fun `withSevenths only makes the dominant a plain seventh when it is actually major`() {
        // 5 番目の和音は「V」ではなく「その調の 5 番目」。自然短音階では
        // 短三和音なので、7th を足しても m7 のまま（ドミナント 7th にはならない）。
        val minor = ChordPads.withSevenths(ChordPads.primary(MusicKey(9, Scale.NATURAL_MINOR)))[4]
        assertEquals("Em7", minor.name)
        assertEquals(ChordQuality.MINOR_SEVENTH, minor.quality)
    }

    @Test
    fun `withSevenths is idempotent on root and length`() {
        // 表を差し替えても壊れていないことを、いくつもの調で押さえる。
        for (scale in Scale.entries) {
            val key = MusicKey(0, scale)
            val primary = ChordPads.primary(key)
            val sevenths = ChordPads.withSevenths(primary)
            assertEquals(scale.name, 7, sevenths.size)
            assertEquals(scale.name, primary.map { it.root }, sevenths.map { it.root })
        }
    }
}
