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

    /**
     * リードの音色。
     *
     * 倍音の組み合わせで音の芯が決まり、包絡線の伸び縮みで弾き方の感じが決まる。
     * 同じ倍音でも、すぐ減衰すれば弾いた音、長く残ればふくらむ音に聞こえる。
     */
    @kotlinx.serialization.Serializable
    enum class LeadVoice(
        val label: String,
        internal val partials: List<Partial>,
        /** 立ち上がりの遅さ。1.0 で基準どおり。 */
        internal val attackScale: Double = 1.0,
        /** 減衰の遅さ。 */
        internal val decayScale: Double = 1.0,
        /** 減衰後に残る音量の割合。 */
        internal val sustainScale: Float = 1.0f,
    ) {
        SQUARE(
            "スクエア",
            listOf(Partial(1, 1.0f), Partial(3, 0.33f), Partial(5, 0.20f), Partial(7, 0.13f)),
        ),
        SAW(
            "ノコギリ",
            listOf(Partial(1, 1.0f), Partial(2, 0.50f), Partial(3, 0.33f), Partial(4, 0.25f), Partial(5, 0.20f)),
        ),
        SOFT(
            "やわらか",
            listOf(Partial(1, 1.0f), Partial(2, 0.22f), Partial(3, 0.08f)),
        ),
        BELL(
            "ベル",
            listOf(Partial(1, 1.0f), Partial(3, 0.45f), Partial(6, 0.30f), Partial(9, 0.14f)),
            decayScale = 2.2,
            sustainScale = 0.45f,
        ),
        TRIANGLE(
            // 奇数倍音が急に小さくなる。三角波に近く、細くて澄んだ音。
            "トライアングル",
            listOf(Partial(1, 1.0f), Partial(3, 0.11f), Partial(5, 0.04f), Partial(7, 0.02f)),
        ),
        PLUCK(
            // すぐ落ちて残らない。弦をはじいたような歯切れ。
            "プラック",
            listOf(Partial(1, 1.0f), Partial(2, 0.45f), Partial(3, 0.28f), Partial(4, 0.16f)),
            decayScale = 0.45,
            sustainScale = 0.12f,
        ),
        ORGAN(
            // 整数倍音を薄く重ねて、減衰せずに伸ばす。
            "オルガン",
            listOf(Partial(1, 1.0f), Partial(2, 0.5f), Partial(3, 0.26f), Partial(4, 0.16f), Partial(8, 0.09f)),
            decayScale = 1.8,
            sustainScale = 1.45f,
        ),
        BRASS(
            // 低い倍音が厚く、立ち上がりが少し遅い。吹き込む感じ。
            "ブラス",
            listOf(Partial(1, 1.0f), Partial(2, 0.7f), Partial(3, 0.5f), Partial(4, 0.3f), Partial(5, 0.18f)),
            attackScale = 4.0,
            sustainScale = 1.3f,
        ),
        FLUTE(
            // ほぼ基音だけ。ゆっくり立ち上げると息を吹き込んだように聞こえる。
            "フルート",
            listOf(Partial(1, 1.0f), Partial(2, 0.12f)),
            attackScale = 6.0,
            sustainScale = 1.4f,
        ),
        GLASS(
            // 高いところに倍音を飛ばして、短く切る。硬くて澄んだ音。
            "グラス",
            listOf(Partial(1, 1.0f), Partial(4, 0.35f), Partial(7, 0.22f), Partial(11, 0.12f)),
            decayScale = 0.7,
            sustainScale = 0.3f,
        ),
    }

    fun timbre(instrument: Instrument, lead: LeadVoice = LeadVoice.SQUARE): Timbre = when (instrument) {
        Instrument.CHORD -> CHORD
        Instrument.BASS -> BASS
        Instrument.LEAD -> LEAD.copy(
            partials = lead.partials,
            attack = LEAD.attack * lead.attackScale,
            decay = LEAD.decay * lead.decayScale,
            // 伸ばし続ける音色は 1 を超えないように止める。
            sustain = (LEAD.sustain * lead.sustainScale).coerceIn(0f, 0.95f),
        )
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
