package com.example.rhythmbox.core

import kotlin.random.Random

/** 自動生成するリズムの種類。 */
enum class RhythmStyle(val label: String) {
    EIGHT_BEAT("8ビート"),
    FOUR_ON_FLOOR("4つ打ち"),
    BREAKBEAT("ブレイクビーツ"),
    HIPHOP("ヒップホップ"),
    LATIN("ラテン"),
    CHIP_DRIVE("チップ"),
    HARD_ROCK("ハードロック"),
    DIGITAL("打ち込み"),
}

/**
 * リズムパターンをその場で作る。
 *
 * 完全な乱数だと音楽にならないので、スタイルごとに
 * 「必ず置く位置（[RowRule.anchors]）」と「置くかもしれない確率」を持たせ、
 * 拍の頭は固定したまま細かいところだけを揺らす。
 */
object PatternGenerator {

    private class RowRule(
        val row: Int,
        /** 必ず置く位置。 */
        val anchors: List<Int> = emptyList(),
        /** 置くかもしれない位置と、その確率。 */
        val chances: DoubleArray = DoubleArray(STEPS_PER_BAR),
        /**
         * 刻み方の候補。ハイハットのように「まず一定の刻みを敷く」パートで使う。
         * ここから 1 つ選んで丸ごと置いたうえで、[chances] で飾りを足す。
         * 確率だけで作ると刻みにランダムな穴が空いて雑に聞こえるため。
         */
        val grids: List<List<Int>> = emptyList(),
        /**
         * 小節の頭を外して、少し遅れて入る確率。
         *
         * コードが毎小節きっちり頭で鳴ると、何を打っても行進曲の硬さが残る。
         * ときどき頭を空けて 16 分ぶん、あるいは 2 拍目から入ると、
         * 同じ打ち込みのまま前へつんのめらない感じが出る。
         *
         * 土台が抜ける心配は要らない。ベースはどの型でも頭に固定してあるので、
         * コードが遅れても 1 拍目そのものは鳴っている。
         */
        val lateStart: Double = 0.0,
    )

    fun generate(style: RhythmStyle, random: Random = Random.Default, name: String = "A"): Pattern {
        val rows = MutableList(STEP_ROW_COUNT) { 0 }
        for (rule in rules(style)) {
            var bits = 0
            if (rule.grids.isNotEmpty()) {
                for (step in rule.grids.random(random)) bits = bits or (1 shl step)
            }
            for (step in rule.anchors) bits = bits or (1 shl step)
            for (step in 0 until STEPS_PER_BAR) {
                if (random.nextDouble() < rule.chances[step]) bits = bits or (1 shl step)
            }
            if (rule.lateStart > 0.0 && random.nextDouble() < rule.lateStart) {
                bits = delayedStart(bits, random)
            }
            rows[rule.row] = bits and Pattern.STEP_MASK
        }
        return accented(Pattern(name, rows), random)
    }

    /**
     * [base] の最後の 1 拍を崩したもの（フィル）。
     *
     * 4 小節が寸分たがわず同じだと、いくら中身が良くても「ループ」に聞こえて
     * 「曲」にならない。区切りの手前でいったん刻みを止めて叩き込むと、
     * そこで一区切り付いて、次の 4 小節が始まったように聞こえる。
     *
     * 崩すのは最後の 1 拍だけ。長く崩すと土台が抜けて間延びするし、
     * 4 ステップなら必ず小節に収まるので、どの型に当てても破綻しない。
     *
     * 触るのはドラムの行だけ。コードとベースはそのまま鳴らす。フィルの
     * あいだ和音まで止まると、曲が切れたように聞こえてしまう。
     */
    fun fill(base: Pattern, random: Random): Pattern {
        val window = STEPS_PER_BAR - FILL_STEPS
        val mask = ((1 shl FILL_STEPS) - 1) shl window
        val run = FILL_RUNS.random(random)

        // 叩き込む音をまず組み立てる。
        val hits = IntArray(DRUM_COUNT)
        var last = -1
        run.forEachIndexed { offset, voice ->
            if (voice == null) return@forEachIndexed
            val step = window + offset
            hits[voice.ordinal] = hits[voice.ordinal] or (1 shl step)
            last = step
        }

        var result = base
        for (voice in 0 until DRUM_COUNT) {
            // 窓の中の刻みを止めてから叩き込む。止めないと叩き込みが埋もれる。
            result = result.withRow(voice, (base.rowAt(voice) and mask.inv()) or hits[voice])
        }
        // 最後の 1 発だけ強くして、次の小節の頭へ送り出す。
        val tail = run.lastOrNull { it != null }
        if (last >= 0 && tail != null) {
            val row = tail.ordinal
            result = result.withLevels(
                row,
                result.maskAt(result.accents, row) or (1 shl last),
                result.maskAt(result.ghosts, row) and (1 shl last).inv(),
            )
        }
        return result
    }

