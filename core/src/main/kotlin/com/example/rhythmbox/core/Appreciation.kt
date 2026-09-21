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
 * 場面（ジャンル・調・テンポ）は一定のブロック数ごとに移り変わる。
 * ずっと同じ場面のままだと単調になるが、毎ブロック変えると逆に
 * 「曲」として聴けなくなるので、数十秒〜数分単位の「場面」で区切る。
 */
object Appreciation {

    /** 1 ブロックの小節数。[SongBuilder] と揃えてある。 */
    const val BLOCK = SongBuilder.BLOCK

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
            get() = Status(era.genre, era.key, era.bpm, era.blocksPlayed, era.recipe)

        init {
            grow()
        }

        /** 今の音声エンジンにそのまま渡せる再生プラン。 */
        fun plan(): PlaybackPlan = PlaybackPlan(mutablePatterns.toList(), mutableBars.toList())

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
            if (era.blocksLeft <= 0) era = newEra(null, null, null)
            era.blocksLeft--
            era.blocksPlayed++

            val recipe = era.recipe
            val key = era.key
            val progression = recipe.pickProgression(random)
            val coloured = Harmony.enrichSevenths(
                progression.chords(key),
                progression.keyFor(key),
                recipe.seventhChance,
                random,
            )
            val cycle = Harmony.sprinkleSus4(coloured, random)
            val chords = List(BLOCK) { cycle[it % cycle.size] }

            val style = recipe.pickRhythm(random)
            val generated = PatternGenerator.generate(style, random, blockName(mutablePatterns.size))
            var pattern = generated
                .withBarCount(BLOCK)
                .withRhythmAt(BLOCK - 1, PatternGenerator.fill(generated, random))

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

        /** 新しい場面を用意する。null を渡した軸はここでおまかせに決める。 */
        private fun newEra(genre: Genre?, key: MusicKey?, scene: GameScene?): Era {
            // 起点の場面は好きなものを選べるが、以降の場面転換は毎回すべておまかせ。
            // ずっと固定の軸を持たせると「移り変わる」にならないため。
            val resolvedGenre = genre ?: Genre.entries.random(random)
            val resolvedKey = key ?: MusicKey(random.nextInt(12), KEY_SCALES.random(random))
            val recipe = SongEditor.recipeFor(resolvedGenre, scene, random)
            return Era(resolvedGenre, resolvedKey, recipe, recipe.pickBpm(random), DRIFT_BLOCKS.random(random))
        }
    }
}
