package com.example.rhythmbox.core

import kotlin.math.PI
import kotlin.math.exp

/** 残響の広さ。長いほど尾を引く。 */
enum class RoomSize(val label: String, val feedback: Float, val damping: Float) {
    /** 狭い部屋。尾が短く、輪郭を保ったまま少しだけ奥行きが出る。 */
    SMALL("狭い", 0.70f, 0.45f),

    /** 普通の部屋。 */
    MEDIUM("普通", 0.83f, 0.35f),

    /** ホール。尾が長く、静かなところで大きく広がる。 */
    LARGE("広い", 0.90f, 0.25f),
    ;

    /**
     * 尾が消えるまでの秒数。書き出しの余韻をここから決める。
     *
     * つまみを右端にして 1 発だけ鳴らし、-80 dB を下回るまでを測った値
     * （0.70 / 1.31 / 2.37 秒）に余裕を足してある。短いと書き出した
     * ファイルの末尾で残響がぶつ切りになる。
     */
    val tailSeconds: Double
        get() = when (this) {
            SMALL -> 1.2
            MEDIUM -> 2.0
            LARGE -> 3.2
        }
}

/**
 * 残響（リバーブ）。ミックスの最後に足す。
 *
 * 遅延を少しずつ違う長さで 8 本並べて（コムフィルタ）密度を作り、
 * そのあと 4 段の全域通過（オールパス）で反射のばらつきを増やす。
 * Schroeder が示した組み立て方で、Freeverb がよく知られている。
 * 遅延の長さは互いに素に近い値を選んであり、揃うと金属的な響きになる。
 *
 * 左右で遅延の長さをずらしてあるので、出てくる尾は左右で違う。
 * ステレオにした意味がいちばん出るのがここで、モノラルのままだと
 * 残響を足しても「奥」に行くだけで「広がり」にはならない。
 *
 * 送る前に低い音を落としている（[HIGHPASS_HZ]）。ミックス全体を
 * そのまま入れるとキックとベースの残響が溜まって濁り、曲の芯が
 * 無くなる。低いところを送らなければ、ハット・スネア・コード・
 * リードだけがふくらんで、土台は締まったまま残る。つまみを
 * 増やさずに、掛けても壊れないようにするための一手。
 *
 * 音声スレッドからだけ触る。[process] は何も確保しない。
 *
 * 掛かる手間は実測で、エンジン全体が実時間の 3.48 %、残響を足して
 * 3.65 %（48 kHz・144 フレームずつ・広さによらずほぼ同じ）。
 * 溜めが 6 ms しかないので、重ければ音が途切れる。足す前に測った。
 */
class Reverb(sampleRate: Int) {

    private val combLeft = Array(COMB_LENGTHS.size) { Comb(lengthAt(COMB_LENGTHS[it], sampleRate)) }
    private val combRight =
        Array(COMB_LENGTHS.size) { Comb(lengthAt(COMB_LENGTHS[it] + STEREO_SPREAD, sampleRate)) }
    private val allpassLeft =
        Array(ALLPASS_LENGTHS.size) { Allpass(lengthAt(ALLPASS_LENGTHS[it], sampleRate)) }
    private val allpassRight =
        Array(ALLPASS_LENGTHS.size) { Allpass(lengthAt(ALLPASS_LENGTHS[it] + STEREO_SPREAD, sampleRate)) }

    /** 低い音を落とすための一次フィルタの係数。 */
    private val highpassCoefficient = (1.0 - exp(-2.0 * PI * HIGHPASS_HZ / sampleRate)).toFloat()

    private var lowLeft = 0f
    private var lowRight = 0f

    /** 溜まっている尾を捨てる。頭から鳴らし直すときに前の残響が残らないようにする。 */
    fun clear() {
        for (comb in combLeft) comb.clear()
        for (comb in combRight) comb.clear()
        for (allpass in allpassLeft) allpass.clear()
        for (allpass in allpassRight) allpass.clear()
        lowLeft = 0f
        lowRight = 0f
    }

