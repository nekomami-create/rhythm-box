package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 鑑賞モード：終わりなく続きを作っていく [Appreciation.Stream]。 */
class AppreciationTest {

    @Test
    fun `starts with one block already made`() {
        val stream = Appreciation.Stream(Genre.JPOP, MusicKey(0, Scale.MAJOR), random = Random(1))
        assertEquals(Appreciation.BLOCK, stream.barCount)
        val plan = stream.plan()
        assertEquals(Appreciation.BLOCK, plan.barCount)
    }

    @Test
    fun `every grow adds exactly one block`() {
        val stream = Appreciation.Stream(Genre.ROCK, MusicKey(0, Scale.MAJOR), random = Random(2))
        val before = stream.barCount
        repeat(20) { i ->
            stream.grow()
            assertEquals(before + (i + 1) * Appreciation.BLOCK, stream.barCount)
        }
    }

    @Test
    fun `the plan is always playable end to end`() {
        // patternAt / chordAt が全小節ぶん引けること（範囲外や null で落ちない）。
        val stream = Appreciation.Stream(Genre.CITY_POP, MusicKey(0, Scale.MAJOR), random = Random(3))
        repeat(30) { stream.grow() }
        val plan = stream.plan()
        assertEquals(stream.barCount, plan.barCount)
        for (bar in 0 until plan.barCount) {
            assertNotNull(plan.chordAt(bar))
            assertNotNull(plan.patternAt(bar))
        }
    }

    @Test
    fun `past blocks never change once made`() {
        // これが本題。パターンをスロットで使い回す作りだと、あとのブロックが
        // 同じ場所を上書きしたとき、もう鳴らし終えたはずの昔の小節まで
        // 中身が変わって見えてしまっていた。増やしっぱなしにして直した。
        val stream = Appreciation.Stream(Genre.DANCE, MusicKey(0, Scale.MAJOR), random = Random(4))
        stream.grow() // bar 4 も読める数（8 小節）まで先に伸ばしておく
        val first = stream.plan()
        val chord0 = first.chordAt(0)
        val lead00 = first.patternAt(0).leadAt(0, 0)
        val chord4 = first.chordAt(4)

        repeat(300) { stream.grow() } // 十分な回数、スロット使い回しなら何周も上書きされる量

        val later = stream.plan()
        assertEquals(chord0, later.chordAt(0))
        assertEquals(lead00, later.patternAt(0).leadAt(0, 0))
        assertEquals(chord4, later.chordAt(4))
    }

    @Test
    fun `the same seed always gives the same music, even across a genre change`() {
        val a = Appreciation.Stream(null, null, random = Random(55))
        val b = Appreciation.Stream(null, null, random = Random(55))
        val genresA = mutableListOf(a.status.genre)
        repeat(200) {
            a.grow()
            b.grow()
            if (a.status.genre != genresA.last()) genresA += a.status.genre
        }
        assertTrue("200 ブロックのうちに一度も場面が変わらなかった", genresA.size > 1)
        val planA = a.plan()
        val planB = b.plan()
        assertEquals(planA.barCount, planB.barCount)
        for (bar in 0 until planA.barCount) {
            assertEquals("bar $bar", planA.chordAt(bar), planB.chordAt(bar))
        }
        assertEquals(a.status, b.status)
    }

    @Test
    fun `a chosen genre and key hold for at least the first era`() {
        val stream = Appreciation.Stream(Genre.HARD_ROCK, MusicKey(9, Scale.NATURAL_MINOR), random = Random(6))
        assertEquals(Genre.HARD_ROCK, stream.status.genre)
        assertEquals(MusicKey(9, Scale.NATURAL_MINOR), stream.status.key)
        // 場面が変わるまでは、選んだ軸がそのまま保たれる。
        while (stream.status.genre == Genre.HARD_ROCK) {
            assertEquals(MusicKey(9, Scale.NATURAL_MINOR), stream.status.key)
            stream.grow()
        }
    }

    @Test
    fun `blocksIntoEra counts up and resets when the scene changes`() {
        val stream = Appreciation.Stream(null, null, random = Random(7))
        var previous = stream.status
        assertEquals(1, previous.blocksIntoEra)
        repeat(150) {
            stream.grow()
            val now = stream.status
            if (now.genre == previous.genre) {
                assertEquals(previous.blocksIntoEra + 1, now.blocksIntoEra)
            } else {
                assertEquals(1, now.blocksIntoEra)
            }
            previous = now
        }
    }

    @Test
    fun `every genre can start a stream without blowing up`() {
        // GAME はジャンル自体に場面（GameScene）の選択が挟まる特殊な形なので、
        // ここで解決に失敗しないことを確かめる。
        for (genre in Genre.entries) {
            val stream = Appreciation.Stream(genre, MusicKey(0, Scale.MAJOR), random = Random(genre.ordinal.toLong()))
            repeat(5) { stream.grow() }
            assertEquals(genre, stream.status.genre)
            // GAME は場面（GameScene）ごとに別のテンポ帯を持つので、ジャンル自体の
            // bpmRange とは合わないことがある。場面を持たないジャンルだけ確かめる。
            if (genre.scenes.isEmpty()) {
                assertTrue("${genre.label} ${stream.status.bpm}", stream.status.bpm in genre.bpmRange)
            }
        }
    }

    @Test
    fun `omitting genre and key still produces something playable`() {
        repeat(20) { seed ->
            val stream = Appreciation.Stream(null, null, random = Random(seed.toLong()))
            repeat(3) { stream.grow() }
            val plan = stream.plan()
            assertTrue(plan.barCount > 0)
            assertNotNull(plan.chordAt(0))
        }
    }
}
