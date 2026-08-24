package com.example.rhythmbox.core

import kotlin.random.Random

/**
 * ジャンルを 1 つ選んで、8 小節ぶんの曲をまるごと組み立てる。
 *
 * ドラム・コード・ベースは 4 小節同じものを繰り返すが、旋律だけは小節ごとに作る。
 * 下のコードが変わるのに旋律が同じままだと、和音から外れるうえに単調になるため。
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
        /** 旋律も作るか。 */
        withMelody: Boolean = true,
    ): Song {
        val progression = genre.pickProgression(random)
        val chords = progression.fill(key, BARS)
        val style = genre.pickRhythm(random)

        var song = base.copy(bpm = genre.pickBpm(random))

        // 前半と後半で別のパターンを作る。同じスタイルでも打点が変わるので、
        // 通して聴いたときに展開が出る。
        var previousLead: List<Int>? = null
        listOf(FIRST_PATTERN, SECOND_PATTERN).forEachIndexed { index, patternIndex ->
            val blockChords = chords.subList(index * BLOCK, index * BLOCK + BLOCK)
            val generated = PatternGenerator.generate(style, random, song.pattern(patternIndex).name)
            var pattern = song.pattern(patternIndex).copy(rows = generated.rows)
            if (withMelody) {
                // ブロックのコード 1 つにつき 1 小節ぶんの旋律を作る。
                val leads = MelodyGenerator.generateBars(
                    chords = blockChords,
                    key = key,
                    random = random,
                    density = genre.melodyDensity,
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
            arrangement = listOf(
                ArrangementStep(FIRST_PATTERN, BLOCK, chords.take(BLOCK)),
                ArrangementStep(SECOND_PATTERN, BLOCK, chords.drop(BLOCK).take(BLOCK)),
            ),
        )
    }
}
