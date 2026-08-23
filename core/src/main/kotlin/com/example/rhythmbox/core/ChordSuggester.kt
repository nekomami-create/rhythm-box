package com.example.rhythmbox.core

import kotlin.math.sqrt
import kotlin.random.Random

/** 調（キー）。[tonic] は C=0 の半音番号。 */
data class MusicKey(val tonic: Int, val minor: Boolean) {
    val name: String get() = Chord.ROOT_NAMES[tonic.mod(12)] + if (minor) "m" else ""

    /** この調の音階上のコード（I, ii, iii, ...）。 */
    fun diatonicChords(): List<Chord> = degrees().map { (offset, quality) ->
        Chord((tonic + offset).mod(12), quality)
    }

    /** 度数の表記（I, ii, V ...）。 */
    fun degreeLabels(): List<String> = if (minor) MINOR_LABELS else MAJOR_LABELS

    internal fun degrees(): List<Pair<Int, ChordQuality>> = if (minor) MINOR_DEGREES else MAJOR_DEGREES

    /** [chord] がこの調の何度か。音階上に無ければ null。 */
    fun degreeOf(chord: Chord): Int? {
        val offset = (chord.root - tonic).mod(12)
        return degrees().indexOfFirst { it.first == offset }.takeIf { it >= 0 }
    }

    companion object {
        // メジャー: I ii iii IV V vi vii°
        private val MAJOR_DEGREES = listOf(
            0 to ChordQuality.MAJOR,
            2 to ChordQuality.MINOR,
            4 to ChordQuality.MINOR,
            5 to ChordQuality.MAJOR,
            7 to ChordQuality.MAJOR,
            9 to ChordQuality.MINOR,
            11 to ChordQuality.DIMINISHED,
        )

        // ナチュラルマイナー: i ii° III iv v VI VII
        private val MINOR_DEGREES = listOf(
            0 to ChordQuality.MINOR,
            2 to ChordQuality.DIMINISHED,
            3 to ChordQuality.MAJOR,
            5 to ChordQuality.MINOR,
            7 to ChordQuality.MINOR,
            8 to ChordQuality.MAJOR,
            10 to ChordQuality.MAJOR,
        )

        private val MAJOR_LABELS = listOf("I", "ii", "iii", "IV", "V", "vi", "vii°")
        private val MINOR_LABELS = listOf("i", "ii°", "III", "iv", "v", "VI", "VII")
    }
}

/** おすすめのコード 1 つぶん。[degree] は "V" のような度数表記。 */
data class ChordSuggestion(
    val chord: Chord,
    val degree: String,
    val weight: Double,
)

/**
 * 「次に繋がりそうなコード」を出す。
 *
 * 曲に出てくるコードから調を推定し、機能和声のよくある進行
 * （V→I、IV→V、vi→ii など）の重みで並べ替える。
 */
object ChordSuggester {

    /** メジャーキーでの進行の起こりやすさ。行が今のコード、列が次のコードの度数。 */
    private val MAJOR_TRANSITIONS = arrayOf(
        //        I    ii   iii  IV   V    vi   vii
        doubleArrayOf(0.20, 0.70, 0.40, 0.90, 0.85, 0.80, 0.20), // I
        doubleArrayOf(0.30, 0.10, 0.20, 0.40, 0.95, 0.30, 0.50), // ii
        doubleArrayOf(0.30, 0.50, 0.10, 0.70, 0.30, 0.90, 0.15), // iii
        doubleArrayOf(0.80, 0.60, 0.20, 0.15, 0.90, 0.50, 0.25), // IV
        doubleArrayOf(0.95, 0.30, 0.20, 0.50, 0.10, 0.80, 0.10), // V
        doubleArrayOf(0.50, 0.85, 0.30, 0.80, 0.70, 0.10, 0.20), // vi
        doubleArrayOf(0.95, 0.20, 0.50, 0.20, 0.20, 0.30, 0.05), // vii°
    )

