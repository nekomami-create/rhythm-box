package com.example.rhythmbox.core

import kotlin.random.Random

/**
 * 出来上がったコードの並びに味付けをする。
 * 進行の型（[ProgressionTemplate]）が骨で、こちらが肉。
 */
object Harmony {

    /** 解ける場所を見つけたとき、実際に sus4 にする確率。 */
    const val SUS4_CHANCE = 0.4

    /** V（属和音）の位置。ここだけ 7th の付け方が違う。 */
    private const val DOMINANT_DEGREE = 4

    /**
     * 度数ごとの「7th を足して良さそうな度合い」。[enrichSevenths] の確率に掛ける。
     *
     * V は 7th を足すと導音と第 7 音が同時に鳴って、主和音へ落ちる力が強くなる。
     * いちばん足す価値がある。逆に I は IM7 にすると終わった感じが薄れて浮くので、
     * 半分に落としてある。終止に使う和音を毎回 M7 にすると曲が着地しなくなる。
     */
    private val DEGREE_WEIGHT = doubleArrayOf(0.5, 1.0, 0.7, 1.0, 1.2, 1.0, 1.0)

    /**
     * 三和音のいくつかを 7th にする。
     *
     * 元の性格は変えない。長三和音は M7、短三和音は m7、減三和音は m7-5。
     * ただし V だけはドミナント 7th（短 7 度）にする。ここを M7 にすると
     * 属和音の緊張が消えて、進行が前へ進まなくなる。度数ごとの違いは
     * ここだけなので、V を見分けられれば残りは機械的に決まる。
     *
     * すでに色の付いた和音（7th、sus、9th など）には触らない。
     * 進行の型が「ここはこの響きで」と決めたものを上書きしないため。
     */
    fun enrichSevenths(
        chords: List<Chord>,
        key: MusicKey,
        chance: Double,
        random: Random,
    ): List<Chord> {
        if (chance <= 0.0) return chords
        val diatonic = key.diatonicChords()
        return chords.map { chord ->
            // 調の中の何番目か。調の外の和音（セカンダリードミナントなど）は
            // 見つからないので触らない。あれはもう 7th になっている。
            val degree = diatonic.indexOfFirst { it.root == chord.root && it.quality == chord.quality }
            if (degree < 0) return@map chord
            val seventh = seventhFor(degree, chord.quality) ?: return@map chord
            val weight = DEGREE_WEIGHT.getOrElse(degree) { 1.0 }
            if (random.nextDouble() >= (chance * weight).coerceAtMost(1.0)) return@map chord
            chord.copy(quality = seventh)
        }
    }

    /** その度数・その三和音に足すべき 7th。三和音でなければ null。 */
    fun seventhFor(degree: Int, quality: ChordQuality): ChordQuality? = when (quality) {
        ChordQuality.MAJOR ->
            if (degree == DOMINANT_DEGREE) ChordQuality.SEVENTH else ChordQuality.MAJOR_SEVENTH
        ChordQuality.MINOR -> ChordQuality.MINOR_SEVENTH
        ChordQuality.DIMINISHED -> ChordQuality.HALF_DIMINISHED
        else -> null
    }

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
