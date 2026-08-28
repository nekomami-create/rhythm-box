package com.example.rhythmbox.core

import kotlin.random.Random

/**
 * 打ち込みに置くコード進行の種。
 *
 * コードを 1 つずつ選ぶだけだと、定番の進行を置くのに毎回 4 回選ぶことになる。
 * 名前の付いた進行をそのまま置ければ、そこから直すほうが早い。
 *
 * ここは種を出すだけで、良し悪しは決めない。どれが合うかは曲の中身と好みで
 * 変わるし、鳴らせば分かる。点を付けて並べ替えるより、手数を減らすほうが役に立つ。
 */
object ChordProgressions {

    /** ひと掴みの長さ。 */
    const val BARS = 4

    /** [seeds] で後ろに並べる、作らせた進行の数。 */
    const val GENERATED = 4

    /** 違う進行を 1 つ見つけるのに、何回まで引き直すか。 */
    private const val TRIES = 12

    /** 置き場所ひとつ。 */
    data class Placement(val bar: Int, val slot: Int, val chord: Chord)

    /** 名前の付いたコード進行ひと組。 */
    data class Seed(val name: String, val chords: List<Chord>)

    /**
     * 定番の型を [key] で解決したもの。
     *
     * 型より短く切るときは頭から、長く伸ばすときは繰り返す（[ProgressionTemplate.fill]
     * と同じ扱い）。カノン進行のように 8 小節の型は、4 小節ぶんだと前半になる。
     */
    fun templates(key: MusicKey, bars: Int = BARS): List<Seed> =
        TEMPLATES.map { Seed(it.name, it.fill(key, bars)) }

    /**
     * その場で作った進行。[count] 個ぶん、それぞれ違う流れになる。
     *
     * 起承転結の割り振りで作るので、4 小節でも「始まって、離れて、帰る」形になる。
     *
     * 4 小節ぶんだと作れる形がそう多くないので、素直に [count] 回引くと同じ並びが
     * 何度も出る。並んだ選択肢が全部同じでは選びようがないので、引き直して違う
     * ものだけを集める。集まらなければ、その数だけ返す（水増しはしない）。
     */
    fun generated(key: MusicKey, bars: Int = BARS, count: Int = 4, random: Random = Random): List<Seed> =
        distinct(count, emptyList()) { ChordSuggester.generateStory(bars, key, random = random) }

    /**
     * [draw] を引き直して、[avoid] とも互いとも違う進行を [count] 個集める。
     *
     * 引ける形には限りがあるので、[TRIES] 倍まで引いて諦める。回し続けると、
     * もう出尽くしている調で画面が止まってしまう。
     */
    private fun distinct(count: Int, avoid: List<List<Chord>>, draw: () -> List<Chord>): List<Seed> {
        val seen = avoid.toMutableSet()
        val found = mutableListOf<List<Chord>>()
        var tries = 0
        while (found.size < count && tries < count * TRIES) {
            tries++
            val chords = draw()
            if (seen.add(chords)) found += chords
        }
        return found.mapIndexed { index, chords -> Seed("作らせた ${index + 1}", chords) }
    }

    /**
     * [chords] を [bars] 小節ぶんの枠に等間隔で割り当てる。
     *
     * 1 小節は [CHORD_SLOTS] 枠なので、置ける場所は全部で bars × 8 個ある。
     * そこへ等間隔で並べると、
     *
     * - 4 小節のパターンに 4 つ … 1 小節に 1 つ（いちばんよくある形）
     * - 2 小節のパターンに 4 つ … 半小節に 1 つ
     * - 1 小節のパターンに 4 つ … 1 拍に 1 つ
     *
     * と、長さに合わせて素直な置き方になる。規則が 1 本で済むので、
     * 小節数ごとに場合分けしなくていい。
     */
    fun spread(chords: List<Chord>, bars: Int): List<Placement> {
        if (chords.isEmpty() || bars <= 0) return emptyList()
        val total = bars * CHORD_SLOTS
        val used = chords.take(total)
        return used.mapIndexed { index, chord ->
            val at = index * total / used.size
            Placement(at / CHORD_SLOTS, at % CHORD_SLOTS, chord)
        }
    }

    /**
     * 種をひと通り。頭に定番、後ろに作らせたものを置く。
     *
     * 作らせたほうは定番と重なったものを外す。同じ並びが「カノン進行」と
     * 「作らせた 2」の両方で出ていると、違うものだと思って選んでしまう。
     */
    fun seeds(key: MusicKey, bars: Int = BARS, random: Random = Random): List<Seed> {
        val templates = templates(key, bars)
        return templates + distinct(GENERATED, templates.map { it.chords }) {
            ChordSuggester.generateStory(bars, key, random = random)
        }
    }

    /**
     * [chords] を [bars] 小節ぶんに揃える。足りなければ最後のコードを伸ばし、
     * 多ければ切る。差し替えの前に必ず通して、長さの食い違いで
     * コードが欠けたり余ったりしないようにする。
     */
    fun fit(chords: List<Chord>, bars: Int): List<Chord> {
        if (bars <= 0) return emptyList()
        val fallback = chords.lastOrNull() ?: Chord()
        return List(bars) { chords.getOrElse(it) { fallback } }
    }

    /** 並べる定番の型。ゲーム用の型も、短調の曲では素直に使える。 */
    private val TEMPLATES = listOf(
        ProgressionTemplate.POP_PUNK,
        ProgressionTemplate.ROYAL_ROAD,
        ProgressionTemplate.KOMURO,
        ProgressionTemplate.CANON,
        ProgressionTemplate.FIFTIES,
        ProgressionTemplate.DANCE_LOOP,
        ProgressionTemplate.CITY,
        ProgressionTemplate.TWO_FIVE_ONE,
        ProgressionTemplate.MINOR_TWO_FIVE,
        ProgressionTemplate.TURNAROUND,
        ProgressionTemplate.DOUBLE_DOMINANT,
        ProgressionTemplate.GAME_FIELD,
        ProgressionTemplate.GAME_BOSS,
        ProgressionTemplate.GAME_DUNGEON,
        ProgressionTemplate.GAME_QUEST,
        ProgressionTemplate.GAME_TOWN,
        ProgressionTemplate.GAME_CAVERN,
    )
}
