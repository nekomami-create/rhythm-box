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

    // --- 7th の色付け ---------------------------------------------------------

    /** 必ず色が付く濃さで 1 周ぶんを色付けする。 */
    private fun enriched(chords: List<Chord>, key: MusicKey = cMajor) =
        Harmony.enrichSevenths(chords, key, chance = 1.0, random = Random(1))

    /** [degree] の和音が色付いた割合（濃さ 1.0 で 200 回）。 */
    private fun colouredRate(degree: Int): Double {
        val diatonic = cMajor.diatonicChords()
        val coloured = (0 until 200).count { seed ->
            Harmony.enrichSevenths(diatonic, cMajor, 1.0, Random(seed))[degree] != diatonic[degree]
        }
        return coloured / 200.0
    }

    @Test
    fun `the dominant gets a flat seventh, never a major seventh`() {
        // ここだけ度数で扱いが変わる。V を M7 にすると属和音の緊張が消え、
        // 主和音へ落ちる力が無くなる。
        assertEquals(ChordQuality.SEVENTH, Harmony.seventhFor(4, ChordQuality.MAJOR))
        assertEquals(ChordQuality.MAJOR_SEVENTH, Harmony.seventhFor(0, ChordQuality.MAJOR))
        assertEquals(ChordQuality.MAJOR_SEVENTH, Harmony.seventhFor(3, ChordQuality.MAJOR))
        // V はいちばん足す価値があるので、濃さを上げ切ると必ず色が付く。
        assertEquals(1.0, colouredRate(4), 0.0)
        assertEquals("G7", enriched(cMajor.diatonicChords())[4].name)
    }

    @Test
    fun `minor and diminished chords keep their character`() {
        assertEquals(ChordQuality.MINOR_SEVENTH, Harmony.seventhFor(1, ChordQuality.MINOR))
        // 減三和音は m7-5。ここを m7 にすると減 5 度が消えて別の和音になる。
        assertEquals(ChordQuality.HALF_DIMINISHED, Harmony.seventhFor(6, ChordQuality.DIMINISHED))

        val result = enriched(cMajor.diatonicChords())
        assertEquals("Dm7", result[1].name)
        assertEquals("Am7", result[5].name)
        assertEquals("Bm7-5", result[6].name)
    }

    @Test
    fun `the tonic keeps its plain triad more often than the others`() {
        // I を毎回 M7 にすると終わった感じが薄れて、曲が着地しなくなる。
        val tonic = colouredRate(0)
        val subdominant = colouredRate(3)
        assertTrue("I $tonic >= IV $subdominant", tonic < subdominant)
        assertTrue("I が一度も色付かない", tonic > 0.0)
        assertEquals(1.0, subdominant, 0.0)
    }

    @Test
    fun `no colouring at all when the genre asks for none`() {
        val diatonic = cMajor.diatonicChords()
        assertEquals(diatonic, Harmony.enrichSevenths(diatonic, cMajor, chance = 0.0, random = Random(1)))
    }

    @Test
    fun `chords the progression already coloured are left alone`() {
        // 型が「ここはこの響きで」と決めたものを上書きしない。
        val already = ProgressionTemplate.CITY.chords(cMajor)
        assertEquals(already, enriched(already))
    }

    @Test
    fun `a borrowed dominant is left alone`() {
        // 循環進行の A7 は調の外なので、度数が見つからない＝触らない。
        val turnaround = ProgressionTemplate.TURNAROUND.chords(cMajor)
        val result = enriched(turnaround)
        assertEquals("A7", result[1].name)
        // 調の中の I だけが色付く。
        assertEquals("CM7", result[0].name)
    }

    @Test
    fun `a suspended chord is not given a third back`() {
        val suspended = Chord(0, ChordQuality.SUS4)
        assertEquals(suspended, enriched(listOf(suspended)).single())
        assertNull(Harmony.seventhFor(0, ChordQuality.SUS4))
    }

    @Test
    fun `the colouring follows the scale the template means`() {
        // 短調の型に長調の音階をあてがうと、どの和音も度数が見つからず色が付かない。
        val minorTemplate = ProgressionTemplate.MINOR_TWO_FIVE
        val chords = minorTemplate.chords(aMinor)
        val right = Harmony.enrichSevenths(chords, minorTemplate.keyFor(aMinor), 1.0, Random(1))
        assertEquals("Am7", right[2].name) // i が色付いた
    }

    @Test
    fun `genres that want plain triads get plain triads`() {
        val base = Song.newSong("s", "test", 0L)
        listOf(Genre.ROCK, Genre.GAME).forEach { genre ->
            assertEquals(0.0, genre.recipe().seventhChance, 0.0)
            repeat(30) { seed ->
                val song = SongBuilder.build(base, genre.recipe(), cMajor, 8, Random(seed))
                val template = genre.progressions.first { candidate ->
                    val filled = candidate.fill(cMajor, 8)
                    val bars = PlaybackPlan.arrangement(song).bars.map { it.chord }
                    filled.indices.all { sameOrDressed(filled[it], bars[it]) }
                }
                // 型がもともと持っている響きだけで、後から足された 7th は無い。
                val fromTemplate = template.fill(cMajor, 8).map { it.quality }.toSet()
                PlaybackPlan.arrangement(song).bars.forEach { bar ->
                    val quality = bar.chord.quality
                    assertTrue(
                        "${genre.label} seed=$seed に足された $quality",
                        quality in fromTemplate || Harmony.suspendedOf(quality) != null ||
                            quality == ChordQuality.SUS4 || quality == ChordQuality.SEVENTH_SUS4,
                    )
                }
            }
        }
    }

    @Test
    fun `the same seed gives the same colouring`() {
        val diatonic = cMajor.diatonicChords()
        assertEquals(
            Harmony.enrichSevenths(diatonic, cMajor, 0.5, Random(9)),
            Harmony.enrichSevenths(diatonic, cMajor, 0.5, Random(9)),
        )
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
