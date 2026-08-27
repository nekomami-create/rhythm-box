package com.example.rhythmbox.core

/**
 * 1 小節ぶんの再生内容: どのパターンを、どのコードで鳴らすか。
 * [patternBar] は、そのパターンの何小節目を鳴らすか（＝繰り返し何回目か）。
 * 打ち込みも旋律も、この番号でパターンの中を引く。
 */
data class Bar(
    val patternIndex: Int,
    val chord: Chord,
    val patternBar: Int = 0,
    /**
     * 前後と繋がるように選び直した和音の音（[Voicing.lead] の結果）。
     * 空なら [Chord.voicing] をそのまま使う。
     *
     * ここで持たせるのは、決めるのに前後の小節が要るから。プランは全小節の
     * コードを知っているので 1 回で解けるが、音声スレッドは 1 小節ずつしか
     * 見ていないので解きようがない。
     */
    val voicing: List<Int> = emptyList(),
)

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

    /**
     * その小節で鳴らす打ち込み（1 小節ぶん）。
     *
     * 複数小節のパターンでも、ここで該当する小節を切り出して返すので、
     * 鳴らす側（再生・MIDI 書き出し）は今までどおり 1 小節ぶんとして読める。
     */
    fun patternAt(bar: Int): Pattern {
        val at = barAt(bar)
        return patterns[at.patternIndex.coerceIn(patterns.indices)].at(at.patternBar)
    }

    fun chordAt(bar: Int): Chord = barAt(bar).chord

    /**
     * その小節で鳴らす和音の音。繋がりを解いた結果が無ければ、和音そのものから作る。
     *
     * 解いた結果は常に持たせてあるが、実際に使うかどうかは鳴らす側が決める
     * （曲の設定は途中で変えられるので、プランを作り直さずに切り替えられる）。
     */
    fun voicingAt(bar: Int): List<Int> = barAt(bar).voicing.ifEmpty { barAt(bar).chord.voicing() }

    /**
     * 次の小節の和音。最後の小節では先頭へ戻る。
     *
     * 曲はループするので、最後の次は先頭。書き出し（1 回だけ鳴らす）でも
     * 同じ扱いにしておくと、終わりの小節が頭のコードへ入る形になり、
     * 繰り返して聴いたときに繋がる。
     */
    fun nextChordAt(bar: Int): Chord =
        if (bars.isEmpty()) Chord() else chordAt((bar + 1).mod(bars.size))

    /** その小節で鳴らす、パターンの中の小節番号。 */
    fun patternBarAt(bar: Int): Int = barAt(bar).patternBar

    /** 同じ並びを [times] 回繰り返したプラン（書き出しで「2 回ぶん」などに使う）。 */
    fun repeated(times: Int): PlaybackPlan =
        PlaybackPlan(patterns, List(times.coerceAtLeast(1)) { bars }.flatten())

    companion object {
        /**
         * 並んだ小節に、前後と繋がる和音の音を入れる。
         *
         * 曲の設定に関係なく必ず解いておく。そうしておけば、設定を切り替えた
         * ときにプランを作り直さずに済む（切り替えは鳴らす側で効く）。
         * 解くのは 1 小節あたり 30 通りほどの候補を比べるだけで、
         * ユーザーの操作 1 回につき 1 度しか走らない。
         */
        private fun voiceLed(bars: List<Bar>): List<Bar> {
            if (bars.isEmpty()) return bars
            val voicings = Voicing.lead(bars.map { it.chord })
            return bars.mapIndexed { index, bar -> bar.copy(voicing = voicings[index]) }
        }

        /**
         * 1 パターンだけをループする。
         * 2 小節以上のパターンは、そのぶんだけ小節を並べて順に鳴らす。
         * コードは、そのパターンを使っている曲構成のブロックがあればそこから取る
         * （曲の中で鳴るのと同じ響きで試聴できる）。
         */
        fun single(song: Song, patternIndex: Int): PlaybackPlan {
            val index = patternIndex.coerceIn(song.patterns.indices)
            val pattern = song.pattern(index)
            val fallback = song.patternChord(index)
            val block = song.arrangement.firstOrNull { it.patternIndex == index }
            val bars = List(pattern.barCount) { bar ->
                Bar(index, block?.chordAt(bar, fallback) ?: fallback, bar)
            }
            return PlaybackPlan(song.patterns, voiceLed(bars))
        }

        /**
         * 複数のパターンを順に鳴らす（A→B→C…のチェーン再生）。
         * 2 小節以上のパターンは、最後まで鳴らしてから次へ進む。
         */
        fun chain(song: Song, patternIndices: List<Int>): PlaybackPlan {
            val bars = patternIndices
                .filter { it in song.patterns.indices }
                .flatMap { index ->
                    val chord = song.patternChord(index)
                    List(song.pattern(index).barCount) { bar -> Bar(index, chord, bar) }
                }
            return PlaybackPlan(song.patterns, voiceLed(bars))
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
            return PlaybackPlan(song.patterns, voiceLed(bars))
        }

        const val MAX_REPEAT = 64
    }
}
