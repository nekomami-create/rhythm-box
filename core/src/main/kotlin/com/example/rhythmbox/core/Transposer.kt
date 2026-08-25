package com.example.rhythmbox.core

/**
 * 曲まるごとのキーを上げ下げする。
 *
 * コードとベースは「その小節のコード」から音が決まるのでコードを動かせば付いてくるが、
 * リードは音の高さを直接持っているので、こちらも同じだけずらさないと和音から外れる。
 */
object Transposer {

    /** 一度にずらせる幅（半音）。上下 1 オクターブまで。 */
    const val MAX_SHIFT = 12

    /**
     * [semitones] 半音だけ動かした曲を返す。
     *
     * リードがピアノロールの外へ出てしまう場合は、曲全体をオクターブ単位で
     * 折り返して収める。音程の関係は変わらないので、旋律の形は保たれる。
     */
    fun transpose(song: Song, semitones: Int): Song {
        val shift = semitones.coerceIn(-MAX_SHIFT, MAX_SHIFT)
        if (shift == 0) return song

        val octaves = leadOctaveFix(song, shift)
        val leadShift = shift + octaves * 12

        return song.copy(
            patterns = song.patterns.map { pattern ->
                pattern.withLeads(
                    pattern.leadBars.map { notes ->
                        notes.map { if (Pattern.isNote(it)) it + leadShift else it }
                    },
                )
            },
            patternChords = song.patternChords.map { it.transposed(shift) },
            arrangement = song.arrangement.map { step ->
                step.copy(chords = step.chords.map { it.transposed(shift) })
            },
        )
    }

    /**
     * リードを音域に収めるために、さらに何オクターブ動かすか。
     * はみ出さないなら 0。
     */
    private fun leadOctaveFix(song: Song, shift: Int): Int {
        val notes = song.patterns
            .flatMap { it.leadBars }
            .flatten()
            .filter { Pattern.isNote(it) }
            .map { it + shift }
        if (notes.isEmpty()) return 0
        var octaves = 0
        while (notes.min() + octaves * 12 < LOWEST_LEAD) octaves++
        while (notes.max() + octaves * 12 > HIGHEST_LEAD) octaves--
        return octaves
    }

    /** ピアノロールで書ける範囲（C4〜C6）。 */
    private const val LOWEST_LEAD = 60
    private const val HIGHEST_LEAD = 84
}
