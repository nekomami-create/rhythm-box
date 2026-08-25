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
     * 1 小節ぶんだけコードを選び直す。前後の小節に馴染むものから、重みに応じて 1 つ引く。
     *
     * いちばん重い候補を返すと、押すたびに同じコードが出て「おまかせ」にならない。
     * かといって一様に引くと繋がりが崩れるので、[suggest] の重みをそのまま
     * 抽選の確率として使う。
     */
    fun pickOne(
        previous: Chord?,
        key: MusicKey,
        next: Chord? = null,
        random: Random = Random.Default,
        /** これは出さない（今そこにあるコード）。 */
        exclude: Chord? = null,
    ): Chord? {
        val candidates = suggest(previous, key, next, limit = 6)
            .filter { it.chord != exclude && it.weight > 0.0 }
        if (candidates.isEmpty()) return null
        val total = candidates.sumOf { it.weight }
        var ticket = random.nextDouble() * total
        for (candidate in candidates) {
            ticket -= candidate.weight
            if (ticket <= 0.0) return candidate.chord
        }
        return candidates.last().chord
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

    // --- 起承転結 -----------------------------------------------------------

    /** 曲の流れの中での役割。 */
    enum class SectionRole(val label: String, val description: String) {
        OPENING("起", "調を示して始める"),
        DEVELOPMENT("承", "受けて広げる"),
        TURN("転", "主和音から離れて変化をつける"),
        CONCLUSION("結", "主和音へ帰って終わる"),
    }

    /** 役割ごとの、その度数の使いやすさ（結は終止形の型で作るのでここには無い）。 */
    private val MAJOR_ROLE_WEIGHTS = mapOf(
        //                              I    ii   iii  IV   V    vi   vii
        SectionRole.OPENING to doubleArrayOf(1.00, 0.40, 0.30, 0.60, 0.50, 0.60, 0.15),
        SectionRole.DEVELOPMENT to doubleArrayOf(0.50, 0.80, 0.40, 1.00, 0.50, 0.80, 0.20),
        SectionRole.TURN to doubleArrayOf(0.05, 0.70, 0.90, 0.60, 0.50, 0.90, 0.40),
    )

    private val MINOR_ROLE_WEIGHTS = mapOf(
        //                              i    ii°  III  iv   v    VI   VII
        SectionRole.OPENING to doubleArrayOf(1.00, 0.20, 0.50, 0.60, 0.40, 0.70, 0.60),
        SectionRole.DEVELOPMENT to doubleArrayOf(0.50, 0.30, 0.70, 0.90, 0.50, 0.90, 0.80),
        SectionRole.TURN to doubleArrayOf(0.05, 0.50, 0.80, 0.60, 0.50, 0.80, 0.90),
    )

    /** 終止形の型（度数）。最後は必ず主和音。 */
    private val CADENCES = mapOf(
        1 to listOf(listOf(0)),
        2 to listOf(
            listOf(4, 0), // V - I（全終止）
            listOf(4, 0),
            listOf(3, 0), // IV - I（変格終止）
        ),
        3 to listOf(
            listOf(1, 4, 0), // ii - V - I
            listOf(3, 4, 0), // IV - V - I
            listOf(5, 4, 0), // vi - V - I
        ),
        4 to listOf(
            listOf(0, 3, 4, 0), // I - IV - V - I
            listOf(5, 1, 4, 0), // vi - ii - V - I
            listOf(3, 1, 4, 0), // IV - ii - V - I
            listOf(5, 3, 4, 0), // vi - IV - V - I
        ),
    )

    /** [bars] 小節を起承転結に割り振る。小節が少ないときは後ろの役割を優先して残す。 */
    fun sections(bars: Int): List<Pair<SectionRole, IntRange>> {
        if (bars <= 0) return emptyList()
        val conclusion = (bars / 4).coerceIn(1, 4)
        val rest = bars - conclusion
        val base = rest / 3
        val extra = rest % 3
        val sizes = listOf(
            SectionRole.OPENING to base + if (extra > 0) 1 else 0,
            SectionRole.DEVELOPMENT to base + if (extra > 1) 1 else 0,
            SectionRole.TURN to base,
            SectionRole.CONCLUSION to conclusion,
        )
        var start = 0
        return sizes.mapNotNull { (role, size) ->
            if (size <= 0) return@mapNotNull null
            val range = start until (start + size)
            start += size
            role to range
        }
    }

    /**
     * 終わらせるためのコード進行（結）を [length] 小節ぶん作る。
     * 最後は必ず主和音。[previous] を渡すと、そこから繋がりやすい型を選ぶ。
     */
    fun cadence(
        length: Int,
        key: MusicKey,
        previous: Chord? = null,
        random: Random = Random.Default,
    ): List<Chord> {
        if (length <= 0) return emptyList()
        val diatonic = key.diatonicChords()
        val tonic = diatonic.first()
        if (length > MAX_CADENCE) {
            // 長いときは前を普通に埋めて、後ろ 4 小節を終止形にする。
            val head = generateSection(length - MAX_CADENCE, SectionRole.TURN, key, previous, random)
            return head + cadence(MAX_CADENCE, key, head.lastOrNull() ?: previous, random)
        }

        val templates = CADENCES.getValue(length)
        // 直前のコードから入りやすい型を、重み付きで選ぶ。
        val fromDegree = previous?.let { key.degreeOf(it) }
        val weights = templates.map { template ->
            val first = template.first()
            if (previous != null && diatonic[first] == previous && templates.size > 1) {
                0.0 // 同じコードが続く型は避ける
            } else {
                val flow = if (fromDegree != null) transitions(key)[fromDegree][first] else 0.6
                flow + 0.2
            }
        }
        val template = pickWeightedIndex(weights, random)?.let { templates[it] } ?: templates.first()

        return template.mapIndexed { index, degree ->
            when {
                index == template.lastIndex -> tonic
                // マイナーキーの V は、メジャーにすると終わった感じが強くなる（和声的短音階）。
                degree == 4 && key.minor && random.nextDouble() < HARMONIC_MINOR_CHANCE ->
                    Chord((key.tonic + 7).mod(12), ChordQuality.MAJOR)
                // メジャーキーの V は、ときどき 7th にして終止感を強める。
                degree == 4 && !key.minor && random.nextDouble() < DOMINANT_SEVENTH_CHANCE ->
                    diatonic[4].copy(quality = ChordQuality.SEVENTH)
                else -> diatonic[degree]
            }
        }
    }

    /**
     * 起承転結の流れを持つコード進行を [bars] 小節ぶん作る。
     * 起で調を示し、承で受け、転で主和音から離れ、結で帰ってくる。
     */
    fun generateStory(
        bars: Int,
        key: MusicKey,
        start: Chord? = null,
        random: Random = Random.Default,
    ): List<Chord> {
        if (bars <= 0) return emptyList()
        val progression = mutableListOf<Chord>()
        for ((role, range) in sections(bars)) {
            val previous = progression.lastOrNull()
            progression += if (role == SectionRole.CONCLUSION) {
                cadence(range.count(), key, previous, random)
            } else {
                val head = if (progression.isEmpty()) start ?: key.diatonicChords().first() else null
                generateSection(range.count(), role, key, previous, random, head)
            }
        }
        return progression
    }

    /** 役割 [role] のまとまりを [length] 小節ぶん作る。 */
    private fun generateSection(
        length: Int,
        role: SectionRole,
        key: MusicKey,
        previous: Chord?,
        random: Random,
        firstChord: Chord? = null,
    ): List<Chord> {
        if (length <= 0) return emptyList()
        val diatonic = key.diatonicChords()
        val roleWeights = (if (key.minor) MINOR_ROLE_WEIGHTS else MAJOR_ROLE_WEIGHTS).getValue(role)
        val result = mutableListOf<Chord>()
        var last = previous
        repeat(length) { index ->
            if (index == 0 && firstChord != null) {
                result += firstChord
                last = firstChord
                return@repeat
            }
            val fromDegree = last?.let { key.degreeOf(it) }
            val weights = diatonic.indices.map { degree ->
                if (diatonic[degree] == last) {
                    0.0 // 同じコードを続けない
                } else {
                    val flow = if (fromDegree != null) transitions(key)[fromDegree][degree] else 0.6
                    flow * roleWeights[degree]
                }
            }
            val chord = pickWeightedIndex(weights, random)?.let { diatonic[it] } ?: diatonic.first()
            result += chord
            last = chord
        }
        return result
    }

    private fun pickWeightedIndex(weights: List<Double>, random: Random): Int? {
        val total = weights.sum()
        if (total <= 0.0) return weights.indices.firstOrNull()
        var target = random.nextDouble() * total
        for (index in weights.indices) {
            target -= weights[index]
            if (target <= 0.0) return index
        }
        return weights.indices.lastOrNull()
    }

    /** 終止形の型を用意してある最大の長さ。 */
    private const val MAX_CADENCE = 4

    /** マイナーキーで V をメジャーにする割合。 */
    private const val HARMONIC_MINOR_CHANCE = 0.75

    /** メジャーキーで V を 7th にする割合。 */
    private const val DOMINANT_SEVENTH_CHANCE = 0.4

    /** 主和音へ帰りやすい度数（IV / V / ii / vii°）。 */
    private val CADENCE_DEGREES = setOf(1, 3, 4, 6)

    private fun transitions(key: MusicKey) = if (key.minor) MINOR_TRANSITIONS else MAJOR_TRANSITIONS

    private fun startOrder(key: MusicKey) = if (key.minor) MINOR_START else MAJOR_START
}
