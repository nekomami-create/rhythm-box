package com.example.rhythmbox.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * コード / ベース / リード用の音程を持つ音づくり。
 *
 * ドラムと違って音の高さも長さも決まらないため、波形を作り置きせず
 * 倍音を足し合わせながらその場で鳴らす（加算合成）。
 * サイン波はテーブル引き + 線形補間で求める。
 */
object ToneSynth {

    /** 倍音 1 本ぶんの指定。[harmonic] は基音の何倍か。 */
    data class Partial(val harmonic: Int, val gain: Float)

    /**
     * 音色の設定。時間の単位は秒。
     * [sustain] は減衰後に保つ音量の割合、[gain] は 1 音あたりの音量。
     */
    data class Timbre(
        val partials: List<Partial>,
        val attack: Double,
        val decay: Double,
        val sustain: Float,
        val release: Double,
        val gain: Float,
        /** 音の長さの上限（ステップ数）。コードは小節いっぱい伸ばす。 */
        val maxGateSteps: Int,
    )

    private val CHORD = Timbre(
        // ドローバーオルガン風に、整数倍の倍音を薄く重ねる。
        partials = listOf(
            Partial(1, 1.0f),
            Partial(2, 0.5f),
            Partial(3, 0.26f),
            Partial(4, 0.16f),
            Partial(8, 0.09f),
        ),
        attack = 0.020,
        decay = 0.30,
        sustain = 0.78f,
        release = 0.34,
        gain = 0.21f, // 3〜4 音を同時に鳴らすので 1 音は控えめに
        maxGateSteps = STEPS_PER_BAR,
    )

    private val BASS = Timbre(
        partials = listOf(
            Partial(1, 1.0f),
            Partial(2, 0.42f),
            Partial(3, 0.18f),
            Partial(4, 0.07f),
        ),
        attack = 0.005,
        decay = 0.26,
        sustain = 0.55f,
        release = 0.09,
        gain = 0.55f,
        maxGateSteps = 4,
    )

    private val LEAD = Timbre(
        // 奇数倍音だけを使って矩形波寄りの音にする。
        partials = listOf(
            Partial(1, 1.0f),
            Partial(3, 0.33f),
            Partial(5, 0.20f),
            Partial(7, 0.13f),
        ),
        attack = 0.008,
        decay = 0.18,
        sustain = 0.62f,
        release = 0.13,
        gain = 0.34f,
        maxGateSteps = 4,
    )

    /**
     * 「音の伸び」つまみを反映した音色。
     *
     * [hold] は 0〜1 で、[DEFAULT_HOLD]（0.5）が元の音。
     * 小さくすると減衰が速くて短く切れる音（プラック）、
     * 大きくすると減衰が遅くて伸び続ける音（パッド）になる。
     */
    fun Timbre.withHold(hold: Float): Timbre {
        if (abs(hold - DEFAULT_HOLD) < 1e-3f) return this
        // 0 で 1/4 倍、0.5 で等倍、1 で 4 倍。
        val factor = 4.0.pow(2.0 * hold.coerceIn(0f, 1f) - 1.0)
        return copy(
            decay = decay * factor,
            release = release * factor,
            // 減衰後に残る音量。上げすぎるとオルガンのように鳴りっぱなしになる。
            sustain = (sustain * factor).toFloat().coerceIn(0f, 0.95f),
            // 次の音が無いときに切る長さも一緒に伸ばす。
            maxGateSteps = (maxGateSteps * factor).roundToInt().coerceIn(1, STEPS_PER_BAR),
        )
    }

    /** つまみの真ん中。ここが今までの音。 */
    const val DEFAULT_HOLD = 0.5f

    fun timbre(instrument: Instrument): Timbre = when (instrument) {
        Instrument.CHORD -> CHORD
        Instrument.BASS -> BASS
        Instrument.LEAD -> LEAD
    }

    /** MIDI ノート番号 -> 周波数 (Hz)。A4 = 69 = 440Hz。 */
    fun frequency(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    // --- サイン波テーブル ---------------------------------------------------

    private const val TABLE_SIZE = 4096
    private val table = FloatArray(TABLE_SIZE + 1) { sin(2 * PI * it / TABLE_SIZE).toFloat() }

    /** [phase] は「周期の何周目か」（1.0 で 1 周）。範囲外の値もそのまま渡してよい。 */
    fun sine(phase: Double): Float {
        val wrapped = phase - floor(phase)
        val position = wrapped * TABLE_SIZE
        val index = position.toInt()
        val fraction = (position - index).toFloat()
        val a = table[index]
        return a + (table[index + 1] - a) * fraction
    }
}
