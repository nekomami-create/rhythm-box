package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GenreTest {

    private val cMajor = MusicKey(0, minor = false)

    @Test
    fun `the classic progressions spell out the chords everyone knows`() {
        assertEquals(
            listOf("F", "G", "Em", "Am"),
            ProgressionTemplate.ROYAL_ROAD.chords(cMajor).map { it.name },
        )
        assertEquals(
            listOf("Am", "F", "G", "C"),
            ProgressionTemplate.KOMURO.chords(cMajor).map { it.name },
        )
        assertEquals(
            listOf("C", "G", "Am", "Em", "F", "C", "F", "G"),
            ProgressionTemplate.CANON.chords(cMajor).map { it.name },
        )
        assertEquals(
            listOf("C", "G", "Am", "F"),
            ProgressionTemplate.POP_PUNK.chords(cMajor).map { it.name },
        )
    }

    @Test
    fun `templates can ask for their own chord types`() {
        assertEquals(
            listOf("FM7", "E7", "Am7", "Am7"),
            ProgressionTemplate.CITY.chords(cMajor).map { it.name },
        )
        assertEquals(
            listOf("Dm7", "G7", "CM7", "CM7"),
            ProgressionTemplate.TWO_FIVE_ONE.chords(cMajor).map { it.name },
        )
    }

    @Test
    fun `progressions transpose with the key`() {
        val gMajor = MusicKey(7, minor = false)
        assertEquals(
            listOf("C", "D", "Bm", "Em"),
            ProgressionTemplate.ROYAL_ROAD.chords(gMajor).map { it.name },
        )
        val aMinor = MusicKey(9, minor = true)
        // マイナーキーでも度数どおりに並ぶ（i, III, iv...）
        assertEquals(4, ProgressionTemplate.KOMURO.chords(aMinor).size)
    }

    @Test
    fun `filling repeats or trims the template to the bar count`() {
        val template = ProgressionTemplate.ROYAL_ROAD
        assertEquals(
            listOf("F", "G", "Em", "Am", "F", "G", "Em", "Am"),
            template.fill(cMajor, 8).map { it.name },
        )
        assertEquals(listOf("F", "G", "Em"), template.fill(cMajor, 3).map { it.name })
        assertEquals(8, ProgressionTemplate.CANON.fill(cMajor, 8).size)
        assertTrue(template.fill(cMajor, 0).isEmpty())
    }

    @Test
    fun `every genre is set up with something usable`() {
        for (genre in Genre.entries) {
            assertTrue(genre.label.isNotBlank())
            assertTrue("${genre.label} のテンポ", genre.bpmRange.first >= Song.MIN_BPM)
            assertTrue("${genre.label} のテンポ", genre.bpmRange.last <= Song.MAX_BPM)
            assertTrue("${genre.label} のリズム", genre.rhythms.isNotEmpty())
            assertTrue("${genre.label} の進行", genre.progressions.isNotEmpty())
        }
    }

    @Test
    fun `picking from a genre stays inside its own settings`() {
        for (genre in Genre.entries) {
            repeat(30) { seed ->
                val random = Random(seed)
                assertTrue(genre.pickBpm(random) in genre.bpmRange)
                assertTrue(genre.pickRhythm(random) in genre.rhythms)
                assertTrue(genre.pickProgression(random) in genre.progressions)
            }
        }
    }

    @Test
    fun `the andalusian lifts its last chord out of the minor scale`() {
        // ここが型の肝。ナチュラルマイナーのままだと v は短三和音（Em）で、
        // i へ落ちる力が出ない。長三和音の 7th にして初めてこの型になる。
        val aMinor = MusicKey(9, Scale.NATURAL_MINOR)
        assertEquals(
            listOf("Am", "G", "F", "E7"),
            ProgressionTemplate.ANDALUSIAN.chords(aMinor).map { it.name },
        )
        val last = ProgressionTemplate.ANDALUSIAN.chords(aMinor).last()
        assertEquals("V が短三和音のままでは効かない", ChordQuality.SEVENTH, last.quality)
        // 主音を変えても付いてくる。
        assertEquals(
            listOf("Em", "D", "C", "B7"),
            ProgressionTemplate.ANDALUSIAN.chords(MusicKey(4, Scale.NATURAL_MINOR)).map { it.name },
        )
    }

    @Test
    fun `hard rock pushes with chords and bass, not with the drums`() {
        // ハードロックらしさはギターの刻みとベースの連打から来る。ドラムは
        // ロックとそう変わらないので、そこで測っても違いが出ない。
        val rock = perBar(Genre.ROCK)
        val hard = perBar(Genre.HARD_ROCK)
        assertTrue("コードが刻めていない（ロック ${rock.chord} / ハードロック ${hard.chord}）",
            hard.chord > rock.chord * 2)
        assertTrue("ベースが押せていない（ロック ${rock.bass} / ハードロック ${hard.bass}）",
            hard.bass > rock.bass * 2)
        // ルートを押し続ける。5 度で動かすと軽くなる。
        assertEquals(BassStyle.ROOT, Genre.HARD_ROCK.bassStyle)
        // 三和音のまま。7th を足すと歪んだギターの角が取れる。
        assertEquals(0.0, Genre.HARD_ROCK.seventhChance, 1e-9)
    }

    @Test
    fun `hard rock stays in the minor scale`() {
        // 長調の型が混ざると、狙っている暗さが毎回は出なくなる。
        val aMinor = MusicKey(9, Scale.NATURAL_MINOR)
        for (template in Genre.HARD_ROCK.progressions) {
            val tonic = template.chords(aMinor).first()
            assertTrue(
                "${template.name} が短調で始まっていない（${tonic.name}）",
                tonic.quality in listOf(ChordQuality.MINOR, ChordQuality.MINOR_SEVENTH, ChordQuality.HALF_DIMINISHED) ||
                    template.keyFor(aMinor).scale != Scale.MAJOR,
            )
        }
    }

    @Test
    fun `the digital genre drives on sixteenth bass`() {
        // 打ち込みらしさは、動き続けるベースそのもの。
        val dance = perBar(Genre.DANCE)
        val digital = perBar(Genre.DIGITAL)
        assertTrue("ベースが 16 分で動いていない（${digital.bass}）", digital.bass >= 10.0)
        assertTrue("ダンスと変わらない（ダンス ${dance.bass} / 打ち込み ${digital.bass}）",
            digital.bass > dance.bass * 2)
        assertTrue("ハットが細かくない（${digital.hat}）", digital.hat >= 11.0)
    }

    private data class Density(val chord: Double, val bass: Double, val hat: Double)

    /** そのジャンルで作った曲の、1 小節あたりの平均打点数。 */
    private fun perBar(genre: Genre): Density {
        var chord = 0; var bass = 0; var hat = 0; var bars = 0
        repeat(30) { seed ->
            val random = Random(seed)
            val recipe = SongEditor.recipeFor(genre, null, random)
            val song = SongBuilder.build(
                Song.newSong("s", "x", 0L), recipe, MusicKey(9, Scale.NATURAL_MINOR), 8, random,
            )
            val pattern = song.pattern(0)
            for (bar in 0 until pattern.barCount) {
                bars++
                val one = pattern.at(bar)
                chord += (0 until STEPS_PER_BAR).count { one.isOn(ROW_CHORD, it) }
                bass += (0 until STEPS_PER_BAR).count { one.isOn(ROW_BASS, it) }
                hat += (0 until STEPS_PER_BAR).count { one.isOn(Voice.CLOSED_HAT.ordinal, it) }
            }
        }
        return Density(chord.toDouble() / bars, bass.toDouble() / bars, hat.toDouble() / bars)
    }

    @Test
    fun `the genres really are different from each other`() {
        // テンポ帯が全部同じだと、選ぶ意味がない。
        val ranges = Genre.entries.map { it.bpmRange }
        assertEquals(ranges.size, ranges.distinct().size)
        assertTrue(Genre.BALLAD.bpmRange.last < Genre.ROCK.bpmRange.first)
        assertEquals(MelodyDensity.SPARSE, Genre.BALLAD.melodyDensity)
        assertEquals(MelodyDensity.BUSY, Genre.CITY_POP.melodyDensity)
    }
}
