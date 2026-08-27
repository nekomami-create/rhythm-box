package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 画面から切り出した、曲を書き換える操作の確認。
 *
 * これまで ViewModel の中で「今どのパターンを開いているか」を見ながら
 * 書き換えていたので、再生を通さないと確かめられなかった。
 */
class SongEditorTest {

    private fun song() = Song(id = "s", name = "曲")

    private fun rowsOf(pattern: Pattern, bar: Int) =
        (0 until STEP_ROW_COUNT).map { pattern.at(bar).rowAt(it) }

    // --- 書き換える相手を決める ---------------------------------------------

    @Test
    fun `only the patterns with something in them are rewritten`() {
        // 既定の曲は A と B に中身が入っていて、C 以降は空。
        assertEquals(listOf(0, 1), SongEditor.usedPatterns(song(), fallback = 5))
    }

    @Test
    fun `an empty song falls back to the pattern being looked at`() {
        val empty = song().let { base ->
            base.patterns.indices.fold(base) { acc, i -> acc.withPattern(i, Pattern.empty("x")) }
        }
        assertEquals(listOf(3), SongEditor.usedPatterns(empty, fallback = 3))
    }

    @Test
    fun `the chords of a song come from its arrangement when it has one`() {
        val arranged = song().copy(
            arrangement = listOf(ArrangementStep(0, 2, listOf(Chord(5), Chord(7)))),
        )
        assertEquals(listOf("F", "G"), SongEditor.chords(arranged).map { it.name })
        // 曲構成がまだ無ければ、パターンの試聴コードを見る。
        assertEquals(song().patternChords, SongEditor.chords(song()))
    }

    // --- リズムを引き直す ---------------------------------------------------

    @Test
    fun `rewriting one bar leaves the other bars alone`() {
        val base = song().let { it.withPattern(0, it.pattern(0).withBarCount(4)) }
        val next = SongEditor.withGeneratedRhythm(
            song = base,
            targets = listOf(0),
            bar = 2,
            style = RhythmStyle.FOUR_ON_FLOOR,
            random = Random(1),
        )
        val before = base.pattern(0)
        val after = next.pattern(0)

        assertEquals(rowsOf(before, 0), rowsOf(after, 0))
        assertEquals(rowsOf(before, 1), rowsOf(after, 1))
        assertEquals(rowsOf(before, 3), rowsOf(after, 3))
        assertNotEquals(rowsOf(before, 2), rowsOf(after, 2))
    }

    @Test
    fun `rewriting a whole pattern puts the same groove in every bar`() {
        // 小節ごとに違うノリを引くとまとまりが無くなるので、揃えて置く。
        val base = song().let { it.withPattern(0, it.pattern(0).withBarCount(3)) }
        val next = SongEditor.withGeneratedRhythm(
            song = base,
            targets = listOf(0),
            bar = null,
            style = RhythmStyle.EIGHT_BEAT,
            random = Random(2),
        )
        val pattern = next.pattern(0)
        assertEquals(rowsOf(pattern, 0), rowsOf(pattern, 1))
        assertEquals(rowsOf(pattern, 0), rowsOf(pattern, 2))
    }

    @Test
    fun `rewriting several patterns draws a fresh groove for each`() {
        val next = SongEditor.withGeneratedRhythm(
            song = song(),
            targets = listOf(0, 1),
            bar = null,
            style = null, // おまかせ。パターンごとに型から引き直す
            random = Random(3),
        )
        assertNotEquals(rowsOf(next.pattern(0), 0), rowsOf(next.pattern(1), 0))
    }

    @Test
    fun `patterns that were not named are untouched`() {
        val next = SongEditor.withGeneratedRhythm(
            song = song(),
            targets = listOf(0),
            bar = null,
            style = RhythmStyle.LATIN,
            random = Random(4),
        )
        assertEquals(song().pattern(1), next.pattern(1))
        // 名前と旋律は残る。書き換えるのは打ち込みだけ。
        assertEquals(song().pattern(0).name, next.pattern(0).name)
        assertEquals(song().pattern(0).leadBars, next.pattern(0).leadBars)
    }

    @Test
    fun `a groove is laid into every bar of a long pattern`() {
        val long = Pattern.empty("A").withBarCount(5)
        val groove = Pattern.of("g", "x...x...x...x...")
        val filled = SongEditor.withGrooveEverywhere(long, groove)

        for (bar in 0 until 5) {
            assertEquals("$bar 小節目", groove.rowAt(0), filled.at(bar).rowAt(0))
        }
    }

    // --- チップ音源への切り替え ---------------------------------------------

    @Test
    fun `a chip recipe switches the sound, the drums and the playing style together`() {
        // どれか 1 つだけでは「ゲーム音楽っぽさ」にならない。
        val next = SongEditor.withChipSound(song(), GameScene.BOSS.recipe())

        assertEquals(SoundSet.CHIP, next.soundSet)
        assertEquals(DrumKit.CHIP, next.drumKit)
        assertEquals(ChordStyle.CHIP_ARPEGGIO, next.chordStyle)
        assertEquals(GameScene.BOSS.recipe().leadVoice, next.leadVoice)
    }

    @Test
    fun `a recipe that is not chip leaves the sound alone`() {
        assertEquals(song(), SongEditor.withChipSound(song(), Genre.JPOP.recipe()))
    }

    // --- 場面の決め方 -------------------------------------------------------

    @Test
    fun `a chosen scene wins over the genre`() {
        val recipe = SongEditor.recipeFor(Genre.GAME, GameScene.TOWN, Random(5))
        assertEquals(GameScene.TOWN.recipe(), recipe)
    }

    @Test
    fun `a genre with scenes always ends up on one of them`() {
        // 「おまかせ」でゲーム音楽が出たときも、どれかの場面にはなる。
        val scenes = GameScene.entries.map { it.recipe() }.toSet()
        repeat(20) { seed ->
            assertTrue(SongEditor.recipeFor(Genre.GAME, null, Random(seed)) in scenes)
        }
    }

    @Test
    fun `a genre without scenes uses its own contents`() {
        assertEquals(Genre.ROCK.recipe(), SongEditor.recipeFor(Genre.ROCK, null, Random(6)))
    }
}
