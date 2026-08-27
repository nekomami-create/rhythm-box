package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/** 左右 2 チャンネルと、トラックごとの定位。 */
class StereoTest {

    private val sampleRate = 48_000
    private val bpm = 120

    /** 音色ごとに違う値を持つ 1 フレームだけのテスト用サンプル。 */
    private fun impulses() = List(DRUM_COUNT) { voice -> floatArrayOf((voice + 1) / 10f) }

    private fun engine() = PlaybackEngine(sampleRate, impulses())

    private fun framesPerStep() = sampleRate * secondsPerStep(bpm)

    private fun config(song: Song, plan: PlaybackPlan, pans: List<Float>) = EngineConfig(
        plan = plan,
        bpm = song.bpm,
        masterVolume = 1f,
        trackVolumes = List(TRACK_COUNT) { 1f },
        mutes = List(TRACK_COUNT) { false },
        trackPans = pans,
    )

    private fun centred() = List(TRACK_COUNT) { 0f }

    /** キックだけを 1 拍目に置いた 1 小節を描いて、左右を返す。 */
    private fun renderKick(pans: List<Float>): Pair<FloatArray, FloatArray> {
        val song = Song("s", "test", bpm = bpm)
            .withPattern(0, Pattern.of("A", "x..............."))
        val engine = engine()
        engine.config = config(song, PlaybackPlan.single(song, 0), pans)
        engine.start()

        val frames = (framesPerStep() * STEPS_PER_BAR).roundToInt()
        val out = FloatArray(frames * CHANNELS)
        engine.render(out)
        return FloatArray(frames) { out[it * CHANNELS] } to FloatArray(frames) { out[it * CHANNELS + 1] }
    }

    private fun peak(buffer: FloatArray) = buffer.maxOf { abs(it) }

    @Test
    fun `a centred song comes out the same on both sides`() {
        val song = Song.newSong("s", "ステレオ", 0L).copy(bpm = bpm)
        val audio = OfflineRenderer.render(
            song,
            PlaybackPlan.arrangement(song),
            DrumSynth.renderAll(sampleRate),
            sampleRate,
            tailSeconds = 0.5,
        )
        assertTrue("無音になっている", peak(audio) > 0.2f)
        // 中央は左右とも音量 1.0。丸め誤差すら入らないので、完全に一致する。
        for (frame in 0 until OfflineRenderer.frames(audio)) {
            val at = frame * CHANNELS
            if (audio[at] != audio[at + 1]) {
                throw AssertionError("frame $frame: ${audio[at]} != ${audio[at + 1]}")
            }
        }
    }

    @Test
    fun `panning hard left empties the right side`() {
        val pans = centred().toMutableList().also { it[Voice.KICK.ordinal] = -1f }
        val (left, right) = renderKick(pans)
        assertEquals(0.1f, peak(left), 1e-6f)
        assertEquals(0f, peak(right), 1e-6f)
    }

    @Test
    fun `panning hard right empties the left side`() {
        val pans = centred().toMutableList().also { it[Voice.KICK.ordinal] = 1f }
        val (left, right) = renderKick(pans)
        assertEquals(0f, peak(left), 1e-6f)
        assertEquals(0.1f, peak(right), 1e-6f)
    }

    @Test
    fun `the near side keeps its level when a track is panned`() {
        // 中央を 1.0 に置いた定位なので、左に振っても左は大きくならない。
        // 振っただけで音量が変わると、これまで決めてきた音量が全部狂う。
        val (centreLeft, _) = renderKick(centred())
        val (hardLeft, _) = renderKick(centred().toMutableList().also { it[Voice.KICK.ordinal] = -1f })
        assertEquals(peak(centreLeft), peak(hardLeft), 1e-6f)
    }

    @Test
    fun `half way across halves the far side`() {
        val pans = centred().toMutableList().also { it[Voice.KICK.ordinal] = -0.5f }
        val (left, right) = renderKick(pans)
        assertEquals(0.1f, peak(left), 1e-6f)
        assertEquals(0.05f, peak(right), 1e-6f)
    }