    /** 崩すのは最後の 1 拍（16 分 4 つ）。 */
    private const val FILL_STEPS = 4

    /**
     * 叩き込みの型。null は休み。
     * タムは 1 音色しか無いが、発音ごとに高さが揺れるので連打しても平板にならない。
     */
    private val FILL_RUNS: List<List<Voice?>> = listOf(
        listOf(Voice.SNARE, Voice.SNARE, Voice.SNARE, Voice.SNARE),
        listOf(Voice.SNARE, Voice.SNARE, Voice.TOM, Voice.TOM),
        listOf(Voice.TOM, Voice.TOM, Voice.SNARE, Voice.SNARE),
        listOf(Voice.SNARE, null, Voice.SNARE, Voice.SNARE),
        listOf(Voice.TOM, null, Voice.TOM, Voice.SNARE),
        listOf(null, Voice.SNARE, Voice.TOM, Voice.SNARE),
        listOf(null, null, Voice.SNARE, Voice.SNARE),
    )

    /**
     * 強弱を置く。すべて同じ音量だと打ち込みが機械的に聞こえる。
     *
     * 大事なのは「強い音を置く」ことではなく「差を作る」こと。
     * 拍の頭を一律に強くすると、4 分打ちでは全部が強になって平坦に戻ってしまう。
     * そこで小節の頭を軸に据え、そこから拍の重みで段を付ける。
     *
     * - 小節の頭（1 拍目）… 必ず強い
     * - 3 拍目 … 半々で強い（4 拍子の第 2 の重心）
     * - 16 分の裏（奇数ステップ）… 弱く。ここを抜くと刻みが人の手つきになる
     * - 8 分の裏 … ときどき弱く
     *
     * キックとスネアは「置いた場所そのものが要」なので弱くはしない。
     */
    private fun accented(pattern: Pattern, random: Random): Pattern {
        var result = pattern
        for (row in 0 until DRUM_COUNT) {
            val bits = pattern.rowAt(row)
            if (bits == 0) continue
            val keeper = row == Voice.KICK.ordinal || row == Voice.SNARE.ordinal
            var accent = 0
            var ghost = 0
            for (step in 0 until STEPS_PER_BAR) {
                if ((bits shr step) and 1 == 0) continue
                when {
                    step == 0 -> accent = accent or (1 shl step)
                    step == 8 && random.nextDouble() < DOWNBEAT_ACCENT_CHANCE ->
                        accent = accent or (1 shl step)
                    keeper -> Unit
                    step % 2 == 1 -> ghost = ghost or (1 shl step)
                    step % 4 == 2 && random.nextDouble() < OFFBEAT_GHOST_CHANCE ->
                        ghost = ghost or (1 shl step)
                }
            }
            result = result.withLevels(row, accent, ghost)
        }
        return result
    }

    /**
     * 小節の頭を外して、代わりに少し後ろから入れる。
     *
     * 16 分 1 つぶん（2）か、2 拍目（4）まで待つ。前者は「もたつき」で、
     * 後者は頭を丸ごと空ける。空けすぎると間延びするので、
     * もたつくほうを多めにする。
     *
     * もともと頭に無いなら何もしない。動かした結果 1 つも音が無くなる、
     * ということは起きない（必ず 2 か 4 に置き直すため）。
     */
    private fun delayedStart(bits: Int, random: Random): Int {
        if (bits and 1 == 0) return bits
        val target = if (random.nextDouble() < DRAG_CHANCE) DRAG_STEP else SECOND_BEAT_STEP
        return (bits and 1.inv()) or (1 shl target)
    }

