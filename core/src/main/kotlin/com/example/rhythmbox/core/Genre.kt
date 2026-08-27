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

        /**
         * 丸サ進行 IVM7 - III7 - vi7 - vi7。シティポップの手触り。
         * III7 は vi へ向かうセカンダリードミナント（V/vi）で、
         * ここが調の外へ一瞬だけ出る。この型の色はほぼこれで決まる。
         *
         * 長調の型なので音階を持たせてある。持たせないと、短調の曲に
         * 当てたときに DM7 - C7 - Fm7 のような別物になる（種類だけ
         * 強制して、土台の音階は曲のまま解決してしまうため）。
         */
        val CITY = ProgressionTemplate(
            "丸サ進行",
            listOf(3, 2, 5, 5),
            listOf(
                ChordQuality.MAJOR_SEVENTH,
                ChordQuality.SEVENTH,
                ChordQuality.MINOR_SEVENTH,
                ChordQuality.MINOR_SEVENTH,
            ),
            scale = Scale.MAJOR,
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

        /**
         * I - ♭VII - IV - I。ミクソリディアンなので長調のまま ♭VII が入る。
         * 明るいのにどこか素朴で、街や村の音。
         */
        val GAME_TOWN = ProgressionTemplate(
            "I-♭VII-IV-I",
            listOf(0, 6, 3, 0),
            scale = Scale.MIXOLYDIAN,
        )

        /**
         * i - ♭II - ♭VII - i。フリジアンの ♭II が独特の緊張を作る。
         * 洞窟や地下の、落ち着かない感じ。
         */
        val GAME_CAVERN = ProgressionTemplate(
            "i-♭II-♭VII-i",
            listOf(0, 1, 6, 0),
            scale = Scale.PHRYGIAN,
        )

        /**
         * ii7 - V7 - IM7。ジャズ寄りの落ち着いた響き。
         * 丸サ進行と同じ理由で長調の型として持たせる。持たせないと
         * 短調の曲で主和音が長三和音（AM7）になってしまう。
         */
        val TWO_FIVE_ONE = ProgressionTemplate(
            "ii-V-I",
            listOf(1, 4, 0, 0),
            listOf(
                ChordQuality.MINOR_SEVENTH,
                ChordQuality.SEVENTH,
                ChordQuality.MAJOR_SEVENTH,
                ChordQuality.MAJOR_SEVENTH,
            ),
            scale = Scale.MAJOR,
        )

        /**
         * 短調のツーファイブワン iim7-5 - V7 - i。
         *
         * v は本来 短三和音だが、ここだけ長三和音（V7）にすると
         * 導音が立って、主和音への戻りがはっきりする。和声的短音階の
         * 使い方そのもの。短調のバラードやボス戦の芯になる。
         */
        val MINOR_TWO_FIVE = ProgressionTemplate(
            "短調のii-V-i",
            listOf(1, 4, 0, 0),
            listOf(
                ChordQuality.HALF_DIMINISHED,
                ChordQuality.SEVENTH,
                ChordQuality.MINOR,
                ChordQuality.MINOR,
            ),
            scale = Scale.NATURAL_MINOR,
        )

        /**
         * 循環進行 I - VI7 - ii7 - V7。
         *
         * VI7 は ii へ向かうセカンダリードミナント（V/ii）。本来の vi は
         * 短三和音なので、長三和音にした瞬間だけ調の外へ出て、次の ii へ
         * 強く落ちる。4 小節で頭に戻るので、いくらでも回せる。
         */
        val TURNAROUND = ProgressionTemplate(
            "循環進行",
            listOf(0, 5, 1, 4),
            listOf(
                null,
                ChordQuality.SEVENTH,
                ChordQuality.MINOR_SEVENTH,
                ChordQuality.SEVENTH,
            ),
            scale = Scale.MAJOR,
        )

        /**
         * I - IV - II7 - V。II7 は V へ向かうセカンダリードミナント（V/V）。
         *
         * ii が長三和音になって、そのまま V へ落ちる。ロックやブルースで
         * 昔から使われている形で、7th を重ねなくても効く。
         */
        val DOUBLE_DOMINANT = ProgressionTemplate(
            "I-IV-II7-V",
            listOf(0, 3, 1, 4),
            listOf(null, null, ChordQuality.SEVENTH, null),
            scale = Scale.MAJOR,
        )
    }
}

/** 旋律の詰め込み具合。 */
enum class MelodyDensity { SPARSE, NORMAL, BUSY }

/**
 * 当てはめる中身。ジャンルそのものからも、ゲームの場面からも作れる。
 *
 * ジャンルと場面で同じ形にしておくと、当てはめる側はどちらから来たかを
 * 気にせず済む。
 */
data class GenreRecipe(
    val bpmRange: IntRange,
    val rhythms: List<RhythmStyle>,
    val progressions: List<ProgressionTemplate>,
    val melodyDensity: MelodyDensity,
    /** チップ音源で鳴らすか。 */
    val chip: Boolean,
    /** チップで鳴らすときのリードの音色。 */
    val leadVoice: ToneSynth.LeadVoice = ToneSynth.LeadVoice.PULSE_25,
) {
    fun pickBpm(random: Random = Random.Default): Int =
        bpmRange.first + random.nextInt(bpmRange.last - bpmRange.first + 1)

    fun pickRhythm(random: Random = Random.Default): RhythmStyle = rhythms.random(random)

    fun pickProgression(random: Random = Random.Default): ProgressionTemplate =
        progressions.random(random)
}

