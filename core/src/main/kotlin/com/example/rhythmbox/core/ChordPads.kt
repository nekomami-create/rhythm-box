package com.example.rhythmbox.core

/**
 * コードパッドに並べる和音。
 *
 * 調が決まれば「よく使う 12 個」はほぼ決まるので、既定はそこから作る。
 * 押すだけで進行が弾けることが目的なので、調の外の音は入れない。
 */
object ChordPads {

    /** パッドの数（3 列 x 4 段）。 */
    const val COUNT = 12

    /**
     * [key] から既定の 12 個を作る。
     *
     * 前半 7 つは音階上のコードをそのまま度数順に。
     * 残り 5 つは、その 7 つのうちよく 7th で鳴らすものを厚くしたもの。
     * 同じ和音が二度出るが、三和音と 7th は別の響きなので並べる価値がある。
     */
    fun forKey(key: MusicKey): List<Chord> {
        val diatonic = key.diatonicChords()
        val extras = listOf(
            // V7（終止感の芯）、IVM7、ii7、vi7、IM7。
            EXTRA_FIFTH to ChordQuality.SEVENTH,
            EXTRA_FOURTH to ChordQuality.MAJOR_SEVENTH,
            EXTRA_SECOND to ChordQuality.MINOR_SEVENTH,
            EXTRA_SIXTH to ChordQuality.MINOR_SEVENTH,
            EXTRA_FIRST to ChordQuality.MAJOR_SEVENTH,
        ).map { (degree, quality) ->
            val base = diatonic.getOrNull(degree) ?: diatonic.first()
            // マイナーキーでは度数ごとの明暗が変わるので、元の和音の性格に寄せる。
            Chord(base.root, seventhFor(base.quality, quality))
        }
        return (diatonic + extras).take(COUNT)
    }

    /**
     * 三和音を 7th にする。元がマイナーならマイナー 7th、
     * 減三和音なら m7-5 と、性格を変えずに音を足す。
     */
    private fun seventhFor(triad: ChordQuality, preferred: ChordQuality): ChordQuality = when (triad) {
        ChordQuality.MINOR -> ChordQuality.MINOR_SEVENTH
        ChordQuality.DIMINISHED -> ChordQuality.HALF_DIMINISHED
        ChordQuality.AUGMENTED -> ChordQuality.AUGMENTED
        else -> preferred
    }

    /** 既定を作るときに厚くする度数（0 から数える）。 */
    private const val EXTRA_FIRST = 0
    private const val EXTRA_SECOND = 1
    private const val EXTRA_FOURTH = 3
    private const val EXTRA_FIFTH = 4
    private const val EXTRA_SIXTH = 5

    /**
     * 実際に並べる 12 個。
     * [saved] が空なら調から作る（調を変えると付いてくる）。
     * 1 つでも自分で決めていれば、そちらを尊重して足りないぶんだけ補う。
     */
    fun resolve(saved: List<Chord>, key: MusicKey): List<Chord> {
        if (saved.isEmpty()) return forKey(key)
        val defaults = forKey(key)
        return List(COUNT) { saved.getOrNull(it) ?: defaults[it] }
    }
}