    /** 頭を外したとき、16 分ぶんのもたつきで済ませる確率（残りは 2 拍目まで待つ）。 */
    private const val DRAG_CHANCE = 0.6

    /** もたつく先。 */
    private const val DRAG_STEP = 2

    /** 頭を丸ごと空けたときの入り。 */
    private const val SECOND_BEAT_STEP = 4

    /** 3 拍目も強くする確率。毎回だと 1 拍目との差が消える。 */
    private const val DOWNBEAT_ACCENT_CHANCE = 0.5

    /** 8 分の裏を弱くする確率。 */
    private const val OFFBEAT_GHOST_CHANCE = 0.4


    /** ハイハットなどの刻み方。 */
    private val QUARTERS = listOf(0, 4, 8, 12)
    private val EIGHTHS = listOf(0, 2, 4, 6, 8, 10, 12, 14)
    private val OFFBEATS = listOf(2, 6, 10, 14)
    private val SIXTEENTHS = (0 until STEPS_PER_BAR).toList()

    /** どの刻みが出やすいかは、同じものを並べて表す。 */
    private val STRAIGHT_GRIDS = listOf(EIGHTHS, EIGHTHS, EIGHTHS, SIXTEENTHS, QUARTERS)
    private val DANCE_GRIDS = listOf(OFFBEATS, OFFBEATS, EIGHTHS, SIXTEENTHS)
    private val BUSY_GRIDS = listOf(SIXTEENTHS, SIXTEENTHS, EIGHTHS)

    /** スタイルもランダムに選んで作る。 */
    fun generateAny(random: Random = Random.Default, name: String = "A"): Pattern =
        generate(RhythmStyle.entries.random(random), random, name)

