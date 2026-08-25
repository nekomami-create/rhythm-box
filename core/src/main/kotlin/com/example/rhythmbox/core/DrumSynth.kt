package com.example.rhythmbox.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * 音源ファイルを持たず、アナログリズムマシン風のドラム音をその場で合成する。
 * 生成結果は -1.0..1.0 のモノラル PCM（1 発ぶんのワンショット）。
 * 乱数は固定シードなので、同じ入力からは常に同じ波形が得られる。
 */
object DrumSynth {

    fun renderAll(sampleRate: Int): List<FloatArray> = Voice.entries.map { render(it, sampleRate) }

    /**
     * メトロノームのクリック。[downbeat] なら小節の頭用に高くする。
     *
     * 曲の音に混ざらないよう、ドラムのどれとも被らない澄んだ音にしてある。
     * 短く切るので、叩くときの目印としてだけ働く。
     */
    fun click(sampleRate: Int, downbeat: Boolean): FloatArray {
        val seconds = 0.045
        val frequency = if (downbeat) 1_800.0 else 1_200.0
        val length = (sampleRate * seconds).toInt()
        return FloatArray(length) { i ->
            val t = i.toDouble() / sampleRate
            // 立ち上がりを一瞬だけ鈍らせて、頭のパチッというノイズを抑える。
            val envelope = exp(-t / 0.012) * (1.0 - exp(-t / 0.0006))
            (ToneSynth.sine(t * frequency) * envelope * 0.55).toFloat()
        }
    }

    fun render(voice: Voice, sampleRate: Int): FloatArray = when (voice) {
        Voice.KICK -> kick(sampleRate)
        Voice.SNARE -> snare(sampleRate)
        Voice.CLOSED_HAT -> hat(sampleRate, decay = 0.055, tone = 0.9f)
        Voice.OPEN_HAT -> hat(sampleRate, decay = 0.42, tone = 0.8f)
        Voice.CLAP -> clap(sampleRate)
        Voice.RIM -> rim(sampleRate)
        Voice.TOM -> tom(sampleRate)
        Voice.COWBELL -> cowbell(sampleRate)
    }

    // --- 各音色 ---------------------------------------------------------

