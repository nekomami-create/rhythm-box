package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class PlaybackEngineTest {

    private val sampleRate = 48_000
    private val bpm = 120

    /** 音色ごとに違う値を持つ 1 フレームだけのテスト用サンプル。 */
    private fun impulses() = List(DRUM_COUNT) { voice -> floatArrayOf((voice + 1) / 10f) }

    private fun engine() = PlaybackEngine(sampleRate, impulses())

    private fun framesPerStep() = sampleRate * secondsPerStep(bpm)

    private fun onsets(buffer: FloatArray, threshold: Float = 1e-4f): List<Int> =
        buffer.indices.filter { abs(buffer[it]) > threshold }

    private fun config(song: Song, plan: PlaybackPlan, loop: Boolean = true) = EngineConfig(
        plan = plan,
        bpm = song.bpm,
        masterVolume = 1f,
        trackVolumes = List(TRACK_COUNT) { 1f },
        mutes = List(TRACK_COUNT) { false },
        loop = loop,
    )

    /** [buffer] の一部を切り出した実効値。 */
    private fun rms(buffer: FloatArray, from: Int, to: Int): Double {
        var sum = 0.0
        for (i in from until minOf(to, buffer.size)) sum += buffer[i].toDouble() * buffer[i]
        return sqrt(sum / (to - from))
    }

    /** [frequency] Hz 成分の強さ（素朴な離散フーリエ変換）。 */
    private fun magnitudeAt(buffer: FloatArray, frequency: Double, from: Int, to: Int): Double {
        var real = 0.0
        var imaginary = 0.0
        for (i in from until minOf(to, buffer.size)) {
            val angle = 2 * PI * frequency * (i - from) / sampleRate
            real += buffer[i] * cos(angle)
            imaginary += buffer[i] * sin(angle)
        }
        return hypot(real, imaginary) / (to - from)
    }

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
            .copy(mutes = List(TRACK_COUNT) { it == 0 })
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
            .copy(masterVolume = 0.5f, trackVolumes = List(TRACK_COUNT) { 0.5f })
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
        val samples = List(DRUM_COUNT) { voice ->
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

    // --- コード / ベース / リード -------------------------------------------

    /** ドラムを鳴らさず、音程のある楽器だけを検証するためのエンジン。 */
    private fun silentDrumEngine() =
        PlaybackEngine(sampleRate, List(DRUM_COUNT) { FloatArray(1) })

    private fun chordSong(chord: Chord, vararg rowSpecs: String): Song =
        Song("s", "test", bpm = bpm)
            .withPattern(0, Pattern.of("A", *rowSpecs))
            .withPatternChord(0, chord)

    @Test
    fun `the bass plays the root of the bar's chord`() {
        val rows = Array(STEP_ROW_COUNT) { "................" }
        rows[ROW_BASS] = "x..............."
        val song = chordSong(Chord(9, ChordQuality.MINOR), *rows) // Am -> A2 = 110Hz
        val engine = silentDrumEngine()
        engine.config = config(song, PlaybackPlan.single(song, 0))
        engine.start()

        val buffer = FloatArray((framesPerStep() * 4).roundToInt())
        engine.render(buffer)

        val window = 0 until (framesPerStep() * 3).toInt()
        val atRoot = magnitudeAt(buffer, 110.0, window.first, window.last)
        val offRoot = magnitudeAt(buffer, 146.83, window.first, window.last) // D3（違う音）
        assertTrue("root=$atRoot off=$offRoot", atRoot > offRoot * 8)
    }

    @Test
    fun `the chord row sounds all the chord tones`() {
        val rows = Array(STEP_ROW_COUNT) { "................" }
        rows[ROW_CHORD] = "x..............."
        val song = chordSong(Chord(0, ChordQuality.MAJOR), *rows) // C: C4 E4 G4
        val engine = silentDrumEngine()
        engine.config = config(song, PlaybackPlan.single(song, 0))
        engine.start()

        val buffer = FloatArray((framesPerStep() * 8).roundToInt())
        engine.render(buffer)

        val to = (framesPerStep() * 6).toInt()
        val c4 = magnitudeAt(buffer, ToneSynth.frequency(60), 0, to)
        val e4 = magnitudeAt(buffer, ToneSynth.frequency(64), 0, to)
        val g4 = magnitudeAt(buffer, ToneSynth.frequency(67), 0, to)
        val d4 = magnitudeAt(buffer, ToneSynth.frequency(62), 0, to) // コードに含まれない音
        assertTrue("C4=$c4 D4=$d4", c4 > d4 * 8)
        assertTrue("E4=$e4 D4=$d4", e4 > d4 * 8)
        assertTrue("G4=$g4 D4=$d4", g4 > d4 * 8)
    }

    @Test
    fun `the chord follows the bar in an arrangement`() {
        val rows = Array(STEP_ROW_COUNT) { "................" }
        rows[ROW_CHORD] = "x..............."
        rows[ROW_BASS] = "x..............."
        val song = Song("s", "test", bpm = bpm)
            .withPattern(0, Pattern.of("A", *rows))
            .copy(
                arrangement = listOf(
                    ArrangementStep(0, 2, listOf(Chord(0, ChordQuality.MAJOR), Chord(5, ChordQuality.MAJOR))),
                ),
            )
        val engine = silentDrumEngine()
        engine.config = config(song, PlaybackPlan.arrangement(song), loop = false)
        engine.start()

        val bar = (framesPerStep() * STEPS_PER_BAR).toInt()
        val buffer = FloatArray(bar * 2)
        engine.render(buffer)

        // 1 小節目は C（ベース C2 = 65.4Hz）、2 小節目は F（F2 = 87.3Hz）
        val firstC = magnitudeAt(buffer, 65.41, 0, bar / 2)
        val firstF = magnitudeAt(buffer, 87.31, 0, bar / 2)
        val secondC = magnitudeAt(buffer, 65.41, bar, bar + bar / 2)
        val secondF = magnitudeAt(buffer, 87.31, bar, bar + bar / 2)
        assertTrue("1小節目 C=$firstC F=$firstF", firstC > firstF * 5)
        assertTrue("2小節目 F=$secondF C=$secondC", secondF > secondC * 5)
    }

    @Test
    fun `lead notes play the pitch that was punched in`() {
        val song = Song("s", "test", bpm = bpm)
            .withPattern(0, Pattern.empty("A").withLead(0, 0, 72)) // C5
        val engine = silentDrumEngine()
        engine.config = config(song, PlaybackPlan.single(song, 0))
        engine.start()

        val buffer = FloatArray((framesPerStep() * 4).roundToInt())
        engine.render(buffer)

        val to = (framesPerStep() * 3).toInt()
        val c5 = magnitudeAt(buffer, ToneSynth.frequency(72), 0, to)
        val c4 = magnitudeAt(buffer, ToneSynth.frequency(60), 0, to)
        assertTrue("C5=$c5 C4=$c4", c5 > c4 * 8)
    }

    @Test
    fun `a tied lead note keeps sounding past one beat`() {
        val held = MutableList(STEPS_PER_BAR) { Pattern.REST }
        held[0] = 72 // C5
        for (step in 1..11) held[step] = Pattern.TIE
        val short = MutableList(STEPS_PER_BAR) { Pattern.REST }
        short[0] = 72

        fun rmsAtStep(notes: List<Int>, step: Int): Double {
            val song = Song("s", "test", bpm = bpm)
                .withPattern(0, Pattern.empty("A").withLeads(listOf(notes)))
            val engine = silentDrumEngine()
            engine.config = config(song, PlaybackPlan.single(song, 0))
            engine.start()
            val buffer = FloatArray((framesPerStep() * STEPS_PER_BAR).roundToInt())
            engine.render(buffer)
            return rms(buffer, (framesPerStep() * step).toInt(), (framesPerStep() * (step + 1)).toInt())
        }

        // 8 ステップ目（2 拍目の裏）で、伸ばした音はまだ鳴っていて、伸ばしていない音は消えている。
        val tied = rmsAtStep(held, 8)
        val cut = rmsAtStep(short, 8)
        assertTrue("tied=$tied cut=$cut", tied > cut * 20)
        assertTrue("tied=$tied", tied > 0.01)
    }

    @Test
    fun `a tie carries the note across the bar line`() {
        val first = MutableList(STEPS_PER_BAR) { Pattern.REST }
        first[12] = 72 // C5
        for (step in 13..15) first[step] = Pattern.TIE
        val second = MutableList(STEPS_PER_BAR) { Pattern.REST }
        for (step in 0..3) second[step] = Pattern.TIE

        val song = Song("s", "test", bpm = bpm)
            .withPattern(0, Pattern.empty("A").withLeads(listOf(first, second)))
        val engine = silentDrumEngine()
        engine.config = config(song, PlaybackPlan.single(song, 0))
        engine.start()

        val buffer = FloatArray((framesPerStep() * STEPS_PER_BAR * 2).roundToInt())
        engine.render(buffer)

        // 2 小節目の頭（ステップ 16〜18）でも、1 小節目から続く C5 が鳴っている。
        val from = (framesPerStep() * STEPS_PER_BAR).toInt()
        val to = (framesPerStep() * (STEPS_PER_BAR + 3)).toInt()
        val c5 = magnitudeAt(buffer, ToneSynth.frequency(72), from, to)
        assertTrue("c5=$c5", c5 > 1e-3)
        // 小節をまたいでも音は鳴り直さない（頭で音量が跳ね上がらない）。
        val before = rms(buffer, (framesPerStep() * (STEPS_PER_BAR - 1)).toInt(), from)
        val after = rms(buffer, from, (framesPerStep() * (STEPS_PER_BAR + 1)).toInt())
        assertTrue("before=$before after=$after", after < before * 1.2)
    }

    /** [hold] を指定して、ベースを 1 発だけ鳴らした波形。 */
    private fun bassHit(hold: Float): FloatArray {
        val rows = Array(STEP_ROW_COUNT) { "................" }
        rows[ROW_BASS] = "x..............."
        val song = chordSong(Chord(0, ChordQuality.MAJOR), *rows)
            .let { it.withTrack(Instrument.BASS.trackIndex, it.track(Instrument.BASS.trackIndex).copy(hold = hold)) }
        val engine = silentDrumEngine()
        engine.config = config(song, PlaybackPlan.single(song, 0)).copy(holds = song.tracks.map { it.hold })
        engine.start()
        val buffer = FloatArray((framesPerStep() * STEPS_PER_BAR).roundToInt())
        engine.render(buffer)
        return buffer
    }

    @Test
    fun `the hold knob makes notes ring longer`() {
        // 8 ステップ目（2 拍後）。既定では切れていて、長くすればまだ鳴っている。
        val from = (framesPerStep() * 8).toInt()
        val to = (framesPerStep() * 9).toInt()
        val short = rms(bassHit(0f), from, to)
        val normal = rms(bassHit(ToneSynth.DEFAULT_HOLD), from, to)
        val long = rms(bassHit(1f), from, to)

        assertTrue("short=$short normal=$normal long=$long", long > normal * 5)
        assertTrue("short=$short normal=$normal", short <= normal)
    }

    @Test
    fun `the middle of the knob leaves the sound as it was`() {
        val plain = bassHit(ToneSynth.DEFAULT_HOLD)
        val rows = Array(STEP_ROW_COUNT) { "................" }
        rows[ROW_BASS] = "x..............."
        val song = chordSong(Chord(0, ChordQuality.MAJOR), *rows)
        val engine = silentDrumEngine()
        engine.config = config(song, PlaybackPlan.single(song, 0))
        engine.start()
        val untouched = FloatArray(plain.size)
        engine.render(untouched)

        for (i in plain.indices) {
            assertEquals("frame=$i", untouched[i], plain[i], 1e-6f)
        }
    }

    /** キックを 1 発だけ、指定した強さで鳴らした波形。 */
    private fun kick(level: Pattern.Level): FloatArray {
        val pattern = Pattern.of("A", "x...............")
            .withLevel(0, 0, level)
        val song = Song("s", "test", bpm = bpm).withPattern(0, pattern)
        val engine = engine()
        engine.config = config(song, PlaybackPlan.single(song, 0))
        engine.start()
        val buffer = FloatArray((framesPerStep() * 2).roundToInt())
        engine.render(buffer)
        return buffer
    }

    @Test
    fun `accents are louder and ghost notes quieter`() {
        val ghost = kick(Pattern.Level.GHOST).maxOf { abs(it) }
        val normal = kick(Pattern.Level.NORMAL).maxOf { abs(it) }
        val accent = kick(Pattern.Level.ACCENT).maxOf { abs(it) }

        assertTrue("ghost=$ghost normal=$normal accent=$accent", ghost < normal)
        assertTrue("ghost=$ghost normal=$normal accent=$accent", normal < accent)
    }

    @Test
    fun `a pattern with no accents sounds exactly as before`() {
        val plain = Pattern.of("A", "x...x...x...x...", "....x.......x...")
        val song = Song("s", "test", bpm = bpm).withPattern(0, plain)

        fun render(pattern: Pattern): FloatArray {
            val engine = engine()
            engine.config = config(song.withPattern(0, pattern), PlaybackPlan.single(song, 0))
            engine.start()
            val buffer = FloatArray((framesPerStep() * STEPS_PER_BAR).roundToInt())
            engine.render(buffer)
            return buffer
        }

        // 強弱を付けてから外したものと、最初から付けていないものが一致する。
        val touched = plain.withLevel(0, 0, Pattern.Level.ACCENT).withLevel(0, 0, Pattern.Level.NORMAL)
        val before = render(plain)
        val after = render(touched)
        for (i in before.indices) assertEquals("frame=$i", before[i], after[i], 0f)
    }

    @Test
    fun `the chord row follows the accent too`() {
        val rows = Array(STEP_ROW_COUNT) { "................" }
        rows[ROW_CHORD] = "x..............."
        fun peak(level: Pattern.Level): Float {
            val song = chordSong(Chord(0, ChordQuality.MAJOR), *rows).let {
                it.withPattern(0, it.pattern(0).withLevel(ROW_CHORD, 0, level))
            }
            val engine = silentDrumEngine()
            engine.config = config(song, PlaybackPlan.single(song, 0))
            engine.start()
            val buffer = FloatArray((framesPerStep() * 4).roundToInt())
            engine.render(buffer)
            return buffer.maxOf { abs(it) }
        }
        assertTrue(peak(Pattern.Level.GHOST) < peak(Pattern.Level.NORMAL))
        assertTrue(peak(Pattern.Level.NORMAL) < peak(Pattern.Level.ACCENT))
    }

    @Test
    fun `notes stop after their gate instead of droning on`() {
        val rows = Array(STEP_ROW_COUNT) { "................" }
        rows[ROW_BASS] = "x..............." // 次の音が無いので上限（4 ステップ）まで
        val song = chordSong(Chord(0, ChordQuality.MAJOR), *rows)
        val engine = silentDrumEngine()
        engine.config = config(song, PlaybackPlan.single(song, 0))
        engine.start()

        val buffer = FloatArray((framesPerStep() * 8).roundToInt())
        engine.render(buffer)

        val step = framesPerStep().toInt()
        val whilePlaying = rms(buffer, step / 2, step * 2)
        val afterGate = rms(buffer, step * 6, step * 7)
        assertTrue("鳴っている間 $whilePlaying", whilePlaying > 0.02)
        assertTrue("ゲート後 $afterGate", afterGate < whilePlaying * 0.2)
    }

    @Test
    fun `muting the chord track silences it without touching the bass`() {
        val rows = Array(STEP_ROW_COUNT) { "................" }
        rows[ROW_CHORD] = "x..............."
        rows[ROW_BASS] = "x..............."
        val song = chordSong(Chord(0, ChordQuality.MAJOR), *rows)

        fun render(muteChord: Boolean): FloatArray {
            val engine = silentDrumEngine()
            engine.config = config(song, PlaybackPlan.single(song, 0))
                .copy(mutes = List(TRACK_COUNT) { muteChord && it == Instrument.CHORD.trackIndex })
            engine.start()
            return FloatArray((framesPerStep() * 4).roundToInt()).also { engine.render(it) }
        }

        val to = (framesPerStep() * 3).toInt()
        val plain = render(muteChord = false)
        val muted = render(muteChord = true)

        // コードの構成音（E4）はミュートで消え、ベース（C2）はそのまま残る。
        val e4Plain = magnitudeAt(plain, ToneSynth.frequency(64), 0, to)
        val e4Muted = magnitudeAt(muted, ToneSynth.frequency(64), 0, to)
        assertTrue("E4 通常=$e4Plain ミュート=$e4Muted", e4Muted < e4Plain * 0.02)

        val c2Plain = magnitudeAt(plain, 65.41, 0, to)
        val c2Muted = magnitudeAt(muted, 65.41, 0, to)
        assertEquals(c2Plain, c2Muted, c2Plain * 0.05)
        assertTrue("ベース C2=$c2Muted", c2Muted > 1e-3)
    }

    @Test
    fun `stopping releases sustained chords`() {
        val rows = Array(STEP_ROW_COUNT) { "................" }
        rows[ROW_CHORD] = "x..............."
        val song = chordSong(Chord(0, ChordQuality.MAJOR), *rows)
        val engine = silentDrumEngine()
        engine.config = config(song, PlaybackPlan.single(song, 0))
        engine.start()

        val head = FloatArray((framesPerStep() * 2).roundToInt())
        engine.render(head)
        assertTrue(rms(head, 0, head.size) > 0.02)

        val sustained = rms(head, head.size / 2, head.size)

        engine.stop()
        val tail = FloatArray(sampleRate * 4) // 4 秒
        engine.render(tail)
        // 離鍵後は指数的に減衰する（コードの減衰時定数は 0.34 秒）
        val justAfterStop = rms(tail, 0, sampleRate / 20)
        val oneSecondLater = rms(tail, sampleRate, sampleRate + sampleRate / 20)
        assertTrue("停止直後 $justAfterStop", justAfterStop > sustained * 0.5)
        assertTrue("1 秒後 $oneSecondLater", oneSecondLater < justAfterStop * 0.1)
        // 十分に小さくなった音は解放される（鳴りっぱなしの発振器が残らない）
        assertEquals(0.0, rms(tail, tail.size - sampleRate / 20, tail.size), 0.0)
    }

    @Test
    fun `preview sounds can be triggered while stopped`() {
        val engine = silentDrumEngine()
        engine.config = config(Song("s", "test"), PlaybackPlan(emptyList(), emptyList()))
        engine.previewChord(Chord(0, ChordQuality.MAJOR))

        val buffer = FloatArray(sampleRate / 4)
        assertFalse(engine.render(buffer)) // 再生はしていない
        assertTrue(rms(buffer, 0, buffer.size) > 0.01)
    }

    @Test
    fun `the melody changes on each repetition of the same pattern`() {
        // 同じドラムパターンを 2 小節鳴らし、1 小節目と 2 小節目で違う音が出ることを確かめる。
        val pattern = Pattern.empty("A")
            .withLeadBarCount(2)
            .withLead(0, 0, 72) // C5
            .withLead(1, 0, 79) // G5
        val song = Song("s", "test", bpm = bpm)
            .withPattern(0, pattern)
            .copy(arrangement = listOf(ArrangementStep(0, 2)))
        val engine = silentDrumEngine()
        engine.config = config(song, PlaybackPlan.arrangement(song), loop = false)
        engine.start()

        val bar = (framesPerStep() * STEPS_PER_BAR).toInt()
        val buffer = FloatArray(bar * 2)
        engine.render(buffer)

        val firstC5 = magnitudeAt(buffer, ToneSynth.frequency(72), 0, bar / 2)
        val firstG5 = magnitudeAt(buffer, ToneSynth.frequency(79), 0, bar / 2)
        val secondC5 = magnitudeAt(buffer, ToneSynth.frequency(72), bar, bar + bar / 2)
        val secondG5 = magnitudeAt(buffer, ToneSynth.frequency(79), bar, bar + bar / 2)
        assertTrue("1 小節目 C5=$firstC5 G5=$firstG5", firstC5 > firstG5 * 5)
        assertTrue("2 小節目 G5=$secondG5 C5=$secondC5", secondG5 > secondC5 * 5)
    }
}
