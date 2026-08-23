package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

class PlaybackEngineTest {

    private val sampleRate = 48_000
    private val bpm = 120

    /** 音色ごとに違う値を持つ 1 フレームだけのテスト用サンプル。 */
    private fun impulses() = List(VOICE_COUNT) { voice -> floatArrayOf((voice + 1) / 10f) }

    private fun engine() = PlaybackEngine(sampleRate, impulses())

    private fun framesPerStep() = sampleRate * secondsPerStep(bpm)

    private fun onsets(buffer: FloatArray, threshold: Float = 1e-4f): List<Int> =
        buffer.indices.filter { abs(buffer[it]) > threshold }

    private fun config(song: Song, plan: PlaybackPlan, loop: Boolean = true) = EngineConfig(
        plan = plan,
        bpm = song.bpm,
        masterVolume = 1f,
        trackVolumes = List(VOICE_COUNT) { 1f },
        mutes = List(VOICE_COUNT) { false },
        loop = loop,
    )

    @Test
    fun `steps fire on the beat`() {
        val song = Song("s", "test", bpm = bpm)
            .withPattern(0, Pattern.of("A", "x...x...x...x..."))
        val engine = engine()
        engine.config = config(song, PlaybackPlan.single(song, 0))
        engine.start()

        val bar = (framesPerStep() * STEPS_PER_BAR).roundToInt()
        val buffer = FloatArray(bar)
        engine.render(buffer)

        val hits = onsets(buffer)
        assertEquals(4, hits.size)
        listOf(0, 4, 8, 12).forEachIndexed { index, step ->
            val expected = (framesPerStep() * step).roundToInt()
            assertTrue("step $step: ${hits[index]} != ~$expected", abs(hits[index] - expected) <= 2)
        }
        // キック（音色 0）の振幅が出ていること
        assertEquals(0.1f, buffer[hits[0]], 1e-6f)
    }

    @Test
    fun `pattern loops seamlessly across bars`() {
        val song = Song("s", "test", bpm = bpm).withPattern(0, Pattern.of("A", "x..............."))
        val engine = engine()
        engine.config = config(song, PlaybackPlan.single(song, 0))
        engine.start()

        val bar = framesPerStep() * STEPS_PER_BAR
        val buffer = FloatArray((bar * 3).roundToInt())
        assertTrue(engine.render(buffer))

        val hits = onsets(buffer)
        assertEquals(3, hits.size)
        assertTrue(abs(hits[1] - bar.roundToInt()) <= 2)
        assertTrue(abs(hits[2] - (bar * 2).roundToInt()) <= 2)
    }

    @Test
    fun `arrangement plays each pattern for the requested number of bars`() {
        val song = Song("s", "test", bpm = bpm)
            .withPattern(0, Pattern.of("A", "x..............."))
            .withPattern(1, Pattern.of("B", "................", "x..............."))
            .copy(arrangement = listOf(ArrangementStep(0, 2), ArrangementStep(1, 1)))
        val engine = engine()
        engine.config = config(song, PlaybackPlan.arrangement(song), loop = false)
        engine.start()

        val bar = framesPerStep() * STEPS_PER_BAR
        val buffer = FloatArray((bar * 3).roundToInt())
        engine.render(buffer)

        val hits = onsets(buffer)
        assertEquals(3, hits.size)
        assertEquals(0.1f, buffer[hits[0]], 1e-6f) // A のキック
        assertEquals(0.1f, buffer[hits[1]], 1e-6f)
        assertEquals(0.2f, buffer[hits[2]], 1e-6f) // B のスネア
    }

    @Test
    fun `song stops at the end when looping is off`() {
        val song = Song("s", "test", bpm = bpm)
            .withPattern(0, Pattern.of("A", "x..............."))
            .copy(arrangement = listOf(ArrangementStep(0, 1)))
        val engine = engine()
        engine.config = config(song, PlaybackPlan.arrangement(song), loop = false)
        engine.start()

        val bar = framesPerStep() * STEPS_PER_BAR
        val buffer = FloatArray((bar * 2).roundToInt())
        assertFalse(engine.render(buffer))
        assertFalse(engine.isPlaying)
        assertEquals(1, onsets(buffer).size)
    }

    @Test
    fun `muted tracks stay silent`() {
        val song = Song("s", "test", bpm = bpm)
            .withPattern(0, Pattern.of("A", "x...x...x...x...", "..x...x...x...x."))
        val engine = engine()
        engine.config = config(song, PlaybackPlan.single(song, 0))
            .copy(mutes = List(VOICE_COUNT) { it == 0 })
        engine.start()

        val buffer = FloatArray((framesPerStep() * STEPS_PER_BAR).roundToInt())
        engine.render(buffer)

        val hits = onsets(buffer)
        assertEquals(4, hits.size)
        hits.forEach { assertEquals(0.2f, buffer[it], 1e-6f) } // スネアだけが残る
    }

