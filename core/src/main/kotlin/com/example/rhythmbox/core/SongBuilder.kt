package com.example.rhythmbox.core

import kotlin.random.Random

/**
 * ジャンルを 1 つ選んで、曲をまるごと組み立てる（オート作曲）。
 *
 * 4 小節を 1 ブロックとして、ブロックごとにパターンを割り当てる。
 * ドラム・コード・ベースは 4 小節同じものを繰り返すが、旋律だけは小節ごとに作る。
 * 下のコードが変わるのに旋律が同じままだと、和音から外れるうえに単調になるため。
 */
object SongBuilder {

    /** 1 ブロックの小節数。 */
    const val BLOCK = 4

    /** 作れる小節数の下限と上限。 */
    const val MIN_BARS = 4
    const val MAX_BARS = 64

    /**
     * 使うパターンの数の上限。
     * A〜D までにとどめて、E 以降は手で書く用に空けておく。
     */
    const val MAX_PATTERNS = 4

    /** 前半に使うパターン。 */
    const val FIRST_PATTERN = 0

    /** 後半に使うパターン。 */
    const val SECOND_PATTERN = 1

    /** 選べる小節数（4, 8, 12 … 64）。 */
    val BAR_CHOICES: List<Int> = (MIN_BARS..MAX_BARS step BLOCK).toList()

    /** [bars] 小節を 4 小節ずつに割り、ブロックごとに使うパターンの番号を返す。 */
    fun patternLayout(bars: Int): List<Int> {
        val blocks = normalizeBars(bars) / BLOCK
        return List(blocks) { it % MAX_PATTERNS }
    }

    /** 4 小節単位に丸めて、扱える範囲に収める。 */
    fun normalizeBars(bars: Int): Int =
        (bars / BLOCK * BLOCK).coerceIn(MIN_BARS, MAX_BARS)

    fun build(
        base: Song,
        recipe: GenreRecipe,
        key: MusicKey,
        bars: Int = 8,
        random: Random = Random.Default,
        /** 旋律も作るか。 */
        withMelody: Boolean = true,
    ): Song {
        val total = normalizeBars(bars)
        val layout = patternLayout(total)
        val progression = recipe.pickProgression(random)
        // 味付けは「進行 1 周ぶん」に掛けてから敷き詰める。
        //
        // 小節ごとにばらばらに掛けると、同じパターンが後半で戻ってきたときに
        // コードだけが変わってしまい、そのパターンのために作った旋律が合わなくなる。
        // 旋律はパターンごとに 1 回しか作らないので、ここが崩れると直しようがない。
        //
        // 旋律を作る前に済ませるのも大事で、あとから足すと旋律が元のコードの
        // 3 度を歌ってしまい、預けたはずの音とぶつかる。
        // 7th の色付けが先。あとから掛けると、sus4 にして 3 度を預けた和音に
        // また 3 度が戻ってくる（7th は 3 度の上に積む音なので）。
        val coloured = Harmony.enrichSevenths(
            progression.chords(key),
            progression.keyFor(key),
            recipe.seventhChance,
            random,
        )
        val cycle = Harmony.sprinkleSus4(coloured, random)
        val chords = List(total) { cycle[it % cycle.size] }
        val style = recipe.pickRhythm(random)

        var song = base.copy(bpm = recipe.pickBpm(random))

        // 同じパターンが何ブロックかに出てくる。最初に出てくるブロックの
        // コードに合わせて作れば、以降のブロックでもコードの並びは同じになる。
        val firstBlockOf = layout.withIndex()
            .groupBy({ it.value }, { it.index })
            .mapValues { it.value.first() }

        var previousLead: List<Int>? = null
        firstBlockOf.entries.sortedBy { it.key }.forEach { (patternIndex, blockIndex) ->
            val blockChords = chords.subList(blockIndex * BLOCK, blockIndex * BLOCK + BLOCK)
            val generated = PatternGenerator.generate(style, random, song.pattern(patternIndex).name)
            var pattern = song.pattern(patternIndex).withRhythmOf(generated)
            if (withMelody) {
                // ブロックのコード 1 つにつき 1 小節ぶんの旋律を作る。
                val leads = MelodyGenerator.generateBars(
                    chords = blockChords,
                    key = key,
                    random = random,
                    density = recipe.melodyDensity,
                    previous = previousLead,
                )
                pattern = pattern.withLeads(leads)
                previousLead = leads.lastOrNull()
            }
            song = song.withPattern(patternIndex, pattern)
            // パターン単体で鳴らしたときも、そのブロックの頭の響きになるように。
            song = song.withPatternChord(patternIndex, blockChords.first())
        }

        return song.copy(
            arrangement = layout.mapIndexed { blockIndex, patternIndex ->
                ArrangementStep(
                    patternIndex = patternIndex,
                    repeat = BLOCK,
                    chords = chords.subList(blockIndex * BLOCK, blockIndex * BLOCK + BLOCK),
                )
            },
        )
    }
}
