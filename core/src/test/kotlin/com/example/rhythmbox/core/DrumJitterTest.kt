package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 発音ごとに音の高さをわずかにずらすぶんの確認。
 *
 * これまで同じ波形をそのまま鳴らしていたので、キックを 16 回叩くと
 * 16 回とも 1 サンプルまで同一だった。強弱を付けても波形が同じなので、
 * 機械的に聞こえる限界がそこにあった。
 */
class DrumJitterTest {

    private val sampleRate = 48_000
    private val bpm = 120

    /**
     * 少し長さのある、山が 1 つだけの波形。読み出し速さの差が長さに出る。
     * 頂点を 0.5 にしてあるのは、1.0 だとマスターのリミッターに当たって
     * 波形そのものが変わってしまうため。
     */
    private fun ramp(length: Int) = FloatArray(length) { 0.5f * (1f - it.toFloat() / length) }

    private fun engine(length: Int = 400) =
        PlaybackEngine(sampleRate, List(DRUM_COUNT) { ramp(length) })

    private fun render(pattern: String, bars: Int = 1): FloatArray {
        val song = Song("s", "test", bpm = bpm).withPattern(0, Pattern.of("A", pattern))
        val engine = engine()
        engine.config = EngineConfig(
            plan = PlaybackPlan.single(song, 0),
            bpm = bpm,
            masterVolume = 1f,
            trackVolumes = List(TRACK_COUNT) { 1f },
            mutes = List(TRACK_COUNT) { false },
        )
        engine.start()
        val framesPerBar = sampleRate * secondsPerStep(bpm) * STEPS_PER_BAR
        val out = FloatArray((framesPerBar * bars).roundToInt())
        engine.renderLeft(out)
        return out
    }

    /** [from] から始まる 1 発ぶんを切り出す。 */
    private fun hitAt(buffer: FloatArray, from: Int, length: Int) =
        buffer.copyOfRange(from, minOf(from + length, buffer.size))

    @Test
    fun `two hits of the same voice are not identical any more`() {
        // 1 拍ごとにキック。1 発目と 2 発目が別の波形になっていること。
        val out = render("x...x...x...x...")
        val step = (sampleRate * secondsPerStep(bpm)).roundToInt()
        val first = hitAt(out, 0, 400).toList()
        val second = hitAt(out, step * 4, 400).toList()

        assertNotEquals("2 発目が 1 発目の焼き直しになっている", first, second)
    }

    @Test
    fun `the first hit is left exactly as it was`() {
        // 揺らぎの表は 1.0 から始まる。頭の 1 発は素の波形のまま鳴る。
        val out = render("x...............")
        val source = ramp(400)
        for (i in source.indices) {
            assertEquals("frame $i", source[i], out[i], 1e-6f)
        }
    }

    @Test
    fun `the wobble stays small enough to be a nudge and not a tuning change`() {
        // 音の高さの差は、長さの差として出る。2% を超えると「別の音」になる。
        val out = render("x...x...x...x...", bars = 2)
        val step = (sampleRate * secondsPerStep(bpm)).roundToInt()
        val ends = (0 until 8).map { hit ->
            val from = step * 4 * hit
            val window = hitAt(out, from, 500)
            (window.indices.lastOrNull { abs(window[it]) > 1e-4f } ?: 0) + 1
        }
        val plain = 400.0
        for ((index, length) in ends.withIndex()) {
            val ratio = length / plain
            assertTrue("$index 発目の長さ比 $ratio", ratio in 0.96..1.04)
        }
        assertTrue("長さが全部同じでは揺れていない", ends.distinct().size > 1)
    }

    @Test
    fun `playing from the top always gives the same result`() {
        // 乱数ではなく通し番号にしてあるので、書き出すたびに違う音にはならない。
        assertEquals(render("x...x...x...x...").toList(), render("x...x...x...x...").toList())
    }

    @Test
    fun `looping the same bar gives the same sound every time round`() {
        // 鳴らした回数で揺らぎの表を引いていたころは、1 周の打点数が表の長さの
        // 倍数でないかぎり周ごとに表がずれ、同じ打ち込みなのに 2 周目から
        // ドラムの高さが変わっていた。1 小節に 4 発（表は 7 個）はまさにその形。
        val bars = 4
        val out = render("x...x...x...x...", bars = bars)
        val framesPerBar = (sampleRate * secondsPerStep(bpm) * STEPS_PER_BAR).roundToInt()
        val first = out.copyOfRange(0, framesPerBar).toList()
        for (loop in 1 until bars) {
            assertEquals(
                "${loop + 1} 周目が 1 周目と違う",
                first,
                out.copyOfRange(loop * framesPerBar, (loop + 1) * framesPerBar).toList(),
            )
        }
    }

    @Test
    fun `what else is playing does not change a hit's wobble`() {
        // 揺らぎはその打点の場所だけで決まる。スネアを何発挟んでも、
        // キックの音は 1 つも変わらない。
        val song = { snare: String ->
            Song("s", "test", bpm = bpm)
                .withPattern(0, Pattern.of("A", "x.......x.......", snare))
        }
        fun renderWith(snare: String): FloatArray {
            val engine = engine()
            engine.config = EngineConfig(
                plan = PlaybackPlan.single(song(snare), 0),
                bpm = bpm,
                masterVolume = 1f,
                trackVolumes = List(TRACK_COUNT) { 1f },
                // スネアは黙らせる。キックの音だけを比べたい。
                mutes = List(TRACK_COUNT) { it == Voice.SNARE.ordinal },
            )
            engine.start()
            val frames = (sampleRate * secondsPerStep(bpm) * STEPS_PER_BAR).roundToInt()
            val out = FloatArray(frames)
            engine.renderLeft(out)
            return out
        }
        assertEquals(
            renderWith("................").toList(),
            renderWith("..x...x...x...x.").toList(),
        )
    }
}