    /**
     * [out] の [offset] フレームから [count] フレームぶんに残響を足す。
     *
     * 乾いた音はそのまま残し、その上に濡れた音を足す（差し替えではない）。
     * こうしておくと [amount] が 0 のときの出力は元の音そのもので、
     * 掛けはじめても曲全体が小さくならない。
     *
     * [amount] が 0 なら何もしない。掛け算で 0 にするのではなく処理ごと
     * 飛ばすので、使っていない曲では 1 サンプルも変わらず、CPU も増えない。
     */
    fun process(out: FloatArray, offset: Int, count: Int, amount: Float, size: RoomSize) {
        if (amount <= 0f) return
        val wet = amount.coerceAtMost(1f) * WET_SCALE
        val feedback = size.feedback
        val damping = size.damping
        for (k in 0 until count) {
            val at = (offset + k) * CHANNELS
            val dryLeft = out[at]
            val dryRight = out[at + 1]

            // 低いところを取り除いてから送る（引いた残りが高いところ）。
            lowLeft += (dryLeft - lowLeft) * highpassCoefficient
            lowRight += (dryRight - lowRight) * highpassCoefficient
            val sendLeft = (dryLeft - lowLeft) * INPUT_GAIN
            val sendRight = (dryRight - lowRight) * INPUT_GAIN

            var wetLeft = 0f
            var wetRight = 0f
            for (i in combLeft.indices) {
                wetLeft += combLeft[i].process(sendLeft, feedback, damping)
                wetRight += combRight[i].process(sendRight, feedback, damping)
            }
            for (i in allpassLeft.indices) {
                wetLeft = allpassLeft[i].process(wetLeft)
                wetRight = allpassRight[i].process(wetRight)
            }
            out[at] = dryLeft + wetLeft * wet
            out[at + 1] = dryRight + wetRight * wet
        }
    }

    /**
     * 遅延を 1 本ぶん。読んだ値を減衰させて書き戻すことで、
     * 一定の間隔で弱くなりながら繰り返す反射になる。
     * 戻す前に高い音を少し削る（[damping]）ので、遠くの反射ほどこもる。
     */
    private class Comb(size: Int) {
        private val buffer = FloatArray(size)
        private var index = 0
        private var store = 0f

        fun process(input: Float, feedback: Float, damping: Float): Float {
            val output = buffer[index]
            store = output * (1f - damping) + store * damping
            buffer[index] = input + store * feedback
            if (++index >= buffer.size) index = 0
            return output
        }

        fun clear() {
            buffer.fill(0f)
            store = 0f
            index = 0
        }
    }

    /**
     * 音の大きさは変えずに、周波数ごとに届く時刻だけをずらす段。
     * コムフィルタが作った反射をここでばらけさせると、
     * 規則正しい繰り返しに聞こえなくなる。
     */
    private class Allpass(size: Int) {
        private val buffer = FloatArray(size)
        private var index = 0

        fun process(input: Float): Float {
            val buffered = buffer[index]
            buffer[index] = input + buffered * ALLPASS_FEEDBACK
            if (++index >= buffer.size) index = 0
            return buffered - input
        }

        fun clear() {
            buffer.fill(0f)
            index = 0
        }
    }

    companion object {
        /**
         * 遅延の長さ（44,100 Hz でのサンプル数）。Freeverb が使っている値で、
         * 互いに素に近いので反射が揃わない。
         */
        private val COMB_LENGTHS = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        private val ALLPASS_LENGTHS = intArrayOf(556, 441, 341, 225)

        /** 右チャンネルをこのぶんだけ長くして、左右の尾を別物にする。 */
        private const val STEREO_SPREAD = 23

        /** 遅延の長さを決めた基準の周波数。 */
        private const val REFERENCE_RATE = 44_100

        private const val ALLPASS_FEEDBACK = 0.5f

        /** ここより低い音は残響に送らない。 */
        private const val HIGHPASS_HZ = 300.0

        /**
         * 送りの大きさ。コムフィルタ 8 本ぶんが溜まると桁が上がるので、
         * 入れる時点で十分小さくしておく。
         */
        private const val INPUT_GAIN = 0.015f

        /**
         * つまみを右端まで回したときの濡れ具合。
         *
         * 既定の曲を鳴らして、乾いた音と濡れた音の実効値を測って決めた。
         * この倍率で、右端のときの濡れ / 乾きは 狭い 0.30・普通 0.38・
         * 広い 0.50 になる。1.0（＝乾いた音と同じ大きさの残響）は
         * 溺れた音で、ここが上限だと使える範囲が右端だけ無駄になる。
         * 広い部屋ほど濡れるのは、実際の部屋でもそうなるので直していない。
         */
        const val WET_SCALE = 3.0f

        /** 基準の周波数で決めた長さを、実際の周波数に合わせる。 */
        private fun lengthAt(length: Int, sampleRate: Int): Int =
            (length.toLong() * sampleRate / REFERENCE_RATE).toInt().coerceAtLeast(1)
    }
}