    @Test
    fun `track volume scales the mix`() {
        val song = Song("s", "test", bpm = bpm).withPattern(0, Pattern.of("A", "x..............."))
        val engine = engine()
        engine.config = config(song, PlaybackPlan.single(song, 0))
            .copy(masterVolume = 0.5f, trackVolumes = List(VOICE_COUNT) { 0.5f })
        engine.start()

        val buffer = FloatArray(64)
        engine.render(buffer)
        assertEquals(0.1f * 0.25f, buffer[0], 1e-6f)
    }

    @Test
    fun `timeline reports the audible step position`() {
        val song = Song("s", "test", bpm = bpm).withPattern(0, Pattern.of("A", "xxxxxxxxxxxxxxxx"))
        val engine = engine()
        engine.config = config(song, PlaybackPlan.single(song, 0))
        engine.start()

        val buffer = FloatArray((framesPerStep() * STEPS_PER_BAR).roundToInt())
        engine.render(buffer)

        assertEquals(StepTimeline.Position(0, 0), engine.timeline.positionAt(0))
        assertEquals(StepTimeline.Position(0, 3), engine.timeline.positionAt((framesPerStep() * 3.5).toLong()))
        assertEquals(StepTimeline.Position(0, 15), engine.timeline.positionAt(Long.MAX_VALUE))
    }

    @Test
    fun `closed hat chokes the open hat`() {
        // 開いたハイハットの余韻が、次のクローズドで止まることを確認する。
        val samples = List(VOICE_COUNT) { voice ->
            when (voice) {
                Voice.OPEN_HAT.ordinal -> FloatArray(sampleRate) { 0.5f } // 1 秒鳴り続ける
                else -> floatArrayOf(0.25f)
            }
        }
        val engine = PlaybackEngine(sampleRate, samples)
        val song = Song("s", "test", bpm = bpm).withPattern(
            0,
            Pattern.empty("A")
                .set(Voice.OPEN_HAT.ordinal, 0, true)
                .set(Voice.CLOSED_HAT.ordinal, 4, true),
        )
        engine.config = config(song, PlaybackPlan.single(song, 0))
        engine.start()

        val buffer = FloatArray((framesPerStep() * 8).roundToInt())
        engine.render(buffer)

        val chokeFrame = (framesPerStep() * 4).roundToInt()
        assertEquals(0.5f, buffer[chokeFrame - 10], 1e-6f)
        assertEquals(0f, buffer[chokeFrame + 10], 1e-6f)
    }

    @Test
    fun `frame position keeps counting while stopped so it matches the audio device`() {
        // 表示位置の計算に使うので、フレーム数は「出力に書き出したぶん」と一致していないといけない。
        val song = Song("s", "test", bpm = bpm).withPattern(0, Pattern.of("A", "x..............."))
        val engine = engine()
        engine.config = config(song, PlaybackPlan.single(song, 0))

        engine.render(FloatArray(1_000)) // 停止中でも進む
        assertEquals(1_000L, engine.framePosition)

        engine.start()
        engine.render(FloatArray(500))
        assertEquals(1_500L, engine.framePosition)
        // 再生開始位置（1000 フレーム目）のステップが記録されている
        assertEquals(StepTimeline.Position(0, 0), engine.timeline.positionAt(1_200))
        assertEquals(null, engine.timeline.positionAt(999))
    }

    @Test
    fun `restarting playback rewinds to the first step`() {
        val song = Song("s", "test", bpm = bpm).withPattern(0, Pattern.of("A", "x..............."))
        val engine = engine()
        engine.config = config(song, PlaybackPlan.single(song, 0))
        engine.start()
        engine.render(FloatArray((framesPerStep() * 4).roundToInt()))
        engine.stop()
        engine.start()

        val buffer = FloatArray(32)
        engine.render(buffer)
        assertEquals(0.1f, buffer[0], 1e-6f)
    }

    @Test
    fun `limiter keeps quiet signals untouched and loud ones in range`() {
        assertEquals(0.5f, PlaybackEngine.limit(0.5f), 1e-6f)
        assertEquals(-0.8f, PlaybackEngine.limit(-0.8f), 1e-6f)
        assertTrue(PlaybackEngine.limit(1.6f) < 1f)
        assertTrue(PlaybackEngine.limit(1.6f) > 0.85f)
        assertTrue(PlaybackEngine.limit(-40f) > -1f)
        assertEquals(-PlaybackEngine.limit(3f), PlaybackEngine.limit(-3f), 1e-6f)
    }
}
