package com.example.rhythmbox.core

/**
 * 1 小節ぶんの再生内容: どのパターンを、どのコードで鳴らすか。
 * [leadBar] は、そのパターンが持つ旋律のうち何小節目を鳴らすか（繰り返し何回目か）。
 */
data class Bar(val patternIndex: Int, val chord: Chord, val leadBar: Int = 0)

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

    /** その小節で鳴らす旋律の番号。 */
    fun leadBarAt(bar: Int): Int = barAt(bar).leadBar

    /** 同じ並びを [times] 回繰り返したプラン（書き出しで「2 回ぶん」などに使う）。 */
    fun repeated(times: Int): PlaybackPlan =
        PlaybackPlan(patterns, List(times.coerceAtLeast(1)) { bars }.flatten())

    companion object {
        /**
         * 1 パターンだけをループする。
         * 旋律を複数小節ぶん持っているパターンは、そのぶんだけ小節を並べて順に鳴らす。
         * コードは、そのパターンを使っている曲構成のブロックがあればそこから取る
         * （曲の中で鳴るのと同じ響きで試聴できる）。
         */
        fun single(song: Song, patternIndex: Int): PlaybackPlan {
            val index = patternIndex.coerceIn(song.patterns.indices)
            val pattern = song.pattern(index)
            val fallback = song.patternChord(index)
            val block = song.arrangement.firstOrNull { it.patternIndex == index }
            val bars = List(pattern.leadBarCount) { bar ->
                Bar(index, block?.chordAt(bar, fallback) ?: fallback, bar)
            }
            return PlaybackPlan(song.patterns, bars)
        }

        /** 複数のパターンを 1 小節ずつ順に鳴らす（A→B→C…のチェーン再生）。 */
        fun chain(song: Song, patternIndices: List<Int>): PlaybackPlan {
            val bars = patternIndices
                .filter { it in song.patterns.indices }
                .map { Bar(it, song.patternChord(it)) }
            return PlaybackPlan(song.patterns, bars)
        }

        /** 曲構成（繰り返し数ぶん展開したもの）。構成が空なら空のプランになる。 */
        fun arrangement(song: Song): PlaybackPlan {
            val bars = buildList {
                for (step in song.arrangement) {
                    val index = step.patternIndex.coerceIn(song.patterns.indices)
                    val fallback = song.patternChord(index)
                    repeat(step.repeat.coerceIn(0, MAX_REPEAT)) { barInBlock ->
                        // 繰り返し何回目かをそのまま旋律の番号にする。
                        add(Bar(index, step.chordAt(barInBlock, fallback), barInBlock))
                    }
                }
            }
            return PlaybackPlan(song.patterns, bars)
        }

        const val MAX_REPEAT = 64
    }
}
