package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 打ち込みに置く進行の種。 */
class ChordProgressionsTest {

    private val cMajor = MusicKey(0, Scale.MAJOR)
    private val aMinor = MusicKey(9, Scale.NATURAL_MINOR)

    @Test
    fun `every seed is exactly as long as asked`() {
        listOf(1, 2, 4, 8).forEach { bars ->
            ChordProgressions.seeds(cMajor, bars, Random(1)).forEach { seed ->
                assertEquals("${seed.name} が $bars 小節ぶんでない", bars, seed.chords.size)
            }
        }
    }

    @Test
    fun `seeds carry a name you can tell apart`() {
        val seeds = ChordProgressions.seeds(cMajor, ChordProgressions.BARS, Random(1))
        assertTrue("種が少なすぎる", seeds.size >= 10)
        assertEquals("名前が重なっている", seeds.size, seeds.map { it.name }.distinct().size)
        assertTrue(seeds.none { it.name.isBlank() })
    }

    @Test
    fun `no two seeds offer the very same progression`() {
        // 同じ並びが 2 行に出ていると、違うものだと思って選んでしまう。
        listOf(cMajor, aMinor).forEach { key ->
            repeat(20) { attempt ->
                val seeds = ChordProgressions.seeds(key, random = Random(attempt.toLong()))
                assertEquals(
                    "$key の $attempt 回目で重なった: " +
                        seeds.map { "${it.name}=${it.chords.map { c -> c.name }}" },
                    seeds.size,
                    seeds.map { it.chords }.distinct().size,
                )
            }
        }
    }

    @Test
    fun `the generated seeds keep coming until they are all different`() {
        // 引き直すので、素直に 4 回引いたら重なる調でも 4 つ並ぶ。
        listOf(cMajor, aMinor).forEach { key ->
            val made = ChordProgressions.seeds(key, random = Random(1))
                .filter { it.name.startsWith("作らせた") }
            assertEquals(key.toString(), ChordProgressions.GENERATED, made.size)
        }
    }

    @Test
    fun `the templates come out in the song's key`() {
        // C 長調なら C から、A 短調なら A から始まる型がある。
        val major = ChordProgressions.templates(cMajor).first { it.name == "I-V-vi-IV" }
        assertEquals(listOf("C", "G", "Am", "F"), major.chords.map { it.name })

        val minor = ChordProgressions.templates(aMinor).first { it.name == "i-♭VII-♭VI-♭VII" }
        assertEquals(listOf("Am", "G", "F", "G"), minor.chords.map { it.name })
    }

    @Test
    fun `an eight bar template gives its first half when four bars are asked`() {
        val canon = ChordProgressions.templates(cMajor, 4).first { it.name == "カノン進行" }
        val full = ProgressionTemplate.CANON.chords(cMajor)
        assertEquals(full.take(4), canon.chords)
    }

    @Test
    fun `the generated seeds land on the tonic`() {
        // 起承転結の「結」で帰ってくるので、4 小節でも終わりは主和音になる。
        ChordProgressions.generated(cMajor, 4, count = 8, random = Random(2)).forEach { seed ->
            assertEquals("${seed.name}: ${seed.chords.map { it.name }}", 0, seed.chords.last().root)
        }
    }

    @Test
    fun `the same seed number gives the same progression`() {
        assertEquals(
            ChordProgressions.seeds(cMajor, 4, Random(5)).map { it.chords },
            ChordProgressions.seeds(cMajor, 4, Random(5)).map { it.chords },
        )
    }

    @Test
    fun `fitting stretches a short progression by holding the last chord`() {
        val short = listOf(Chord(0), Chord(7))
        assertEquals(
            listOf(Chord(0), Chord(7), Chord(7), Chord(7)),
            ChordProgressions.fit(short, 4),
        )
    }

    @Test
    fun `fitting trims a long progression`() {
        val long = List(8) { Chord(it) }
        assertEquals(List(4) { Chord(it) }, ChordProgressions.fit(long, 4))
    }

    @Test
    fun `fitting an empty progression still gives something playable`() {
        assertEquals(List(4) { Chord() }, ChordProgressions.fit(emptyList(), 4))
        assertEquals(emptyList<Chord>(), ChordProgressions.fit(listOf(Chord(0)), 0))
    }

    @Test
    fun `four chords fall one to a beat`() {
        // 一番よくある形。1 小節に 4 つなら 1 拍ずつ。
        val chords = List(4) { Chord(it) }
        assertEquals(listOf(0, 2, 4, 6), ChordProgressions.spread(chords).map { it.slot })
        assertEquals(chords, ChordProgressions.spread(chords).map { it.chord })
    }

