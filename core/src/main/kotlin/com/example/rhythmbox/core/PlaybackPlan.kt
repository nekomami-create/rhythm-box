package com.example.rhythmbox.core

/** 1 小節ぶんの再生内容: どのパターンを、どのコードで鳴らすか。 */
data class Bar(val patternIndex: Int, val chord: Chord)

/**
 * 「どの小節でどのパターンをどのコードで鳴らすか」だけを表す再生プラン。
 * パターン単体のループも曲構成の通し再生も、これ 1 つで表現する。
 */
class PlaybackPlan(
    private val patterns: List<Pattern>,
    val bars: List<Bar>,
) {
    val barCount: Int get() = bars.size

    val isEmpty: Boolean get() = bars.isEmpty() || patterns.isEmpty()

    fun barAt(bar: Int): Bar = bars[bar.coerceIn(bars.indices)]

    fun patternAt(bar: Int): Pattern {
        val index = barAt(bar).patternIndex
        return patterns[index.coerceIn(patterns.indices)]
    }

    fun chordAt(bar: Int): Chord = barAt(bar).chord

    companion object {
        /** 1 パターンだけを、そのパターンの試聴コードで延々とループする。 */
        fun single(song: Song, patternIndex: Int): PlaybackPlan {
            val index = patternIndex.coerceIn(song.patterns.indices)
            return PlaybackPlan(song.patterns, listOf(Bar(index, song.patternChord(index))))
        }

        /** 曲構成（繰り返し数ぶん展開したもの）。構成が空なら空のプランになる。 */
        fun arrangement(song: Song): PlaybackPlan {
            val bars = buildList {
                for (step in song.arrangement) {
                    val index = step.patternIndex.coerceIn(song.patterns.indices)
                    val fallback = song.patternChord(index)
                    repeat(step.repeat.coerceIn(0, MAX_REPEAT)) { barInBlock ->
                        add(Bar(index, step.chordAt(barInBlock, fallback)))
                    }
                }
            }
            return PlaybackPlan(song.patterns, bars)
        }

        const val MAX_REPEAT = 64
    }
}
