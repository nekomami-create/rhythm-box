package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** セカンダリードミナント・ツーファイブワン・sus4。 */
class HarmonyTest {

    private val cMajor = MusicKey(0, Scale.MAJOR)
    private val aMinor = MusicKey(9, Scale.NATURAL_MINOR)

    private fun names(template: ProgressionTemplate, key: MusicKey) =
        template.chords(key).map { it.name }

    // --- 進行の型 -----------------------------------------------------------

    @Test
    fun `the turnaround borrows a dominant to reach the two chord`() {
        // VI7 は本来 短三和音の vi。長三和音にした瞬間だけ調の外へ出て、次の ii へ落ちる。
        assertEquals(listOf("C", "A7", "Dm7", "G7"), names(ProgressionTemplate.TURNAROUND, cMajor))
        val borrowed = ProgressionTemplate.TURNAROUND.chords(cMajor)[1]
        assertEquals(ChordQuality.SEVENTH, borrowed.quality)
        // A7 の中の C# は C 長調の音階に無い。ここが「調の外へ出る」ということ。
        assertTrue(1 !in cMajor.scalePitches())
    }

    @Test
    fun `the double dominant turns the two chord into a dominant of five`() {
        assertEquals(listOf("C", "F", "D7", "G"), names(ProgressionTemplate.DOUBLE_DOMINANT, cMajor))
        // D7 の 3 度は F#。V（G）の 5 度上から落ちてくる形になっている。
        assertTrue(6 !in cMajor.scalePitches())
    }

    @Test
    fun `the minor two five raises the leading tone`() {
        assertEquals(
            listOf("Bm7-5", "E7", "Am", "Am"),
            names(ProgressionTemplate.MINOR_TWO_FIVE, aMinor),
        )
        // v は本来 Em（短三和音）。E7 にすると G# が入り、主音 A へ半音で上がる。
        assertEquals(ChordQuality.MINOR, aMinor.diatonicChords()[4].quality)
        assertTrue(8 !in aMinor.scalePitches())
    }

    @Test
    fun `templates that force qualities keep their own scale`() {
        // 種類だけ強制して音階を曲任せにすると、短調の曲で別物になる。
        // 主音は曲のまま、音階だけ型に合わせるのが正しい。
        assertEquals(listOf("FM7", "E7", "Am7", "Am7"), names(ProgressionTemplate.CITY, cMajor))
        assertEquals(listOf("DM7", "C#7", "F#m7", "F#m7"), names(ProgressionTemplate.CITY, aMinor))

        assertEquals(listOf("Dm7", "G7", "CM7", "CM7"), names(ProgressionTemplate.TWO_FIVE_ONE, cMajor))
        // 主和音は長三和音のまま（A minor に当てても Am にはならない＝長調の型だから）。
        assertEquals(listOf("Bm7", "E7", "AM7", "AM7"), names(ProgressionTemplate.TWO_FIVE_ONE, aMinor))
    }

    @Test
    fun `every template that forces a quality says which scale it means`() {
        // 音階を持たないまま種類を強制すると、静かに違う和音になる。
        val forcing = listOf(
            ProgressionTemplate.CITY,
            ProgressionTemplate.TWO_FIVE_ONE,
            ProgressionTemplate.MINOR_TWO_FIVE,
            ProgressionTemplate.TURNAROUND,
            ProgressionTemplate.DOUBLE_DOMINANT,
        )
        forcing.forEach { template ->
            assertTrue("${template.name} は種類を強制している", template.qualities.any { it != null })
            assertNotEquals("${template.name} に音階が無い", null, template.scale)
        }
    }

    // --- sus4 ---------------------------------------------------------------

    @Test
    fun `sus4 only lands where the next bar resolves it`() {
        val c = Chord(0, ChordQuality.MAJOR)
        val f = Chord(5, ChordQuality.MAJOR)
        // 同じコードが 2 小節続くのは 1 か所だけ（index 1 と 2）。
        val chords = listOf(f, c, c, f)
        // 必ず置き換わる確率で回しても、置き換わるのはそこだけ。
        val result = Harmony.sprinkleSus4(chords, Random(1), chance = 1.0)
        assertEquals(listOf(f, Chord(0, ChordQuality.SUS4), c, f), result)
    }

    @Test
    fun `a chord that never repeats is left alone`() {
        val chords = listOf(
            Chord(0, ChordQuality.MAJOR),
            Chord(7, ChordQuality.MAJOR),
            Chord(9, ChordQuality.MINOR),
            Chord(5, ChordQuality.MAJOR),
        )
        assertEquals(chords, Harmony.sprinkleSus4(chords, Random(1), chance = 1.0))
    }

    @Test
    fun `the first bar keeps its third`() {
        // 先頭が宙吊りだと、何の調で始まったのか分からない。
        val c = Chord(0, ChordQuality.MAJOR)
        val result = Harmony.sprinkleSus4(listOf(c, c, c), Random(1), chance = 1.0)
        assertEquals(c, result.first())
    }

    @Test
    fun `the seventh keeps its seventh when it is suspended`() {
        val g7 = Chord(7, ChordQuality.SEVENTH)
        val result = Harmony.sprinkleSus4(listOf(Chord(0), g7, g7, g7), Random(1), chance = 1.0)
        assertEquals(ChordQuality.SEVENTH_SUS4, result[1].quality)
        assertEquals(listOf(0, 5, 7, 10), ChordQuality.SEVENTH_SUS4.intervals)
    }

    @Test
    fun `chords with no third to suspend are skipped`() {
        assertNull(Harmony.suspendedOf(ChordQuality.SUS4))
        assertNull(Harmony.suspendedOf(ChordQuality.SUS2))
        assertNull(Harmony.suspendedOf(ChordQuality.DIMINISHED))
        assertNull(Harmony.suspendedOf(ChordQuality.MAJOR_SEVENTH))
        assertEquals(ChordQuality.SUS4, Harmony.suspendedOf(ChordQuality.MINOR))
    }

    @Test
    fun `sus4 never falls on two bars in a row`() {
        val c = Chord(0, ChordQuality.MAJOR)
        val chords = List(8) { c }
        val result = Harmony.sprinkleSus4(chords, Random(3), chance = 1.0)
        val suspended = result.indices.filter { result[it].quality == ChordQuality.SUS4 }
        assertTrue("宙吊りが続いている: $suspended", suspended.zipWithNext().none { it.second - it.first == 1 })
        assertTrue("1 つも置かれていない", suspended.isNotEmpty())
    }

    @Test
    fun `the same seed gives the same chords`() {
        val chords = List(8) { Chord(it % 2 * 5, ChordQuality.MAJOR) }
        assertEquals(
            Harmony.sprinkleSus4(chords, Random(7)),
            Harmony.sprinkleSus4(chords, Random(7)),
        )
    }
}
