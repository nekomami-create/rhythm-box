package com.example.rhythmbox.core

import kotlin.math.abs
import kotlin.random.Random

/**
 * リード（単音の旋律）をその場で作る。
 *
 * 音を完全にランダムに選ぶと旋律にならないので、次の 3 つで縛っている。
 *  - 使う音は調の音階の中だけ。拍の頭ではコードの構成音に着地する
 *  - 直前の音から近い音ほど選ばれやすい（跳躍は時々だけ）
 *  - リズムは決まった型から 1 つ選び、そこから少しだけ音を抜く
 */
object MelodyGenerator {

    /** 使う音域（C4 〜 C6）。ピアノロールの表示範囲と合わせてある。 */
    const val LOWEST_MIDI = 60
    const val HIGHEST_MIDI = 84

    /** リズムの型。x が音を置く位置。詰め込み具合ごとに分けてある。 */
    private val RHYTHMS = mapOf(
        MelodyDensity.SPARSE to listOf(
            "x.......x.......",
            "x...............",
            "x.......x...x...",
            "x...x...........",
        ),
        MelodyDensity.NORMAL to listOf(
            "x...x...x...x...",
            "x.......x.......",
            "x...x.x...x.x...",
            "x...x...x.x.x...",
            "x..x..x...x..x..",
        ),
        MelodyDensity.BUSY to listOf(
            "x.x.x.x.x.x.x.x.",
            "x.x...x.x.x...x.",
            "x..x.x..x..x.x..",
            "x.x.x.x.x...x.x.",
            "x.xx..x.x.xx..x.",
        ),
    )

    /**
     * 小節の途中で和音が切り替わること。[atStep] から先は、拍の頭の音を
     * [chord] の構成音に合わせて選ぶ（[generate] 参照）。
     */
    data class ChordChange(val chord: Chord, val atStep: Int)

    fun generate(
        chord: Chord,
        key: MusicKey,
        random: Random = Random.Default,
        /** 直前の旋律。最後の音を受けて滑らかに繋げる。 */
        previous: List<Int>? = null,
        /** 音の詰め込み具合。ジャンルによって変える。 */
        density: MelodyDensity = MelodyDensity.NORMAL,
        /**
         * 小節の途中で和音が変わる場合はここに渡す。無ければ小節じゅう
         * [chord] のまま。呼び出し側（コード/ベースの打ち込み）と食い違うと
         * 拍の頭で「合わせたはずの構成音」が別の和音とぶつかって聞こえる。
         */
        chordChange: ChordChange? = null,
    ): List<Int> {
        val positions = pickPositions(density, random)
        val scale = pitchClasses(key)
        val chordTones = chord.voicing().map { it.mod(12) }.toSet()
        val laterChordTones = chordChange?.chord?.voicing()?.map { it.mod(12) }?.toSet()

        // 拍の頭・終わりに使う構成音。切り替わりのステップ以降は、後から
        // 鳴っている和音のほうに合わせる（そちらが実際に鳴っているため）。
        fun chordTonesAt(step: Int): Set<Int> =
            if (laterChordTones != null && step >= chordChange!!.atStep) laterChordTones else chordTones

        val lead = MutableList(STEPS_PER_BAR) { Pattern.REST }
        // 出だしは前の小節の最後の音の近くから。無ければ真ん中あたり。
        var current = previous?.lastOrNull { Pattern.isNote(it) } ?: START_MIDI
        var sameCount = 0

        positions.forEachIndexed { index, step ->
            val strongBeat = step % 4 == 0
            val last = index == positions.lastIndex
            // 拍の頭と終わりの音はコードの構成音に置いて、響きを外さないようにする。
            val pool = if (strongBeat || last) chordTonesAt(step) else scale
            val next = pickPitch(pool, current, sameCount, random)
            sameCount = if (next == current) sameCount + 1 else 0
            current = next
            lead[step] = next
        }
        holdLongNotes(lead, positions, chordChange?.atStep)
        return lead
    }