    /** マイナーキーでの進行の起こりやすさ。 */
    private val MINOR_TRANSITIONS = arrayOf(
        //        i    ii°  III  iv   v    VI   VII
        doubleArrayOf(0.15, 0.40, 0.60, 0.80, 0.60, 0.85, 0.80), // i
        doubleArrayOf(0.40, 0.05, 0.30, 0.30, 0.90, 0.30, 0.60), // ii°
        doubleArrayOf(0.40, 0.20, 0.10, 0.50, 0.30, 0.80, 0.70), // III
        doubleArrayOf(0.80, 0.20, 0.30, 0.10, 0.90, 0.50, 0.60), // iv
        doubleArrayOf(0.95, 0.10, 0.30, 0.40, 0.10, 0.80, 0.30), // v
        doubleArrayOf(0.40, 0.30, 0.50, 0.70, 0.60, 0.10, 0.85), // VI
        doubleArrayOf(0.90, 0.20, 0.70, 0.30, 0.20, 0.40, 0.10), // VII
    )

    /** 何も手がかりが無いときに出す度数の順番（よく使う順）。 */
    private val MAJOR_START = listOf(0, 3, 4, 5, 1, 2, 6)
    private val MINOR_START = listOf(0, 5, 6, 3, 2, 4, 1)

    /**
     * 曲に出てくる [chords] から調を推定する。
     * 音階に収まるコードが多い調を選び、同点なら最初と最後のコードを主和音とする調を優先する。
     */
    fun detectKey(chords: List<Chord>): MusicKey {
        if (chords.isEmpty()) return MusicKey(0, minor = false)
        var best = MusicKey(0, minor = false)
        var bestScore = Double.NEGATIVE_INFINITY
        for (minor in listOf(false, true)) {
            for (tonic in 0..11) {
                val key = MusicKey(tonic, minor)
                val score = score(key, chords)
                if (score > bestScore) {
                    bestScore = score
                    best = key
                }
            }
        }
        return best
    }

    private fun score(key: MusicKey, chords: List<Chord>): Double {
        val degrees = key.degrees()
        var total = 0.0
        chords.forEachIndexed { index, chord ->
            val offset = (chord.root - key.tonic).mod(12)
            val degree = degrees.indexOfFirst { it.first == offset }
            val fit = when {
                degree < 0 -> -0.5 // 音階に無いコード
                sameFamily(degrees[degree].second, chord.quality) -> 1.0
                else -> 0.35 // 度数は合うが種類が違う
            }
            // 最初と最後のコードは調を決める手がかりになりやすい。
            val positionBonus = if (index == 0 || index == chords.lastIndex) 0.5 else 0.0
            val tonicBonus = if (degree == 0) 0.35 + positionBonus else 0.0
            total += fit + tonicBonus
        }
        // 同点ならメジャーを少しだけ優先する。
        return total + if (key.minor) -0.01 else 0.0
    }

    private fun sameFamily(expected: ChordQuality, actual: ChordQuality): Boolean {
        fun family(quality: ChordQuality) = when (quality) {
            ChordQuality.MINOR, ChordQuality.MINOR_SEVENTH -> "m"
            ChordQuality.DIMINISHED -> "dim"
            ChordQuality.AUGMENTED -> "aug"
            else -> "M" // maj / 7 / M7 / sus4
        }
        return family(expected) == family(actual)
    }

