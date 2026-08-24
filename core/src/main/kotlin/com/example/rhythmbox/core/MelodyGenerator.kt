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

    /** リズムの型。x が音を置く位置。 */
    private val RHYTHMS = listOf(
        "x...x...x...x...",
        "x.......x.......",
        "x.x.x.x.x.x.x.x.",
        "x..x..x...x..x..",
        "x...x.x...x.x...",
        "x.x...x.x.x...x.",
        "x..x.x..x..x.x..",
        "x...x...x.x.x...",
    )

    fun generate(
        chord: Chord,
        key: MusicKey,
        random: Random = Random.Default,
        /** 直前の旋律。最後の音を受けて滑らかに繋げる。 */
        previous: List<Int>? = null,
    ): List<Int> {
        val positions = pickPositions(random)
        val scale = pitchClasses(key)
        val chordTones = chord.voicing().map { it.mod(12) }.toSet()

        val lead = MutableList(STEPS_PER_BAR) { Pattern.REST }
        // 出だしは前の小節の最後の音の近くから。無ければ真ん中あたり。
        var current = previous?.lastOrNull { it != Pattern.REST } ?: START_MIDI
        var sameCount = 0

        positions.forEachIndexed { index, step ->
            val strongBeat = step % 4 == 0
            val last = index == positions.lastIndex
            // 拍の頭と終わりの音はコードの構成音に置いて、響きを外さないようにする。
            val pool = if (strongBeat || last) chordTones else scale
            val next = pickPitch(pool, current, sameCount, random)
            sameCount = if (next == current) sameCount + 1 else 0
            current = next
            lead[step] = next
        }
        return lead
    }

    /** リズムの型を 1 つ選び、拍の頭以外を少しだけ抜いて変化を付ける。 */
    private fun pickPositions(random: Random): List<Int> {
        val rhythm = RHYTHMS.random(random)
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
    private fun pitchClasses(key: MusicKey): Set<Int> =
        key.diatonicChords().map { it.root.mod(12) }.toSet()

    /** 出だしの目安（C5）。 */
    private const val START_MIDI = 72

    /** 拍の頭以外の音を抜く確率。 */
    private const val REST_CHANCE = 0.25
}
