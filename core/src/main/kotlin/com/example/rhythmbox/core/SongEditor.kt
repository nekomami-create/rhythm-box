package com.example.rhythmbox.core

import kotlin.random.Random

/**
 * 曲を書き換える操作のうち、画面の状態に依存しないもの。
 *
 * これまで ViewModel の中で「今どのパターンを開いているか」を見ながら
 * 書き換えていたため、音楽としての判断（どの小節に何を置くか）が
 * 画面の都合と混ざっていて、再生を通さないと確かめられなかった。
 *
 * ここに置いたものは曲と引数だけで答えが決まるので、そのまま測れる。
 * Android の API も使っていないので、iOS へ移すときもこのまま動く。
 */
object SongEditor {

    /**
     * 中身のあるパターンの番号。1 つも無ければ [fallback]（ふつうは今開いているもの）。
     * 「全パターン」に書き換えるとき、空のパターンまで埋めないための線引き。
     */
    fun usedPatterns(song: Song, fallback: Int): List<Int> =
        song.patterns.indices.filter { !song.pattern(it).isEmpty() }.ifEmpty { listOf(fallback) }

    /**
     * 曲に出てくるコード。曲構成があればそこから、無ければパターンの試聴コードから。
     * 調を推定するときの材料になる。
     */
    fun chords(song: Song): List<Chord> =
        PlaybackPlan.arrangement(song).bars.map { it.chord }.ifEmpty { song.patternChords }

    /** リズムを 1 つ引く。[style] が null ならスタイルもおまかせ。 */
    fun drawRhythm(style: RhythmStyle?, name: String, random: Random): Pattern =
        if (style == null) {
            PatternGenerator.generateAny(random, name)
        } else {
            PatternGenerator.generate(style, random, name)
        }

    /**
     * パターンの全小節に [groove] を置く。
     * 1 小節目にだけ書くと、長いパターンの後半が古いまま残る。
     */
    fun withGrooveEverywhere(pattern: Pattern, groove: Pattern): Pattern =
        (0 until pattern.barCount).fold(pattern) { acc, bar -> acc.withRhythmAt(bar, groove) }

    /**
     * リズムを引き直した曲。
     *
     * [targets] のパターンを書き換える。[bar] を渡すとその小節だけ、
     * null なら全小節に同じノリを置く。小節ごとに違うノリを引くと
     * まとまりが無くなるので、変化を付けるのは小節単位の引き直しに任せている。
     *
     * スタイルはパターンごとに引き直す（おまかせなら全部違う型になる）。
     */
    fun withGeneratedRhythm(
        song: Song,
        targets: List<Int>,
        bar: Int?,
        style: RhythmStyle?,
        random: Random,
    ): Song = targets.fold(song) { acc, index ->
        val current = acc.pattern(index)
        val groove = drawRhythm(style, current.name, random)
        val next = if (bar == null) {
            withGrooveEverywhere(current, groove)
        } else {
            current.withRhythmAt(bar, groove)
        }
        acc.withPattern(index, next)
    }

    /**
     * チップ音源で鳴らす中身なら、音色とドラムと弾き方をまとめて切り替えた曲。
     * どれか 1 つだけでは「ゲーム音楽っぽさ」にならない。
     */
    fun withChipSound(song: Song, recipe: GenreRecipe): Song =
        if (!recipe.chip) {
            song
        } else {
            song.copy(
                soundSet = SoundSet.CHIP,
                drumKit = DrumKit.CHIP,
                leadVoice = recipe.leadVoice,
                chordStyle = ChordStyle.CHIP_ARPEGGIO,
            )
        }

    /**
     * 当てはめる中身。場面を持つジャンルは、選ばれていなければ 1 つ引く。
     * 「おまかせ」でゲーム音楽が出たときも、どれかの場面にはなる。
     */
    fun recipeFor(genre: Genre, scene: GameScene?, random: Random): GenreRecipe = when {
        scene != null -> scene.recipe()
        genre.scenes.isNotEmpty() -> genre.scenes.random(random).recipe()
        else -> genre.recipe()
    }
}
