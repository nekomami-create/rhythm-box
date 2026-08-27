package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/** 残響（リバーブ）。 */
class ReverbTest {

    private val sampleRate = 48_000

    private fun rms(buffer: FloatArray, from: Int = 0, to: Int = buffer.size): Double {
        var sum = 0.0
        for (i in from until minOf(to, buffer.size)) sum += buffer[i].toDouble() * buffer[i]
        return sqrt(sum / (to - from))
    }

    /** 左右とも同じ [hz] の正弦波が入った、[seconds] 秒ぶんのバッファ。 */
    private fun tone(hz: Double, seconds: Double, level: Float = 0.5f): FloatArray {
        val frames = (sampleRate * seconds).toInt()
        return FloatArray(frames * CHANNELS) { index ->
            val frame = index / CHANNELS
            (level * sin(2 * PI * hz * frame / sampleRate)).toFloat()
        }
    }

    /** 頭で一瞬だけ鳴って、あとは無音のバッファ。 */
    private fun click(seconds: Double): FloatArray {
        val frames = (sampleRate * seconds).toInt()
        return FloatArray(frames * CHANNELS) { index ->
            if (index / CHANNELS < 20) 0.8f else 0f
        }
    }

    /** 濡れた音だけを取り出す（掛けたあと － 掛ける前）。 */
    private fun wetOnly(dry: FloatArray, amount: Float, size: RoomSize): FloatArray {
        val mixed = dry.copyOf()
        Reverb(sampleRate).process(mixed, 0, mixed.size / CHANNELS, amount, size)
        return FloatArray(dry.size) { mixed[it] - dry[it] }
    }

    @Test
    fun `no reverb leaves every sample untouched`() {
        val song = Song.newSong("s", "残響なし", 0L)
        assertEquals(0f, song.reverb, 0f) // 既定は掛けない

        val dry = click(1.0)
        val mixed = dry.copyOf()
        Reverb(sampleRate).process(mixed, 0, mixed.size / CHANNELS, 0f, RoomSize.MEDIUM)
        for (i in dry.indices) {
            if (mixed[i] != dry[i]) throw AssertionError("$i: ${mixed[i]} != ${dry[i]}")
        }
    }

    @Test
    fun `a negative amount is treated as off`() {
        val dry = click(0.5)
        val mixed = dry.copyOf()
        Reverb(sampleRate).process(mixed, 0, mixed.size / CHANNELS, -1f, RoomSize.LARGE)
        for (i in dry.indices) {
            if (mixed[i] != dry[i]) throw AssertionError("$i: ${mixed[i]} != ${dry[i]}")
        }
    }

    @Test
    fun `the sound keeps going after the hit has stopped`() {
        // 頭の一瞬だけ鳴らして、そのあと無音のはずのところに音が残る。
        val dry = click(1.0)
        val wet = wetOnly(dry, 1f, RoomSize.MEDIUM)
        val late = rms(wet, sampleRate / 2 * CHANNELS, wet.size)
        assertTrue("尾が残っていない (rms=$late)", late > 1e-4)
    }

    @Test
    fun `a bigger room rings for longer`() {
        val dry = click(4.0)
        fun tail(size: RoomSize): Double {
            val wet = wetOnly(dry, 1f, size)
            return rms(wet, sampleRate * CHANNELS, wet.size) // 1 秒より後
        }
        val small = tail(RoomSize.SMALL)
        val medium = tail(RoomSize.MEDIUM)
        val large = tail(RoomSize.LARGE)
        assertTrue("狭い $small < 普通 $medium", small < medium)
        assertTrue("普通 $medium < 広い $large", medium < large)
    }

    @Test
    fun `turning the knob up makes it wetter`() {
        val dry = click(2.0)
        val quiet = rms(wetOnly(dry, 0.25f, RoomSize.MEDIUM))
        val loud = rms(wetOnly(dry, 1f, RoomSize.MEDIUM))
        // 濡れた音だけを見ているので、量にそのまま比例する。
        assertEquals(4.0, loud / quiet, 0.01)
    }

    @Test
    fun `low sounds are kept out of the tail`() {
        // キックとベースを残響に送ると濁る。低いところは送らない。
        val low = rms(wetOnly(tone(60.0, 2.0), 1f, RoomSize.MEDIUM))
        val high = rms(wetOnly(tone(1_000.0, 2.0), 1f, RoomSize.MEDIUM))
        assertTrue("低い音が素通ししている (低 $low / 高 $high)", low * 5 < high)
    }

    @Test
    fun `the two sides do not come out identical`() {
        // 左右が同じ尾だと、奥には行っても横には広がらない。
        val dry = click(2.0)
        val wet = wetOnly(dry, 1f, RoomSize.MEDIUM)
        var same = 0
        var differing = 0
        for (frame in 0 until wet.size / CHANNELS) {
            val at = frame * CHANNELS
            if (wet[at] == wet[at + 1]) same++ else differing++
        }
        assertTrue("左右が同じ尾になっている (同じ $same / 違う $differing)", differing > same)
    }

