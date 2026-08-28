package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 打ち込みに置いたコードが、再生と書き出しに届くか。 */
class StepChordsTest {

    private val c = Chord(0)
    private val f = Chord(5)
    private val g = Chord(7)
    private val am = Chord(9, ChordQuality.MINOR)

    /** CHD と BAS を鳴らす 1 小節のパターンを持つ曲。 */
    private fun song(chordRow: String = "x...x...x...x...", bassRow: String = "x...x...x...x..."): Song {
        val rows = Array(STEP_ROW_COUNT) { "................" }
        rows[ROW_CHORD] = chordRow
        rows[ROW_BASS] = bassRow
        return Song("s", "test")
            .withPattern(0, Pattern.of("A", *rows))
            .withPatternChord(0, c)
    }

    private fun place(base: Song, vararg placed: Pair<Int, Chord>): Song {
        var pattern = base.pattern(0)
        placed.forEach { (step, chord) -> pattern = pattern.withChordAt(0, step, chord) }
        return base.withPattern(0, pattern)
    }

    @Test
    fun `with nothing placed the song still takes its chords from the arrangement`() {
        val base = song().copy(arrangement = listOf(ArrangementStep(0, 2, listOf(am, f))))
        val plan = PlaybackPlan.arrangement(base)
        assertEquals(am, plan.chordAt(0, 0))
        assertEquals(am, plan.chordAt(0, 15))
        assertEquals(f, plan.chordAt(1, 0))
    }

    @Test
    fun `a chord placed in the pattern takes over from the arrangement`() {
        // 置いたら、そのパターンのコードは打ち込みが決める。
        val base = song().copy(arrangement = listOf(ArrangementStep(0, 1, listOf(am))))
        val plan = PlaybackPlan.arrangement(place(base, 0 to c))
        assertEquals(c, plan.chordAt(0, 0))
    }

    @Test
    fun `two chords in one bar both come out`() {
        // これが今回の狙い。1 小節に 2 つ置ける＝本来のツーファイブが書ける。
        val plan = PlaybackPlan.single(place(song(), 0 to g, 8 to c), 0)
        assertEquals(g, plan.chordAt(0, 0))
        assertEquals(g, plan.chordAt(0, 7))
        assertEquals(c, plan.chordAt(0, 8))
        assertEquals(c, plan.chordAt(0, 15))
    }

    @Test
    fun `the plan lists every place the harmony moves`() {
        val plan = PlaybackPlan.single(place(song(), 0 to g, 8 to c), 0)
        assertEquals(listOf(0 to 0, 0 to 8), plan.changes.map { it.bar to it.step })
    }

    @Test
    fun `the voices are worked out at every change, not just at bar lines`() {
        val plan = PlaybackPlan.single(place(song(), 0 to g, 8 to c), 0)
        // どちらの変わり目にも音が入っていて、しかも別の和音なので別の音になる。
        assertTrue(plan.voicingAt(0, 0).isNotEmpty())
        assertTrue(plan.voicingAt(0, 8).isNotEmpty())
        assertNotEquals(plan.voicingAt(0, 0), plan.voicingAt(0, 8))
        // 繋がりを解いた結果になっている（素の積み方より動きが小さい）。
        val led = Voicing.lead(listOf(g, c))
        assertEquals(led[0], plan.voicingAt(0, 0))
        assertEquals(led[1], plan.voicingAt(0, 8))
    }

    @Test
    fun `the next chord for the bass is the next change, not the next bar`() {
        val plan = PlaybackPlan.single(place(song(), 0 to g, 8 to c), 0)
        assertEquals(c, plan.nextChordAt(0, 0))
        // 最後まで来たらループの頭へ戻る。
        assertEquals(g, plan.nextChordAt(0, 8))
    }

    @Test
    fun `the bass knows where the harmony moves inside the bar`() {
        val plan = PlaybackPlan.single(place(song(), 0 to g, 8 to c), 0)
        assertEquals(8, plan.nextChangeStepAt(0, 0))
        assertEquals(8, plan.nextChangeStepAt(0, 7))
        // 8 より後はこの小節では変わらない＝小節の終わりが向かう先。
        assertEquals(STEPS_PER_BAR, plan.nextChangeStepAt(0, 8))
    }

    @Test
    fun `with nothing placed the bass behaves exactly as before`() {
        val plan = PlaybackPlan.single(song(), 0)
        assertEquals(STEPS_PER_BAR, plan.nextChangeStepAt(0, 0))
        assertEquals(STEPS_PER_BAR, plan.nextChangeStepAt(0, 15))
    }

    /** [placed] を置いた 1 小節を鳴らして、左チャンネルを返す。 */
    private fun render(vararg placed: Pair<Int, Chord>): FloatArray {
        val sampleRate = 48_000
        val song = place(song(chordRow = "x.......x......."), *placed)
        val engine = PlaybackEngine(sampleRate, List(DRUM_COUNT) { floatArrayOf(0f) })
        engine.config = EngineConfig(
            plan = PlaybackPlan.single(song, 0),
            bpm = 120,
            masterVolume = 1f,
            trackVolumes = List(TRACK_COUNT) { 1f },
            mutes = List(TRACK_COUNT) { it != Instrument.CHORD.trackIndex },
        )
        engine.start()
        val frames = (sampleRate * secondsPerStep(120) * STEPS_PER_BAR).toInt()
        val out = FloatArray(frames * CHANNELS)
        engine.render(out)
        return FloatArray(frames) { out[it * CHANNELS] }
    }

    @Test
    fun `the engine plays the chord that was placed, not the one before it`() {
        // 8 ステップ目に置いた和音だけを変えて、後半の音が変わることを見る。
        // 前半は同じままでなければ、変えたつもりの無い所まで動いていることになる。
        val moved = render(0 to g, 8 to c)
        val same = render(0 to g, 8 to g)
        val half = moved.size / 2

        var frontDiff = 0
        var backDiff = 0
        for (i in moved.indices) {
            if (moved[i] != same[i]) if (i < half) frontDiff++ else backDiff++
        }
        assertEquals("前半まで変わっている", 0, frontDiff)
        assertTrue("後半が変わっていない", backDiff > 0)
        assertTrue("そもそも鳴っていない", moved.any { kotlin.math.abs(it) > 1e-4f })
    }
}
