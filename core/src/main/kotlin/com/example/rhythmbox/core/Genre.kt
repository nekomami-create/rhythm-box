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
    /**
     * この型が前提にしている音階。null なら曲の調のまま解決する。
     *
     * 度数は音階の中の位置なので、前提が違うと同じ番号でも別の和音になる。
     * たとえば 3 度は、ドリアンでは長三和音の IV、ナチュラルマイナーでは
     * 短三和音の iv。型ごとに持たせないと、静かに違う響きになってしまう。
     */
    val scale: Scale? = null,
) {
    /** [key] での実際の和音。この型が音階を決めているなら、そちらで解決する。 */
    fun chords(key: MusicKey): List<Chord> {
        val diatonic = keyFor(key).diatonicChords()
        return degrees.mapIndexed { index, degree ->
            val chord = diatonic[degree.coerceIn(diatonic.indices)]
            val quality = qualities.getOrNull(index)
            if (quality != null) chord.copy(quality = quality) else chord
        }
    }

    /** この型を当てるときの調。主音は曲のまま、音階だけ型に合わせる。 */
    fun keyFor(key: MusicKey): MusicKey = if (scale == null) key else MusicKey(key.tonic, scale)

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

        // ゲーム音楽の進行。♭III / ♭VI / ♭VII はナチュラルマイナーの中の和音なので、
        // 調外のものを持ち込む必要はない。音階を切り替えるだけで出てくる。

        /** i - ♭VII - ♭VI - ♭VII。フィールドや街の、歩き続ける感じ。 */
        val GAME_FIELD = ProgressionTemplate(
            "i-♭VII-♭VI-♭VII",
            listOf(0, 6, 5, 6),
            scale = Scale.NATURAL_MINOR,
        )

        /** ♭VI - ♭VII - i。上がって着地する、戦闘曲の定番。 */
        val GAME_BOSS = ProgressionTemplate(
            "♭VI-♭VII-i",
            listOf(5, 6, 0, 0),
            scale = Scale.NATURAL_MINOR,
        )

        /** i - ♭VI - ♭III - ♭VII。落ちていく感じ。洞窟や夜。 */
        val GAME_DUNGEON = ProgressionTemplate(
            "i-♭VI-♭III-♭VII",
            listOf(0, 5, 2, 6),
            scale = Scale.NATURAL_MINOR,
        )

        /**
         * i - ♭III - IV - ♭VII。ドリアンなので 4 度が長三和音になり、
         * 暗いのに開けて聞こえる。冒険の出だしの音。
         */
        val GAME_QUEST = ProgressionTemplate(
            "i-♭III-IV-♭VII",
            listOf(0, 2, 3, 6),
            scale = Scale.DORIAN,
        )

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
    /**
     * チップ音源で鳴らすジャンルか。
     * 立てておくと、当てたときに音色とドラムもチップのものに切り替わる。
     */
    val chip: Boolean = false,
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
    ),
    GAME_FIELD(
        "ゲーム（フィールド）",
        "チップ音源・速め。歩き続ける進行",
        142..162,
        listOf(RhythmStyle.CHIP_DRIVE),
        listOf(ProgressionTemplate.GAME_FIELD, ProgressionTemplate.GAME_QUEST),
        MelodyDensity.BUSY,
        chip = true,
    ),
    GAME_BOSS(
        "ゲーム（ボス）",
        "チップ音源・かなり速い。緊張した進行",
        168..186,
        listOf(RhythmStyle.CHIP_DRIVE),
        listOf(ProgressionTemplate.GAME_BOSS, ProgressionTemplate.GAME_DUNGEON),
        MelodyDensity.BUSY,
        chip = true,
    ),
    GAME_TITLE(
        "ゲーム（タイトル）",
        "チップ音源・堂々と。長調で開ける",
        104..124,
        listOf(RhythmStyle.CHIP_DRIVE),
        listOf(ProgressionTemplate.CANON, ProgressionTemplate.POP_PUNK),
        MelodyDensity.NORMAL,
        chip = true,
    );

    fun pickBpm(random: Random = Random.Default): Int =
        bpmRange.first + random.nextInt(bpmRange.last - bpmRange.first + 1)

    fun pickRhythm(random: Random = Random.Default): RhythmStyle = rhythms.random(random)

    fun pickProgression(random: Random = Random.Default): ProgressionTemplate =
        progressions.random(random)
}
