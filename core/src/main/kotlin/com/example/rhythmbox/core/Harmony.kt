package com.example.rhythmbox.core

import kotlin.random.Random

/**
 * 出来上がったコードの並びに味付けをする。
 * 進行の型（[ProgressionTemplate]）が骨で、こちらが肉。
 */
object Harmony {

    /** 解ける場所を見つけたとき、実際に sus4 にする確率。 */
    const val SUS4_CHANCE = 0.4

    /**
     * ところどころを sus4 にする。
     *
     * 置くのは「次の小節が同じコードのところ」だけ。sus4 は 3 度を 4 度に
     * 預けた宙吊りの響きで、**解けてはじめて sus4 に聞こえる**。次が別の
     * コードだと預けた音が行き先を失って、ただの違うコードになってしまう。
     * 同じコードが 2 小節続くところに置けば、次の小節で自然に解ける。
     *
     * 進行の型には同じコードが 2 小節続く箇所がよくある（丸サ進行の vi7、
     * ツーファイブワンの I など）ので、置き場所には困らない。
     */
    fun sprinkleSus4(
        chords: List<Chord>,
        random: Random,
        chance: Double = SUS4_CHANCE,
    ): List<Chord> {
        if (chords.size < 2) return chords
        val result = chords.toMutableList()
        var previousChanged = false
        // 先頭は置き換えない。パターン単体で試聴したときの響きになるので、
        // そこが宙吊りだと調が分からないまま始まってしまう。
        for (index in 1 until chords.size - 1) {
            if (previousChanged) {
                previousChanged = false
                continue
            }
            if (chords[index] != chords[index + 1]) continue
            val suspended = suspendedOf(chords[index].quality) ?: continue
            if (random.nextDouble() >= chance) continue
            result[index] = chords[index].copy(quality = suspended)
            previousChanged = true
        }
        return result
    }

    /**
     * その和音の sus4 版。3 度を持たない和音（すでに sus、減/ 増、9th など）は
     * そのままにする。sus は 3 度を預ける技なので、預ける 3 度が無いと意味がない。
     */
    fun suspendedOf(quality: ChordQuality): ChordQuality? = when (quality) {
        // 長短どちらの 3 度も 4 度に置き換わるので、行き先は同じ。
        ChordQuality.MAJOR, ChordQuality.MINOR -> ChordQuality.SUS4
        ChordQuality.SEVENTH, ChordQuality.MINOR_SEVENTH -> ChordQuality.SEVENTH_SUS4
        else -> null
    }
}
