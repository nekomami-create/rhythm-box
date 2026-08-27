package com.example.rhythmbox.core

import kotlin.math.abs

/**
 * 和音をどの高さで鳴らすか（ボイシング）を決める。
 *
 * [Chord.voicing] は前後を見ずに、その和音だけから音を決めている。
 * そのため C → G と進むと全部の音がごっそり動く。人が弾くときは
 * 共通する音をその場に残し、動く音も近いところへ動かす（声部の進行）。
 * 同じコード進行でも、繋げ方だけで滑らかさがまるで変わる。
 *
 * ここでは転回形（構成音の積み順を変えたもの）を候補として並べ、
 * 前の和音からの移動がいちばん小さいものを選ぶ。
 *
 * 決めるのは再生プランを組み立てるときで、そのとき全小節のコードが
 * 分かっている。音声スレッドは決まった音を鳴らすだけで、今までどおり
 * 何も考えなくていい。
 */
object Voicing {

    /** この幅に収める。下は低すぎて濁らない線、上は細くなりすぎない線。 */
    const val LOWEST = 52
    const val HIGHEST = 81

    /** 最初の和音を置く目安の高さ。ここに近い転回形から始める。 */
    const val CENTRE = 64

    /**
     * 低いほうに足すルートの基準（C3）。
     *
     * ベースは 36〜47（C2 の帯）を弾く。ここを 48 起点にしておけば、
     * どの調でも必ず 1 オクターブ上に乗る。単純に「和音の 1 オクターブ下」に
     * すると、ルートが F#〜B のときだけベースと同じ音になってしまう
     * （和音の基準がその帯だけ 12 下がっているため、どちらも 42〜47 に着地する）。
     */
    const val LOW_ROOT_BASE = 48

    /** [chord] に足す低いルート（分数コードならそのベース音）。 */
    fun lowRoot(chord: Chord): Int = LOW_ROOT_BASE + (chord.bass ?: chord.root).mod(12)

    /**
     * [chord] の転回形の候補。積み順を 1 つずつ繰り上げ、
     * さらに全体を 1 オクターブ上下したものも含める。
     */
    fun candidates(chord: Chord): List<List<Int>> {
        val base = chord.voicing().sorted()
        if (base.isEmpty()) return emptyList()
        val rotations = mutableListOf<List<Int>>()
        var current = base
        repeat(base.size) {
            rotations += current
            // いちばん下の音を 1 オクターブ上へ動かす＝次の転回形。
            current = (current.drop(1) + (current.first() + 12)).sorted()
        }
        val shifted = rotations.flatMap { listOf(it.map { note -> note - 12 }, it, it.map { note -> note + 12 }) }
        val inRange = shifted.filter { it.first() >= LOWEST && it.last() <= HIGHEST }
        // どれも幅に入らない和音（音域の広い 9th など）は、そのままの形で鳴らす。
        return inRange.ifEmpty { listOf(base) }
    }

    /**
     * 2 つの和音の間で声部がどれだけ動いたか。
     *
     * それぞれの音から相手のいちばん近い音までを測って足す。両方向を足すのは、
     * 構成音の数が違う（三和音 → 7th など）ときに片側だけだと差が出ないため。
     */
    fun distance(from: List<Int>, to: List<Int>): Int {
        if (from.isEmpty() || to.isEmpty()) return 0
        return to.sumOf { note -> from.minOf { abs(note - it) } } +
            from.sumOf { note -> to.minOf { abs(note - it) } }
    }

    /**
     * [chords] を順に、前からの移動がいちばん小さい転回形で並べる。
     *
     * 2 周する。1 周目の終わりの和音を 2 周目の出発点にすると、
     * ループしたときの継ぎ目も繋がる（曲は最後まで行ったら頭へ戻るので、
     * 最後→最初の動きも声部の進行の一部になる）。
     */
    fun lead(chords: List<Chord>): List<List<Int>> {
        if (chords.isEmpty()) return emptyList()
        val first = pass(chords, previous = null)
        return pass(chords, previous = first.last())
    }

    private fun pass(chords: List<Chord>, previous: List<Int>?): List<List<Int>> {
        var last = previous
        return chords.map { chord ->
            val options = candidates(chord)
            val chosen = if (last == null) {
                // 出発点は真ん中あたり。曲ごとに高さが暴れないようにする。
                options.minByOrNull { abs(centreOf(it) - CENTRE) } ?: chord.voicing()
            } else {
                options.minByOrNull { distance(last!!, it) } ?: chord.voicing()
            }
            last = chosen
            chosen
        }
    }

    /** その並びの真ん中の高さ。 */
    private fun centreOf(notes: List<Int>): Int =
        if (notes.isEmpty()) CENTRE else (notes.first() + notes.last()) / 2
}
