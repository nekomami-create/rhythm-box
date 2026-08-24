package com.example.rhythmbox.core

import kotlin.random.Random

/**
 * よく使われるコード進行の型。
 *
 * [degrees] は調の中での度数（0 = I）。[qualities] を指定した位置だけ、
 * 調のままの和音ではなくその種類に置き換える（丸サ進行の III7 など）。
 */
data class ProgressionTemplate(
    val name: String,
    val degrees: List<Int>,
    val qualities: List<ChordQuality?> = emptyList(),
) {
    /** [key] での実際の和音。 */
    fun chords(key: MusicKey): List<Chord> {
        val diatonic = key.diatonicChords()
        return degrees.mapIndexed { index, degree ->
            val chord = diatonic[degree.coerceIn(diatonic.indices)]
            val quality = qualities.getOrNull(index)
            if (quality != null) chord.copy(quality = quality) else chord
        }
    }

    /** [bars] 小節ぶんに敷き詰める（足りなければ繰り返し、多ければ切る）。 */
    fun fill(key: MusicKey, bars: Int): List<Chord> {
        if (bars <= 0) return emptyList()
        val base = chords(key)
        return List(bars) { base[it % base.size] }
    }

    companion object {
        /** I - V - vi - IV。ロックやポップスの定番。 */
        val POP_PUNK = ProgressionTemplate("I-V-vi-IV", listOf(0, 4, 5, 3))

        /** 王道進行 IV - V - iii - vi。J-POP でいちばんよく聞く形。 */
        val ROYAL_ROAD = ProgressionTemplate("王道進行", listOf(3, 4, 2, 5))

        /** 小室進行 vi - IV - V - I。 */
        val KOMURO = ProgressionTemplate("小室進行", listOf(5, 3, 4, 0))

        /** カノン進行。8 小節で 1 周する。 */
        val CANON = ProgressionTemplate("カノン進行", listOf(0, 4, 5, 2, 3, 0, 3, 4))

        /** 50 年代進行 I - vi - IV - V。バラードに合う。 */
        val FIFTIES = ProgressionTemplate("I-vi-IV-V", listOf(0, 5, 3, 4))

        /** 丸サ進行 IVM7 - III7 - vi7 - vi7。シティポップの手触り。 */
        val CITY = ProgressionTemplate(
            "丸サ進行",
            listOf(3, 2, 5, 5),
            listOf(
                ChordQuality.MAJOR_SEVENTH,
                ChordQuality.SEVENTH,
                ChordQuality.MINOR_SEVENTH,
                ChordQuality.MINOR_SEVENTH,
            ),
        )

        /** vi - IV - I - V。ダンス系でよく回す形。 */
        val DANCE_LOOP = ProgressionTemplate("vi-IV-I-V", listOf(5, 3, 0, 4))

        /** ii7 - V7 - IM7。ジャズ寄りの落ち着いた響き。 */
        val TWO_FIVE_ONE = ProgressionTemplate(
            "ii-V-I",
            listOf(1, 4, 0, 0),
            listOf(
                ChordQuality.MINOR_SEVENTH,
                ChordQuality.SEVENTH,
                ChordQuality.MAJOR_SEVENTH,
                ChordQuality.MAJOR_SEVENTH,
            ),
        )
    }
}

/** 旋律の詰め込み具合。 */
enum class MelodyDensity { SPARSE, NORMAL, BUSY }

/**
 * ジャンルのプリセット。
 *
 * ジャンルらしさはドラムだけでは出ないので、テンポ・リズムの型・
 * コード進行・旋律の密度をまとめて持たせている。
 */
enum class Genre(
    val label: String,
    val description: String,
    val bpmRange: IntRange,
    val rhythms: List<RhythmStyle>,
    val progressions: List<ProgressionTemplate>,
    val melodyDensity: MelodyDensity,
) {
    ROCK(
        "ロック",
        "8ビート・速め。I-V-vi-IV 系",
        132..152,
        listOf(RhythmStyle.EIGHT_BEAT),
        listOf(ProgressionTemplate.POP_PUNK, ProgressionTemplate.KOMURO),
        MelodyDensity.NORMAL,
    ),
    JPOP(
        "J-POP",
        "王道進行・小室進行・カノン進行",
        118..138,
        listOf(RhythmStyle.EIGHT_BEAT),
        listOf(
            ProgressionTemplate.ROYAL_ROAD,
            ProgressionTemplate.KOMURO,
            ProgressionTemplate.CANON,
        ),
        MelodyDensity.NORMAL,
    ),
    BALLAD(
        "バラード",
        "ゆっくり・隙間を空ける",
        62..80,
        listOf(RhythmStyle.EIGHT_BEAT),
        listOf(ProgressionTemplate.FIFTIES, ProgressionTemplate.CANON),
        MelodyDensity.SPARSE,
    ),
    CITY_POP(
        "シティポップ",
        "16 分の細かい刻み・M7 や m7",
        96..116,
        listOf(RhythmStyle.BREAKBEAT, RhythmStyle.HIPHOP),
        listOf(ProgressionTemplate.CITY, ProgressionTemplate.TWO_FIVE_ONE),
        MelodyDensity.BUSY,
    ),
    DANCE(
        "ダンス",
        "4 つ打ち",
        122..130,
        listOf(RhythmStyle.FOUR_ON_FLOOR),
        listOf(ProgressionTemplate.DANCE_LOOP, ProgressionTemplate.KOMURO),
        MelodyDensity.NORMAL,
    );

    fun pickBpm(random: Random = Random.Default): Int =
        bpmRange.first + random.nextInt(bpmRange.last - bpmRange.first + 1)

    fun pickRhythm(random: Random = Random.Default): RhythmStyle = rhythms.random(random)

    fun pickProgression(random: Random = Random.Default): ProgressionTemplate =
        progressions.random(random)
}
