package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 1/60 秒ごとに音を動かすぶんの確認。
 *
 * チップ音源の「動き」はテンポではなく実機のフレームに紐付いているので、
 * ここでの刻みも BPM とは無関係な 1/60 秒。
 */
class ChipModulationTest {

    private val sampleRate = 44_100

    private fun song(style: ChordStyle, voice: ToneSynth.LeadVoice): Song =
        Song(id = "s", name = "s", bpm = 120)
            .copy(chordStyle = style, leadVoice = voice)
            // コード行だけを小節の頭で鳴らす。ほかの行は黙らせる。
            .withPattern(0, Pattern.of("A").let { base ->
                base.set(ROW_CHORD, 0, true)
            })
            .withPatternChord(0, Chord(0, ChordQuality.MAJOR))

    private fun render(song: Song, frames: Int, vibrato: Float = 0f): FloatArray {
        val engine = PlaybackEngine(sampleRate, List(DRUM_COUNT) { FloatArray(1) })
        engine.config = EngineConfig(
                plan = PlaybackPlan.single(song, 0),
                bpm = song.bpm,
                masterVolume = 1f,
                trackVolumes = List(TRACK_COUNT) { 1f },
                mutes = List(TRACK_COUNT) { false },
                chordStyle = song.chordStyle,
                leadVoice = song.leadVoice,
                leadVibrato = vibrato,
                loop = true,
        )
        engine.start()
        val out = FloatArray(frames)
        engine.render(out)
        return out
    }

    /** [from] から [length] フレームぶんだけ切り出した [frequency] Hz 成分の強さ。 */
    private fun magnitudeIn(buffer: FloatArray, frequency: Double, from: Int, length: Int): Double {
        var real = 0.0
        var imaginary = 0.0
        for (i in from until minOf(from + length, buffer.size)) {
            val angle = 2 * PI * frequency * (i - from) / sampleRate
            real += buffer[i] * cos(angle)
            imaginary += buffer[i] * sin(angle)
        }
        return hypot(real, imaginary) / length
    }

    /** 1 刻み（1/60 秒）ぶんのフレーム数。 */
    private val framesPerTick = (sampleRate / ToneSynth.TICKS_PER_SECOND).toInt()

    // --- 回す順番 -----------------------------------------------------------

    @Test
    fun `the arpeggio walks the chord tones and starts over`() {
        val order = (0 until 9).map { ToneSynth.arpeggioIndex(it, 3) }
        assertEquals(listOf(0, 1, 2, 0, 1, 2, 0, 1, 2), order)
    }

    @Test
    fun `a chord with no notes does not break the walk`() {
        assertEquals(0, ToneSynth.arpeggioIndex(7, 0))
    }

    @Test
    fun `only the lowest note of the chord gets a voice`() {
        // 実機は 1 声部で 1 音しか出せない。速く回すことで和音に聞こえさせている。
        val voicing = Chord(0, ChordQuality.MAJOR).voicing()
        assertEquals(listOf(voicing.first()), ChordStyle.CHIP_ARPEGGIO.notesAt(voicing, 0))
        // 何回目かに関係なく、鳴らすのは常に 1 音（回すのは発音中の仕事）。
        assertEquals(listOf(voicing.first()), ChordStyle.CHIP_ARPEGGIO.notesAt(voicing, 5))
    }

    // --- 実際に鳴らしてみる -------------------------------------------------

    @Test
    fun `the arpeggio steps through the chord tones every sixtieth of a second`() {
        // 全体をまとめて周波数分析しても構成音は出てこない。1 つの音が
        // 途切れ途切れに現れ、しかも戻るたびに位相が繋がっていないので打ち消し合う。
        // その打ち消し合い（元の音の周りに並ぶ側波帯）こそが、和音ではなく
        // 1 つのきらめいた音色に聞こえる理由なので、ここでは刻みごとに測る。
        val out = render(song(ChordStyle.CHIP_ARPEGGIO, ToneSynth.LeadVoice.PULSE_25), 22_050)
        val tones = Chord(0, ChordQuality.MAJOR).voicing().map { ToneSynth.frequency(it) }

        val loudestEachTick = (0 until 12).map { tick ->
            val strengths = tones.map { magnitudeIn(out, it, tick * framesPerTick, framesPerTick) }
            strengths.indices.maxBy { strengths[it] }
        }
        assertEquals(listOf(0, 1, 2, 0, 1, 2, 0, 1, 2, 0, 1, 2), loudestEachTick)
    }