    /**
     * 次の音まで 1 拍以上あく音をタイで伸ばす。
     *
     * 伸ばさないと 1 拍で切れてしまい、間の広い旋律がぶつ切りに聞こえる。
     * 1 拍未満の空きはそのまま残して、歯切れの良さを保つ。
     *
     * [chordChangeStep] より前で鳴り始めた音は、そこをまたいでは伸ばさない。
     * 伸ばしたままだと、選んだときには無かった別の和音の上で鳴り続けて
     * ぶつかって聞こえる。
     */
    private fun holdLongNotes(lead: MutableList<Int>, positions: List<Int>, chordChangeStep: Int? = null) {
        positions.forEachIndexed { index, step ->
            val until = positions.getOrElse(index + 1) { STEPS_PER_BAR }
            val limit = if (chordChangeStep != null && step < chordChangeStep) {
                minOf(until, chordChangeStep)
            } else {
                until
            }
            if (limit - step < HOLD_MIN_STEPS) return@forEachIndexed
            for (tie in (step + 1) until limit) lead[tie] = Pattern.TIE
        }
    }

    /**
     * [chords] の並びに沿って、1 小節ずつ旋律を作る。
     *
     * 同じ旋律を繰り返すと、下のコードが変わったときに合わなくなる。
     * 小節ごとにそのコードの構成音へ着地させ、前の小節の最後の音から
     * 続きを書き始めることで、通して聴いたときに 1 本の線になるようにしている。
     */
    fun generateBars(
        chords: List<Chord>,
        key: MusicKey,
        random: Random = Random.Default,
        density: MelodyDensity = MelodyDensity.NORMAL,
        previous: List<Int>? = null,
        /** [chords] と同じ並びの、小節ごとの [ChordChange]（無ければ渡さない）。 */
        chordChanges: List<ChordChange?>? = null,
    ): List<List<Int>> {
        var last = previous
        return chords.mapIndexed { index, chord ->
            val bar = generate(chord, key, random, last, density, chordChanges?.getOrNull(index))
            last = bar
            bar
        }
    }

    /** リズムの型を 1 つ選び、拍の頭以外を少しだけ抜いて変化を付ける。 */
    private fun pickPositions(density: MelodyDensity, random: Random): List<Int> {
        val rhythm = RHYTHMS.getValue(density).random(random)
        val positions = rhythm.mapIndexedNotNull { step, c -> step.takeIf { c == 'x' } }
        val thinned = positions.filter { it == 0 || it % 4 == 0 || random.nextDouble() > REST_CHANCE }
        return thinned.ifEmpty { listOf(0) }
    }

    /**
     * [pool]（使ってよい音名）の中から、[current] に近い音を選ぶ。
     * 距離が離れるほど選ばれにくく、同じ音が続きすぎたらずらす。
     */
    private fun pickPitch(pool: Set<Int>, current: Int, sameCount: Int, random: Random): Int {
        val candidates = (LOWEST_MIDI..HIGHEST_MIDI).filter { it.mod(12) in pool }
        if (candidates.isEmpty()) return current
        val weights = candidates.map { midi ->
            val distance = abs(midi - current)
            when {
                distance == 0 && sameCount >= 1 -> 0.05 // 同じ音の連打を避ける
                distance == 0 -> 0.5
                distance <= 2 -> 1.0 // 隣り合う音（順次進行）が旋律の基本
                distance <= 4 -> 0.55
                distance <= 7 -> 0.25
                distance <= 12 -> 0.06
                else -> 0.0 // 1 オクターブを超える跳躍はしない
            }
        }
        val total = weights.sum()
        if (total <= 0.0) return candidates.minByOrNull { abs(it - current) } ?: current
        var target = random.nextDouble() * total
        for (index in candidates.indices) {
            target -= weights[index]
            if (target <= 0.0) return candidates[index]
        }
        return candidates.last()
    }

    /** その調の音階（音名の集合）。 */
    private fun pitchClasses(key: MusicKey): Set<Int> = key.scalePitches()

    /** 出だしの目安（C5）。 */
    private const val START_MIDI = 72

    /** 拍の頭以外の音を抜く確率。 */
    private const val REST_CHANCE = 0.25

    /** これ以上あくならタイで伸ばす（1 拍）。 */
    private const val HOLD_MIN_STEPS = 4
}
