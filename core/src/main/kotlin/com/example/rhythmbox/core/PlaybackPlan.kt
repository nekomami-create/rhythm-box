package com.example.rhythmbox.core

/**
 * 「どの小節でどのパターンを鳴らすか」だけを表す再生プラン。
 * パターン単体のループも曲構成の通し再生も、これ 1 つで表現する。
 */
class PlaybackPlan(
    private val patterns: List<Pattern>,
    /** 小節ごとに鳴らすパターンの番号。 */
    val bars: List<Int>,
) {
    val barCount: Int get() = bars.size

    val isEmpty: Boolean get() = bars.isEmpty() || patterns.isEmpty()

    fun patternAt(bar: Int): Pattern {
        val index = bars[bar.coerceIn(bars.indices)]
        return patterns[index.coerceIn(patterns.indices)]
    }

    companion object {
        /** 1 パターンだけを延々とループする。 */
        fun single(song: Song, patternIndex: Int): PlaybackPlan =
            PlaybackPlan(song.patterns, listOf(patternIndex.coerceIn(song.patterns.indices)))

        /** 曲構成（繰り返し数ぶん展開したもの）。構成が空なら空のプランになる。 */
        fun arrangement(song: Song): PlaybackPlan {
            val bars = buildList {
                for (step in song.arrangement) {
                    val index = step.patternIndex.coerceIn(song.patterns.indices)
                    repeat(step.repeat.coerceIn(0, MAX_REPEAT)) { add(index) }
                }
            }
            return PlaybackPlan(song.patterns, bars)
        }

        const val MAX_REPEAT = 64
    }
}
