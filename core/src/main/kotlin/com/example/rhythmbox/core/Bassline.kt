package com.example.rhythmbox.core

/**
 * ベースがその打点で弾く音を決める。
 *
 * ベースの行は 16 ステップの ON/OFF しか持っていない（音の高さは書けない）。
 * だから高さは、和音・次の和音・その小節で何回目の打点か、から導く。
 * 状態を持たないので、ループしても書き出しても同じところで同じ音が鳴る。
 *
 * 弾く高さはベースの帯（C2 の 1 オクターブ）に収める。5 度を上に取ると
 * 調によっては和音の下側とぶつかるので、同じオクターブの中に折り返す
 * （ルートが C なら G は上、G なら D は下。ベース弾きが実際にやる形になる）。
 */
object Bassline {

    /** 半音番号をベースの帯の音に直す。 */
    private fun pitch(semitone: Int): Int = Chord.BASS_BASE_MIDI + semitone.mod(12)

    /**
     * [chord] の [hitIndex] 回目の打点で弾く音。
     *
     * [next] は次の小節の和音。[last] がその小節の最後の打点なら、
     * [BassStyle.WALK] のときだけ次の和音へ半音下から入る（アプローチ音）。
     * 和音が変わらないときは入れない。行き先が同じ音では「近づいた」ことに
     * ならず、ただ半音下がって戻るだけに聞こえる。
     */
    fun noteAt(
        chord: Chord,
        next: Chord,
        hitIndex: Int,
        last: Boolean,
        style: BassStyle,
    ): Int {
        val root = chord.bass ?: chord.root
        if (style == BassStyle.ROOT) return chord.bassMidi()
        val target = next.bass ?: next.root
        if (style == BassStyle.WALK && last && target.mod(12) != root.mod(12)) {
            return pitch(target - 1)
        }
        // 頭は必ずルート。そこが和音を決めているので、5 度から始めると
        // 何のコードなのかが分からなくなる。
        return if (hitIndex % 2 == 0) chord.bassMidi() else pitch(root + 7)
    }
}
