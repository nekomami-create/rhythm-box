package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 4 小節目で刻みを崩すフィル。 */
class FillTest {

    private val fillWindow = (STEPS_PER_BAR - 4) until STEPS_PER_BAR

    private fun base(seed: Int = 1) = PatternGenerator.generate(RhythmStyle.EIGHT_BEAT, Random(seed))

    /** 崩す窓より前だけを見たときの、その行の中身。 */
    private fun beforeWindow(pattern: Pattern, row: Int): Int =
        pattern.rowAt(row) and ((1 shl fillWindow.first) - 1)

    @Test
    fun `the groove is untouched before the last beat`() {
        // 崩すのは最後の 1 拍だけ。手前まで同じでないと、拍が分からなくなる。
        repeat(20) { seed ->
            val original = base(seed)
            val filled = PatternGenerator.fill(original, Random(seed))
            for (row in 0 until STEP_ROW_COUNT) {
                assertEquals(
                    "seed=$seed の $row 行目が手前から違う",
                    beforeWindow(original, row),
                    beforeWindow(filled, row),
                )
            }
        }
    }

    @Test
    fun `the chord and bass rows are left completely alone`() {
        // フィルのあいだ和音まで止まると、曲が切れたように聞こえる。
        repeat(20) { seed ->
            val original = base(seed)
            val filled = PatternGenerator.fill(original, Random(seed))
            assertEquals(original.rowAt(ROW_CHORD), filled.rowAt(ROW_CHORD))
            assertEquals(original.rowAt(ROW_BASS), filled.rowAt(ROW_BASS))
        }
    }

    @Test
    fun `the last beat always has something hit into it`() {
        repeat(20) { seed ->
            val filled = PatternGenerator.fill(base(seed), Random(seed))
            val hits = fillWindow.count { step ->
                (0 until DRUM_COUNT).any { filled.isOn(it, step) }
            }
            assertTrue("seed=$seed で叩き込みが無い", hits > 0)
        }
    }

    @Test
    fun `the last hit is accented so it hands over to the next bar`() {
        repeat(20) { seed ->
            val filled = PatternGenerator.fill(base(seed), Random(seed))
            val last = fillWindow.last { step -> (0 until DRUM_COUNT).any { filled.isOn(it, step) } }
            val accented = (0 until DRUM_COUNT).any {
                filled.isOn(it, last) && filled.levelAt(it, last) == Pattern.Level.ACCENT
            }
            assertTrue("seed=$seed の最後の 1 発が強くない", accented)
        }
    }

    @Test
    fun `the hats stop for the fill`() {
        // 刻みを止めないと叩き込みが埋もれる。
        val original = Pattern.of(
            "A",
            "x...x...x...x...",
            "....x.......x...",
            "xxxxxxxxxxxxxxxx",
        )
        val filled = PatternGenerator.fill(original, Random(1))
        fillWindow.forEach { step ->
            assertTrue("$step でハイハットが残っている", !filled.isOn(Voice.CLOSED_HAT.ordinal, step))
        }
    }

    @Test
    fun `the same seed gives the same fill`() {
        val original = base()
        assertEquals(PatternGenerator.fill(original, Random(9)), PatternGenerator.fill(original, Random(9)))
    }

    @Test
    fun `different seeds give different fills`() {
        val original = base()
        val fills = (0 until 40).map { PatternGenerator.fill(original, Random(it)) }.toSet()
        assertTrue("フィルが 1 種類しか出ない", fills.size > 1)
    }

    // --- 曲としての形 ---------------------------------------------------------

    @Test
    fun `a generated song puts a fill at the end of every block`() {
        val song = SongBuilder.build(
            Song.newSong("s", "test", 0L),
            Genre.ROCK.recipe(),
            MusicKey(0, Scale.MAJOR),
            8,
            Random(6),
        )
        val pattern = song.pattern(0)
        assertEquals(SongBuilder.BLOCK, pattern.barCount)
        // 手前の 3 小節は同じ刻み、最後だけ違う。
        assertEquals(pattern.at(0).rows, pattern.at(1).rows)
        assertEquals(pattern.at(1).rows, pattern.at(2).rows)
        assertNotEquals(pattern.at(2).rows, pattern.at(3).rows)
    }

    @Test
    fun `the fill lands on the fourth bar when the song plays`() {
        val song = SongBuilder.build(
            Song.newSong("s", "test", 0L),
            Genre.ROCK.recipe(),
            MusicKey(0, Scale.MAJOR),
            8,
            Random(6),
        )
        val plan = PlaybackPlan.arrangement(song)
        assertEquals(8, plan.barCount)
        // ブロックの 4 小節目（0 から数えて 3 と 7）だけが違う刻みになる。
        assertEquals(plan.patternAt(0).rows, plan.patternAt(1).rows)
        assertNotEquals(plan.patternAt(2).rows, plan.patternAt(3).rows)
        assertNotEquals(plan.patternAt(6).rows, plan.patternAt(7).rows)
    }

    @Test
    fun `a pattern written by hand is not touched`() {
        // フィルが入るのは作らせたときだけ。手で書いた曲は 1 小節のまま。
        assertEquals(1, Song.newSong("s", "test", 0L).pattern(0).barCount)
    }
}
