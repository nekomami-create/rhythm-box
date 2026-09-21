package com.example.rhythmbox.core

import kotlin.random.Random

/**
 * 鑑賞モード。終わりなく新しい小節を作り続けて、聴くだけの画面に流す。
 *
 * [SongBuilder] は決まった長さの曲をまとめて組むが、ここは逆に、あらかじめ
 * 長さを決めない。再生が先へ進むたびに [Stream.grow] を呼び、続きを
 * 1 ブロックずつ足していく。
 *
 * リズム・コード進行・リードが食い違わないのは、1 ブロックの中身をぜんぶ
 * 同じ材料（[Era.key]・[Era.recipe] が選んだ進行の型・同じ乱数の流れ）から
 * 組むため。SongBuilder が 1 曲ぶんでやっていることを、ブロック単位で
 * 際限なく繰り返しているだけで、別の理屈は使っていない。
 *
 * ただしブロックごとに進行をまるごと引き直すと、コード進行そのものが
 * 毎回よそへ飛んでしまう。4 ブロック（16 小節）を起承転結の 1 組として、
 * 転だけ別の進行に変え、結で起の進行へ戻す（[Stream.grow] を参照）。
 *
 * 場面（調・テンポ）は一定のブロック数ごとに移り変わる。ずっと同じ場面の
 * ままだと単調になるが、毎ブロック変えると逆に「曲」として聴けなくなる
 * ので、数十秒〜数分単位の「場面」で区切る。切り替わるのは句（起承転結）
 * の区切りだけで、しかもそこは必ず V7 → I の終止に着地させてから次の
 * 場面へ渡す。句の途中でいきなり別ジャンルへ飛ぶと、曲が終止しないまま
 * 断ち切られたように聞こえるため。
 *
 * ジャンルだけは、始まってからは変わらない（最初に選んだ・おまかせで
 * 引いたジャンルのまま）。テンポは [Stream.setBpm] で聴きながら自分で動かせる。
 */
object Appreciation {

    /** 1 ブロックの小節数。[SongBuilder] と揃えてある。 */
    const val BLOCK = SongBuilder.BLOCK

    /**
     * 起承転結、1 まとまりのブロック数。[BLOCK] と掛けて 16 小節。
     *
     * ブロックごとに進行をまるごと引き直すと、コードが毎回よそへ飛んで
     * リードもそれに引きずられ、「曲」ではなく断片の連続に聞こえていた。
     * 4 ブロックを 1 つの起承転結として、起（0）・承（1）は同じ進行、
     * 転（2）だけ別の進行に変えて、結（3）で起の進行へ戻す。
     */
    private const val PHRASE_BLOCKS = 4

    /** 起承転結のうち「転」に当たるブロックの位置（0 始まり）。 */
    private const val TURN_BLOCK = 2

    /** 起承転結のうち「結」に当たるブロックの位置（0 始まり）。 */
    private const val CODA_BLOCK = PHRASE_BLOCKS - 1

    /** 終止に使う和音の度数。V（ドミナント）。 */
    private const val CADENCE_DOMINANT_DEGREE = 4

    /**
     * 小節の後半で、次の和音を先取りする確率。
     *
     * 鑑賞モードは 1 小節に 1 和音が続くと、進行がずっと同じ速さでしか
     * 動かず単調になる。ここだけ（打ち込みでの手作業とは別に）ときどき
     * 後半だけ次の小節の和音を先取りして、和音の動きに緩急を付ける。
     */
    private const val MID_BAR_CHANCE = 0.3

    /**
     * ブロック最後の小節も、後半だけ差し替える確率。
     *
     * こちらは他の 3 小節（[MID_BAR_CHANCE]）より控えめにしてある。次の
     * ブロックの頭の和音は乱数の並びを崩さずには先読みできないので、
     * 差し替え先はそのブロック最初の和音（[withMidBarMovement] 参照）。
     * 毎回だと不自然なので、頻度を落として時々だけにしている。
     */
    private const val LAST_BAR_WRAP_CHANCE = 0.1