    @Test
    fun `fewer or more chords still divide the bar evenly`() {
        assertEquals(listOf(0), ChordProgressions.spread(List(1) { Chord(it) }).map { it.slot })
        assertEquals(listOf(0, 4), ChordProgressions.spread(List(2) { Chord(it) }).map { it.slot })
        assertEquals(listOf(0, 2, 5), ChordProgressions.spread(List(3) { Chord(it) }).map { it.slot })
        assertEquals(
            (0 until CHORD_SLOTS).toList(),
            ChordProgressions.spread(List(CHORD_SLOTS) { Chord(it) }).map { it.slot },
        )
    }

    @Test
    fun `spreading never puts two chords in the same slot`() {
        // 同じ枠に二度置くと、あとから置いたほうだけが残ってコードが消える。
        (1..CHORD_SLOTS).forEach { count ->
            val places = ChordProgressions.spread(List(count) { Chord(it.mod(12)) })
            assertEquals("$count 個で欠けた", count, places.size)
            assertEquals("$count 個で枠がぶつかった", count, places.map { it.slot }.distinct().size)
            assertTrue(places.all { it.slot in 0 until CHORD_SLOTS })
        }
    }

    @Test
    fun `spreading really is even, right through to the end`() {
        // どのコードも受け持つ長さがほぼ同じで、最後のコードの後ろだけが
        // 余る、ということも無い。端数を頭で丸めると、後半がすかすかになる。
        (1..CHORD_SLOTS).forEach { count ->
            val at = ChordProgressions.spread(List(count) { Chord(it.mod(12)) }).map { it.slot }
            val spans = at.indices.map { (at.getOrNull(it + 1) ?: CHORD_SLOTS) - at[it] }
            assertTrue("$count 個で長さがばらけた: $spans", spans.max() - spans.min() <= 1)
        }
    }

    @Test
    fun `spreading keeps the order and starts at the very top`() {
        val places = ChordProgressions.spread(List(3) { Chord(it) })
        assertEquals(0, places.first().slot)
        assertEquals(List(3) { Chord(it) }, places.map { it.chord })
    }

    @Test
    fun `spreading drops what will not fit rather than piling it up`() {
        val chords = List(CHORD_SLOTS + 4) { Chord(it.mod(12)) }
        val places = ChordProgressions.spread(chords)
        assertEquals(CHORD_SLOTS, places.size)
        assertEquals(chords.take(CHORD_SLOTS), places.map { it.chord })
    }

    @Test
    fun `spreading nothing places nothing`() {
        assertEquals(emptyList<ChordProgressions.Placement>(), ChordProgressions.spread(emptyList()))
    }

    @Test
    fun `over bars puts one chord at the head of each bar`() {
        val chords = List(4) { Chord(it) }
        assertEquals(
            listOf(0, 1, 2, 3),
            ChordProgressions.overBars(chords, from = 0, barCount = 4).map { it.bar },
        )
        assertEquals(chords, ChordProgressions.overBars(chords, 0, 4).map { it.chord })
    }

    @Test
    fun `over bars starts where you opened and drops what runs past the end`() {
        val chords = List(4) { Chord(it) }
        val places = ChordProgressions.overBars(chords, from = 2, barCount = 4)
        assertEquals("はみ出したぶんは切る", listOf(2, 3), places.map { it.bar })
        assertEquals(chords.take(2), places.map { it.chord })
        // 1 小節しか無いパターンなら、頭の 1 つだけが入る。
        assertEquals(1, ChordProgressions.overBars(chords, 0, 1).size)
        assertEquals(emptyList<Int>(), ChordProgressions.overBars(chords, 4, 4).map { it.bar })
    }

    @Test
    fun `every seed fills a bar a beat at a time`() {
        // 画面から渡すのは既定の長さのまま。どの種も 1 拍に 1 つで収まる。
        ChordProgressions.seeds(cMajor, random = Random(3)).forEach { seed ->
            assertEquals(seed.name, listOf(0, 2, 4, 6), ChordProgressions.spread(seed.chords).map { it.slot })
        }
    }

    @Test
    fun `a seed can be played as a plan of its own`() {
        // 置く前に聴けることが肝なので、そのままプランに組めることを押さえる。
        val song = Song.newSong("s", "test", 0L)
        val chords = ChordProgressions.templates(cMajor).first().chords
        val plan = PlaybackPlan(song.patterns, chords.mapIndexed { bar, chord -> Bar(0, chord, bar) })
        assertEquals(4, plan.barCount)
        assertEquals(chords, (0 until plan.barCount).map { plan.chordAt(it) })
        // 声部はプランが自分で解く。試聴と本番で同じ手順を通るので音が食い違わない。
        assertEquals(Voicing.lead(chords), (0 until plan.barCount).map { plan.voicingAt(it) })
    }
}
