package com.example.rhythmbox.core

import kotlin.math.PI
import kotlin.math.abs
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
     * 6 本の矩形波を非整数倍で重ねてハイパスした 808 系ハイハット。
     * [decay] を変えるだけでクローズド／オープンを作り分ける。
     */
    private fun hat(sampleRate: Int, decay: Double, tone: Float): FloatArray {
        val out = FloatArray(frames(sampleRate, decay * 3.2 + 0.02))
        val ratios = doubleArrayOf(1.0, 1.4471, 1.6170, 1.9265, 2.5028, 2.6637)
        val base = 263.0
        val phases = DoubleArray(ratios.size)
        val hp = OnePoleHighPass(0.93)
        for (i in out.indices) {
            val t = i.toDouble() / sampleRate
            var acc = 0.0
            for (k in ratios.indices) {
                phases[k] += 2 * PI * base * ratios[k] / sampleRate
                acc += if (sin(phases[k]) >= 0) 1.0 else -1.0
            }
            val env = exp(-t / (decay / 3.0)) * (1 - exp(-t / 0.0008))
            out[i] = softClip(hp.process(acc / ratios.size) * env * tone * 1.6)
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

    /** 矩形波 2 本のカウベル。 */
    private fun cowbell(sampleRate: Int): FloatArray {
        val out = FloatArray(frames(sampleRate, 0.34))
        var p1 = 0.0
        var p2 = 0.0
        val hp = OnePoleHighPass(0.60)
        for (i in out.indices) {
            val t = i.toDouble() / sampleRate
            p1 += 2 * PI * 540.0 / sampleRate
            p2 += 2 * PI * 800.0 / sampleRate
            val square = (if (sin(p1) >= 0) 1.0 else -1.0) + (if (sin(p2) >= 0) 1.0 else -1.0)
            val env = exp(-t / 0.10) * (1 - exp(-t / 0.001))
            out[i] = softClip(hp.process(square * 0.5) * env)
        }
        return normalize(out, 0.75f)
    }

    // --- ユーティリティ --------------------------------------------------

    private fun frames(sampleRate: Int, seconds: Double): Int = (sampleRate * seconds).toInt()

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