/**
 * ゲーム音楽の場面。
 *
 * ゲーム音楽は 1 つの型ではなく、場面ごとに速さも明暗もまるで違う。
 * ジャンルを 5 つに割るとほかのジャンルと並びが釣り合わないので、
 * 「ゲーム音楽」の中の選択肢として持たせている。
 */
enum class GameScene(
    val label: String,
    val description: String,
    private val bpmRange: IntRange,
    private val progressions: List<ProgressionTemplate>,
    private val melodyDensity: MelodyDensity,
    private val leadVoice: ToneSynth.LeadVoice,
) {
    TITLE(
        "タイトル",
        "堂々と。長調で開ける",
        104..124,
        listOf(ProgressionTemplate.CANON, ProgressionTemplate.POP_PUNK),
        MelodyDensity.NORMAL,
        // 丸い矩形波。飾らずに旋律を出す。
        ToneSynth.LeadVoice.PULSE_50,
    ),
    FIELD(
        "フィールド",
        "速め。歩き続ける進行",
        142..162,
        listOf(ProgressionTemplate.GAME_FIELD, ProgressionTemplate.GAME_QUEST),
        MelodyDensity.BUSY,
        ToneSynth.LeadVoice.PULSE_25,
    ),
    TOWN(
        "街",
        "のんびり。明るいのに素朴",
        108..126,
        listOf(ProgressionTemplate.GAME_TOWN, ProgressionTemplate.FIFTIES),
        MelodyDensity.NORMAL,
        // いちばん細いパルス。素朴で軽い音になる。
        ToneSynth.LeadVoice.PULSE_12,
    ),
    DUNGEON(
        "ダンジョン",
        "遅め。暗く落ちていく",
        92..112,
        listOf(ProgressionTemplate.GAME_DUNGEON, ProgressionTemplate.GAME_CAVERN),
        MelodyDensity.SPARSE,
        ToneSynth.LeadVoice.PULSE_50,
    ),
    BOSS(
        "ボス戦",
        "かなり速い。緊張した進行",
        168..186,
        listOf(
            ProgressionTemplate.GAME_BOSS,
            ProgressionTemplate.GAME_CAVERN,
            // 導音が立つので、戦闘曲の張り詰めた感じが出る。
            ProgressionTemplate.MINOR_TWO_FIVE,
        ),
        MelodyDensity.BUSY,
        ToneSynth.LeadVoice.PULSE_25,
    ),
    ;

    fun recipe(): GenreRecipe = GenreRecipe(
        bpmRange = bpmRange,
        rhythms = listOf(RhythmStyle.CHIP_DRIVE),
        progressions = progressions,
        melodyDensity = melodyDensity,
        chip = true,
        leadVoice = leadVoice,
    )
}

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
    /**
     * 場面の選択肢。空でなければ、当てはめる前にどれかを選ぶ。
     * ゲーム音楽は場面ごとに速さも明暗もまるで違うので、1 つには畳めない。
     */
    val scenes: List<GameScene> = emptyList(),
) {
    ROCK(
        "ロック",
        "8ビート・速め。I-V-vi-IV 系",
        132..152,
        listOf(RhythmStyle.EIGHT_BEAT),
        listOf(
            ProgressionTemplate.POP_PUNK,
            ProgressionTemplate.KOMURO,
            // II7 から V へ落ちる形は、ロックでは 7th を重ねなくても効く。
            ProgressionTemplate.DOUBLE_DOMINANT,
        ),
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
            ProgressionTemplate.TURNAROUND,
        ),
        MelodyDensity.NORMAL,
    ),
    BALLAD(
        "バラード",
        "ゆっくり・隙間を空ける",
        62..80,
        listOf(RhythmStyle.EIGHT_BEAT),
        listOf(
            ProgressionTemplate.FIFTIES,
            ProgressionTemplate.CANON,
            // 短調のツーファイブ。ゆっくりだと導音の効き目がよく見える。
            ProgressionTemplate.MINOR_TWO_FIVE,
        ),
        MelodyDensity.SPARSE,
    ),
    CITY_POP(
        "シティポップ",
        "16 分の細かい刻み・M7 や m7",
        96..116,
        listOf(RhythmStyle.BREAKBEAT, RhythmStyle.HIPHOP),
        listOf(
            ProgressionTemplate.CITY,
            ProgressionTemplate.TWO_FIVE_ONE,
            ProgressionTemplate.TURNAROUND,
        ),
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
    GAME(
        "ゲーム音楽",
        "チップ音源。場面を選べます",
        92..186,
        listOf(RhythmStyle.CHIP_DRIVE),
        listOf(ProgressionTemplate.GAME_FIELD, ProgressionTemplate.GAME_QUEST),
        MelodyDensity.BUSY,
        chip = true,
        scenes = GameScene.entries,
    );

    /** 場面を選ばなかったときの中身。 */
    fun recipe(): GenreRecipe = GenreRecipe(
        bpmRange = bpmRange,
        rhythms = rhythms,
        progressions = progressions,
        melodyDensity = melodyDensity,
        chip = chip,
    )

    fun pickBpm(random: Random = Random.Default): Int =
        bpmRange.first + random.nextInt(bpmRange.last - bpmRange.first + 1)

    fun pickRhythm(random: Random = Random.Default): RhythmStyle = rhythms.random(random)

    fun pickProgression(random: Random = Random.Default): ProgressionTemplate =
        progressions.random(random)
}