    /**
     * 転で、近い調へ寄り道する確率。
     *
     * 起・承・結は場面の調のまま、転だけこの確率で属調・下属調・平行調の
     * いずれかへ実際に転調する。結では必ず元の進行（[Era.mainProgression]）・
     * 元の調に戻るので、転はあくまで一時的な寄り道になる。
     */
    private const val MODULATION_CHANCE = 0.4

    /**
     * 場面が移り変わるまでのブロック数の範囲。
     *
     * 常に同じ数だと切り替わりが規則的に聞こえてしまうので、幅を持たせて
     * ブロックごとに引き直す。1 ブロックはジャンルのテンポで数秒〜十数秒
     * なので、この範囲でだいたい 2〜5 分ごとの場面転換になる。
     */
    private val DRIFT_BLOCKS = 16..28

    /** 鳴らせる調の候補。ロクリアンやブルースのような癖の強い音階は外してある。 */
    private val KEY_SCALES = listOf(Scale.MAJOR, Scale.NATURAL_MINOR, Scale.DORIAN, Scale.MIXOLYDIAN)

    /**
     * 今どんな場面を流しているかの読み取り専用スナップショット。
     * 画面表示だけでなく、音色（チップ音源かどうか・リードの音色など）を
     * 組み立て直すのにも使う。[recipe] は場面転換のたびに中で選び直した、
     * その場面そのものの中身（呼び直すと別のものが返る値ではない）。
     */
    data class Status(
        val genre: Genre,
        val key: MusicKey,
        val bpm: Int,
        val blocksIntoEra: Int,
        val recipe: GenreRecipe,
    )

    /** 場面ひとつぶんの、変わらない設定。 */
    private class Era(
        val genre: Genre,
        val key: MusicKey,
        val recipe: GenreRecipe,
        val bpm: Int,
        /** 次の場面まで、あと何ブロックか。0 になったら引き直す。 */
        var blocksLeft: Int,
        /** 起・承・結で使う進行。 */
        val mainProgression: ProgressionTemplate,
        /** 転だけで使う、対になる進行。 */
        val turnProgression: ProgressionTemplate,
    ) {
        var blocksPlayed: Int = 0
    }

