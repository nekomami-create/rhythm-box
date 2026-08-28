package com.example.rhythmbox.core

import kotlin.random.Random

/**
 * コード進行だけを取り出して、数小節ぶんを手元で捏ねるための種。
 *
 * これまでコードを触る手段は「1 小節ずつ選ぶ」か「曲全体を書き換える」かの
 * 両極端しか無かった。4 小節という手頃な単位が無く、しかも決める前に
 * 進行として聴く手段も無かった（試聴で鳴るのは和音ひとつだけ）。
 *
 * ここは種を出すだけで、良し悪しは決めない。どれが good かは曲の中身と
 * 好みで変わるし、聴けば分かる。並べ替えたり点を付けたりするより、
 * 手数を減らして早く聴けるほうが役に立つ。
 */
object ChordCruiser {

    /** ひと掴みの長さ。 */
    const val BARS = 4

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
     */
    fun generated(key: MusicKey, bars: Int = BARS, count: Int = 4, random: Random = Random): List<Seed> =
        List(count) { index ->
            Seed("作らせた ${index + 1}", ChordSuggester.generateStory(bars, key, random = random))
        }

    /** 種をひと通り。頭に定番、後ろに作らせたものを置く。 */
    fun seeds(key: MusicKey, bars: Int = BARS, random: Random = Random): List<Seed> =
        templates(key, bars) + generated(key, bars, random = random)

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
