package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * ノイズと三角波だけで組んだチップ音源のドラムの確認。
 *
 * 実機の打楽器は専用の音源を持たず、ノイズをどれだけ短く切るかと、
 * 三角波をどこまで速く落とすかしか手が無い。その制約こそが音になっている。
 */
class ChipDrumTest {

    private val sampleRate = 44_100

    private fun chip(voice: Voice) = DrumSynth.render(voice, sampleRate, DrumKit.CHIP)

    private fun normal(voice: Voice) = DrumSynth.render(voice, sampleRate, DrumKit.NORMAL)

    private fun magnitudeAt(buffer: FloatArray, frequency: Double, length: Int): Double {
        var real = 0.0
        var imaginary = 0.0
        for (i in 0 until minOf(length, buffer.size)) {
            val angle = 2 * PI * frequency * i / sampleRate
            real += buffer[i] * cos(angle)
            imaginary += buffer[i] * sin(angle)
        }
        return hypot(real, imaginary) / length
    }

    /** [from] から [length] フレームぶんの、0 をまたいだ回数（≒ 音の高さの目安）。 */
    private fun crossings(buffer: FloatArray, from: Int, length: Int): Int {
        var count = 0
        for (i in from + 1 until minOf(from + length, buffer.size)) {
            if ((buffer[i - 1] < 0f) != (buffer[i] < 0f)) count++
        }
        return count
    }

    /** 音が鳴り終わるまでのフレーム数。 */
    private fun tail(buffer: FloatArray, threshold: Float = 0.02f): Int =
        (buffer.indices.lastOrNull { abs(buffer[it]) > threshold } ?: 0) + 1

    @Test
    fun `both kits give every voice something to play`() {
        for (kit in DrumKit.entries) {
            for (voice in Voice.entries) {
                val sample = DrumSynth.render(voice, sampleRate, kit)
                assertTrue("${kit.label} の ${voice.label} が空", sample.isNotEmpty())
                assertTrue("${kit.label} の ${voice.label} が無音", sample.any { abs(it) > 0.1f })
                assertTrue(
                    "${kit.label} の ${voice.label} が振り切れている",
                    sample.all { abs(it) <= 1.0001f },
                )
            }
        }
    }

    @Test
    fun `the chip kit does not just repeat the normal one`() {
        for (voice in Voice.entries) {
            assertNotEquals("${voice.label} が標準と同じ", normal(voice).toList(), chip(voice).toList())
        }
    }

    @Test
    fun `a kit is asked for by name and the whole set comes back`() {
        assertEquals(DRUM_COUNT, DrumSynth.renderAll(sampleRate, DrumKit.CHIP).size)
        // 指定しなければ今までどおり標準。
        assertEquals(
            DrumSynth.renderAll(sampleRate, DrumKit.NORMAL).map { it.size },
            DrumSynth.renderAll(sampleRate).map { it.size },
        )
    }

    @Test
    fun `the chip kick drops its pitch fast`() {
        // 落ちること自体が音になっているので、頭と終わりで高さが変わっていること。
        val kick = chip(Voice.KICK)
        val early = crossings(kick, 0, 441) // 最初の 10ms
        val late = crossings(kick, 4_410, 441) // 100ms あたり

        assertTrue("頭は高く鳴っている ($early)", early > 2)
        assertTrue("そのあと低くなる (頭 $early / 後 $late)", late < early)
    }

    @Test
    fun `the chip cowbell has a pitch because its noise repeats quickly`() {
        // 短周期のノイズは 93 段で 1 周する。速く刻めば繰り返しが音程として聞こえる。
        val cowbell = chip(Voice.COWBELL)
        val length = minOf(cowbell.size, 4_410)
        val pitch = 44_000.0 / 93.0 // 刻む速さ ÷ 1 周の段数
        val onPitch = magnitudeAt(cowbell, pitch, length)
        val offPitch = magnitudeAt(cowbell, pitch * 1.6, length)

        assertTrue("繰り返しが音程になっている ($onPitch / $offPitch)", onPitch > offPitch * 2)
    }

    @Test
    fun `the chip snare has no pitch of its own`() {
        // スネアは長周期。1 周が 32767 段もあるので繰り返しは聞こえず、雑音になる。
        val snare = chip(Voice.SNARE)
        val length = minOf(snare.size, 4_410)
        val strengths = (400..3_000 step 200).map { magnitudeAt(snare, it.toDouble(), length) }

        // どこかの高さだけが飛び抜けていたら、それは音程が付いてしまっている。
        assertTrue("突出した高さが無い ($strengths)", strengths.max() < strengths.average() * 3)
    }

    @Test
    fun `the hats differ only in how long they ring`() {
        val closed = tail(chip(Voice.CLOSED_HAT))
        val open = tail(chip(Voice.OPEN_HAT))
        assertTrue("オープンのほうが長い (閉 $closed / 開 $open)", open > closed * 2)
        // リムはハットよりさらに短い一瞬の音。
        assertTrue("リムは短い", tail(chip(Voice.RIM)) < closed)
    }

    @Test
    fun `the kit is remembered with the song`() {
        val saved = Song(id = "s", name = "曲").copy(drumKit = DrumKit.CHIP)
        val restored = SongCodec.decode(SongCodec.encode(SongLibrary(listOf(saved), "s")))!!
            .songs
            .first()
        assertEquals(DrumKit.CHIP, restored.drumKit)
    }

    @Test
    fun `a song saved before the chip kit existed opens on the normal one`() {
        val json = """
            {"songs": [{"id": "old", "name": "前の形"}], "currentId": "old"}
        """.trimIndent()
        assertEquals(DrumKit.NORMAL, SongCodec.decode(json)!!.current()!!.drumKit)
    }

    @Test
    fun `playback reaches for the kit the song asks for`() {
        // 音色ごとに違う値の 1 フレームだけを渡し、どちらの組が鳴ったかで見分ける。
        val plain = List(DRUM_COUNT) { floatArrayOf(0.25f) }
        val chipped = List(DRUM_COUNT) { floatArrayOf(0.75f) }
        val song = Song(id = "s", name = "s")
            .withPattern(0, Pattern.of("A", "x..............."))

        fun peakFor(kit: DrumKit): Float {
            val engine = PlaybackEngine(sampleRate, plain, chipped)
            engine.config = EngineConfig(
                plan = PlaybackPlan.single(song, 0),
                masterVolume = 1f,
                trackVolumes = List(TRACK_COUNT) { 1f },
                mutes = List(TRACK_COUNT) { false },
                drumKit = kit,
            )
            engine.start()
            val out = FloatArray(64)
            engine.render(out)
            return out.maxOf { abs(it) }
        }

        assertEquals(0.25f, peakFor(DrumKit.NORMAL), 1e-4f)
        assertEquals(0.75f, peakFor(DrumKit.CHIP), 1e-4f)
    }
}
