package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChordSuggesterTest {

    private val c = Chord(0, ChordQuality.MAJOR)
    private val dm = Chord(2, ChordQuality.MINOR)
    private val em = Chord(4, ChordQuality.MINOR)
    private val f = Chord(5, ChordQuality.MAJOR)
    private val g = Chord(7, ChordQuality.MAJOR)
    private val am = Chord(9, ChordQuality.MINOR)

    @Test
    fun `detects the key of a plain progression`() {
        assertEquals(MusicKey(0, minor = false), ChordSuggester.detectKey(listOf(c, am, f, g)))
        assertEquals(
            MusicKey(5, minor = false),
            ChordSuggester.detectKey(
                listOf(f, Chord(10, ChordQuality.MAJOR), Chord(0, ChordQuality.MAJOR), f),
            ),
        )
    }

    @Test
    fun `detects a minor key when the music sits on the minor tonic`() {
        val key = ChordSuggester.detectKey(listOf(am, f, Chord(0, ChordQuality.MAJOR), g, am))
        assertEquals(MusicKey(9, minor = true), key)
    }

    @Test
    fun `an empty song falls back to C major`() {
        assertEquals(MusicKey(0, minor = false), ChordSuggester.detectKey(emptyList()))
    }

    @Test
    fun `diatonic chords of C major are the familiar seven`() {
        val chords = MusicKey(0, minor = false).diatonicChords()
        assertEquals(listOf("C", "Dm", "Em", "F", "G", "Am", "Bdim"), chords.map { it.name })
        assertEquals(listOf("I", "ii", "iii", "IV", "V", "vi", "vii°"), MusicKey(0, false).degreeLabels())
    }

    @Test
    fun `the dominant resolves to the tonic`() {
        val key = MusicKey(0, minor = false)
        val best = ChordSuggester.suggest(g, key).first()
        assertEquals(c, best.chord)
        assertEquals("I", best.degree)
    }

    @Test
    fun `the tonic suggests the usual places to go`() {
        val suggestions = ChordSuggester.suggest(c, MusicKey(0, minor = false))
        val chords = suggestions.map { it.chord }
        assertTrue("$chords", f in chords)
        assertTrue("$chords", g in chords)
        assertTrue("$chords", am in chords)
        // 今のコードそのものは出さない
        assertFalse(c in chords)
    }

    @Test
    fun `major keys offer the dominant seventh too`() {
        val suggestions = ChordSuggester.suggest(dm, MusicKey(0, minor = false), limit = 8)
        assertTrue(suggestions.any { it.chord == Chord(7, ChordQuality.SEVENTH) && it.degree == "V7" })
    }

    @Test
    fun `minor keys offer the major dominant`() {
        val key = MusicKey(9, minor = true) // Am
        val suggestions = ChordSuggester.suggest(dm, key, limit = 8)
        assertTrue(
            suggestions.map { it.chord }.toString(),
            suggestions.any { it.chord == Chord(4, ChordQuality.MAJOR) }, // E
        )
    }

    @Test
    fun `without a previous chord the common chords come first`() {
        val suggestions = ChordSuggester.suggest(null, MusicKey(0, minor = false), limit = 4)
        assertEquals(c, suggestions.first().chord)
        assertTrue(suggestions.map { it.chord }.containsAll(listOf(c, f, g)))
    }

    @Test
    fun `a chord from outside the key still gets sensible answers`() {
        val outside = Chord(1, ChordQuality.MAJOR) // C# は C メジャーの音階外
        val suggestions = ChordSuggester.suggest(outside, MusicKey(0, minor = false))
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.map { it.chord }.contains(c))
    }

    @Test
    fun `generated progressions stay in key and end on the tonic`() {
        val key = MusicKey(0, minor = false)
        val diatonic = key.diatonicChords() + Chord(7, ChordQuality.SEVENTH)
        repeat(20) { seed ->
            val progression = ChordSuggester.generateStory(8, key, c, Random(seed))
            assertEquals(8, progression.size)
            assertEquals(c, progression.first())
            assertEquals(c, progression.last())
            assertTrue("$progression", progression.all { it in diatonic })
            // 同じコードが延々と続いたりしない
            assertTrue("$progression", progression.zipWithNext().none { it.first == it.second })
        }
    }

    @Test
    fun `progressions are reproducible from a seed`() {
        val key = MusicKey(9, minor = true)
        val first = ChordSuggester.generateStory(6, key, random = Random(42))
        val second = ChordSuggester.generateStory(6, key, random = Random(42))
        assertEquals(first, second)
        assertEquals(6, first.size)
    }

    // --- 起承転結 -----------------------------------------------------------

    @Test
    fun `bars are shared out between the four roles`() {
        val roles = ChordSuggester.SectionRole.entries
        for (bars in 1..32) {
            val sections = ChordSuggester.sections(bars)
            assertEquals("$bars 小節", bars, sections.sumOf { it.second.count() })
            // 役割の順番は 起 → 承 → 転 → 結 のまま
            val order = sections.map { roles.indexOf(it.first) }
            assertTrue("$bars 小節: $order", order.zipWithNext().all { it.first < it.second })
            // 終わりは必ず「結」
            assertEquals(ChordSuggester.SectionRole.CONCLUSION, sections.last().first)
            // 区間が飛んだり重なったりしない
            var next = 0
            sections.forEach { (_, range) ->
                assertEquals(next, range.first)
                next = range.last + 1
            }
        }
        assertEquals(
            listOf(2, 2, 2, 2),
            ChordSuggester.sections(8).map { it.second.count() },
        )
        assertEquals(listOf(1, 1, 1, 1), ChordSuggester.sections(4).map { it.second.count() })
        assertEquals(
            listOf(ChordSuggester.SectionRole.CONCLUSION),
            ChordSuggester.sections(1).map { it.first },
        )
    }

    @Test
    fun `a cadence always lands on the tonic`() {
        for (key in listOf(MusicKey(0, false), MusicKey(9, true), MusicKey(5, false))) {
            val tonic = key.diatonicChords().first()
            for (length in 1..8) {
                repeat(10) { seed ->
                    val ending = ChordSuggester.cadence(length, key, random = Random(seed))
                    assertEquals(length, ending.size)
                    assertEquals("$key $ending", tonic, ending.last())
                }
            }
        }
    }

    @Test
    fun `a cadence pulls home through the dominant`() {
        // 3 小節以上の終止形は、最後の 1 つ前が必ずドミナント（V）。ここが終わった感じの芯。
        for (key in listOf(MusicKey(0, false), MusicKey(9, true), MusicKey(5, false))) {
            val dominantRoot = (key.tonic + 7).mod(12)
            for (length in 3..6) {
                repeat(15) { seed ->
                    val ending = ChordSuggester.cadence(length, key, random = Random(seed))
                    assertEquals(
                        "${key.name} $ending",
                        dominantRoot,
                        ending[ending.lastIndex - 1].root,
                    )
                }
            }
        }
    }

    @Test
    fun `two bar endings are either a full close or a plagal one`() {
        val key = MusicKey(0, minor = false)
        val endings = (0 until 20).map { ChordSuggester.cadence(2, key, random = Random(it)) }
        assertTrue(endings.all { it.first().root == g.root || it.first().root == f.root })
        assertTrue("全終止 (V - I) が出ていない", endings.any { it.first().root == g.root })
    }

    @Test
    fun `minor keys borrow the major dominant to sound finished`() {
        val key = MusicKey(9, minor = true) // Am
        val e = Chord(4, ChordQuality.MAJOR)
        val endings = (0 until 30).map { ChordSuggester.cadence(3, key, random = Random(it)) }
        assertTrue("和声的短音階の V が一度も出ていない", endings.any { e in it })
        assertTrue(endings.all { it.last() == Chord(9, ChordQuality.MINOR) })
    }

    @Test
    fun `the turn section moves away from the tonic`() {
        val key = MusicKey(0, minor = false)
        // 転（3 番目の区間）に主和音が居座らないこと。
        val turnRange = ChordSuggester.sections(8)[2].second
        val tonicCount = (0 until 40).count { seed ->
            val progression = ChordSuggester.generateStory(8, key, c, Random(seed))
            turnRange.any { progression[it] == c }
        }
        assertTrue("転に主和音が出すぎ: $tonicCount / 40", tonicCount < 8)
    }

    @Test
    fun `a story opens on the tonic and closes on it`() {
        for (key in listOf(MusicKey(0, false), MusicKey(9, true))) {
            val tonic = key.diatonicChords().first()
            repeat(20) { seed ->
                val progression = ChordSuggester.generateStory(8, key, random = Random(seed))
                assertEquals(tonic, progression.first())
                assertEquals(tonic, progression.last())
            }
        }
    }

    @Test
    fun `short songs still get an ending`() {
        val key = MusicKey(0, minor = false)
        for (bars in 1..4) {
            val progression = ChordSuggester.generateStory(bars, key, random = Random(1))
            assertEquals(bars, progression.size)
            assertEquals(c, progression.last())
        }
    }

    @Test
    fun `suggestions fit between the chords on both sides`() {
        val key = MusicKey(0, minor = false)

        // C から C へ戻る 1 小節。行って帰ってこられる V が最有力。
        val between = ChordSuggester.suggest(c, key, next = c)
        assertEquals(g, between.first().chord)

        // C から G へ向かう 1 小節。C - F - G の流れが自然。
        val towardG = ChordSuggester.suggest(c, key, next = g)
        assertEquals(f, towardG.first().chord)

        // ii - V - I の真ん中
        val cadence = ChordSuggester.suggest(dm, key, next = c)
        assertTrue(cadence.first().chord.name, cadence.first().chord.root == g.root)
    }

    @Test
    fun `the chords on both sides are not suggested again`() {
        val suggestions = ChordSuggester.suggest(f, MusicKey(0, minor = false), next = g, limit = 8)
        assertFalse(f in suggestions.map { it.chord })
        assertFalse(g in suggestions.map { it.chord })
    }

    @Test
    fun `only knowing what comes next is still useful`() {
        // 先頭の小節を選び直すとき（前が無く、次だけある）。
        val suggestions = ChordSuggester.suggest(null, MusicKey(0, minor = false), next = c)
        assertTrue(suggestions.map { it.chord }.contains(g))
        assertTrue("$suggestions", suggestions.first().chord in listOf(g, f, em))
    }

    @Test
    fun `picking one chord stays in the key and avoids the neighbours`() {
        val key = MusicKey(0, minor = false)
        val previous = Chord(0, ChordQuality.MAJOR) // C
        val next = Chord(7, ChordQuality.MAJOR) // G
        val diatonic = key.diatonicChords().map { it.root.mod(12) }.toSet()

        repeat(200) { seed ->
            val picked = ChordSuggester.pickOne(previous, key, next, Random(seed))
            assertNotNull(picked)
            assertTrue("$picked", picked!!.root.mod(12) in diatonic)
            // 前後と同じコードは繰り返しになるので出さない。
            assertTrue("$picked", picked != previous && picked != next)
        }
    }

    @Test
    fun `picking one chord never returns what is already there`() {
        val key = MusicKey(0, minor = false)
        val current = Chord(5, ChordQuality.MAJOR) // F
        repeat(200) { seed ->
            val picked = ChordSuggester.pickOne(
                previous = Chord(0, ChordQuality.MAJOR),
                key = key,
                next = null,
                random = Random(seed),
                exclude = current,
            )
            assertTrue("$picked", picked != current)
        }
    }

    @Test
    fun `picking one chord is not always the same answer`() {
        val key = MusicKey(0, minor = false)
        val seen = (0 until 200)
            .mapNotNull { ChordSuggester.pickOne(Chord(0, ChordQuality.MAJOR), key, null, Random(it)) }
            .toSet()
        // 重みつきの抽選なので、押すたびに違うものが出る。
        assertTrue("seen=$seen", seen.size >= 3)
    }
}
