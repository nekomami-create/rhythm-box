package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SongBuilderTest {

    private val key = MusicKey(0, minor = false)

    private fun base() = Song.newSong("s", "テスト", 0L)

    @Test
    fun `builds eight bars as two blocks of four`() {
        for (genre in Genre.entries) {
            repeat(10) { seed ->
                val song = SongBuilder.build(base(), genre.recipe(), key, random = Random(seed))
                assertEquals(8, song.totalBars())
                assertEquals(2, song.arrangement.size)
                assertTrue(song.arrangement.all { it.repeat == 4 })
                assertEquals(0, song.arrangement[0].patternIndex)
                assertEquals(1, song.arrangement[1].patternIndex)
                assertTrue("${genre.label} のテンポ", song.bpm in genre.bpmRange)
            }
        }
    }

    @Test
    fun `every bar gets a chord from one of the genre's progressions`() {
        for (genre in Genre.entries) {
            repeat(10) { seed ->
                val song = SongBuilder.build(base(), genre.recipe(), key, random = Random(seed))
                val bars = PlaybackPlan.arrangement(song).bars.map { it.chord }
                assertEquals(8, bars.size)
                // どれかの型を 8 小節に敷いたものと一致する。
                // ところどころ 7th や sus4 に色が付くので、そこは許す。
                assertTrue(
                    "${genre.label} $bars",
                    genre.progressions.any { template ->
                        val filled = template.fill(key, 8)
                        filled.size == bars.size && filled.indices.all { sameOrDressed(filled[it], bars[it]) }
                    },
                )
            }
        }
    }

    @Test
    fun `both patterns get something to play`() {
        repeat(10) { seed ->
            val song = SongBuilder.build(base(), Genre.JPOP.recipe(), key, random = Random(seed))
            listOf(0, 1).forEach { index ->
                val pattern = song.pattern(index)
                assertTrue("パターン ${pattern.name} が空", pattern.hitCount() > 0)
                assertTrue(pattern.isOn(Voice.KICK.ordinal, 0))
                // コードは頭から外れることがある（ChordTimingTest 参照）。
                assertTrue(pattern.rowAt(ROW_CHORD) != 0)
                assertTrue(pattern.isOn(ROW_BASS, 0))
            }
        }
    }

    @Test
    fun `each pattern previews with the chord its block starts on`() {
        val song = SongBuilder.build(base(), Genre.ROCK.recipe(), key, random = Random(3))
        val bars = PlaybackPlan.arrangement(song).bars.map { it.chord }
        assertEquals(bars[0], song.patternChord(0))
        assertEquals(bars[4], song.patternChord(1))
    }

    @Test
    fun `the two halves are not identical`() {
        // 前半と後半が毎回同じだと、8 小節にする意味がない。
        val different = (0 until 20).count { seed ->
            val song = SongBuilder.build(base(), Genre.JPOP.recipe(), key, random = Random(seed))
            song.pattern(0).rows != song.pattern(1).rows
        }
        assertTrue("20 回中 $different 回しか違わない", different >= 18)
    }

    @Test
    fun `each bar of a block gets its own melody`() {
        // ドラムは 4 小節同じでも、旋律は小節ごとに変える必要がある。
        repeat(10) { seed ->
            val song = SongBuilder.build(base(), Genre.JPOP.recipe(), key, random = Random(seed))
            listOf(SongBuilder.FIRST_PATTERN, SongBuilder.SECOND_PATTERN).forEach { index ->
                val pattern = song.pattern(index)
                assertEquals(SongBuilder.BLOCK, pattern.leadBarCount)
                assertTrue("旋律が入っていない", pattern.leadNoteCount() > 0)
                // 4 小節が全部同じだと、繰り返しても意味がない。
                assertTrue("4 小節とも同じ旋律", pattern.leadBars.distinct().size > 1)
            }
        }
    }

    @Test
    fun `each melody lands on the chord of its own bar`() {
        repeat(10) { seed ->
            val song = SongBuilder.build(base(), Genre.JPOP.recipe(), key, random = Random(seed))
            val block = song.arrangement.first()
            val pattern = song.pattern(block.patternIndex)
            block.chords.forEachIndexed { bar, chord ->
                val tones = chord.voicing().map { it.mod(12) }.toSet()
                for (step in 0 until STEPS_PER_BAR step 4) {
                    val midi = pattern.leadAt(bar, step)
                    if (Pattern.isNote(midi)) {
                        assertTrue(
                            "${bar + 1} 小節目 ${chord.name} に ${midiName(midi)}",
                            midi.mod(12) in tones,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `melodies can be left alone, and other patterns always are`() {
        val written = base()
            .withPattern(0, base().pattern(0).withLead(0, 0, 72).withLead(0, 4, 74))
            .withPattern(5, Pattern.of("F", "x...x...x...x..."))
        val song = SongBuilder.build(written, Genre.DANCE.recipe(), key, random = Random(1), withMelody = false)
        assertEquals(72, song.pattern(0).leadAt(0, 0))
        assertEquals(74, song.pattern(0).leadAt(0, 4))
        assertEquals(written.pattern(5), song.pattern(5))

        // 旋律も作る場合でも、使っていないパターンには触らない。
        val withMelody = SongBuilder.build(written, Genre.DANCE.recipe(), key, random = Random(1))
        assertEquals(written.pattern(5), withMelody.pattern(5))
    }

    @Test
    fun `the same seed builds the same song`() {
        assertEquals(
            SongBuilder.build(base(), Genre.CITY_POP.recipe(), key, random = Random(9)),
            SongBuilder.build(base(), Genre.CITY_POP.recipe(), key, random = Random(9)),
        )
        assertNotEquals(
            SongBuilder.build(base(), Genre.CITY_POP.recipe(), key, random = Random(9)),
            SongBuilder.build(base(), Genre.CITY_POP.recipe(), key, random = Random(10)),
        )
    }

    // --- 小節数を選べる（オート作曲） -------------------------------------

    @Test
    fun `the length can be chosen in blocks of four`() {
        assertEquals(16, SongBuilder.BAR_CHOICES.size)
        assertEquals(4, SongBuilder.BAR_CHOICES.first())
        assertEquals(64, SongBuilder.BAR_CHOICES.last())
        assertTrue(SongBuilder.BAR_CHOICES.all { it % SongBuilder.BLOCK == 0 })
    }

    @Test
    fun `it builds exactly the number of bars asked for`() {
        for (bars in SongBuilder.BAR_CHOICES) {
            val song = SongBuilder.build(base(), Genre.JPOP.recipe(), key, bars, Random(bars))
            assertEquals("$bars 小節", bars, song.totalBars())
            assertEquals(bars / SongBuilder.BLOCK, song.arrangement.size)
            assertTrue(song.arrangement.all { it.repeat == SongBuilder.BLOCK })
        }
    }

    @Test
    fun `odd lengths are rounded into shape`() {
        assertEquals(4, SongBuilder.normalizeBars(0))
        assertEquals(4, SongBuilder.normalizeBars(3))
        assertEquals(4, SongBuilder.normalizeBars(7))
        assertEquals(8, SongBuilder.normalizeBars(11))
        assertEquals(64, SongBuilder.normalizeBars(999))
    }

    @Test
    fun `blocks reuse a handful of patterns instead of eating every slot`() {
        // 6 幕（Aメロ→Bメロ→サビ→Aメロ→Cメロ→まとめ）を組めるだけの
        // 長さが無い短い曲は、幕を後ろから間引いて頭から 1 ブロックずつ使う。
        assertEquals(listOf(0), SongBuilder.patternLayout(4))
        assertEquals(listOf(0, 1), SongBuilder.patternLayout(8))
        assertEquals(listOf(0, 1, 2), SongBuilder.patternLayout(12))
        // 16 小節でもまだ 4 幕ぶん（Aメロ・Bメロ・サビ・Aメロ）しか無いので、
        // Cメロ・まとめ抜きで、2 回目のAメロはパターン 0 の使い回しに戻る。
        assertEquals(listOf(0, 1, 2, 0), SongBuilder.patternLayout(16))
        // 32 小節でようやく 6 幕を全部組める長さになる（[SongBuilder.sections] 参照）。
        assertEquals(listOf(0, 0, 1, 2, 2, 0, 3, 2), SongBuilder.patternLayout(32))

        val song = SongBuilder.build(base(), Genre.ROCK.recipe(), key, 64, Random(1))
        val used = song.arrangement.map { it.patternIndex }.distinct()
        assertEquals(SongBuilder.MAX_PATTERNS, used.size)
        // E 以降は手で書く用に空けておく
        assertTrue(
            "使っていないパターンが書き換わっている",
            (SongBuilder.MAX_PATTERNS until Song.PATTERN_COUNT).all { base().pattern(it) == song.pattern(it) },
        )
    }

    // --- 曲としてのまとまり（Aメロ→Bメロ→サビ→Aメロ→Cメロ→まとめ） -------

    @Test
    fun `a full song walks through the six sections in order`() {
        val labels = SongBuilder.sections(64).map { it.section }
        assertEquals(
            listOf(
                SongBuilder.Section.A_MELODY,
                SongBuilder.Section.B_MELODY,
                SongBuilder.Section.CHORUS,
                SongBuilder.Section.A_MELODY,
                SongBuilder.Section.C_MELODY,
                SongBuilder.Section.OUTRO,
            ),
            labels,
        )
        // 64 小節ぶん、幕の長さを足すと過不足なく敷き詰まる。
        assertEquals(64, SongBuilder.sections(64).sumOf { it.blocks } * SongBuilder.BLOCK)
    }

    @Test
    fun `the second verse and the outro reprise earlier patterns`() {
        val slots = SongBuilder.sections(64)
        val byRole = slots.groupBy { it.section }
        // 2 回目のAメロは 1 回目と同じパターン（＝同じ演奏）を使い回す。
        assertEquals(2, byRole.getValue(SongBuilder.Section.A_MELODY).size)
        assertEquals(
            byRole.getValue(SongBuilder.Section.A_MELODY)[0].patternIndex,
            byRole.getValue(SongBuilder.Section.A_MELODY)[1].patternIndex,
        )
        // まとめはサビの使い回し（サビをそのまま繰り返して終える）。
        assertEquals(
            byRole.getValue(SongBuilder.Section.CHORUS).single().patternIndex,
            byRole.getValue(SongBuilder.Section.OUTRO).single().patternIndex,
        )
        // それでも全体では 4 パターンぶんしか使わない（E 以降は空けておく制約）。
        assertEquals(SongBuilder.MAX_PATTERNS, slots.map { it.patternIndex }.distinct().size)
    }

    @Test
    fun `short songs fall back to one section per block, skipping the later acts first`() {
        assertEquals(listOf(SongBuilder.Section.A_MELODY), SongBuilder.sections(4).map { it.section })
        assertEquals(
            listOf(SongBuilder.Section.A_MELODY, SongBuilder.Section.B_MELODY),
            SongBuilder.sections(8).map { it.section },
        )
        assertTrue(SongBuilder.sections(8).all { it.blocks == 1 })
    }

    @Test
    fun `a full-length song's chorus usually starts on a different chord than the verse`() {
        // 王道進行や丸サ進行のように、そもそも主和音（I）を通らない型もあるので
        // 100% にはならない。それでも、たいていは Aメロと違う出だしになる。
        var differ = 0
        repeat(30) { seed ->
            val song = SongBuilder.build(base(), Genre.JPOP.recipe(), key, 64, Random(seed))
            val verseStart = song.arrangement.first { it.patternIndex == 0 }.chords.first()
            val chorusStart = song.arrangement.first { it.patternIndex == 2 }.chords.first()
            if (verseStart != chorusStart) differ++
        }
        assertTrue("30 回中 $differ 回しか違わない", differ >= 20)
    }

    @Test
    fun `a repeated pattern still fits the chords wherever it comes back`() {
        // 同じパターンが後半にもう一度出てくるとき、そのブロックのコードが
        // 最初のブロックと同じでないと、作った旋律が合わなくなる。
        // 種を 1 つに決め打ちすると、たまたま進行の長さがブロックの間隔と
        // 割り切れる組み合わせしか通らず、見落としが起きる。何粒か振る。
        for (genre in Genre.entries) {
            for (bars in listOf(16, 32, 64)) {
                for (seed in 0 until 5) {
                    val song = SongBuilder.build(base(), genre.recipe(), key, bars, Random(bars * 100 + seed))
                    val byPattern = song.arrangement.groupBy { it.patternIndex }
                    byPattern.forEach { (index, blocks) ->
                        val first = blocks.first().chords
                        assertTrue(
                            "${genre.label} $bars 小節 seed=$seed: パターン $index のコードがブロックごとに違う",
                            blocks.all { it.chords == first },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `a reused pattern keeps its chords even in a short, degraded song with a long progression`() {
        // 短い曲（幕が足りず後ろから間引く）では、進行を頭から 1 本の流れの
        // まま敷く。進行の長さ（ここではカノン進行、8 和音）がブロックの
        // 間隔（16 小節では 12 小節ぶん）と割り切れないと、位置だけで決めると
        // 使い回しのブロックが違うコードを引いてしまう。進行を固定して確かめる。
        val canonOnly = GenreRecipe(
            bpmRange = 100..100,
            rhythms = listOf(RhythmStyle.EIGHT_BEAT),
            progressions = listOf(ProgressionTemplate.CANON),
            melodyDensity = MelodyDensity.NORMAL,
            chip = false,
        )
        val song = SongBuilder.build(base(), canonOnly, key, 16, Random(1))
        val byPattern = song.arrangement.groupBy { it.patternIndex }
        byPattern.forEach { (index, blocks) ->
            val first = blocks.first().chords
            assertTrue("パターン $index のコードがブロックごとに違う", blocks.all { it.chords == first })
        }
    }

    @Test
    fun `long songs still get a melody for every bar of every pattern`() {
        val song = SongBuilder.build(base(), Genre.JPOP.recipe(), key, 32, Random(5))
        song.arrangement.map { it.patternIndex }.distinct().forEach { index ->
            val pattern = song.pattern(index)
            assertEquals(SongBuilder.BLOCK, pattern.leadBarCount)
            assertTrue(pattern.leadNoteCount() > 0)
        }
    }

    @Test
    fun `an auto-composed song comes with accents already in it`() {
        val song = SongBuilder.build(
            base = Song.newSong("id", "曲", 0L),
            recipe = Genre.ROCK.recipe(),
            key = MusicKey(0, minor = false),
            bars = 8,
            random = Random(3),
        )
        val accented = song.patterns.any { pattern ->
            (0 until STEPS_PER_BAR).any { step ->
                (0 until DRUM_COUNT).any { row ->
                    pattern.isOn(row, step) && pattern.levelAt(row, step) != Pattern.Level.NORMAL
                }
            }
        }
        assertTrue("オート作曲の結果に強弱が入っていない", accented)
    }
}