    private fun rules(style: RhythmStyle): List<RowRule> = when (style) {
        RhythmStyle.EIGHT_BEAT -> listOf(
            RowRule(Voice.KICK.ordinal, listOf(0, 8), chances(3 to 0.35, 6 to 0.30, 10 to 0.25, 11 to 0.20, 14 to 0.30)),
            RowRule(Voice.SNARE.ordinal, listOf(4, 12), chances(7 to 0.12, 15 to 0.15)),
            RowRule(Voice.CLOSED_HAT.ordinal, grids = STRAIGHT_GRIDS, chances = odds(0.10)),
            RowRule(Voice.OPEN_HAT.ordinal, chances = chances(6 to 0.15, 14 to 0.35)),
            RowRule(Voice.CLAP.ordinal, chances = chances(4 to 0.20, 12 to 0.20)),
            RowRule(Voice.TOM.ordinal, chances = chances(13 to 0.12, 14 to 0.12, 15 to 0.15)),
            RowRule(ROW_CHORD, listOf(0), chances(4 to 0.20, 8 to 0.50, 12 to 0.20), lateStart = 0.18),
            RowRule(ROW_BASS, listOf(0), chances(3 to 0.30, 6 to 0.35, 8 to 0.70, 10 to 0.30, 14 to 0.30)),
        )

        RhythmStyle.FOUR_ON_FLOOR -> listOf(
            RowRule(Voice.KICK.ordinal, listOf(0, 4, 8, 12)),
            RowRule(Voice.SNARE.ordinal, chances = chances(4 to 0.15, 12 to 0.15)),
            RowRule(Voice.CLOSED_HAT.ordinal, grids = DANCE_GRIDS, chances = odds(0.08)),
            RowRule(Voice.OPEN_HAT.ordinal, chances = offbeats(0.22)),
            RowRule(Voice.CLAP.ordinal, listOf(4, 12)),
            RowRule(Voice.COWBELL.ordinal, chances = chances(7 to 0.10, 15 to 0.10)),
            RowRule(ROW_CHORD, listOf(0), chances(4 to 0.30, 8 to 0.50, 12 to 0.30), lateStart = 0.12),
            RowRule(ROW_BASS, listOf(0, 8), offbeats(0.55)),
        )

        RhythmStyle.BREAKBEAT -> listOf(
            RowRule(Voice.KICK.ordinal, listOf(0), chances(3 to 0.50, 6 to 0.45, 10 to 0.55, 11 to 0.30, 14 to 0.20)),
            RowRule(Voice.SNARE.ordinal, listOf(4, 12), chances(7 to 0.25, 10 to 0.20, 15 to 0.20)),
            RowRule(Voice.CLOSED_HAT.ordinal, grids = BUSY_GRIDS, chances = odds(0.25)),
            RowRule(Voice.OPEN_HAT.ordinal, chances = chances(7 to 0.20, 15 to 0.25)),
            RowRule(Voice.RIM.ordinal, chances = chances(2 to 0.15, 9 to 0.15)),
            RowRule(Voice.TOM.ordinal, chances = chances(11 to 0.12, 14 to 0.12)),
            RowRule(ROW_CHORD, listOf(0), chances(8 to 0.40), lateStart = 0.30),
            RowRule(ROW_BASS, listOf(0), chances(3 to 0.40, 6 to 0.40, 10 to 0.40, 13 to 0.30)),
        )

        RhythmStyle.HIPHOP -> listOf(
            RowRule(Voice.KICK.ordinal, listOf(0), chances(3 to 0.30, 6 to 0.50, 10 to 0.45, 11 to 0.25)),
            RowRule(Voice.SNARE.ordinal, listOf(4, 12)),
            RowRule(Voice.CLOSED_HAT.ordinal, grids = STRAIGHT_GRIDS, chances = odds(0.18)),
            RowRule(Voice.OPEN_HAT.ordinal, chances = chances(14 to 0.25)),
            RowRule(Voice.RIM.ordinal, chances = chances(2 to 0.15, 10 to 0.15)),
            RowRule(Voice.CLAP.ordinal, chances = chances(12 to 0.25)),
            RowRule(ROW_CHORD, listOf(0), chances(8 to 0.30, 11 to 0.15), lateStart = 0.40),
            RowRule(ROW_BASS, listOf(0), chances(6 to 0.40, 10 to 0.40, 14 to 0.25)),
        )

        // チップ音源のドラムは 1 発が短いので、詰めて置かないと土台が抜けて聞こえる。
        // 推進力はベースの 8 分連打から来ていて、これがゲーム音楽の芯になる。
        RhythmStyle.CHIP_DRIVE -> listOf(
            RowRule(
                Voice.KICK.ordinal,
                listOf(0, 4, 8, 12),
                chances(2 to 0.25, 6 to 0.30, 10 to 0.25, 14 to 0.30),
            ),
            RowRule(Voice.SNARE.ordinal, listOf(4, 12), chances(7 to 0.20, 15 to 0.25)),
            RowRule(Voice.CLOSED_HAT.ordinal, grids = BUSY_GRIDS, chances = odds(0.20)),
            RowRule(Voice.OPEN_HAT.ordinal, chances = chances(14 to 0.30)),
            RowRule(Voice.RIM.ordinal, chances = chances(2 to 0.12, 10 to 0.12)),
            // 高速アルペジオは打ち直すたびに回り直すので、細かく置くほどきらめく。
            // ここだけは頭を外さない。回りはじめる位置がずれると、
            // チップ音源の「和音が 1 つの音色に聞こえる」効き目が薄れる。
            RowRule(ROW_CHORD, listOf(0, 4, 8, 12), chances(2 to 0.30, 10 to 0.30)),
            RowRule(ROW_BASS, listOf(0, 2, 4, 6, 8, 10, 12, 14)),
        )

        // ハードロック。芯はギターの刻みなので、コードは 8 分で打ち続ける。
        // 1 小節に 1 発だと、どれだけ速くしても「刻んでいる」感じが出ない。
        // ベースも 8 分でルートを押し続ける（動かすと軽くなる）。
        RhythmStyle.HARD_ROCK -> listOf(
            // バスドラの 16 分の連打がハードロックの推進力。2・3 と 10・11 に
            // 寄せてあるのは、スネアの手前で詰まって聞こえるため。
            RowRule(
                Voice.KICK.ordinal,
                listOf(0, 8),
                chances(2 to 0.40, 3 to 0.35, 10 to 0.40, 11 to 0.35, 14 to 0.30),
            ),
            RowRule(Voice.SNARE.ordinal, listOf(4, 12), chances(7 to 0.20, 15 to 0.25)),
            RowRule(
                Voice.CLOSED_HAT.ordinal,
                grids = listOf(EIGHTHS, EIGHTHS, EIGHTHS, SIXTEENTHS),
                chances = odds(0.12),
            ),
            // 小節の頭のオープンハットはシンバル代わり。ここが無いと出だしが立たない。
            RowRule(Voice.OPEN_HAT.ordinal, listOf(0), chances(8 to 0.30, 14 to 0.25)),
            RowRule(Voice.TOM.ordinal, chances = chances(11 to 0.15, 13 to 0.18, 14 to 0.20, 15 to 0.20)),
            RowRule(ROW_CHORD, listOf(0, 4, 8, 12), chances(2 to 0.35, 6 to 0.35, 10 to 0.35, 14 to 0.35)),
            RowRule(ROW_BASS, listOf(0, 2, 4, 6, 8, 10, 12, 14)),
        )

        // 打ち込み。らしさは 16 分で動き続けるベースから出る。
        // ドラムは 4 つ打ちに近い土台にして、コードは裏で刺す。
        // コードを表で鳴らすとベースと団子になって、どちらも前に出ない。
        RhythmStyle.DIGITAL -> listOf(
            RowRule(Voice.KICK.ordinal, listOf(0, 4, 8, 12), chances(7 to 0.25, 15 to 0.30)),
            RowRule(Voice.SNARE.ordinal, listOf(4, 12)),
            RowRule(Voice.CLAP.ordinal, listOf(4, 12), chances(7 to 0.20)),
            RowRule(Voice.CLOSED_HAT.ordinal, grids = BUSY_GRIDS, chances = odds(0.30)),
            RowRule(Voice.OPEN_HAT.ordinal, chances = offbeats(0.25)),
            RowRule(Voice.RIM.ordinal, chances = chances(3 to 0.15, 11 to 0.15)),
            // 裏を厚めに埋める。1 小節に 2〜3 発だと、この速さでは
            // 刺さっているというより、まばらに鳴っているだけに聞こえる。
            RowRule(
                ROW_CHORD,
                listOf(0),
                chances(2 to 0.55, 4 to 0.30, 6 to 0.60, 10 to 0.60, 12 to 0.30, 14 to 0.55),
                lateStart = 0.20,
            ),
            // 8 分を土台に、裏を高い確率で埋める。全部 16 分で固定すると
            // 機械の連打になって、動いている感じがかえって消える。
            RowRule(ROW_BASS, listOf(0, 2, 4, 6, 8, 10, 12, 14), odds(0.55)),
        )

        RhythmStyle.LATIN -> listOf(
            // リムショットはソン・クラーベ（3-2）。ラテンらしさの芯になる。
            RowRule(Voice.RIM.ordinal, listOf(0, 3, 6, 10, 12)),
            RowRule(Voice.KICK.ordinal, listOf(0), chances(3 to 0.30, 8 to 0.60, 11 to 0.40)),
            RowRule(Voice.COWBELL.ordinal, listOf(4, 12), chances(0 to 0.40, 8 to 0.50)),
            RowRule(Voice.CLOSED_HAT.ordinal, grids = listOf(EIGHTHS, EIGHTHS, QUARTERS), chances = odds(0.10)),
            RowRule(Voice.TOM.ordinal, chances = chances(7 to 0.20, 14 to 0.20, 15 to 0.20)),
            RowRule(Voice.SNARE.ordinal, chances = chances(12 to 0.15)),
            RowRule(ROW_CHORD, listOf(0), chances(6 to 0.30, 10 to 0.30), lateStart = 0.22),
            RowRule(ROW_BASS, listOf(0), chances(3 to 0.50, 8 to 0.50, 11 to 0.40)),
        )
    }

    private fun chances(vararg pairs: Pair<Int, Double>): DoubleArray {
        val values = DoubleArray(STEPS_PER_BAR)
        for ((step, chance) in pairs) values[step] = chance
        return values
    }

    /** 16 分の裏（奇数ステップ）だけを [chance] の確率にする。刻みの飾り用。 */
    private fun odds(chance: Double): DoubleArray =
        DoubleArray(STEPS_PER_BAR) { if (it % 2 == 1) chance else 0.0 }

    /** 8 分の裏（2, 6, 10, 14）を [chance] の確率にする。 */
    private fun offbeats(chance: Double): DoubleArray =
        DoubleArray(STEPS_PER_BAR) { if (it % 4 == 2) chance else 0.0 }
}
