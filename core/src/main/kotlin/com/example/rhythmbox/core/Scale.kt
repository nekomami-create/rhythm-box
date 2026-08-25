package com.example.rhythmbox.core

import kotlinx.serialization.Serializable

/**
 * 音階。[intervals] は主音からの半音の並び。
 *
 * どの音を「調の中」とみなすかを決める。旋律の生成・ピアノロールの色分け・
 * コードの候補が、すべてここを見る。
 */
@Serializable
enum class Scale(val label: String, val intervals: List<Int>) {
    MAJOR("メジャー", listOf(0, 2, 4, 5, 7, 9, 11)),
    NATURAL_MINOR("マイナー", listOf(0, 2, 3, 5, 7, 8, 10)),
    HARMONIC_MINOR("和声的短音階", listOf(0, 2, 3, 5, 7, 8, 11)),
    MELODIC_MINOR("旋律的短音階", listOf(0, 2, 3, 5, 7, 9, 11)),
    DORIAN("ドリアン", listOf(0, 2, 3, 5, 7, 9, 10)),
    PHRYGIAN("フリジアン", listOf(0, 1, 3, 5, 7, 8, 10)),
    LYDIAN("リディアン", listOf(0, 2, 4, 6, 7, 9, 11)),
    MIXOLYDIAN("ミクソリディアン", listOf(0, 2, 4, 5, 7, 9, 10)),
    LOCRIAN("ロクリアン", listOf(0, 1, 3, 5, 6, 8, 10)),
    MAJOR_PENTATONIC("メジャーペンタ", listOf(0, 2, 4, 7, 9)),
    MINOR_PENTATONIC("マイナーペンタ", listOf(0, 3, 5, 7, 10)),
    BLUES("ブルース", listOf(0, 3, 5, 6, 7, 10)),
    ;

    /** 短調寄りか。終止形の作り方など、明暗で分かれる処理で使う。 */
    val minorish: Boolean get() = 3 in intervals

    /** 7 音そろっているか。三度を積んでコードを作れるのはこの場合だけ。 */
    val heptatonic: Boolean get() = intervals.size == 7

    /**
     * コードを組み立てるときの土台。
     *
     * ペンタトニックやブルースは音が足りず三度を積めないので、
     * 明暗の近いほうの 7 音音階から借りる。旋律と色分けは元の音階のままなので、
     * 「使う音は 5 音、コードは普通に付く」という自然な形になる。
     */
    val chordSource: Scale get() = when {
        heptatonic -> this
        minorish -> NATURAL_MINOR
        else -> MAJOR
    }

    /** その音階の音（C=0 起点の半音番号）。 */
    fun pitches(tonic: Int): Set<Int> = intervals.map { (tonic + it).mod(12) }.toSet()
}