    @Test
    fun `the tail dies away instead of building up`() {
        // 戻す量が 1 を超えていると、鳴らし続けるうちに発散する。
        val dry = tone(1_000.0, 6.0)
        val wet = wetOnly(dry, 1f, RoomSize.LARGE)
        val early = rms(wet, sampleRate * CHANNELS, 2 * sampleRate * CHANNELS)
        val late = rms(wet, 5 * sampleRate * CHANNELS, wet.size)
        assertTrue("尾が育っている (前 $early → 後 $late)", late < early * 1.2)
        assertTrue("音が消えている", early > 1e-4)
        val peak = wet.maxOf { abs(it) }
        assertTrue("発散している (peak=$peak)", peak < 4f)
    }

    @Test
    fun `a wet mix still comes out inside the rails`() {
        val song = Song.newSong("s", "残響あり", 0L)
            .copy(bpm = 120, reverb = 1f, roomSize = RoomSize.LARGE)
        val audio = OfflineRenderer.render(
            song,
            PlaybackPlan.arrangement(song),
            DrumSynth.renderAll(sampleRate),
            sampleRate,
        )
        val peak = audio.maxOf { abs(it) }
        assertTrue("無音になっている", peak > 0.2f)
        assertTrue("音が割れている (peak=$peak)", peak <= 1.0f)
    }

    @Test
    fun `the knob at full is wet enough to hear but not a drowning`() {
        // 右端の濡れ具合を数で押さえておく。ここが動いたら気付けるように。
        val song = Song.newSong("s", "残響", 0L).copy(bpm = 120)
        val drums = DrumSynth.renderAll(sampleRate)
        val plan = PlaybackPlan.arrangement(song)
        // 残響を掛けると余韻が伸びるので、掛けないほうにも同じ長さを渡して
        // 突き合わせる長さを揃える（揃えないと尾のぶんだけ比較から外れる）。
        val tail = RoomSize.MEDIUM.tailSeconds
        val dry = OfflineRenderer.render(song, plan, drums, sampleRate, tailSeconds = tail)
        val wetMix = OfflineRenderer.render(
            song.copy(reverb = 1f, roomSize = RoomSize.MEDIUM),
            plan,
            drums,
            sampleRate,
            tailSeconds = tail,
        )
        assertEquals(dry.size, wetMix.size)
        val wet = FloatArray(dry.size) { wetMix[it] - dry[it] }
        val ratio = rms(wet) / rms(dry)
        assertTrue("薄すぎる ($ratio)", ratio > 0.2)
        assertTrue("溺れている ($ratio)", ratio < 0.6)
    }

    @Test
    fun `the file is made long enough for the tail`() {
        val song = Song.newSong("s", "残響", 0L)
        assertEquals(OfflineRenderer.DEFAULT_TAIL_SECONDS, OfflineRenderer.tailFor(song), 1e-9)
        // 掛けたら、その広さの尾が消えるまで伸ばす。
        val large = song.copy(reverb = 0.5f, roomSize = RoomSize.LARGE)
        assertEquals(RoomSize.LARGE.tailSeconds, OfflineRenderer.tailFor(large), 1e-9)
        // 既定の余韻より短い広さなら、短くはしない。
        val small = song.copy(reverb = 0.5f, roomSize = RoomSize.SMALL)
        assertEquals(OfflineRenderer.DEFAULT_TAIL_SECONDS, OfflineRenderer.tailFor(small), 1e-9)
    }

    @Test
    fun `the tail really has died out by the time the file ends`() {
        val song = Song.newSong("s", "残響", 0L)
            .copy(bpm = 120, reverb = 1f, roomSize = RoomSize.LARGE)
        val audio = OfflineRenderer.render(
            song,
            PlaybackPlan.arrangement(song),
            DrumSynth.renderAll(sampleRate),
            sampleRate,
        )
        // 末尾 0.1 秒が曲の途中よりずっと静か＝ぶつ切りになっていない。
        val middle = rms(audio, audio.size / 2, audio.size / 2 + sampleRate / 5)
        val ending = rms(audio, audio.size - sampleRate / 5, audio.size)
        assertTrue("末尾で切れている (末尾 $ending / 途中 $middle)", ending < middle * 0.05)
    }

    @Test
    fun `starting over does not drag the old tail along`() {
        val song = Song("s", "test", bpm = 120).withPattern(0, Pattern.of("A", "x..............."))
        val engine = PlaybackEngine(sampleRate, List(DRUM_COUNT) { floatArrayOf(0.8f) })
        engine.config = EngineConfig(
            plan = PlaybackPlan.single(song, 0),
            bpm = song.bpm,
            masterVolume = 1f,
            trackVolumes = List(TRACK_COUNT) { 1f },
            reverb = 1f,
            roomSize = RoomSize.LARGE,
        )
        val frames = sampleRate / 2
        val first = FloatArray(frames * CHANNELS)
        engine.start()
        engine.render(first)
        // 尾が溜まっているところで頭に戻す。
        engine.start()
        val second = FloatArray(frames * CHANNELS)
        engine.render(second)
        for (i in first.indices) {
            if (first[i] != second[i]) {
                throw AssertionError("$i フレーム目で違う: ${first[i]} != ${second[i]}")
            }
        }
    }
}