    /**
     * [previous] の次に置くと繋がりやすいコードを、良い順に [limit] 個返す。
     *
     * [next] を渡すと「前のコードから来て、次のコードへ繋がる」両側の馴染みで並べ替える
     * （曲の途中の小節を差し替えるとき用）。[previous] が null なら、その調でよく使うコードを順に返す。
     */
    fun suggest(
        previous: Chord?,
        key: MusicKey,
        next: Chord? = null,
        limit: Int = 6,
    ): List<ChordSuggestion> {
        val diatonic = key.diatonicChords()
        val labels = key.degreeLabels()

        val suggestions = diatonic.indices
            .map { ChordSuggestion(diatonic[it], labels[it], affinity(diatonic[it], it, previous, next, key)) }
            .toMutableList()

        // ドミナントセブンスは終止感が強いので、メジャーキーでは V7 も出す。
        val dominant = if (key.minor) null else suggestions.firstOrNull { it.degree == "V" }
        if (dominant != null && dominant.weight > 0.4) {
            suggestions += ChordSuggestion(
                dominant.chord.copy(quality = ChordQuality.SEVENTH),
                dominant.degree + "7",
                dominant.weight - 0.05,
            )
        }
        // マイナーキーでは、和声的短音階のメジャー V もよく使う。
        if (key.minor) {
            val fifth = Chord((key.tonic + 7).mod(12), ChordQuality.MAJOR)
            suggestions += ChordSuggestion(fifth, "V", affinity(fifth, 4, previous, next, key) + 0.12)
        }

        // 前後と同じコードは、繰り返しになるので候補から外す。
        return suggestions
            .filter { it.chord != previous && it.chord != next }
            .sortedByDescending { it.weight }
            .take(limit)
    }

    /**
     * [chord]（この調の [degree] 度）が、[previous] のあと・[next] の前にどれだけ馴染むか。
     * 両側が分かっているときは相乗平均を取り、片側だけに寄り過ぎないようにする。
     */
    private fun affinity(
        chord: Chord,
        degree: Int,
        previous: Chord?,
        next: Chord?,
        key: MusicKey,
    ): Double {
        val fromPrevious = when {
            previous == null -> null
            else -> key.degreeOf(previous)?.let { transitions(key)[it][degree] }
                // 調の外のコードから戻るときは、主和音まわりを厚めに。
                ?: if (degree == 0 || degree == 4 || degree == 3) 0.8 else 0.4
        }
        val toNext = when {
            next == null -> null
            else -> key.degreeOf(next)?.let { transitions(key)[degree][it] }
                ?: 0.5 // 調の外のコードへは、どれから行ってもそこそこ
        }
        val order = 1.0 - startOrder(key).indexOf(degree) * 0.1
        return when {
            fromPrevious != null && toNext != null -> sqrt(fromPrevious * toNext)
            fromPrevious != null -> fromPrevious
            toNext != null -> toNext
            else -> order
        }
    }

    /** [length] 小節ぶんのコード進行を作る。[start] から始めて、最後は主和音に戻す。 */
    fun generateProgression(
        length: Int,
        key: MusicKey,
        start: Chord? = null,
        random: Random = Random.Default,
    ): List<Chord> {
        if (length <= 0) return emptyList()
        val tonic = key.diatonicChords().first()
        val first = start ?: tonic
        val progression = mutableListOf(first)
        while (progression.size < length) {
            when (length - progression.size) {
                // 締めは主和音に帰る。
                1 -> progression += tonic
                // その 1 つ前は、主和音へ繋がるコード（V, IV, ii, vii°）から選ぶ。
                2 -> {
                    val cadence = suggest(progression.last(), key, limit = 8)
                        .filter { it.chord != tonic && key.degreeOf(it.chord) in CADENCE_DEGREES }
                    progression += pickWeighted(cadence, random)?.chord ?: key.diatonicChords()[4]
                }
                else -> {
                    val candidates = suggest(progression.last(), key, limit = 4)
                    progression += pickWeighted(candidates, random)?.chord ?: tonic
                }
            }
        }
        return progression
    }

    private fun pickWeighted(candidates: List<ChordSuggestion>, random: Random): ChordSuggestion? {
        if (candidates.isEmpty()) return null
        val total = candidates.sumOf { it.weight }
        if (total <= 0.0) return candidates.first()
        var target = random.nextDouble() * total
        for (candidate in candidates) {
            target -= candidate.weight
            if (target <= 0.0) return candidate
        }
        return candidates.last()
    }

    /** 主和音へ帰りやすい度数（IV / V / ii / vii°）。 */
    private val CADENCE_DEGREES = setOf(1, 3, 4, 6)

    private fun transitions(key: MusicKey) = if (key.minor) MINOR_TRANSITIONS else MAJOR_TRANSITIONS

    private fun startOrder(key: MusicKey) = if (key.minor) MINOR_START else MAJOR_START
}
