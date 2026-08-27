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
     * 波形の作り方。
     *
     * [Additive] は倍音を足し合わせる従来の方式。それ以外は波形を直接作る。
     *
     * 足し合わせでチップ音源のパルス波を出すことはできない。倍音ごとの位相を
     * 持てないので、どのデューティ比を指定しても 50%（ただの矩形波）になってしまう。
     * 実測でも、12.5% を狙って倍音を 16 本足しても波形が上にいる割合は 50% のまま。
     * 直接作れば正確なうえ、正弦テーブルを 6 回引くより軽い。
     */
    sealed interface Waveform {
        /** 1 音あたりの音量の補正。直接作る波形は倍音の山が高く、そのままだと大きい。 */
        val levelTrim: Float

        /** 倍音を足し合わせる（今までの音）。 */
        data object Additive : Waveform {
            override val levelTrim = 1.0f
        }

        /**
         * パルス波。[duty] は 1 周期のうち上にいる割合。
         * 0.5 で矩形波、細くするほど鼻にかかった音になる。
         */
        data class Pulse(val duty: Float) : Waveform {
            override val levelTrim = 0.55f
        }

        /**
         * ファミコンの三角波。4 bit・16 段の階段になっている。
         * 段差の濁りがあの音の芯なので、なめらかに均さずそのまま出す。
         */
        data object ChipTriangle : Waveform {
            override val levelTrim = 0.75f
        }

        /**
         * LFSR（線形帰還シフトレジスタ）のノイズ。
         * [shortPeriod] を立てると 93 段で 1 周し、音程のある金属質な音になる。
         */
        data class Noise(val shortPeriod: Boolean = false) : Waveform {
            override val levelTrim = 0.45f
        }
    }

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
        /** 波形の作り方。既定は今までの加算合成なので、既存の音は変わらない。 */
        val wave: Waveform = Waveform.Additive,
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
        /** 波形の作り方。チップ音色だけがここを使う。 */
        internal val wave: Waveform = Waveform.Additive,
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

        // ここから下はチップ音源（ファミコン / ゲームボーイ）の音。
        // 実機のチャンネルは押している間ずっと同じ音量で鳴るので、
        // 減衰をほとんどさせず、立ち上がりも即座にしてある。
        PULSE_12(
            // いちばん細いパルス。鼻にかかった、ゲームの主旋律の音。
            "チップ 12.5%",
            listOf(Partial(1, 1.0f)), // 波形を直接作るので中身は使われない
            attackScale = 0.3,
            decayScale = 3.0,
            sustainScale = 1.5f,
            wave = Waveform.Pulse(0.125f),
        ),
        PULSE_25(
            // ファミコンでいちばんよく使われる幅。太さと細さの中間。
            "チップ 25%",
            listOf(Partial(1, 1.0f)), // 波形を直接作るので中身は使われない
            attackScale = 0.3,
            decayScale = 3.0,
            sustainScale = 1.5f,
            wave = Waveform.Pulse(0.25f),
        ),
        PULSE_50(
            // 矩形波。丸くて素直な音で、ハモりや対旋律に向く。
            "チップ 50%",
            listOf(Partial(1, 1.0f)), // 波形を直接作るので中身は使われない
            attackScale = 0.3,
            decayScale = 3.0,
            sustainScale = 1.5f,
            wave = Waveform.Pulse(0.5f),
        ),
        CHIP_TRIANGLE(
            // ファミコンの三角波。16 段の階段のざらつきがそのまま音色になる。
            "チップ三角",
            listOf(Partial(1, 1.0f)), // 波形を直接作るので中身は使われない
            attackScale = 0.3,
            decayScale = 3.0,
            sustainScale = 1.5f,
            wave = Waveform.ChipTriangle,
        ),
        CHIP_NOISE(
            // 短周期のノイズ。音程が付くので、効果音めいた旋律が書ける。
            "チップノイズ",
            listOf(Partial(1, 1.0f)), // 波形を直接作るので中身は使われない
            attackScale = 0.3,
            decayScale = 2.0,
            sustainScale = 1.2f,
            wave = Waveform.Noise(shortPeriod = true),
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
            wave = lead.wave,
        )
    }

    /** MIDI ノート番号 -> 周波数 (Hz)。A4 = 69 = 440Hz。 */
    fun frequency(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    // --- 直接作る波形 -------------------------------------------------------
    //
    // どれも状態を持たない。位相（と LFSR の中身）を渡せば答えが決まるので、
    // 再生を通さずにそのまま測れる。

    /**
     * パルス波 1 サンプル。[duty] は 1 周期のうち上にいる割合。
     *
     * 上がるところと下がるところの 2 か所に段差があるので、その前後 1 サンプルだけ
     * [blep] で丸める（PolyBLEP）。丸めないと高い音で、実機には無い
     * 金属質なうなり（折り返し）が乗る。[phaseStep] は 1 サンプルで進む位相。
     */
    fun pulse(phase: Double, duty: Float, phaseStep: Double): Float {
        val t = phase - floor(phase)
        var value = if (t < duty) 1f else -1f
        value += blep(t, phaseStep)
        value -= blep((t - duty + 1.0).mod(1.0), phaseStep)
        return value
    }

    /** 段差を多項式で丸める。段差から離れたところでは 0 を返す。 */
    fun blep(t: Double, phaseStep: Double): Float {
        if (phaseStep <= 0.0) return 0f
        return when {
            t < phaseStep -> {
                val x = t / phaseStep
                (x + x - x * x - 1.0).toFloat()
            }
            t > 1.0 - phaseStep -> {
                val x = (t - 1.0) / phaseStep
                (x * x + x + x + 1.0).toFloat()
            }
            else -> 0f
        }
    }

    /**
     * ファミコンの三角波 1 サンプル。[TRIANGLE_LEVELS] 段の階段を上って下りる。
     * なめらかに均すとあの濁りが消えてしまうので、段差はそのまま出す。
     */
    fun chipTriangle(phase: Double): Float {
        val t = phase - floor(phase)
        val slot = (t * TRIANGLE_SLOTS).toInt().coerceIn(0, TRIANGLE_SLOTS - 1)
        val step = if (slot < TRIANGLE_LEVELS) TRIANGLE_LEVELS - 1 - slot else slot - TRIANGLE_LEVELS
        return step / ((TRIANGLE_LEVELS - 1) / 2f) - 1f
    }

    /**
     * LFSR（線形帰還シフトレジスタ）を 1 段進める。
     * [shortPeriod] を立てると帰還を取る位置が変わり、93 段で 1 周して
     * 音程のある金属音になる。立てなければ 32767 段で、ざらついた雑音。
     */
    fun nextLfsr(state: Int, shortPeriod: Boolean): Int {
        val tap = if (shortPeriod) SHORT_TAP else LONG_TAP
        val feedback = (state and 1) xor ((state shr tap) and 1)
        return (state shr 1) or (feedback shl (LFSR_BITS - 1))
    }

    /** LFSR の今の出力。実機と同じく最下位ビットが 0 のときに上を向く。 */
    fun lfsrOutput(state: Int): Float = if (state and 1 == 0) 1f else -1f

    /** 三角波の段数と、1 周期ぶんの枠（上り 16 + 下り 16）。 */
    const val TRIANGLE_LEVELS = 16
    const val TRIANGLE_SLOTS = TRIANGLE_LEVELS * 2

    /** LFSR の桁数と、帰還を取る位置。0 以外なら何を種にしてもよい。 */
    const val LFSR_BITS = 15
    const val LFSR_SEED = 1
    private const val LONG_TAP = 1
    private const val SHORT_TAP = 6

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
