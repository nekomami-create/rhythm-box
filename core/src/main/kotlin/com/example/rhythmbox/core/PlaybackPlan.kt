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
)

/**
 * 和音が変わる位置と、そこで鳴らす音。
 *
 * 打ち込みにコードを置けるようになって、和音は小節の頭以外でも変わるように
 * なった。「いつ何の和音か」を 1 本の並びにしておくと、鳴らす側は
 * 小節でもステップでも同じ引き方で済む。
 */
data class ChordChange(
    val bar: Int,
    val step: Int,
    val chord: Chord,
    /** 前後と繋がるように選び直した音。 */
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
    /**
     * 和音が変わるところを頭から並べたもの。小節ごとに必ず 1 つ（頭のぶん）あり、
     * 打ち込みに置いたコードがあればその位置にも入る。
     *
     * 声部の繋がりはこの並びに沿って解く。小節ごとに解いていたのを変わり目ごとに
     * したので、1 小節に 2 つ置いても間が繋がる。
     */
    val changes: List<ChordChange> = buildChanges()

    /** 小節ごとの、[changes] の中での開始位置。引くときに頭から探さなくて済む。 */
    private val barStart: IntArray = IntArray(bars.size).also { starts ->
        changes.forEachIndexed { index, change ->
            if (change.step == 0 && change.bar in starts.indices) starts[change.bar] = index
        }
    }

    private fun buildChanges(): List<ChordChange> {
        if (bars.isEmpty() || patterns.isEmpty()) return emptyList()
        val raw = buildList {
            bars.forEachIndexed { index, bar ->
                val pattern = patterns.getOrNull(bar.patternIndex)
                val placed = pattern?.takeIf { it.hasChords }
                // 小節の頭は必ず 1 つ置く。ここが決まっていれば、あとは
                // 「その位置を過ぎない最後のもの」を探すだけで引ける。
                add(ChordChange(index, 0, placed?.chordAt(bar.patternBar, 0) ?: bar.chord))
                placed?.gridAt(bar.patternBar)?.chords
                    ?.filter { it.step > 0 }
                    ?.forEach { add(ChordChange(index, it.step, it.chord)) }
            }
        }
        val voicings = Voicing.lead(raw.map { it.chord })
        return raw.mapIndexed { index, change -> change.copy(voicing = voicings[index]) }
    }

    /** [bar] の [step] の時点で効いている変わり目。 */
    private fun changeAt(bar: Int, step: Int): ChordChange? {
        if (changes.isEmpty()) return null
        val at = bar.coerceIn(bars.indices)
        var index = barStart.getOrElse(at) { 0 }
        while (index + 1 < changes.size &&
            changes[index + 1].bar == at &&
            changes[index + 1].step <= step
        ) {
            index++
        }
        return changes[index]
    }
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

    /** [bar] の [step] で鳴っている和音。 */
    fun chordAt(bar: Int, step: Int = 0): Chord = changeAt(bar, step)?.chord ?: barAt(bar).chord

    /**
     * [bar] の [step] で鳴らす和音の音。繋がりを解いた結果が無ければ、和音そのものから作る。
     *
     * 解いた結果は常に持たせてあるが、実際に使うかどうかは鳴らす側が決める
     * （曲の設定は途中で変えられるので、プランを作り直さずに切り替えられる）。
     */
    fun voicingAt(bar: Int, step: Int = 0): List<Int> {
        val change = changeAt(bar, step)
        return change?.voicing?.ifEmpty { change.chord.voicing() } ?: chordAt(bar, step).voicing()
    }

    /**
     * [bar] の [step] のあと、次に変わる和音。無ければ先頭へ戻る。
     *
     * 曲はループするので、最後の次は先頭。書き出し（1 回だけ鳴らす）でも
     * 同じ扱いにしておくと、終わりの小節が頭のコードへ入る形になり、
     * 繰り返して聴いたときに繋がる。
     */
    fun nextChordAt(bar: Int, step: Int = STEPS_PER_BAR - 1): Chord {
        if (changes.isEmpty()) return Chord()
        val here = changeAt(bar, step) ?: return changes.first().chord
        val index = changes.indexOf(here)
        return changes[(index + 1).mod(changes.size)].chord
    }

    /**
     * [bar] の [step] より後で、この小節の中で次に和音が変わるステップ。
     * この小節の中で変わらなければ [STEPS_PER_BAR]（＝小節の終わり）。
     *
     * ベースが「次の和音へ向かう音」をどこで弾くかを決めるのに使う。
     * 小節の途中で和音が変われば、その手前が向かう先になる。
     */
    fun nextChangeStepAt(bar: Int, step: Int): Int {
        val at = bar.coerceIn(bars.indices)
        return changes
            .firstOrNull { it.bar == at && it.step > step }
            ?.step
            ?: STEPS_PER_BAR
    }

    /** その小節で鳴らす、パターンの中の小節番号。 */
    fun patternBarAt(bar: Int): Int = barAt(bar).patternBar

    /** 同じ並びを [times] 回繰り返したプラン（書き出しで「2 回ぶん」などに使う）。 */
    fun repeated(times: Int): PlaybackPlan =
        PlaybackPlan(patterns, List(times.coerceAtLeast(1)) { bars }.flatten())

    companion object {
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
            return PlaybackPlan(song.patterns, bars)
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