    @Test
    fun `each track is placed on its own`() {
        val song = Song("s", "test", bpm = bpm)
            .withPattern(0, Pattern.of("A", "x...............", "....x..........."))
        val pans = centred().toMutableList().also {
            it[Voice.KICK.ordinal] = -1f
            it[Voice.SNARE.ordinal] = 1f
        }
        val engine = engine()
        engine.config = config(song, PlaybackPlan.single(song, 0), pans)
        engine.start()

        val frames = (framesPerStep() * STEPS_PER_BAR).roundToInt()
        val out = FloatArray(frames * CHANNELS)
        engine.render(out)
        val left = FloatArray(frames) { out[it * CHANNELS] }
        val right = FloatArray(frames) { out[it * CHANNELS + 1] }

        // 左にはキック（0.1）だけ、右にはスネア（0.2）だけが立つ。
        val leftHits = left.indices.filter { abs(left[it]) > 1e-4f }
        val rightHits = right.indices.filter { abs(right[it]) > 1e-4f }
        assertEquals("左に立った点: $leftHits", 1, leftHits.size)
        assertEquals("右に立った点: $rightHits", 1, rightHits.size)
        assertEquals(0, leftHits.first())
        val snareAt = (framesPerStep() * 4).roundToInt()
        assertTrue("スネアの位置 ${rightHits.first()} != ~$snareAt", abs(rightHits.first() - snareAt) <= 2)
        assertEquals(0.1f, left[leftHits.first()], 1e-6f)
        assertEquals(0.2f, right[rightHits.first()], 1e-6f)
    }

    @Test
    fun `an out of range setting is clamped instead of inverting the sides`() {
        val (left, right) = renderKick(centred().toMutableList().also { it[Voice.KICK.ordinal] = -5f })
        assertEquals(0.1f, peak(left), 1e-6f)
        assertEquals(0f, peak(right), 1e-6f)
    }

    @Test
    fun `pan gains meet at one in the middle`() {
        assertEquals(1f, panLeft(0f), 1e-6f)
        assertEquals(1f, panRight(0f), 1e-6f)
        assertEquals(1f, panLeft(-1f), 1e-6f)
        assertEquals(0f, panRight(-1f), 1e-6f)
        assertEquals(0f, panLeft(1f), 1e-6f)
        assertEquals(1f, panRight(1f), 1e-6f)
        assertEquals(0.25f, panRight(-0.75f), 1e-6f)
        assertEquals(0.25f, panLeft(0.75f), 1e-6f)
    }

    @Test
    fun `the limiter holds the image instead of pulling it sideways`() {
        // 大きいほうで抑える量を決めて、左右に同じだけ掛ける。
        val frame = floatArrayOf(1.6f, 0.8f)
        PlaybackEngine.limitFrame(frame, 0)
        val scale = PlaybackEngine.limit(1.6f) / 1.6f
        assertEquals(1.6f * scale, frame[0], 1e-6f)
        assertEquals(0.8f * scale, frame[1], 1e-6f)
        // 左右の比が変わらない＝定位が動かない。
        assertEquals(0.5f, frame[1] / frame[0], 1e-6f)
    }

    @Test
    fun `a centred frame is limited exactly as one channel would be`() {
        val frame = floatArrayOf(-1.6f, -1.6f)
        PlaybackEngine.limitFrame(frame, 0)
        assertEquals(PlaybackEngine.limit(-1.6f), frame[0], 1e-6f)
        assertEquals(PlaybackEngine.limit(-1.6f), frame[1], 1e-6f)
    }

    @Test
    fun `quiet frames pass through the limiter untouched`() {
        val frame = floatArrayOf(0.5f, -0.3f)
        PlaybackEngine.limitFrame(frame, 0)
        assertEquals(0.5f, frame[0], 1e-6f)
        assertEquals(-0.3f, frame[1], 1e-6f)
    }

    @Test
    fun `the exported file carries the placement too`() {
        val song = Song.newSong("s", "書き出し", 0L).copy(bpm = bpm).let { base ->
            base.copy(tracks = base.tracks.map { it.copy(pan = -1f) })
        }
        val audio = OfflineRenderer.render(
            song,
            PlaybackPlan.arrangement(song),
            DrumSynth.renderAll(sampleRate),
            sampleRate,
            tailSeconds = 0.5,
        )
        val left = FloatArray(OfflineRenderer.frames(audio)) { audio[it * CHANNELS] }
        val right = FloatArray(OfflineRenderer.frames(audio)) { audio[it * CHANNELS + 1] }
        assertTrue("左が鳴っていない", peak(left) > 0.2f)
        assertEquals("右に漏れている", 0f, peak(right), 1e-6f)
    }

    @Test
    fun `the metronome stays in the middle wherever the tracks are`() {
        val song = Song("s", "test", bpm = bpm).withPattern(0, Pattern.of("A", "................"))
        val engine = engine()
        engine.config = config(song, PlaybackPlan.single(song, 0), List(TRACK_COUNT) { -1f })
            .copy(metronome = true)
        engine.start()

        val frames = (framesPerStep() * STEPS_PER_BAR).roundToInt()
        val out = FloatArray(frames * CHANNELS)
        engine.render(out)
        for (frame in 0 until frames) {
            val at = frame * CHANNELS
            assertEquals("frame $frame", out[at], out[at + 1], 1e-6f)
        }
        assertTrue("クリックが鳴っていない", peak(out) > 1e-3f)
    }
}