    /** サイン波のピッチを一気に落とし、頭にクリックを足したバスドラム。 */
    private fun kick(sampleRate: Int): FloatArray {
        val out = FloatArray(frames(sampleRate, 0.60))
        val rng = Random(1)
        var phase = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / sampleRate
            val freq = 46.0 + 95.0 * exp(-t / 0.028)
            phase += 2 * PI * freq / sampleRate
            val body = sin(phase) * exp(-t / 0.19)
            val click = (rng.nextDouble() * 2 - 1) * 0.5 * exp(-t / 0.0035)
            out[i] = softClip(body * 1.25 + click)
        }
        return normalize(out, 0.95f)
    }

    /** 胴鳴り（サイン 2 本）とスナッピー（ハイパスノイズ）のミックス。 */
    private fun snare(sampleRate: Int): FloatArray {
        val out = FloatArray(frames(sampleRate, 0.32))
        val rng = Random(2)
        var p1 = 0.0
        var p2 = 0.0
        val hp = OnePoleHighPass(0.80)
        for (i in out.indices) {
            val t = i.toDouble() / sampleRate
            p1 += 2 * PI * 186.0 / sampleRate
            p2 += 2 * PI * 331.0 / sampleRate
            val tone = (sin(p1) * 0.7 + sin(p2) * 0.3) * exp(-t / 0.085)
            val noise = hp.process(rng.nextDouble() * 2 - 1) * exp(-t / 0.115)
            out[i] = softClip(tone * 0.6 + noise * 0.9)
        }
        return normalize(out, 0.9f)
    }

    /**
     * 6 本の矩形波を非整数倍で重ねた 808 系ハイハット。
     * [decay] を変えるだけでクローズド／オープンを作り分ける。
     *
     * 実機と同じく、重ねた矩形波の「高いところだけ」を通して金属的な音にする。
     * フィルタが緩いと発振器の基音（260〜700Hz）が残り、
     * シャッという音ではなくブーッという中域の濁りになってしまう。
     */
    private fun hat(sampleRate: Int, decay: Double, tone: Float): FloatArray {
        val out = FloatArray(frames(sampleRate, decay * 3.2 + 0.02))
        val ratios = doubleArrayOf(1.0, 1.4471, 1.6170, 1.9265, 2.5028, 2.6637)
        val base = 263.0
        val phases = DoubleArray(ratios.size)
        val steps = DoubleArray(ratios.size) { base * ratios[it] / sampleRate }
        val harmonics = IntArray(ratios.size) { oddHarmonicCount(base * ratios[it], sampleRate) }
        // 6kHz 以下を落とす（2 段重ねで急峻に）。
        val highPass = listOf(
            Biquad.highPass(HAT_HIGH_PASS_HZ, sampleRate),
            Biquad.highPass(HAT_HIGH_PASS_HZ, sampleRate),
        )
        // 耳に刺さる最上部だけ少し抑える。
        val lowPass = Biquad.lowPass(HAT_LOW_PASS_HZ, sampleRate)

        for (i in out.indices) {
            val t = i.toDouble() / sampleRate
            var acc = 0.0
            for (k in ratios.indices) {
                acc += square(phases[k], harmonics[k])
                phases[k] += steps[k]
            }
            var value = acc / ratios.size
            for (filter in highPass) value = filter.process(value)
            value = lowPass.process(value)
            val env = exp(-t / (decay / 3.0)) * (1 - exp(-t / 0.0008))
            out[i] = softClip(value * env * tone * 2.2)
        }
        return normalize(out, 0.7f)
    }

    /** 短いノイズバーストを 3 連発 + 残響でクラップらしさを出す。 */
    private fun clap(sampleRate: Int): FloatArray {
        val out = FloatArray(frames(sampleRate, 0.42))
        val rng = Random(3)
        val hp = OnePoleHighPass(0.86)
        val lp = OnePoleLowPass(0.55)
        val burstOffsets = doubleArrayOf(0.0, 0.011, 0.022)
        for (i in out.indices) {
            val t = i.toDouble() / sampleRate
            var env = 0.0
            for (offset in burstOffsets) {
                if (t >= offset) env += exp(-(t - offset) / 0.0075)
            }
            if (t >= burstOffsets.last()) env += 0.7 * exp(-(t - burstOffsets.last()) / 0.10)
            val noise = lp.process(hp.process(rng.nextDouble() * 2 - 1))
            out[i] = softClip(noise * env * 0.9)
        }
        return normalize(out, 0.85f)
    }

    /** 木質の「コッ」。高めのサイン 2 本を一瞬だけ鳴らす。 */
    private fun rim(sampleRate: Int): FloatArray {
        val out = FloatArray(frames(sampleRate, 0.09))
        val rng = Random(4)
        var p1 = 0.0
        var p2 = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / sampleRate
            p1 += 2 * PI * 1720.0 / sampleRate
            p2 += 2 * PI * 470.0 / sampleRate
            val env = exp(-t / 0.014)
            val click = (rng.nextDouble() * 2 - 1) * 0.25 * exp(-t / 0.0015)
            out[i] = softClip((sin(p1) * 0.6 + sin(p2) * 0.4 + click) * env)
        }
        return normalize(out, 0.8f)
    }

    /** ピッチが緩やかに下がるロータム。 */
    private fun tom(sampleRate: Int): FloatArray {
        val out = FloatArray(frames(sampleRate, 0.45))
        val rng = Random(5)
        var phase = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / sampleRate
            val freq = 96.0 + 95.0 * exp(-t / 0.09)
            phase += 2 * PI * freq / sampleRate
            val body = sin(phase) * exp(-t / 0.16)
            val attack = (rng.nextDouble() * 2 - 1) * 0.18 * exp(-t / 0.004)
            out[i] = softClip(body + attack)
        }
        return normalize(out, 0.9f)
    }

    /**
     * 矩形波 2 本（540Hz / 800Hz）のカウベル。
     * そのままだと高次倍音が耳に刺さるので、中域だけを残して「コーン」という胴鳴りにする。
     */
    private fun cowbell(sampleRate: Int): FloatArray {
        val out = FloatArray(frames(sampleRate, 0.55))
        val lowFrequency = 540.0
        val highFrequency = 800.0
        var lowPhase = 0.0
        var highPhase = 0.0
        val lowHarmonics = oddHarmonicCount(lowFrequency, sampleRate)
        val highHarmonics = oddHarmonicCount(highFrequency, sampleRate)
        val body = Biquad.lowPass(COWBELL_LOW_PASS_HZ, sampleRate)
        val cut = Biquad.highPass(COWBELL_HIGH_PASS_HZ, sampleRate)

        for (i in out.indices) {
            val t = i.toDouble() / sampleRate
            val mix = square(lowPhase, lowHarmonics) * 0.5 + square(highPhase, highHarmonics) * 0.5
            lowPhase += lowFrequency / sampleRate
            highPhase += highFrequency / sampleRate
            val filtered = body.process(cut.process(mix))
            // 立ち上がりは速く、余韻はカウベルらしく少し長め。
            val env = exp(-t / 0.16) * (1 - exp(-t / 0.001))
            out[i] = softClip(filtered * env * 1.4)
        }
        return normalize(out, 0.75f)
    }

    // --- ユーティリティ --------------------------------------------------

    private fun frames(sampleRate: Int, seconds: Double): Int = (sampleRate * seconds).toInt()

    /** ハイハットで 6kHz 以下を落とすときのカットオフ。 */
    private const val HAT_HIGH_PASS_HZ = 6_000.0

    /** ハイハットの最上部を少し抑えるカットオフ。 */
    private const val HAT_LOW_PASS_HZ = 13_000.0

    /** カウベルで残す帯域（実機と同じく中域を主役にする）。 */
    private const val COWBELL_LOW_PASS_HZ = 5_000.0
    private const val COWBELL_HIGH_PASS_HZ = 900.0

    /** [frequency] の矩形波が、折り返さずに持てる奇数倍音の数。 */
    private fun oddHarmonicCount(frequency: Double, sampleRate: Int): Int {
        var count = 0
        var harmonic = 1
        while (frequency * harmonic < sampleRate / 2.0) {
            count++
            harmonic += 2
        }
        return count.coerceAtLeast(1)
    }

    /**
     * 折り返し（エイリアス）の出ない矩形波。
     * 奇数倍音をナイキスト周波数まで足し合わせて作る。
     * 単純に sin の符号を取ると、可聴域に戻ってくる不協和な成分が混じる。
     */
    private fun square(phase: Double, harmonics: Int): Double {
        var sum = 0.0
        var harmonic = 1
        repeat(harmonics) {
            sum += ToneSynth.sine(phase * harmonic) / harmonic
            harmonic += 2
        }
        return sum * 4.0 / PI
    }

    /** 2 次のフィルタ（RBJ の式）。ハイパス / ローパスに使う。 */
    private class Biquad(
        private val b0: Double,
        private val b1: Double,
        private val b2: Double,
        private val a1: Double,
        private val a2: Double,
    ) {
        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0

        fun process(input: Double): Double {
            val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = input
            y2 = y1
            y1 = output
            return output
        }

        companion object {
            private const val Q = 0.707

            fun highPass(frequency: Double, sampleRate: Int): Biquad {
                val w = 2 * PI * frequency / sampleRate
                val alpha = sin(w) / (2 * Q)
                val cosine = cos(w)
                val a0 = 1 + alpha
                return Biquad(
                    b0 = (1 + cosine) / 2 / a0,
                    b1 = -(1 + cosine) / a0,
                    b2 = (1 + cosine) / 2 / a0,
                    a1 = -2 * cosine / a0,
                    a2 = (1 - alpha) / a0,
                )
            }

            fun lowPass(frequency: Double, sampleRate: Int): Biquad {
                val w = 2 * PI * frequency / sampleRate
                val alpha = sin(w) / (2 * Q)
                val cosine = cos(w)
                val a0 = 1 + alpha
                return Biquad(
                    b0 = (1 - cosine) / 2 / a0,
                    b1 = (1 - cosine) / a0,
                    b2 = (1 - cosine) / 2 / a0,
                    a1 = -2 * cosine / a0,
                    a2 = (1 - alpha) / a0,
                )
            }
        }
    }

    private fun softClip(x: Double): Float = (x / (1.0 + abs(x) * 0.5)).toFloat()

    /** ピーク値を [peak] に揃える。無音ならそのまま返す。 */
    private fun normalize(buffer: FloatArray, peak: Float): FloatArray {
        var max = 0f
        for (v in buffer) max = maxOf(max, abs(v))
        if (max <= 1e-6f) return buffer
        val gain = peak / max
        for (i in buffer.indices) buffer[i] *= gain
        return buffer
    }

    private class OnePoleHighPass(private val a: Double) {
        private var lastIn = 0.0
        private var lastOut = 0.0
        fun process(x: Double): Double {
            val y = a * (lastOut + x - lastIn)
            lastIn = x
            lastOut = y
            return y
        }
    }

    private class OnePoleLowPass(private val a: Double) {
        private var lastOut = 0.0
        fun process(x: Double): Double {
            lastOut += a * (x - lastOut)
            return lastOut
        }
    }
}
