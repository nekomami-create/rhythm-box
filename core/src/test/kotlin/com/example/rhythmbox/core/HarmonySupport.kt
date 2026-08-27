package com.example.rhythmbox.core

/**
 * [expected]（進行の型がそのまま出す和音）に対して、[actual] が
 * 味付けの結果として在り得る形かどうか（テスト用）。
 *
 * 味付けは根音を動かさない。動かすのは響きの種類だけで、行き先は
 * 7th の色付けか sus4、あるいはその両方に限られる。だから
 * 「根音が同じで、種類が決められた行き先のどれか」で判定できる。
 */
internal fun sameOrDressed(expected: Chord, actual: Chord): Boolean {
    if (actual.root != expected.root || actual.bass != expected.bass) return false
    if (actual.quality == expected.quality) return true
    val allowed = buildSet {
        Harmony.suspendedOf(expected.quality)?.let { add(it) }
        // 7th の行き先は度数で変わる（V だけ短 7 度）ので、どちらも許す。
        for (degree in 0..6) {
            val seventh = Harmony.seventhFor(degree, expected.quality) ?: continue
            add(seventh)
            Harmony.suspendedOf(seventh)?.let { add(it) }
        }
    }
    return actual.quality in allowed
}
