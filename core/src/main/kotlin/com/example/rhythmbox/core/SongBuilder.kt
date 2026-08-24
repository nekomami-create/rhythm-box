package com.example.rhythmbox.core

import kotlin.random.Random

/**
 * ジャンルを 1 つ選んで、8 小節ぶんの曲をまるごと組み立てる。
 *
 * 旋律（リード）には触れない。旋律はパターンごとに 1 つしか持てないのに対し、
 * コードは小節ごとに変わるため、4 小節ぶん通して合う旋律を自動では作れないため。
 * ドラム・コード・ベースは小節のコードから音程が決まるので、そのまま最後まで馴染む。
 */
object SongBuilder {

    /** 作る小節数（前半 4 小節 + 後半 4 小節）。 */
    const val BARS = 8

    /** 1 ブロックの小節数。 */
    const val BLOCK = 4

    /** 前半に使うパターン。 */
    const val FIRST_PATTERN = 0

    /** 後半に使うパターン。 */
    const val SECOND_PATTERN = 1

    fun build(
        base: Song,
        genre: Genre,
        key: MusicKey,
        random: Random = Random.Default,
    ): Song {
        val progression = genre.pickProgression(random)
        val chords = progression.fill(key, BARS)
        val style = genre.pickRhythm(random)

        var song = base.copy(bpm = genre.pickBpm(random))

        // 前半と後半で別のパターンを作る。同じスタイルでも打点が変わるので、
        // 通して聴いたときに展開が出る。
        listOf(FIRST_PATTERN, SECOND_PATTERN).forEachIndexed { index, patternIndex ->
            val generated = PatternGenerator.generate(style, random, song.pattern(patternIndex).name)
            song = song.withPattern(patternIndex, song.pattern(patternIndex).copy(rows = generated.rows))
            // パターン単体で鳴らしたときも、そのブロックの頭の響きになるように。
            song = song.withPatternChord(patternIndex, chords[index * BLOCK])
        }

        return song.copy(
            arrangement = listOf(
                ArrangementStep(FIRST_PATTERN, BLOCK, chords.take(BLOCK)),
                ArrangementStep(SECOND_PATTERN, BLOCK, chords.drop(BLOCK).take(BLOCK)),
            ),
        )
    }
}