    @Test
    fun `the arpeggio is quieter than playing the chord all at once`() {
        // 和音は 3 声部を重ねるが、高速アルペジオは 1 声部しか使わない。
        val together = render(song(ChordStyle.BLOCK, ToneSynth.LeadVoice.PULSE_25), 22_050)
        val rolled = render(song(ChordStyle.CHIP_ARPEGGIO, ToneSynth.LeadVoice.PULSE_25), 22_050)

        val loudest = { b: FloatArray -> b.maxOf { abs(it) } }
        assertTrue(
            "1 声部ぶんの音量に収まる（和音 ${loudest(together)} / アルペジオ ${loudest(rolled)}）",
            loudest(rolled) < loudest(together),
        )
    }

    // --- 揺れ（ビブラート）--------------------------------------------------

    @Test
    fun `the vibrato knob at zero changes nothing`() = with(ToneSynth) {
        for (voice in ToneSynth.LeadVoice.entries) {
            val base = timbre(Instrument.LEAD, voice)
            assertEquals(base, base.withVibrato(0f))
        }
    }

    @Test
    fun `the vibrato waits a moment and then swings both ways`() {
        val shaken = with(ToneSynth) { ToneSynth.timbre(Instrument.LEAD).withVibrato(1f) }
        val modulation = shaken.modulation

        // 出だしは揺らさない。いきなり揺れると、狙って外したように聞こえる。
        assertEquals(0.0, ToneSynth.semitoneOffset(modulation, 0), 1e-9)

        val swings = (0 until 120).map { ToneSynth.semitoneOffset(modulation, it) }
        assertTrue("上に揺れる", swings.max() > 0.05)
        assertTrue("下にも揺れる", swings.min() < -0.05)
        assertTrue("半音より広くは揺らさない", swings.all { abs(it) <= 1.0 })
    }

    @Test
    fun `a deeper vibrato swings wider than a shallow one`() = with(ToneSynth) {
        val base = timbre(Instrument.LEAD)
        val widest = { amount: Float ->
            val m = base.withVibrato(amount).modulation
            (0 until 120).maxOf { abs(ToneSynth.semitoneOffset(m, it)) }
        }
        assertTrue(widest(1f) > widest(0.5f))
        assertTrue(widest(0.5f) > widest(0.2f))
    }

    // --- パルス幅のうねり ---------------------------------------------------

    @Test
    fun `the wobbling voice cycles through its pulse widths`() {
        val modulation = ToneSynth.timbre(Instrument.LEAD, ToneSynth.LeadVoice.PULSE_SWEEP).modulation
        val widths = (0 until 96).map { ToneSynth.dutyAt(modulation, it, 0.5f) }

        assertEquals("表にある幅がひととおり出る", setOf(0.125f, 0.25f, 0.5f), widths.toSet())
        // 表を一周したら同じ並びに戻る。
        assertEquals(widths.take(24), widths.drop(24).take(24))
    }

    @Test
    fun `the new settings survive saving and loading`() {
        val saved = Song(id = "s", name = "曲")
            .copy(
                chordStyle = ChordStyle.CHIP_ARPEGGIO,
                leadVoice = ToneSynth.LeadVoice.PULSE_12,
                leadVibrato = 0.7f,
            )
        val restored = SongCodec.decode(SongCodec.encode(SongLibrary(listOf(saved), "s")))!!
            .songs
            .first()

        assertEquals(ChordStyle.CHIP_ARPEGGIO, restored.chordStyle)
        assertEquals(ToneSynth.LeadVoice.PULSE_12, restored.leadVoice)
        assertEquals(0.7f, restored.leadVibrato, 1e-6f)
    }

    @Test
    fun `a song saved before the vibrato existed opens with it off`() {
        val json = """
            {"songs": [{"id": "old", "name": "前の形"}], "currentId": "old"}
        """.trimIndent()
        assertEquals(0f, SongCodec.decode(json)!!.current()!!.leadVibrato, 1e-6f)
    }

    @Test
    fun `a voice with no width table keeps the width it was given`() {
        val plain = ToneSynth.timbre(Instrument.LEAD, ToneSynth.LeadVoice.PULSE_25).modulation
        for (tick in 0 until 60) {
            assertEquals(0.25f, ToneSynth.dutyAt(plain, tick, 0.25f), 1e-6f)
        }
    }
}