    /**
     * 鑑賞の続きを持ち続ける器。[grow] を呼ぶたびに、鳴らせる範囲が
     * [BLOCK] 小節ぶん伸びる。
     *
     * @param genre 開始時のジャンル。null なら最初からおまかせ。
     * @param key 開始時の調。null なら最初からおまかせ。
     * @param scene 開始時の場面（ゲーム音楽のみ）。genre が場面を持つのに
     *   渡さなければ、そこだけおまかせで引く。以降の場面転換では毎回引き直す。
     */
    class Stream(
        genre: Genre?,
        key: MusicKey?,
        scene: GameScene? = null,
        private val random: Random = Random.Default,
    ) {
        private var era: Era = newEra(genre, key, scene)
        private var previousLead: List<Int>? = null

        /**
         * 聴きながら自分で決めたテンポ。null ならジャンルのテンポ帯から
         * おまかせで決まる（[Era.bpm]）。一度決めたら、場面が変わっても
         * （ジャンルは変わらないので）そのまま引き継がれる。
         */
        private var bpmOverride: Int? = null

        // パターンもブロックごとに 1 つ増やし続ける。スロットを使い回すと、
        // 先のブロックが後で同じ場所を上書きしたとき、もう鳴らし終えたはずの
        // 昔の小節まで中身が変わって見えてしまう（再生は前にしか進まないので
        // 実害は出ないが、育てる速さを少しでも間違えると事故になる作りだった）。
        // Pattern は軽いオブジェクトなので、bars と同じく増やしっぱなしにする。
        private val mutablePatterns = mutableListOf<Pattern>()
        private val mutableBars = mutableListOf<Bar>()

        /** ここまでに作った小節数。 */
        val barCount: Int get() = mutableBars.size

        /** いまの場面のスナップショット。 */
        val status: Status
            get() = Status(era.genre, era.key, bpmOverride ?: era.bpm, era.blocksPlayed, era.recipe)

        init {
            grow()
        }

        /** 今の音声エンジンにそのまま渡せる再生プラン。 */
        fun plan(): PlaybackPlan = PlaybackPlan(mutablePatterns.toList(), mutableBars.toList())

        /**
         * テンポを自分で決める。次に作るブロックから、以降ずっとこのテンポで
         * 鳴る（場面が変わってもジャンルは変わらないので、引き直されない）。
         */
        fun setBpm(bpm: Int) {
            bpmOverride = bpm.coerceIn(Song.MIN_BPM, Song.MAX_BPM)
        }

        /**
         * もう 1 ブロック作って、鳴らせる範囲を伸ばす。
         *
         * 中身の組み立ては [SongBuilder.build] の 1 ブロックぶんとほぼ同じ
         * （進行を選ぶ → 7th で色付け → sus4 を散らす → リズムを作る →
         * 最後の小節だけ崩す → コードに合わせて旋律を作る）。SongBuilder は
         * これを決まった数のパターンに割り当てて終わるが、ここは終わらせずに
         * 呼ばれるたびに繰り返す。
         */
        fun grow() {
            // 場面転換は起承転結の区切り（結を鳴らし終えたところ）でだけ起きる。
            // ブロックの途中でいきなり別ジャンルへ切り替わると、曲が終止しないまま
            // 断ち切られたように聞こえるため、句の途中では blocksLeft が尽きていても
            // 待つ（結を待つ間、blocksLeft はマイナスまで進む。害はない）。
            val phraseBlock = era.blocksPlayed % PHRASE_BLOCKS
            // ジャンルは引き継ぐ（始まってから変わらない）。調とテンポだけ
            // おまかせで引き直す。
            if (phraseBlock == 0 && era.blocksLeft <= 0) era = newEra(era.genre, null, null)
            // 場面の最後の結かどうか。ここだけは進行の終わりに関係なく、
            // しっかり主和音へ着地させる（下の cadence）。
            val endsEra = phraseBlock == CODA_BLOCK && era.blocksLeft <= 1
            era.blocksLeft--
            era.blocksPlayed++

            val recipe = era.recipe
            // 転だけ、ある確率で近い調へ一時的に転調する。起・承・結は
            // 場面の調（era.key）のまま。結では必ずここへ戻ってくる。
            val key = if (phraseBlock == TURN_BLOCK) modulationKeyFor(era.key) else era.key
            val progression = if (phraseBlock == TURN_BLOCK) era.turnProgression else era.mainProgression
            val coloured = Harmony.enrichSevenths(
                progression.chords(key),
                progression.keyFor(key),
                recipe.seventhChance,
                random,
            )
            val cycle = Harmony.sprinkleSus4(coloured, random)
            val chords = List(BLOCK) { cycle[it % cycle.size] }
                .let { if (endsEra) withCadence(it, key) else it }

            val style = recipe.pickRhythm(random)
            val generated = PatternGenerator.generate(style, random, blockName(mutablePatterns.size))
            var pattern = generated
                .withBarCount(BLOCK)
                .withRhythmAt(BLOCK - 1, PatternGenerator.fill(generated, random))
                .let { if (endsEra) it else withMidBarMovement(it, chords, random) }

            val leads = MelodyGenerator.generateBars(
                chords = chords,
                key = key,
                random = random,
                density = recipe.melodyDensity,
                previous = previousLead,
            )
            pattern = pattern.withLeads(leads)
            previousLead = leads.lastOrNull()

            val index = mutablePatterns.size
            mutablePatterns += pattern
            for (i in 0 until BLOCK) {
                mutableBars += Bar(patternIndex = index, chord = chords[i], patternBar = i)
            }
        }

        /** そのブロックのパターンに付ける、見分けが付けばいいだけの名前。 */
        private fun blockName(index: Int): String = "#${index + 1}"

        /**
         * ブロックの中の小節を、ときどき半分だけ差し替える。「打ち込みに
         * コードを置く」と同じ仕組み（[Pattern.withChordAt]）にそのまま
         * 乗せるので、鳴らす側は普段の打ち込みと区別せずに引ける。
         *
         * 旋律（[MelodyGenerator]）は 1 小節に 1 和音のままにしてある。
         * 半小節ごとに旋律まで作り直すのは大掛かりになるうえ、先取りする
         * 和音はどのみち次の小節でそのまま鳴る和音なので、後半だけ
         * コード楽器が先に動くのは、旋律が向かう先を軽く先取りする形に
         * 聞こえて破綻しない。
         */
        private fun withMidBarMovement(pattern: Pattern, chords: List<Chord>, random: Random): Pattern {
            var result = pattern
            for (bar in 0 until BLOCK - 1) {
                val next = chords[bar + 1]
                if (chords[bar] == next) continue
                if (random.nextDouble() < MID_BAR_CHANCE) {
                    result = result.withChordAt(bar, STEPS_PER_BAR / 2, next)
                }
            }
            // 最後の小節だけは「次」が無い（次のブロックの頭は、乱数の並びを
            // 崩さずには先読みできない）。そのブロック最初の和音へ一度だけ
            // 戻す形にする。句の起がまた同じところから始まる感覚に近い。
            val last = BLOCK - 1
            if (chords[last] != chords[0] && random.nextDouble() < LAST_BAR_WRAP_CHANCE) {
                result = result.withChordAt(last, STEPS_PER_BAR / 2, chords[0])
            }
            return result
        }

        /**
         * 転のときだけ、ある確率で [from] の近い調（属調・下属調・平行調）
         * へ寄り道する。外れたとき・寄り道先が無いときは [from] のまま。
         */
        private fun modulationKeyFor(from: MusicKey): MusicKey {
            if (random.nextDouble() >= MODULATION_CHANCE) return from
            val candidates = buildList {
                add(MusicKey((from.tonic + 7).mod(12), from.scale)) // 属調（5 度上）
                add(MusicKey((from.tonic + 5).mod(12), from.scale)) // 下属調（4 度上）
                when (from.scale) {
                    Scale.MAJOR -> add(MusicKey((from.tonic + 9).mod(12), Scale.NATURAL_MINOR)) // 平行短調
                    Scale.NATURAL_MINOR -> add(MusicKey((from.tonic + 3).mod(12), Scale.MAJOR)) // 平行長調
                    else -> Unit
                }
            }
            return candidates.random(random)
        }

        /**
         * 場面の最後の 2 小節を V7 → I の終止に差し替える。
         *
         * 次のブロックから別のジャンル・調・テンポへ切り替わるので、進行が
         * 中途半端なところで断ち切られると、そこだけ曲が途切れたように聞こえる。
         * 7th・sus4 の色付けより後にここで上書きすることで、色付けの結果に
         * 関係なく必ずしっかり着地させる。
         */
        private fun withCadence(chords: List<Chord>, key: MusicKey): List<Chord> {
            val diatonic = key.diatonicChords()
            val dominant = diatonic[CADENCE_DOMINANT_DEGREE].copy(quality = ChordQuality.SEVENTH)
            val tonic = diatonic[0]
            return chords.toMutableList().apply {
                this[lastIndex] = tonic
                this[lastIndex - 1] = dominant
            }
        }

        /** 新しい場面を用意する。null を渡した軸はここでおまかせに決める。 */
        private fun newEra(genre: Genre?, key: MusicKey?, scene: GameScene?): Era {
            // 起点の場面は好きなものを選べるが、以降の場面転換は毎回すべておまかせ。
            // ずっと固定の軸を持たせると「移り変わる」にならないため。
            val resolvedGenre = genre ?: Genre.entries.random(random)
            val resolvedKey = key ?: MusicKey(random.nextInt(12), KEY_SCALES.random(random))
            val recipe = SongEditor.recipeFor(resolvedGenre, scene, random)
            val main = recipe.pickProgression(random)
            // 転は別の型を選ぶ。候補が 1 つしか無いジャンルは仕方なく同じになる。
            val turn = recipe.progressions.filter { it != main }.randomOrNull(random) ?: main
            return Era(
                resolvedGenre,
                resolvedKey,
                recipe,
                recipe.pickBpm(random),
                DRIFT_BLOCKS.random(random),
                mainProgression = main,
                turnProgression = turn,
            )
        }
    }
}
