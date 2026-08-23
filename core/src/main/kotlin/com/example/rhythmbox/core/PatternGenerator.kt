package com.example.rhythmbox.core

import kotlin.random.Random

/** 自動生成するリズムの種類。 */
enum class RhythmStyle(val label: String) {
    EIGHT_BEAT("8ビート"),
    FOUR_ON_FLOOR("4つ打ち"),
    BREAKBEAT("ブレイクビーツ"),
    HIPHOP("ヒップホップ"),
    LATIN("ラテン"),
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
            rows[rule.row] = bits and Pattern.STEP_MASK
        }
        return Pattern(name, rows)
    }

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
            RowRule(ROW_CHORD, listOf(0), chances(4 to 0.20, 8 to 0.50, 12 to 0.20)),
            RowRule(ROW_BASS, listOf(0), chances(3 to 0.30, 6 to 0.35, 8 to 0.70, 10 to 0.30, 14 to 0.30)),
        )

        RhythmStyle.FOUR_ON_FLOOR -> listOf(
            RowRule(Voice.KICK.ordinal, listOf(0, 4, 8, 12)),
            RowRule(Voice.SNARE.ordinal, chances = chances(4 to 0.15, 12 to 0.15)),
            RowRule(Voice.CLOSED_HAT.ordinal, grids = DANCE_GRIDS, chances = odds(0.08)),
            RowRule(Voice.OPEN_HAT.ordinal, chances = offbeats(0.22)),
            RowRule(Voice.CLAP.ordinal, listOf(4, 12)),
            RowRule(Voice.COWBELL.ordinal, chances = chances(7 to 0.10, 15 to 0.10)),
            RowRule(ROW_CHORD, listOf(0), chances(4 to 0.30, 8 to 0.50, 12 to 0.30)),
            RowRule(ROW_BASS, listOf(0, 8), offbeats(0.55)),
        )

        RhythmStyle.BREAKBEAT -> listOf(
            RowRule(Voice.KICK.ordinal, listOf(0), chances(3 to 0.50, 6 to 0.45, 10 to 0.55, 11 to 0.30, 14 to 0.20)),
            RowRule(Voice.SNARE.ordinal, listOf(4, 12), chances(7 to 0.25, 10 to 0.20, 15 to 0.20)),
            RowRule(Voice.CLOSED_HAT.ordinal, grids = BUSY_GRIDS, chances = odds(0.25)),
            RowRule(Voice.OPEN_HAT.ordinal, chances = chances(7 to 0.20, 15 to 0.25)),
            RowRule(Voice.RIM.ordinal, chances = chances(2 to 0.15, 9 to 0.15)),
            RowRule(Voice.TOM.ordinal, chances = chances(11 to 0.12, 14 to 0.12)),
            RowRule(ROW_CHORD, listOf(0), chances(8 to 0.40)),
            RowRule(ROW_BASS, listOf(0), chances(3 to 0.40, 6 to 0.40, 10 to 0.40, 13 to 0.30)),
        )

        RhythmStyle.HIPHOP -> listOf(
            RowRule(Voice.KICK.ordinal, listOf(0), chances(3 to 0.30, 6 to 0.50, 10 to 0.45, 11 to 0.25)),
            RowRule(Voice.SNARE.ordinal, listOf(4, 12)),
            RowRule(Voice.CLOSED_HAT.ordinal, grids = STRAIGHT_GRIDS, chances = odds(0.18)),
            RowRule(Voice.OPEN_HAT.ordinal, chances = chances(14 to 0.25)),
            RowRule(Voice.RIM.ordinal, chances = chances(2 to 0.15, 10 to 0.15)),
            RowRule(Voice.CLAP.ordinal, chances = chances(12 to 0.25)),
            RowRule(ROW_CHORD, listOf(0), chances(8 to 0.30, 11 to 0.15)),
            RowRule(ROW_BASS, listOf(0), chances(6 to 0.40, 10 to 0.40, 14 to 0.25)),
        )

        RhythmStyle.LATIN -> listOf(
            // リムショットはソン・クラーベ（3-2）。ラテンらしさの芯になる。
            RowRule(Voice.RIM.ordinal, listOf(0, 3, 6, 10, 12)),
            RowRule(Voice.KICK.ordinal, listOf(0), chances(3 to 0.30, 8 to 0.60, 11 to 0.40)),
            RowRule(Voice.COWBELL.ordinal, listOf(4, 12), chances(0 to 0.40, 8 to 0.50)),
            RowRule(Voice.CLOSED_HAT.ordinal, grids = listOf(EIGHTHS, EIGHTHS, QUARTERS), chances = odds(0.10)),
            RowRule(Voice.TOM.ordinal, chances = chances(7 to 0.20, 14 to 0.20, 15 to 0.20)),
            RowRule(Voice.SNARE.ordinal, chances = chances(12 to 0.15)),
            RowRule(ROW_CHORD, listOf(0), chances(6 to 0.30, 10 to 0.30)),
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
