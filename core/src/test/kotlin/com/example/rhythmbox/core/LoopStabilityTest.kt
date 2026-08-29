package com.example.rhythmbox.core

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * パターンを回したとき、周ごとに音が変わらないことの確認。
 *
 * 「1 周目と 2 周目で音が違う」という報告から入った。原因は 2 つあって、
 * どちらも「前の周の状態が次の周の音を決めてしまう」形だった。
 *
 * - ドラムの揺らぎを、鳴らした回数で引いていた（[DrumJitterTest] 参照）
 * - 発振器が足りず、足りないぶんを「そのとき一番小さい声」から奪っていた。
 *   どれが一番小さいかは前の周の減衰具合で決まるので、毎周ちがう声が消えた
 *
 * 1 周目だけは、その前に鳴っているものが無いぶん、どうしても違う（どんな
 * 音源でも同じで、これは直せないし直すものでもない）。ここで押さえるのは
 * 2 周目から先が揃うこと。
 */
class LoopStabilityTest {

    private val sampleRate = 48_000

    /** 1 ステップがちょうど 6000 フレームになるので、周期が整数で出る。 */
    private val bpm = 120
    private val framesPerBar = (sampleRate * secondsPerStep(bpm) * STEPS_PER_BAR).roundToInt()

    /** 和音・ベース・ドラムが詰まった 2 小節。声部をいちばん食う形。 */
    private fun crowded(): Song {
        val bar = Pattern.of(
            "A",
            "x...x...x...x...", // キック
            "....x.......x...", // スネア
            "x.x.x.x.x.x.x.x.", // ハイハット
        )
        var pattern = bar.withBarCount(2)
        for (b in 0 until 2) {
            for (step in 0 until STEPS_PER_BAR step 4) {
                pattern = pattern.setAt(b, ROW_CHORD, step, true).setAt(b, ROW_BASS, step, true)
                pattern = pattern.withLead(b, step + 2, 72)
            }
        }
        // 小節ごとに和音が変わるので、前の和音の減衰と次の和音が必ず重なる。
        pattern = pattern
            .withChordAt(0, 0, Chord(0, ChordQuality.MAJOR_SEVENTH))
            .withChordAt(1, 0, Chord(5, ChordQuality.MAJOR_SEVENTH))
        return Song("s", "test", bpm = bpm)
            .withPattern(0, pattern)
            .copy(chordVoicing = ChordVoicing.THICK)
    }

    private fun render(song: Song, loops: Int): FloatArray {
        val engine = PlaybackEngine(sampleRate, List(DRUM_COUNT) { FloatArray(300) { i -> 0.4f * (1f - i / 300f) } })
        engine.config = EngineConfig(
            plan = PlaybackPlan.single(song, 0),
            bpm = bpm,
            masterVolume = 1f,
            trackVolumes = List(TRACK_COUNT) { 1f },
            mutes = List(TRACK_COUNT) { false },
            chordVoicing = song.chordVoicing,
            loop = true,
        )
        engine.start()
        val out = FloatArray(framesPerBar * 2 * loops)
        engine.renderLeft(out)
        return out
    }

    private fun lap(buffer: FloatArray, index: Int): FloatArray {
        val period = framesPerBar * 2
        return buffer.copyOfRange(index * period, (index + 1) * period)
    }

    @Test
    fun `from the second time round, every lap is the same`() {
        val out = render(crowded(), loops = 5)
        val second = lap(out, 1)
        for (index in 2..4) {
            val here = lap(out, index)
            val worst = second.indices.maxOf { abs(second[it] - here[it]) }
            // 浮動小数の丸めぶんだけは許す。耳に付くのは -60dB より上。
            assertTrue("${index + 1} 周目が 2 周目と違う（差 $worst）", worst < 1e-5f)
        }
    }

    @Test
    fun `the crowded bar really does need more voices than it used to have`() {
        // 声部が 12 しか無いと奪い合いが起きて、周ごとに音が変わっていた。
        // このテストが守っているものが本当に効いていることを、ここで押さえる。
        val song = crowded()
        val engine = PlaybackEngine(
            sampleRate,
            List(DRUM_COUNT) { FloatArray(300) { i -> 0.4f * (1f - i / 300f) } },
            maxTonePolyphony = 12,
        )
        engine.config = EngineConfig(
            plan = PlaybackPlan.single(song, 0),
            bpm = bpm,
            masterVolume = 1f,
            trackVolumes = List(TRACK_COUNT) { 1f },
            mutes = List(TRACK_COUNT) { false },
            chordVoicing = song.chordVoicing,
            loop = true,
        )
        engine.start()
        val out = FloatArray(framesPerBar * 2 * 5)
        engine.renderLeft(out)
        val second = lap(out, 1)
        val worst = (2..4).maxOf { index ->
            val here = lap(out, index)
            second.indices.maxOf { abs(second[it] - here[it]) }
        }
        assertTrue("12 声でも揺れないなら、増やした意味がどこにも無い", worst > 1e-4f)
    }
}
